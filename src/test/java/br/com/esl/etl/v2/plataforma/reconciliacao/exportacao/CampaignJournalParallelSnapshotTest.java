package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignJournalParallelSnapshotTest {
    private static final String SHA = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    private static final String PREFIX = "qualification-journal-snapshot-";
    @TempDir Path directory;
    private int ordinal;

    @Test
    void repeatedFreshReadsKeepEveryEventHashAndLiteralFileUnchangedWithNoLiveWorkers()
            throws Exception {
        final var journal = populated(8);
        final var expected = journal.read();
        final var before = hashes(journal.root());
        for (int pass = 0; pass < 3; pass++) {
            assertEquals(expected, journal.read());
            assertNoWorkers();
        }
        assertEquals(before, hashes(journal.root()));
        assertEquals(expected, CampaignJournal.open(journal.root(), SHA, CODE).read());
        assertNoWorkers();
    }

    @Test
    void laterPassCannotReuseEarlierSnapshotAfterEventBytesChange() throws Exception {
        final var journal = populated(2);
        assertEquals(6, journal.read().size());
        final Path first = journal.root().resolve("0001.json");
        Files.writeString(first, Files.readString(first) + " ", StandardCharsets.UTF_8);
        final var refused = assertThrows(IllegalArgumentException.class, journal::read);
        assertEquals("QUAL_JOURNAL_CHAIN", refused.getMessage());
        assertNoWorkers();
    }

    @Test
    void headerLockAndUnexpectedEntryStillRefuseWithoutLeavingReaders() throws Exception {
        final var changedHeader = populated(1);
        final Path header = changedHeader.root().resolve("header.json");
        Files.writeString(header, Files.readString(header).replace(CODE, "c".repeat(64)));
        assertEquals(
                "QUAL_JOURNAL_REVISION",
                assertThrows(IllegalArgumentException.class, changedHeader::read).getMessage());
        assertNoWorkers();
        final var changedLock = populated(1);
        Files.delete(changedLock.root().resolve("journal.lock"));
        Files.createDirectory(changedLock.root().resolve("journal.lock"));
        assertEquals(
                "QUAL_FILE_REGULAR_REQUIRED",
                assertThrows(IllegalArgumentException.class, changedLock::read).getMessage());
        assertNoWorkers();
        final var extra = populated(1);
        Files.writeString(extra.root().resolve("unexpected.json"), "{}");
        assertEquals(
                "QUAL_JOURNAL_UNDECLARED_FILE",
                assertThrows(IllegalArgumentException.class, extra::read).getMessage());
        assertNoWorkers();
    }

    @Test
    void snapshotFailuresKeepTheirActualUtf8SizeAndJsonExceptionTypesAndJoinReaders()
            throws Exception {
        for (final byte[] invalid :
                new byte[][] {
                    new byte[] {(byte) 0xc3, 0x28},
                    new byte[0],
                    " ".repeat(4097).getBytes(StandardCharsets.UTF_8)
                }) {
            final var journal = campaign();
            Files.write(journal.root().resolve("0001.json"), invalid);
            final var refused = assertThrows(IllegalArgumentException.class, journal::read);
            assertTrue(
                    "QUAL_JSON_UTF8".equals(refused.getMessage())
                            || "QUAL_JSON_SIZE".equals(refused.getMessage()));
            assertNoWorkers();
        }
        for (final String invalid : new String[] {"{\"v\":1,\"v\":2}", "{} {}"}) {
            final var journal = campaign();
            Files.writeString(journal.root().resolve("0001.json"), invalid);
            assertThrows(IOException.class, journal::read);
            assertNoWorkers();
        }
    }

    @Test
    void concurrentFreshCallsKeepTheirOwnEventsAndJoinAllReadersBeforeTheCallersContinue()
            throws Exception {
        final var first = populated(4);
        final var second = populated(6);
        final var ready = new CountDownLatch(1);
        final var start = new CountDownLatch(1);
        final var failure = new AtomicReference<Throwable>();
        final var coordinator =
                new Thread(
                        () -> {
                            try {
                                ready.countDown();
                                if (!start.await(40, TimeUnit.SECONDS)) {
                                    throw new AssertionError(
                                            "AUTHORED_SNAPSHOT_START_NOT_RELEASED");
                                }
                                assertEquals(12, first.read().size());
                            } catch (final Throwable refused) {
                                failure.set(refused);
                            }
                        },
                        "authored-snapshot-coordinator");
        coordinator.start();
        try {
            assertTrue(ready.await(40, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(18, second.read().size());
        } finally {
            start.countDown();
            coordinator.join(TimeUnit.SECONDS.toMillis(40));
            if (coordinator.isAlive()) {
                coordinator.interrupt();
                coordinator.join(TimeUnit.SECONDS.toMillis(5));
            }
        }
        assertFalse(coordinator.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("AUTHORED_SNAPSHOT_READ_FAILED", failure.get());
        }
        assertNoWorkers();
        assertEquals(12, first.read().size());
        assertEquals(18, second.read().size());
        assertNoWorkers();
    }

    @Test
    void persistentFactsRemainForcedPinnedAndReopenableAcrossParallelControlReads()
            throws Exception {
        final Path root = next();
        final UUID nonce =
                UUID.nameUUIDFromBytes("authored-persistent".getBytes(StandardCharsets.UTF_8));
        try (var journal = PersistentExportJournal.create(root, SHA, CODE, nonce)) {
            final String pin =
                    journal.record(
                            "AUTHORED_A",
                            JsonNodeFactory.instance.objectNode().put("literal", "A B"));
            assertEquals(pin, journal.pin(pin).sha256());
            journal.record("AUTHORED_B", JsonNodeFactory.instance.objectNode().put("number", 7));
            assertEquals("A B", journal.latest("AUTHORED_A").required("literal").textValue());
            assertEquals(1L, journal.count("AUTHORED_B"));
            assertNoWorkers();
        }
        final var before = hashes(root);
        try (var reopened = PersistentExportJournal.open(root, SHA, CODE, nonce)) {
            assertEquals(7, reopened.latest("AUTHORED_B").required("number").intValue());
            assertNoWorkers();
        }
        assertEquals(before, hashes(root));
    }

    @Test
    void interruptedCallerKeepsItsInterruptAndCannotAcceptOrLeaveSnapshotReaders()
            throws Exception {
        final var journal = populated(1);
        try {
            Thread.currentThread().interrupt();
            assertThrows(IOException.class, journal::read);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertNoWorkers();
        assertEquals(3, journal.read().size());
        assertNoWorkers();
    }

    @Test
    void pathDirectoryAnd513EntryBoundsRemainFailClosedWithAllReadersJoined() throws Exception {
        final var eventDirectory = campaign();
        Files.createDirectory(eventDirectory.root().resolve("0001.json"));
        assertEquals(
                "QUAL_FILE_REGULAR_REQUIRED",
                assertThrows(IllegalArgumentException.class, eventDirectory::read).getMessage());
        assertNoWorkers();
        final var overflow = campaign();
        for (int number = 1; number <= 513; number++) {
            Files.writeString(
                    overflow.root()
                            .resolve(String.format(java.util.Locale.ROOT, "%04d.json", number)),
                    "{}");
        }
        assertEquals(
                "QUAL_JOURNAL_UNDECLARED_FILE",
                assertThrows(IllegalArgumentException.class, overflow::read).getMessage());
        assertNoWorkers();
    }

    private CampaignJournal campaign() throws IOException {
        return CampaignJournal.create(next(), SHA, CODE);
    }

    private CampaignJournal populated(final int facts) throws IOException {
        final var journal = campaign();
        for (int number = 1; number <= facts; number++) {
            journal.appendFactRecord(
                    "authored-" + number,
                    UUID.nameUUIDFromBytes(("nonce-" + number).getBytes(StandardCharsets.UTF_8)),
                    "c".repeat(64));
            assertNoWorkers();
        }
        return journal;
    }

    private Path next() {
        return directory.resolve("authored-parallel-" + ++ordinal);
    }

    private static Map<String, String> hashes(final Path root) throws IOException {
        final var result = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                result.put(
                        root.relativize(path).toString(),
                        QualificationJson.sha256(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private static void assertNoWorkers() {
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(PREFIX)) {
                assertFalse(thread.isAlive());
            }
        }
    }
}
