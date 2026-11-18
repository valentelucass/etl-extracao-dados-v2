package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignJournalEnumerationSnapshotTest {
    private static final String SELECTION = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    private static final String EVIDENCE = "c".repeat(64);

    @TempDir Path directory;

    @Test
    void enumeratedSnapshotsStillProduceOrderedEventsAndExactPhysicalHashes() throws Exception {
        final var journal = campaign("ordered");
        journal.appendFactRecord("first-fact", nonce(1), EVIDENCE);
        journal.appendFactRecord("second-fact", nonce(2), EVIDENCE);
        final var expected = journal.read();
        final var bytes = new ArrayList<byte[]>();
        for (int ordinal = 1; ordinal <= 6; ordinal++) {
            bytes.add(Files.readAllBytes(event(journal, ordinal)));
            Files.delete(event(journal, ordinal));
        }
        // Physical creation order does not define journal order.
        for (int ordinal = 6; ordinal >= 1; ordinal--) {
            Files.write(event(journal, ordinal), bytes.get(ordinal - 1));
        }
        final var actual = journal.read();
        assertEquals(expected, actual);
        assertEquals(actual, CampaignJournal.open(journal.root(), SELECTION, CODE).read());
        String previous = digest(Files.readAllBytes(journal.root().resolve("header.json")));
        for (int index = 0; index < actual.size(); index++) {
            final var item = actual.get(index);
            assertEquals(index + 1, item.sequence());
            assertEquals(previous, item.previous());
            assertArrayEquals(bytes.get(index), Files.readAllBytes(event(journal, index + 1)));
            assertEquals(digest(bytes.get(index)), item.sha256());
            previous = item.sha256();
        }
    }

    @Test
    void eachNewReadReopensPhysicalFilesAndDoesNotReuseAnEarlierPass() throws Exception {
        final var journal = campaign("fresh");
        journal.append("live-case", nonce(3), CampaignJournal.Kind.RESERVED, "FACT_APPEND");
        final var first = journal.read();
        journal.append("live-case", nonce(3), CampaignJournal.Kind.STARTED, "FACT_APPEND");
        final var second = journal.read();
        assertEquals(1, first.size());
        assertEquals(2, second.size());
        assertNotSame(first, second);
        assertEquals(first.get(0), second.get(0));
        final byte[] retained = Files.readAllBytes(event(journal, 2));
        Files.write(event(journal, 2), new byte[] {(byte) 0xc3, 0x28});
        assertThrows(IllegalArgumentException.class, journal::read);
        Files.write(event(journal, 2), retained);
        assertEquals(second, journal.read());
    }

    @Test
    void changedHeaderLiteralBytesOrBindingAreRejectedOnReadAndFullReopen() throws Exception {
        final var journal = campaign("header");
        journal.appendFactRecord("header-fact", nonce(4), EVIDENCE);
        final Path header = journal.root().resolve("header.json");
        final String retained = Files.readString(header);
        for (final String changed :
                new String[] {retained + " \n", retained.replace(SELECTION, "d".repeat(64))}) {
            Files.writeString(header, changed);
            assertThrows(IllegalArgumentException.class, journal::read);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CampaignJournal.open(journal.root(), SELECTION, CODE));
        }
        Files.writeString(header, retained);
        assertEquals(3, journal.read().size());
    }

    @Test
    void changedHistoricalEventBytesAndTransitionBindingsRemainRejected() throws Exception {
        final var journal = campaign("event");
        journal.appendFactRecord("event-fact", nonce(5), EVIDENCE);
        final Path first = event(journal, 1);
        final String retained = Files.readString(first);
        for (final String changed :
                new String[] {
                    retained + " \n",
                    retained.replace("event-fact", "other-fact"),
                    retained.replace("FACT_APPEND", "FACT_XPPEND")
                }) {
            Files.writeString(first, changed);
            assertThrows(IllegalArgumentException.class, journal::read);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CampaignJournal.open(journal.root(), SELECTION, CODE));
        }
        Files.writeString(first, retained);
        assertEquals(3, journal.read().size());
    }

    @Test
    void headerLockEventsAndUnknownEntriesMustRemainRegularFiles() throws Exception {
        for (final String leaf : new String[] {"header.json", "journal.lock", "0001.json"}) {
            final var journal = campaign("regular-" + leaf.replace('.', '-'));
            journal.append("regular-case", nonce(6), CampaignJournal.Kind.RESERVED, "FACT_APPEND");
            final Path path = journal.root().resolve(leaf);
            Files.delete(path);
            Files.createDirectory(path);
            assertThrows(IllegalArgumentException.class, journal::read);
        }
        for (final String leaf : new String[] {"unexpected.json", "0001.json.partial"}) {
            final var journal = campaign("unknown-" + leaf.replace('.', '-'));
            Files.writeString(journal.root().resolve(leaf), "{}");
            final var refused = assertThrows(IllegalArgumentException.class, journal::read);
            assertEquals("QUAL_JOURNAL_UNDECLARED_FILE", refused.getMessage());
        }
    }

    @Test
    void enumerationReadsRetainByteBoundsStrictJsonUtf8AndEof() throws Exception {
        final var journal = campaign("bounded");
        journal.appendFactRecord("bounded-fact", nonce(7), EVIDENCE);
        final Path last = event(journal, 3);
        final byte[] retained = Files.readAllBytes(last);
        final String text = new String(retained, StandardCharsets.UTF_8);
        final byte[] exact =
                (text + " ".repeat(4096 - retained.length)).getBytes(StandardCharsets.UTF_8);
        Files.write(last, exact);
        final var changed = journal.read();
        assertEquals(4096, exact.length);
        assertEquals(digest(exact), changed.get(2).sha256());
        assertNotEquals(digest(retained), changed.get(2).sha256());
        Files.writeString(last, text + " ".repeat(4097 - retained.length));
        assertThrows(IllegalArgumentException.class, journal::read);
        Files.writeString(last, text.replace("\"version\":1", "\"version\":1,\"version\":1"));
        assertThrows(IOException.class, journal::read);
        Files.writeString(last, text + "{}");
        assertThrows(IOException.class, journal::read);
        Files.write(last, new byte[] {(byte) 0xc3, 0x28});
        assertThrows(IllegalArgumentException.class, journal::read);
        Files.write(last, retained);
        assertEquals(3, journal.read().size());
        final var empty = campaign("header-bound");
        final Path header = empty.root().resolve("header.json");
        final String headerText = Files.readString(header);
        Files.writeString(header, headerText + " ".repeat(1024 - headerText.length()));
        assertEquals(0, empty.read().size());
        Files.writeString(header, headerText + " ".repeat(1025 - headerText.length()));
        assertThrows(IllegalArgumentException.class, empty::read);
    }

    @Test
    void full512EventChainRemainsAdmittedAndAnExtraEntryCannotRaiseTheBound() throws Exception {
        final var journal = campaign("limit");
        String previous = digest(Files.readAllBytes(journal.root().resolve("header.json")));
        // Complete synthetic chain seed avoids hundreds of durable append cycles in this fixture.
        for (int ordinal = 1; ordinal <= 512; ordinal++) {
            final var node =
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("version", 1)
                            .put("sequence", ordinal)
                            .put("campaign", SELECTION)
                            .put("package", CODE)
                            .put("previous", previous)
                            .put("case", "seed-" + ordinal)
                            .put("nonce", nonce(1000 + ordinal).toString())
                            .put("kind", "RESERVED")
                            .put("detail", "FACT_APPEND");
            final byte[] bytes = (node + "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(event(journal, ordinal), bytes);
            previous = digest(bytes);
        }
        final var events = journal.read();
        assertEquals(512, events.size());
        assertEquals(previous, events.get(511).sha256());
        Files.writeString(journal.root().resolve("0513.json"), "{}");
        final var refused = assertThrows(IllegalArgumentException.class, journal::read);
        assertEquals("QUAL_JOURNAL_UNDECLARED_FILE", refused.getMessage());
    }

    @Test
    void persistentFactRoundtripAndTamperChecksStillUseIndependentFullPasses() throws Exception {
        final Path root = directory.resolve("persistent");
        final UUID attempt = nonce(8);
        final String payload;
        try (var journal = PersistentExportJournal.create(root, SELECTION, CODE, attempt)) {
            payload =
                    journal.record(
                            "AUTHORED_FACT", JsonNodeFactory.instance.objectNode().put("value", 1));
            assertEquals(1, journal.latest("AUTHORED_FACT").required("value").intValue());
            assertEquals(1, journal.count("AUTHORED_FACT"));
            assertEquals(payload, journal.latestPin("AUTHORED_FACT").sha256());
        }
        try (var journal = PersistentExportJournal.open(root, SELECTION, CODE, attempt)) {
            assertEquals(1, journal.latest("AUTHORED_FACT").required("value").intValue());
            final Path path = root.resolve("facts").resolve(payload + ".json");
            final byte[] retained = Files.readAllBytes(path);
            assertEquals(payload, digest(retained));
            Files.writeString(path, "{\"value\":2}");
            assertThrows(IllegalArgumentException.class, () -> journal.latest("AUTHORED_FACT"));
            Files.write(path, retained);
            assertEquals(1, journal.latest("AUTHORED_FACT").required("value").intValue());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> PersistentExportJournal.open(root, "d".repeat(64), CODE, attempt));
    }

    private CampaignJournal campaign(final String name) throws IOException {
        return CampaignJournal.create(directory.resolve(name), SELECTION, CODE);
    }

    private static Path event(final CampaignJournal journal, final int ordinal) {
        return journal.root().resolve(String.format(Locale.ROOT, "%04d.json", ordinal));
    }

    private static UUID nonce(final int ordinal) {
        return UUID.nameUUIDFromBytes(
                ("authored-enumeration-" + ordinal).getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
