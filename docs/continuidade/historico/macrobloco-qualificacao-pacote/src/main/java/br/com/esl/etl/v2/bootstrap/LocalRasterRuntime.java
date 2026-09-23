package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterResponseParser;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterBatch;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** Capture, bounded staging, terminality and atomic SQL apply share the rollback-only session. */
public final class LocalRasterRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final JdbcRasterLaboratory database;
    private final RasterResponseParser parser;
    private final Observer observer;

    public LocalRasterRuntime(
            final ColetaTemporalLaboratorySession session, final Clock clock, final ZoneId zone) {
        this(session, clock, zone, Observer.NONE);
    }

    public LocalRasterRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock clock,
            final ZoneId zone,
            final Observer observer) {
        this.session = Objects.requireNonNull(session);
        database = new JdbcRasterLaboratory(session, clock);
        parser = new RasterResponseParser(zone);
        this.observer = Objects.requireNonNull(observer);
    }

    public Capture capture(
            final UUID run,
            final ExecutionMode mode,
            final RasterWindow window,
            final RasterGateway gateway,
            final int maximumCalls,
            final int maximumRows,
            final CancellationToken cancellation)
            throws SQLException, IOException, InterruptedException {
        if (maximumCalls < 1 || maximumCalls > 10000 || maximumRows < 1 || maximumRows > 100000) {
            throw new IllegalArgumentException("RAS_CAPTURE_BUDGET");
        }
        final var connection = session.getConnection();
        final var savepoint = connection.setSavepoint();
        final UUID capture = UUID.randomUUID();
        final var progress = new Progress(maximumCalls, maximumRows, cancellation);
        try {
            database.begin(run, capture, window.start(), window.endExclusive(), mode);
            try (var batch = new JdbcRasterBatch(connection, capture)) {
                traverse(capture, window, gateway, batch, progress);
                batch.flush();
                database.seal(capture, progress.trips, progress.stops, progress.calls);
                return new Capture(
                        capture,
                        database.apply(run, capture),
                        progress.calls,
                        batch.maximumPending(),
                        progress.bytes,
                        progress.maximumPageBytes,
                        batch.executedBatches());
            }
        } catch (final SQLException
                | IOException
                | InterruptedException
                | RuntimeException failure) {
            try {
                connection.rollback(savepoint);
            } catch (final SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            connection.close();
        }
    }

    private void traverse(
            final UUID capture,
            final RasterWindow window,
            final RasterGateway gateway,
            final JdbcRasterBatch batch,
            final Progress progress)
            throws IOException, InterruptedException, SQLException {
        if (captureWindow(capture, window, gateway, batch, progress)) {
            return;
        }
        final var middle = window.start().plusDays(window.days() / 2);
        traverse(capture, new RasterWindow(window.start(), middle), gateway, batch, progress);
        traverse(
                capture, new RasterWindow(middle, window.endExclusive()), gateway, batch, progress);
    }

    /** The response scope ends before recursively visiting either child window. */
    private boolean captureWindow(
            final UUID capture,
            final RasterWindow window,
            final RasterGateway gateway,
            final JdbcRasterBatch batch,
            final Progress progress)
            throws IOException, InterruptedException, SQLException {
        progress.cancellation.throwIfCancellationRequested();
        if (++progress.calls > progress.maximumCalls) {
            throw new IllegalArgumentException("RAS_CALL_BUDGET");
        }
        observer.rasterBeforeFetch();
        final var response = gateway.fetch(window);
        final byte[] body = response.body();
        progress.bytes = Math.addExact(progress.bytes, body.length);
        progress.maximumPageBytes = Math.max(progress.maximumPageBytes, body.length);
        observer.rasterPageFetched(body);
        try {
            final var counts =
                    parser.parse(
                            body,
                            new RasterResponseParser.Sink() {
                                @Override
                                public void trip(
                                        final int position, final RasterTripObservation value) {}

                                @Override
                                public void stop(
                                        final int trip,
                                        final int position,
                                        final RasterStop value) {}
                            });
            if (counts.trips() >= 500) {
                if (window.days() == 1) {
                    throw new IllegalArgumentException("RAS_CAP_MINIMUM_WINDOW");
                }
                return false;
            }
            final var proof = response.terminal();
            if (proof == null
                    || !proof.window().equals(window)
                    || proof.trips() != counts.trips()
                    || proof.stops() != counts.stops()) {
                throw new IllegalArgumentException("RAS_TERMINAL_UNPROVEN");
            }
            if (progress.trips + progress.stops + counts.trips() + counts.stops()
                    > progress.maximumRows) {
                throw new IllegalArgumentException("RAS_ROW_BUDGET");
            }
            parser.parse(
                    body,
                    new RasterResponseParser.Sink() {
                        @Override
                        public void trip(final int position, final RasterTripObservation value)
                                throws SQLException {
                            progress.cancellation.throwIfCancellationRequested();
                            batch.trip(value, gateway.binding(window, position, 0));
                        }

                        @Override
                        public void stop(final int trip, final int position, final RasterStop value)
                                throws SQLException {
                            progress.cancellation.throwIfCancellationRequested();
                            batch.stop(value, gateway.binding(window, trip, position));
                        }
                    });
            database.terminal(capture, ++progress.leaves, proof);
            progress.trips += counts.trips();
            progress.stops += counts.stops();
            batch.flush();
            observer.rasterPageConsumed(body, counts.trips() + counts.stops());
            return true;
        } finally {
            observer.rasterPageReleased(body);
        }
    }

    public record Capture(
            UUID capture,
            JdbcRasterLaboratory.Receipt receipt,
            int calls,
            int maximumBatch,
            long responseBytes,
            int maximumPageBytes,
            int executedBatches) {}

    public interface Observer {
        Observer NONE = new Observer() {};

        default void rasterBeforeFetch() {}

        default void rasterPageFetched(byte[] body) {}

        default void rasterPageConsumed(byte[] body, long records) {}

        default void rasterPageReleased(byte[] body) {}
    }

    private static final class Progress {
        private final int maximumCalls;
        private final int maximumRows;
        private final CancellationToken cancellation;
        private int calls;
        private int leaves;
        private long trips;
        private long stops;
        private long bytes;
        private int maximumPageBytes;

        private Progress(
                final int maximumCalls,
                final int maximumRows,
                final CancellationToken cancellation) {
            this.maximumCalls = maximumCalls;
            this.maximumRows = maximumRows;
            this.cancellation = Objects.requireNonNull(cancellation);
        }
    }
}
