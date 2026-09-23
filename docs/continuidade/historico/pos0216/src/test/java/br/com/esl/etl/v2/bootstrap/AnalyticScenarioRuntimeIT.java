package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticScenarioRuntimeIT {
    @Test
    void fourModesRecomposeBothDatesBranchesAndReplayWithoutDuplicatingBusinessFacts()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            AnalyticScenarioRuntime.Cycle previous = null;
            int revision = 0;
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final boolean correction =
                        mode == ExecutionMode.BACKFILL || mode == ExecutionMode.REPLAY;
                final var cycle =
                        runtime.capture(
                                run,
                                mode,
                                ++revision,
                                correction,
                                previous,
                                CancellationToken.none());
                assertTrue(cycle.status().complete(), mode + ":" + cycle.status());
                assertEquals(
                        mode == ExecutionMode.BOOTSTRAP
                                ? AnalyticScenarioRuntime.START
                                : AnalyticScenarioRuntime.START.plusDays(1),
                        cycle.status().nextDate());
                assertEquals(
                        cycle.status(),
                        new JdbcAnalyticScenario(session).status(cycle.intent().cycle()));
                assertEquals(4, cycle.freight().ready(), mode + ":" + cycle.freight());
                assertEquals(2, cycle.manifests().ready(), mode + ":" + cycle.manifests());
                assertEquals(
                        4,
                        scalar(
                                session,
                                "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?",
                                run.id()));
                assertEquals(
                        2,
                        scalar(
                                session,
                                "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_current WHERE run_id=?",
                                run.id()));
                assertEquals(
                        240,
                        scalar(
                                session,
                                "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                                run.expansion()));
                if (correction) {
                    assertEquals(
                            0,
                            scalar(
                                    session,
                                    "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_current"
                                            + " WHERE run_id=? AND reference_date='20360401'",
                                    run.id()));
                    assertEquals(
                            2,
                            scalar(
                                    session,
                                    "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_current"
                                            + " WHERE run_id=? AND reference_date='20360402'",
                                    run.id()));
                }
                if (mode == ExecutionMode.REPLAY) {
                    assertEquals(0, cycle.freight().inserts() + cycle.freight().updates());
                    assertEquals(0, cycle.manifests().inserts() + cycle.manifests().updates());
                    assertEquals(0, cycle.collectors().inserts());
                    new JdbcAnalyticScenario(session)
                            .verifyCollectorReplay(
                                    previous.intent().mat02(), cycle.intent().mat02());
                }
                previous = cycle;
                new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), 2);
            }
        }
    }

    @Test
    void confirmedAbsenceMakesTheNineteenthPositiveContractAndReappearanceClearsIt()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var sweep =
                    new LocalAnalyticCollectionSweep(
                            session,
                            run.id(),
                            run.relational(),
                            AnalyticScenarioRuntime.policy(run.pageSize()),
                            AnalyticScenarioRuntime.LOGICAL_CLOCK,
                            Clock.systemUTC());
            final var omitted =
                    new SyntheticCollectionSnapshot(
                            run.id(), AnalyticScenarioRuntime.START, 2, true);
            sweep.observe(omitted, UUID.randomUUID(), CancellationToken.none());
            final var queries = new JdbcAnalyticQueries(session);
            assertEquals(
                    0,
                    queries.read(
                                    run.id(),
                                    AnalyticSqlContract.SQL_04,
                                    2,
                                    20,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
            sweep.observe(omitted, UUID.randomUUID(), CancellationToken.none());
            assertEquals(
                    1,
                    queries.read(
                                    run.id(),
                                    AnalyticSqlContract.SQL_04,
                                    2,
                                    20,
                                    CancellationToken.none(),
                                    row -> assertEquals(13, row.values().size()))
                            .rows());
            assertEquals(
                    4,
                    queries.read(
                                    run.id(),
                                    AnalyticSqlContract.SQL_06,
                                    2,
                                    20,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
            sweep.observe(
                    new SyntheticCollectionSnapshot(
                            run.id(), AnalyticScenarioRuntime.START, 2, false),
                    UUID.randomUUID(),
                    CancellationToken.none());
            assertEquals(
                    0,
                    queries.read(
                                    run.id(),
                                    AnalyticSqlContract.SQL_04,
                                    2,
                                    20,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
        }
    }

    @Test
    void elevenInputsHydrationFiveFactsAndEighteenPositiveQueriesShareOneRollbackSession()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var result =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertEquals(1, result.expanded().hydrated());
            assertTrue(result.status().complete(), result.status().toString());
            assertEquals(
                    result.status(),
                    new JdbcAnalyticScenario(session)
                            .complete(
                                    result.intent().cycle(),
                                    result.sources(),
                                    null,
                                    CancellationToken.none()));
            assertEquals(
                    19,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_query_receipt q"
                                    + " JOIN ctl.analytic_scenario_cycle c ON c.cycle_id=q.cycle_id WHERE c.run_id=?",
                            run.id()));
            assertEquals(4, result.freight().ready(), result.freight().toString());
            assertTrue(result.collectors().ready() > 0, result.collectors().toString());
            assertEquals(2, result.manifests().ready(), result.manifests().toString());
            final var queries = new JdbcAnalyticQueries(session);
            for (final var contract : AnalyticSqlContract.values()) {
                final var receipt =
                        queries.read(
                                run.id(),
                                contract,
                                2,
                                4096,
                                CancellationToken.none(),
                                row -> assertEquals(contract.columns(), row.values().size()));
                if (contract == AnalyticSqlContract.SQL_04) {
                    assertEquals(0, receipt.rows());
                } else {
                    assertTrue(receipt.rows() > 0, contract + ":" + receipt);
                }
            }
            assertEquals(
                    240,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                            run.expansion()));
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice WHERE r"
                                    + "un_id=?",
                            run.expansion()));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_freight p JOIN pub.analytic_lab_manif"
                                    + "ests f"
                                    + " ON f.observation_id=p.observation_id WHERE f.run_id=?",
                            run.id()));
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_freight_path p JOIN pub.analytic_lab_"
                                    + "manifests f"
                                    + " ON f.observation_id=p.observation_id WHERE f.run_id=?",
                            run.id()));
        }
    }
}
