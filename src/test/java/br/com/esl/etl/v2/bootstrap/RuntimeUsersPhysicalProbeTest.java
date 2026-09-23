package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class RuntimeUsersPhysicalProbeTest {
    @Test
    void discardedConfirmationHappensAfterDelegateAndOnlyOnce() throws Exception {
        final var physicalCalls = new AtomicInteger();
        final var commands = new AtomicInteger();
        final var triggered = new AtomicBoolean();
        final var observed =
                RuntimeUsersPhysicalProbe.observe(
                        delegate(physicalCalls, false),
                        RuntimeUsersPhysicalProbe.Fault.SEAL_ACK_LOST,
                        commands,
                        triggered);
        try (var connection = observed.getConnection();
                var statement = connection.prepareStatement("EXEC ctl.usp_runtime_recovery ?,?")) {
            statement.setString(2, "READ");
            assertEquals(1, statement.executeUpdate());
            assertFalse(triggered.get());
            statement.setString(2, "SEAL");
            assertThrows(SQLException.class, statement::executeUpdate);
            assertTrue(triggered.get());
            assertEquals(2, physicalCalls.get());
            assertEquals(1, statement.executeUpdate());
            assertEquals(3, commands.get());
        }
    }

    @Test
    void delegateFailureCannotBeReportedAsInjectedConfirmationLoss() throws Exception {
        final var calls = new AtomicInteger();
        final var triggered = new AtomicBoolean();
        final var observed =
                RuntimeUsersPhysicalProbe.observe(
                        delegate(calls, true),
                        RuntimeUsersPhysicalProbe.Fault.APPLY_ACK_LOST,
                        new AtomicInteger(),
                        triggered);
        try (var connection = observed.getConnection();
                var statement =
                        connection.prepareStatement(
                                "EXEC core.usp_apply_reconcile_publish_usuarios")) {
            assertEquals(
                    "DELEGATE_FAILURE",
                    assertThrows(SQLException.class, statement::executeUpdate).getMessage());
            assertFalse(triggered.get());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void consumedConfirmationIsLostOnlyAfterCompletionWasDrained() throws Exception {
        final var triggered = new AtomicBoolean();
        final var calls = new AtomicInteger();
        final var observed =
                RuntimeUsersPhysicalProbe.observe(
                        delegate(calls, false),
                        RuntimeUsersPhysicalProbe.Fault.CONSUME_ACK_LOST,
                        new AtomicInteger(),
                        triggered);
        try (var connection = observed.getConnection();
                var statement =
                        connection.prepareStatement("EXEC ctl.usp_runtime_authorization ?,?")) {
            statement.setString(1, "CONSUME");
            assertEquals(1, statement.executeUpdate());
            assertFalse(triggered.get());
            assertThrows(SQLException.class, statement::getMoreResults);
            assertTrue(triggered.get());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void budgetRefusesBeforeNextDelegateSubmission() throws Exception {
        final var calls = new AtomicInteger();
        final var observed =
                RuntimeUsersPhysicalProbe.observe(
                        delegate(calls, false),
                        RuntimeUsersPhysicalProbe.Fault.NONE,
                        new AtomicInteger(128),
                        new AtomicBoolean());
        try (var connection = observed.getConnection();
                var statement = connection.prepareStatement("SELECT 1")) {
            assertThrows(SQLException.class, statement::executeUpdate);
            assertEquals(0, calls.get());
        }
    }

    private static DataSource delegate(final AtomicInteger calls, final boolean fail) {
        final var statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("executeUpdate")) {
                                        calls.incrementAndGet();
                                        if (fail) {
                                            throw new SQLException("DELEGATE_FAILURE");
                                        }
                                        return 1;
                                    }
                                    return method.getName().equals("getMoreResults") ? false : null;
                                });
        final var connection =
                (Connection)
                        Proxy.newProxyInstance(
                                getLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        method.getName().equals("prepareStatement")
                                                ? statement
                                                : null);
        return (DataSource)
                Proxy.newProxyInstance(
                        getLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> connection);
    }

    private static ClassLoader getLoader() {
        return RuntimeUsersPhysicalProbeTest.class.getClassLoader();
    }
}
