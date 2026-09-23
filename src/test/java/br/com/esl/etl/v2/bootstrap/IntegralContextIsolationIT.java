package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class IntegralContextIsolationIT {
    @TempDir Path folder;

    @Test
    @Timeout(180)
    void anotherDeclaredScopeCannotCaptureIntoAnExistingRun() throws Exception {
        final Path first = IntegralArtifactFixtures.write(folder.resolve("first"), false, 2);
        final Path second = IntegralArtifactFixtures.write(folder.resolve("second"), false, 2);
        rescope(second, new HashSet<>());
        final var original = input(first);
        final var foreign = input(second);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var firstRuntime = runtime(session, original);
            final var run = firstRuntime.start(2, 2);
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    runtime(session, foreign)
                                            .capture(
                                                    run,
                                                    ExecutionMode.BOOTSTRAP,
                                                    foreign.revision(),
                                                    false,
                                                    null,
                                                    CancellationToken.none()));
            assertTrue(failure.getMessage().contains("INTEGRAL_RUN_CONTEXT"), failure::getMessage);
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=?"));
            final var valid =
                    firstRuntime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            original.revision(),
                            false,
                            null,
                            CancellationToken.none());
            assertTrue(valid.status().complete());
        }
    }

    @Test
    @Timeout(240)
    void identicalKeysAndDatesRemainIsolatedByExplicitSourceAndTenant() throws Exception {
        final Path first = IntegralArtifactFixtures.write(folder.resolve("first"), false, 2);
        final Path second = IntegralArtifactFixtures.write(folder.resolve("second"), false, 2);
        rescope(second, new HashSet<>());
        final Path secondOracle = second.resolveSibling("oracle.json");
        final var oracle = (ObjectNode) IntegralArtifactFixtures.read(secondOracle);
        oracle.put("inputSha256", QualificationJson.sha256(second));
        IntegralArtifactFixtures.save(secondOracle, oracle);
        final UUID firstRun = UUID.randomUUID(), secondRun = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            IntegralArtifactReplayIT.exact(
                    new LocalArtifactScenario(
                                    first,
                                    first.resolveSibling("oracle.json"),
                                    CancellationToken.none())
                            .execute(
                                    session,
                                    CancellationToken.none(),
                                    AnalyticScenarioObserver.NONE,
                                    firstRun,
                                    () -> {}));
            IntegralArtifactReplayIT.exact(
                    new LocalArtifactScenario(second, secondOracle, CancellationToken.none())
                            .execute(
                                    session,
                                    CancellationToken.none(),
                                    AnalyticScenarioObserver.NONE,
                                    secondRun,
                                    () -> {}));
            for (final UUID run : new UUID[] {firstRun, secondRun}) {
                assertEquals(
                        4,
                        IntegralArtifactResilienceIT.count(
                                session,
                                run,
                                "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?"));
                assertEquals(
                        1,
                        IntegralArtifactResilienceIT.count(
                                session,
                                run,
                                "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=? AND state='COMPLETE'"));
            }
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            firstRun,
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_source_current s JOIN ctl.execution_partition p"
                                    + " ON p.current_execution_id=s.execution_id WHERE s.run_id=?"
                                    + " AND (p.source_instance<>'SYNTHETIC_CARRIER_A' OR p.tenant_scope<>'SYNTHETIC_WEST')"));
        }
    }

    private static DeclaredIntegralInputs input(final Path file) throws Exception {
        return new DeclaredIntegralInputs(
                PinnedLocalJson.open(file, 16384), CancellationToken.none());
    }

    private static AnalyticScenarioRuntime runtime(
            final ColetaTemporalLaboratorySession session, final DeclaredIntegralInputs input) {
        return new AnalyticScenarioRuntime(
                session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
    }

    private static void rescope(final Path file, final Set<Path> seen) throws Exception {
        if (!seen.add(file)) {
            return;
        }
        if (seen.size() > 1024) {
            throw new IllegalStateException("TEST_PIN_GRAPH_BOUND");
        }
        final JsonNode data = IntegralArtifactFixtures.read(file);
        replace(file.getParent(), data, seen);
        IntegralArtifactFixtures.save(file, data);
    }

    private static void replace(final Path directory, final JsonNode node, final Set<Path> seen)
            throws Exception {
        if (node instanceof ObjectNode object) {
            if (object.has("source")
                    && object.path("source").asText().equals("SYNTHETIC_CARRIER_A")) {
                object.put("source", "SYNTHETIC_CARRIER_OTHER");
            }
            if (object.has("tenant") && object.path("tenant").asText().equals("SYNTHETIC_WEST")) {
                object.put("tenant", "SYNTHETIC_OTHER");
            }
            if (object.has("file") && object.has("sha256")) {
                final Path file = directory.resolve(object.path("file").asText()).normalize();
                rescope(file, seen);
                object.put("sha256", QualificationJson.sha256(file));
            }
        }
        if (node.isContainerNode()) {
            for (final var child : node) {
                replace(directory, child, seen);
            }
        }
    }
}
