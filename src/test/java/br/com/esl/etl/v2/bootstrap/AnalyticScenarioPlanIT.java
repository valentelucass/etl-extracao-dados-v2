package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static br.com.esl.etl.v2.bootstrap.AnalyticScenarioRuntime.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AnalyticScenarioPlanIT {
    @Test
    @Timeout(180)
    void immutableIntentAndReceiptsRejectDivergenceAndMissingModeOrWindowEvidence()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var cycle =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var repository = new JdbcAnalyticScenario(session);
            assertEquals(
                    cycle.intent().cycle(),
                    begin(
                                    repository,
                                    run.id(),
                                    ExecutionMode.BOOTSTRAP,
                                    1,
                                    START,
                                    START.plusDays(1))
                            .cycle());
            final var divergent =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    begin(
                                            repository,
                                            run.id(),
                                            ExecutionMode.BOOTSTRAP,
                                            1,
                                            START,
                                            AnalyticScenarioRuntime.END));
            assertEquals(53822, divergent.getErrorCode());
            assertEquals(
                    cycle.status(),
                    repository.complete(
                            cycle.intent().cycle(),
                            cycle.sources(),
                            null,
                            CancellationToken.none()));
            final var changed = replace(cycle.sources(), "CAP", UUID.randomUUID());
            assertEquals(
                    53822,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            repository.complete(
                                                    cycle.intent().cycle(),
                                                    changed,
                                                    null,
                                                    CancellationToken.none()))
                            .getErrorCode());
            assertEquals(1, scalar(session, "SELECT XACT_STATE()"));
            int revision = 10;
            for (final var mode :
                    List.of(
                            ExecutionMode.BACKFILL,
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL)) {
                final var intent =
                        begin(
                                repository,
                                run.id(),
                                mode,
                                ++revision,
                                START,
                                mode == ExecutionMode.INCREMENTAL
                                        ? START.plusDays(2)
                                        : START.plusDays(1));
                final var sources =
                        mode == ExecutionMode.BOOTSTRAP
                                ? replace(cycle.sources(), "RASTER", null)
                                : cycle.sources();
                final var status =
                        repository.complete(
                                intent.cycle(), sources, null, CancellationToken.none());
                assertFalse(status.complete());
                assertEquals("SOURCE_UNPROVEN", status.failure());
                assertEquals(START, status.nextDate());
                assertEquals(status, new JdbcAnalyticScenario(session).status(intent.cycle()));
            }
            // Correct mode and actual receipts still cannot certify a wider input partition.
            final var wider =
                    begin(
                            repository,
                            run.id(),
                            ExecutionMode.BOOTSTRAP,
                            20,
                            START,
                            START.plusDays(2));
            assertEquals(
                    "SOURCE_UNPROVEN",
                    repository
                            .complete(
                                    wider.cycle(), cycle.sources(), null, CancellationToken.none())
                            .failure());
            assertEquals(
                    19,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_query_receipt WHERE cycle_id=?",
                            cycle.intent().cycle()));
        }
    }

    @Test
    @Timeout(180)
    void alreadyConsumedIncrementalPartitionCannotAdvanceAgainWithValidModeReceipts()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var boot =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var increment =
                    runtime.capture(
                            run,
                            ExecutionMode.INCREMENTAL,
                            2,
                            false,
                            boot,
                            CancellationToken.none());
            assertTrue(increment.status().complete());
            assertEquals(START.plusDays(1), increment.status().nextDate());
            final var repository = new JdbcAnalyticScenario(session);
            final var retry =
                    begin(
                            repository,
                            run.id(),
                            ExecutionMode.INCREMENTAL,
                            3,
                            START,
                            START.plusDays(1));
            final var status =
                    repository.complete(
                            retry.cycle(), increment.sources(), null, CancellationToken.none());
            assertEquals("SOURCE_UNPROVEN", status.failure());
            assertEquals(START.plusDays(1), status.nextDate());
            new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), 2);
        }
    }

    @Test
    @Timeout(180)
    void actualPlanProcedureContendsAcrossSessionsAndFullConsumerRecoversSameRunAfterRollback()
            throws Exception {
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment();
                var second = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertNotEquals(scalar(first, "SELECT @@SPID"), scalar(second, "SELECT @@SPID"));
            final var runtime = new AnalyticScenarioRuntime(first, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var complete =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertTrue(complete.status().complete());
            try (var connection = second.getConnection()) {
                connection.setSavepoint();
            }
            final var repository = new JdbcAnalyticScenario(second);
            assertEquals(
                    53502,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            begin(
                                                    repository,
                                                    run.id(),
                                                    ExecutionMode.BACKFILL,
                                                    2,
                                                    START,
                                                    START.plusDays(1)))
                            .getErrorCode());
            assertEquals(1, scalar(second, "SELECT XACT_STATE()"));
            first.rollback();
            final var recovering = new AnalyticScenarioRuntime(second, Clock.systemUTC());
            final var restored =
                    recovering.start(run.id(), 2, 2, AnalyticScenarioRuntime.Fault.NONE);
            final var cycle =
                    recovering.capture(
                            restored,
                            ExecutionMode.BOOTSTRAP,
                            1,
                            false,
                            null,
                            CancellationToken.none());
            assertTrue(cycle.status().complete());
            repository.verifyFixtureFacts(restored.id(), 2);
            assertEquals(
                    19,
                    scalar(
                            second,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_query_receipt WHERE cycle_id=?",
                            cycle.intent().cycle()));
            second.rollback();
            assertEquals(
                    0,
                    scalar(
                            second,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?",
                            run.id()));
        }
    }

    private static JdbcAnalyticScenario.Intent begin(
            final JdbcAnalyticScenario repository,
            final UUID run,
            final ExecutionMode mode,
            final int revision,
            final LocalDate start,
            final LocalDate end)
            throws SQLException {
        return repository.begin(run, mode, revision, start, end, null, 2, 2, 2, false);
    }

    private static List<JdbcAnalyticScenario.Source> replace(
            final List<JdbcAnalyticScenario.Source> sources,
            final String entity,
            final UUID execution) {
        final var result = new ArrayList<JdbcAnalyticScenario.Source>(11);
        for (final var source : sources) {
            result.add(
                    source.entity().equals(entity)
                            ? new JdbcAnalyticScenario.Source(entity, execution)
                            : source);
        }
        return List.copyOf(result);
    }
}
