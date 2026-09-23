package br.com.esl.etl.v2.contratos.bloco58;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.caracterizacao.StrictUtf8JsonLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

final class CharacterizationFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CharacterizationFixtures() {}

    static JsonNode bundle(final String entity) throws Exception {
        final JsonNode result;
        try (var input =
                CharacterizationFixtures.class.getResourceAsStream(
                        "/contracts/bloco58/" + entity + ".current-v1.synthetic.json")) {
            result = new StrictUtf8JsonLoader().load(input.readNBytes(1_048_577), 1_048_576);
        }
        assertEquals("SYNTHETIC_LOCAL_ONLY", result.path("evidence").textValue());
        assertEquals(
                result.path("decisionSha256").textValue(),
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                Files.readAllBytes(
                                                        Path.of(
                                                                result.path("decision")
                                                                        .textValue())))));
        assertTrue(result.path("cases").size() > 0 && result.path("cases").size() <= 1_000);
        return result;
    }

    static ByteArrayInputStream input(final String document) {
        return new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8));
    }

    static void write(final String entity, final Object reports) throws Exception {
        final Path directory =
                Path.of(
                        System.getProperty(
                                "characterization.reports", "target/bloco58-local/reports"));
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve(entity + "-" + UUID.randomUUID() + ".json"),
                JSON.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(reports)
                                .replace("\r\n", "\n")
                        + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }
}
