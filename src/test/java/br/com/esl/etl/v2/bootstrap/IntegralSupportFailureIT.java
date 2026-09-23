package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralSupportFailureIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"manifestStates", "fiscal"})
    @Timeout(180)
    void unknownDeclaredIdentityCannotReachMaterialization(final String kind) throws Exception {
        final Path inputFile = IntegralArtifactFixtures.write(folder, false, 2);
        final Path support = folder.resolve("support/manifest.json");
        final var supportJson = (ObjectNode) IntegralArtifactFixtures.read(support);
        final var pin = (ObjectNode) supportJson.path("batches").path(kind).path(0);
        final Path data = support.getParent().resolve(pin.path("file").textValue());
        final var rows = IntegralArtifactFixtures.read(data);
        if (kind.equals("fiscal")) {
            ((ObjectNode) rows.path(1).path("component"))
                    .put("value", "synthetic-unknown-component");
        } else {
            ((ObjectNode) rows.path(1)).put("sourceKey", "INTEGER:9999991");
        }
        IntegralArtifactFixtures.save(data, rows);
        pin.put("sha256", br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.sha256(data));
        IntegralArtifactFixtures.save(support, supportJson);
        final var root = (ObjectNode) IntegralArtifactFixtures.read(inputFile);
        root.set("supplements", IntegralArtifactFixtures.pin(folder, support));
        IntegralArtifactFixtures.save(inputFile, root);
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(inputFile, 16384), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final var run = runtime.start(2, 2);
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    runtime.capture(
                                            run,
                                            ExecutionMode.BOOTSTRAP,
                                            input.revision(),
                                            false,
                                            null,
                                            CancellationToken.none()));
            assertTrue(
                    failure.getMessage()
                            .contains(
                                    kind.equals("fiscal")
                                            ? "INTEGRAL_FISCAL_SOURCE_MISSING"
                                            : "INTEGRAL_SUPPORT_SOURCE_MISSING_MAN"),
                    failure::getMessage);
            assertEquals(
                    4,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.expansion(),
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_current_input WHERE run_id=? AND vertical='CAP'"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=? AND state='COMPLETE'"));
            assertEquals(0, session.openControlledStatements());
        }
    }
}
