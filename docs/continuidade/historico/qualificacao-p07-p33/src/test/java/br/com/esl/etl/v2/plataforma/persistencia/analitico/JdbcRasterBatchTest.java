package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JdbcRasterBatchTest {
    @Test
    void closesStatementsBeforeObserverInTheOriginalOrder() throws Exception {
        final var calls = new ArrayList<String>();
        final var batch = batch(calls, null, null, null, null);

        batch.close();

        assertEquals(List.of("trips", "stops", "structures", "observer"), calls);
    }

    @Test
    void preservesFirstSqlFailureAndSuppressesEveryLaterCleanupFailure() throws Exception {
        final var calls = new ArrayList<String>();
        final var trips = new SQLException("SYNTHETIC_TRIP_CLOSE");
        final var stops = new SQLException("SYNTHETIC_STOP_CLOSE");
        final var structures = new SQLException("SYNTHETIC_STRUCTURE_CLOSE");
        final var observer = new IllegalStateException("SYNTHETIC_OBSERVER_CLOSE");
        final var batch = batch(calls, trips, stops, structures, observer);

        assertSame(trips, assertThrows(SQLException.class, batch::close));

        assertArrayEquals(new Throwable[] {stops, structures, observer}, trips.getSuppressed());
        assertEquals(List.of("trips", "stops", "structures", "observer"), calls);
    }

    @Test
    void preservesTheFirstFailureAfterAnEarlierStatementClosedSuccessfully() throws Exception {
        final var calls = new ArrayList<String>();
        final var stops = new SQLException("SYNTHETIC_STOP_CLOSE");
        final var structures = new SQLException("SYNTHETIC_STRUCTURE_CLOSE");
        final var observer = new IllegalStateException("SYNTHETIC_OBSERVER_CLOSE");
        final var batch = batch(calls, null, stops, structures, observer);

        assertSame(stops, assertThrows(SQLException.class, batch::close));

        assertArrayEquals(new Throwable[] {structures, observer}, stops.getSuppressed());
        assertEquals(List.of("trips", "stops", "structures", "observer"), calls);
    }

    @Test
    void propagatesObserverFailureAfterSuccessfulStatementCleanup() throws Exception {
        final var calls = new ArrayList<String>();
        final var observer = new IllegalStateException("SYNTHETIC_OBSERVER_CLOSE");
        final var batch = batch(calls, null, null, null, observer);

        assertSame(observer, assertThrows(IllegalStateException.class, batch::close));

        assertEquals(0, observer.getSuppressed().length);
        assertEquals(List.of("trips", "stops", "structures", "observer"), calls);
    }

    @Test
    void retainsFatalStatementFailureWhileStillClosingRemainingResources() throws Exception {
        final var calls = new ArrayList<String>();
        final var trips = new AssertionError("SYNTHETIC_FATAL_CLOSE");
        final var stops = new SQLException("SYNTHETIC_STOP_CLOSE");
        final var observer = new IllegalStateException("SYNTHETIC_OBSERVER_CLOSE");
        final var batch = batch(calls, trips, stops, null, observer);

        assertSame(trips, assertThrows(AssertionError.class, batch::close));

        assertArrayEquals(new Throwable[] {stops, observer}, trips.getSuppressed());
        assertEquals(List.of("trips", "stops", "structures", "observer"), calls);
    }

    private static JdbcRasterBatch batch(
            final List<String> calls,
            final Throwable tripsFailure,
            final Throwable stopsFailure,
            final Throwable structuresFailure,
            final RuntimeException observerFailure)
            throws SQLException {
        final var statements =
                List.of(
                        statement("trips", calls, tripsFailure),
                        statement("stops", calls, stopsFailure),
                        statement("structures", calls, structuresFailure));
        final var next = new AtomicInteger();
        final var connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("prepareStatement")) {
                                        return statements.get(next.getAndIncrement());
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        final var observer =
                new SyntheticCaptureObserver() {
                    @Override
                    public void captureClosed() {
                        calls.add("observer");
                        if (observerFailure != null) {
                            throw observerFailure;
                        }
                    }
                };
        return new JdbcRasterBatch(connection, new UUID(0, 1), observer);
    }

    private static PreparedStatement statement(
            final String name, final List<String> calls, final Throwable failure) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("setQueryTimeout")) {
                                assertEquals(15, arguments[0]);
                                return null;
                            }
                            if (method.getName().equals("close")) {
                                calls.add(name);
                                if (failure != null) {
                                    throw failure;
                                }
                                return null;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }
}
