package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.HistoricalContentFixtureBridge;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalContentRouteIntegrationTest {
    @TempDir Path directory;

    private record Result(int code, String out, String error) {}

    private Result run(final String operation, final String scenario) throws Exception {
        final var input = HistoricalContentFixtureBridge.manifest(directory, scenario);
        final var stdout = new ByteArrayOutputStream();
        final var stderr = new ByteArrayOutputStream();
        final int code =
                Main.run(
                        new String[] {
                            "independent-export",
                            operation,
                            "--input",
                            input.path().toString(),
                            "--sha256",
                            input.sha256(),
                            "--output",
                            directory.resolve("result.json").toString()
                        },
                        new PrintStream(stdout),
                        new PrintStream(stderr),
                        new Main.RuntimeDependencies(
                                new Properties(),
                                Map.of(),
                                Clock.fixed(Instant.parse("2036-03-27T15:00:00Z"), ZoneOffset.UTC),
                                new RuntimeConfigurationFactory()));
        return new Result(code, stdout.toString(), stderr.toString());
    }

    @Test
    void mainHistoricalContentRoutePersistsAContentOnlyReceiptWithoutAcquisitionClaims()
            throws Exception {
        final var result = run("compare-content", "EQUAL");
        assertEquals(0, result.code(), result.error());
        assertEquals("", result.error());
        final var receipt = ExportValidationSupport.JSON.readTree(result.out());
        final var storedReceipt =
                ExportValidationSupport.JSON.readTree(
                        Files.readAllBytes(directory.resolve("result.json.receipt.json")));
        assertEquals(
                "INDEPENDENT_EXPORT_HISTORICAL_EXECUTED_RECEIPT_V1",
                storedReceipt.path("schema").asText());
        assertFalse(storedReceipt.path("mainResumeAllowed").asBoolean());
        assertFalse(storedReceipt.path("entityQualified").asBoolean());
        assertFalse(storedReceipt.has("attemptId"));
        assertEquals(0, storedReceipt.path("HTTP").asInt(-1));
        assertEquals("HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR", receipt.path("status").asText());
        assertTrue(receipt.path("historicalContentOnly").asBoolean());
        assertFalse(receipt.has("attemptId"));
        assertFalse(receipt.path("mainAdopted").asBoolean());
        assertFalse(receipt.path("entityQualified").asBoolean());
        assertFalse(receipt.path("dashboardConsumptionVerified").asBoolean());
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        assertFalse(diagnostic.path("requestProximityVerified").asBoolean());
        assertFalse(diagnostic.has("attemptId"));
        assertTrue(diagnostic.path("comparison").path("fullTuplesEqual").asBoolean());
        assertEquals(114, diagnostic.path("commonColumns").asInt());
        assertEquals(50, diagnostic.path("totalFields").asInt());
        assertEquals(0, receipt.path("HTTP").asInt());
        assertEquals(0, receipt.path("SQL").asInt());
    }

    @Test
    void historicalManifestCannotEnterTheLiveComparisonRoute() throws Exception {
        assertEquals(2, run("compare", "EQUAL").code());
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertFalse(Files.exists(directory.resolve("result.json.receipt.json")));
    }

    @Test
    void historicalRequestCannotCarryAFabricatedAcquisitionTimestamp() throws Exception {
        assertEquals(2, run("compare-content", "ACQUISITION_FIELD").code());
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test
    void historicalRouteRejectsDataOutsideTheExactCatalogPair() throws Exception {
        assertEquals(2, run("compare-content", "MIXED_PAIR").code());
        assertFalse(Files.exists(directory.resolve("result.json")));
    }
}
