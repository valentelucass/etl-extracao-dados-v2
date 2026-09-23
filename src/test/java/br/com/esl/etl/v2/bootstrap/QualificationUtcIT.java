package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationUtcIT {
    @Test
    @Timeout(240)
    void explicitUtcParametersAndLeaseReadPreserveTheDeclaredInstant() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = new AnalyticScenarioRuntime(session, Clock.systemUTC()).start(2, 2);
            new AnalyticExpansionCapture(
                            session,
                            run.expansion(),
                            AnalyticScenarioRuntime.LOGICAL_CLOCK,
                            Clock.systemUTC())
                    .capture(ExecutionMode.BOOTSTRAP, 1, 2, false, CancellationToken.none());
            final var now = AnalyticScenarioRuntime.LOGICAL_CLOCK.instant();
            final var queue =
                    new JdbcExpansionRelations(session, AnalyticScenarioRuntime.LOGICAL_CLOCK);
            final var claims = queue.claimBatch(run.expansion(), UUID.randomUUID(), 1, 45);
            assertEquals(1, claims.size());
            assertEquals(now.plusSeconds(45), claims.get(0).leaseUntil());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    """
                    SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture
                    WHERE run_id=? AND created_at=? AND sealed_at=?
                    """)) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.expansion().toString());
                final var utc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
                sql.setObject(2, utc);
                sql.setObject(3, utc);
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(4, rows.getLong(1));
                }
            }
        }
    }
}
