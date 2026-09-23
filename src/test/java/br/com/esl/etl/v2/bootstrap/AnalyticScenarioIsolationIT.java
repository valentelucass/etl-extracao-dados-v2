package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnalyticScenarioIsolationIT {
    @ParameterizedTest
    @EnumSource(
            value = AnalyticScenarioRuntime.Fault.class,
            names = "NONE",
            mode = EnumSource.Mode.EXCLUDE)
    void dependentFailureKeepsCapIndependentAndNeverPublishesGlobalSuccess(
            final AnalyticScenarioRuntime.Fault fault) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2, fault);
            final var cycle =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertFalse(cycle.status().complete());
            assertEquals("DEGRADED", cycle.status().state());
            assertEquals(fault.name(), cycle.status().failure());
            assertEquals(AnalyticScenarioRuntime.START, cycle.status().nextDate());
            final var queries = new JdbcAnalyticQueries(session);
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
            assertEquals(
                    2,
                    queries.read(
                                    run.id(),
                                    AnalyticSqlContract.SQL_05,
                                    2,
                                    20,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
            if (fault == AnalyticScenarioRuntime.Fault.RASTER_INCOMPLETE) {
                assertEquals(
                        0,
                        scalar(
                                session,
                                "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=?",
                                run.id()));
                assertEquals(
                        0,
                        queries.read(
                                        run.id(),
                                        AnalyticSqlContract.SQL_13,
                                        2,
                                        20,
                                        CancellationToken.none(),
                                        row -> {})
                                .rows());
                assertEquals(2, cycle.manifests().ready());
            }
            if (fault == AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING) {
                assertEquals(0, cycle.manifests().ready());
                assertEquals(2, cycle.manifests().blocked());
                assertTrue(cycle.collectors().ready() > 0);
            }
            if (fault == AnalyticScenarioRuntime.Fault.FINANCIAL_REFERENCE_MISSING) {
                assertEquals(
                        2,
                        scalar(
                                session,
                                "SELECT COUNT_BIG(*) FROM mart.expansion_lab_revenue WHERE run_id=?"
                                        + " AND disposition='REFERENCE_MISSING'",
                                run.expansion()));
                assertEquals(2, cycle.manifests().ready());
            }
            if (fault == AnalyticScenarioRuntime.Fault.COLLECTION_SNAPSHOT_INVALID) {
                assertEquals(
                        0,
                        scalar(
                                session,
                                "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_application a"
                                        + " JOIN recon.analytic_collection_sweep_cycle c ON c.cycle_id=a.cycle_id WHERE c.run_id=?",
                                run.id()));
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
                assertEquals(
                        2,
                        queries.read(
                                        run.id(),
                                        AnalyticSqlContract.SQL_03,
                                        2,
                                        20,
                                        CancellationToken.none(),
                                        row -> {})
                                .rows());
            }
            assertEquals(1, scalar(session, "SELECT XACT_STATE()"));
        }
    }
}
