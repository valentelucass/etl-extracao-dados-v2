package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationCancellationIT {
    @Test
    @Timeout(30)
    void cancellationReachesBlockingDriverAndClosesObservableResources() throws Exception {
        final var config =
                QualificationConfiguration.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/config.synthetic.json"));
        try (var session = ColetaTemporalLaboratorySession.open(config.jdbcUrl())) {
            session.controlStatements(config.querySeconds());
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            runtime.start(2, 2);
            assertEquals(0, session.openControlledStatements());
            final var worker = Executors.newSingleThreadScheduledExecutor();
            try (var connection = session.getConnection();
                    var query = connection.createStatement()) {
                assertEquals(60, query.getQueryTimeout());
                final var cancelled =
                        worker.schedule(
                                session::cancelActiveStatements, 750, TimeUnit.MILLISECONDS);
                final long start = System.nanoTime();
                assertThrows(
                        SQLException.class,
                        () -> query.execute("WAITFOR DELAY '00:00:10'; SELECT 1"));
                assertTrue(cancelled.get(5, TimeUnit.SECONDS) >= 1);
                assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
            } finally {
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            assertEquals(0, session.openControlledStatements());
            session.rollback();
            try (var connection = session.getConnection();
                    var query = connection.createStatement();
                    var row = query.executeQuery("SELECT XACT_STATE()")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
    }

    @Test
    void trackedStatementsKeepVendorTvpInterfaceAndReleasePerBatch() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60);
            for (int index = 0; index < 20; index++) {
                try (var connection = session.getConnection();
                        var query = connection.prepareStatement("SELECT 1")) {
                    assertInstanceOf(SQLServerPreparedStatement.class, query);
                    assertEquals(1, session.openControlledStatements());
                    try (var row = query.executeQuery()) {
                        assertTrue(row.next());
                    }
                }
                assertEquals(0, session.openControlledStatements());
            }
            assertEquals(0, session.cancelActiveStatements());
        }
    }

    @Test
    void explicitConfigurationStillUsesOriginalTargetAndCommitFences() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaTemporalLaboratorySession.open(
                                "jdbc:sqlserver://forbidden;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaTemporalLaboratorySession.open(
                                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA;integratedSecurity=true"));
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment();
                var connection = session.getConnection()) {
            assertThrows(SQLException.class, () -> connection.setAutoCommit(true));
            assertThrows(SQLException.class, () -> connection.unwrap(java.sql.Connection.class));
            connection.commit();
            assertEquals(1, session.suppressedCommits());
            assertThrows(IllegalArgumentException.class, () -> session.controlStatements(0));
        }
    }
}
