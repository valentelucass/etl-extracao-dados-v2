package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Public parser controls only: this fixture is never a Root dispatch or physical admission. */
class RuntimeDevCatalogDispatchTest {
    @Test
    void newCatalogUnitBindsItsOwnRequestAndRejectsCrossUnitClaims() throws Exception {
        final String current =
                RuntimeDevCatalogDispatch.requestForUnit(
                        java.nio.file.Path.of("private/hermesf4eda62e83d1"));
        assertEquals("HERMES_f4eda62e83d1", current);
        final var node = descriptor();
        final var mapper = new ObjectMapper();
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(mapper.writeValueAsBytes(node), current));
        node.put("request", current);
        ((ObjectNode) node.path("limits")).put("queryChars", 1_500_000);
        ((ObjectNode) node.path("limits")).put("reportChars", 1_500_000);
        assertEquals(
                node, RuntimeDevCatalogDispatch.parse(mapper.writeValueAsBytes(node), current));
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(mapper.writeValueAsBytes(node)));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                mapper.writeValueAsBytes(node), "HERMES_unissued"));
    }

    @Test
    void currentRequestSelectsActualCoveragePlanAndPreservesHistoricalQueries() {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var invocation = UUID.randomUUID();
        final var current =
                RuntimeDevCatalogExecution.planForRequest(
                        "HERMES_f4eda62e83d1", invocation, target);
        final var original =
                RuntimeDevCatalogExecution.planForRequest(
                        "HERMES_8fdb85e78657", invocation, target);
        assertEquals(
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.Mode
                        .CONTRACT_COVERAGE12,
                current.mode());
        assertEquals(
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.Mode
                        .ORIGINAL12,
                original.mode());
        assertEquals(12, current.queries().size());
        assertEquals(
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.Query
                        .MODULE_CONTRACTS,
                current.queries().get(9));
        assertEquals(
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.Query
                        .DQ_METADATA,
                original.queries().get(9));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeDevCatalogExecution.planForRequest("unissued", invocation, target));
    }

    @Test
    void catalogUnitNamesStayClosedAndHistoricalNamespaceIsPreserved() throws Exception {
        assertEquals(
                "HERMES_8fdb85e78657",
                RuntimeDevCatalogDispatch.requestForUnit(
                        java.nio.file.Path.of("private/hermes8fdb85e78657")));
        for (final String name :
                new String[] {"hermes863b2573942e", "hermesf4eda62e83d1-copy", "production"}) {
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.requestForUnit(
                                    java.nio.file.Path.of("private", name)));
        }
    }

    @Test
    void acceptsOnlyTheExactCatalogShapeAndDoesNotAdmitSourceOrCapture() throws Exception {
        final var node = descriptor();
        assertEquals(
                node, RuntimeDevCatalogDispatch.parse(new ObjectMapper().writeValueAsBytes(node)));
        for (final String key : new String[] {"sourceHeld", "captureAdmitted"}) {
            final var changed = node.deepCopy();
            changed.put(key, !changed.path(key).booleanValue());
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.parse(
                                    new ObjectMapper().writeValueAsBytes(changed)));
        }
    }

    @Test
    void rejectsExpandedLimitsForeignTargetMissingCoreAndHistoricalGrammar() throws Exception {
        final var node = descriptor();
        for (final String key :
                new String[] {
                    "connections",
                    "selects",
                    "querySeconds",
                    "unitSeconds",
                    "retry",
                    "rowsPerQuery",
                    "queryChars",
                    "reportChars",
                    "frameBytes"
                }) {
            final var changed = node.deepCopy();
            ((ObjectNode) changed.path("limits"))
                    .put(key, changed.path("limits").path(key).intValue() + 1);
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.parse(
                                    new ObjectMapper().writeValueAsBytes(changed)),
                    key);
        }
        final var changed = node.deepCopy();
        changed.put("target", C78LocalSqlTarget.URL.replace("localhost", "remote"));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                new ObjectMapper().writeValueAsBytes(changed)));
        changed.set("target", node.path("target"));
        changed.withArray("runtimePins").remove(0);
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                new ObjectMapper().writeValueAsBytes(changed)));
        changed.set("runtimePins", node.path("runtimePins"));
        changed.put("dispatchId", "C78ROOT01-" + "a".repeat(32));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                new ObjectMapper().writeValueAsBytes(changed)));
    }

    @Test
    void rejectsDuplicateKeysTrailingDocumentsWrongNumericTypesAndLongValidity() throws Exception {
        final var node = descriptor();
        final var json = new ObjectMapper().writeValueAsString(node);
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                (json + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                json.replace("\"schema\":", "\"schema\":\"duplicate\",\"schema\":")
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ((ObjectNode) node.path("limits")).put("selects", "12");
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(new ObjectMapper().writeValueAsBytes(node)));
        ((ObjectNode) node.path("limits")).put("selects", 12);
        node.put(
                "deadlineUtc",
                Instant.parse(node.path("issuedUtc").textValue()).plusSeconds(61).toString());
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(new ObjectMapper().writeValueAsBytes(node)));
        assertThrows(IOException.class, () -> RuntimeDevCatalogDispatch.parse(new byte[65537]));
    }

    static ObjectNode descriptor() {
        final var mapper = new ObjectMapper();
        final var node = mapper.createObjectNode();
        node.put("schema", RuntimeDevCatalogDispatch.SCHEMA);
        node.put("request", "HERMES_8fdb85e78657");
        node.put("operation", "CATALOG12_SELECT_ONLY");
        node.put("dispatchId", "C78CAT01-" + "a".repeat(32));
        node.put("invocationId", UUID.randomUUID().toString());
        node.put("nonce", "b".repeat(64));
        for (final String key :
                new String[] {
                    "bundleManifestSHA256", "configurationSHA256", "requestSHA256", "planSHA256"
                }) {
            node.put(key, "0".repeat(64));
        }
        node.put("profile", C78LocalSqlTarget.PROFILE);
        node.put("target", C78LocalSqlTarget.URL);
        node.put("leafSHA256", C78LocalTlsScope.LEAF_SHA256);
        node.put("sourceHeld", true);
        node.put("captureAdmitted", false);
        final var now = Instant.now();
        node.put("issuedUtc", now.toString());
        node.put("deadlineUtc", now.plusSeconds(60).toString());
        node.put("parentPID", 101);
        node.put("parentStartUtc", now.minusSeconds(1).toString());
        node.put("occurrenceDirectory", "C:/public-control-only");
        final var limits = node.putObject("limits");
        final int[] values = {1, 12, 10, 60, 0, 512, 65536, 262144, 8192};
        final String[] names = {
            "connections",
            "selects",
            "querySeconds",
            "unitSeconds",
            "retry",
            "rowsPerQuery",
            "queryChars",
            "reportChars",
            "frameBytes"
        };
        for (int i = 0; i < names.length; i++) {
            limits.put(names[i], values[i]);
        }
        final var pins = node.putArray("runtimePins");
        for (final String core : RuntimeDevCatalogDispatch.CORE) {
            pins.addObject().put("path", core).put("sha256", "0".repeat(64));
        }
        node.putObject("nativeImage")
                .put("service", "MSSQLSERVER")
                .put("pid", 202)
                .put("rawCreation", "1")
                .put("version", "16.0.1000.6")
                .put("sha256", RuntimeDevCatalogDispatch.SQL_IMAGE_SHA256);
        return node;
    }
}
