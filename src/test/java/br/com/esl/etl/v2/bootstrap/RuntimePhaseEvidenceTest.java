package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class RuntimePhaseEvidenceTest {
    @Test
    void successRecordsStartAndCompletionWithTheSameSequence() throws SQLException {
        for (final var phase : RuntimePhaseEvidence.Phase.values()) {
            final List<String> lines = new ArrayList<>();
            assertEquals(
                    "receipt",
                    RuntimePhaseEvidence.sql(
                            phase, () -> "receipt", ticks(100, 5_100_100), lines::add));
            assertEquals(
                    List.of(
                            "P08_PHASE phase=" + phase + " sequence=N event=START",
                            "P08_PHASE phase="
                                    + phase
                                    + " sequence=N event=END durationMillis=5 outcome=SUCCESS"),
                    withoutSequence(lines));
        }
    }

    @Test
    void sqlFailureRecordsTypeAndCodesWithoutMessageAndPropagatesSameObject() {
        final var failure =
                new SQLTimeoutException(
                        "SELECT private_payload WHERE id=synthetic-secret", "HYT00", 1222);
        final List<String> lines = new ArrayList<>();
        final var caught =
                assertThrows(
                        SQLTimeoutException.class,
                        () ->
                                RuntimePhaseEvidence.sql(
                                        RuntimePhaseEvidence.Phase.REFERENCE_IMPORT,
                                        () -> {
                                            throw failure;
                                        },
                                        ticks(10, 7_000_010),
                                        lines::add));
        assertSame(failure, caught);
        assertEquals(
                List.of(
                        "P08_PHASE phase=REFERENCE_IMPORT sequence=N event=START",
                        "P08_PHASE phase=REFERENCE_IMPORT sequence=N event=END"
                                + " durationMillis=7 outcome=FAILURE"
                                + " type=SQLTimeoutException sqlState=HYT00 errorCode=1222"),
                withoutSequence(lines));
        assertFalse(String.join("\n", lines).contains("private_payload"));
        assertFalse(String.join("\n", lines).contains("synthetic-secret"));
    }

    @Test
    void wrappedFailureSanitizesStateAndKeepsOriginalException() {
        final var sql = new SQLException("sensitive-marker", "SECRET", 77);
        final var failure = new IllegalStateException("payload=sensitive-marker", sql);
        final List<String> lines = new ArrayList<>();
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimePhaseEvidence.sql(
                                        RuntimePhaseEvidence.Phase.EXPANSION_START,
                                        () -> {
                                            throw failure;
                                        },
                                        ticks(20, 1_000_020),
                                        lines::add)));
        assertEquals(
                List.of(
                        "P08_PHASE phase=EXPANSION_START sequence=N event=START",
                        "P08_PHASE phase=EXPANSION_START sequence=N event=END"
                                + " durationMillis=1 outcome=FAILURE"
                                + " type=IllegalStateException sqlState=UNKNOWN errorCode=77"),
                withoutSequence(lines));
        assertFalse(String.join("\n", lines).contains("sensitive-marker"));
    }

    @Test
    void diagnosticSinkFailureDoesNotReplaceSqlFailureOrSuccess() throws SQLException {
        final var failure = new SQLException("private SQL", "42000", 9);
        final var caught =
                assertThrows(
                        SQLException.class,
                        () ->
                                RuntimePhaseEvidence.sql(
                                        RuntimePhaseEvidence.Phase.RASTER_APPLY,
                                        () -> {
                                            throw failure;
                                        },
                                        ticks(0, 1_000_000),
                                        ignored -> {
                                            throw new IllegalStateException("sink failed");
                                        }));
        assertSame(failure, caught);
        assertEquals(
                7,
                RuntimePhaseEvidence.sql(
                        RuntimePhaseEvidence.Phase.RASTER_APPLY,
                        () -> 7,
                        ticks(0, 1_000_000),
                        ignored -> {
                            throw new IllegalStateException("sink failed");
                        }));
        assertTrue(failure.getSuppressed().length == 0);
    }

    @Test
    void frontierStartIsVisibleBeforeTheOperationAndRuntimeFailureIsPreserved() {
        final var failure = new IllegalStateException("private frontier context");
        final List<String> lines = new ArrayList<>();
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimePhaseEvidence.sql(
                                        RuntimePhaseEvidence.Phase.FRONTIER_REGISTER,
                                        () -> {
                                            assertEquals(1, lines.size());
                                            assertTrue(lines.get(0).endsWith(" event=START"));
                                            throw failure;
                                        },
                                        ticks(5, 5_000_005),
                                        lines::add)));
        assertEquals(
                List.of(
                        "P08_PHASE phase=FRONTIER_REGISTER sequence=N event=START",
                        "P08_PHASE phase=FRONTIER_REGISTER sequence=N event=END"
                                + " durationMillis=5 outcome=FAILURE"
                                + " type=IllegalStateException sqlState=NONE errorCode=NONE"),
                withoutSequence(lines));
        assertFalse(String.join("\n", lines).contains("private frontier context"));
    }

    private static LongSupplier ticks(final long first, final long second) {
        final var index = new AtomicInteger();
        return () -> index.getAndIncrement() == 0 ? first : second;
    }

    private static List<String> withoutSequence(final List<String> lines) {
        assertEquals(2, lines.size());
        final var sequence = lines.get(0).replaceFirst("^.* sequence=(\\d+) event=START$", "$1");
        assertTrue(sequence.matches("\\d+"));
        assertTrue(lines.get(1).contains(" sequence=" + sequence + " event=END"));
        return lines.stream()
                .map(line -> line.replace("sequence=" + sequence, "sequence=N"))
                .toList();
    }
}
