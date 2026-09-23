package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryRasterIT {
    static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2036-04-15T12:00:00Z"), ZoneOffset.UTC);
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final RasterWindow WINDOW = new RasterWindow(DATE, DATE.plusDays(1));
    static final String ROW =
            "{\"CodSolicitacao\":\"1\",\"Sequencial\":2,\"CodFilial\":3,"
                    + "\"PlacaVeiculo\":\"SYNTHETIC-A\",\"TempoTotalViagem\":90,"
                    + "\"DataHoraPrevIni\":\"2036-04-01T08:00:00.123456789-03:00\","
                    + "\"DataHoraRealFim\":\"1900-01-01 00:00:00\","
                    + "\"Rota\":{\"CodRota\":1,\"Descricao\":\"ORIGEM/SP ATE DESTINO/RJ/BRASIL\"},"
                    + "\"ColetasEntregas\":[{\"Tipo\":\"Entrega\",\"Latitude\":12.50000001}]}";

    @Test
    void duplicateObservationsApplyParentsAndStopsOnceAndReplayIsNoop() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final long before =
                    scalar(session, "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip");
            final UUID run = UUID.randomUUID();
            final var runtime = start(session, run);
            final var first =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            WINDOW,
                            source("[" + ROW + "," + ROW + "]", 2, 2, 1, true),
                            10,
                            100,
                            CancellationToken.none());
            assertEquals(2, first.receipt().applied());
            assertEquals(2, first.receipt().duplicates());
            assertTrue(first.receipt().complete());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
            assertEquals(
                    123456789,
                    scalar(
                            session,
                            "SELECT data_hora_prev_ini_nano FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT data_hora_real_fim_sentinel FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=? AND ordem_p='ABSENT"
                                    + "' AND ordem IS NULL",
                            run));
            final var replay =
                    runtime.capture(
                            run,
                            ExecutionMode.REPLAY,
                            WINDOW,
                            source("[" + ROW + "]", 1, 1, 1, true),
                            10,
                            100,
                            CancellationToken.none());
            assertEquals(2, replay.receipt().noops());
            assertEquals(0, replay.receipt().applied());
            assertEquals(
                    first.receipt(),
                    new JdbcRasterLaboratory(session, CLOCK).apply(run, first.capture()));
            session.rollback();
            assertEquals(
                    before, scalar(session, "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip"));
        }
    }

    @Test
    void partialUpdatesKeepKnownAttributesAndChildrenAndExplicitNullClears() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = start(session, run);
            runtime.capture(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    WINDOW,
                    source("[" + ROW + "]", 1, 1, 1, true),
                    10,
                    100,
                    CancellationToken.none());
            runtime.capture(
                    run,
                    ExecutionMode.BACKFILL,
                    WINDOW,
                    source(
                            "[{\"CodSolicitacao\":\"1\",\"StatusViagem\":\"closed\"}]",
                            1,
                            0,
                            2,
                            true),
                    10,
                    100,
                    CancellationToken.none());
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
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
            runtime.capture(
                    run,
                    ExecutionMode.INCREMENTAL,
                    WINDOW,
                    source("[{\"CodSolicitacao\":\"1\",\"TempoTotalViagem\":null}]", 1, 0, 3, true),
                    10,
                    100,
                    CancellationToken.none());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=? AND tempo_total_via"
                                    + "gem IS NULL AND tempo_total_viagem_p='NULL'",
                            run));
        }
    }

    @Test
    void unboundAndInvalidRemainAuditablyDegradedWithoutCanonicalRows() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = start(session, run);
            final var unbound =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            WINDOW,
                            source("[" + ROW + "]", 1, 1, 1, false),
                            10,
                            100,
                            CancellationToken.none());
            assertEquals(2, unbound.receipt().unbound());
            assertFalse(unbound.receipt().complete());
            final var invalid =
                    runtime.capture(
                            run,
                            ExecutionMode.BACKFILL,
                            WINDOW,
                            source("[{\"CodSolicitacao\":true}]", 1, 0, 1, true),
                            10,
                            100,
                            CancellationToken.none());
            assertEquals(1, invalid.receipt().quarantine());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
        }
    }

    @Test
    void incompleteReceiptAndFailureAfterParentRollBackTheEntireCapture() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = start(session, run);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            runtime.capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    WINDOW,
                                    source("[" + ROW + "]", 2, 1, 1, true),
                                    10,
                                    100,
                                    CancellationToken.none()));
            final var delegate = source("[" + ROW + "]", 1, 1, 1, true);
            final RasterGateway failing =
                    new RasterGateway() {
                        @Override
                        public Response fetch(final RasterWindow window)
                                throws java.io.IOException, InterruptedException {
                            return delegate.fetch(window);
                        }

                        @Override
                        public RasterBinding binding(
                                final RasterWindow window, final int trip, final int stop) {
                            if (stop > 0) {
                                throw new IllegalStateException("SYNTHETIC_CHILD_FAILURE");
                            }
                            return delegate.binding(window, trip, stop);
                        }
                    };
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            runtime.capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    WINDOW,
                                    failing,
                                    10,
                                    100,
                                    CancellationToken.none()));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
        }
    }

    static LocalRasterRuntime start(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        new JdbcRasterLaboratory(session, CLOCK)
                .start(run, DATE, DATE.plusDays(3), ZONE, 100000, 100);
        return new LocalRasterRuntime(session, CLOCK, ZONE);
    }

    static RasterGateway source(
            final String body,
            final long roots,
            final long stops,
            final int revision,
            final boolean bound) {
        return new RasterGateway() {
            @Override
            public Response fetch(final RasterWindow window) {
                return new Response(
                        body.getBytes(StandardCharsets.UTF_8),
                        new Terminal(
                                window,
                                roots,
                                stops,
                                "synthetic-proof-v1",
                                JdbcRasterLaboratory.SOURCE,
                                JdbcRasterLaboratory.TENANT,
                                JdbcRasterLaboratory.VERSION));
            }

            @Override
            public RasterBinding binding(
                    final RasterWindow window, final int tripPosition, final int stopPosition) {
                return bound
                        ? new RasterBinding(
                                "synthetic-trip-1",
                                stopPosition == 0 ? null : "synthetic-stop-1",
                                revision,
                                true,
                                false,
                                "synthetic-binding-v1",
                                JdbcRasterLaboratory.SOURCE,
                                JdbcRasterLaboratory.TENANT,
                                JdbcRasterLaboratory.VERSION)
                        : null;
            }
        };
    }

    static long scalar(
            final ColetaTemporalLaboratorySession session, final String sql, final UUID... run)
            throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            if (run.length == 1) {
                statement.setString(1, run[0].toString());
            }
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("ANA_PROBE_EMPTY");
                }
                return rows.getLong(1);
            }
        }
    }
}
