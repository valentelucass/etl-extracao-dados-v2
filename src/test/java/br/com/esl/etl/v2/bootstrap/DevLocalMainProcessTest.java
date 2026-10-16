package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevLocalMainProcessTest {
    private static final ObjectMapper JSON = DevLocalMainJson.mapper();
    @TempDir Path temporary;
    private int launches;

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    void realCliFourScalesPersistAndReplayWithoutChangingLedger(final int scale) throws Exception {
        final Path workspace = temporary.resolve("synthetic-workspace");
        final Path config = temporary.resolve("config.json");
        Files.writeString(
                config,
                new DevLocalMainConfiguration(scale, 4, 1, 1_048_576, 30).document().toString());
        assertEquals(
                "INITIALIZED",
                command(
                                0,
                                "init",
                                "--synthetic",
                                "--workspace",
                                workspace.toString(),
                                "--config",
                                config.toString())
                        .path("status")
                        .asText());
        final var executed =
                command(
                        0,
                        "run",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "synthetic-run");
        assertEquals("COMPLETED", executed.path("status").asText());
        assertEquals(11, executed.path("result").path("entities").size());
        assertEquals(scale, executed.path("result").path("metrics").path("scale").asInt());
        assertEquals(1, executed.path("result").path("metrics").path("maximumInFlight").asInt());
        assertTrue(executed.path("result").path("metrics").path("processedRows").asInt() > 0);
        assertTrue(executed.path("outputBytes").asLong() > 0);
        final var before = pins(workspace);
        final var replay =
                command(
                        0,
                        "replay",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "synthetic-run");
        assertTrue(replay.path("replayed").asBoolean());
        assertEquals(executed.path("result"), replay.path("result"));
        assertEquals(before, pins(workspace));
        assertEquals(
                "COMPLETED",
                command(
                                0,
                                "status",
                                "--synthetic",
                                "--workspace",
                                workspace.toString(),
                                "--execution",
                                "synthetic-run")
                        .path("status")
                        .asText());
        assertEquals(before, pins(workspace));
    }

    @Test
    void realCliCancellationErrorAndShutdownHaveHonestPersistedTerminalStates() throws Exception {
        final Path workspace = temporary.resolve("lifecycle");
        command(0, "init", "--synthetic", "--workspace", workspace.toString());
        final var cancel =
                command(
                        0,
                        "cancel",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "synthetic-cancel");
        assertEquals("CANCELLED", cancel.path("status").asText());
        assertFalse(cancel.path("result").path("published").asBoolean());
        final var error =
                command(
                        3,
                        "error",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "synthetic-error");
        assertEquals("FAILED", error.path("status").asText());
        assertFalse(error.path("result").path("published").asBoolean());
        assertEquals(
                "FAILED",
                command(
                                3,
                                "status",
                                "--synthetic",
                                "--workspace",
                                workspace.toString(),
                                "--execution",
                                "synthetic-error")
                        .path("status")
                        .asText());
        final var stopped = command(0, "stop", "--synthetic", "--workspace", workspace.toString());
        assertEquals("STOPPED", stopped.path("status").asText());
        assertTrue(stopped.path("shutdownComplete").asBoolean());
        command(0, "status", "--synthetic", "--workspace", workspace.toString());
    }

    @Test
    void realCliRefusesMissingOptInUnsafeConfigurationAndExistingWorkspace() throws Exception {
        final Path workspace = temporary.resolve("refused");
        assertEquals(
                "REFUSED",
                command(
                                2,
                                "init",
                                "--workspace",
                                workspace.toString(),
                                "--config",
                                temporary.resolve("unused.json").toString())
                        .path("status")
                        .asText());
        assertFalse(Files.exists(workspace));
        final Path config = temporary.resolve("unsafe.json");
        Files.writeString(
                config,
                DevLocalMainConfiguration.sample()
                        .document()
                        .put("password", "SYNTHETIC_CONFIG_CANARY")
                        .toString());
        final var refused =
                command(
                        2,
                        "init",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--config",
                        config.toString());
        assertFalse(refused.toString().contains("SYNTHETIC_CONFIG_CANARY"));
        assertFalse(Files.exists(workspace));
        command(0, "init", "--synthetic", "--workspace", workspace.toString());
        final var before = pins(workspace);
        command(3, "init", "--synthetic", "--workspace", workspace.toString());
        assertEquals(before, pins(workspace));
    }

    @Test
    void realCliIncrementalAndRecompositionReadPreviousCompletedLocalState() throws Exception {
        final Path workspace = temporary.resolve("modes");
        command(0, "init", "--synthetic", "--workspace", workspace.toString());
        command(
                0,
                "run",
                "--synthetic",
                "--workspace",
                workspace.toString(),
                "--execution",
                "base");
        String previous = "base";
        for (final String mode : new String[] {"INCREMENTAL", "RECOMPOSE"}) {
            final var result =
                    command(
                            0,
                            "run",
                            "--synthetic",
                            "--workspace",
                            workspace.toString(),
                            "--execution",
                            mode.toLowerCase(java.util.Locale.ROOT),
                            "--mode",
                            mode,
                            "--previous",
                            previous);
            assertEquals(mode, result.path("result").path("mode").asText());
            assertEquals("COMPLETED", result.path("status").asText());
            final var before = pins(workspace);
            final var replay =
                    command(
                            0,
                            "replay",
                            "--synthetic",
                            "--workspace",
                            workspace.toString(),
                            "--execution",
                            mode.toLowerCase(java.util.Locale.ROOT));
            assertEquals(result.path("result"), replay.path("result"));
            assertEquals(before, pins(workspace));
            previous = mode.toLowerCase(java.util.Locale.ROOT);
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
    void realCliScenarioProvesAbsenceReactivationOrRefusesApplyBeforeOutput(final String scenario)
            throws Exception {
        final var workspace = temporary.resolve("scenario-workspace");
        command(0, "init", "--synthetic", "--workspace", workspace.toString());
        final var reply =
                command(
                        0,
                        "scenario",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "scenario01",
                        "--case",
                        scenario);
        final var result = reply.path("result");
        assertEquals(scenario, result.path("scenario").asText());
        assertEquals("ABSENCE_REACTIVATION".equals(scenario) ? 7 : 2, result.path("runs").size());
        assertEquals(result.path("runs").size(), reply.path("scenarioRuns").size());
        final var phases = new java.util.HashMap<String, JsonNode>();
        try (final var ledger = DevLocalLedger.open(workspace)) {
            for (final var run : result.path("runs")) {
                final var readback = ledger.readback(run.path("executionId").asText());
                assertEquals(run.path("state").asText(), readback.state().name());
                if (readback.state() == DevLocalLedger.State.COMPLETED) {
                    final var phaseResult =
                            JSON.readTree(ledger.readOutput(readback.executionId(), "result.json"));
                    if (java.util.Set.of("CLEAN", "ABSENCE", "REACTIVATION")
                            .contains(run.path("phase").asText())) {
                        phases.put(run.path("phase").asText(), phaseResult);
                    } else {
                        assertTrue(run.path("phase").asText().matches("LOCAL_WITNESS_[1-4]"));
                        assertEquals(2, readback.outputs().size());
                        final var absentBytes =
                                ledger.readOutput(readback.executionId(), "result.json");
                        final var returnedBytes =
                                ledger.readOutput(readback.executionId(), "reappearance.json");
                        assertEquals(
                                DevLocalLedger.sha256(absentBytes),
                                run.path("absenceOutputSha256").asText());
                        assertEquals(
                                DevLocalLedger.sha256(returnedBytes),
                                run.path("reappearanceOutputSha256").asText());
                        final var returned = JSON.readTree(returnedBytes);
                        assertEquals("COMPLETED", returned.path("status").asText());
                        assertEquals(
                                "SYNTHETIC_LOCAL_ONLY", returned.path("identityScope").asText());
                        final var absentKeys = activeRows(phaseResult).keySet();
                        final var returnedKeys = activeRows(returned).keySet();
                        assertTrue(returnedKeys.containsAll(absentKeys));
                        assertEquals(
                                2,
                                returnedKeys.size() - absentKeys.size(),
                                "two scaled Users roots absent from witness snapshot");
                    }
                } else {
                    assertEquals(DevLocalLedger.State.FAILED, readback.state());
                    assertEquals(
                            0, readback.outputs().size(), "refused apply emitted no state output");
                }
            }
        }
        final var clean = activeRows(phases.get("CLEAN"));
        assertTrue(!clean.isEmpty() && clean.values().stream().allMatch(Boolean::booleanValue));
        if ("ABSENCE_REACTIVATION".equals(scenario)) {
            final var absence = activeRows(phases.get("ABSENCE"));
            final var reactivation = activeRows(phases.get("REACTIVATION"));
            assertEquals(
                    clean.keySet(), absence.keySet(), "absence retained all previous identities");
            assertEquals(clean, reactivation, "reactivation restored the exact local identities");
            final long changed = absence.values().stream().filter(active -> !active).count();
            assertTrue(changed > 0, "actual absence must change persisted local flags");
            assertEquals(2, changed, "one built-in Users root at configured scale2");
            assertFalse(
                    phases.get("ABSENCE")
                            .path("absence")
                            .path("canonicalColetaChangesActiveState")
                            .asBoolean());
            assertEquals(
                    changed, phases.get("ABSENCE").path("absence").path("deactivated").asLong());
            assertEquals(
                    changed,
                    phases.get("REACTIVATION").path("absence").path("reactivated").asLong());
        } else {
            final var checks = result.path("checks");
            assertTrue(checks.path("applyRefused").asBoolean());
            assertTrue(checks.path("refusalCode").asText().startsWith("DEV_LOCAL_SWEEP_"));
            assertTrue(checks.path("beforeStateSha256").asText().matches("[a-f0-9]{64}"));
            assertEquals(checks.path("beforeStateSha256"), checks.path("afterStateSha256"));
            assertEquals(1, phases.size(), "only the clean state was emitted before guard refusal");
        }
        final var before = pins(workspace);
        final var replay =
                command(
                        0,
                        "replay",
                        "--synthetic",
                        "--workspace",
                        workspace.toString(),
                        "--execution",
                        "scenario01");
        assertEquals(result, replay.path("result"));
        assertEquals(before, pins(workspace));
        command(
                0,
                "status",
                "--synthetic",
                "--workspace",
                workspace.toString(),
                "--execution",
                "scenario01");
        assertEquals(before, pins(workspace));
    }

    private static Map<String, Boolean> activeRows(final JsonNode result) {
        assertTrue(
                result != null && result.path("state").path("entities").isArray(),
                "persisted phase state");
        final var rows = new java.util.TreeMap<String, Boolean>();
        for (final var entity : result.path("state").path("entities")) {
            for (final var row : entity.path("rows")) {
                assertTrue(row.path("active").isBoolean());
                assertNull(
                        rows.put(
                                row.path("technicalKey").asText(), row.path("active").asBoolean()));
            }
        }
        return rows;
    }

    private JsonNode command(final int expectedExit, final String... arguments) throws Exception {
        final var command = new ArrayList<String>();
        command.add(
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString());
        command.addAll(
                java.util.List.of(
                        "-Dfile.encoding=UTF-8",
                        "-Xmx128m",
                        "-cp",
                        System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path")),
                        DevLocalMain.class.getName()));
        command.addAll(java.util.List.of(arguments));
        final var builder = new ProcessBuilder(command);
        final var environment = builder.environment();
        final var inherited = Map.copyOf(environment);
        environment.clear();
        for (final String key :
                new String[] {"SystemRoot", "WINDIR", "TEMP", "TMP", "USERPROFILE", "HOME"}) {
            if (inherited.containsKey(key)) {
                environment.put(key, inherited.get(key));
            }
        }
        final var stdout = temporary.resolve("cli-" + (++launches) + ".stdout");
        final var stderr = temporary.resolve("cli-" + launches + ".stderr");
        final var child =
                builder.redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        final boolean closed = child.waitFor(45, TimeUnit.SECONDS);
        if (!closed) {
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "owned CLI did not close");
        }
        final var archive = System.getProperty("v2.dev.local.test.receipts");
        if (archive != null) {
            final var destination =
                    Path.of(archive).resolve(temporary.getFileName() + "-" + launches);
            Files.createDirectories(destination.getParent());
            Files.copy(stdout, Path.of(destination + ".stdout.json"));
            Files.copy(stderr, Path.of(destination + ".stderr.log"));
            Files.writeString(
                    Path.of(destination + ".process.json"),
                    JSON.createObjectNode()
                            .put("pid", child.pid())
                            .put("nativeExit", child.exitValue())
                            .put("ownerProcessExited", !child.isAlive())
                            .put("withinTimeout", closed)
                            .put("timeoutSeconds", 45)
                            .put("expectedExit", expectedExit)
                            .toString(),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        assertTrue(closed, "owned CLI exceeded finite timeout");
        assertEquals(expectedExit, child.exitValue(), "native CLI exit");
        assertTrue(Files.size(stdout) <= 2 * 1_048_576L);
        assertTrue(Files.size(stderr) <= 1_048_576L);
        final var receipt = JSON.readTree(Files.readAllBytes(stdout));
        assertEquals(expectedExit, receipt.path("exitCode").asInt(-1));
        assertTrue(receipt.path("synthetic").asBoolean());
        assertEquals("SYNTHETIC_LOCAL_ONLY", receipt.path("identityScope").asText());
        assertEquals(0, receipt.path("sql").asInt());
        assertEquals(0, receipt.path("httpExternal").asInt());
        assertEquals(0, receipt.path("realSecrets").asInt());
        assertTrue(receipt.path("elapsedNanos").asLong() > 0);
        assertTrue(receipt.path("jvmMaximumHeapBytes").asLong() <= 128L * 1024 * 1024);
        if (expectedExit == 0 || "FAILED".equals(receipt.path("status").asText())) {
            assertTrue(receipt.path("shutdownComplete").asBoolean(), "ledger close before receipt");
            assertTrue(receipt.path("readbackVerified").asBoolean());
        }
        return receipt;
    }

    private static Map<String, String> pins(final Path workspace) throws Exception {
        final var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(workspace)) {
            for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(
                        workspace.relativize(path).toString(),
                        java.util.HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(Files.readAllBytes(path))));
            }
        }
        return result;
    }
}
