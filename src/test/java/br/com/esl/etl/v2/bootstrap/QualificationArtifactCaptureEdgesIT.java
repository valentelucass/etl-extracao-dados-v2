package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QualificationArtifactCaptureEdgesIT {
    @TempDir Path folder;
    private int sequence;

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    @Timeout(120)
    void duplicateFreshnessTieCorrectionAndMissingBindingTraverseActualArtifactJdbc(
            final DataExportTemplate template) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var first = row(template, 1, "100", 1);
            final var duplicate = first.deepCopy().put("capture_occurrence", 947);
            final var initial = capture(runtime, template, file(template, first, duplicate));
            assertEquals(1, initial.receipt().inserts());
            assertEquals(1, initial.receipt().duplicates());
            assertEquals(
                    1,
                    capture(runtime, template, file(template, row(template, 2, "100", 1)))
                            .receipt()
                            .updates());
            assertEquals(
                    1,
                    capture(runtime, template, file(template, row(template, 1, "50", 1)))
                            .receipt()
                            .stale());
            assertEquals(
                    1,
                    capture(runtime, template, file(template, row(template, 2, "200", 1)))
                            .receipt()
                            .quarantine());
            assertEquals(
                    1,
                    capture(runtime, template, file(template, row(template, 2, "100", 2)))
                            .receipt()
                            .updates());
            final var unbound = row(template, 2, "100", 1);
            unbound.remove("binding");
            assertEquals(
                    1, capture(runtime, template, file(template, unbound)).receipt().unbound());
            assertEquals(
                    1,
                    ExpansionLaboratoryLocalIntegrationIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    @Timeout(120)
    void partialExcessBindingShapeAndLateCancellationCannotPublish(
            final DataExportTemplate template) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var partial = file(template, row(template, 1, "100", 1));
            Files.writeString(partial.getParent().resolve("page-one.json"), "[");
            assertThrows(RuntimeException.class, () -> capture(runtime, template, partial));
            final var excess =
                    file(
                            template,
                            row(template, 1, "100", 1),
                            row(template, 1, "100", 1),
                            row(template, 1, "100", 1));
            assertThrows(RuntimeException.class, () -> capture(runtime, template, excess));
            final var malformed = row(template, 1, "100", 1);
            ((ObjectNode) malformed.path("binding")).put("revision", "one");
            final var binding = file(template, malformed);
            assertThrows(RuntimeException.class, () -> capture(runtime, template, binding));
            final var input = ExpansionArtifact.read(file(template, row(template, 1, "100", 1)));
            final var cancel = new AtomicBoolean();
            final var source =
                    input.source(
                            new ExpansionCaptureSource.Observer() {
                                @Override
                                public void batchStaged(final int rows) {
                                    cancel.set(true);
                                }
                            });
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            runtime.capture(
                                    template,
                                    ExpansionLaboratoryLocalIntegrationIT.DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source,
                                    cancel::get));
            assertEquals(
                    0,
                    ExpansionLaboratoryLocalIntegrationIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    ExpansionLaboratoryLocalIntegrationIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
        }
    }

    private Path file(final DataExportTemplate template, final ObjectNode... rows)
            throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        folder.resolve("attempt-" + ++sequence),
                        template,
                        JdbcExpansionLaboratory.vertical(template),
                        row -> {});
        final ArrayNode page = JsonNodeFactory.instance.arrayNode();
        for (final var row : rows) {
            page.add(row);
        }
        Files.writeString(path.getParent().resolve("page-one.json"), page.toString());
        final var manifest = (ObjectNode) QualificationJson.read(path, 262144);
        ((ObjectNode) manifest.path("pages").get(0))
                .put("sha256", QualificationJson.sha256(path.getParent().resolve("page-one.json")));
        Files.writeString(path, manifest.toString());
        return path;
    }

    private static ObjectNode row(
            final DataExportTemplate template,
            final int day,
            final String amount,
            final int revision) {
        final var row =
                ExpansionLaboratoryAdversarialIT.row(
                        template, 73, 1, amount, day, revision, true, false);
        ((ObjectNode) row.path("binding"))
                .put("root", "independent-title-alpha")
                .put("part", "independent-part-omega")
                .put("component", "independent-component-zeta");
        return row;
    }

    private static LocalExpansionRuntime.Capture capture(
            final LocalExpansionRuntime runtime, final DataExportTemplate template, final Path path)
            throws Exception {
        return runtime.capture(
                template,
                ExpansionLaboratoryLocalIntegrationIT.DATE,
                ExecutionMode.BACKFILL,
                null,
                ExpansionArtifact.read(path).source(ExpansionCaptureSource.Observer.NONE),
                CancellationToken.none());
    }
}
