package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.HistoricalContentFixture;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalContentMainTest {
    @TempDir Path directory;

    private int run(
            final String action,
            final com.fasterxml.jackson.databind.node.ObjectNode pair,
            final Path output,
            final ByteArrayOutputStream out,
            final ByteArrayOutputStream err)
            throws Exception {
        final var pin = HistoricalContentFixture.write(directory, "main-product.json", pair);
        return Main.run(
                new String[] {
                    "independent-export",
                    action,
                    "--input",
                    pin.path("path").asText(),
                    "--sha256",
                    pin.path("sha256").asText(),
                    "--output",
                    output.toString()
                },
                new PrintStream(out),
                new PrintStream(err),
                new Main.RuntimeDependencies(
                        new java.util.Properties(),
                        java.util.Map.of(),
                        Clock.fixed(Instant.parse("2025-03-10T16:00:00Z"), ZoneOffset.UTC),
                        new RuntimeConfigurationFactory()));
    }

    @Test
    void malformedHistoricalMainDoesNotPublishPartialResult() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        pair.put("mainResumeAllowed", true);
        final var output = directory.resolve("refused-result.json");
        assertEquals(
                2,
                run(
                        "compare-content",
                        pair,
                        output,
                        new ByteArrayOutputStream(),
                        new ByteArrayOutputStream()));
        assertFalse(Files.exists(output));
    }

    @Test
    void preparationCannotUseProductAsExecutionContext() throws Exception {
        final var output = directory.resolve("controls-result.json");
        assertEquals(
                2,
                run(
                        "prepare-content-controls",
                        HistoricalContentFixture.create(directory),
                        output,
                        new ByteArrayOutputStream(),
                        new ByteArrayOutputStream()));
        assertFalse(Files.exists(output));
    }

    @Test
    void realMainPreparesFretesControlsWithoutReadingSyntheticBooks() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var context =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_JAVA_HISTORICAL_CONTROLS_BOUND0728_V1")
                        .put("OriginalPid", ProcessHandle.current().parent().orElseThrow().pid())
                        .put("physicalOriginalObserved", true)
                        .put("OriginalHardMaximumBytes", 1073741824L)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        final var contextPin =
                HistoricalContentFixture.write(
                        directory, "synthetic-controls-context.json", context);
        final var request = HistoricalContentFixture.read(pair.required("requestPin"));
        final var plan =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTROLS_PLAN_V1")
                        .put("historicalContentOnly", true)
                        .put("pairId", "SYNTHETIC_FRETES")
                        .put("replayId", request.path("replayId").asText());
        plan.set("catalogPin", pair.required("catalogPin"));
        plan.set("executionContextPin", contextPin);
        Files.delete(Path.of(pair.path("api").path("dataPin").path("path").asText()));
        Files.delete(Path.of(pair.path("ui").path("dataPin").path("path").asText()));
        final var output = directory.resolve("prepared-controls.json");
        assertEquals(
                0,
                run(
                        "prepare-content-controls",
                        plan,
                        output,
                        new ByteArrayOutputStream(),
                        new ByteArrayOutputStream()));
        final var result = ExportValidationSupport.JSON.readTree(Files.readAllBytes(output));
        assertEquals(
                "HISTORICAL_CONTROLS_PREPARED_NOT_CONTENT_REPLAYED",
                result.path("status").asText());
        assertTrue(result.path("controlsMetadataOnly").asBoolean());
        assertFalse(result.path("contentEqualityAsserted").asBoolean());
        assertFalse(result.path("businessCellsOrIdsExported").asBoolean());
        assertFalse(result.has("fullValueProjection"));
        assertEquals(114, result.path("mapping").path("columns").size());
    }
}
