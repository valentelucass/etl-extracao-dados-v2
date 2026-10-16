package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.devlocal.DevLocalDataset;
import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Separate explicit synthetic file-based composition; never constructs a physical adapter. */
public final class DevLocalMain {
    private static final ObjectMapper JSON = DevLocalMainJson.mapper();
    private static final String INITIALIZATION = "workspace-init";

    private DevLocalMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out));
    }

    static int run(final String[] arguments, final PrintStream output) {
        return run(arguments, output, System::nanoTime);
    }

    static int run(
            final String[] arguments, final PrintStream output, final LongSupplier monotonic) {
        final long elapsedBegan = System.nanoTime();
        final long began = monotonic.getAsLong();
        final ObjectNode receipt = JSON.createObjectNode();
        receipt.put("synthetic", true)
                .put("identityScope", "SYNTHETIC_LOCAL_ONLY")
                .put("status", "REFUSED")
                .put("shutdownComplete", false)
                .put("sql", 0)
                .put("httpExternal", 0)
                .put("realSecrets", 0)
                .put("sqlTiming", "NOT_APPLICABLE")
                .put("physicalEvidence", false);
        int exit = 3;
        OwnedLedger ownership = null;
        try {
            final var request = DevLocalMainArguments.parse(arguments);
            receipt.put("command", request.command());
            final var supplied =
                    request.config() == null
                            ? null
                            : DevLocalMainConfiguration.read(request.config());
            final DevLocalLedger ledger =
                    "init".equals(request.command())
                            ? DevLocalLedger.create(request.workspace())
                            : DevLocalLedger.open(request.workspace());
            ownership = new OwnedLedger(ledger);
            try (var owned = ownership) {
                receipt.put("workspaceId", owned.ledger.workspaceId());
                if ("init".equals(request.command())) {
                    final var configuration =
                            supplied == null ? DevLocalMainConfiguration.sample() : supplied;
                    final var bytes = JSON.writeValueAsBytes(configuration.document());
                    ledger.start(INITIALIZATION, DevLocalLedger.sha256(bytes));
                    ledger.output(INITIALIZATION, "config.json", bytes);
                    ledger.finish(
                            INITIALIZATION, DevLocalLedger.State.COMPLETED, "LOCAL_INITIALIZED");
                    final var verified =
                            DevLocalMainConfiguration.parse(
                                    ledger.readOutput(INITIALIZATION, "config.json"));
                    if (!configuration.equals(verified)) {
                        throw new IOException("DEV_LOCAL_INIT_READBACK");
                    }
                    summarize(ledger, ledger.readback(INITIALIZATION), receipt);
                    receipt.put("status", "INITIALIZED")
                            .put("executionId", INITIALIZATION)
                            .put("readbackVerified", true);
                    receipt.set("configuration", verified.document());
                } else {
                    final var configuration =
                            supplied == null
                                    ? DevLocalMainConfiguration.parse(
                                            ledger.readOutput(INITIALIZATION, "config.json"))
                                    : supplied;
                    receipt.set("configuration", configuration.document());
                    if ("reconcile-start".equals(request.command())) {
                        receipt.put("configurationScope", "RECONCILIATION_CONTROL_ONLY");
                    }
                    checkpoint(monotonic, began, configuration);
                    execute(request, configuration, ledger, receipt);
                    checkpoint(monotonic, began, configuration);
                }
            }
            checkpoint(
                    monotonic,
                    began,
                    DevLocalMainConfiguration.parse(
                            JSON.writeValueAsBytes(receipt.path("configuration"))));
            exit = "FAILED".equals(receipt.path("status").asText()) ? 3 : 0;
        } catch (final IllegalArgumentException failure) {
            receipt.put("status", "REFUSED").put("reasonCode", code(failure));
            exit = 2;
        } catch (final IOException | RuntimeException failure) {
            receipt.put("status", "REFUSED").put("reasonCode", code(failure));
        }
        receipt.put("shutdownComplete", ownership != null && ownership.closed);
        receipt.put("elapsedNanos", System.nanoTime() - elapsedBegan)
                .put(
                        "heapUsedAtReceiptBytes",
                        ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())
                .put(
                        "heapPoolPeakSumBytes",
                        ManagementFactory.getMemoryPoolMXBeans().stream()
                                .filter(pool -> pool.getType() == MemoryType.HEAP)
                                .mapToLong(pool -> Math.max(0, pool.getPeakUsage().getUsed()))
                                .sum())
                .put(
                        "heapPeakMeaning",
                        "SUM_OF_SEPARATE_HEAP_POOL_PEAKS_NOT_RSS_OR_SIMULTANEOUS_PEAK")
                .put("jvmMaximumHeapBytes", Runtime.getRuntime().maxMemory())
                .put("processId", ProcessHandle.current().pid())
                .put("exitCode", exit);
        try {
            output.println(JSON.writeValueAsString(receipt));
        } catch (final IOException failure) {
            output.println("{\"synthetic\":true,\"status\":\"RECEIPT_FAILED\",\"exitCode\":3}");
            return 3;
        }
        return exit;
    }

    private static void execute(
            final DevLocalMainArguments request,
            final DevLocalMainConfiguration configuration,
            final DevLocalLedger ledger,
            final ObjectNode receipt)
            throws IOException {
        if ("reconcile-start".equals(request.command())) {
            receipt.put("reconciledStart", false);
            final boolean pending = ledger.pendingStartIds().contains(request.executionId());
            final var run = ledger.reconcileStart(request.executionId(), request.inputSha256());
            summarize(ledger, run, receipt);
            receipt.put("reconciledStart", true)
                    .put("pendingStartCompleted", pending)
                    .put("alreadyTerminal", run.state() != DevLocalLedger.State.STARTED);
            return;
        }
        if ("status".equals(request.command())) {
            final var pending = ledger.pendingStartIds();
            receipt.put("pendingStarts", pending.size())
                    .put("pendingStartHold", !pending.isEmpty());
            final var pendingIds = receipt.putArray("pendingStartIds");
            pending.forEach(pendingIds::add);
            final var runs = ledger.runs();
            receipt.put("runs", runs.size());
            if (request.executionId() != null && pending.contains(request.executionId())) {
                receipt.put("executionId", request.executionId())
                        .put("status", "START_PENDING")
                        .put("reasonCode", "DEV_LOCAL_START_RECONCILIATION_REQUIRED")
                        .put("pendingStartReadbackVerified", true);
                return;
            }
            final var run =
                    request.executionId() == null
                            ? runs.get(runs.size() - 1)
                            : ledger.readback(request.executionId());
            summarize(ledger, run, receipt);
            return;
        }
        if ("replay".equals(request.command())) {
            final var run = ledger.readback(request.executionId());
            summarize(ledger, run, receipt);
            if (run.state() == DevLocalLedger.State.STARTED) {
                throw new IOException("DEV_LOCAL_REPLAY_NOT_TERMINAL");
            }
            final var result =
                    JSON.readTree(ledger.readOutput(request.executionId(), "result.json"));
            requireSyntheticResult(result);
            requireTerminalMatch(run, result);
            receipt.put("replayed", true).set("result", result);
            return;
        }
        final var execution =
                request.executionId() == null ? "dev-" + UUID.randomUUID() : request.executionId();
        if (INITIALIZATION.equals(execution)) {
            throw new IllegalArgumentException("DEV_LOCAL_EXECUTION_RESERVED");
        }
        if ("stop".equals(request.command())) {
            if (request.executionId() != null) {
                final var observed = ledger.readback(execution);
                final var closed =
                        observed.state() == DevLocalLedger.State.STARTED
                                ? ledger.recover(execution, "LOCAL_STOP_REQUESTED")
                                : observed;
                summarize(ledger, closed, receipt);
                receipt.put("alreadyTerminal", observed.state() != DevLocalLedger.State.STARTED);
            } else {
                ledger.start(
                        execution,
                        DevLocalLedger.sha256(JSON.writeValueAsBytes(configuration.document())));
                ledger.event(execution, "LOCAL_SHUTDOWN", "SYNTHETIC_LOCAL_ONLY");
                ledger.finish(execution, DevLocalLedger.State.STOPPED, "LOCAL_STOP_REQUESTED");
                summarize(ledger, ledger.readback(execution), receipt);
            }
            return;
        }
        final var options = configuration.document();
        options.put(
                "mode",
                switch (request.command()) {
                    case "cancel" -> "CANCEL";
                    case "error" -> "ERROR";
                    default -> request.mode();
                });
        if (request.previousExecution() != null) {
            final var previousRun = ledger.readback(request.previousExecution());
            if (previousRun.state() != DevLocalLedger.State.COMPLETED) {
                throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_NOT_COMPLETED");
            }
            final var previous =
                    JSON.readTree(ledger.readOutput(request.previousExecution(), "result.json"));
            requireSyntheticResult(previous);
            requireTerminalMatch(previousRun, previous);
            if (!previous.path("state").isObject()) {
                throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_STATE_REQUIRED");
            }
            options.set("previousState", previous.path("state"));
        }
        final JsonNode result;
        try {
            result =
                    "scenario".equals(request.command())
                            ? DevLocalEngineScenario.execute(
                                    ledger,
                                    execution,
                                    DevLocalDataset.characterize(),
                                    configuration.document(),
                                    request.scenarioCase())
                            : DevLocalEngine.execute(
                                    ledger, execution, DevLocalDataset.characterize(), options);
        } catch (final IOException | RuntimeException failure) {
            // Preserve only observed durable state; a late refusal never compensates it.
            try {
                summarize(ledger, ledger.readback(execution), receipt);
            } catch (final IOException | RuntimeException readbackFailure) {
                failure.addSuppressed(readbackFailure);
            }
            throw failure;
        }
        requireSyntheticResult(result);
        final var observed = ledger.readback(execution);
        summarize(ledger, observed, receipt);
        requireTerminalMatch(observed, result);
        receipt.set("result", result);
        if ("scenario".equals(request.command())) {
            final var runs = result.path("runs");
            final int expectedRuns = "ABSENCE_REACTIVATION".equals(request.scenarioCase()) ? 7 : 2;
            if (!runs.isArray() || runs.size() != expectedRuns) {
                throw new IOException("DEV_LOCAL_SCENARIO_RUNS_BOUND");
            }
            final var summaries = receipt.putArray("scenarioRuns");
            final var seen = new java.util.HashSet<String>();
            final var phases = new java.util.HashSet<String>();
            for (final var child : runs) {
                final var childId = child.path("executionId").asText();
                final var phase = child.path("phase").asText();
                final var suffix =
                        switch (phase) {
                            case "CLEAN" -> "-clean";
                            case "ABSENCE" -> "-absent";
                            case "REACTIVATION" -> "-reactivated";
                            case "REFUSED_APPLY" -> "-refused";
                            default ->
                                    phase.matches("LOCAL_WITNESS_[1-4]")
                                            ? "-witness-proof" + phase.substring(phase.length() - 1)
                                            : "";
                        };
                if (suffix.isEmpty()
                        || !childId.equals(execution + suffix)
                        || !seen.add(childId)
                        || !phases.add(phase)) {
                    throw new IOException("DEV_LOCAL_SCENARIO_RUN_ID");
                }
                final var childRun = ledger.readback(childId);
                if (!childRun.state().name().equals(child.path("state").asText())
                        || !childRun.reasonCode().equals(child.path("reasonCode").asText())) {
                    throw new IOException("DEV_LOCAL_SCENARIO_RUN_READBACK");
                }
                summarize(ledger, childRun, summaries.addObject());
                if (phase.startsWith("LOCAL_WITNESS_")) {
                    final var absent = ledger.readOutput(childId, "result.json");
                    final var returned = ledger.readOutput(childId, "reappearance.json");
                    final var absentResult = JSON.readTree(absent);
                    final var returnedResult = JSON.readTree(returned);
                    requireSyntheticResult(absentResult);
                    requireSyntheticResult(returnedResult);
                    requireTerminalMatch(childRun, absentResult);
                    requireTerminalMatch(childRun, returnedResult);
                    if (childRun.outputs().size() != 2
                            || !"reappearance.json"
                                    .equals(child.path("reappearanceOutputName").asText())
                            || !DevLocalLedger.sha256(absent)
                                    .equals(child.path("absenceOutputSha256").asText())
                            || !DevLocalLedger.sha256(returned)
                                    .equals(child.path("reappearanceOutputSha256").asText())
                            || !child.path("absenceSnapshotSha256").asText().matches("[a-f0-9]{64}")
                            || !child.path("reappearanceSnapshotSha256")
                                    .asText()
                                    .matches("[a-f0-9]{64}")
                            || child.path("absenceSnapshotSha256")
                                    .equals(child.path("reappearanceSnapshotSha256"))) {
                        throw new IOException("DEV_LOCAL_SCENARIO_WITNESS_READBACK");
                    }
                }
            }
        }
    }

    private static void summarize(
            final DevLocalLedger ledger, final DevLocalLedger.Run run, final ObjectNode receipt)
            throws IOException {
        if (!run.equals(ledger.readback(run.executionId()))) {
            throw new IOException("DEV_LOCAL_STATUS_READBACK");
        }
        receipt.put("executionId", run.executionId())
                .put("status", run.state().name())
                .put("ledgerState", run.state().name())
                .put("reasonCode", run.reasonCode())
                .put("events", run.events().size())
                .put("outputs", run.outputs().size())
                .put(
                        "outputBytes",
                        run.outputs().stream().mapToLong(DevLocalLedger.Output::bytes).sum())
                .put("readbackVerified", true);
    }

    private static void checkpoint(
            final LongSupplier monotonic,
            final long began,
            final DevLocalMainConfiguration configuration) {
        if (monotonic.getAsLong() - began >= configuration.deadlineSeconds() * 1_000_000_000L) {
            throw new IllegalStateException("DEV_LOCAL_CLI_DEADLINE");
        }
    }

    private static void requireTerminalMatch(final DevLocalLedger.Run run, final JsonNode result)
            throws IOException {
        if (run.state() == DevLocalLedger.State.STARTED
                || !run.state().name().equals(result.path("status").asText())
                || !run.reasonCode().equals(result.path("reasonCode").asText())) {
            throw new IOException("DEV_LOCAL_REPLAY_TERMINAL_MISMATCH");
        }
    }

    private static void requireSyntheticResult(final JsonNode result) throws IOException {
        if (result == null
                || !result.path("synthetic").isBoolean()
                || !result.path("synthetic").booleanValue()
                || !"SYNTHETIC_LOCAL_ONLY".equals(result.path("identityScope").asText())) {
            throw new IOException("DEV_LOCAL_RESULT_SCOPE");
        }
    }

    private static String code(final Exception failure) {
        final String message =
                failure instanceof DevLocalLedger.Failure ledgerFailure
                        ? "DEV_LOCAL_" + ledgerFailure.code()
                        : failure.getMessage();
        return message != null && message.matches("DEV_LOCAL_[A-Z0-9_]{1,64}")
                ? message
                : "DEV_LOCAL_COMMAND_REFUSED";
    }

    private static final class OwnedLedger implements AutoCloseable {
        private final DevLocalLedger ledger;
        private boolean closed;

        OwnedLedger(final DevLocalLedger ledger) {
            this.ledger = ledger;
        }

        @Override
        public void close() throws IOException {
            ledger.close();
            closed = true;
        }
    }
}
