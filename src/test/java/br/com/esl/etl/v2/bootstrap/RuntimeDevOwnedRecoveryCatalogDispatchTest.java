package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure recovery selection and evidence readback; no Root authority, provider or connection. */
class RuntimeDevOwnedRecoveryCatalogDispatchTest {
    private static final String REQUEST = "USER_20261016_CONTINUAR_V2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir Path directory;

    @Test
    void exactCurrentOwnedScopeSelectsRecoveryWithoutAdmittingSourceOrCapture() throws Exception {
        final var descriptor = descriptor();
        assertEquals(descriptor, parse(descriptor));
        assertTrue(descriptor.path("sourceHeld").booleanValue());
        assertEquals(false, descriptor.path("captureAdmitted").booleanValue());
        final var plan = plan(descriptor);
        assertEquals(C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12, plan.mode());
        assertEquals(12, plan.queries().size());
        assertEquals("OWNED_RECOVERY_SUMMARY", plan.queries().get(9).name());
        assertEquals(1_900_000, plan.reportCharacters());
        assertEquals(
                UUID.fromString(RuntimeDevCatalogDispatch.OWNED_RECOVERY_EXECUTION),
                plan.ownedRecoveryScope().executionId());
        assertEquals(
                RuntimeDevCatalogDispatch.OWNED_RECOVERY_MAIN_PID,
                plan.ownedRecoveryScope().mainPID());
        assertEquals(
                RuntimeDevCatalogDispatch.OWNED_RECOVERY_CLIENT_PORT,
                plan.ownedRecoveryScope().clientPort());
        RuntimeDevCatalogControl.validatePlanSelection(plan, descriptor);
    }

    @Test
    void recoveryReceiptCannotBeConfusedWithACompleteCurrentCatalog() throws Exception {
        assertEquals(
                "RECOVERY_READONLY_OBSERVED",
                RuntimeDevCatalogExecution.completionStatus(plan(descriptor())));
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        for (final String request :
                new String[] {REQUEST, "HERMES_f4eda62e83d1", "HERMES_8fdb85e78657"}) {
            assertEquals(
                    "DEV_C78_CATALOG_COMPLETE_REPORT_STORED",
                    RuntimeDevCatalogExecution.completionStatus(
                            RuntimeDevCatalogExecution.planForRequest(
                                    request, UUID.randomUUID(), target)));
        }
    }

    @Test
    void recoveryIsNotAnAliasForHistoricalRequestsOrTheCurrentModuleCatalog() throws Exception {
        final var descriptor = descriptor();
        for (final String request :
                new String[] {"HERMES_f4eda62e83d1", "HERMES_8fdb85e78657", REQUEST + "-copy"}) {
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.parse(
                                    MAPPER.writeValueAsBytes(descriptor), request));
        }
        for (final String prefix : new String[] {"U20261016CAT12-", "C78CAT01-"}) {
            final var changed = descriptor.deepCopy();
            changed.put("dispatchId", prefix + "a".repeat(32));
            assertThrows(IOException.class, () -> parse(changed));
        }
        final var current = descriptor.deepCopy();
        current.remove("recoveryScope");
        current.put("operation", "CATALOG12_SELECT_ONLY");
        current.put("dispatchId", "U20261016CAT12-" + "a".repeat(32));
        assertEquals(current, parse(current));
        assertEquals(C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12, plan(current).mode());
    }

    @Test
    void recoveryScopeIsRequiredOnlyForTheExactNewOperation() throws Exception {
        final var missing = descriptor();
        missing.remove("recoveryScope");
        assertThrows(IOException.class, () -> parse(missing));
        final var ordinary = descriptor();
        ordinary.put("operation", "CATALOG12_SELECT_ONLY");
        ordinary.put("dispatchId", "U20261016CAT12-" + "a".repeat(32));
        assertThrows(IOException.class, () -> parse(ordinary));
        final var unknown = descriptor();
        unknown.put("operation", RuntimeDevCatalogDispatch.OWNED_RECOVERY_OPERATION + "_COPY");
        assertThrows(IOException.class, () -> parse(unknown));
        assertThrows(IOException.class, () -> plan(unknown));
    }

    @Test
    void everyScopeFieldAndTheNestedEvidenceShapeAreClosed() throws Exception {
        for (final String key :
                new String[] {"executionId", "mainPID", "clientPort", "consumedSampleDispatch"}) {
            final var changed = descriptor();
            ((ObjectNode) changed.path("recoveryScope")).remove(key);
            assertThrows(IOException.class, () -> parse(changed), key);
        }
        final var extra = descriptor();
        ((ObjectNode) extra.path("recoveryScope")).put("allowRollback", true);
        assertThrows(IOException.class, () -> parse(extra));
        final var nested = descriptor();
        ((ObjectNode) nested.path("recoveryScope").path("consumedSampleDispatch"))
                .put("permit", true);
        assertThrows(IOException.class, () -> parse(nested));
    }

    @Test
    void foreignOrNoncanonicalOwnedExecutionIsRejected() throws Exception {
        for (final String execution :
                new String[] {
                    UUID.randomUUID().toString(),
                    RuntimeDevCatalogDispatch.OWNED_RECOVERY_EXECUTION.toUpperCase(
                            java.util.Locale.ROOT),
                    "00000000-0000-0000-0000-000000000000",
                    RuntimeDevCatalogDispatch.OWNED_RECOVERY_EXECUTION + " "
                }) {
            final var changed = descriptor();
            ((ObjectNode) changed.path("recoveryScope")).put("executionId", execution);
            assertThrows(IOException.class, () -> parse(changed));
        }
    }

    @Test
    void foreignPidOrPortAndNonintegralNumericShapesAreRejected() throws Exception {
        for (final String key : new String[] {"mainPID", "clientPort"}) {
            for (final long value : new long[] {0, -1, 1, Long.MAX_VALUE}) {
                final var changed = descriptor();
                ((ObjectNode) changed.path("recoveryScope")).put(key, value);
                assertThrows(IOException.class, () -> parse(changed));
            }
            final var fractional = descriptor();
            ((ObjectNode) fractional.path("recoveryScope"))
                    .put(key, fractional.path("recoveryScope").path(key).doubleValue());
            assertThrows(IOException.class, () -> parse(fractional));
            final var textual = descriptor();
            ((ObjectNode) textual.path("recoveryScope"))
                    .put(key, textual.path("recoveryScope").path(key).asText());
            assertThrows(IOException.class, () -> parse(textual));
        }
    }

    @Test
    void consumedDispatchReferenceCannotBeRedirectedOrRepinned() throws Exception {
        for (final String key : new String[] {"path", "sha256"}) {
            final var changed = descriptor();
            final var consumed =
                    (ObjectNode) changed.path("recoveryScope").path("consumedSampleDispatch");
            consumed.put(key, key.equals("path") ? "private/copy.json" : "f".repeat(64));
            assertThrows(IOException.class, () -> parse(changed));
            consumed.remove(key);
            assertThrows(IOException.class, () -> parse(changed));
        }
    }

    @Test
    void recoveryCannotIncreaseCapsOrReleaseSourceOrCapture() throws Exception {
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
            assertThrows(IOException.class, () -> parse(changed));
        }
        for (final String key : new String[] {"sourceHeld", "captureAdmitted"}) {
            final var changed = original.deepCopy();
            changed.put(key, !changed.path(key).booleanValue());
            assertThrows(IOException.class, () -> parse(changed));
        }
    }

    @Test
    void controlRejectsAnotherPlanModeOrForeignRecoveryTuple() throws Exception {
        final var descriptor = descriptor();
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var invocation = UUID.randomUUID();
        final var ordinary = C78DevCatalogPlan.prepareSample12Current(invocation, target);
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogControl.validatePlanSelection(ordinary, descriptor));
        final var foreign =
                C78DevCatalogPlan.prepareOwnedSample12Recovery(
                        invocation,
                        target,
                        UUID.fromString(RuntimeDevCatalogDispatch.OWNED_RECOVERY_EXECUTION),
                        RuntimeDevCatalogDispatch.OWNED_RECOVERY_MAIN_PID,
                        RuntimeDevCatalogDispatch.OWNED_RECOVERY_CLIENT_PORT + 1);
        assertThrows(
                IOException.class,
                () -> RuntimeDevCatalogControl.validatePlanSelection(foreign, descriptor));
    }

    @Test
    void planSelectionItselfRejectsCrossRequestOrChangedScope() throws Exception {
        final var cross = descriptor();
        cross.put("request", "HERMES_f4eda62e83d1");
        assertThrows(IOException.class, () -> plan(cross));
        final var changed = descriptor();
        ((ObjectNode) changed.path("recoveryScope")).put("clientPort", 1);
        assertThrows(IOException.class, () -> plan(changed));
    }

    @Test
    void consumedEvidenceReadbackDoesNotParseOrReviveItsExpiredAuthority() throws Exception {
        final var path = directory.resolve("consumed.json");
        final byte[] bytes =
                "{\"deadlineUtc\":\"2000-01-01T00:00:00Z\",\"permit\":false}"
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes);
        final String sha =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        try (var evidence = FileChannel.open(path, StandardOpenOption.READ)) {
            assertDoesNotThrow(
                    () -> RuntimeDevCatalogDispatch.verifyConsumedEvidence(evidence, sha));
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.verifyConsumedEvidence(
                                    evidence, "f".repeat(64)));
            assertTrue(evidence.isOpen());
        }
    }

    @Test
    void oversizedConsumedEvidenceIsRejectedBeforeHashAcceptance() throws Exception {
        final var path = directory.resolve("oversized.json");
        Files.write(path, new byte[65_537]);
        try (var evidence = FileChannel.open(path, StandardOpenOption.READ)) {
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevCatalogDispatch.verifyConsumedEvidence(
                                    evidence, "0".repeat(64)));
        }
    }

    private static C78DevCatalogPlan plan(final ObjectNode descriptor) throws Exception {
        return RuntimeDevCatalogExecution.planForDispatch(
                descriptor,
                UUID.randomUUID(),
                C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
    }

    private static com.fasterxml.jackson.databind.JsonNode parse(final ObjectNode descriptor)
            throws IOException {
        return RuntimeDevCatalogDispatch.parse(MAPPER.writeValueAsBytes(descriptor), REQUEST);
    }

    private static ObjectNode descriptor() {
        final var node = RuntimeDevCatalogDispatchTest.descriptor();
        node.put("request", REQUEST);
        node.put("operation", RuntimeDevCatalogDispatch.OWNED_RECOVERY_OPERATION);
        node.put("dispatchId", "U20261016RECOVERY12-" + "a".repeat(32));
        final var limits = (ObjectNode) node.path("limits");
        limits.put("rowsPerQuery", 2048);
        limits.put("queryChars", 1_900_000);
        limits.put("reportChars", 1_900_000);
        final var scope = node.putObject("recoveryScope");
        scope.put("executionId", RuntimeDevCatalogDispatch.OWNED_RECOVERY_EXECUTION);
        scope.put("mainPID", RuntimeDevCatalogDispatch.OWNED_RECOVERY_MAIN_PID);
        scope.put("clientPort", RuntimeDevCatalogDispatch.OWNED_RECOVERY_CLIENT_PORT);
        scope.putObject("consumedSampleDispatch")
                .put("path", RuntimeDevCatalogDispatch.OWNED_RECOVERY_CONSUMED_DISPATCH.toString())
                .put("sha256", RuntimeDevCatalogDispatch.OWNED_RECOVERY_CONSUMED_SHA256);
        return node;
    }
}
