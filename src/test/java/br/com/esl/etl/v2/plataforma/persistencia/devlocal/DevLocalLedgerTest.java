package br.com.esl.etl.v2.plataforma.persistencia.devlocal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** DEV-FS-001 regression evidence is retained, including corrupt and interrupted workspaces. */
class DevLocalLedgerTest {
    private static final String INPUT = DevLocalLedger.sha256(bytes("synthetic-input"));
    private Path caseRoot;
    private Path workspace;

    @BeforeEach
    void newCase(final TestInfo test) throws IOException {
        final Path parent =
                Path.of(
                                System.getProperty(
                                        "dev.local.tests.root",
                                        "target/dev-local-b77c5029cfbd/banco/permanent-fixtures"))
                        .toAbsolutePath();
        Files.createDirectories(parent);
        final String method = test.getTestMethod().orElseThrow().getName();
        caseRoot = Files.createDirectory(parent.resolve(method + "-" + UUID.randomUUID()));
        workspace = caseRoot.resolve("workspace");
    }

    @Test
    void durableRoundTripSeparatesJournalEventsOutputsAndReasons() throws Exception {
        final String identity;
        final DevLocalLedger.Run expected;
        try (var ledger = DevLocalLedger.create(workspace)) {
            identity = ledger.workspaceId();
            assertEquals(workspace, ledger.workspace());
            assertEquals(DevLocalLedger.State.STARTED, ledger.start("run1", INPUT).state());
            ledger.event("run1", "ENTITY_OBSERVED", "SYNTHETIC açã\nline2");
            final var output = ledger.output("run1", "result.json", bytes("{\"synthetic\":true}"));
            assertEquals(DevLocalLedger.sha256(bytes("{\"synthetic\":true}")), output.sha256());
            expected = ledger.finish("run1", DevLocalLedger.State.COMPLETED, "LOCAL_COMPLETE");
            assertEquals("LOCAL_COMPLETE", expected.reasonCode());
            assertEquals(List.of(expected), ledger.runs());
            assertTrue(ledger.orphanFiles("run1").isEmpty());
        }
        assertTrue(
                Files.readString(workspace.resolve("identity.txt"))
                        .startsWith("DEV_LOCAL_SYNTHETIC_V1\n"));
        assertEquals("SYNTHETIC açã\nline2", Files.readString(runPath("events/00000001.txt")));
        assertEquals("LOCAL_COMPLETE\n", Files.readString(runPath("reasons/terminal.txt")));
        assertEquals(4, fileCount(workspace.resolve("journal")));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(identity, ledger.workspaceId());
            assertEquals(expected, ledger.readback("run1"));
            assertEquals("SYNTHETIC açã\nline2", ledger.readEvent("run1", 1));
            assertArrayEquals(
                    bytes("{\"synthetic\":true}"), ledger.readOutput("run1", "result.json"));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DevLocalLedger.State.class,
            names = {"COMPLETED", "CANCELLED", "FAILED", "STOPPED"})
    void everyTerminalStateReplaysWithoutAWriteOrPurge(final DevLocalLedger.State state)
            throws Exception {
        final DevLocalLedger.Run expected;
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.output("run1", "retained.txt", bytes("SYNTHETIC_PARTIAL"));
            expected = ledger.finish("run1", state, "EXPECTED_REASON");
        }
        final String before = treeDigest(workspace);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(expected, ledger.start("run1", INPUT));
            assertEquals(expected, ledger.finish("run1", state, "EXPECTED_REASON"));
            refuses("RUN_TERMINAL", () -> ledger.event("run1", "LATE", "synthetic"));
            refuses("RUN_TERMINAL", () -> ledger.output("run1", "late.txt", new byte[0]));
            refuses("TERMINAL_CONFLICT", () -> ledger.finish("run1", state, "DIFFERENT_REASON"));
            refuses("RECOVERY_STATE", () -> ledger.recover("run1", "RECOVER"));
        }
        assertEquals(before, treeDigest(workspace));
        assertArrayEquals(
                bytes("SYNTHETIC_PARTIAL"), Files.readAllBytes(runPath("outputs/retained.txt")));
    }

    @Test
    void identityConflictsAndIncompleteReplayRefuseBeforeWriting() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final String before = treeDigest(workspace);
            refuses("RECOVERY_REQUIRED", () -> ledger.start("run1", INPUT));
            refuses(
                    "EXECUTION_INPUT_CONFLICT",
                    () -> ledger.start("run1", DevLocalLedger.sha256(bytes("different"))));
            refuses("EXECUTION_CASE_CONFLICT", () -> ledger.start("RUN1", INPUT));
            refuses(
                    "TERMINAL_STATE_REQUIRED",
                    () -> ledger.finish("run1", DevLocalLedger.State.STARTED, "INVALID"));
            refuses("TERMINAL_STATE_REQUIRED", () -> ledger.finish("run1", null, "INVALID"));
            assertEquals(before, treeDigest(workspace));
        }
    }

    @Test
    void outputNeverOverwritesIncludingSameBytesAndCaseAliases() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.output("run1", "result.txt", bytes("original"));
            final String before = treeDigest(workspace);
            refuses("OUTPUT_EXISTS", () -> ledger.output("run1", "result.txt", bytes("original")));
            refuses("OUTPUT_EXISTS", () -> ledger.output("run1", "result.txt", bytes("different")));
            refuses("OUTPUT_EXISTS", () -> ledger.output("run1", "RESULT.TXT", bytes("different")));
            assertArrayEquals(bytes("original"), ledger.readOutput("run1", "RESULT.TXT"));
            assertEquals(before, treeDigest(workspace));
        }
    }

    @Test
    void existingWorkspaceIsNeverReinitializedEvenIfEmptyOrNonSynthetic() throws Exception {
        Files.createDirectory(workspace);
        refuses("WORKSPACE_EXISTS", () -> DevLocalLedger.create(workspace));
        assertEquals(0, fileCount(workspace));
        Files.writeString(
                workspace.resolve("keep.txt"), "USER_EVIDENCE", StandardOpenOption.CREATE_NEW);
        assertThrows(IOException.class, () -> DevLocalLedger.open(workspace));
        assertEquals("USER_EVIDENCE", Files.readString(workspace.resolve("keep.txt")));
    }

    @Test
    void markerAndParentAreRequiredAndDistinctWorkspacesHaveDistinctIdentity() throws Exception {
        assertThrows(
                IOException.class,
                () -> DevLocalLedger.create(caseRoot.resolve("missing/workspace")));
        refuses("WORKSPACE_REQUIRED", () -> DevLocalLedger.create(null));
        final String first;
        try (var ledger = DevLocalLedger.create(workspace)) {
            first = ledger.workspaceId();
        }
        try (var ledger = DevLocalLedger.create(caseRoot.resolve("another"))) {
            assertNotEquals(first, ledger.workspaceId());
        }
        Files.writeString(workspace.resolve("identity.txt"), "NON_SYNTHETIC\n");
        refuses("WORKSPACE_IDENTITY", () -> DevLocalLedger.open(workspace));
    }

    @Test
    void invalidNamesCannotEscapeOrAddressWindowsDevicesOrAlternateStreams() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final String before = treeDigest(workspace);
            for (final String invalid :
                    List.of(
                            "..",
                            ".",
                            "../escape",
                            "a/b",
                            "a\\b",
                            "x:stream",
                            "NUL",
                            "con.txt",
                            "LPT1",
                            "COM9.x",
                            "trailing.",
                            " space",
                            "a\nline",
                            "a".repeat(81))) {
                refuses("NAME_INVALID", () -> ledger.output("run1", invalid, bytes("synthetic")));
                refuses("NAME_INVALID", () -> ledger.start(invalid, INPUT));
            }
            refuses("NAME_INVALID", () -> ledger.output("run1", null, new byte[0]));
            assertEquals(before, treeDigest(workspace));
            assertFalse(Files.exists(caseRoot.resolve("escape")));
        }
    }

    @Test
    void payloadHashCodeAndSizeLimitsRefuseWithoutArtifacts() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final String before = treeDigest(workspace);
            refuses("HASH_INVALID", () -> ledger.start("run2", "invalid"));
            refuses("HASH_INVALID", () -> ledger.start("run2", INPUT.toUpperCase()));
            refuses("CODE_INVALID", () -> ledger.event("run1", "path/TYPE", "synthetic"));
            refuses(
                    "CODE_INVALID",
                    () -> ledger.finish("run1", DevLocalLedger.State.FAILED, "reason\n"));
            refuses("PAYLOAD_REQUIRED", () -> ledger.event("run1", "VALID", null));
            refuses("PAYLOAD_UTF8", () -> ledger.event("run1", "VALID", "\uD800"));
            refuses(
                    "EVENT_BYTES_LIMIT",
                    () -> ledger.event("run1", "VALID", "x".repeat(1_048_577)));
            refuses(
                    "OUTPUT_BYTES_LIMIT",
                    () -> ledger.output("run1", "large.txt", new byte[4_194_305]));
            refuses("OUTPUT_BYTES_LIMIT", () -> ledger.output("run1", "null.txt", null));
            assertEquals(before, treeDigest(workspace));
            ledger.event("run1", "EMPTY", "");
            ledger.output("run1", "empty.txt", new byte[0]);
            assertEquals("", ledger.readEvent("run1", 1));
        }
    }

    @Test
    void missingRunAndArtifactReferencesAreExplicitErrors() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            refuses("RUN_NOT_FOUND", () -> ledger.readback("missing"));
            ledger.start("run1", INPUT);
            refuses("EVENT_NOT_FOUND", () -> ledger.readEvent("run1", 0));
            refuses("EVENT_NOT_FOUND", () -> ledger.readEvent("run1", 1));
            refuses("OUTPUT_NOT_FOUND", () -> ledger.readOutput("run1", "missing.txt"));
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("run1").state());
        }
    }

    @Test
    void snapshotsAndOutputBuffersAreDefensive() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final byte[] original = bytes("synthetic");
            ledger.output("run1", "out.txt", original);
            original[0] = 0;
            final byte[] read = ledger.readOutput("run1", "out.txt");
            read[0] = 0;
            assertArrayEquals(bytes("synthetic"), ledger.readOutput("run1", "out.txt"));
            final var snapshot = ledger.readback("run1");
            assertThrows(UnsupportedOperationException.class, () -> snapshot.outputs().clear());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.events().clear());
            assertThrows(UnsupportedOperationException.class, () -> ledger.runs().clear());
        }
    }

    @Test
    void sameJvmAndActualSecondJvmCannotAcquireOwnedWorkspace() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final String before = treeDigest(workspace);
            refuses("WORKSPACE_LOCKED", () -> DevLocalLedger.open(workspace));
            assertEquals(0, process("lock"));
            assertTrue(
                    Files.readString(caseRoot.resolve("lock.stdout.log"))
                            .contains("SECOND_PROCESS_LOCK_REFUSED"));
            assertEquals(before, treeDigest(workspace));
        }
        try (var reopened = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, reopened.readback("run1").state());
        }
    }

    @Test
    void competingThreadsHaveOneOutputWinnerAndOneConflict() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final var executor = Executors.newFixedThreadPool(2);
            try {
                final Callable<String> write =
                        () -> {
                            try {
                                ledger.output("run1", "same.txt", bytes("synthetic"));
                                return "WRITTEN";
                            } catch (final DevLocalLedger.Failure failure) {
                                return failure.code();
                            }
                        };
                final var first = executor.submit(write);
                final var second = executor.submit(write);
                assertEquals(
                        List.of("OUTPUT_EXISTS", "WRITTEN"),
                        java.util.stream.Stream.of(
                                        first.get(10, TimeUnit.SECONDS),
                                        second.get(10, TimeUnit.SECONDS))
                                .sorted()
                                .toList());
                assertEquals(1, ledger.readback("run1").outputs().size());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void abruptJvmTerminationRecoversExplicitlyAsStoppedAndRetainsEvidence() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            assertTrue(ledger.runs().isEmpty());
        }
        assertEquals(23, process("crash"));
        final Path retained = workspace.resolve("runs/crash-run/outputs/result.txt");
        final byte[] before = Files.readAllBytes(retained);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("crash-run").state());
            refuses(
                    "RECOVERY_REQUIRED",
                    () -> ledger.start("crash-run", DevLocalLedger.sha256(bytes("fixture"))));
            assertEquals(
                    DevLocalLedger.State.STOPPED,
                    ledger.recover("crash-run", "PROCESS_INTERRUPTED").state());
            assertEquals("SYNTHETIC_CRASH_EVIDENCE", ledger.readEvent("crash-run", 1));
        }
        assertArrayEquals(before, Files.readAllBytes(retained));
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(
                    DevLocalLedger.State.STOPPED,
                    ledger.start("crash-run", DevLocalLedger.sha256(bytes("fixture"))).state());
        }
    }

    @Test
    void orphansBlockCompletionAndRecoveryNeverDeletesThem() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.output("run1", "retained.txt", bytes("journaled"));
            final Path orphan = runPath("outputs/interrupted.txt");
            Files.writeString(orphan, "SYNTHETIC_UNJOURNALED", StandardOpenOption.CREATE_NEW);
            assertEquals(List.of("outputs/interrupted.txt"), ledger.orphanFiles("run1"));
            refuses(
                    "ORPHAN_ARTIFACTS_REQUIRE_RECOVERY",
                    () -> ledger.finish("run1", DevLocalLedger.State.COMPLETED, "COMPLETE"));
            assertEquals(
                    DevLocalLedger.State.STOPPED,
                    ledger.recover("run1", "UNJOURNALED_ARTIFACT").state());
            assertEquals("SYNTHETIC_UNJOURNALED", Files.readString(orphan));
            assertArrayEquals(bytes("journaled"), ledger.readOutput("run1", "retained.txt"));
        }
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(List.of("outputs/interrupted.txt"), ledger.orphanFiles("run1"));
            assertEquals(DevLocalLedger.State.STOPPED, ledger.readback("run1").state());
        }
    }

    @Test
    void orphanTerminalReasonIsRetainedAndCannotBeOverwrittenByRecovery() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            Files.writeString(
                    runPath("reasons/terminal.txt"),
                    "INCOMPLETE_TERMINAL\n",
                    StandardOpenOption.CREATE_NEW);
            final String before = treeDigest(workspace);
            refuses("ARTIFACT_EXISTS", () -> ledger.recover("run1", "RECOVER"));
            assertEquals(before, treeDigest(workspace));
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("run1").state());
        }
    }

    @Test
    void orphanRunAndOutputDirectoryCollisionsRefuseWithoutOverwrite() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            Files.createDirectory(workspace.resolve("runs/orphan"));
            refuses("ORPHAN_RUN_CONFLICT", () -> ledger.start("orphan", INPUT));
            ledger.start("run1", INPUT);
            Files.createDirectory(runPath("outputs/collision.txt"));
            refuses(
                    "ARTIFACT_EXISTS",
                    () -> ledger.output("run1", "collision.txt", bytes("synthetic")));
            assertTrue(Files.isDirectory(runPath("outputs/collision.txt")));
            assertTrue(ledger.readback("run1").outputs().isEmpty());
        }
    }

    @Test
    void changedOutputAndEventHashFailClosedWithoutPurging() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.event("run1", "TEST", "original");
            ledger.output("run1", "out.txt", bytes("original"));
        }
        Files.writeString(runPath("outputs/out.txt"), "changed!");
        final String before = treeDigest(workspace);
        refuses("ARTIFACT_HASH_CONFLICT", () -> DevLocalLedger.open(workspace));
        assertEquals(before, treeDigest(workspace));
        Files.writeString(runPath("outputs/out.txt"), "original");
        Files.writeString(runPath("events/00000001.txt"), "changed!");
        refuses("ARTIFACT_HASH_CONFLICT", () -> DevLocalLedger.open(workspace));
        assertEquals("changed!", Files.readString(runPath("events/00000001.txt")));
    }

    @Test
    void missingArtifactDoesNotBecomeEmptyOrSuccessful() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.output("run1", "out.txt", bytes("original"));
        }
        Files.move(runPath("outputs/out.txt"), caseRoot.resolve("retained-out.txt"));
        assertThrows(IOException.class, () -> DevLocalLedger.open(workspace));
        assertArrayEquals(
                bytes("original"), Files.readAllBytes(caseRoot.resolve("retained-out.txt")));
        assertEquals(2, fileCount(workspace.resolve("journal")));
    }

    @Test
    void corruptJournalRefusesAndOpenFailureReleasesLock() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
        }
        final Path entry = workspace.resolve("journal/00000001.entry");
        final byte[] original = Files.readAllBytes(entry);
        Files.writeString(entry, "CORRUPT\n");
        refuses("JOURNAL_CORRUPT", () -> DevLocalLedger.open(workspace));
        Files.write(entry, original);
        try (var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, ledger.readback("run1").state());
        }
    }

    @Test
    void journalGapAndChecksummedBrokenChainAreRefused() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            ledger.event("run1", "TEST", "synthetic");
        }
        final Path entry = workspace.resolve("journal/00000002.entry");
        Files.move(entry, workspace.resolve("journal/00000003.entry"));
        refuses("JOURNAL_SEQUENCE", () -> DevLocalLedger.open(workspace));
        Files.move(workspace.resolve("journal/00000003.entry"), entry);
        final String[] fields = Files.readString(entry).split("\n")[0].split("\t", -1);
        fields[1] = "0".repeat(64);
        final String body = String.join("\t", fields) + "\n";
        Files.writeString(entry, body + DevLocalLedger.sha256(bytes(body)) + "\n");
        refuses("JOURNAL_CHAIN", () -> DevLocalLedger.open(workspace));
    }

    @Test
    void oversizedJournalAndMalformedUtf8AreRejectedWithEvidenceRetained() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
        }
        final Path entry = workspace.resolve("journal/00000001.entry");
        Files.write(entry, new byte[4097]);
        refuses("READ_BYTES_LIMIT", () -> DevLocalLedger.open(workspace));
        assertEquals(4097, Files.size(entry));
        Files.write(entry, new byte[] {(byte) 0xC3, (byte) 0x28});
        refuses("UTF8_INVALID", () -> DevLocalLedger.open(workspace));
    }

    @Test
    void workspaceIdentityChangeFencesFurtherMutations() throws Exception {
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            Files.writeString(
                    workspace.resolve("identity.txt"),
                    "DEV_LOCAL_SYNTHETIC_V1\n" + UUID.randomUUID() + "\n");
            final String before = treeDigest(workspace);
            refuses(
                    "WORKSPACE_IDENTITY_CHANGED",
                    () -> ledger.output("run1", "late.txt", bytes("synthetic")));
            assertEquals(before, treeDigest(workspace));
        }
    }

    @Test
    void lostLockAndUncertainWriteFenceFurtherWork() throws Exception {
        final var ledger = DevLocalLedger.create(workspace);
        try {
            ledger.start("run1", INPUT);
            final var uncertain = DevLocalLedger.class.getDeclaredField("uncertain");
            uncertain.setAccessible(true);
            uncertain.setBoolean(ledger, true);
            refuses("WRITE_UNCERTAIN", () -> ledger.readback("run1"));
            uncertain.setBoolean(ledger, false);
            final var field = DevLocalLedger.class.getDeclaredField("lock");
            field.setAccessible(true);
            ((FileLock) field.get(ledger)).release();
            refuses(
                    "WORKSPACE_FENCE_LOST",
                    () -> ledger.output("run1", "late.txt", bytes("synthetic")));
        } finally {
            ledger.close();
        }
    }

    @Test
    void closeIsIdempotentAndAllIoAfterCloseRefuses() throws Exception {
        final var ledger = DevLocalLedger.create(workspace);
        ledger.start("run1", INPUT);
        ledger.close();
        ledger.close();
        refuses("CLOSED", () -> ledger.readback("run1"));
        refuses("CLOSED", () -> ledger.start("another", INPUT));
        refuses("CLOSED", () -> ledger.output("run1", "late.txt", new byte[0]));
        refuses("CLOSED", () -> ledger.finish("run1", DevLocalLedger.State.CANCELLED, "CLOSE"));
        try (var reopened = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, reopened.readback("run1").state());
        }
    }

    @Test
    void closeReleaseFailureStillClosesChannelAndPreservesOriginalEvidence() throws Exception {
        final var ledger = DevLocalLedger.create(workspace);
        ledger.start("run1", INPUT);
        final var channelField = DevLocalLedger.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        final var channel = (FileChannel) channelField.get(ledger);
        final var lockField = DevLocalLedger.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        final int[] releases = {0};
        final FileLock failingLock =
                new FileLock(channel, 0, Long.MAX_VALUE, false) {
                    @Override
                    public boolean isValid() {
                        return channel.isOpen();
                    }

                    @Override
                    public void release() throws IOException {
                        releases[0]++;
                        throw new IOException("PRIVATE_FAULT_MUST_NOT_ESCAPE");
                    }
                };
        lockField.set(ledger, failingLock);
        final String before = treeDigest(workspace);
        refuses("LOCK_CLOSE_UNCERTAIN", ledger::close);
        assertFalse(channel.isOpen());
        ledger.close();
        assertEquals(1, releases[0]);
        assertEquals(before, treeDigest(workspace));
        try (var reopened = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STARTED, reopened.readback("run1").state());
        }
    }

    @Test
    void boundedSnapshotConstructorRefusesOversizedMetadataBeforeCopy() {
        final var event = new DevLocalLedger.Event(1, "SYNTHETIC", INPUT, 0);
        final var tooMany = java.util.Collections.nCopies(100_001, event);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DevLocalLedger.Run(
                                "run1",
                                INPUT,
                                DevLocalLedger.State.STARTED,
                                "",
                                tooMany,
                                List.of()));
    }

    @Test
    void filesystemDirectoryLinkCannotRedirectWorkspaceOrOutputs() throws Exception {
        final Path actual = Files.createDirectory(caseRoot.resolve("actual"));
        final Path link = caseRoot.resolve("linked");
        directoryLink(link, actual);
        assertThrows(IOException.class, () -> DevLocalLedger.create(link.resolve("workspace")));
        assertEquals(0, fileCount(actual));
        try (var ledger = DevLocalLedger.create(workspace)) {
            ledger.start("run1", INPUT);
            final Path outputs = runPath("outputs");
            Files.move(outputs, runPath("outputs-retained"));
            directoryLink(outputs, actual);
            assertThrows(
                    IOException.class,
                    () -> ledger.output("run1", "escape.txt", bytes("synthetic")));
            assertEquals(0, fileCount(actual));
        }
    }

    private void directoryLink(final Path link, final Path actual) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createSymbolicLink(link, actual);
            return;
        }
        final Path owner = caseRoot.toAbsolutePath().normalize();
        final Path ownedLink = link.toAbsolutePath().normalize();
        final Path ownedTarget = actual.toAbsolutePath().normalize();
        assertEquals(owner, caseRoot.toRealPath());
        assertTrue(ownedLink.startsWith(owner));
        assertTrue(ownedTarget.startsWith(owner));
        assertTrue(ownedLink.getParent().toRealPath().startsWith(owner));
        assertEquals(ownedTarget, ownedTarget.toRealPath());
        assertFalse(Files.exists(ownedLink, LinkOption.NOFOLLOW_LINKS));
        // Paths in the aggregate snapshot exceed cmd's legacy path limit.
        final var command =
                new ProcessBuilder(
                        "cmd.exe",
                        "/d",
                        "/c",
                        "mklink",
                        "/J",
                        "\\\\?\\" + ownedLink,
                        "\\\\?\\" + ownedTarget);
        final String log = "junction-" + UUID.randomUUID();
        command.redirectOutput(caseRoot.resolve(log + ".stdout.log").toFile());
        command.redirectError(caseRoot.resolve(log + ".stderr.log").toFile());
        final Process child = command.start();
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, child.exitValue());
            assertEquals(ownedTarget, ownedLink.toRealPath());
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }

    private int process(final String mode) throws Exception {
        final String java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString();
        final var builder =
                new ProcessBuilder(
                        java,
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        System.getProperty("java.class.path"),
                        DevLocalLedgerProcessFixture.class.getName(),
                        mode,
                        workspace.toString());
        builder.environment()
                .keySet()
                .removeIf(
                        key ->
                                !List.of("SYSTEMROOT", "WINDIR", "TEMP", "TMP", "PATH")
                                        .contains(key.toUpperCase(Locale.ROOT)));
        builder.redirectOutput(caseRoot.resolve(mode + ".stdout.log").toFile());
        builder.redirectError(caseRoot.resolve(mode + ".stderr.log").toFile());
        final Process child = builder.start();
        Files.writeString(
                caseRoot.resolve(mode + ".pid.txt"),
                Long.toString(child.pid()),
                StandardOpenOption.CREATE_NEW);
        try {
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "OWNED_FIXTURE_PROCESS_TIMEOUT");
            return child.exitValue();
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }

    private Path runPath(final String relative) {
        return workspace.resolve("runs/run1").resolve(relative);
    }

    private static void refuses(final String code, final Throwing action) {
        final var failure = assertThrows(DevLocalLedger.Failure.class, action::run);
        assertEquals(code, failure.code());
        assertEquals("DEV_LOCAL_" + code, failure.getMessage());
    }

    private static long fileCount(final Path parent) throws IOException {
        try (var files = Files.list(parent)) {
            return files.count();
        }
    }

    private static String treeDigest(final Path root) throws IOException {
        final var text = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.sorted().toList()) {
                text.append(root.relativize(path)).append('\n');
                if (Files.isRegularFile(path)) {
                    if (path.getFileName().toString().equals("workspace.lock")) {
                        // Windows enforces the live OS lock even for readAllBytes on an empty file.
                        assertEquals(0, Files.size(path));
                        text.append("OWNED_ZERO_BYTE_LOCK\n");
                    } else {
                        text.append(DevLocalLedger.sha256(Files.readAllBytes(path))).append('\n');
                    }
                }
            }
        }
        return DevLocalLedger.sha256(bytes(text.toString()));
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
