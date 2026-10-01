package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryObservationModesIT {
    @Test
    void usersObserveFourCyclesThroughTheirExistingSnapshotModesAndQualityEngine()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var runtime = new LocalAnalyticUsersRuntime(session, Clock.systemUTC());
            UUID first = null;
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final var execution = UUID.randomUUID();
                final var result =
                        runtime.capture(
                                f.run(),
                                execution,
                                DATE,
                                LocalAnalyticUsersRuntime.observationMode(mode),
                                mode == ExecutionMode.REPLAY ? first : null,
                                AnalyticLaboratoryUsersIT.users(
                                        f.run(), 2, mode == ExecutionMode.INCREMENTAL),
                                CancellationToken.none());
                assertEquals(2, result.insertedRows() + result.updatedRows() + result.noopRows());
                if (first == null) {
                    first = execution;
                }
            }
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_execution_source s JOIN ctl.execution_attem"
                                    + "pt e ON e.execution_id=s.execution_id WHERE s.run_id=? AND s.entity='USUARIO' AND e.c"
                                    + "urrent_state='PUBLISHED'",
                            f.run()));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            f.run()));
        }
    }

    @Test
    void quotesPublishFourModesWithExplicitTariffAndIdempotentBusinessSnapshot() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var tariff =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(f.run(), 1, DATE, DATE.plusDays(3));
            final var runtime = new LocalAnalyticQuotesRuntime(session, Clock.systemUTC());
            final var initial = DATE.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            final var control =
                    new br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane(session);
            RuntimePhaseEvidence.sql(
                    RuntimePhaseEvidence.Phase.SOURCE_REGISTER,
                    () -> {
                        control.registerSource(
                                new ControlPlaneSource(
                                        "LOCAL_V2", "DATA_EXPORT", java.time.Instant.now()));
                        return null;
                    });
            RuntimePhaseEvidence.sql(
                    RuntimePhaseEvidence.Phase.FRONTIER_REGISTER,
                    () -> {
                        control.registerIncrementalFrontier(
                                new br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey(
                                        "LOCAL_SHADOW",
                                        "LOCAL_V2",
                                        "LOCAL_V2",
                                        "cotacoes",
                                        ExecutionMode.INCREMENTAL,
                                        initial,
                                        DATE.plusDays(1)
                                                .atStartOfDay()
                                                .toInstant(java.time.ZoneOffset.UTC)),
                                initial,
                                java.time.Instant.now());
                        return null;
                    });
            UUID first = null;
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final var execution = UUID.randomUUID();
                runtime.capture(
                        f.run(),
                        execution,
                        DATE,
                        mode,
                        mode == ExecutionMode.REPLAY ? first : null,
                        1,
                        tariff.release(),
                        2,
                        AnalyticQuotesFixtures.source(1, 1, 2),
                        CancellationToken.none());
                if (first == null) {
                    first = execution;
                }
            }
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_execution_source s JOIN ctl.execution_attem"
                                    + "pt e ON e.execution_id=s.execution_id WHERE s.run_id=? AND s.entity='COT' AND e.curre"
                                    + "nt_state='PUBLISHED'",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_quote_snapshot WHERE run_id=?",
                            f.run()));
        }
    }
}
