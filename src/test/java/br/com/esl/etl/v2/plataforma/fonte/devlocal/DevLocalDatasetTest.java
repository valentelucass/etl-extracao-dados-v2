package br.com.esl.etl.v2.plataforma.fonte.devlocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevLocalDatasetTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Users",
                "Manifestos",
                "Coletas",
                "Fretes",
                "Cotacoes",
                "Localizacao",
                "CAP",
                "Faturas",
                "Inventario",
                "Sinistros",
                "Raster"
            })
    void allElevenRealMappingsMeetIndependentLiteralAssertions(final String name) {
        final JsonNode actual = DevLocalDataset.characterize();
        final JsonNode entity = entity(actual, name);
        final JsonNode oracle = entity(DevLocalDataset.expected(), name);
        for (final String metric :
                new String[] {"inputRows", "pages", "accepted", "quarantined", "filtered"}) {
            assertEquals(oracle.get(metric), entity.get(metric), name + " " + metric);
        }
        for (final JsonNode assertion : oracle.path("checks")) {
            assertEquals(
                    assertion.get("value"),
                    entity.at(assertion.path("path").asText()),
                    name + " " + assertion.path("path").asText());
        }
        assertEquals(4, entity.path("rows").size());
        assertTrue(entity.path("terminal").asBoolean());
        assertFalse(entity.path("canonicalIdentityProven").asBoolean());
        assertEquals("NOT_EXECUTED", entity.path("providerEvidence").asText());
    }

    @Test
    void embeddedSampleAndIndependentTestResourceAreByteSemanticallyEqual() throws IOException {
        assertEquals(resource("sample.json"), DevLocalDataset.sample());
        assertEquals(resource("expected.json"), DevLocalDataset.expected());
        final JsonNode actual = DevLocalDataset.characterize();
        assertEquals("MATCH", DevLocalDataset.verify(actual).path("status").asText());
        assertTrue(DevLocalDataset.verify(actual).path("comparisons").intValue() >= 130);
        assertEquals("STALE_NO_OP", actual.path("freshnessChecks").path("stale").asText());
        assertEquals(
                "EQUAL_FRESHNESS_CONFLICT",
                actual.path("freshnessChecks").path("conflict").asText());
    }

    @Test
    void mutationFlowsThroughRealMapperAndIndependentOracleDetectsIt() {
        final JsonNode sample = DevLocalDataset.sample();
        row(sample, "CAP", 0, 0).put("value", 123.45);
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals(
                "123.45000000", entity(actual, "CAP").at("/rows/0/values/value/typed").asText());
        assertEquals("DIVERGED", DevLocalDataset.verify(actual).path("status").asText());
        assertEquals(
                "100.25000000",
                entity(DevLocalDataset.characterize(), "CAP")
                        .at("/rows/0/values/value/typed")
                        .asText());
    }

    @Test
    void wireIdentityAndPresenceStayDistinct() {
        final JsonNode actual = entity(DevLocalDataset.characterize(), "Users");
        assertEquals("INTEGER:1101", actual.at("/rows/0/sourceKey").asText());
        assertEquals("STRING:1101", actual.at("/rows/1/sourceKey").asText());
        assertEquals("VALUE", actual.at("/rows/0/presence").asText());
        assertEquals("NULL", actual.at("/rows/1/presence").asText());
        assertEquals("ABSENT", actual.at("/rows/2/presence").asText());
        assertFalse(actual.at("/rows/3/valid").asBoolean());
    }

    @Test
    void explicitSyntheticConsentAndClosedSchemaAreRequiredBeforeProcessing() {
        final ObjectNode sample = (ObjectNode) DevLocalDataset.sample();
        sample.put("synthetic", false);
        assertReason("DEV_SYNTHETIC_OPT_IN_REQUIRED", sample);
        sample.put("synthetic", true);
        sample.put("endpoint", "synthetic-unapproved-field");
        assertReason("DEV_CLOSED_SCHEMA", sample);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class, () -> DevLocalDataset.characterize(sample));
        assertFalse(failure.toString().contains("synthetic-unapproved-field"));
        assertEquals(null, failure.getCause());
    }

    @Test
    void incompleteOutOfOrderAndEarlyTerminalPagesCannotBecomeSuccess() {
        final JsonNode sample = DevLocalDataset.sample();
        final ArrayNode pages = (ArrayNode) entity(sample, "Users").get("pages");
        ((ObjectNode) pages.get(0)).put("terminal", true);
        assertReason("DEV_PAGE_SEQUENCE_OR_TERMINAL", sample);
        ((ObjectNode) pages.get(0)).put("terminal", false);
        ((ObjectNode) pages.get(1)).put("number", 3);
        assertReason("DEV_PAGE_SEQUENCE_OR_TERMINAL", sample);
        pages.remove(1);
        assertReason("DEV_PAGE_SEQUENCE_OR_TERMINAL", sample);
    }

    @Test
    void inventoriesAndPhysicalRowsHaveFiniteCaps() {
        final JsonNode duplicate = DevLocalDataset.sample();
        ((ObjectNode) duplicate.path("entities").get(1)).put("entity", "Users");
        assertReason("DEV_ENTITY_INVENTORY", duplicate);
        final JsonNode tooMany = DevLocalDataset.sample();
        final ArrayNode rows = (ArrayNode) entity(tooMany, "Users").at("/pages/0/rows");
        for (int i = 0; i < 17; i++) {
            rows.addObject().put("id", 1200 + i);
        }
        assertReason("DEV_ROW_BOUND", tooMany);
        final JsonNode pagesInput = DevLocalDataset.sample();
        final ArrayNode pages = (ArrayNode) entity(pagesInput, "Users").get("pages");
        for (int i = 0; i < 9; i++) {
            pages.add(pages.get(0).deepCopy());
        }
        assertReason("DEV_PAGE_BOUND", pagesInput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}{}", "{\"x\":1,\"x\":2}", "{bad", "[1,]"})
    void strictLocalParsingRejectsDuplicateTrailingAndMalformedDocuments(final String invalid) {
        assertEquals(
                "DEV_DATASET_INVALID_JSON",
                assertThrows(IllegalArgumentException.class, () -> DevLocalDataset.parse(invalid))
                        .getMessage());
    }

    @Test
    void byteDepthAndFilterLimitsCannotBeWidenedImplicitly() {
        assertEquals(
                "DEV_DATASET_BYTE_LIMIT",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DevLocalDataset.parse("x".repeat(65_537)))
                        .getMessage());
        assertEquals(
                "DEV_DATASET_INVALID_JSON",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DevLocalDataset.parse("[".repeat(18) + "0" + "]".repeat(18)))
                        .getMessage());
        final JsonNode sample = DevLocalDataset.sample();
        ((ObjectNode) sample.get("filter")).put("endExclusive", "2026-12-01T00:00:00Z");
        assertReason("DEV_FILTER_BOUND", sample);
        ((ObjectNode) sample.get("filter")).put("endExclusive", "2026-10-01T00:00:00Z");
        assertReason("DEV_FILTER_BOUND", sample);
        ((ObjectNode) sample.get("filter")).put("zone", "UTC");
        assertReason("DEV_FILTER_ZONE", sample);
    }

    @Test
    void timeWindowIsInclusiveAtStartExclusiveAtEndAndUsesMapperFreshness() {
        final JsonNode sample = DevLocalDataset.sample();
        row(sample, "Coletas", 0, 0).put("status_updated_at", "2026-10-01T00:00:00Z");
        row(sample, "Coletas", 0, 1).put("status_updated_at", "2026-09-30T23:59:59Z");
        final JsonNode actual = entity(DevLocalDataset.characterize(sample), "Coletas");
        assertEquals("IN_WINDOW", actual.at("/rows/0/filterDecision").asText());
        assertEquals("OUTSIDE_WINDOW", actual.at("/rows/1/filterDecision").asText());
        assertEquals("OUTSIDE_WINDOW", actual.at("/rows/2/filterDecision").asText());
        assertEquals(1, actual.path("accepted").intValue());
        assertEquals(2, actual.path("filtered").intValue());
    }

    @Test
    void invalidFreshnessAndExpansionValuesRemainQuarantined() {
        final JsonNode sample = DevLocalDataset.sample();
        row(sample, "Fretes", 0, 0).put("cte_created_at", "invalid-synthetic-date");
        row(sample, "Faturas", 0, 0).putObject("total").put("invalid", true);
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertFalse(entity(actual, "Fretes").at("/rows/0/eligible").asBoolean());
        assertFalse(entity(actual, "Faturas").at("/rows/0/eligible").asBoolean());
        assertEquals(
                "EXP_FIELD_INVALID", entity(actual, "Faturas").at("/rows/0/quarantine").asText());
    }

    @Test
    void rasterParserActuallyProcessesStopsAndRefusesErrorEnvelopes() {
        final JsonNode actual = entity(DevLocalDataset.characterize(), "Raster");
        assertEquals(1, actual.at("/rows/0/stops").size());
        final JsonNode sample = DevLocalDataset.sample();
        row(sample, "Raster", 0, 0).put("Erro", "synthetic-private-error");
        final var failure =
                assertThrows(
                        IllegalArgumentException.class, () -> DevLocalDataset.characterize(sample));
        assertEquals("DEV_RASTER_CONTRACT_FAILED", failure.getMessage());
        assertFalse(failure.toString().contains("synthetic-private-error"));
    }

    @Test
    void expectedAndSampleCopiesCannotBeMutatedAcrossRuns() {
        final ObjectNode expected = (ObjectNode) DevLocalDataset.expected();
        expected.put("synthetic", false);
        assertTrue(DevLocalDataset.expected().path("synthetic").asBoolean());
        assertEquals(DevLocalDataset.characterize(), DevLocalDataset.characterize());
    }

    private static JsonNode resource(final String name) throws IOException {
        try (final var input =
                DevLocalDatasetTest.class.getResourceAsStream("/dev-local/" + name)) {
            assertNotNull(input);
            return DevLocalDataset.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static JsonNode entity(final JsonNode root, final String name) {
        for (final JsonNode entity : root.path("entities")) {
            if (name.equals(entity.path("entity").asText())) {
                return entity;
            }
        }
        throw new AssertionError("ENTITY_MISSING:" + name);
    }

    private static ObjectNode row(
            final JsonNode sample, final String entity, final int page, final int row) {
        return (ObjectNode) entity(sample, entity).path("pages").get(page).path("rows").get(row);
    }

    private static void assertReason(final String expected, final JsonNode sample) {
        assertEquals(
                expected,
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DevLocalDataset.characterize(sample))
                        .getMessage());
    }
}
