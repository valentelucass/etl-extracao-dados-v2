package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationRasterPartitionIT {
    @TempDir Path folder;

    @Test
    @Timeout(120)
    void fiveHundredIsRepartitionedAndOnlyCompleteChildrenReachLedger() throws Exception {
        final var artifact = new RasterArtifact(input(false));
        final var profile = artifact.characterize(CancellationToken.none());
        assertTrue(profile.executable());
        assertEquals(3, profile.calls());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session, artifact);
            final var runtime = new LocalRasterRuntime(session, Clock.systemUTC(), artifact.zone());
            final var result =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            artifact.window(),
                            artifact.gateway(),
                            1000,
                            100000,
                            CancellationToken.none());
            assertEquals(3, result.calls());
            assertEquals(4, result.receipt().applied());
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
            final var replay =
                    runtime.capture(
                            run,
                            ExecutionMode.REPLAY,
                            artifact.window(),
                            artifact.gateway(),
                            1000,
                            100000,
                            CancellationToken.none());
            assertEquals(4, replay.receipt().noops());
        }
    }

    @Test
    @Timeout(120)
    void fiveHundredAtMinimumWindowRefusesCharacterizationAndCannotPublishPartialRows()
            throws Exception {
        final var artifact = new RasterArtifact(input(true));
        assertEquals(
                "RAS_CAP_MINIMUM_WINDOW",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> artifact.characterize(CancellationToken.none()))
                        .getMessage());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session, artifact);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new LocalRasterRuntime(session, Clock.systemUTC(), artifact.zone())
                                    .capture(
                                            run,
                                            ExecutionMode.BOOTSTRAP,
                                            artifact.window(),
                                            artifact.gateway(),
                                            1000,
                                            100000,
                                            CancellationToken.none()));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
        }
    }

    private Path input(final boolean minimumCap) throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 1, false);
        final var manifest = (ObjectNode) QualificationJson.read(file, 131072);
        final var prototype = (ObjectNode) manifest.path("pages").get(0);
        final var bindings = QualificationJson.read(folder.resolve("bindings.json"), 524288);
        final var pages = manifest.putArray("pages");
        page(pages, prototype, bindings, "root", "2036-04-01", "2036-04-04", 500);
        page(pages, prototype, bindings, "first", "2036-04-01", "2036-04-02", minimumCap ? 500 : 3);
        page(pages, prototype, bindings, "second", "2036-04-02", "2036-04-04", 3);
        Files.writeString(file, manifest.toString());
        return file;
    }

    private void page(
            final ArrayNode pages,
            final ObjectNode prototype,
            final com.fasterxml.jackson.databind.JsonNode originalBindings,
            final String name,
            final String start,
            final String end,
            final int count)
            throws Exception {
        final var body = JsonNodeFactory.instance.arrayNode();
        final var bindings = JsonNodeFactory.instance.arrayNode();
        for (int index = 1; index <= count; index++) {
            body.add(AnalyticRasterFixtures.data());
            for (int stop = 0; stop <= 1; stop++) {
                final var binding = ((ObjectNode) originalBindings.get(stop)).deepCopy();
                binding.put("tripPosition", index).put("tripKey", "synthetic-partition-" + name);
                bindings.add(binding);
            }
        }
        Files.writeString(folder.resolve(name + "-body.json"), body.toString());
        Files.writeString(folder.resolve(name + "-bindings.json"), bindings.toString());
        final var page =
                prototype
                        .deepCopy()
                        .put("windowStart", start)
                        .put("windowEndExclusive", end)
                        .put("trips", count)
                        .put("stops", count)
                        .put("receipt", "synthetic-partition-" + name);
        for (final var kind : new String[] {"body", "bindings"}) {
            final String file = name + "-" + kind + ".json";
            page.putObject(kind)
                    .put("file", file)
                    .put("sha256", QualificationJson.sha256(folder.resolve(file)));
        }
        pages.add(page);
    }

    private static UUID start(
            final ColetaTemporalLaboratorySession session, final RasterArtifact artifact)
            throws Exception {
        final var run = UUID.randomUUID();
        new JdbcRasterLaboratory(session, Clock.systemUTC())
                .start(
                        run,
                        artifact.window().start(),
                        artifact.window().endExclusive(),
                        artifact.zone(),
                        100000,
                        1000);
        return run;
    }
}
