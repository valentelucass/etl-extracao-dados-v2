package br.com.esl.etl.v2.contratos.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationReportSanitizer;
import br.com.esl.etl.v2.contratos.caracterizacao.StrictUtf8JsonLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperCharacterizationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void cotacoesCrossesTheProductionParserAndMapper() throws Exception {
        exercise("cotacoes");
    }

    @Test
    void localizacaoPreservesTheWireLexemeThroughTheBoundedEnvelope() throws Exception {
        exercise("localizacao");
    }

    @Test
    void fretesUsesOnlyTheDataExportChannel() throws Exception {
        exercise("fretes");
    }

    private static void exercise(final String resource) throws Exception {
        final byte[] bytes;
        try (var input =
                MapperCharacterizationTest.class.getResourceAsStream(
                        "/contracts/bloco57/" + resource + ".current-v1.synthetic.json")) {
            bytes = input.readNBytes(1_048_577);
        }
        final JsonNode bundle = new StrictUtf8JsonLoader().load(bytes, 1_048_576);
        assertEquals("SYNTHETIC_LOCAL_ONLY", bundle.path("evidence").textValue());
        assertEquals(
                bundle.path("decisionSha256").textValue(),
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                Files.readAllBytes(
                                                        Path.of(
                                                                bundle.path("decision")
                                                                        .textValue())))));
        final var entity = MapperProjection.Entity.valueOf(bundle.path("entity").textValue());
        assertTrue(bundle.path("cases").size() > 0 && bundle.path("cases").size() <= 1_000);
        final var reports = JSON.createArrayNode();
        final List<String> failures = new ArrayList<>();
        for (final JsonNode scenario : bundle.path("cases")) {
            assertTrue(scenario.path("id").textValue().matches("SYNTH_[A-Z0-9_]+"));
            final byte[] envelope =
                    scenario.path("envelope").textValue().getBytes(StandardCharsets.UTF_8);
            final var result =
                    MapperCharacterization.compare(
                            entity,
                            1,
                            page -> new ByteArrayInputStream(envelope),
                            page -> scenario.get("expected"));
            final var report = result.sanitized();
            report.put("scenario", scenario.path("id").textValue());
            reports.add(report);
            if (!result.matches()) {
                failures.add(report.toString());
            }
        }
        CharacterizationReportSanitizer.requireSanitized(reports);
        final Path output =
                Path.of(
                        "target",
                        "bloco57-local",
                        "reports",
                        resource + "-" + UUID.randomUUID() + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                JSON.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(reports)
                                .replace("\r\n", "\n")
                        + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }
}
