package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationJsonReuseTest {
    @TempDir Path directory;

    @Test
    void repeatedParsesPreserveDecimalScaleAndIndependentMutableTrees() throws Exception {
        final byte[] bytes =
                "{\"amount\":1.2300,\"text\":\"ação\"}".getBytes(StandardCharsets.UTF_8);
        final var first = QualificationJson.parse(bytes, bytes.length);
        for (int index = 0; index < 32; index++) {
            final var next = QualificationJson.parse(bytes, bytes.length);
            assertNotSame(first, next);
            assertEquals(4, next.required("amount").decimalValue().scale());
            assertEquals("1.2300", next.required("amount").decimalValue().toPlainString());
            assertEquals("ação", next.required("text").textValue());
            ((ObjectNode) next).put("text", "changed");
        }
        assertEquals("ação", first.required("text").textValue());
        assertEquals(
                "ação", QualificationJson.parse(bytes, bytes.length).required("text").textValue());
    }

    @Test
    void duplicateAndTrailingTokenFailuresDoNotPoisonTheNextParse() throws Exception {
        for (int index = 0; index < 8; index++) {
            assertThrows(IOException.class, () -> parse("{\"v\":1,\"v\":2}"));
            assertThrows(IOException.class, () -> parse("{\"v\":1} {\"v\":2}"));
            assertEquals(7, parse("{\"v\":7}").required("v").intValue());
        }
    }

    @Test
    void malformedUtf8BomAndReplacementCharacterRemainRefused() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationJson.parse(new byte[] {(byte) 0xc3, 0x28}, 2));
        assertThrows(IllegalArgumentException.class, () -> parse("\ufeff{}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"v\":\"\ufffd\"}"));
        assertEquals("ação日本", parse("{\"v\":\"ação日本\"}").required("v").textValue());
    }

    @Test
    void configuredNumberAndDepthLimitsRemainStrictAfterReuse() throws Exception {
        assertEquals(40, parse("1".repeat(40)).bigIntegerValue().toString().length());
        assertThrows(IOException.class, () -> parse("1".repeat(41)));
        assertTrue(parse("[".repeat(24) + "0" + "]".repeat(24)).isArray());
        assertThrows(IOException.class, () -> parse("[".repeat(25) + "0" + "]".repeat(25)));
        assertEquals(2, parse("{\"v\":2}").required("v").intValue());
    }

    @Test
    void stringAndInputByteBoundsRemainIndependentOfPreviousCalls() throws Exception {
        assertEquals(131072, parse("\"" + "x".repeat(131072) + "\"").textValue().length());
        assertThrows(IOException.class, () -> parse("\"" + "x".repeat(131073) + "\""));
        final byte[] bytes = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationJson.parse(bytes, bytes.length - 1));
        assertThrows(IllegalArgumentException.class, () -> QualificationJson.parse(new byte[0], 1));
        assertEquals(1, QualificationJson.parse(bytes, bytes.length).required("v").intValue());
    }

    @Test
    void simultaneousParsesKeepTheirOwnValuesAndFailureState() throws Exception {
        final var executor = Executors.newFixedThreadPool(4);
        try {
            final var tasks = new ArrayList<Callable<Integer>>();
            for (int index = 0; index < 32; index++) {
                final int expected = index;
                tasks.add(
                        () -> {
                            assertThrows(IOException.class, () -> parse("{\"x\":1,\"x\":2}"));
                            return parse("{\"value\":" + expected + "}")
                                    .required("value")
                                    .intValue();
                        });
            }
            final var results = executor.invokeAll(tasks);
            for (int index = 0; index < results.size(); index++) {
                assertEquals(index, results.get(index).get().intValue());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void fileReadsDoNotCacheEarlierContentOrEarlierSuccessfulValidation() throws Exception {
        final Path path = directory.resolve("authored.json");
        Files.writeString(path, "{\"value\":1}", StandardCharsets.UTF_8);
        assertEquals(1, QualificationJson.read(path, 1024).required("value").intValue());
        Files.writeString(path, "{\"value\":2}", StandardCharsets.UTF_8);
        assertEquals(2, QualificationJson.read(path, 1024).required("value").intValue());
        Files.writeString(path, "{\"value\":1,\"value\":2}", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> QualificationJson.read(path, 1024));
        Files.writeString(path, "{\"value\":3}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> QualificationJson.read(path, 3));
        assertEquals(3, QualificationJson.read(path, 1024).required("value").intValue());
    }

    @Test
    void reopeningTheDurableJournalStillDetectsChangedFactBytes() throws Exception {
        final Path root = directory.resolve("journal");
        final UUID attempt = UUID.randomUUID();
        final String fact;
        try (var journal =
                PersistentExportJournal.create(root, "a".repeat(64), "b".repeat(64), attempt)) {
            fact = journal.record("AUTHORED_FACT", parse("{\"value\":1}"));
            assertEquals(1, journal.count("AUTHORED_FACT"));
        }
        Files.writeString(
                root.resolve("facts").resolve(fact + ".json"),
                "{\"value\":2}",
                StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> PersistentExportJournal.open(root, "a".repeat(64), "b".repeat(64), attempt));
    }

    private static com.fasterxml.jackson.databind.JsonNode parse(final String text)
            throws IOException {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return QualificationJson.parse(bytes, 262144);
    }
}
