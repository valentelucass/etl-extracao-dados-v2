package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationWindowIT {
    @Test
    @Timeout(240)
    void plannedLookbackChangesFiveSqlScopesWhileEveryFactModePreservesTheSourceFrontier()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var executor = new QualificationWindowExecutor(session, Clock.systemUTC());
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final var shortWindow =
                        executor.execute(run, item(mode, 0, List.of()), CancellationToken.none());
                final var overlap =
                        executor.execute(
                                run, item(mode, 86400, List.of()), CancellationToken.none());
                assertEquals(QualificationGate.State.PASS_LOCAL, shortWindow.gate().state());
                assertEquals(QualificationGate.State.PASS_LOCAL, overlap.gate().state());
                assertEquals(5, shortWindow.applied().size());
                assertEquals(5, overlap.applied().size());
                assertTrue(
                        shortWindow.applied().stream()
                                .allMatch(row -> row.start().equals(LocalDate.of(2036, 4, 2))));
                assertTrue(
                        overlap.applied().stream()
                                .allMatch(row -> row.start().equals(LocalDate.of(2036, 4, 1))));
                assertEquals(0, shortWindow.applied().get(4).candidates(), mode.name());
                assertEquals(2, overlap.applied().get(4).candidates(), mode.name());
                assertEquals(AnalyticScenarioRuntime.START, overlap.sourceAfter());
                assertEquals(shortWindow.sourceBefore(), shortWindow.sourceAfter());
            }
            final var blackout =
                    executor.execute(
                            run,
                            item(ExecutionMode.INCREMENTAL, 0, List.of(LocalDate.of(2036, 4, 2))),
                            CancellationToken.none());
            assertEquals(QualificationGate.State.BLOCKED_DEPENDENCY, blackout.gate().state());
            assertTrue(blackout.applied().isEmpty());
            new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), 2);
        }
    }

    @Test
    @Timeout(240)
    void lateSourceCorrectionMovesDateAndBranchAndThePlannedRecutConsumesTheNewDay()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var initial =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var correction =
                    runtime.capture(
                            run,
                            ExecutionMode.BACKFILL,
                            2,
                            true,
                            initial,
                            CancellationToken.none());
            assertTrue(correction.status().complete());
            final var result =
                    new QualificationWindowExecutor(session, Clock.systemUTC())
                            .execute(
                                    run,
                                    item(ExecutionMode.BACKFILL, 0, List.of()),
                                    CancellationToken.none());
            assertEquals(QualificationGate.State.PASS_LOCAL, result.gate().state());
            assertEquals(2, result.applied().get(4).candidates());
            assertEquals(AnalyticScenarioRuntime.START, result.sourceAfter());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT COUNT_BIG(*),MIN(reference_date),MAX(reference_date)"
                                            + " FROM mart.analytic_manifest_current WHERE run_id=?")) {
                sql.setQueryTimeout(15);
                sql.setString(1, run.id().toString());
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getLong(1));
                    assertEquals(LocalDate.of(2036, 4, 2), rows.getObject(2, LocalDate.class));
                    assertEquals(LocalDate.of(2036, 4, 2), rows.getObject(3, LocalDate.class));
                }
            }
            new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), 2);
        }
    }

    private static QualificationCampaign.Case item(
            final ExecutionMode mode, final int overlap, final List<LocalDate> blackouts) {
        return new QualificationCampaign.Case(
                "window",
                "facts",
                List.of(),
                QualificationCampaign.Action.RECOMPOSE,
                mode,
                QualificationCampaign.Fault.NONE,
                QualificationCampaign.Barrier.NONE,
                List.of(AnalyticSqlContract.values()),
                Instant.parse("2036-04-03T12:00:00Z"),
                LocalDate.of(2036, 4, 2),
                LocalDate.of(2036, 4, 3),
                AnalyticScenarioRuntime.ZONE,
                overlap,
                86400,
                1,
                blackouts,
                QualificationGate.State.PASS_LOCAL);
    }
}
