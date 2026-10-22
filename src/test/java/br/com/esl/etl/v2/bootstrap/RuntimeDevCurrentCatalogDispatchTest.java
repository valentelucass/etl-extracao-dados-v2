package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Parser and plan controls only; these fixtures never reserve or execute a catalog. */
class RuntimeDevCurrentCatalogDispatchTest {
    private static final String REQUEST = "USER_20261016_CONTINUAR_V2";

    @Test
    void currentUnitBindsItsOwnRequestAndFiniteCatalogPrefix() throws Exception {
        assertEquals(
                REQUEST,
                RuntimeDevCatalogDispatch.requestForUnit(
                        Path.of("private/user20261016-continuar-v2")));
        final var descriptor = descriptor();
        assertEquals(
                descriptor,
                RuntimeDevCatalogDispatch.parse(
                        new ObjectMapper().writeValueAsBytes(descriptor), REQUEST));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.requestForUnit(
                                Path.of("private/user20261016-continuar-v2-copy")));
    }

    @Test
    void currentAndHistoricalDescriptorsCannotCrossRequestsOrDispatchPrefixes() throws Exception {
        final var mapper = new ObjectMapper();
        final var current = descriptor();
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(mapper.writeValueAsBytes(current)));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                mapper.writeValueAsBytes(current), "HERMES_f4eda62e83d1"));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                mapper.writeValueAsBytes(
                                        RuntimeDevCatalogDispatchTest.descriptor()),
                                REQUEST));
        current.put("dispatchId", "C78CAT01-" + "a".repeat(32));
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogDispatch.parse(mapper.writeValueAsBytes(current), REQUEST));
    }

    @Test
    void currentCatalogCapsAreExactAndDoNotAdmitSourceOrCapture() throws Exception {
        final var mapper = new ObjectMapper();
        final var original = descriptor();
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
            final var changed = original.deepCopy();
            ((ObjectNode) changed.path("limits"))
                    .put(key, changed.path("limits").path(key).intValue() + 1);
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.parse(
                                    mapper.writeValueAsBytes(changed), REQUEST),
                    key);
        }
        for (final String key : new String[] {"sourceHeld", "captureAdmitted"}) {
            final var changed = original.deepCopy();
            changed.put(key, !changed.path(key).booleanValue());
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.parse(
                                    mapper.writeValueAsBytes(changed), REQUEST));
        }
    }

    @Test
    void currentRequestSelectsItsOwnBoundedPlanWithoutChangingHistoricalModes() {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var invocation = UUID.randomUUID();
        final var current = RuntimeDevCatalogExecution.planForRequest(REQUEST, invocation, target);
        assertEquals(C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12, current.mode());
        assertEquals(12, current.queries().size());
        assertEquals(1_900_000, current.reportCharacters());
        final String modules = current.sql(current.queries().get(9));
        assertTrue(modules.contains("fn_runtime_recovery_field"));
        assertTrue(modules.contains("usp_stage_record"));
        assertEquals(
                C78DevCatalogPlan.Mode.ORIGINAL12,
                RuntimeDevCatalogExecution.planForRequest("HERMES_8fdb85e78657", invocation, target)
                        .mode());
        assertEquals(
                C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12,
                RuntimeDevCatalogExecution.planForRequest("HERMES_f4eda62e83d1", invocation, target)
                        .mode());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeDevCatalogExecution.planForRequest(
                                REQUEST + "-copy", invocation, target));
    }

    @Test
    void newCatalogDoesNotChangeTheDefaultLegacyParserOrCreateAThirdNamespaceAlias()
            throws Exception {
        final var legacy = RuntimeDevCatalogDispatchTest.descriptor();
        assertEquals(
                legacy,
                RuntimeDevCatalogDispatch.parse(new ObjectMapper().writeValueAsBytes(legacy)));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevCatalogDispatch.parse(
                                new ObjectMapper().writeValueAsBytes(descriptor()),
                                REQUEST.toLowerCase(java.util.Locale.ROOT)));
    }

    private static ObjectNode descriptor() {
        final var node = RuntimeDevCatalogDispatchTest.descriptor();
        node.put("request", REQUEST);
        node.put("dispatchId", "U20261016CAT12-" + "a".repeat(32));
        final var limits = (ObjectNode) node.path("limits");
        limits.put("rowsPerQuery", 2048);
        limits.put("queryChars", 1_900_000);
        limits.put("reportChars", 1_900_000);
        return node;
    }
}
