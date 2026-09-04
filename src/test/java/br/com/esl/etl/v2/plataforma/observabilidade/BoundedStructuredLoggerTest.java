package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedStructuredLoggerTest {

    private static final CorrelationReference CORRELATION =
            CorrelationReference.fromTechnicalScope("SYNTHETIC_TEST_SCOPE");
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void emitsWithinBudgetThenOneBoundedExhaustionSummary() {
        final List<StructuredLogEvent> events = new ArrayList<>();
        final BoundedStructuredLogger logger = new BoundedStructuredLogger(events::add, 2, 16_384);

        logger.write(event("FIRST_EVENT"));
        logger.write(event("SECOND_EVENT"));
        logger.write(event("THIRD_EVENT"));
        logger.write(event("FOURTH_EVENT"));

        assertEquals(3, events.size());
        assertEquals("LOG_BUDGET_EXHAUSTED", events.get(2).eventCode());
        assertEquals(3, logger.snapshot().emittedEvents());
        assertEquals(2, logger.snapshot().emittedPrimaryEvents());
        assertTrue(logger.snapshot().emittedBytes() > 0);
        assertEquals(2, logger.snapshot().droppedEvents());
        assertTrue(logger.snapshot().exhausted());
        assertTrue(logger.snapshot().exhaustionSummaryAttempted());
    }

    @Test
    void validatesClosedEventShapeAndBudget() {
        assertThrows(NullPointerException.class, () -> new BoundedStructuredLogger(null, 1, 1024));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedStructuredLogger(ignored -> {}, 0, 1024));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedStructuredLogger(ignored -> {}, 1_000_001, 1024));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedStructuredLogger(ignored -> {}, 1, 1023));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StructuredLogEvent(
                                LogSeverity.INFO,
                                "bad-event",
                                "COMPONENT",
                                "OK",
                                CORRELATION,
                                1,
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StructuredLogEvent(
                                LogSeverity.INFO,
                                "VALID_EVENT",
                                "COMPONENT",
                                "OK",
                                CORRELATION,
                                -1,
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StructuredLogBudgetSnapshot(1, 2, 0, 0, true, true));
        assertTrue(event("SAFE_EVENT").toString().contains("SAFE_EVENT"));
    }

    @Test
    void byteBudgetStopsBeforeWritingAnOversizedSequence() {
        final List<StructuredLogEvent> events = new ArrayList<>();
        final BoundedStructuredLogger logger = new BoundedStructuredLogger(events::add, 100, 1024);

        logger.write(event("FIRST_EVENT"));
        logger.write(event("SECOND_EVENT"));

        assertEquals(1, logger.snapshot().emittedEvents());
        assertEquals(1, logger.snapshot().emittedPrimaryEvents());
        assertEquals(1, logger.snapshot().droppedEvents());
        assertTrue(logger.snapshot().emittedBytes() <= 1024);
        assertTrue(events.size() <= 2);
    }

    @Test
    void doesNotClaimEmissionWhenTheSinkRejectsThePrimaryEvent() {
        final IllegalStateException cause = new IllegalStateException("synthetic-sink-failure");
        final BoundedStructuredLogger logger =
                new BoundedStructuredLogger(
                        ignored -> {
                            throw cause;
                        },
                        1,
                        16_384);

        assertEquals(
                cause,
                assertThrows(
                        IllegalStateException.class, () -> logger.write(event("VALID_EVENT"))));
        assertEquals(0, logger.snapshot().emittedEvents());
        assertEquals(0, logger.snapshot().emittedPrimaryEvents());
        assertEquals(0, logger.snapshot().emittedBytes());
        assertEquals(0, logger.snapshot().droppedEvents());
        assertFalse(logger.snapshot().exhausted());
    }

    @Test
    void recordsAnAttemptButNotAnEmissionWhenTheSummarySinkFails() {
        final IllegalStateException cause = new IllegalStateException("synthetic-sink-failure");
        final List<StructuredLogEvent> events = new ArrayList<>();
        final BoundedStructuredLogger logger =
                new BoundedStructuredLogger(
                        event -> {
                            if (event.eventCode().equals("LOG_BUDGET_EXHAUSTED")) {
                                throw cause;
                            }
                            events.add(event);
                        },
                        1,
                        16_384);

        logger.write(event("PRIMARY_EVENT"));
        assertEquals(
                cause,
                assertThrows(
                        IllegalStateException.class, () -> logger.write(event("DROPPED_EVENT"))));

        assertEquals(1, events.size());
        assertEquals(1, logger.snapshot().emittedEvents());
        assertEquals(1, logger.snapshot().emittedPrimaryEvents());
        assertEquals(1, logger.snapshot().droppedEvents());
        assertTrue(logger.snapshot().exhaustionSummaryAttempted());
        assertTrue(logger.snapshot().exhausted());
    }

    private static StructuredLogEvent event(final String code) {
        return new StructuredLogEvent(
                LogSeverity.INFO, code, "COMPONENT", "SUCCESS", CORRELATION, 1, NOW);
    }
}
