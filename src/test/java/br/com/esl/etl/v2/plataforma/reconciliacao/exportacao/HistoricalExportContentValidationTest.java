package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalExportContentValidationTest {
    @TempDir Path directory;

    private ObjectNode compare(final ObjectNode pair) throws Exception {
        return HistoricalExportContentValidation.compare(
                pair, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
    }

    @Test
    void historicalContentUsesFullKernelWithoutAcquisitionClaim() throws Exception {
        final var result = compare(HistoricalContentFixture.create(directory));
        assertEquals("HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR", result.path("status").asText());
        assertEquals(114, result.path("commonColumns").asInt());
        assertEquals(50, result.path("totalFields").asInt());
        assertTrue(result.path("comparison").path("fullTuplesEqual").asBoolean());
        assertTrue(result.has("fullValueProjection"));
        assertFalse(result.has("attemptId"));
        for (final String flag :
                java.util.List.of(
                        "requestProximityVerified",
                        "acquisitionEvidenceAuthenticationVerified",
                        "operationalAcceptanceIssued",
                        "entityQualified",
                        "mainAdopted",
                        "mainResumeAllowed",
                        "dashboardConsumptionVerified")) {
            assertFalse(result.path(flag).asBoolean(), flag);
        }
        for (final String counter : java.util.List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, result.path(counter).asInt(-1));
        }
    }

    @Test
    void ordinaryCompareCannotConsumeHistoricalProduct() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        assertThrows(
                Exception.class,
                () ->
                        IndependentExportValidation.compare(
                                pair, new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void normalNear300GuardStillRefusesFarPair() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiTime("2025-03-10T13:00:00-03:00", "SECOND");
        assertEquals("PENDING_CONTROLLED_NEAR_PAIR", fixture.compare().path("status").asText());
    }

    @Test
    void uiArtifactCannotStandInForApiEvenWhenContentEqual() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        ((ObjectNode) pair.required("api")).set("dataPin", pair.path("ui").required("dataPin"));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalRequestCannotInventRequestTime() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var request = HistoricalContentFixture.read(pair.required("requestPin"));
        request.put("requestedAt", "2025-03-10T12:00:00-03:00");
        pair.set(
                "requestPin",
                HistoricalContentFixture.write(directory, "changed-request.json", request));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalRequestCannotInventCycleId() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var request = HistoricalContentFixture.read(pair.required("requestPin"));
        request.put("cycleId", request.path("replayId").asText());
        pair.set(
                "requestPin",
                HistoricalContentFixture.write(directory, "changed-request.json", request));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void periodMustMatchExactCatalogPair() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var request = HistoricalContentFixture.read(pair.required("requestPin"));
        request.put("periodEnd", "2025-03-10");
        pair.set(
                "requestPin",
                HistoricalContentFixture.write(directory, "changed-request.json", request));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalNegativePairStaysBlocked() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var catalog = HistoricalContentFixture.read(pair.required("catalogPin"));
        ((ObjectNode) catalog.path("pairs").get(0))
                .put("historicalContentAccepted", false)
                .put("replayPreparationBlocked", true);
        final var pin = HistoricalContentFixture.write(directory, "blocked-catalog.json", catalog);
        pair.set("catalogPin", pin);
        ((ObjectNode) pair.required("catalogBinding"))
                .set("pairCatalog", HistoricalContentFixture.upper(pin));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void rootCatalogPinCaseCannotBeSilentlyCoerced() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        ((ObjectNode) pair.required("catalogBinding"))
                .set("pairCatalog", pair.required("catalogPin"));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalConfigCannotOverrideNear() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        final var config = HistoricalContentFixture.read(pair.required("configPin"));
        config.put("maximumRequestSeparationSeconds", 999999);
        pair.set(
                "configPin",
                HistoricalContentFixture.write(directory, "changed-config.json", config));
        HistoricalContentFixture.admit(directory, pair);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalProductCannotAdoptMain() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        pair.put("mainResumeAllowed", true);
        assertThrows(Exception.class, () -> compare(pair));
    }

    @Test
    void historicalProductCannotPrepareDashboard() throws Exception {
        final var pair = HistoricalContentFixture.create(directory);
        pair.set("dashboardBridgePin", pair.required("mappingPin"));
        assertThrows(Exception.class, () -> compare(pair));
    }
}
