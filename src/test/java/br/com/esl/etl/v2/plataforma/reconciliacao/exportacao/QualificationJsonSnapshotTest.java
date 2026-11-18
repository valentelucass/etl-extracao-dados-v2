package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationJsonSnapshotTest {
    private static final String SELECTION = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    private static final String EVIDENCE = "c".repeat(64);

    @TempDir Path directory;

    @Test
    void nodeAndHashComeFromTheExactPhysicalBytesRatherThanReserializedJson() throws Exception {
        final byte[] bytes =
                "{ \"amount\" : 1.2300, \"zero\" : -0, \"text\" : \"a\\u0301\" }\r\n"
                        .getBytes(StandardCharsets.UTF_8);
        final Path path = Files.write(directory.resolve("exact.json"), bytes);
        final var snapshot = QualificationJson.readSnapshot(path, bytes.length);
        assertEquals(digest(bytes), snapshot.sha256());
        assertEquals(QualificationJson.sha256(path), snapshot.sha256());
        assertEquals(QualificationJson.read(path, bytes.length), snapshot.node());
        assertEquals("1.2300", snapshot.node().required("amount").decimalValue().toPlainString());
        assertEquals("a\u0301", snapshot.node().required("text").textValue());
        assertNotEquals(
                digest(snapshot.node().toString().getBytes(StandardCharsets.UTF_8)),
                snapshot.sha256());
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    @Test
    void changedLaterPassIsReadAgainAndEarlierNodesAreNotAContentCache() throws Exception {
        final Path path = Files.writeString(directory.resolve("changed.json"), "{\"value\":1}");
        final var first = QualificationJson.readSnapshot(path, 1024);
        Files.writeString(path, "{\"value\":2}");
        final var second = QualificationJson.readSnapshot(path, 1024);
        assertEquals(1, first.node().required("value").intValue());
        assertEquals(2, second.node().required("value").intValue());
        assertNotEquals(first.sha256(), second.sha256());
        assertEquals(digest(Files.readAllBytes(path)), second.sha256());
        ((ObjectNode) first.node()).put("value", 99);
        final var third = QualificationJson.readSnapshot(path, 1024);
        assertNotSame(second.node(), third.node());
        assertEquals(2, third.node().required("value").intValue());
        Files.writeString(path, "{\"value\":1,\"value\":2}");
        assertThrows(IOException.class, () -> QualificationJson.readSnapshot(path, 1024));
        Files.writeString(path, "{\"value\":3}");
        assertEquals(
                3, QualificationJson.readSnapshot(path, 1024).node().required("value").intValue());
    }

    @Test
    void modifiedHeaderIsRefusedByTheNextLivePassAndFullReopen() throws Exception {
        final var journal = campaign("header");
        journal.appendFactRecord("authored-fact", UUID.randomUUID(), EVIDENCE);
        final Path header = journal.root().resolve("header.json");
        final String retained = Files.readString(header);
        Files.writeString(header, retained.replace(SELECTION, "d".repeat(64)));
        assertThrows(IllegalArgumentException.class, journal::read);
        assertThrows(
                IllegalArgumentException.class,
                () -> CampaignJournal.open(journal.root(), SELECTION, CODE));
        Files.writeString(header, retained);
        assertEquals(3, CampaignJournal.open(journal.root(), SELECTION, CODE).read().size());
    }

    @Test
    void historicalEventWhitespaceOrValueTamperingIsNotHiddenByCanonicalHashing() throws Exception {
        final var journal = campaign("event");
        journal.appendFactRecord("authored-fact", UUID.randomUUID(), EVIDENCE);
        final Path event = journal.root().resolve("0001.json");
        final String retained = Files.readString(event);
        for (final String changed :
                new String[] {retained + " \n", retained.replace("FACT_APPEND", "FACT_XPPEND")}) {
            Files.writeString(event, changed);
            assertThrows(IllegalArgumentException.class, journal::read);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CampaignJournal.open(journal.root(), SELECTION, CODE));
        }
        Files.writeString(event, retained);
        assertEquals(3, journal.read().size());
    }

    @Test
    void strictDuplicateUtf8AndParserConstraintsRemainIndependentAcrossSnapshots()
            throws Exception {
        final Path path = directory.resolve("strict.json");
        for (final String invalid :
                new String[] {
                    "{\"x\":1,\"x\":2}", "1".repeat(41), "[".repeat(25) + "0" + "]".repeat(25)
                }) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> QualificationJson.readSnapshot(path, 4096));
        }
        Files.write(path, new byte[] {(byte) 0xc3, 0x28});
        assertThrows(
                IllegalArgumentException.class, () -> QualificationJson.readSnapshot(path, 4096));
        for (final String invalid : new String[] {"\ufeff{}", "{\"x\":\"\ufffd\"}"}) {
            Files.writeString(path, invalid, StandardCharsets.UTF_8);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualificationJson.readSnapshot(path, 4096));
        }
        Files.writeString(path, "{\"text\":\"ação日本\"}", StandardCharsets.UTF_8);
        assertEquals(
                "ação日本",
                QualificationJson.readSnapshot(path, 4096).node().required("text").textValue());
    }

    @Test
    void genericAndBatchEventsKeepByteEqualLegacyFilesHashesAndFullReopen() throws Exception {
        final var legacy = campaign("legacy-files");
        final var batched = campaign("batch-files");
        final UUID nonce = UUID.randomUUID();
        legacy.append("authored-fact", nonce, CampaignJournal.Kind.RESERVED, "FACT_APPEND");
        legacy.append("authored-fact", nonce, CampaignJournal.Kind.STARTED, "FACT_APPEND");
        legacy.append("authored-fact", nonce, CampaignJournal.Kind.EVIDENCE, EVIDENCE);
        batched.appendFactRecord("authored-fact", nonce, EVIDENCE);
        final var reopened = CampaignJournal.open(batched.root(), SELECTION, CODE);
        assertEquals(legacy.read(), reopened.read());
        for (int sequence = 1; sequence <= 3; sequence++) {
            final String name = String.format(Locale.ROOT, "%04d.json", sequence);
            final Path file = reopened.root().resolve(name);
            assertArrayEquals(
                    Files.readAllBytes(legacy.root().resolve(name)), Files.readAllBytes(file));
            assertEquals(
                    QualificationJson.sha256(file), reopened.read().get(sequence - 1).sha256());
            assertEquals(
                    QualificationJson.read(file, 4096),
                    QualificationJson.readSnapshot(file, 4096).node());
            assertFalse(Files.exists(reopened.root().resolve(name + ".partial")));
        }
    }

    @Test
    void persistentReopenStillRevalidatesEnvelopeAndPayloadBytesIndependently() throws Exception {
        final Path cycle = directory.resolve("persistent");
        final UUID attempt = UUID.randomUUID();
        final var payload = ExportValidationSupport.JSON.createObjectNode().put("value", 7);
        final String payloadSha;
        try (var journal = PersistentExportJournal.create(cycle, SELECTION, CODE, attempt)) {
            payloadSha = journal.record("AUTHORED_FACT", payload);
        }
        final var events = CampaignJournal.open(cycle.resolve("control"), SELECTION, CODE).read();
        final Path envelope = cycle.resolve("facts").resolve(events.get(4).detail() + ".json");
        final Path original = cycle.resolve("facts").resolve(payloadSha + ".json");
        final byte[] retainedEnvelope = Files.readAllBytes(envelope);
        final byte[] retainedPayload = Files.readAllBytes(original);
        try (var journal = PersistentExportJournal.open(cycle, SELECTION, CODE, attempt)) {
            assertEquals(payload, journal.latest("AUTHORED_FACT"));
            assertEquals(payloadSha, journal.latestPin("AUTHORED_FACT").sha256());
        }
        Files.writeString(envelope, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> PersistentExportJournal.open(cycle, SELECTION, CODE, attempt));
        Files.write(envelope, retainedEnvelope);
        Files.writeString(original, "{\"value\":8}");
        assertThrows(
                IllegalArgumentException.class,
                () -> PersistentExportJournal.open(cycle, SELECTION, CODE, attempt));
        Files.write(original, retainedPayload);
        try (var journal = PersistentExportJournal.open(cycle, SELECTION, CODE, attempt)) {
            assertEquals(payload, journal.latest("AUTHORED_FACT"));
        }
    }

    @Test
    void deterministicPathSizeAndEofGuardsRemainStrictWithoutAnyLinkHelper() throws Exception {
        final Path path = Files.writeString(directory.resolve("bounded.json"), "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationJson.readSnapshot(directory, 4096));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationJson.readSnapshot(directory.resolve("missing.json"), 4096));
        assertEquals(
                QualificationJson.read(path, 2), QualificationJson.readSnapshot(path, 2).node());
        for (final int limit : new int[] {-1, 0, 1, Integer.MAX_VALUE}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualificationJson.readSnapshot(path, limit));
        }
        for (final String invalid : new String[] {"{} {}", "{"}) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> QualificationJson.readSnapshot(path, 4096));
        }
        Files.write(path, new byte[0]);
        assertThrows(
                IllegalArgumentException.class, () -> QualificationJson.readSnapshot(path, 4096));
        Files.writeString(path, "{} \r\n");
        assertEquals(
                digest(Files.readAllBytes(path)), QualificationJson.readSnapshot(path, 5).sha256());
    }

    private CampaignJournal campaign(final String name) throws IOException {
        return CampaignJournal.create(directory.resolve(name), SELECTION, CODE);
    }

    private static String digest(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
