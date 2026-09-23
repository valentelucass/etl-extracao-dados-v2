package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColetaTemporalLaboratorySessionCloseTest {
    @Test
    void cancellationBorrowsTrackedHandlesAndPruningDoesNotCloseThemAgain() throws Exception {
        final var closed = new AtomicBoolean();
        final var closes = new AtomicInteger();
        final var cancellations = new AtomicInteger();
        final var statement =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "isClosed" -> closed.get();
                                            case "close" -> {
                                                closes.incrementAndGet();
                                                closed.set(true);
                                                yield null;
                                            }
                                            case "cancel" -> {
                                                cancellations.incrementAndGet();
                                                yield null;
                                            }
                                            default -> null;
                                        });
        final var physical =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        method.getName().equals("createStatement")
                                                ? statement
                                                : null);
        final var constructor =
                ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
        constructor.setAccessible(true);
        try (var session = constructor.newInstance(physical);
                var connection = session.getConnection()) {
            session.controlStatements(5);
            assertSame(statement, connection.createStatement());
            assertEquals(1, session.cancelActiveStatements());
            assertEquals(1, cancellations.get());
            assertEquals(0, closes.get());
            assertEquals(1, session.openControlledStatements());
            statement.close();
            assertEquals(0, session.openControlledStatements());
            assertEquals(0, session.cancelActiveStatements());
            assertEquals(1, closes.get());
            assertEquals(1, cancellations.get());
        }
    }

    @ParameterizedTest
    @MethodSource("statementSetupFailures")
    void statementSetupRetainsPrimaryFailureAndClosesNewHandleExactlyOnce(
            final boolean failExisting, final Throwable primary, final Throwable cleanup)
            throws Exception {
        final var closes = new AtomicInteger();
        final var creations = new AtomicInteger();
        final var existing =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "isClosed" -> {
                                                if (failExisting) {
                                                    throw primary;
                                                }
                                                yield false;
                                            }
                                            default -> null;
                                        });
        final var incoming =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("setQueryTimeout")) {
                                        throw primary;
                                    }
                                    if (method.getName().equals("close")) {
                                        closes.incrementAndGet();
                                        if (cleanup != null) {
                                            throw cleanup;
                                        }
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        final var physical =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        method.getName().equals("createStatement")
                                                ? (creations.getAndIncrement() == 0
                                                        ? existing
                                                        : incoming)
                                                : null);
        final var constructor =
                ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
        constructor.setAccessible(true);
        try (var session = constructor.newInstance(physical);
                var connection = session.getConnection()) {
            session.controlStatements(5);
            assertSame(existing, connection.createStatement());
            assertSame(primary, assertThrows(primary.getClass(), connection::createStatement));
            assertEquals(1, closes.get());
            final boolean distinctCleanup = cleanup != null && cleanup != primary;
            assertEquals(distinctCleanup ? 1 : 0, primary.getSuppressed().length);
            if (distinctCleanup) {
                assertSame(cleanup, primary.getSuppressed()[0]);
            }
        }
    }

    private static Stream<Arguments> statementSetupFailures() {
        return Stream.of("SQL", "RUNTIME", "ERROR")
                .flatMap(
                        kind ->
                                Stream.concat(
                                        Stream.of(Arguments.of(true, setupFailure(kind), null)),
                                        Stream.of("NONE", "SQL", "RUNTIME", "ERROR", "SELF")
                                                .map(
                                                        cleanupKind -> {
                                                            final Throwable primary =
                                                                    setupFailure(kind);
                                                            final Throwable cleanup =
                                                                    cleanupKind.equals("NONE")
                                                                            ? null
                                                                            : cleanupKind.equals(
                                                                                            "SELF")
                                                                                    ? primary
                                                                                    : setupFailure(
                                                                                            cleanupKind);
                                                            return Arguments.of(
                                                                    false, primary, cleanup);
                                                        })));
    }

    private static Throwable setupFailure(final String kind) {
        return switch (kind) {
            case "SQL" -> new SQLException("SYNTHETIC_STATEMENT_SETUP");
            case "RUNTIME" -> new IllegalStateException("SYNTHETIC_STATEMENT_SETUP");
            case "ERROR" -> new AssertionError("SYNTHETIC_STATEMENT_SETUP");
            default -> throw new IllegalArgumentException("SYNTHETIC_FAILURE_KIND");
        };
    }

    @Test
    void rollbackFailureRemainsPrimaryWhenPhysicalCloseAlsoFails() throws Exception {
        check(true, true);
    }

    @Test
    void rollbackFailureStillClosesPhysicalConnection() throws Exception {
        check(true, false);
    }

    @Test
    void closeFailureRemainsObservableAfterSuccessfulRollback() throws Exception {
        check(false, true);
    }

    @Test
    void successfulCloseRollsBackExactlyOnce() throws Exception {
        check(false, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesNewStatementWhenCheckingAnExistingStatementFails(final boolean failClose)
            throws Exception {
        final var primary = new SQLException("SYNTHETIC_STATEMENT_STATUS_FAILURE");
        final var cleanup = new SQLException("SYNTHETIC_STATEMENT_CLOSE_FAILURE");
        final var closed = new AtomicBoolean();
        final var creations = new AtomicInteger();
        final var existing =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "isClosed" -> throw primary;
                                            default -> null;
                                        });
        final var incoming =
                (Statement)
                        Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) {
                                        closed.set(true);
                                        if (failClose) {
                                            throw cleanup;
                                        }
                                    }
                                    return null;
                                });
        final var physical =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        method.getName().equals("createStatement")
                                                ? (creations.getAndIncrement() == 0
                                                        ? existing
                                                        : incoming)
                                                : null);
        final var constructor =
                ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
        constructor.setAccessible(true);
        try (var session = constructor.newInstance(physical);
                var connection = session.getConnection()) {
            session.controlStatements(5);
            assertSame(existing, connection.createStatement());
            final var failure = assertThrows(SQLException.class, connection::createStatement);
            assertSame(primary, failure);
            assertTrue(closed.get());
            assertEquals(failClose ? 1 : 0, failure.getSuppressed().length);
            if (failClose) {
                assertSame(cleanup, failure.getSuppressed()[0]);
            }
        }
    }

    private void check(final boolean failRollback, final boolean failClose) throws Exception {
        final var calls = new ArrayList<String>();
        final var rollback = new SQLException("SYNTHETIC_ROLLBACK_FAILURE");
        final var close = new SQLException("SYNTHETIC_CLOSE_FAILURE");
        final var physical =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    calls.add(method.getName());
                                    if (method.getName().equals("rollback") && failRollback) {
                                        throw rollback;
                                    }
                                    if (method.getName().equals("close") && failClose) {
                                        throw close;
                                    }
                                    return null;
                                });
        final var constructor =
                ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
        constructor.setAccessible(true);
        final var session = constructor.newInstance(physical);
        if (failRollback || failClose) {
            final var failure = assertThrows(SQLException.class, session::close);
            assertSame(failRollback ? rollback : close, failure);
            assertEquals(failRollback && failClose ? 1 : 0, failure.getSuppressed().length);
            if (failRollback && failClose) {
                assertSame(close, failure.getSuppressed()[0]);
            }
        } else {
            session.close();
        }
        session.close();
        assertEquals(java.util.List.of("rollback", "close"), calls);
        assertThrows(SQLException.class, session::getConnection);
    }
}
