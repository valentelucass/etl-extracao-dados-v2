package br.com.esl.etl.v2.plataforma.autorizacao;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Additional process bounds for the explicitly administered B60 laboratory artifact. */
final class LaboratorySqlBudget {
    private static final LaboratorySqlBudget ACTIVE = load();
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicInteger submissions = new AtomicInteger();
    private final AtomicInteger concurrent = new AtomicInteger();

    @FunctionalInterface
    interface Opener {
        Connection open() throws SQLException;
    }

    static Connection openIfApplicable(final Opener opener) throws SQLException {
        return ACTIVE == null ? opener.open() : ACTIVE.open(opener);
    }

    private static LaboratorySqlBudget load() {
        try (var stream =
                LaboratorySqlBudget.class.getResourceAsStream("/runtime-laboratory.properties")) {
            if (stream == null) {
                return null;
            }
            final String content =
                    new String(stream.readNBytes(513), java.nio.charset.StandardCharsets.UTF_8);
            if (!content.startsWith("bloco60-v1\n")) {
                return null;
            }
            if (content.length() > 512) {
                throw new IllegalStateException("LABORATORY_RESOURCE_LIMIT");
            }
            final var budget = new LaboratorySqlBudget();
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> System.out.println(budget.summary()),
                                    "laboratory-sql-counts"));
            return budget;
        } catch (final IOException failure) {
            throw new IllegalStateException("LABORATORY_RESOURCE_UNAVAILABLE", failure);
        }
    }

    Connection open(final Opener opener) throws SQLException {
        if (connections.incrementAndGet() > 256) {
            throw new SQLException("LABORATORY_SQL_CONNECTION_BUDGET");
        }
        if (concurrent.incrementAndGet() > 4) {
            concurrent.decrementAndGet();
            throw new SQLException("LABORATORY_SQL_CONCURRENT_LIMIT");
        }
        final Connection delegate;
        try {
            delegate = opener.open();
        } catch (final SQLException | RuntimeException failure) {
            concurrent.decrementAndGet();
            throw failure;
        }
        final var closed = new AtomicBoolean();
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("close")) {
                                if (closed.compareAndSet(false, true)) {
                                    try {
                                        delegate.close();
                                    } finally {
                                        concurrent.decrementAndGet();
                                    }
                                }
                                return null;
                            }
                            if (method.getName().equals("unwrap")) {
                                throw new SQLException("LABORATORY_UNWRAP_REFUSED");
                            }
                            if (method.getName().equals("isWrapperFor")) {
                                return false;
                            }
                            if (method.getName().equals("commit")) {
                                reserveSubmission();
                            }
                            try {
                                final Object value = method.invoke(delegate, args);
                                return value instanceof Statement statement
                                        ? statement(statement)
                                        : value;
                            } catch (final InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    private Statement statement(final Statement delegate) {
        final Class<?> type =
                delegate instanceof java.sql.CallableStatement
                        ? java.sql.CallableStatement.class
                        : delegate instanceof java.sql.PreparedStatement
                                ? java.sql.PreparedStatement.class
                                : Statement.class;
        return (Statement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (method.getName().startsWith("execute")) {
                                reserveSubmission();
                            }
                            if (method.getName().equals("unwrap")) {
                                throw new SQLException("LABORATORY_UNWRAP_REFUSED");
                            }
                            if (method.getName().equals("isWrapperFor")) {
                                return false;
                            }
                            try {
                                return method.invoke(delegate, args);
                            } catch (final InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    private void reserveSubmission() throws SQLException {
        if (submissions.incrementAndGet() > 512) {
            throw new SQLException("LABORATORY_SQL_SUBMISSION_BUDGET");
        }
    }

    String summary() {
        return "B60_SQL_ATTEMPTS connections="
                + connections.get()
                + " submissions="
                + submissions.get();
    }
}
