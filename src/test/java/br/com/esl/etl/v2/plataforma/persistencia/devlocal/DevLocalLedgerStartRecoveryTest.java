package br.com.esl.etl.v2.plataforma.persistencia.devlocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Real file ledger and JVMs. External crash/baseline fixtures require
 * bank.start.external-fixtures.enabled=true plus the three hash-pinned paths checked below. Without
 * the opt-in only dependent cases explicitly abort; independent cases still execute.
 */
class DevLocalLedgerStartRecoveryTest {
    private static final String INPUT = DevLocalLedger.sha256(bytes("local-controlled-input"));
    private Path caseRoot;
    private Path workspace;

    private record ExternalFixture(Path agent, Path asm, Path legacy) {}

    @BeforeEach
    void ownedCase(final TestInfo test) throws IOException {
        final Path parent =
                Path.of(
                                System.getProperty(
                                        "bank.start.tests.root",
                                        "target/banco-start-recovery-tests"))
                        .toAbsolutePath();
        Files.createDirectories(parent);
        caseRoot =
                Files.createDirectory(
                        parent.resolve(
                                test.getTestMethod().orElseThrow().getName().substring(0, 6)
                                        + "-"
                                        + UUID.randomUUID()));
        workspace = caseRoot.resolve("workspace");
    }

    @Test
    void externalFixtureOptInAbsentIsExplicitAndDoesNotRequirePaths() throws Exception {
        assertNull(externalFixture(null, null, null, null));
        assertNull(externalFixture("false", "unused", "unused", "unused"));
    }

    @Test
    void externalFixtureOptInMalformedOrIncompleteRefusesBeforeChildLaunch() {
        assertEquals(
                "BANK_START_FIXTURE_OPT_IN_INVALID",
                assertThrows(IOException.class, () -> externalFixture("TRUE", null, null, null))
                        .getMessage());
        assertEquals(
                "BANK_START_FIXTURE_INCOMPLETE",
                assertThrows(
                                IOException.class,
                                () -> externalFixture("true", "provided", null, "provided"))
                        .getMessage());
    }

    @Test
    void externalFixturePinMismatchRefusesBeforeChildLaunch() throws Exception {
        final Path wrong = caseRoot.resolve("wrong-agent.jar");
        Files.write(wrong, bytes("OWN_INVALID_PUBLIC_FIXTURE"), StandardOpenOption.CREATE_NEW);
        final String path = wrong.toString();
        assertEquals(
                "BANK_START_FIXTURE_PIN_MISMATCH",
                assertThrows(IOException.class, () -> externalFixture("true", path, path, path))
                        .getMessage());
        assertFalse(Files.exists(workspace));
    }

    @Test
    void completedStartBoundariesRecoverInFreshJvmsWithoutDuplicateStart() throws Exception {
        for (final String point :
                List.of(
                        "INTENT_DURABLE",
                        "DIRECTORY_1",
                        "DIRECTORY_2",
                        "DIRECTORY_3",
                        "DIRECTORY_4",
                        "BEFORE_START",
                        "AFTER_START")) {
            final Path target = caseRoot.resolve(point.toLowerCase());
            assertEquals(70, child("start", target, point, false));
            final var before = tree(target);
            assertEquals(0, child("reconcile", target, null, false));
            try (var ledger = DevLocalLedger.open(target)) {
                assertEquals(DevLocalLedger.State.STARTED, ledger.readback("run1").state());
                assertEquals(List.of(), ledger.pendingStartIds());
                final var first = ledger.reconcileStart("run1", INPUT);
                final var after = tree(target);
                assertEquals(first, ledger.reconcileStart("run1", INPUT));
                assertEquals(after, tree(target));
                refuses("RECOVERY_REQUIRED", () -> ledger.start("run1", INPUT));
                assertEquals(1, count(target.resolve("journal")));
                assertEquals(1, count(target.resolve("start-intents")));
            }
            for (final var e : before.entrySet()) {
                assertEquals(e.getValue(), tree(target).get(e.getKey()), "PREVIOUS_FILE_PRESERVED");
            }
        }
    }

    @Test
    void crashBeforeIntentLeavesNoRunAndFreshStartIsExplicit() throws Exception {
        assertEquals(70, child("start", workspace, "BEFORE_INTENT", false));
        assertFalse(Files.exists(workspace.resolve("runs/run1")));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of(), ledger.pendingStartIds());
            refuses("START_INTENT_REQUIRED", () -> ledger.reconcileStart("run1", INPUT));
            assertEquals(DevLocalLedger.State.STARTED, ledger.start("run1", INPUT).state());
            assertEquals(1, count(workspace.resolve("journal")));
        }
    }

    @Test
    void pendingOpenAndStatusAreReadOnlyAndExplicitReconciliationSurvivesTerminalState()
            throws Exception {
        assertEquals(70, child("start", workspace, "INTENT_DURABLE", false));
        final var before = tree(workspace);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of(), ledger.runs());
            assertEquals(List.of("run1"), ledger.pendingStartIds());
            refuses("RUN_NOT_FOUND", () -> ledger.readback("run1"));
        }
        assertEquals(before, tree(workspace));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(
                    DevLocalLedger.State.STARTED, ledger.reconcileStart("run1", INPUT).state());
            final var stopped = ledger.recover("run1", "EXPLICIT_INTERRUPTED_STOP");
            final var stoppedBytes = tree(workspace);
            assertEquals(DevLocalLedger.State.STOPPED, stopped.state());
            assertEquals(stopped, ledger.reconcileStart("run1", INPUT));
            assertEquals(stoppedBytes, tree(workspace));
        }
    }

    @Test
    void legacyJournalWrittenByOriginalCodeStillOpensWithoutIntent() throws Exception {
        assertEquals(0, child("legacy", workspace, null, true));
        assertFalse(Files.exists(workspace.resolve("start-intents")));
        final var before = tree(workspace);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STOPPED, ledger.readback("legacy1").state());
            assertEquals(List.of(), ledger.pendingStartIds());
            assertEquals(DevLocalLedger.State.STOPPED, ledger.start("legacy1", INPUT).state());
            refuses("START_INTENT_REQUIRED", () -> ledger.reconcileStart("legacy1", INPUT));
        }
        assertEquals(before, tree(workspace));
    }

    @Test
    void foreignOrphanWithoutIntentCannotBeAdopted() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            Files.createDirectory(workspace.resolve("runs/run1"));
            Files.writeString(
                    workspace.resolve("runs/run1/keep.txt"),
                    "FOREIGN_TEST_MATERIAL",
                    StandardOpenOption.CREATE_NEW);
            final var before = tree(workspace);
            refuses("ORPHAN_RUN_CONFLICT", () -> ledger.start("run1", INPUT));
            refuses("START_INTENT_REQUIRED", () -> ledger.reconcileStart("run1", INPUT));
            assertEquals(before, tree(workspace));
            assertFalse(Files.exists(workspace.resolve("start-intents")));
        }
    }

    @Test
    void inputMismatchAndAnotherStartCannotConsumePendingJournalSlot() throws Exception {
        assertEquals(70, child("start", workspace, "INTENT_DURABLE", false));
        final var before = tree(workspace);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of("run1"), ledger.pendingStartIds());
            refuses(
                    "EXECUTION_INPUT_CONFLICT",
                    () -> ledger.reconcileStart("run1", DevLocalLedger.sha256(bytes("different"))));
            refuses(
                    "EXECUTION_INPUT_CONFLICT",
                    () -> ledger.start("run1", DevLocalLedger.sha256(bytes("different"))));
            refuses("START_RECONCILIATION_REQUIRED", () -> ledger.start("run2", INPUT));
        }
        assertEquals(before, tree(workspace));
    }

    @Test
    void existingUnexpectedMaterialRefusesBeforeCreatingMissingDirectoriesOrJournal()
            throws Exception {
        assertEquals(70, child("start", workspace, "DIRECTORY_2", false));
        Files.writeString(
                workspace.resolve("runs/run1/events/foreign.txt"),
                "PRESERVE_NEGATIVE_CASE",
                StandardOpenOption.CREATE_NEW);
        final var before = tree(workspace);
        try (var ledger = DevLocalLedger.open(workspace)) {
            refuses("START_MATERIAL_CONFLICT", () -> ledger.reconcileStart("run1", INPUT));
        }
        assertEquals(before, tree(workspace));
        assertFalse(Files.exists(workspace.resolve("runs/run1/outputs")));
        assertEquals(0, count(workspace.resolve("journal")));
    }

    @Test
    void tamperAndPartialIntentRefuseWithoutJournalMutation() throws Exception {
        for (final boolean partial : new boolean[] {false, true}) {
            final Path target = caseRoot.resolve(partial ? "partial" : "tamper");
            assertEquals(70, child("start", target, "INTENT_DURABLE", false));
            final Path intent = target.resolve("start-intents/run1.intent");
            final byte[] original = Files.readAllBytes(intent);
            Files.write(
                    caseRoot.resolve(
                            partial ? "partial-original.intent" : "tamper-original.intent"),
                    original,
                    StandardOpenOption.CREATE_NEW);
            final byte[] bad = partial ? bytes("PARTIAL\n") : original.clone();
            if (!partial) {
                bad[bad.length - 2] = bad[bad.length - 2] == '0' ? (byte) '1' : (byte) '0';
            }
            Files.write(intent, bad, StandardOpenOption.TRUNCATE_EXISTING);
            final var before = tree(target);
            refuses(
                    "START_INTENT_CORRUPT",
                    () -> {
                        try (var ledger = DevLocalLedger.open(target)) {
                            ledger.runs();
                        }
                    });
            assertEquals(before, tree(target));
            assertEquals(0, count(target.resolve("journal")));
        }
    }

    @Test
    void copiedForeignWorkspaceIntentIsRefused() throws Exception {
        final Path foreign = caseRoot.resolve("foreign");
        assertEquals(70, child("start", foreign, "INTENT_DURABLE", false));
        try (var ledger = DevLocalLedger.create(workspace)) {
            assertEquals(List.of(), ledger.runs());
        }
        Files.createDirectory(workspace.resolve("start-intents"));
        Files.copy(
                foreign.resolve("start-intents/run1.intent"),
                workspace.resolve("start-intents/run1.intent"));
        final var before = tree(workspace);
        refuses(
                "START_INTENT_WORKSPACE_CONFLICT",
                () -> {
                    try (var ledger = DevLocalLedger.open(workspace)) {
                        ledger.runs();
                    }
                });
        assertEquals(before, tree(workspace));
    }

    @Test
    void staleSequenceAndAppliedInputMismatchRefuseEvenWithValidIntentChecksum() throws Exception {
        assertEquals(70, child("start", workspace, "INTENT_DURABLE", false));
        rewriteIntent(
                workspace.resolve("start-intents/run1.intent"), 5, "2", "sequence-original.intent");
        refuses(
                "START_INTENT_JOURNAL_CONFLICT",
                () -> {
                    try (var ledger = DevLocalLedger.open(workspace)) {
                        ledger.runs();
                    }
                });
        final Path applied = caseRoot.resolve("applied");
        assertEquals(70, child("start", applied, "AFTER_START", false));
        rewriteIntent(
                applied.resolve("start-intents/run1.intent"),
                4,
                DevLocalLedger.sha256(bytes("changed")),
                "applied-original.intent");
        final var before = tree(applied);
        refuses(
                "START_INTENT_JOURNAL_CONFLICT",
                () -> {
                    try (var ledger = DevLocalLedger.open(applied)) {
                        ledger.runs();
                    }
                });
        assertEquals(before, tree(applied));
    }

    @Test
    void multiplePendingIntentsAndCaseCollisionRefuse() throws Exception {
        assertEquals(70, child("start", workspace, "INTENT_DURABLE", false));
        final String[] fields =
                Files.readString(workspace.resolve("start-intents/run1.intent"))
                        .split("\n")[0]
                        .split("\t");
        fields[3] = "run2";
        Files.write(
                workspace.resolve("start-intents/run2.intent"),
                intentBytes(fields),
                StandardOpenOption.CREATE_NEW);
        refuses(
                "START_INTENT_PENDING_CONFLICT",
                () -> {
                    try (var ledger = DevLocalLedger.open(workspace)) {
                        ledger.runs();
                    }
                });
        final Path alias = caseRoot.resolve("case-alias");
        assertEquals(70, child("start", alias, "INTENT_DURABLE", false));
        Files.createDirectory(alias.resolve("runs/RUN1"));
        final var before = tree(alias);
        try (var ledger = DevLocalLedger.open(alias)) {
            refuses("EXECUTION_CASE_CONFLICT", () -> ledger.reconcileStart("run1", INPUT));
        }
        assertEquals(before, tree(alias));
    }

    @Test
    void intentCountAndByteCapsRefuseBeforeInterpretation() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            assertEquals(List.of(), ledger.runs());
        }
        Files.createDirectory(workspace.resolve("start-intents"));
        for (int i = 0; i < 1001; i++) {
            Files.write(
                    workspace.resolve("start-intents/i" + i + ".intent"),
                    new byte[0],
                    StandardOpenOption.CREATE_NEW);
        }
        refuses(
                "START_INTENT_LIMIT",
                () -> {
                    try (var ledger = DevLocalLedger.open(workspace)) {
                        ledger.runs();
                    }
                });
        final Path oversized = caseRoot.resolve("oversized");
        assertEquals(70, child("start", oversized, "INTENT_DURABLE", false));
        final Path intent = oversized.resolve("start-intents/run1.intent");
        Files.write(
                caseRoot.resolve("oversized-original.intent"),
                Files.readAllBytes(intent),
                StandardOpenOption.CREATE_NEW);
        Files.write(intent, new byte[513], StandardOpenOption.TRUNCATE_EXISTING);
        refuses(
                "READ_BYTES_LIMIT",
                () -> {
                    try (var ledger = DevLocalLedger.open(oversized)) {
                        ledger.runs();
                    }
                });
        assertEquals(0, count(oversized.resolve("journal")));
    }

    @Test
    void concurrentWriterUsesRealOsLockAndReleaseIsKnown() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            assertEquals(0, child("locked", workspace, null, false));
            ledger.start("run1", INPUT);
        }
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("run1").state());
        }
    }

    @Test
    void ownJunctionMaterialRefusesWithoutFollowingOrDeletingIt() throws Exception {
        assertEquals(70, child("start", workspace, "INTENT_DURABLE", false));
        final Path outside = Files.createDirectory(caseRoot.resolve("foreign-material"));
        Files.writeString(
                outside.resolve("keep.txt"),
                "PRESERVE_OUTSIDE_TEST_MATERIAL",
                StandardOpenOption.CREATE_NEW);
        final var process =
                new ProcessBuilder(
                                "cmd.exe",
                                "/d",
                                "/c",
                                "mklink",
                                "/J",
                                "workspace\\runs\\run1",
                                "foreign-material")
                        .directory(caseRoot.toFile())
                        .start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            Files.write(
                    caseRoot.resolve("junction.stdout"),
                    process.getInputStream().readAllBytes(),
                    StandardOpenOption.CREATE_NEW);
            Files.write(
                    caseRoot.resolve("junction.stderr"),
                    process.getErrorStream().readAllBytes(),
                    StandardOpenOption.CREATE_NEW);
            assertEquals(0, process.exitValue(), "OWN_JUNCTION_CREATE_EXISTING_RIGHTS");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertThrows(IOException.class, () -> ledger.reconcileStart("run1", INPUT));
            assertEquals(0, count(workspace.resolve("journal")));
        }
        assertEquals(
                "PRESERVE_OUTSIDE_TEST_MATERIAL", Files.readString(outside.resolve("keep.txt")));
        assertTrue(
                Files.exists(
                        workspace.resolve("runs/run1"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void arbitraryPartialJournalRemainsRefusedAndPreserved() throws Exception {
        assertEquals(70, child("start", workspace, "BEFORE_START", false));
        Files.writeString(
                workspace.resolve("journal/00000001.entry"),
                "PARTIAL_START_JOURNAL\n",
                StandardOpenOption.CREATE_NEW);
        final var before = tree(workspace);
        refuses(
                "JOURNAL_CORRUPT",
                () -> {
                    try (var ledger = DevLocalLedger.open(workspace)) {
                        ledger.runs();
                    }
                });
        assertEquals(before, tree(workspace));
    }

    private void rewriteIntent(
            final Path path, final int field, final String value, final String preserve)
            throws IOException {
        final byte[] old = Files.readAllBytes(path);
        Files.write(caseRoot.resolve(preserve), old, StandardOpenOption.CREATE_NEW);
        final String[] fields = new String(old, StandardCharsets.UTF_8).split("\n")[0].split("\t");
        fields[field] = value;
        Files.write(path, intentBytes(fields), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static byte[] intentBytes(final String[] fields) {
        final String body = String.join("\t", fields) + "\n";
        return bytes(body + DevLocalLedger.sha256(bytes(body)) + "\n");
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void refuses(final String code, final Action action) {
        assertEquals(code, assertThrows(DevLocalLedger.Failure.class, action::run).code());
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static long count(final Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }

    private static Map<String, String> tree(final Path root) throws IOException {
        final var result = new LinkedHashMap<String, String>();
        try (var files = Files.walk(root)) {
            for (final Path path : files.sorted().toList()) {
                if (path.getFileName().toString().equals("workspace.lock")) {
                    result.put(
                            root.relativize(path).toString(),
                            "OS_LOCK_FILE_SIZE_" + Files.size(path));
                    continue;
                }
                if (Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    result.put(
                            root.relativize(path).toString(),
                            DevLocalLedger.sha256(Files.readAllBytes(path)));
                } else if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    result.put(root.relativize(path).toString(), "DIRECTORY");
                }
            }
        }
        return result;
    }

    private int child(
            final String action, final Path target, final String crashPoint, final boolean legacy)
            throws Exception {
        final ExternalFixture fixtures;
        if (crashPoint != null || legacy) {
            fixtures =
                    externalFixture(
                            System.getProperty("bank.start.external-fixtures.enabled"),
                            System.getProperty("bank.start.crash.agent"),
                            System.getProperty("bank.start.crash.asm"),
                            System.getProperty("bank.start.legacy.classes"));
            assumeTrue(
                    fixtures != null,
                    "BANK_START_EXTERNAL_FIXTURES_OPT_IN_REQUIRED: "
                            + "bank.start.external-fixtures.enabled=true and pinned "
                            + "bank.start.crash.agent/asm/legacy.classes (legacy JAR)");
        } else {
            fixtures = null;
        }
        final var args = new java.util.ArrayList<String>();
        args.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        args.add("-Xms16m");
        args.add("-Xmx128m");
        if (crashPoint != null) {
            args.add("-javaagent:" + fixtures.agent() + "=" + crashPoint);
        }
        args.add("-cp");
        args.add(
                (legacy ? fixtures.legacy() + java.io.File.pathSeparator : "")
                        + System.getProperty("java.class.path")
                        + (crashPoint != null ? java.io.File.pathSeparator + fixtures.asm() : ""));
        args.add(Child.class.getName());
        args.add(action);
        args.add(target.toString());
        final var process = new ProcessBuilder(args).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "OWN_CHILD_DEADLINE");
            final byte[] output = process.getInputStream().readAllBytes();
            final byte[] error = process.getErrorStream().readAllBytes();
            final String name =
                    action
                            + "-"
                            + (crashPoint == null ? "normal" : crashPoint)
                            + "-"
                            + UUID.randomUUID();
            Files.write(caseRoot.resolve(name + ".stdout"), output, StandardOpenOption.CREATE_NEW);
            Files.write(caseRoot.resolve(name + ".stderr"), error, StandardOpenOption.CREATE_NEW);
            if (process.exitValue() != 0 && process.exitValue() != 70) {
                throw new AssertionError(
                        "OWN_CHILD_FAILED: " + new String(error, StandardCharsets.UTF_8));
            }
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "OWN_CHILD_CLOSE_UNKNOWN");
            }
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
    }

    private static ExternalFixture externalFixture(
            final String enabled, final String agent, final String asm, final String legacy)
            throws IOException {
        if (enabled == null || enabled.equals("false")) {
            return null;
        }
        if (!enabled.equals("true")) {
            throw new IOException("BANK_START_FIXTURE_OPT_IN_INVALID");
        }
        if (agent == null
                || agent.isBlank()
                || asm == null
                || asm.isBlank()
                || legacy == null
                || legacy.isBlank()) {
            throw new IOException("BANK_START_FIXTURE_INCOMPLETE");
        }
        return new ExternalFixture(
                pinnedFixture(
                        agent, "93bf2d8f253e1ecd83c577ae33f41a7d50510e95eb869e4a02067bf4c0be97a3"),
                pinnedFixture(
                        asm, "8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281"),
                pinnedFixture(
                        legacy,
                        "f94457342a8f64166968fe48422ae244769ac019c2a47821bf96b9def567a1a8"));
    }

    private static Path pinnedFixture(final String value, final String expected)
            throws IOException {
        final Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().equals(path)
                || Files.size(path) > 1_048_576) {
            throw new IOException("BANK_START_FIXTURE_PATH_INVALID");
        }
        if (!DevLocalLedger.sha256(Files.readAllBytes(path)).equals(expected)) {
            throw new IOException("BANK_START_FIXTURE_PIN_MISMATCH");
        }
        return path;
    }

    /** Same package and actual product component in a fresh own JVM; no shared helper collision. */
    public static final class Child {
        private Child() {}

        public static void main(final String[] args) throws Exception {
            final Path target = Path.of(args[1]);
            if (args[0].equals("start")) {
                try (var ledger = DevLocalLedger.create(target)) {
                    ledger.start("run1", INPUT);
                }
            } else if (args[0].equals("legacy")) {
                try (var ledger = DevLocalLedger.create(target)) {
                    ledger.start("legacy1", INPUT);
                    ledger.event("legacy1", "LEGACY_EVENT", "LOCAL_ONLY");
                    ledger.finish("legacy1", DevLocalLedger.State.STOPPED, "LEGACY_STOP");
                }
            } else if (args[0].equals("locked")) {
                try (var ledger = DevLocalLedger.open(target)) {
                    ledger.runs();
                    throw new AssertionError("OS_LOCK_WAS_NOT_HELD");
                } catch (final DevLocalLedger.Failure failure) {
                    if (!failure.code().equals("WORKSPACE_LOCKED")) {
                        throw failure;
                    }
                    System.out.println("REAL_OS_LOCK_REFUSED_SECOND_WRITER");
                }
            } else {
                try (var ledger = DevLocalLedger.open(target)) {
                    final var first = ledger.reconcileStart("run1", INPUT);
                    if (first.state() != DevLocalLedger.State.STARTED) {
                        throw new AssertionError("NO_IMPLICIT_COMPLETION");
                    }
                    final var before = tree(target);
                    if (!first.equals(ledger.reconcileStart("run1", INPUT))
                            || !before.equals(tree(target))
                            || count(target.resolve("journal")) != 1) {
                        throw new AssertionError("DUPLICATE_START_OR_MUTATION");
                    }
                    System.out.println("RECONCILED_STARTED_ONCE_NEW_JVM_OS_LOCK");
                }
            }
        }
    }
}
