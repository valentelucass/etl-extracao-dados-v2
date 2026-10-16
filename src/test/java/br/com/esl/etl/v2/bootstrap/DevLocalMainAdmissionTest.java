package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevLocalMainAdmissionTest {
    private static final ObjectMapper JSON = DevLocalMainJson.mapper();
    @TempDir Path temporary;

    @Test
    void stoppedReadbackCannotReplayACompletedResultOrMutateDurableEvidence() throws Exception {
        final var workspace = temporary.resolve("workspace");
        assertEquals(0, invoke("init", workspace, null).exit());
        try (final var ledger = DevLocalLedger.open(workspace)) {
            ledger.start(
                    "interrupted",
                    DevLocalLedger.sha256("synthetic".getBytes(StandardCharsets.UTF_8)));
            final var result =
                    JSON.createObjectNode()
                            .put("synthetic", true)
                            .put("identityScope", "SYNTHETIC_LOCAL_ONLY")
                            .put("status", "COMPLETED")
                            .put("reasonCode", "LOCAL_COMPUTATION_COMPLETED");
            ledger.output("interrupted", "result.json", JSON.writeValueAsBytes(result));
            ledger.recover("interrupted", "LOCAL_STOP_REQUESTED");
        }
        final var before = pins(workspace);
        final var replay = invoke("replay", workspace, "interrupted");
        assertEquals(3, replay.exit());
        assertEquals("REFUSED", replay.receipt().path("status").asText());
        assertEquals(
                "DEV_LOCAL_REPLAY_TERMINAL_MISMATCH", replay.receipt().path("reasonCode").asText());
        assertEquals("STOPPED", replay.receipt().path("ledgerState").asText());
        assertTrue(replay.receipt().path("readbackVerified").asBoolean());
        assertTrue(replay.receipt().path("shutdownComplete").asBoolean());
        assertEquals(before, pins(workspace));
        try (final var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.STOPPED, ledger.readback("interrupted").state());
            assertEquals(
                    "COMPLETED",
                    JSON.readTree(ledger.readOutput("interrupted", "result.json"))
                            .path("status")
                            .asText());
        }
    }

    @Test
    void deadlineDuringOwnCloseRefusesSuccessAndRetainsKnownCompletedReadback() throws Exception {
        final var workspace = temporary.resolve("closed-deadline");
        assertEquals(0, invoke("init", workspace, null).exit());
        final var checks = new AtomicInteger();
        final LongSupplier deadline =
                () -> {
                    final int check = checks.incrementAndGet();
                    if (check == 4) {
                        assertDoesNotThrow(
                                () -> {
                                    try (final var ledger = DevLocalLedger.open(workspace)) {
                                        assertEquals(
                                                DevLocalLedger.State.COMPLETED,
                                                ledger.readback("known-run").state());
                                        assertEquals(
                                                "COMPLETED",
                                                JSON.readTree(
                                                                ledger.readOutput(
                                                                        "known-run", "result.json"))
                                                        .path("status")
                                                        .asText());
                                    }
                                });
                        return 30_000_000_000L;
                    }
                    return 0L;
                };
        final var reply = invoke("run", workspace, "known-run", deadline);
        assertEquals(4, checks.get());
        assertEquals(3, reply.exit());
        assertEquals("REFUSED", reply.receipt().path("status").asText());
        assertEquals("DEV_LOCAL_CLI_DEADLINE", reply.receipt().path("reasonCode").asText());
        assertEquals("COMPLETED", reply.receipt().path("ledgerState").asText());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        assertTrue(reply.receipt().path("readbackVerified").asBoolean());
        final var before = pins(workspace);
        final var replay = invoke("replay", workspace, "known-run");
        assertEquals(0, replay.exit());
        assertEquals(reply.receipt().path("result"), replay.receipt().path("result"));
        assertEquals(before, pins(workspace));
    }

    @Test
    void deadlineAfterPersistenceBeforeCloseRetainsOutcomeWithoutCompensation() throws Exception {
        final var workspace = temporary.resolve("persisted-deadline");
        invoke("init", workspace, null);
        final var checks = new AtomicInteger();
        final var reply =
                invoke(
                        "run",
                        workspace,
                        "durable-run",
                        () -> checks.incrementAndGet() >= 3 ? 30_000_000_000L : 0L);
        assertEquals(3, checks.get());
        assertEquals(3, reply.exit());
        assertEquals("COMPLETED", reply.receipt().path("ledgerState").asText());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        try (final var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(DevLocalLedger.State.COMPLETED, ledger.readback("durable-run").state());
            assertEquals(1, ledger.readback("durable-run").outputs().size());
        }
    }

    @Test
    void deadlineBeforeExecutionDoesNotStartAnotherRun() throws Exception {
        final var workspace = temporary.resolve("entry-deadline");
        invoke("init", workspace, null);
        final var before = pins(workspace);
        final var checks = new AtomicInteger();
        final var reply =
                invoke(
                        "run",
                        workspace,
                        "never-started",
                        () -> checks.incrementAndGet() >= 2 ? 30_000_000_000L : 0L);
        assertEquals(3, reply.exit());
        assertEquals("DEV_LOCAL_CLI_DEADLINE", reply.receipt().path("reasonCode").asText());
        assertTrue(reply.receipt().path("shutdownComplete").asBoolean());
        assertEquals(before, pins(workspace));
        try (final var ledger = DevLocalLedger.open(workspace)) {
            assertEquals(1, ledger.runs().size());
        }
    }

    private Reply invoke(final String command, final Path workspace, final String execution)
            throws Exception {
        return invoke(command, workspace, execution, System::nanoTime);
    }

    private Reply invoke(
            final String command,
            final Path workspace,
            final String execution,
            final LongSupplier monotonic)
            throws Exception {
        final var args =
                new java.util.ArrayList<>(
                        java.util.List.of(
                                command, "--synthetic", "--workspace", workspace.toString()));
        if (execution != null) {
            args.addAll(java.util.List.of("--execution", execution));
        }
        final var bytes = new ByteArrayOutputStream();
        final int exit;
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exit = DevLocalMain.run(args.toArray(String[]::new), output, monotonic);
        }
        return new Reply(exit, JSON.readTree(bytes.toByteArray()));
    }

    private static Map<String, String> pins(final Path workspace) throws Exception {
        final var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(workspace)) {
            for (final Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(
                        workspace.relativize(path).toString(),
                        DevLocalLedger.sha256(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private record Reply(int exit, JsonNode receipt) {}
}
