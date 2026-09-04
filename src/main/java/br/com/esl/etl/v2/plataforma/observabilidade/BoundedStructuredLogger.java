package br.com.esl.etl.v2.plataforma.observabilidade;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Budget O(1) por instância/componente, limitado simultaneamente por eventos e bytes. */
public final class BoundedStructuredLogger {

    private static final long MAXIMUM_EVENT_LIMIT = 1_000_000L;
    private static final long MINIMUM_BYTE_LIMIT = 1_024L;
    private static final long MAXIMUM_BYTE_LIMIT = 1_073_741_824L;
    private static final long JSON_ENVELOPE_UPPER_BOUND_BYTES = 512L;

    private final StructuredLogSink sink;
    private final long maximumPrimaryEvents;
    private final long maximumBytes;
    private long emittedEvents;
    private long emittedPrimaryEvents;
    private long emittedBytes;
    private long droppedEvents;
    private boolean exhaustionSummaryAttempted;

    public BoundedStructuredLogger(
            final StructuredLogSink sink,
            final long maximumPrimaryEvents,
            final long maximumBytes) {
        this.sink = Objects.requireNonNull(sink, "O sink estruturado é obrigatório.");
        if (maximumPrimaryEvents < 1 || maximumPrimaryEvents > MAXIMUM_EVENT_LIMIT) {
            throw new IllegalArgumentException(
                    "O limite de eventos deve estar entre 1 e 1.000.000.");
        }
        if (maximumBytes < MINIMUM_BYTE_LIMIT || maximumBytes > MAXIMUM_BYTE_LIMIT) {
            throw new IllegalArgumentException("O limite de bytes deve estar entre 1 KiB e 1 GiB.");
        }
        this.maximumPrimaryEvents = maximumPrimaryEvents;
        this.maximumBytes = maximumBytes;
    }

    public synchronized void write(final StructuredLogEvent event) {
        final StructuredLogEvent required =
                Objects.requireNonNull(event, "O evento estruturado é obrigatório.");
        final long requiredBytes = encodedUpperBound(required);
        if (emittedPrimaryEvents < maximumPrimaryEvents && fitsByteBudget(requiredBytes)) {
            sink.write(required);
            emittedEvents++;
            emittedPrimaryEvents++;
            emittedBytes = Math.addExact(emittedBytes, requiredBytes);
            return;
        }

        droppedEvents = Math.addExact(droppedEvents, 1);
        if (!exhaustionSummaryAttempted) {
            final StructuredLogEvent summary =
                    new StructuredLogEvent(
                            LogSeverity.WARN,
                            "LOG_BUDGET_EXHAUSTED",
                            required.componentCode(),
                            "DROPPED",
                            required.correlationReference(),
                            1,
                            required.occurredAt());
            final long summaryBytes = encodedUpperBound(summary);
            exhaustionSummaryAttempted = true;
            if (fitsByteBudget(summaryBytes)) {
                sink.write(summary);
                emittedEvents++;
                emittedBytes = Math.addExact(emittedBytes, summaryBytes);
            }
        }
    }

    public synchronized StructuredLogBudgetSnapshot snapshot() {
        return new StructuredLogBudgetSnapshot(
                emittedEvents,
                emittedPrimaryEvents,
                emittedBytes,
                droppedEvents,
                exhaustionSummaryAttempted,
                droppedEvents > 0);
    }

    private boolean fitsByteBudget(final long requiredBytes) {
        return requiredBytes <= maximumBytes - emittedBytes;
    }

    private static long encodedUpperBound(final StructuredLogEvent event) {
        return Math.addExact(
                JSON_ENVELOPE_UPPER_BOUND_BYTES,
                utf8Bytes(event.severity().name())
                        + utf8Bytes(event.eventCode())
                        + utf8Bytes(event.componentCode())
                        + utf8Bytes(event.outcomeCode())
                        + utf8Bytes(event.correlationReference().sha256())
                        + utf8Bytes(Long.toString(event.occurrenceCount()))
                        + utf8Bytes(event.occurredAt().toString()));
    }

    private static long utf8Bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
