package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.devlocal.DevLocalDataset;
import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevLocalEngineTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    void realMappersFeedElevenVerticalsAndBoundedCalculatedOutputs(final int scale)
            throws IOException {
        final JsonNode dataset = DevLocalDataset.characterize();
        assertEquals("MATCH", DevLocalDataset.verify(dataset).path("status").asText());
        final JsonNode result = DevLocalEngine.execute(dataset, options(scale));
        assertEquals("COMPLETED", result.path("status").asText());
        assertEquals(11, result.path("entities").size());
        assertEquals(19, result.path("contracts").size());
        assertEquals(33, result.path("sweepPreview").size());
        assertEquals(5, result.path("facts").size());
        assertEquals(2 * scale, result.path("relations").size());
        for (final JsonNode row : result.path("relations")) {
            assertEquals("RESOLVED_LOCAL", row.path("status").asText());
        }
        final Set<String> dimensions = new HashSet<>();
        result.path("dimensions").forEach(row -> dimensions.add(row.path("dimension").asText()));
        assertEquals(
                Set.of("FILIAL", "CLIENTE", "VEICULO", "MOTORISTA", "PLANOCONTAS", "USUARIO"),
                dimensions);
        assertEquals(0, result.path("metrics").path("sqlCalls").asInt());
        assertFalse(result.path("published").asBoolean());
        assertTrue(DevLocalEngine.bytes(result).length <= 1_048_576);
        assertEquals("ABERTO", stateRow(result, "CAP", 0).path("domain").path("payment").asText());
        final String evidence = System.getProperty("devlocal.regras.evidence");
        if (evidence != null) {
            final Path directory = Path.of(evidence);
            Files.createDirectories(directory);
            Files.write(
                    directory.resolve("scale-" + scale + ".json"),
                    DevLocalEngine.bytes(result),
                    StandardOpenOption.CREATE_NEW);
        }
    }

    @Test
    void sqlOnlyFactsRetainMetadataWithoutSubstituteRowsOrNewFinancialRule() {
        final JsonNode result = DevLocalEngine.execute(DevLocalDataset.characterize(), options(2));
        for (int index = 0; index < 5; index++) {
            final JsonNode fact = result.path("facts").path(index);
            assertEquals("MAT-0" + (index + 1), fact.path("id").asText());
            assertEquals("EXCLUIDO_DEV_LOCAL", fact.path("status").asText());
            assertEquals("SQL_ONLY", fact.path("implementation").asText());
            assertFalse(fact.has("rows"));
            assertFalse(fact.path("computed").asBoolean());
            assertFalse(fact.path("applyCapability").asBoolean());
        }
        assertEquals("sourceKey,indicator", result.path("facts").path(0).path("grain").asText());
        assertEquals("date,branch", result.path("facts").path(1).path("grain").asText());
    }

    @Test
    void incrementalUsesRealFreshnessDecisionsAndRecompositionUsesExplicitRelease() {
        final JsonNode first = DevLocalEngine.execute(DevLocalDataset.characterize(), options(2));
        final ObjectNode replayOptions = options(2);
        replayOptions.put("mode", "INCREMENTAL");
        replayOptions.set("previousState", first.path("state"));
        final JsonNode replay =
                DevLocalEngine.execute(DevLocalDataset.characterize(), replayOptions);
        assertEquals(first.path("facts"), replay.path("facts"));
        for (final JsonNode entity : replay.path("entities")) {
            assertEquals(0, entity.path("updates").asInt());
        }
        final ObjectNode changed = DevLocalDataset.sample().deepCopy();
        final ObjectNode freight =
                (ObjectNode) raw(changed, "Fretes").path("pages").path(0).path("rows").path(0);
        freight.put("total", "199.50");
        assertThrows(
                IllegalArgumentException.class,
                () -> DevLocalEngine.execute(DevLocalDataset.characterize(changed), replayOptions));
        freight.put("cte_created_at", "2026-10-01T12:00:00Z");
        final JsonNode updated =
                DevLocalEngine.execute(DevLocalDataset.characterize(changed), replayOptions);
        assertEquals(
                "199.50",
                stateRow(updated, "Fretes", 0)
                        .path("observation")
                        .path("financial")
                        .path("total")
                        .path("typedDecimal")
                        .asText());
        freight.put("cte_created_at", "2026-10-01T09:00:00Z");
        final JsonNode stale =
                DevLocalEngine.execute(DevLocalDataset.characterize(changed), replayOptions);
        assertEquals(first.path("facts"), stale.path("facts"));
        replayOptions.put("mode", "RECOMPOSE");
        replayOptions.put("referenceReleaseId", 2);
        final JsonNode recomposed =
                DevLocalEngine.execute(DevLocalDataset.characterize(), replayOptions);
        assertEquals(2, recomposed.path("facts").path(0).path("referenceReleaseId").asInt());
        assertEquals(
                first.path("state").path("entities").path(0).path("rows"),
                recomposed.path("state").path("entities").path(0).path("rows"));
    }

    @Test
    void originalThirtyThreePoliciesNeverAcquireApplyCapability() {
        final JsonNode result = DevLocalEngine.execute(DevLocalDataset.characterize(), options(2));
        final Set<String> ids = new HashSet<>();
        for (final JsonNode preview : result.path("sweepPreview")) {
            assertTrue(ids.add(preview.path("id").asText()));
            assertNotEquals("ENABLED", preview.path("applicability").asText());
            assertEquals("BLOCKED", preview.path("disposition").asText());
            assertFalse(preview.path("apply").asBoolean());
        }
        assertFalse(result.path("absence").path("canonicalColetaChangesActiveState").asBoolean());
    }

    @Test
    void guardedSyntheticAbsenceSoftDeletesAndReactivationPreservesRows() {
        final ObjectNode complete = cleaned();
        final JsonNode first = DevLocalEngine.execute(complete, options(2));
        final ObjectNode missing = complete.deepCopy();
        final ObjectNode users = (ObjectNode) characterized(missing, "Users");
        ((ArrayNode) users.path("rows")).remove(0);
        users.put("inputRows", users.path("rows").size());
        users.put("accepted", users.path("rows").size());
        final ObjectNode apply = options(2);
        apply.put("mode", "INCREMENTAL");
        apply.set("previousState", first.path("state"));
        final JsonNode noApply = DevLocalEngine.execute(missing, apply);
        assertTrue(stateRow(noApply, "Users", 0).path("active").asBoolean());
        apply.set("sweep", sweep(missing));
        final JsonNode absent = DevLocalEngine.execute(missing, apply);
        assertEquals(2, absent.path("absence").path("deactivated").asInt());
        assertFalse(stateRow(absent, "Users", 0).path("active").asBoolean());
        assertEquals(
                first.path("state").path("entities").path(0).path("rows").size(),
                absent.path("state").path("entities").path(0).path("rows").size());
        apply.set("previousState", absent.path("state"));
        apply.set("sweep", sweep(complete));
        ((ObjectNode) apply.path("sweep")).put("reactivate", true);
        final JsonNode returned = DevLocalEngine.execute(complete, apply);
        assertEquals(2, returned.path("absence").path("reactivated").asInt());
        assertTrue(stateRow(returned, "Users", 0).path("active").asBoolean());
    }

    @Test
    void absenceRefusesPartialSharedOccurrenceQuarantineAndUnknownAuthority() {
        final ObjectNode complete = cleaned();
        final JsonNode first = DevLocalEngine.execute(complete, options(2));
        final ObjectNode options = options(2);
        options.set("previousState", first.path("state"));
        for (final String flag :
                new String[] {"synthetic", "owner", "enabled", "completeSnapshot"}) {
            final ObjectNode request = sweep(complete);
            request.put(flag, false);
            options.set("sweep", request);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DevLocalEngine.execute(complete, options),
                    flag);
        }
        final ObjectNode shared = sweep(complete);
        ((ObjectNode) shared.path("proofs").path(1)).put("run", "run-a");
        options.set("sweep", shared);
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(complete, options));
        final ObjectNode request = sweep(DevLocalDataset.characterize());
        options.set("sweep", request);
        assertThrows(
                IllegalArgumentException.class,
                () -> DevLocalEngine.execute(DevLocalDataset.characterize(), options));
    }

    @Test
    void ledgerPersistsActualOutputReplaysImmutableBytesAndRefusesInputConflict() throws Exception {
        final Path workspace = temporary.resolve("new-workspace");
        final JsonNode dataset = DevLocalDataset.characterize();
        final JsonNode first;
        final long events;
        try (var ledger = DevLocalLedger.create(workspace)) {
            first = DevLocalEngine.execute(ledger, "execution-one", dataset, options(2));
            assertEquals(DevLocalLedger.State.COMPLETED, ledger.readback("execution-one").state());
            events = ledger.readback("execution-one").events().size();
        }
        try (var reopened = DevLocalLedger.open(workspace)) {
            assertEquals(
                    first, DevLocalEngine.execute(reopened, "execution-one", dataset, options(2)));
            assertEquals(events, reopened.readback("execution-one").events().size());
            assertThrows(
                    DevLocalLedger.Failure.class,
                    () -> DevLocalEngine.execute(reopened, "execution-one", dataset, options(4)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCEL", "ERROR"})
    void cancellationAndLocalFailureHaveHonestPersistedTerminalWithoutOutputPublication(
            final String mode) throws Exception {
        try (var ledger = DevLocalLedger.create(temporary.resolve("new-" + mode))) {
            final ObjectNode options = options(2);
            options.put("mode", mode);
            final JsonNode result =
                    DevLocalEngine.execute(
                            ledger, "terminal", DevLocalDataset.characterize(), options);
            assertFalse(result.path("published").asBoolean());
            assertFalse(result.has("state"));
            assertEquals(
                    "CANCEL".equals(mode)
                            ? DevLocalLedger.State.CANCELLED
                            : DevLocalLedger.State.FAILED,
                    ledger.readback("terminal").state());
        }
    }

    @Test
    void boundaryRejectsOptInExternalOptionsCountsAndTamperedPriorState() {
        final JsonNode dataset = DevLocalDataset.characterize();
        assertThrows(
                IllegalArgumentException.class,
                () -> DevLocalEngine.execute(dataset, DevLocalEngine.JSON.createObjectNode()));
        final ObjectNode badOptions = options(2);
        badOptions.put("jdbcUrl", "FORBIDDEN_TEST_TOKEN");
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(dataset, badOptions));
        final ObjectNode counts = dataset.deepCopy();
        ((ObjectNode) counts.path("entities").path(0)).put("accepted", 100);
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(counts, options(2)));
        final JsonNode first = DevLocalEngine.execute(dataset, options(2));
        final ObjectNode prior = first.path("state").deepCopy();
        ((ObjectNode) prior.path("entities").path(0).path("rows").path(0))
                .put("canonicalHash", "a".repeat(64));
        final ObjectNode incremental = options(2);
        incremental.set("previousState", prior);
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(dataset, incremental));
    }

    @Test
    void recoveredStoppedLedgerCannotReplayCompletedJsonAndPreservesEvidence() throws Exception {
        final JsonNode dataset = DevLocalDataset.characterize();
        final ObjectNode options = options(2);
        final ObjectNode input = DevLocalEngine.JSON.createObjectNode();
        input.set("dataset", dataset);
        input.set("options", options);
        try (var ledger = DevLocalLedger.create(temporary.resolve("recovery-mismatch"))) {
            ledger.start("interrupted", DevLocalLedger.sha256(DevLocalEngine.bytes(input)));
            final byte[] completed = DevLocalEngine.bytes(DevLocalEngine.execute(dataset, options));
            ledger.output("interrupted", "result.json", completed);
            ledger.recover("interrupted", "LOCAL_INTERRUPTED");
            final var before = ledger.readback("interrupted");
            final IOException refusal =
                    assertThrows(
                            IOException.class,
                            () -> DevLocalEngine.execute(ledger, "interrupted", dataset, options));
            assertEquals("DEV_LOCAL_REPLAY_TERMINAL_MISMATCH", refusal.getMessage());
            assertEquals(before, ledger.readback("interrupted"));
            assertArrayEquals(completed, ledger.readOutput("interrupted", "result.json"));
        }
    }

    @Test
    void deadlineAfterKnownTerminalDoesNotReturnSuccessOrCompensateCompletedLedger()
            throws Exception {
        final AtomicInteger observations = new AtomicInteger();
        try (var ledger = DevLocalLedger.create(temporary.resolve("late-terminal"))) {
            final IOException refusal =
                    assertThrows(
                            IOException.class,
                            () ->
                                    DevLocalEngine.execute(
                                            ledger,
                                            "late",
                                            DevLocalDataset.characterize(),
                                            options(2),
                                            () ->
                                                    observations.incrementAndGet() >= 7
                                                            ? 31_000_000_000L
                                                            : 0L));
            assertEquals("DEV_LOCAL_CALLER_DEADLINE_EXPIRED_NO_RETRY", refusal.getMessage());
            assertEquals(DevLocalLedger.State.COMPLETED, ledger.readback("late").state());
            assertEquals(
                    "COMPLETED",
                    DevLocalEngine.JSON
                            .readTree(ledger.readOutput("late", "result.json"))
                            .path("status")
                            .asText());
            assertEquals(1, ledger.readback("late").outputs().size());
        }
    }

    @Test
    void undeclaredRelationsNeverGetInventedAndExplicitOrphansAndConflictsAreReported() {
        final ObjectNode options = options(2);
        options.remove("relationBindings");
        final ObjectNode changed = DevLocalDataset.characterize().deepCopy();
        changed.remove("relationBindings");
        changed.remove("relationBindingObservations");
        ((ObjectNode) characterized(changed, "Users").path("rows").path(0))
                .put("name", "Synthetic Changed");
        assertTrue(DevLocalEngine.execute(changed, options).path("relations").isEmpty());
        options.set("relationBindings", options(2).path("relationBindings"));
        ((ObjectNode) options.path("relationBindings").path(0)).put("targetKey", "INTEGER:9999");
        assertEquals(
                "ORPHAN_BLOCKED",
                DevLocalEngine.execute(DevLocalDataset.characterize(), options)
                        .path("relations")
                        .path(0)
                        .path("status")
                        .asText());
        ((ArrayNode) options.path("relationBindings"))
                .add(options.path("relationBindings").path(0).deepCopy());
        assertThrows(
                IllegalArgumentException.class,
                () -> DevLocalEngine.execute(DevLocalDataset.characterize(), options));
    }

    @Test
    void packagedSampleContractIsExplicitAndBoundToExactSourceBytes() {
        final JsonNode dataset = DevLocalDataset.characterize();
        assertEquals(
                DevLocalLedger.sha256(DevLocalEngine.bytes(dataset)),
                DevLocalEngine.sampleContract().path("datasetSha256").asText());
        final ObjectNode options = options(2);
        options.remove("relationBindings");
        final JsonNode result = DevLocalEngine.execute(dataset, options);
        assertEquals(4, result.path("relations").size());
        result.path("relations")
                .forEach(row -> assertEquals("RESOLVED_LOCAL", row.path("status").asText()));
    }

    @Test
    void fonteScopedRelationObservationsKeepBlocksAndRejectMissingOrMismatchedEvidence() {
        final ObjectNode options = options(2);
        options.remove("relationBindings");
        final ObjectNode dataset = DevLocalDataset.characterize().deepCopy();
        assertEquals(
                "OBSERVED_SYNTHETIC_ROOTS",
                DevLocalEngine.execute(dataset, options)
                        .path("relations")
                        .path(0)
                        .path("sourceObservation")
                        .asText());
        for (final String status :
                Set.of(
                        "AMBIGUOUS_BLOCKED",
                        "QUARANTINED_BLOCKED",
                        "OUTSIDE_WINDOW_BLOCKED",
                        "DATE_MISMATCH_BLOCKED")) {
            final ObjectNode blocked = dataset.deepCopy();
            ((ObjectNode) blocked.path("relationBindingObservations").path(0))
                    .put("status", status);
            assertEquals(
                    status,
                    DevLocalEngine.execute(blocked, options)
                            .path("relations")
                            .path(0)
                            .path("status")
                            .asText());
        }
        final ObjectNode missing = dataset.deepCopy();
        missing.remove("relationBindingObservations");
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(missing, options));
        final ObjectNode changed = dataset.deepCopy();
        ((ObjectNode) changed.path("relationBindingObservations").path(0))
                .put("targetKey", "INTEGER:9999");
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(changed, options));
        final ObjectNode empty = dataset.deepCopy();
        empty.putArray("relationBindings");
        assertThrows(IllegalArgumentException.class, () -> DevLocalEngine.execute(empty, options));
        empty.putArray("relationBindingObservations");
        assertTrue(DevLocalEngine.execute(empty, options).path("relations").isEmpty());
        empty.remove(Set.of("relationBindings", "relationBindingObservations"));
        assertTrue(DevLocalEngine.execute(empty, options).path("relations").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ABSENCE_REACTIVATION",
                "PARTIAL_REFUSED",
                "QUARANTINE_REFUSED",
                "OWNER_REFUSED"
            })
    void directScenarioApiPersistsActualWitnessesTransitionsAndImmutableReplay(
            final String scenario) throws Exception {
        final Path workspace = temporary.resolve("direct-scenario");
        final ObjectNode options = options(2);
        options.remove("relationBindings");
        final JsonNode dataset = DevLocalDataset.characterize();
        final JsonNode report;
        try (var ledger = DevLocalLedger.create(workspace)) {
            report = DevLocalEngineScenario.execute(ledger, "direct01", dataset, options, scenario);
            assertEquals("COMPLETED", report.path("status").asText());
            assertFalse(report.path("physicalCompletenessProven").asBoolean());
            assertEquals(
                    "ABSENCE_REACTIVATION".equals(scenario) ? 7 : 2, report.path("runs").size());
            final JsonNode clean =
                    DevLocalEngine.JSON.readTree(
                            ledger.readOutput("direct01-clean", "result.json"));
            if ("ABSENCE_REACTIVATION".equals(scenario)) {
                final JsonNode absent =
                        DevLocalEngine.JSON.readTree(
                                ledger.readOutput("direct01-absent", "result.json"));
                final JsonNode restored =
                        DevLocalEngine.JSON.readTree(
                                ledger.readOutput("direct01-reactivated", "result.json"));
                assertEquals(2, absent.path("absence").path("deactivated").asInt());
                assertEquals(2, restored.path("absence").path("reactivated").asInt());
                for (int index = 0; index < 4; index++) {
                    final JsonNode child = report.path("runs").path(index + 1);
                    final String id = child.path("executionId").asText();
                    assertEquals(2, ledger.readback(id).outputs().size());
                    assertEquals(
                            child.path("absenceOutputSha256").asText(),
                            DevLocalLedger.sha256(ledger.readOutput(id, "result.json")));
                    assertEquals(
                            child.path("reappearanceOutputSha256").asText(),
                            DevLocalLedger.sha256(ledger.readOutput(id, "reappearance.json")));
                }
                assertEquals(activeFlags(clean), activeFlags(restored));
                assertEquals(activeFlags(clean).keySet(), activeFlags(absent).keySet());
                assertEquals(
                        2L,
                        activeFlags(absent).values().stream().filter(active -> !active).count());
            } else {
                assertTrue(report.path("checks").path("applyRefused").asBoolean());
                assertEquals(
                        report.path("checks").path("beforeStateSha256"),
                        report.path("checks").path("afterStateSha256"));
                assertEquals(
                        DevLocalLedger.State.FAILED, ledger.readback("direct01-refused").state());
                assertTrue(ledger.readback("direct01-refused").outputs().isEmpty());
            }
        }
        final String before;
        try (var ledger = DevLocalLedger.open(workspace)) {
            before = DevLocalLedger.sha256(ledger.readOutput("direct01", "result.json"));
            assertArrayEquals(
                    DevLocalEngine.bytes(report),
                    DevLocalEngine.bytes(
                            DevLocalEngineScenario.execute(
                                    ledger, "direct01", dataset, options, scenario)));
            assertEquals(
                    before, DevLocalLedger.sha256(ledger.readOutput("direct01", "result.json")));
        }
        final String evidence = System.getProperty("devlocal.regras.evidence");
        if (evidence != null) {
            final Path retained = Path.of(evidence).resolve("direct-" + scenario);
            Files.createDirectory(retained);
            try (var paths = Files.walk(workspace)) {
                for (final Path source : paths.filter(Files::isRegularFile).toList()) {
                    final Path target = retained.resolve(workspace.relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    private static java.util.Map<String, Boolean> activeFlags(final JsonNode result) {
        final java.util.Map<String, Boolean> rows = new java.util.TreeMap<>();
        for (final JsonNode entity : result.path("state").path("entities")) {
            for (final JsonNode row : entity.path("rows")) {
                rows.put(row.path("technicalKey").asText(), row.path("active").asBoolean());
            }
        }
        return rows;
    }

    @Test
    void cooperativeCancellationDuringBatchesReturnsNoPartialCompletedState() {
        final AtomicInteger signals = new AtomicInteger();
        final JsonNode result =
                DevLocalEngine.execute(
                        DevLocalDataset.characterize(),
                        options(16),
                        () -> signals.incrementAndGet() >= 12);
        assertEquals("CANCELLED", result.path("status").asText());
        assertEquals("LOCAL_COOPERATIVE_CANCELLED", result.path("reasonCode").asText());
        assertFalse(result.has("state"));
        assertFalse(result.path("published").asBoolean());
    }

    @Test
    void serializedReadbackRetainsExactDomainAndDecimalBytesForIncrementalAndRecomposition()
            throws IOException {
        final ObjectNode sample = DevLocalDataset.sample().deepCopy();
        ((ObjectNode) raw(sample, "Fretes").path("pages").path(0).path("rows").path(0))
                .put("total", new java.math.BigDecimal("12345678901234567890.12345678"));
        final JsonNode dataset = DevLocalDataset.characterize(sample);
        final JsonNode first = DevLocalEngine.execute(dataset, options(2));
        final JsonNode readback =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(DevLocalEngine.bytes(first));
        final ObjectNode options = options(2);
        options.put("mode", "INCREMENTAL");
        options.set("previousState", readback.path("state"));
        final JsonNode incremental = DevLocalEngine.execute(dataset, options);
        assertEquals(
                "12345678901234567890.12345678",
                stateRow(incremental, "Fretes", 0)
                        .path("observation")
                        .path("financial")
                        .path("total")
                        .path("typedDecimal")
                        .asText());
        assertEquals(0, characterized(incremental, "Fretes").path("updates").asInt());
        final JsonNode nextReadback =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(DevLocalEngine.bytes(incremental));
        options.put("mode", "RECOMPOSE");
        options.put("referenceReleaseId", 2);
        options.set("previousState", nextReadback.path("state"));
        assertEquals("COMPLETED", DevLocalEngine.execute(dataset, options).path("status").asText());
        final ObjectNode corrupted = nextReadback.path("state").deepCopy();
        final String hash = stateRow(incremental, "Fretes", 0).path("canonicalHash").asText();
        ((ObjectNode) corrupted.path("observations"))
                .put(
                        hash,
                        corrupted
                                .path("observations")
                                .path(hash)
                                .asText()
                                .replace(
                                        "12345678901234567890.12345678",
                                        "12345678901234567890.12345679"));
        options.set("previousState", corrupted);
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalEngine.execute(dataset, options));
    }

    @Test
    void runtimeNativeBootstrapIncrementalRecomposeAndReplayPreserveLedgerBytes() throws Exception {
        final DevLocalMainProcessTest runtimeFixture = new DevLocalMainProcessTest();
        runtimeFixture.temporary = temporary;
        runtimeFixture.realCliIncrementalAndRecompositionReadPreviousCompletedLocalState();
        final Path workspace = temporary.resolve("modes");
        final var command =
                DevLocalMainProcessTest.class.getDeclaredMethod(
                        "command", int.class, String[].class);
        command.setAccessible(true);
        final JsonNode recomposed =
                (JsonNode)
                        command.invoke(
                                runtimeFixture,
                                0,
                                new String[] {
                                    "run",
                                    "--synthetic",
                                    "--workspace",
                                    workspace.toString(),
                                    "--execution",
                                    "recompose-after-incremental",
                                    "--mode",
                                    "RECOMPOSE",
                                    "--previous",
                                    "incremental"
                                });
        assertEquals("COMPLETED", recomposed.path("status").asText());
        final var pins = DevLocalMainProcessTest.class.getDeclaredMethod("pins", Path.class);
        pins.setAccessible(true);
        final Object before = pins.invoke(null, workspace);
        for (final String execution :
                new String[] {"base", "incremental", "recompose-after-incremental"}) {
            final JsonNode replay =
                    (JsonNode)
                            command.invoke(
                                    runtimeFixture,
                                    0,
                                    new String[] {
                                        "replay",
                                        "--synthetic",
                                        "--workspace",
                                        workspace.toString(),
                                        "--execution",
                                        execution
                                    });
            assertEquals("COMPLETED", replay.path("status").asText());
            assertTrue(replay.path("replayed").asBoolean());
        }
        assertEquals(before, pins.invoke(null, workspace));
        final String evidence = System.getProperty("devlocal.regras.evidence");
        if (evidence != null) {
            final Path retained = Path.of(evidence).resolve("runtime-causal-snapshot");
            Files.createDirectory(retained);
            try (var paths = Files.walk(temporary)) {
                for (final Path source : paths.filter(Files::isRegularFile).toList()) {
                    final Path target = retained.resolve(temporary.relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ABSENCE_REACTIVATION",
                "PARTIAL_REFUSED",
                "QUARANTINE_REFUSED",
                "OWNER_REFUSED"
            })
    void runtimeNativeScenarioUsesPersistedTransitionsAndGuardRefusals(final String scenario)
            throws Exception {
        final DevLocalMainProcessTest runtimeFixture = new DevLocalMainProcessTest();
        runtimeFixture.temporary = temporary;
        runtimeFixture.realCliScenarioProvesAbsenceReactivationOrRefusesApplyBeforeOutput(scenario);
        final String evidence = System.getProperty("devlocal.regras.evidence");
        if (evidence != null) {
            final Path retained = Path.of(evidence).resolve("scenario-" + scenario);
            Files.createDirectory(retained);
            try (var paths = Files.walk(temporary)) {
                for (final Path source : paths.filter(Files::isRegularFile).toList()) {
                    final Path target = retained.resolve(temporary.relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    static ObjectNode options(final int scale) {
        final ObjectNode result = DevLocalEngine.JSON.createObjectNode();
        result.put("synthetic", true);
        result.put("scale", scale);
        final ArrayNode bindings = result.putArray("relationBindings");
        bindings.addObject()
                .put("relation", "MC")
                .put("originKey", "INTEGER:2101")
                .put("originComponent", "INTEGER:3101")
                .put("targetKey", "INTEGER:3101")
                .put("targetComponent", "ROOT")
                .put("evidence", "synthetic-test-mc")
                .put("revision", 1)
                .put("cardinality", "ONE_TO_ONE")
                .put("date", "2026-10-01");
        bindings.addObject()
                .put("relation", "CF")
                .put("originKey", "INTEGER:3101")
                .put("originComponent", "INTEGER:4101")
                .put("targetKey", "INTEGER:4101")
                .put("targetComponent", "INTEGER:3101")
                .put("evidence", "synthetic-test-cf")
                .put("revision", 1)
                .put("cardinality", "ONE_TO_ONE")
                .put("date", "2026-10-01");
        return result;
    }

    private static JsonNode raw(final JsonNode sample, final String name) {
        return characterized(sample, name);
    }

    private static JsonNode characterized(final JsonNode sample, final String name) {
        for (final JsonNode entity : sample.path("entities")) {
            if (name.equals(entity.path("entity").asText())) {
                return entity;
            }
        }
        throw new AssertionError("missing entity");
    }

    private static JsonNode stateRow(final JsonNode result, final String name, final int index) {
        return characterized(result.path("state"), name).path("rows").path(index);
    }

    private static ObjectNode cleaned() {
        final ObjectNode dataset = DevLocalDataset.characterize().deepCopy();
        for (final JsonNode item : dataset.path("entities")) {
            final ObjectNode entity = (ObjectNode) item;
            final ArrayNode accepted = DevLocalEngine.JSON.createArrayNode();
            entity.path("rows")
                    .forEach(
                            row -> {
                                if (row.path("eligible").asBoolean()) {
                                    accepted.add(row);
                                }
                            });
            entity.set("rows", accepted);
            entity.put("inputRows", accepted.size());
            entity.put("accepted", accepted.size());
            entity.put("quarantined", 0);
            entity.put("filtered", 0);
        }
        return dataset;
    }

    private static ObjectNode sweep(final JsonNode dataset) {
        final ObjectNode request = DevLocalEngine.JSON.createObjectNode();
        request.put("synthetic", true);
        request.put("owner", true);
        request.put("enabled", true);
        request.put("completeSnapshot", true);
        request.put("apply", true);
        request.put("emptyAllowed", false);
        request.put("snapshotSha256", DevLocalLedger.sha256(DevLocalEngine.bytes(dataset)));
        final ArrayNode proofs = request.putArray("proofs");
        for (final String suffix : new String[] {"a", "b", "c", "d"}) {
            proofs.addObject()
                    .put("run", "run-" + suffix)
                    .put("occurrence", "occurrence-" + suffix);
        }
        return request;
    }
}
