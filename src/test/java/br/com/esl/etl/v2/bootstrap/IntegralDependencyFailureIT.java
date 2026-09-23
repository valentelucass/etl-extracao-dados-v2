package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IntegralDependencyFailureIT {
    @TempDir Path folder;

    @ParameterizedTest
    @EnumSource(
            value = AnalyticScenarioRuntime.Fault.class,
            names = {"RASTER_INCOMPLETE"})
    @Timeout(180)
    void dependentRefusalPreservesIndependentCapturesWithoutCompletingTheScenario(
            final AnalyticScenarioRuntime.Fault fault) throws Exception {
        final var input = declared();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final var run = runtime.start(2, 2, fault);
            final var cycle =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            input.revision(),
                            false,
                            null,
                            CancellationToken.none());
            assertFalse(cycle.status().complete());
            assertEquals("DEGRADED", cycle.status().state());
            assertEquals(fault.name(), cycle.status().failure());
            assertEquals(input.start(), cycle.status().nextDate());
            assertEquals(
                    4,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_06 WHERE run_id=?"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_cycle WHERE run_id=?"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=?"));
            assertEquals(0, session.openControlledStatements());
        }
    }

    @Test
    void implicitFixtureFaultsAreRefusedBeforeAnyStatement() throws Exception {
        final var input = declared();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final long before = session.preparedStatements();
            for (final var fault :
                    new AnalyticScenarioRuntime.Fault[] {
                        AnalyticScenarioRuntime.Fault.COLLECTION_SNAPSHOT_INVALID,
                        AnalyticScenarioRuntime.Fault.FINANCIAL_REFERENCE_MISSING,
                        AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING
                    }) {
                final var failure =
                        assertThrows(
                                IllegalArgumentException.class, () -> runtime.start(2, 2, fault));
                assertEquals("INTEGRAL_FAULT_REQUIRES_DECLARED_INPUT", failure.getMessage());
            }
            assertEquals(before, session.preparedStatements());
        }
    }

    @Test
    @Timeout(180)
    void failedPhysicalMaterializationRetryCannotReplacePriorFactsOrIndependentOutputs()
            throws Exception {
        final var input = declared();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final var run = runtime.start(2, 2);
            final var cycle =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            input.revision(),
                            false,
                            null,
                            CancellationToken.none());
            assertTrue(cycle.status().complete());
            final var changed =
                    new AnalyticMaterializationRequest(
                            run.id(),
                            cycle.intent().mat01(),
                            999,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            input.start(),
                            input.end());
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    new JdbcAnalyticMaterializations(session, Clock.systemUTC())
                                            .freight(changed, CancellationToken.none()));
            assertEquals(53584, failure.getErrorCode(), failure::getMessage);
            IntegralArtifactReplayIT.exact(
                    IntegralArtifactReplayIT.verifier(folder)
                            .verifyIntegral(
                                    session,
                                    run,
                                    cycle,
                                    input,
                                    java.time.Instant.now().minusSeconds(180),
                                    CancellationToken.none()));
            assertEquals(0, session.openControlledStatements());
        }
    }

    private DeclaredIntegralInputs declared() throws Exception {
        return new DeclaredIntegralInputs(
                PinnedLocalJson.open(IntegralArtifactFixtures.write(folder, false, 2), 16384),
                CancellationToken.none());
    }
}
