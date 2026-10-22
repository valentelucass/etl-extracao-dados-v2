package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.devlocal.DevLocalDataset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure local computation over actual mapper output; no ledger, worker, source client or SQL. */
class DevLocalEngineStateIntegrityTest {
    private static final String PERFORMANCE = "2026-10-01T10:00:00.123456789Z";

    @Test
    void serializedInventoryRetainsOrderedDuplicateMappingsAndItsExactFreshnessOnReplay()
            throws Exception {
        final var sample = inventorySample(PERFORMANCE, originalMapping());
        final var dataset = DevLocalDataset.characterize(sample);
        final var inputBefore = DevLocalEngine.bytes(dataset);
        final var first = DevLocalEngine.execute(dataset, options());
        assertInventory(first, PERFORMANCE, originalMapping());
        final var readback = DevLocalEngine.JSON.readTree(DevLocalEngine.bytes(first));
        final var previousBefore = DevLocalEngine.bytes(readback.path("state"));
        final var replay = DevLocalEngine.execute(dataset, incremental(readback.path("state")));
        assertEquals("COMPLETED", replay.path("status").asText());
        assertInventory(replay, PERFORMANCE, originalMapping());
        assertEquals(0, entity(replay, "Inventario").path("updates").asInt());
        assertEquals(6, entity(replay, "Inventario").path("noops").asInt());
        assertArrayEquals(
                DevLocalEngine.bytes(entity(first.path("state"), "Inventario").path("rows")),
                DevLocalEngine.bytes(entity(replay.path("state"), "Inventario").path("rows")));
        assertArrayEquals(previousBefore, DevLocalEngine.bytes(readback.path("state")));
        assertArrayEquals(inputBefore, DevLocalEngine.bytes(dataset));
        assertFalse(replay.path("published").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OLDER", "NEWER"})
    void oneNanosecondControlsWhetherChangedInventoryMappingsCanReplaceRetainedState(
            final String direction) throws Exception {
        final var firstDataset =
                DevLocalDataset.characterize(inventorySample(PERFORMANCE, originalMapping()));
        final var first = DevLocalEngine.execute(firstDataset, options());
        final var readback = DevLocalEngine.JSON.readTree(DevLocalEngine.bytes(first));
        final var previousBefore = DevLocalEngine.bytes(readback.path("state"));
        final boolean newer = "NEWER".equals(direction);
        final String candidateAt =
                newer ? "2026-10-01T10:00:00.123456790Z" : "2026-10-01T10:00:00.123456788Z";
        final var changedMapping =
                DevLocalEngine.JSON
                        .createArrayNode()
                        .add("SYNTHETIC-B")
                        .add("SYNTHETIC-A")
                        .add("SYNTHETIC-A");
        final var changed =
                DevLocalDataset.characterize(inventorySample(candidateAt, changedMapping));
        final var changedBefore = DevLocalEngine.bytes(changed);
        final var result = DevLocalEngine.execute(changed, incremental(readback.path("state")));
        assertEquals("COMPLETED", result.path("status").asText());
        assertEquals(newer ? 2 : 0, entity(result, "Inventario").path("updates").asInt());
        assertEquals(newer ? 4 : 6, entity(result, "Inventario").path("noops").asInt());
        assertInventory(
                result,
                newer ? candidateAt : PERFORMANCE,
                newer ? changedMapping : originalMapping());
        final var firstHash = inventoryRow(first).path("canonicalHash").asText();
        final var currentHash = inventoryRow(result).path("canonicalHash").asText();
        if (newer) {
            assertNotEquals(firstHash, currentHash);
        } else {
            assertEquals(firstHash, currentHash);
            assertArrayEquals(
                    DevLocalEngine.bytes(entity(first.path("state"), "Inventario").path("rows")),
                    DevLocalEngine.bytes(entity(result.path("state"), "Inventario").path("rows")));
        }
        assertArrayEquals(previousBefore, DevLocalEngine.bytes(readback.path("state")));
        assertArrayEquals(changedBefore, DevLocalEngine.bytes(changed));
    }

    @Test
    void aChangedMappingAtTheSameExactFreshnessCannotBecomeAnUpdate() throws Exception {
        final var firstDataset =
                DevLocalDataset.characterize(inventorySample(PERFORMANCE, originalMapping()));
        final var first = DevLocalEngine.execute(firstDataset, options());
        final var previous =
                DevLocalEngine.JSON.readTree(DevLocalEngine.bytes(first)).path("state");
        final var before = DevLocalEngine.bytes(previous);
        final var changed =
                DevLocalDataset.characterize(
                        inventorySample(
                                PERFORMANCE,
                                DevLocalEngine.JSON
                                        .createArrayNode()
                                        .add("SYNTHETIC-A")
                                        .add("SYNTHETIC-B")
                                        .add("SYNTHETIC-A")));
        final var changedBefore = DevLocalEngine.bytes(changed);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DevLocalEngine.execute(changed, incremental(previous)));
        assertEquals("DEV_LOCAL_EQUAL_FRESHNESS_CONFLICT", failure.getMessage());
        assertArrayEquals(before, DevLocalEngine.bytes(previous));
        assertArrayEquals(changedBefore, DevLocalEngine.bytes(changed));
        assertInventory(first, PERFORMANCE, originalMapping());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"ENTITY_DUPLICATE", "ROW_DUPLICATE", "ENTITY_MISSING", "CANONICAL_JSON"})
    void damagedPreviousStateCannotBeMergedSilentlyOrRewriteItsInput(final String damage)
            throws Exception {
        final var dataset =
                DevLocalDataset.characterize(inventorySample(PERFORMANCE, originalMapping()));
        final var first = DevLocalEngine.execute(dataset, options());
        final var previous =
                (ObjectNode)
                        DevLocalEngine.JSON.readTree(DevLocalEngine.bytes(first)).path("state");
        final var entries = (ArrayNode) previous.path("entities");
        final String expected;
        switch (damage) {
            case "ENTITY_DUPLICATE" -> {
                entries.set(1, entries.path(0).deepCopy());
                expected = "DEV_LOCAL_PREVIOUS_DUPLICATE";
            }
            case "ROW_DUPLICATE" -> {
                final var rows = (ArrayNode) entries.path(0).path("rows");
                rows.add(rows.path(0).deepCopy());
                expected = "DEV_LOCAL_PREVIOUS_DUPLICATE";
            }
            case "ENTITY_MISSING" -> {
                ((ObjectNode) entries.path(0)).put("entity", "UNDECLARED");
                expected = "DEV_LOCAL_PREVIOUS_ENTITY";
            }
            case "CANONICAL_JSON" -> {
                final String hash =
                        entries.path(0).path("rows").path(0).path("canonicalHash").asText();
                ((ObjectNode) previous.path("observations")).put(hash, "{");
                expected = "DEV_LOCAL_PREVIOUS_CANONICAL_JSON";
            }
            default -> throw new AssertionError(damage);
        }
        final var before = DevLocalEngine.bytes(previous);
        final var datasetBefore = DevLocalEngine.bytes(dataset);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DevLocalEngine.execute(dataset, incremental(previous)));
        assertEquals(expected, failure.getMessage());
        assertArrayEquals(before, DevLocalEngine.bytes(previous));
        assertArrayEquals(datasetBefore, DevLocalEngine.bytes(dataset));
        // The original computed state remains usable; no repair or compensation is attempted.
        final var validReplay = DevLocalEngine.execute(dataset, incremental(first.path("state")));
        assertEquals("COMPLETED", validReplay.path("status").asText());
        assertInventory(validReplay, PERFORMANCE, originalMapping());
    }

    private static ObjectNode inventorySample(final String performance, final JsonNode mapping) {
        final ObjectNode sample = DevLocalDataset.sample().deepCopy();
        final var row =
                (ObjectNode)
                        entity(sample, "Inventario").path("pages").path(0).path("rows").path(0);
        // Performance is the declared priority, even when finished_at is chronologically later.
        row.put("started_at", "2026-10-01T09:00:00.999999999Z");
        row.put("finished_at", "2026-10-01T11:00:00.999999999Z");
        row.put("cnr_c_s_fit_dpn_performance_finished_at", performance);
        row.set("cnr_c_s_fit_invoices_mapping", mapping.deepCopy());
        return sample;
    }

    private static ArrayNode originalMapping() {
        return DevLocalEngine.JSON
                .createArrayNode()
                .add("SYNTHETIC-A")
                .add("SYNTHETIC-A")
                .add("SYNTHETIC-B");
    }

    private static ObjectNode options() {
        return DevLocalEngine.JSON.createObjectNode().put("synthetic", true).put("scale", 2);
    }

    private static ObjectNode incremental(final JsonNode previous) {
        final var result = options().put("mode", "INCREMENTAL");
        result.set("previousState", previous);
        return result;
    }

    private static JsonNode entity(final JsonNode document, final String name) {
        for (final JsonNode entity : document.path("entities")) {
            if (name.equals(entity.path("entity").asText())) {
                return entity;
            }
        }
        throw new AssertionError("Missing synthetic entity " + name);
    }

    private static JsonNode inventoryRow(final JsonNode result) {
        for (final JsonNode row : entity(result.path("state"), "Inventario").path("rows")) {
            if (row.path("copy").asInt() == 0
                    && "SYNTHETIC:Inventario:1".equals(row.path("sourceKey").asText())) {
                return row;
            }
        }
        throw new AssertionError("Missing first synthetic inventory occurrence");
    }

    private static void assertInventory(
            final JsonNode result, final String at, final JsonNode expectedMapping)
            throws Exception {
        final var row = inventoryRow(result);
        final var freshness = row.path("domain").path("freshness");
        final var expectedAt = Instant.parse(at);
        assertEquals(expectedAt.getEpochSecond(), freshness.path("second").asLong());
        assertEquals(expectedAt.getNano(), freshness.path("nano").asInt());
        assertFalse(freshness.path("exclusive").asBoolean());
        final var observation =
                DevLocalEngine.JSON.readTree(
                        result.path("state")
                                .path("observations")
                                .path(row.path("canonicalHash").asText())
                                .asText());
        final var field = observation.path("values").path("cnrCSFitInvoicesMapping");
        assertEquals("VALUE", field.path("presence").asText());
        assertEquals("ARRAY", field.path("wire").asText());
        assertEquals(expectedMapping, DevLocalEngine.JSON.readTree(field.path("raw").asText()));
        assertEquals(0, result.path("metrics").path("sqlCalls").asInt());
        assertEquals(0, result.path("metrics").path("httpCalls").asInt());
        assertFalse(result.path("metrics").path("physicalEvidence").asBoolean());
    }
}
