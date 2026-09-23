package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LaboratorySqlBudgetTest {
    @Test
    void boundsRealSubmissionsBeforeDelegateAndRefusesUnwrap() throws Exception {
        final var budget = new LaboratorySqlBudget();
        final var calls = new AtomicInteger();
        try (var connection = budget.open(() -> delegate(calls));
                var statement = connection.createStatement()) {
            for (int i = 0; i < 512; i++) {
                assertEquals(0, statement.executeUpdate("SELECT 1"));
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("SELECT 1"));
            assertEquals(512, calls.get());
            assertThrows(SQLException.class, () -> connection.unwrap(Connection.class));
            assertThrows(SQLException.class, () -> statement.unwrap(Statement.class));
        }
    }

    @Test
    void failedOpensRemainConsumedButReleaseConcurrency() throws Exception {
        final var budget = new LaboratorySqlBudget();
        final var opens = new AtomicInteger();
        for (int i = 0; i < 256; i++) {
            assertThrows(
                    SQLException.class,
                    () ->
                            budget.open(
                                    () -> {
                                        opens.incrementAndGet();
                                        throw new SQLException("SYNTHETIC_FAILURE");
                                    }));
        }
        assertThrows(SQLException.class, () -> budget.open(() -> delegate(opens)));
        assertEquals(256, opens.get());
    }

    @Test
    void concurrentConnectionsAreBoundedAndDoubleCloseDoesNotReleaseTwice() throws Exception {
        final var budget = new LaboratorySqlBudget();
        final var opened = new ArrayList<Connection>();
        try {
            for (int i = 0; i < 4; i++) {
                opened.add(budget.open(() -> delegate(new AtomicInteger())));
            }
            assertThrows(
                    SQLException.class, () -> budget.open(() -> delegate(new AtomicInteger())));
            opened.get(0).close();
            opened.get(0).close();
            opened.add(budget.open(() -> delegate(new AtomicInteger())));
            assertThrows(
                    SQLException.class, () -> budget.open(() -> delegate(new AtomicInteger())));
        } finally {
            for (final var connection : opened) {
                connection.close();
            }
        }
    }

    @Test
    void failedOpenerErrorsConsumeAttemptsWithoutRetainingConcurrency() throws Exception {
        final var budget = new LaboratorySqlBudget();
        final var failure = new AssertionError("SYNTHETIC_OPENER_ERROR");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertSame(
                    failure,
                    assertThrows(
                            AssertionError.class,
                            () ->
                                    budget.open(
                                            () -> {
                                                throw failure;
                                            })));
        }
        final var opened = new ArrayList<Connection>();
        try {
            for (int i = 0; i < 4; i++) {
                opened.add(budget.open(() -> delegate(new AtomicInteger())));
            }
            assertThrows(
                    SQLException.class, () -> budget.open(() -> delegate(new AtomicInteger())));
            assertEquals("B60_SQL_ATTEMPTS connections=10 submissions=0", budget.summary());
        } finally {
            for (final var connection : opened) {
                connection.close();
            }
        }
    }

    private static Connection delegate(final AtomicInteger calls) {
        final var loader = LaboratorySqlBudgetTest.class.getClassLoader();
        final var statement =
                (Statement)
                        Proxy.newProxyInstance(
                                loader,
                                new Class<?>[] {Statement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("executeUpdate")) {
                                        calls.incrementAndGet();
                                        return 0;
                                    }
                                    return null;
                                });
        return (Connection)
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) ->
                                method.getName().equals("createStatement") ? statement : null);
    }
}
