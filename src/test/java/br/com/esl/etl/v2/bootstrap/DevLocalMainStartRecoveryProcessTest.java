package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Real CLI JVMs and file locks; controlled START interruption never executes business work. */
class DevLocalMainStartRecoveryProcessTest {
    private static final String EXECUTION = "controlled-start";
    private static final String INPUT =
            DevLocalLedger.sha256("public-runtime-control-input".getBytes(StandardCharsets.UTF_8));
    private Path caseRoot;
    private Path workspace;
    private int children;

    @BeforeEach
    void ownedCase() throws IOException {
        final Path parent =
                Path.of(
                                System.getProperty(
                                        "runtime.start.tests.root",
                                        "target/runtime-start-cli-tests"))
                        .toAbsolutePath();
        Files.createDirectories(parent);
        caseRoot =
                Files.createDirectory(
                        parent.resolve(UUID.randomUUID().toString().substring(0, 12)));
        workspace = caseRoot.resolve("workspace");
    }

    @Test
    void argumentsRequireAnExclusiveTypedReconciliationRequest() {
        final var valid = reconcileArguments(workspace, INPUT);
        final var parsed = DevLocalMainArguments.parse(valid);
        assertEquals("reconcile-start", parsed.command());
        assertEquals(EXECUTION, parsed.executionId());
        assertEquals(INPUT, parsed.inputSha256());
        for (final String flag :
                List.of("--execution", "--input-sha256", "--workspace", "--synthetic")) {
            final var missing = new ArrayList<>(List.of(valid));
            final int offset = missing.indexOf(flag);
            missing.remove(offset);
            if (!flag.equals("--synthetic")) {
                missing.remove(offset);
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DevLocalMainArguments.parse(missing.toArray(String[]::new)));
        }
        for (final String flag : List.of("--config", "--mode", "--previous", "--case")) {
            final var extra = new ArrayList<>(List.of(valid));
            extra.add(flag);
            extra.add(flag.equals("--mode") ? "BOOTSTRAP" : "control");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DevLocalMainArguments.parse(extra.toArray(String[]::new)));
        }
        for (final String flag :
                List.of("--workspace", "--execution", "--input-sha256", "--synthetic")) {
            final var duplicate = new ArrayList<>(List.of(valid));
            duplicate.add(flag);
            if (!flag.equals("--synthetic")) {
                duplicate.add(valid[List.of(valid).indexOf(flag) + 1]);
            }
            assertEquals(
                    "DEV_LOCAL_CLI_DUPLICATE",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            DevLocalMainArguments.parse(
                                                    duplicate.toArray(String[]::new)))
                            .getMessage());
        }
        for (final String badHash :
                List.of(INPUT.toUpperCase(java.util.Locale.ROOT), "0".repeat(63), "g".repeat(64))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DevLocalMainArguments.parse(reconcileArguments(workspace, badHash)));
        }
        final var reserved = valid.clone();
        reserved[5] = "workspace-init";
        assertEquals(
                "DEV_LOCAL_EXECUTION_RESERVED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DevLocalMainArguments.parse(reserved))
                        .getMessage());
        for (final String command :
                List.of("init", "run", "status", "replay", "cancel", "error", "stop", "scenario")) {
            final var outside = valid.clone();
            outside[0] = command;
            assertThrows(
                    IllegalArgumentException.class, () -> DevLocalMainArguments.parse(outside));
        }
        for (final String unsafe :
                List.of(
                        "\\\\server\\share\\workspace",
                        "//server/share/workspace",
                        "https://host/workspace",
                        "C:\\control:stream")) {
            final var remote = valid.clone();
            remote[3] = unsafe;
            assertEquals(
                    "DEV_LOCAL_LOCAL_PATH_REQUIRED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> DevLocalMainArguments.parse(remote))
                            .getMessage());
        }
        assertFalse(Files.exists(workspace));
    }

    @Test
    @EnabledIf("crashOptInPresent")
    void pendingCrashReconcilesThroughFreshCliThenStopsWithoutDuplicateStart() throws Exception {
        pending();
        final var pendingBytes = tree(workspace);
        final var selectedPending = cli(0, command("status"));
        assertEquals("START_PENDING", selectedPending.path("status").asText());
        assertEquals(
                "DEV_LOCAL_START_RECONCILIATION_REQUIRED",
                selectedPending.path("reasonCode").asText());
        assertTrue(selectedPending.path("pendingStartHold").asBoolean());
        assertEquals(1, selectedPending.path("pendingStarts").asInt());
        assertEquals(EXECUTION, selectedPending.path("pendingStartIds").get(0).asText());
        assertFalse(selectedPending.has("ledgerState"));
        assertFalse(selectedPending.has("outputs"));
        final var defaultStatus =
                cli(0, "status", "--workspace", workspace.toString(), "--synthetic");
        assertEquals("COMPLETED", defaultStatus.path("status").asText());
        assertEquals(1, defaultStatus.path("pendingStarts").asInt());
        assertTrue(defaultStatus.path("pendingStartHold").asBoolean());
        assertEquals(pendingBytes, tree(workspace));
        final var started = cli(0, reconcileArguments(workspace, INPUT));
        assertEquals("STARTED", started.path("status").asText());
        assertTrue(started.path("reconciledStart").asBoolean());
        assertTrue(started.path("pendingStartCompleted").asBoolean());
        assertTrue(started.path("readbackVerified").asBoolean());
        assertFalse(started.has("result"));
        final var status = cli(0, command("status"));
        assertEquals("STARTED", status.path("ledgerState").asText());
        assertEquals("STOPPED", cli(0, command("stop")).path("status").asText());
        final var before = tree(workspace);
        final var again = cli(0, reconcileArguments(workspace, INPUT));
        assertEquals("STOPPED", again.path("status").asText());
        assertTrue(again.path("alreadyTerminal").asBoolean());
        assertFalse(again.path("pendingStartCompleted").asBoolean());
        assertEquals(before, tree(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of(), ledger.pendingStartIds());
            assertEquals(DevLocalLedger.State.STOPPED, ledger.readback(EXECUTION).state());
            assertEquals(0, ledger.readback(EXECUTION).outputs().size());
        }
        try (var files = Files.list(workspace.resolve("journal"))) {
            long starts = 0;
            for (final Path path : files.toList()) {
                if (Files.readString(path).contains("\tSTART\t" + EXECUTION + "\t")) {
                    starts++;
                }
            }
            assertEquals(1, starts);
        }
    }

    @Test
    @EnabledIf("crashOptInPresent")
    void wrongExpectedInputRefusesWithoutWriting() throws Exception {
        pending();
        final var before = tree(workspace);
        final var refusal =
                cli(3, reconcileArguments(workspace, DevLocalLedger.sha256(new byte[] {1})));
        assertEquals("DEV_LOCAL_EXECUTION_INPUT_CONFLICT", refusal.path("reasonCode").asText());
        assertFalse(refusal.path("reconciledStart").asBoolean());
        assertEquals(before, tree(workspace));
    }

    @Test
    @EnabledIf("crashOptInPresent")
    void tamperedRealPendingIntentRefusesWithoutWriting() throws Exception {
        pending();
        final Path path = workspace.resolve("start-intents").resolve(EXECUTION + ".intent");
        final byte[] original = Files.readAllBytes(path);
        Files.write(caseRoot.resolve("original.intent"), original, StandardOpenOption.CREATE_NEW);
        final byte[] changed = original.clone();
        changed[changed.length - 2] = changed[changed.length - 2] == '0' ? (byte) '1' : (byte) '0';
        Files.write(path, changed, StandardOpenOption.TRUNCATE_EXISTING);
        final var before = tree(workspace);
        assertEquals(
                "DEV_LOCAL_START_INTENT_CORRUPT",
                cli(3, reconcileArguments(workspace, INPUT)).path("reasonCode").asText());
        assertEquals(before, tree(workspace));
    }

    @Test
    void foreignOrphanRefusesWithoutAdoptingMaterial() throws Exception {
        initialize();
        final Path foreign = Files.createDirectory(workspace.resolve("runs").resolve(EXECUTION));
        Files.writeString(
                foreign.resolve("preserve.txt"),
                "PUBLIC_FOREIGN_CONTROL",
                StandardOpenOption.CREATE_NEW);
        final var before = tree(workspace);
        assertEquals(
                "DEV_LOCAL_START_INTENT_REQUIRED",
                cli(3, reconcileArguments(workspace, INPUT)).path("reasonCode").asText());
        assertEquals(before, tree(workspace));
    }

    @Test
    void realInterprocessLockRefusesAndClosesBeforeNewStatus() throws Exception {
        initialize();
        final var before = tree(workspace);
        try (var held = DevLocalLedger.open(workspace)) {
            assertEquals(
                    "DEV_LOCAL_WORKSPACE_LOCKED",
                    cli(3, reconcileArguments(workspace, INPUT)).path("reasonCode").asText());
            final var unlockedFiles = new LinkedHashMap<>(before);
            unlockedFiles.remove("workspace.lock");
            assertEquals(unlockedFiles, tree(workspace, true));
            assertEquals(0, Files.size(workspace.resolve("workspace.lock")));
            assertEquals(1, held.runs().size());
        }
        assertEquals(
                "COMPLETED",
                cli(0, "status", "--workspace", workspace.toString(), "--synthetic")
                        .path("status")
                        .asText());
        assertEquals(before, tree(workspace));
    }

    @Test
    void workspaceLinkRefusesWithoutChangingItsTarget() throws Exception {
        initialize();
        final Path link = caseRoot.resolve("linked");
        if (System.getProperty("os.name").startsWith("Windows")) {
            final var process =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    link.toString(),
                                    workspace.toString())
                            .start();
            try {
                assertTrue(process.waitFor(3, TimeUnit.SECONDS), "OWN_LINK_FIXTURE_DEADLINE");
                assertEquals(0, process.exitValue());
            } finally {
                close(process);
            }
        } else {
            Files.createSymbolicLink(link, workspace);
        }
        final var before = tree(workspace);
        assertEquals(
                "DEV_LOCAL_LINK_REFUSED",
                cli(3, reconcileArguments(link, INPUT)).path("reasonCode").asText());
        assertEquals(before, tree(workspace));
    }

    @Test
    void existingCliAdmissionStillRejectsOptInConfigurationAndOverwrite() throws Exception {
        cli(
                2,
                "init",
                "--workspace",
                workspace.toString(),
                "--config",
                caseRoot.resolve("unused.json").toString());
        assertFalse(Files.exists(workspace));
        final Path configuration = caseRoot.resolve("unsafe.json");
        Files.writeString(
                configuration,
                DevLocalMainConfiguration.sample().document().put("physical", true).toString(),
                StandardOpenOption.CREATE_NEW);
        cli(
                2,
                "init",
                "--synthetic",
                "--workspace",
                workspace.toString(),
                "--config",
                configuration.toString());
        assertFalse(Files.exists(workspace));
        initialize();
        final var before = tree(workspace);
        cli(3, "init", "--synthetic", "--workspace", workspace.toString());
        assertEquals(before, tree(workspace));
    }

    @Test
    void existingSyntheticRunReplayKeepsItsScopeAndPersistedBytes() throws Exception {
        initialize();
        final var runWire = child(DevLocalMain.class.getName(), command("run"), false);
        assertEquals(0, runWire.exit());
        assertTrue(runWire.stdoutLimited());
        final var executed = receivedHeader(runWire);
        assertEquals("COMPLETED", executed.path("status").asText());
        final byte[] persisted;
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.COMPLETED, ledger.readback(EXECUTION).state());
            persisted = ledger.readOutput(EXECUTION, "result.json");
            assertTrue(DevLocalMainJson.mapper().readTree(persisted).path("synthetic").asBoolean());
        }
        final var before = tree(workspace);
        final var replayWire = child(DevLocalMain.class.getName(), command("replay"), false);
        assertEquals(0, replayWire.exit());
        assertTrue(replayWire.stdoutLimited());
        final var replay = receivedHeader(replayWire);
        assertTrue(replay.path("replayed").asBoolean());
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(
                    DevLocalLedger.sha256(persisted),
                    DevLocalLedger.sha256(ledger.readOutput(EXECUTION, "result.json")));
        }
        final var status = cli(0, command("status"));
        assertEquals("COMPLETED", status.path("status").asText());
        assertEquals(0, status.path("pendingStarts").asInt());
        assertFalse(status.path("pendingStartHold").asBoolean());
        assertEquals(before, tree(workspace));
    }

    private static JsonNode receivedHeader(final Raw wire) throws IOException {
        final var mapper = DevLocalMainJson.mapper();
        final var header = mapper.createObjectNode();
        try (var parser = mapper.createParser(wire.stdout())) {
            assertEquals(com.fasterxml.jackson.core.JsonToken.START_OBJECT, parser.nextToken());
            while (parser.nextToken() == com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
                final String field = parser.currentName();
                parser.nextToken();
                if (field.equals("result")) {
                    break;
                }
                switch (parser.currentToken()) {
                    case VALUE_TRUE, VALUE_FALSE -> header.put(field, parser.getBooleanValue());
                    case VALUE_STRING -> header.put(field, parser.getText());
                    case VALUE_NUMBER_INT -> header.put(field, parser.getLongValue());
                    default -> parser.skipChildren();
                }
            }
        }
        assertTrue(header.path("synthetic").asBoolean());
        assertEquals("SYNTHETIC_LOCAL_ONLY", header.path("identityScope").asText());
        assertFalse(header.path("physicalEvidence").asBoolean());
        assertTrue(header.path("shutdownComplete").asBoolean());
        assertEquals(0, header.path("sql").asInt());
        assertEquals(0, header.path("httpExternal").asInt());
        assertEquals(0, header.path("realSecrets").asInt());
        assertFalse(header.has("result"));
        // A capped prefix is not a complete receipt or a replayed result object.
        return header;
    }

    @Test
    void crashFixtureOptInAbsenceSkipsButPresentIncompleteConfigurationFails() {
        assertThrows(
                org.opentest4j.TestAbortedException.class,
                () -> crashFixture(null, null, null, null, null));
        assertThrows(
                org.opentest4j.TestAbortedException.class,
                () -> crashFixture("false", null, null, null, null));
        for (final String invalid : List.of("TRUE", "yes")) {
            assertEquals(
                    "START_CRASH_TEST_OPT_IN_INVALID",
                    assertThrows(
                                    IllegalStateException.class,
                                    () -> crashFixture(invalid, null, null, null, null))
                            .getMessage());
        }
        assertEquals(
                "START_CRASH_FIXTURE_CONFIGURATION_REQUIRED",
                assertThrows(
                                IllegalStateException.class,
                                () -> crashFixture("true", null, null, null, null))
                        .getMessage());
        assertEquals(
                "START_CRASH_FIXTURE_HASH_REQUIRED",
                assertThrows(
                                IllegalStateException.class,
                                () -> crashFixture("true", "agent.jar", "asm.jar", "bad", "bad"))
                        .getMessage());
    }

    private static boolean crashOptInPresent() {
        final String value = System.getProperty("bank.start.external-fixtures.enabled");
        return value != null && !"false".equals(value);
    }

    private static CrashFixture crashFixture() throws IOException {
        return crashFixture(
                System.getProperty("bank.start.external-fixtures.enabled"),
                System.getProperty("bank.start.crash.agent"),
                System.getProperty("bank.start.crash.asm"),
                System.getProperty("bank.start.crash.agent.sha256"),
                System.getProperty("bank.start.crash.asm.sha256"));
    }

    private static CrashFixture crashFixture(
            final String optIn,
            final String agent,
            final String asm,
            final String agentHash,
            final String asmHash)
            throws IOException {
        assumeTrue(optIn != null && !"false".equals(optIn), "START_CRASH_TEST_OPT_IN_ABSENT");
        if (!"true".equals(optIn)) {
            throw new IllegalStateException("START_CRASH_TEST_OPT_IN_INVALID");
        }
        if (agent == null
                || agent.isBlank()
                || asm == null
                || asm.isBlank()
                || agentHash == null
                || asmHash == null) {
            throw new IllegalStateException("START_CRASH_FIXTURE_CONFIGURATION_REQUIRED");
        }
        if (!agentHash.matches("[a-f0-9]{64}") || !asmHash.matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("START_CRASH_FIXTURE_HASH_REQUIRED");
        }
        return new CrashFixture(fixturePath(agent, agentHash), fixturePath(asm, asmHash));
    }

    private static Path fixturePath(final String value, final String expected) throws IOException {
        if (value.startsWith("\\\\")
                || value.startsWith("//")
                || value.contains("://")
                || value.substring(Math.min(2, value.length())).contains(":")) {
            throw new IllegalStateException("START_CRASH_LOCAL_FIXTURE_REQUIRED");
        }
        final Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().equals(path)
                || Files.size(path) > 1_048_576
                || !DevLocalLedger.sha256(Files.readAllBytes(path)).equals(expected)) {
            throw new IllegalStateException("START_CRASH_FIXTURE_PIN_CONFLICT");
        }
        return path;
    }

    private record CrashFixture(Path agent, Path asm) {}

    private void initialize() throws Exception {
        assertEquals(
                "INITIALIZED",
                cli(0, "init", "--workspace", workspace.toString(), "--synthetic")
                        .path("status")
                        .asText());
    }

    private void pending() throws Exception {
        crashFixture();
        initialize();
        final var raw = child(Child.class.getName(), new String[] {workspace.toString()}, true);
        assertEquals(70, raw.exit());
        assertTrue(raw.stdout().contains("BANK_PROCESS_CRASH_BOUNDARY_INTENT_DURABLE"));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of(EXECUTION), ledger.pendingStartIds());
        }
    }

    private static String[] reconcileArguments(final Path target, final String expected) {
        return new String[] {
            "reconcile-start",
            "--synthetic",
            "--workspace",
            target.toString(),
            "--execution",
            EXECUTION,
            "--input-sha256",
            expected
        };
    }

    private String[] command(final String action) {
        return new String[] {
            action, "--synthetic", "--workspace", workspace.toString(), "--execution", EXECUTION
        };
    }

    private JsonNode cli(final int expectedExit, final String... arguments) throws Exception {
        final var raw = child(DevLocalMain.class.getName(), arguments, false);
        assertFalse(raw.stdoutLimited() || raw.stderrLimited(), "CONTROL_RECEIPT_CAPTURE_LIMIT");
        assertEquals(expectedExit, raw.exit(), raw.stderr());
        assertTrue(raw.stderr().isEmpty(), "OWN_CHILD_STDERR");
        final var receipt = DevLocalMainJson.mapper().readTree(raw.stdout());
        assertTrue(receipt.path("synthetic").asBoolean());
        assertEquals("SYNTHETIC_LOCAL_ONLY", receipt.path("identityScope").asText());
        assertFalse(receipt.path("physicalEvidence").asBoolean());
        assertEquals(0, receipt.path("sql").asInt());
        assertEquals(0, receipt.path("httpExternal").asInt());
        assertEquals(0, receipt.path("realSecrets").asInt());
        assertEquals(expectedExit, receipt.path("exitCode").asInt());
        if (expectedExit == 0) {
            assertTrue(receipt.path("shutdownComplete").asBoolean());
        }
        assertFalse(raw.stdout().contains(INPUT));
        return receipt;
    }

    private Raw child(final String main, final String[] arguments, final boolean interrupt)
            throws Exception {
        final var fixture = interrupt ? crashFixture() : null;
        final var args = new ArrayList<String>();
        args.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        args.add("-Xmx128m");
        if (interrupt) {
            args.add("-javaagent:" + fixture.agent() + "=INTENT_DURABLE");
        }
        args.add("-cp");
        args.add(
                System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path"))
                        + (interrupt ? java.io.File.pathSeparator + fixture.asm() : ""));
        args.add(main);
        args.addAll(List.of(arguments));
        final String name = "child" + (++children);
        Files.writeString(
                caseRoot.resolve(name + ".reservation.json"),
                "{\"deadlineSeconds\":10,\"cleanupSeconds\":3,\"retry\":0,\"SQL\":0,\"HTTP\":0,\"ownedChild\":true}",
                StandardOpenOption.CREATE_NEW);
        final var process = new ProcessBuilder(args).start();
        final var readers = Executors.newFixedThreadPool(2);
        final Future<Captured> stdout =
                readers.submit(
                        () ->
                                capture(
                                        process.getInputStream(),
                                        caseRoot.resolve(name + ".stdout")));
        final Future<Captured> stderr =
                readers.submit(
                        () ->
                                capture(
                                        process.getErrorStream(),
                                        caseRoot.resolve(name + ".stderr")));
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "OWN_CHILD_DEADLINE");
            final var output = stdout.get(3, TimeUnit.SECONDS);
            final var error = stderr.get(3, TimeUnit.SECONDS);
            assertTrue(output.complete() || output.limited(), "OWN_STDOUT_CLOSE_UNCERTAIN");
            assertTrue(error.complete() || error.limited(), "OWN_STDERR_CLOSE_UNCERTAIN");
            Files.writeString(
                    caseRoot.resolve(name + ".exit"),
                    Integer.toString(process.exitValue()),
                    StandardOpenOption.CREATE_NEW);
            return new Raw(
                    process.exitValue(),
                    new String(output.bytes(), StandardCharsets.UTF_8),
                    new String(error.bytes(), StandardCharsets.UTF_8),
                    output.limited(),
                    error.limited());
        } finally {
            close(process);
            readers.shutdown();
            if (!readers.awaitTermination(3, TimeUnit.SECONDS)) {
                readers.shutdownNow();
                throw new AssertionError("OWN_CAPTURE_THREADS_CLOSE_UNCERTAIN");
            }
            Files.writeString(
                    caseRoot.resolve(name + ".closed"),
                    "OWN_PROCESS_AND_STREAMS_CLOSED",
                    StandardOpenOption.CREATE_NEW);
        }
    }

    private static Captured capture(final InputStream stream, final Path output)
            throws IOException {
        final var bytes = new ByteArrayOutputStream();
        boolean limited = false;
        boolean complete = false;
        try (stream) {
            final byte[] buffer = new byte[4096];
            while (bytes.size() < 65_536) {
                final int count =
                        stream.read(buffer, 0, Math.min(buffer.length, 65_536 - bytes.size()));
                if (count == -1) {
                    complete = true;
                    break;
                }
                bytes.write(buffer, 0, count);
            }
            if (!complete) {
                limited = stream.read() != -1;
                complete = !limited;
            }
        } catch (final IOException failure) {
            complete = false;
        } finally {
            Files.write(output, bytes.toByteArray(), StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    Path.of(output + ".capture.json"),
                    "{\"retainedBytes\":"
                            + bytes.size()
                            + ",\"limited\":"
                            + limited
                            + ",\"EOF\":"
                            + complete
                            + ",\"cap\":65536}",
                    StandardOpenOption.CREATE_NEW);
        }
        return new Captured(bytes.toByteArray(), limited, complete);
    }

    private static void close(final Process process) throws Exception {
        if (process.isAlive()) {
            process.destroyForcibly();
            assertTrue(process.waitFor(3, TimeUnit.SECONDS), "OWN_CHILD_CLOSE_UNCERTAIN");
        }
        process.getInputStream().close();
        process.getErrorStream().close();
        process.getOutputStream().close();
    }

    private static Map<String, String> tree(final Path root) throws IOException {
        return tree(root, false);
    }

    private static Map<String, String> tree(final Path root, final boolean ownLockHeld)
            throws IOException {
        final var result = new LinkedHashMap<String, String>();
        try (var files = Files.walk(root)) {
            for (final Path path : files.sorted().toList()) {
                if (ownLockHeld && path.equals(root.resolve("workspace.lock"))) {
                    continue;
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    result.put(
                            root.relativize(path).toString(),
                            DevLocalLedger.sha256(Files.readAllBytes(path)));
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    result.put(root.relativize(path).toString(), "DIRECTORY");
                }
            }
        }
        return result;
    }

    private record Raw(
            int exit, String stdout, String stderr, boolean stdoutLimited, boolean stderrLimited) {}

    private record Captured(byte[] bytes, boolean limited, boolean complete) {}

    public static final class Child {
        private Child() {}

        public static void main(final String[] arguments) throws Exception {
            try (var ledger = DevLocalLedger.open(Path.of(arguments[0]))) {
                ledger.start(EXECUTION, INPUT);
            }
        }
    }
}
