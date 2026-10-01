package br.com.esl.etl.v2.bootstrap;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded diagnostics for the synthetic laboratory's external SQL call boundaries. */
final class RuntimePhaseEvidence {
    enum Phase {
        REFERENCE_IMPORT,
        EXPANSION_START,
        RASTER_APPLY,
        SOURCE_REGISTER,
        FRONTIER_REGISTER
    }

    @FunctionalInterface
    interface SqlOperation<T> {
        T run() throws SQLException;
    }

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private RuntimePhaseEvidence() {}

    static <T> T sql(final Phase phase, final SqlOperation<T> operation) throws SQLException {
        return sql(phase, operation, System::nanoTime, System.err::println);
    }

    static <T> T sql(
            final Phase phase,
            final SqlOperation<T> operation,
            final LongSupplier nanos,
            final Consumer<String> output)
            throws SQLException {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(nanos);
        Objects.requireNonNull(output);
        final long started = nanos.getAsLong();
        final long sequence = SEQUENCE.incrementAndGet();
        emit(output, "P08_PHASE phase=" + phase.name() + " sequence=" + sequence + " event=START");
        try {
            final T result = operation.run();
            emit(output, line(phase, sequence, started, nanos.getAsLong(), null));
            return result;
        } catch (final SQLException | RuntimeException | Error failure) {
            emit(output, line(phase, sequence, started, nanos.getAsLong(), failure));
            throw failure;
        }
    }

    private static String line(
            final Phase phase,
            final long sequence,
            final long started,
            final long ended,
            final Throwable failure) {
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(Math.max(0, ended - started));
        final var line =
                new StringBuilder("P08_PHASE phase=")
                        .append(phase.name())
                        .append(" sequence=")
                        .append(sequence)
                        .append(" event=END")
                        .append(" durationMillis=")
                        .append(elapsedMillis);
        if (failure == null) {
            return line.append(" outcome=SUCCESS").toString();
        }
        line.append(" outcome=FAILURE type=").append(type(failure));
        SQLException sql = null;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof SQLException candidate) {
                sql = candidate;
                break;
            }
            current = current.getCause();
        }
        if (sql == null) {
            return line.append(" sqlState=NONE errorCode=NONE").toString();
        }
        final String state = sql.getSQLState();
        return line.append(" sqlState=")
                .append(state != null && state.matches("[A-Z0-9]{5}") ? state : "UNKNOWN")
                .append(" errorCode=")
                .append(sql.getErrorCode())
                .toString();
    }

    private static String type(final Throwable failure) {
        final String simple = failure.getClass().getSimpleName();
        return simple.matches("[A-Za-z0-9_$]{1,80}") ? simple : "UNKNOWN";
    }

    private static void emit(final Consumer<String> output, final String line) {
        try {
            output.accept(line);
        } catch (final RuntimeException ignored) {
            // Evidence failure must not replace the original SQL or test outcome.
        }
    }
}
