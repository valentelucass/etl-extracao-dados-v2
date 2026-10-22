package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** File-backed CLI invariants in the current JVM; existing process tests remain independent. */
class DevLocalMainOfflineLifecycleTest {
    private static final ObjectMapper JSON = DevLocalMainJson.mapper();
    private static final String INPUT = digest("local business input");
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_OPT_IN", "DUPLICATE", "REMOTE_PATH", "RESERVED"})
    void rejectedInvocationDoesNotCreateWorkspaceOrExposeArguments(final String invalid)
            throws Exception {
        final var workspace = temporary.resolve("not-created");
        final String canary = "SYNTHETIC_REJECTED_ARGUMENT_CANARY";
        final String[] arguments =
                switch (invalid) {
                    case "MISSING_OPT_IN" ->
                            new String[] {
                                "init", "--workspace", workspace.toString(), "--config", canary
                            };
                    case "DUPLICATE" ->
                            new String[] {
                                "init",
                                "--synthetic",
                                "--workspace",
                                workspace.toString(),
                                "--synthetic"
                            };
                    case "REMOTE_PATH" ->
                            new String[] {"init", "--synthetic", "--workspace", "//" + canary};
                    default ->
                            new String[] {
                                "reconcile-start",
                                "--synthetic",
                                "--workspace",
                                workspace.toString(),
                                "--execution",
                                "workspace-init",
                                "--input-sha256",
                                INPUT
                            };
                };
        final var reply = invoke(arguments);
        assertEquals(2, reply.exit());
        assertEquals("REFUSED", reply.receipt().path("status").asText());
        assertFalse(reply.receipt().path("shutdownComplete").asBoolean());
        assertFalse(reply.receipt().toString().contains(canary));
        assertFalse(Files.exists(workspace));
    }

    @Test
    void unsafeSuppliedConfigurationRefusesBeforeOpeningExistingWorkspace() throws Exception {
        final var workspace = initialized("unsafe-config");
        final var before = pins(workspace);
        final var configuration = temporary.resolve("unsafe.json");
        Files.writeString(
                configuration,
                DevLocalMainConfiguration.sample()
                        .document()
                        .put("password", "SYNTHETIC_CONFIG_VALUE_CANARY")
                        .toString());
        final var reply =
                command("run", workspace, "never-started", "--config", configuration.toString());
        assertEquals(2, reply.exit());
        assertEquals("DEV_LOCAL_CONFIG_SCHEMA", reply.receipt().path("reasonCode").asText());
        assertFalse(reply.receipt().path("shutdownComplete").asBoolean());
        assertFalse(reply.receipt().toString().contains("SYNTHETIC_CONFIG_VALUE_CANARY"));
        assertEquals(before, pins(workspace));
    }

    @Test
    void perInvocationConfigurationDoesNotReplaceInitializedConfiguration() throws Exception {
        final var workspace = initialized("configuration-scope");
        final var configuration = temporary.resolve("scale-four.json");
        Files.writeString(
                configuration,
                new DevLocalMainConfiguration(4, 4, 1, 1_048_576, 30).document().toString());
        final var run =
                command("run", workspace, "scale-four", "--config", configuration.toString());
        assertEquals(0, run.exit());
        assertEquals(4, run.receipt().path("result").path("metrics").path("scale").asInt());
        final var before = pins(workspace);
        final var status = command("status", workspace, "scale-four");
        assertEquals(0, status.exit());
        assertEquals(2, status.receipt().path("configuration").path("scale").asInt());
        final var replay = command("replay", workspace, "scale-four");
        assertEquals(0, replay.exit());
        assertEquals(run.receipt().path("result"), replay.receipt().path("result"));
        assertTrue(replay.receipt().path("replayed").asBoolean());
        assertEquals(before, pins(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(
                    2,
                    JSON.readTree(ledger.readOutput("workspace-init", "config.json"))
                            .path("scale")
                            .asInt());
            assertEquals(2, ledger.runs().size());
        }
    }

    @Test
    void selectedStatusDoesNotAdoptLatestExecutionOrChangeDurableBytes() throws Exception {
        final var workspace = initialized("selected-status");
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("active", INPUT);
            ledger.event("active", "LOCAL_WORK", "SYNTHETIC_LOCAL_ONLY");
        }
        final var before = pins(workspace);
        final var latest = command("status", workspace, null);
        assertEquals(0, latest.exit());
        assertEquals("active", latest.receipt().path("executionId").asText());
        assertEquals("STARTED", latest.receipt().path("status").asText());
        assertEquals(0, latest.receipt().path("pendingStarts").asInt());
        assertFalse(latest.receipt().path("pendingStartHold").asBoolean());
        final var selected = command("status", workspace, "workspace-init");
        assertEquals(0, selected.exit());
        assertEquals("workspace-init", selected.receipt().path("executionId").asText());
        assertEquals("COMPLETED", selected.receipt().path("status").asText());
        assertTrue(selected.receipt().path("readbackVerified").asBoolean());
        assertEquals(before, pins(workspace));
    }

    @Test
    void replayOfStartedExecutionRefusesWithoutManufacturingOutputOrTerminalState()
            throws Exception {
        final var workspace = initialized("started-replay");
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("unfinished", INPUT);
        }
        final var before = pins(workspace);
        final var reply = command("replay", workspace, "unfinished");
        assertEquals(3, reply.exit());
        assertEquals("DEV_LOCAL_REPLAY_NOT_TERMINAL", reply.receipt().path("reasonCode").asText());
        assertEquals("STARTED", reply.receipt().path("ledgerState").asText());
        assertTrue(reply.receipt().path("readbackVerified").asBoolean());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        assertEquals(before, pins(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("unfinished").state());
            assertTrue(ledger.readback("unfinished").outputs().isEmpty());
        }
    }

    @Test
    void stopRecoversOnlySelectedExecutionAndRepeatedStopIsReadOnly() throws Exception {
        final var workspace = initialized("selected-stop");
        final DevLocalLedger.Run other;
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("interrupted", INPUT);
            ledger.event("interrupted", "LOCAL_WORK", "SYNTHETIC_LOCAL_ONLY");
            ledger.start("other", digest("other local input"));
            other = ledger.finish("other", DevLocalLedger.State.COMPLETED, "LOCAL_OTHER_FINISHED");
        }
        final var stopped = command("stop", workspace, "interrupted");
        assertEquals(0, stopped.exit());
        assertEquals("STOPPED", stopped.receipt().path("status").asText());
        assertEquals("LOCAL_STOP_REQUESTED", stopped.receipt().path("reasonCode").asText());
        assertFalse(stopped.receipt().path("alreadyTerminal").asBoolean());
        final var before = pins(workspace);
        final var repeated = command("stop", workspace, "interrupted");
        assertEquals(0, repeated.exit());
        assertTrue(repeated.receipt().path("alreadyTerminal").asBoolean());
        assertEquals("STOPPED", repeated.receipt().path("status").asText());
        final var terminal = command("stop", workspace, "other");
        assertEquals(0, terminal.exit());
        assertEquals("COMPLETED", terminal.receipt().path("status").asText());
        assertTrue(terminal.receipt().path("alreadyTerminal").asBoolean());
        assertEquals(before, pins(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(other, ledger.readback("other"));
            assertEquals(1, ledger.readback("interrupted").events().size());
            assertTrue(ledger.readback("interrupted").outputs().isEmpty());
        }
    }

    @Test
    void stopWithoutExecutionWritesOneHonestLocalShutdownAndNoResult() throws Exception {
        final var workspace = initialized("shutdown");
        final var reply = command("stop", workspace, null);
        assertEquals(0, reply.exit());
        assertEquals("STOPPED", reply.receipt().path("status").asText());
        final String execution = reply.receipt().path("executionId").asText();
        assertTrue(execution.startsWith("dev-"));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(2, ledger.runs().size());
            final var run = ledger.readback(execution);
            assertEquals(DevLocalLedger.State.STOPPED, run.state());
            assertEquals("LOCAL_STOP_REQUESTED", run.reasonCode());
            assertEquals(1, run.events().size());
            assertEquals("LOCAL_SHUTDOWN", run.events().get(0).type());
            assertTrue(run.outputs().isEmpty());
        }
        final var before = pins(workspace);
        assertEquals(0, command("status", workspace, null).exit());
        assertEquals(before, pins(workspace));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FALSE_SYNTHETIC", "WRONG_SCOPE", "WRONG_TERMINAL"})
    void replayRefusesPersistedResultOutsideItsDeclaredScopeOrTerminalState(final String invalid)
            throws Exception {
        final var workspace = initialized("invalid-result");
        final var result = localCompletedResult();
        switch (invalid) {
            case "FALSE_SYNTHETIC" -> result.put("synthetic", false);
            case "WRONG_SCOPE" -> result.put("identityScope", "SYNTHETIC_FOREIGN_SCOPE");
            default -> result.put("status", "STOPPED");
        }
        result.put("canary", "SYNTHETIC_DURABLE_VALUE_CANARY");
        completed(workspace, "invalid", result);
        final var before = pins(workspace);
        final var reply = command("replay", workspace, "invalid");
        assertEquals(3, reply.exit());
        assertEquals(
                "WRONG_TERMINAL".equals(invalid)
                        ? "DEV_LOCAL_REPLAY_TERMINAL_MISMATCH"
                        : "DEV_LOCAL_RESULT_SCOPE",
                reply.receipt().path("reasonCode").asText());
        assertEquals("COMPLETED", reply.receipt().path("ledgerState").asText());
        assertFalse(reply.receipt().has("result"));
        assertFalse(reply.receipt().toString().contains("SYNTHETIC_DURABLE_VALUE_CANARY"));
        assertEquals(before, pins(workspace));
    }

    @Test
    void previousExecutionMustBeCompletedBeforeAnyNewStart() throws Exception {
        final var workspace = initialized("previous-started");
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("previous", INPUT);
        }
        final var before = pins(workspace);
        final var reply =
                command(
                        "run",
                        workspace,
                        "not-started",
                        "--mode",
                        "INCREMENTAL",
                        "--previous",
                        "previous");
        assertEquals(2, reply.exit());
        assertEquals(
                "DEV_LOCAL_PREVIOUS_NOT_COMPLETED", reply.receipt().path("reasonCode").asText());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        assertEquals(before, pins(workspace));
    }

    @Test
    void completedPreviousResultWithoutStateDoesNotAdmitRecomposition() throws Exception {
        final var workspace = initialized("previous-state");
        completed(workspace, "previous", localCompletedResult());
        final var before = pins(workspace);
        final var reply =
                command(
                        "run",
                        workspace,
                        "not-started",
                        "--mode",
                        "RECOMPOSE",
                        "--previous",
                        "previous");
        assertEquals(2, reply.exit());
        assertEquals(
                "DEV_LOCAL_PREVIOUS_STATE_REQUIRED", reply.receipt().path("reasonCode").asText());
        assertEquals(before, pins(workspace));
    }

    @Test
    void realCompletedStateFeedsNewModesWithoutRewritingPreviousEvidence() throws Exception {
        final var workspace = initialized("mode-lineage");
        final var base = command("run", workspace, "base");
        assertEquals(0, base.exit());
        final var basePins = pins(workspace.resolve("runs/base"));
        String previous = "base";
        for (final String mode : List.of("INCREMENTAL", "RECOMPOSE")) {
            final String execution = mode.toLowerCase(java.util.Locale.ROOT);
            final var reply =
                    command("run", workspace, execution, "--mode", mode, "--previous", previous);
            assertEquals(0, reply.exit());
            assertEquals("COMPLETED", reply.receipt().path("status").asText());
            assertEquals(mode, reply.receipt().path("result").path("mode").asText());
            assertEquals(rows(base.receipt().path("result")), rows(reply.receipt().path("result")));
            assertEquals(basePins, pins(workspace.resolve("runs/base")));
            previous = execution;
        }
    }

    @Test
    void reconciliationOfAnAppliedIntentIsReadOnlyAndNeverRetriesBusinessWork() throws Exception {
        final var workspace = initialized("applied-start");
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("applied", INPUT);
            ledger.event("applied", "LOCAL_WORK", "SYNTHETIC_LOCAL_ONLY");
        }
        final var before = pins(workspace);
        final var reply = command("reconcile-start", workspace, "applied", "--input-sha256", INPUT);
        assertEquals(0, reply.exit());
        assertEquals("STARTED", reply.receipt().path("status").asText());
        assertTrue(reply.receipt().path("reconciledStart").asBoolean());
        assertFalse(reply.receipt().path("pendingStartCompleted").asBoolean());
        assertFalse(reply.receipt().path("alreadyTerminal").asBoolean());
        assertEquals(
                "RECONCILIATION_CONTROL_ONLY", reply.receipt().path("configurationScope").asText());
        assertEquals(before, pins(workspace));
        assertEquals(0, command("stop", workspace, "applied").exit());
        final var stopped = pins(workspace);
        final var again = command("reconcile-start", workspace, "applied", "--input-sha256", INPUT);
        assertEquals(0, again.exit());
        assertEquals("STOPPED", again.receipt().path("status").asText());
        assertTrue(again.receipt().path("alreadyTerminal").asBoolean());
        assertEquals(stopped, pins(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(2, ledger.runs().size());
            assertEquals(1, ledger.readback("applied").events().size());
            assertTrue(ledger.readback("applied").outputs().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"WRONG_INPUT", "MISSING_INTENT"})
    void reconciliationCannotAdoptAnotherInputOrAnAbsentIntent(final String invalid)
            throws Exception {
        final var workspace = initialized("refused-reconciliation");
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start("applied", INPUT);
        }
        final var before = pins(workspace);
        final var reply =
                command(
                        "reconcile-start",
                        workspace,
                        "WRONG_INPUT".equals(invalid) ? "applied" : "unknown",
                        "--input-sha256",
                        digest("different local input"));
        assertEquals(3, reply.exit());
        assertEquals(
                "WRONG_INPUT".equals(invalid)
                        ? "DEV_LOCAL_EXECUTION_INPUT_CONFLICT"
                        : "DEV_LOCAL_START_INTENT_REQUIRED",
                reply.receipt().path("reasonCode").asText());
        assertFalse(reply.receipt().path("reconciledStart").asBoolean());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        assertEquals(before, pins(workspace));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "error"})
    void explicitCancellationAndFailureKeepTheirDurableVerdictOnReplay(final String operation)
            throws Exception {
        final var workspace = initialized("terminal-replay");
        final var reply = command(operation, workspace, "terminal");
        final boolean failure = "error".equals(operation);
        assertEquals(failure ? 3 : 0, reply.exit());
        assertEquals(failure ? "FAILED" : "CANCELLED", reply.receipt().path("status").asText());
        assertFalse(reply.receipt().path("result").path("published").asBoolean());
        final var before = pins(workspace);
        final var replay = command("replay", workspace, "terminal");
        assertEquals(failure ? 3 : 0, replay.exit());
        assertTrue(replay.receipt().path("replayed").asBoolean());
        assertEquals(reply.receipt().path("result"), replay.receipt().path("result"));
        assertEquals(before, pins(workspace));
    }

    @Test
    void absenceScenarioSummaryCorrespondsToEveryDurableChildAndWitnessOutput() throws Exception {
        final var workspace = initialized("scenario-correspondence");
        final var reply =
                command("scenario", workspace, "scenario", "--case", "ABSENCE_REACTIVATION");
        assertEquals(0, reply.exit());
        final var result = reply.receipt().path("result");
        assertEquals(7, result.path("runs").size());
        assertEquals(7, reply.receipt().path("scenarioRuns").size());
        assertFalse(result.path("physicalCompletenessProven").asBoolean());
        final var ids = new HashSet<String>();
        int witnesses = 0;
        try (var ledger = DevLocalLedger.open(workspace)) {
            for (int index = 0; index < result.path("runs").size(); index++) {
                final var child = result.path("runs").get(index);
                final String id = child.path("executionId").asText();
                assertTrue(ids.add(id));
                final var run = ledger.readback(id);
                final var summary = reply.receipt().path("scenarioRuns").get(index);
                assertEquals(id, summary.path("executionId").asText());
                assertEquals(run.state().name(), summary.path("ledgerState").asText());
                assertEquals(run.reasonCode(), summary.path("reasonCode").asText());
                assertEquals(run.outputs().size(), summary.path("outputs").asInt());
                assertTrue(summary.path("readbackVerified").asBoolean());
                if (child.path("phase").asText().startsWith("LOCAL_WITNESS_")) {
                    witnesses++;
                    assertEquals(2, run.outputs().size());
                    assertEquals(
                            DevLocalLedger.sha256(ledger.readOutput(id, "result.json")),
                            child.path("absenceOutputSha256").asText());
                    assertEquals(
                            DevLocalLedger.sha256(ledger.readOutput(id, "reappearance.json")),
                            child.path("reappearanceOutputSha256").asText());
                }
            }
            assertEquals(4, witnesses);
            assertEquals(9, ledger.runs().size());
        }
        final var before = pins(workspace);
        final var replay = command("replay", workspace, "scenario");
        assertEquals(0, replay.exit());
        assertEquals(result, replay.receipt().path("result"));
        assertEquals(before, pins(workspace));
    }

    private Path initialized(final String name) throws Exception {
        final var workspace = temporary.resolve(name);
        final var reply = command("init", workspace, null);
        assertEquals(0, reply.exit());
        assertEquals("INITIALIZED", reply.receipt().path("status").asText());
        return workspace;
    }

    private static ObjectNode localCompletedResult() {
        return JSON.createObjectNode()
                .put("synthetic", true)
                .put("identityScope", "SYNTHETIC_LOCAL_ONLY")
                .put("status", "COMPLETED")
                .put("reasonCode", "LOCAL_COMPUTATION_COMPLETED");
    }

    private static void completed(
            final Path workspace, final String execution, final JsonNode result) throws Exception {
        try (var ledger = DevLocalLedger.open(workspace)) {
            ledger.start(execution, INPUT);
            ledger.output(execution, "result.json", JSON.writeValueAsBytes(result));
            ledger.finish(execution, DevLocalLedger.State.COMPLETED, "LOCAL_COMPUTATION_COMPLETED");
        }
    }

    private static Reply command(
            final String operation,
            final Path workspace,
            final String execution,
            final String... extra)
            throws Exception {
        final var arguments =
                new ArrayList<>(
                        List.of(operation, "--synthetic", "--workspace", workspace.toString()));
        if (execution != null) {
            arguments.addAll(List.of("--execution", execution));
        }
        arguments.addAll(List.of(extra));
        return invoke(arguments.toArray(String[]::new));
    }

    private static Reply invoke(final String... arguments) throws Exception {
        final var bytes = new ByteArrayOutputStream();
        final int exit;
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exit = DevLocalMain.run(arguments, output);
        }
        final JsonNode receipt = JSON.readTree(bytes.toByteArray());
        assertNotNull(receipt);
        assertEquals(exit, receipt.path("exitCode").asInt());
        assertTrue(receipt.path("synthetic").asBoolean());
        assertEquals("SYNTHETIC_LOCAL_ONLY", receipt.path("identityScope").asText());
        assertFalse(receipt.path("physicalEvidence").asBoolean());
        assertEquals(0, receipt.path("sql").asInt());
        assertEquals(0, receipt.path("httpExternal").asInt());
        assertEquals(0, receipt.path("realSecrets").asInt());
        return new Reply(exit, receipt);
    }

    private static Map<String, String> pins(final Path root) throws Exception {
        final var result = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(
                        root.relativize(path).toString(),
                        DevLocalLedger.sha256(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private static Map<String, JsonNode> rows(final JsonNode result) {
        assertTrue(result.path("state").path("entities").isArray());
        final var rows = new TreeMap<String, JsonNode>();
        for (final var entity : result.path("state").path("entities")) {
            for (final var row : entity.path("rows")) {
                assertNull(rows.put(row.path("technicalKey").asText(), row));
            }
        }
        assertFalse(rows.isEmpty());
        return rows;
    }

    private static String digest(final String value) {
        return DevLocalLedger.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Reply(int exit, JsonNode receipt) {}
}
