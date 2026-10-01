package br.com.esl.etl.v2.plataforma.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Sanitized timing at the two laboratory JDBC statement boundaries. */
public final class JdbcStatementEvidence {
    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }

    private JdbcStatementEvidence() {}

    public static void contractBatch(final PreparedStatement statement) throws SQLException {
        contractBatch(statement, System::nanoTime, System.err::println);
    }

    public static ResultSet referenceExecute(final PreparedStatement statement)
            throws SQLException {
        return referenceExecute(statement, System::nanoTime, System.err::println);
    }

    static void contractBatch(
            final PreparedStatement statement,
            final LongSupplier nanos,
            final Consumer<String> output)
            throws SQLException {
        execute("CONTRACT_BATCH", statement::executeBatch, nanos, output);
    }

    static ResultSet referenceExecute(
            final PreparedStatement statement,
            final LongSupplier nanos,
            final Consumer<String> output)
            throws SQLException {
        return execute("REFERENCE_EXECUTE", statement::executeQuery, nanos, output);
    }

    private static <T> T execute(
            final String phase,
            final SqlOperation<T> operation,
            final LongSupplier nanos,
            final Consumer<String> output)
            throws SQLException {
        final long started = nanos.getAsLong();
        Throwable failure = null;
        try {
            return operation.run();
        } catch (final SQLException | RuntimeException | Error caught) {
            failure = caught;
            throw caught;
        } finally {
            emit(output, phase, started, nanos, failure);
        }
    }

    private static void emit(
            final Consumer<String> output,
            final String phase,
            final long started,
            final LongSupplier nanos,
            final Throwable failure) {
        try {
            final long millis =
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0, nanos.getAsLong() - started));
            final var line =
                    new StringBuilder("P08_PHASE phase=")
                            .append(phase)
                            .append(" durationMillis=")
                            .append(millis);
            if (failure == null) {
                line.append(" outcome=SUCCESS");
            } else {
                final String simple = failure.getClass().getSimpleName();
                line.append(" outcome=FAILURE type=")
                        .append(simple.matches("[A-Za-z0-9_$]{1,80}") ? simple : "UNKNOWN");
                if (failure instanceof SQLException sql) {
                    final String state = sql.getSQLState();
                    line.append(" sqlState=")
                            .append(
                                    state != null && state.matches("[A-Z0-9]{5}")
                                            ? state
                                            : "UNKNOWN")
                            .append(" errorCode=")
                            .append(sql.getErrorCode());
                } else {
                    line.append(" sqlState=NONE errorCode=NONE");
                }
            }
            output.accept(line.toString());
        } catch (final RuntimeException | Error ignored) {
            // Diagnostic failure cannot replace the JDBC result or original exception.
        }
    }
}
