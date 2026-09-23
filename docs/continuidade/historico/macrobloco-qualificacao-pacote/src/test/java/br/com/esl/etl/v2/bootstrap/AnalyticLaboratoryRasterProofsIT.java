package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.WINDOW;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.ZONE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryRasterProofsIT {
    @Test
    void allFiftyOneDeclarationsReachTypedSqlAndThirtySevenBusinessColumns() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final var capture =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            new RasterWindow(DATE, DATE.plusDays(3)),
                            AnalyticRasterFixtures.source(1, 1),
                            10,
                            100,
                            CancellationToken.none());
            assertTrue(capture.receipt().complete());
            assertEquals(2, capture.receipt().applied());
            assertEquals(4, capture.receipt().duplicates());
            assertEquals(29, typedFields(session, run, "trip"));
            assertEquals(20, typedFields(session, run, "stop"));
            assertEquals(
                    3,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.analytic_raster_structure s JOIN stg.analytic_raster_trip t"
                                    + " ON t.observation_id=s.observation_id JOIN ctl.analytic_raster_capture c"
                                    + " ON c.capture_id=t.capture_id WHERE c.run_id=? AND route_presence='VALUE'"
                                    + " AND stops_presence='VALUE'",
                            run));
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_13 WHERE run_id=?")) {
                statement.setQueryTimeout(10);
                statement.setString(1, run.toString());
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    for (int column = 1; column <= 37; column++) {
                        assertNotNull(
                                row.getObject(column), row.getMetaData().getColumnName(column));
                    }
                    assertEquals("01:30", row.getString("TRANSIT TIME"));
                    assertEquals("3509502", row.getString("DESTINO - SM"));
                    assertFalse(row.next());
                }
            }
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_terminal_window t JOIN ctl.analytic_rast"
                                    + "er_capture c"
                                    + " ON c.capture_id=t.capture_id WHERE c.run_id=? AND t.receipt=c.receipt"
                                    + " AND t.expected_roots=3 AND t.expected_stops=3",
                            run));
        }
    }

    @Test
    void replayAndStaleRefreshExtractionWithSeparateObservationLineage() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run)
                    .capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            WINDOW,
                            AnalyticLaboratoryRasterIT.source(
                                    "[" + AnalyticLaboratoryRasterIT.ROW + "]", 1, 1, 2, true),
                            10,
                            100,
                            CancellationToken.none());
            final long original =
                    scalar(
                            session,
                            "SELECT observation_id FROM core.analytic_raster_trip WHERE run_id=?",
                            run);
            final var later = Clock.offset(CLOCK, Duration.ofSeconds(60));
            final var replay =
                    new LocalRasterRuntime(session, later, ZONE)
                            .capture(
                                    run,
                                    ExecutionMode.REPLAY,
                                    WINDOW,
                                    AnalyticLaboratoryRasterIT.source(
                                            "[" + AnalyticLaboratoryRasterIT.ROW + "]",
                                            1,
                                            1,
                                            2,
                                            true),
                                    10,
                                    100,
                                    CancellationToken.none());
            assertEquals(2, replay.receipt().noops());
            assertEquals(
                    original,
                    scalar(
                            session,
                            "SELECT observation_id FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_13 WHERE run_id=?")) {
                statement.setQueryTimeout(10);
                statement.setString(1, run.toString());
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(later.instant(), row.getTimestamp("Data de extracao").toInstant());
                    assertTrue(
                            row.getLong("trip_last_observation") > row.getLong("trip_observation"));
                    assertTrue(
                            row.getLong("stop_last_observation") > row.getLong("stop_observation"));
                }
            }
            final var stale =
                    new LocalRasterRuntime(
                                    session, Clock.offset(CLOCK, Duration.ofSeconds(120)), ZONE)
                            .capture(
                                    run,
                                    ExecutionMode.BACKFILL,
                                    WINDOW,
                                    AnalyticLaboratoryRasterIT.source(
                                            "["
                                                    + AnalyticLaboratoryRasterIT.ROW.replace(
                                                            ":90", ":91")
                                                    + "]",
                                            1,
                                            1,
                                            1,
                                            true),
                                    10,
                                    100,
                                    CancellationToken.none());
            assertEquals(2, stale.receipt().stale());
            assertEquals(
                    90,
                    scalar(
                            session,
                            "SELECT tempo_total_viagem FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip_history WHERE run_id=?",
                            run));
        }
    }

    @Test
    void splitCapturesProveEveryLeafWhileMinimumWindowCapRollsBack() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final var result =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            new RasterWindow(DATE, DATE.plusDays(3)),
                            AnalyticRasterFixtures.source(170, 1),
                            10,
                            2000,
                            CancellationToken.none());
            assertEquals(3, result.calls());
            assertEquals(340, result.receipt().applied());
            assertEquals(680, result.receipt().duplicates());
            assertTrue(result.maximumBatch() <= 16);
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_terminal_window t JOIN ctl.analytic_rast"
                                    + "er_capture c"
                                    + " ON c.capture_id=t.capture_id WHERE c.run_id=?",
                            run));
            final var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    runtime.capture(
                                            run,
                                            ExecutionMode.BACKFILL,
                                            new RasterWindow(DATE, DATE.plusDays(3)),
                                            AnalyticRasterFixtures.source(501, 1),
                                            10,
                                            10000,
                                            CancellationToken.none()));
            assertEquals("RAS_CAP_MINIMUM_WINDOW", failure.getMessage());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=?",
                            run));
        }
    }

    @Test
    void emptyIsExplicitAndUnprovedGappedAndDivergentSealsCannotApply() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final var empty =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            new RasterWindow(DATE, DATE.plusDays(3)),
                            AnalyticRasterFixtures.source(0, 1),
                            10,
                            100,
                            CancellationToken.none());
            assertTrue(empty.receipt().complete());
            assertEquals(0, empty.receipt().trips());
            final var database = new JdbcRasterLaboratory(session, CLOCK);
            final UUID bad = UUID.randomUUID();
            database.begin(run, bad, DATE, DATE.plusDays(3), ExecutionMode.BACKFILL);
            assertEquals(
                    53804,
                    assertThrows(SQLException.class, () -> database.seal(bad, 0, 0, 1))
                            .getErrorCode());
            database.terminal(
                    bad,
                    1,
                    new RasterGateway.Terminal(
                            WINDOW,
                            0,
                            0,
                            "synthetic-gap-proof",
                            JdbcRasterLaboratory.SOURCE,
                            JdbcRasterLaboratory.TENANT,
                            JdbcRasterLaboratory.VERSION));
            assertEquals(
                    53804,
                    assertThrows(SQLException.class, () -> database.seal(bad, 0, 0, 1))
                            .getErrorCode());
            assertEquals(
                    53510,
                    assertThrows(SQLException.class, () -> database.apply(run, bad))
                            .getErrorCode());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=? AND state='APPLIED'",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
        }
    }

    private static int typedFields(
            final ColetaTemporalLaboratorySession session, final UUID run, final String entity)
            throws SQLException {
        if (!entity.equals("trip") && !entity.equals("stop")) {
            throw new IllegalArgumentException("TEST_TABLE");
        }
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT * FROM core.analytic_raster_"
                                        + entity
                                        + " WHERE run_id=?")) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                int fields = 0;
                for (int column = 1; column <= row.getMetaData().getColumnCount(); column++) {
                    final String name = row.getMetaData().getColumnName(column);
                    if (name.endsWith("_p")) {
                        fields++;
                        final String base = name.substring(0, name.length() - 2);
                        assertEquals("VALUE", row.getString(column), name);
                        assertNotNull(row.getObject(base), base);
                        assertNotNull(row.getString(base + "_raw"), base);
                        if (base.startsWith("data_hora")) {
                            assertEquals(123456789, row.getInt(base + "_nano"));
                            assertEquals(-10800, row.getInt(base + "_offset"));
                        }
                    }
                }
                assertFalse(row.next());
                return fields;
            }
        }
    }
}
