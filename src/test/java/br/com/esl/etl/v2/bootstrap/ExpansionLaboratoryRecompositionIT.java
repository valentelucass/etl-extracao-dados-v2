package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryExecutor.Boundary;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRecomposition;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRecomposition.Slot;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExpansionLaboratoryRecompositionIT {
    @ParameterizedTest
    @EnumSource(Boundary.class)
    void reopensAdaptersAndRecoversSqlSlotsAfterEachBoundary(final Boundary boundary)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var plans = new JdbcExpansionRecomposition(session, CLOCK);
            final var slots = List.of(new Slot(1, DATE, 1, 2, true, 1, 1));
            plans.plan(run, ExecutionMode.BOOTSTRAP, 1, null, slots);
            final var partition = plans.partition(run, ExecutionMode.BOOTSTRAP, 1, 1);
            final var before = plans.stepsBatch(partition.id(), 6);
            final var injected = new AtomicBoolean();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            executor(session, run)
                                    .execute(
                                            ExecutionMode.BOOTSTRAP,
                                            1,
                                            1,
                                            true,
                                            NONE,
                                            point -> {
                                                if (point == boundary
                                                        && injected.compareAndSet(false, true)) {
                                                    throw new IllegalStateException(
                                                            "SYNTHETIC_BOUNDARY_FAILURE");
                                                }
                                            }));
            assertTrue(injected.get());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.expansion_lab_failure f "
                                    + "JOIN recon.expansion_lab_partition p ON p.partition_id=f.partition_id WHERE p.run_id=?",
                            run));
            final var reopened = new JdbcExpansionRecomposition(session, CLOCK);
            reopened.plan(run, ExecutionMode.BOOTSTRAP, 1, null, slots);
            assertEquals(
                    partition.id(), reopened.partition(run, ExecutionMode.BOOTSTRAP, 1, 1).id());
            assertEquals(
                    before.stream().map(JdbcExpansionRecomposition.Step::execution).toList(),
                    reopened.stepsBatch(partition.id(), 6).stream()
                            .map(JdbcExpansionRecomposition.Step::execution)
                            .toList());
            assertEquals(
                    1,
                    executor(session, run)
                            .execute(ExecutionMode.BOOTSTRAP, 1, 1, true, NONE, point -> {})
                            .complete());
            assertEquals(
                    7,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.expansion_lab_step_capture WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=? AND state='RESOLVED'",
                            run));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_materialization_receipt WHERE run_id=?",
                            run));
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice "
                                    + "WHERE run_id=?",
                            run));
            assertEquals(
                    240,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                            run));
            final var queries = new JdbcExpansionQueries(session);
            for (final var vertical : JdbcExpansionQueries.Vertical.values()) {
                assertEquals(4, queries.detailPage(run, vertical, 1, 0, 100).size());
            }
            assertEquals(2, queries.invoiceFactsPage(run, 0, 100).size());
            assertEquals(2, queries.revenueFactsPage(run, 0, 100).size());
        }
    }

    @Test
    void reversePartitionsOverlapBackfillNewRevisionAndReplayKeepGapAndMoney() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var plans = new JdbcExpansionRecomposition(session, CLOCK);
            final var original =
                    List.of(
                            new Slot(1, DATE, 1, 1, false, 1, 1),
                            new Slot(2, DATE.plusDays(1), 2, 1, false, 1, 1));
            plans.plan(run, ExecutionMode.BOOTSTRAP, 1, null, original);
            final var partial =
                    executor(session, run)
                            .execute(ExecutionMode.BOOTSTRAP, 1, 2, true, NONE, point -> {});
            assertEquals(1, partial.complete());
            assertEquals(DATE, partial.firstIncomplete());
            assertEquals(
                    2,
                    executor(session, run)
                            .execute(ExecutionMode.BOOTSTRAP, 1, 1, true, NONE, point -> {})
                            .complete());
            for (final var mode :
                    List.of(
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final int revision = mode == ExecutionMode.REPLAY ? 1 : 2;
                final var slots =
                        mode == ExecutionMode.REPLAY
                                ? original
                                : List.of(
                                        new Slot(1, DATE, 1, 1, false, 2, 2),
                                        new Slot(2, DATE.plusDays(1), 2, 1, false, 2, 2));
                plans.plan(run, mode, revision, mode == ExecutionMode.REPLAY ? 1 : null, slots);
                executor(session, run).execute(mode, revision, 2, true, NONE, point -> {});
                assertNotNull(plans.progress(run, mode, revision).firstIncomplete());
                final var complete =
                        executor(session, run).execute(mode, revision, 1, true, NONE, point -> {});
                assertEquals(2, complete.complete());
                assertNull(complete.firstIncomplete());
            }
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice WHERE run_id=?",
                            run));
            assertEquals(
                    240,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                            run));
            assertEquals(
                    48,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.expansion_lab_step_capture WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark w JOIN "
                                    + "ctl.execution_attempt a "
                                    + "ON a.partition_id=w.last_partition_id JOIN "
                                    + "recon.expansion_lab_step_capture c ON c.execution_id=a.execution_id "
                                    + "WHERE c.run_id=?",
                            run));
        }
    }

    @Test
    void validEmptyCompletesAndUnhydratedDependencyDegradesUntilNewPlan() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var plans = new JdbcExpansionRecomposition(session, CLOCK);
            plans.plan(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    1,
                    null,
                    List.of(new Slot(1, DATE, 1, 0, false, 1, 1)));
            assertEquals(
                    1,
                    executor(session, run)
                            .execute(ExecutionMode.BOOTSTRAP, 1, 1, false, NONE, point -> {})
                            .complete());
            plans.plan(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    2,
                    null,
                    List.of(new Slot(1, DATE, 1, 2, true, 1, 1)));
            assertEquals(
                    1,
                    executor(session, run)
                            .execute(ExecutionMode.BOOTSTRAP, 2, 1, false, NONE, point -> {})
                            .degraded());
            plans.plan(
                    run,
                    ExecutionMode.BACKFILL,
                    1,
                    null,
                    List.of(new Slot(1, DATE, 1, 2, true, 1, 1)));
            assertEquals(
                    1,
                    executor(session, run)
                            .execute(ExecutionMode.BACKFILL, 1, 1, true, NONE, point -> {})
                            .complete());
            assertEquals(
                    240,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                            run));
        }
    }

    @Test
    void divergentRetryAndReplayWithoutCompletedOriginalAreRefused() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var plans = new JdbcExpansionRecomposition(session, CLOCK);
            plans.plan(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    1,
                    null,
                    List.of(new Slot(1, DATE, 1, 1, false, 1, 1)));
            assertThrows(
                    SQLException.class,
                    () ->
                            plans.plan(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    1,
                                    null,
                                    List.of(new Slot(1, DATE, 1, 2, false, 1, 1))));
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            assertThrows(
                    SQLException.class,
                    () ->
                            new JdbcExpansionRecomposition(session, CLOCK)
                                    .plan(
                                            run,
                                            ExecutionMode.REPLAY,
                                            1,
                                            1,
                                            List.of(new Slot(1, DATE, 1, 1, false, 1, 1))));
        }
    }

    private static ExpansionLaboratoryExecutor executor(
            final ColetaTemporalLaboratorySession session, final UUID run) {
        return new ExpansionLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
    }
}
