package br.com.esl.etl.v2.plataforma.persistencia.devlocal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** DEV-FS-001: limit directory consumption before sorting, retain refusal and complete ordering. */
class DevLocalLedgerJournalLimitTest {
    @Test
    void oversizedInputConsumesOnlyCeilingPlusOneAndStillRefuses() {
        final var consumed = new AtomicInteger();
        try (var files =
                IntStream.range(0, 200_004)
                        .mapToObj(i -> Path.of("synthetic-entry-" + i))
                        .peek(ignored -> consumed.incrementAndGet())) {
            final var failure =
                    assertThrows(
                            DevLocalLedger.Failure.class,
                            () -> DevLocalLedger.journalEntriesLimited(files, 100_000));
            assertEquals("JOURNAL_LIMIT", failure.code());
            assertEquals(100_001, consumed.get());
        }
    }

    @Test
    void unlimitedInputTerminatesAtTheFiniteSentinel() {
        final var consumed = new AtomicInteger();
        try (var files =
                Stream.generate(
                        () -> {
                            final int next = consumed.incrementAndGet();
                            if (next > 100_001) {
                                throw new AssertionError("DIRECTORY_CONSUMPTION_EXCEEDED_SENTINEL");
                            }
                            return Path.of("synthetic-entry-" + next);
                        })) {
            final var failure =
                    assertThrows(
                            DevLocalLedger.Failure.class,
                            () -> DevLocalLedger.journalEntriesLimited(files, 100_000));
            assertEquals("JOURNAL_LIMIT", failure.code());
            assertEquals(100_001, consumed.get());
        }
    }

    @Test
    void exactCeilingRetainsAndSortsEveryEntry() throws Exception {
        final var entries =
                List.of(
                        Path.of("00000004.entry"),
                        Path.of("00000002.entry"),
                        Path.of("00000003.entry"),
                        Path.of("00000001.entry"));
        final var consumed = new AtomicInteger();
        try (var files = entries.stream().peek(ignored -> consumed.incrementAndGet())) {
            assertEquals(
                    entries.stream().sorted().toList(),
                    DevLocalLedger.journalEntriesLimited(files, 4));
        }
        assertEquals(4, consumed.get());
    }

    @Test
    void ceilingCannotBeExpandedByTheInternalCaller() {
        final var consumed = new AtomicInteger();
        for (final int invalid : new int[] {0, -1, 100_001, Integer.MAX_VALUE}) {
            try (var files =
                    Stream.of(Path.of("synthetic.entry"))
                            .peek(ignored -> consumed.incrementAndGet())) {
                final var failure =
                        assertThrows(
                                DevLocalLedger.Failure.class,
                                () -> DevLocalLedger.journalEntriesLimited(files, invalid));
                assertEquals("JOURNAL_LIMIT", failure.code());
            }
        }
        assertEquals(0, consumed.get());
    }

    @Test
    void realJournalRoundtripKeepsCompleteOrderingAndRetainedBytes() throws Exception {
        final Path parent =
                Path.of(
                                System.getProperty(
                                        "dev.local.tests.root",
                                        "target/dev-local-b77c5029cfbd/banco/journal-limit-fixtures"))
                        .toAbsolutePath();
        Files.createDirectories(parent);
        final Path workspace = parent.resolve("roundtrip-" + UUID.randomUUID());
        final byte[] input = "SYNTHETIC_INPUT".getBytes(StandardCharsets.UTF_8);
        final byte[] output = "SYNTHETIC_RETAINED".getBytes(StandardCharsets.UTF_8);
        final String digest = DevLocalLedger.sha256(input);
        final DevLocalLedger.Run expected;
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", digest);
            for (int ordinal = 1; ordinal <= 3; ordinal++) {
                ledger.event("run1", "OBSERVED", "SYNTHETIC_EVENT_" + ordinal);
            }
            ledger.output("run1", "result.txt", output);
            expected = ledger.finish("run1", DevLocalLedger.State.COMPLETED, "LOCAL_COMPLETE");
        }
        final List<Path> reversed;
        try (var files = Files.list(workspace.resolve("journal"))) {
            reversed = files.sorted(Comparator.reverseOrder()).toList();
        }
        assertEquals(6, reversed.size());
        try (var files = reversed.stream()) {
            final var ordered = DevLocalLedger.journalEntriesLimited(files, 100_000);
            assertEquals(reversed.stream().sorted().toList(), ordered);
            assertEquals("00000001.entry", ordered.get(0).getFileName().toString());
            assertEquals("00000006.entry", ordered.get(5).getFileName().toString());
        }
        final var hashes = reversed.stream().map(DevLocalLedgerJournalLimitTest::hashFile).toList();
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(expected, ledger.readback("run1"));
            assertEquals(expected, ledger.start("run1", digest));
            assertArrayEquals(output, ledger.readOutput("run1", "result.txt"));
            for (int ordinal = 1; ordinal <= 3; ordinal++) {
                assertEquals("SYNTHETIC_EVENT_" + ordinal, ledger.readEvent("run1", ordinal));
            }
            assertTrue(ledger.orphanFiles("run1").isEmpty());
        }
        assertEquals(
                hashes, reversed.stream().map(DevLocalLedgerJournalLimitTest::hashFile).toList());
        assertArrayEquals(
                output, Files.readAllBytes(workspace.resolve("runs/run1/outputs/result.txt")));
    }

    private static String hashFile(final Path path) {
        try {
            return DevLocalLedger.sha256(Files.readAllBytes(path));
        } catch (final java.io.IOException failure) {
            throw new AssertionError("SYNTHETIC_FIXTURE_READ_FAILED", failure);
        }
    }
}
