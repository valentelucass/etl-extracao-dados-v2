package br.com.esl.etl.v2.plataforma.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class JdbcStatementEvidenceTest {
    @Test
    void contractBatchEmitsOnceAndDiscardsTheUnusedCounts() throws SQLException {
        final var calls = new AtomicInteger();
        final int[] result = {1, 2};
        final List<String> lines = new ArrayList<>();
        JdbcStatementEvidence.contractBatch(
                statement("executeBatch", result, null, calls), ticks(), lines::add);
        assertEquals(1, calls.get());
        assertEquals(
                List.of("P08_PHASE phase=CONTRACT_BATCH durationMillis=5 outcome=SUCCESS"), lines);
    }

    @Test
    void referenceExecuteEmitsOnceAndReturnsTheSameResult() throws SQLException {
        final var calls = new AtomicInteger();
        final ResultSet result =
                (ResultSet)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ResultSet.class},
                                (proxy, method, arguments) -> null);
        final List<String> lines = new ArrayList<>();
        assertSame(
                result,
                JdbcStatementEvidence.referenceExecute(
                        statement("executeQuery", result, null, calls), ticks(), lines::add));
        assertEquals(1, calls.get());
        assertEquals(
                List.of("P08_PHASE phase=REFERENCE_EXECUTE durationMillis=5 outcome=SUCCESS"),
                lines);
    }

    @Test
    void bothBoundariesKeepTheExactSqlExceptionAndLockTimeout() {
        for (final String phase : List.of("CONTRACT_BATCH", "REFERENCE_EXECUTE")) {
            assertFailure(phase, new SQLException("private payload and URL", "42000", 17));
            assertFailure(phase, new SQLTimeoutException("private payload and URL", "HYT00", 1222));
        }
    }

    @Test
    void invalidStateAndSensitiveMessageNeverAppear() {
        final var failure = new SQLException("private payload and URL", "SECRET_STATE", 31);
        final List<String> lines = new ArrayList<>();
        assertSame(
                failure,
                assertThrows(
                        SQLException.class,
                        () ->
                                JdbcStatementEvidence.contractBatch(
                                        statement(
                                                "executeBatch", null, failure, new AtomicInteger()),
                                        ticks(),
                                        lines::add)));
        assertEquals(
                List.of(
                        "P08_PHASE phase=CONTRACT_BATCH durationMillis=5 outcome=FAILURE"
                                + " type=SQLException sqlState=UNKNOWN errorCode=31"),
                lines);
        assertFalse(lines.get(0).contains("private"));
        assertFalse(lines.get(0).contains("SECRET_STATE"));
    }

    @Test
    void failedEvidenceSinkCannotReplaceEitherResult() throws SQLException {
        final Consumer<String> failedSink =
                ignored -> {
                    throw new IllegalStateException("sink failed");
                };
        final var batchCalls = new AtomicInteger();
        JdbcStatementEvidence.contractBatch(
                statement("executeBatch", new int[] {1}, null, batchCalls), ticks(), failedSink);
        assertEquals(1, batchCalls.get());
        final var batchFailure = new SQLException("private", "42000", 1);
        assertSame(
                batchFailure,
                assertThrows(
                        SQLException.class,
                        () ->
                                JdbcStatementEvidence.contractBatch(
                                        statement(
                                                "executeBatch",
                                                null,
                                                batchFailure,
                                                new AtomicInteger()),
                                        ticks(),
                                        failedSink)));
        assertEquals(
                null,
                JdbcStatementEvidence.referenceExecute(
                        statement("executeQuery", null, null, new AtomicInteger()),
                        ticks(),
                        failedSink));
        final var referenceFailure = new SQLTimeoutException("private", "HYT00", 1222);
        assertSame(
                referenceFailure,
                assertThrows(
                        SQLTimeoutException.class,
                        () ->
                                JdbcStatementEvidence.referenceExecute(
                                        statement(
                                                "executeQuery",
                                                null,
                                                referenceFailure,
                                                new AtomicInteger()),
                                        ticks(),
                                        failedSink)));
    }

    private static void assertFailure(final String phase, final SQLException failure) {
        final List<String> lines = new ArrayList<>();
        final var calls = new AtomicInteger();
        final var statement =
                statement(
                        phase.equals("CONTRACT_BATCH") ? "executeBatch" : "executeQuery",
                        null,
                        failure,
                        calls);
        final SQLException caught =
                assertThrows(
                        SQLException.class,
                        () -> {
                            if (phase.equals("CONTRACT_BATCH")) {
                                JdbcStatementEvidence.contractBatch(statement, ticks(), lines::add);
                            } else {
                                JdbcStatementEvidence.referenceExecute(
                                        statement, ticks(), lines::add);
                            }
                        });
        assertSame(failure, caught);
        assertEquals(1, calls.get());
        assertEquals(
                List.of(
                        "P08_PHASE phase="
                                + phase
                                + " durationMillis=5 outcome=FAILURE type="
                                + failure.getClass().getSimpleName()
                                + " sqlState="
                                + failure.getSQLState()
                                + " errorCode="
                                + failure.getErrorCode()),
                lines);
        assertFalse(lines.get(0).contains("private"));
        assertFalse(lines.get(0).contains("URL"));
    }

    private static PreparedStatement statement(
            final String operation,
            final Object result,
            final SQLException failure,
            final AtomicInteger calls) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        JdbcStatementEvidenceTest.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, arguments) -> {
                            if (!operation.equals(method.getName())) {
                                throw new AssertionError("Unexpected JDBC method");
                            }
                            calls.incrementAndGet();
                            if (failure != null) {
                                throw failure;
                            }
                            return result;
                        });
    }

    private static LongSupplier ticks() {
        final var index = new AtomicInteger();
        return () -> index.getAndIncrement() == 0 ? 100 : 5_000_100;
    }
}
