package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import org.junit.jupiter.api.Test;

class SavepointScopeTest {
    @Test
    void rollsBackTheSavepointAndLeavesTheBorrowedConnectionOpen() throws SQLException {
        final var database = new ControlledConnection(null, null);
        final var scope = SavepointScope.open(database.connection());

        try (scope) {
            assertEquals(0, database.rollbacks);
        }

        assertEquals(1, database.rollbacks);
        assertFalse(database.closed);
    }

    @Test
    void preservesThePrimaryExceptionAndSuppressesTheRollbackFailure() {
        final var primary = new SQLException("synthetic primary failure");
        final var rollback = new SQLException("synthetic rollback failure");
        final var database = new ControlledConnection(null, rollback);

        final var failure =
                assertThrows(
                        SQLException.class,
                        () -> {
                            final var scope = SavepointScope.open(database.connection());
                            try (scope) {
                                throw primary;
                            }
                        });

        assertSame(primary, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(rollback, failure.getSuppressed()[0]);
        assertEquals(1, database.rollbacks);
        assertFalse(database.closed);
    }

    @Test
    void preservesAnErrorAndSuppressesTheRollbackFailure() {
        final var primary = new AssertionError("synthetic primary error");
        final var rollback = new SQLException("synthetic rollback failure");
        final var database = new ControlledConnection(null, rollback);

        final var failure =
                assertThrows(
                        AssertionError.class,
                        () -> {
                            final var scope = SavepointScope.open(database.connection());
                            try (scope) {
                                throw primary;
                            }
                        });

        assertSame(primary, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(rollback, failure.getSuppressed()[0]);
        assertEquals(1, database.rollbacks);
        assertFalse(database.closed);
    }

    @Test
    void propagatesTheRollbackFailureWithoutAPrimaryFailure() {
        final var rollback = new SQLException("synthetic rollback failure");
        final var database = new ControlledConnection(null, rollback);

        final var failure =
                assertThrows(
                        SQLException.class,
                        () -> {
                            final var scope = SavepointScope.open(database.connection());
                            try (scope) {
                                assertEquals(0, database.rollbacks);
                            }
                        });

        assertSame(rollback, failure);
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(1, database.rollbacks);
        assertFalse(database.closed);
    }

    @Test
    void propagatesTheSavepointFailureWithoutClosingTheBorrowedConnection() {
        final var opening = new SQLException("synthetic savepoint failure");
        final var database = new ControlledConnection(opening, null);

        final var failure =
                assertThrows(SQLException.class, () -> SavepointScope.open(database.connection()));

        assertSame(opening, failure);
        assertEquals(0, database.rollbacks);
        assertFalse(database.closed);
    }

    private static final class ControlledConnection {
        private final SQLException opening;
        private final SQLException rollback;
        private final Savepoint savepoint =
                new Savepoint() {
                    @Override
                    public int getSavepointId() {
                        return 1;
                    }

                    @Override
                    public String getSavepointName() {
                        return "synthetic";
                    }
                };
        private int rollbacks;
        private boolean closed;

        private ControlledConnection(final SQLException opening, final SQLException rollback) {
            this.opening = opening;
            this.rollback = rollback;
        }

        private Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                return switch (method.getName()) {
                                    case "setSavepoint" -> {
                                        if (opening != null) {
                                            throw opening;
                                        }
                                        yield savepoint;
                                    }
                                    case "rollback" -> {
                                        assertEquals(1, args.length);
                                        assertSame(savepoint, args[0]);
                                        rollbacks++;
                                        if (rollback != null) {
                                            throw rollback;
                                        }
                                        yield null;
                                    }
                                    case "close" -> {
                                        closed = true;
                                        yield null;
                                    }
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                };
                            });
        }
    }
}
