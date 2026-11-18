package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Concrete bridge to the pinned Root worker/OwnJob/native census/privacy boundary. The worker owns
 * the actual wire process and Source-wide STOP429 latch; Java never starts curl.
 */
public final class RootOwnedWireChannel implements OwnedExportWire {
    private final ExportValidationSupport.Pin contextPin;
    private final ExportValidationSupport.Pin powershell;
    private final ExportValidationSupport.Pin worker;
    private final PersistentExportJournal journal;
    private final String sourceConfigurationSha;
    private final ExportValidationSupport.Pin mainProofPin;

    public RootOwnedWireChannel(
            final ExportValidationSupport.Pin contextPin,
            final PersistentExportJournal journal,
            final String expectedSourceConfigurationSha)
            throws Exception {
        this.contextPin = contextPin;
        this.journal = journal;
        final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
        final var context = contextPin.json(262144, budget);
        require(
                "ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1"
                                .equals(context.path("schema").asText())
                        && expectedSourceConfigurationSha.equals(
                                context.path("sourceConfigurationSha256").asText()),
                "EXPORT_ROOT_PHYSICAL_CONTEXT_BINDING");
        this.sourceConfigurationSha = expectedSourceConfigurationSha;
        final String image = context.path("ownedMainImage").asText();
        require(
                java.util.List.of("java.exe", "javaw.exe").contains(image),
                "EXPORT_ROOT_OWNED_MAIN_NAME");
        final var current = ProcessHandle.current();
        final var parent = current.parent().orElseThrow();
        final var birth = current.info().startInstant().orElseThrow();
        mainProofPin =
                journal.pin(
                        journal.record(
                                "OWNED_MAIN_SELF_PROOF",
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("schema", "INDEPENDENT_OWNED_MAIN_SELF_PROOF_V1")
                                        .put("pid", current.pid())
                                        .put("parentPid", parent.pid())
                                        .put("startEpochMillis", birth.toEpochMilli())
                                        .put("imageName", image)
                                        .put("birthComparisonPrecision", "MILLISECOND")
                                        .put(
                                                "codeUnitSha256",
                                                context.path("codeUnitSha256").asText())));
        powershell = ExportValidationSupport.Pin.from(context.required("powershell"));
        worker = ExportValidationSupport.Pin.from(context.required("worker"));
        // Execute only the bytes that are actually packaged with this product, never an SPI script.
        verifyResource(worker, "/independent-export/RootWireWorker.ps1", budget);
        verifyResource(
                ExportValidationSupport.Pin.from(context.required("nativeGuard")),
                "/independent-export/RootNativeCensus.ps1",
                budget);
        verifyResource(
                ExportValidationSupport.Pin.from(context.required("privacyGuard")),
                "/independent-export/RootPrivacyGuard.ps1",
                budget);
        final var nativeSource =
                ExportValidationSupport.Pin.from(context.required("childJobSource"));
        verifyResource(nativeSource, "/independent-export/OwnedCurlJob.cs", budget);
        final var build =
                ExportValidationSupport.Pin.from(context.required("nativeBuildReceiptPin"))
                        .json(262144, budget);
        require(
                "ROOT_INDEPENDENT_EXPORT_NATIVE_BUILD_V1".equals(build.path("schema").asText())
                        && build.path("success").asBoolean()
                        && build.path("originalClosedKnown").asBoolean()
                        && build.required("sourcePin").equals(context.required("childJobSource"))
                        && build.required("dllPin").equals(context.required("childJobDll")),
                "EXPORT_ROOT_NATIVE_BUILD_ORIGIN_REQUIRED");
    }

    private static void verifyResource(
            final ExportValidationSupport.Pin pin,
            final String resource,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        try (var input = RootOwnedWireChannel.class.getResourceAsStream(resource)) {
            require(input != null, "EXPORT_ROOT_PACKAGED_GUARD_REQUIRED");
            final byte[] bytes = input.readNBytes(262145);
            require(
                    bytes.length <= 262144
                            && pin.sha256().equals(ExportValidationSupport.sha256(bytes)),
                    "EXPORT_ROOT_GUARD_CODE_PIN");
            pin.read(262144, budget);
        }
    }

    public synchronized CurlExportTransport.Wire exchange(
            final String method,
            final URI url,
            final byte[] jsonBody,
            final boolean dataExportAuthentication,
            final int maximumBodyBytes,
            final Duration timeout,
            final Instant deadline)
            throws Exception {
        final Instant localDeadline = Instant.now().plus(timeout);
        final Instant requestDeadline = localDeadline.isBefore(deadline) ? localDeadline : deadline;
        final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(60));
        powershell.read(8388608, budget);
        worker.read(262144, budget);
        contextPin.read(262144, budget);
        final var message =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_SINGLE_WIRE_REQUEST_V1")
                        .put("operationId", java.util.UUID.randomUUID().toString())
                        .put("sourceConfigurationSha256", sourceConfigurationSha)
                        .put("method", method)
                        .put("url", url.toASCIIString())
                        .put("authentication", dataExportAuthentication ? "DATA_EXPORT" : "NONE")
                        .put("maximumBodyBytes", maximumBodyBytes)
                        .put("timeoutMillis", timeout.toMillis())
                        .put("deadline", requestDeadline.toString());
        if (jsonBody != null) {
            message.put("jsonBody", new String(jsonBody, StandardCharsets.UTF_8));
        }
        final var start =
                new ProcessBuilder(
                        powershell.path().toString(),
                        "-NoLogo",
                        "-NoProfile",
                        "-NonInteractive",
                        "-File",
                        worker.path().toString(),
                        "-ContextPath",
                        contextPin.path().toString(),
                        "-ContextSha256",
                        contextPin.sha256(),
                        "-MainProofPath",
                        mainProofPin.path().toString(),
                        "-MainProofSha256",
                        mainProofPin.sha256());
        start.environment().clear();
        start.environment().put("SystemRoot", "C:\\Windows");
        start.environment().put("WINDIR", "C:\\Windows");
        final Process process = start.start();
        final var readers = Executors.newFixedThreadPool(2);
        boolean streamsClosed = false;
        try {
            final var stdout =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8));
            final var readyFuture = readers.submit(() -> boundedLine(stdout, 4096));
            final String readyLine =
                    readyFuture.get(
                            Math.min(12000, remainingMillis(requestDeadline)),
                            TimeUnit.MILLISECONDS);
            require(readyLine != null && readyLine.length() <= 4096, "EXPORT_ROOT_READY_BOUND");
            final var ready = ExportValidationSupport.JSON.readTree(readyLine);
            require(
                    "ROOT_WIRE_READY_V1".equals(ready.path("schema").asText())
                            && ready.path("privateInputMayFollow").asBoolean(),
                    "EXPORT_ROOT_PHYSICAL_PRECHECK_REQUIRED");
            // No credential is materialized in Java. The pinned existing provider is called by
            // the worker only after its real Native/OwnJob/private-path gates.
            final var resultFuture =
                    readers.submit(
                            () -> {
                                final String line = boundedLine(stdout, 131072);
                                require(
                                        line != null && boundedLine(stdout, 1) == null,
                                        "EXPORT_ROOT_RESULT_CHANNEL_BOUND");
                                return line;
                            });
            final var errorFuture =
                    readers.submit(
                            () -> {
                                final byte[] bytes = process.getErrorStream().readNBytes(65537);
                                require(
                                        bytes.length <= 65536
                                                && process.getErrorStream().read() == -1,
                                        "EXPORT_ROOT_ERROR_BOUND");
                                return bytes;
                            });
            final byte[] privateInput = ExportValidationSupport.JSON.writeValueAsBytes(message);
            require(privateInput.length <= 1048576, "EXPORT_ROOT_PRIVATE_INPUT_BOUND");
            try (var input = process.getOutputStream()) {
                input.write(privateInput);
            } finally {
                Arrays.fill(privateInput, (byte) 0);
            }
            require(
                    process.waitFor(
                            Math.min(
                                    timeout.toMillis() + 15000,
                                    remainingMillis(requestDeadline) + 5000),
                            TimeUnit.MILLISECONDS),
                    "EXPORT_ROOT_WORKER_DEADLINE");
            final JsonNode receipt =
                    ExportValidationSupport.JSON.readTree(
                            resultFuture.get(1000, TimeUnit.MILLISECONDS));
            Arrays.fill(errorFuture.get(1000, TimeUnit.MILLISECONDS), (byte) 0);
            stdout.close();
            process.getErrorStream().close();
            streamsClosed = true;
            journal.record("ROOT_WIRE_RECEIPT", receipt);
            require(
                    "ROOT_INDEPENDENT_SINGLE_WIRE_RESULT_V1".equals(receipt.path("schema").asText())
                            && receipt.path("OriginalPid").asLong() == process.pid()
                            && message.path("operationId").equals(receipt.path("operationId"))
                            && process.exitValue() == 0
                            && receipt.path("closedKnown").asBoolean()
                            && receipt.path("stdinClosedKnown").asBoolean()
                            && receipt.path("stdoutEof").asBoolean()
                            && receipt.path("stderrEof").asBoolean()
                            && receipt.path("failure").isNull(),
                    "EXPORT_ROOT_WIRE_NOT_CLOSED");
            final var child = receipt.required("childJobFacts");
            require(
                    child.isArray()
                            && child.size() == 7
                            && child.get(1).asLong() == 134217728
                            && child.get(2).asLong() == 134217728
                            && child.get(5).asLong() == 0
                            && child.get(6).asLong() == 1
                            && child.get(0).asLong() == receipt.path("ownedCurlPid").asLong()
                            && !receipt.path("kernelBirthUtc").asText().isBlank(),
                    "EXPORT_ROOT_CHILD_JOB_RECEIPT");
            final var bodyNode = receipt.required("bodyPin");
            final var body =
                    bodyNode.path("bytes").asLong() == 0
                            ? null
                            : ExportValidationSupport.Pin.from(bodyNode);
            if (body != null) {
                body.read(maximumBodyBytes, budget);
            }
            return new CurlExportTransport.Wire(
                    receipt.path("status").asInt(),
                    body,
                    java.time.OffsetDateTime.parse(receipt.path("completedAt").asText())
                            .toInstant());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(2000, TimeUnit.MILLISECONDS);
            }
            process.getOutputStream().close();
            process.getInputStream().close();
            process.getErrorStream().close();
            readers.shutdownNow();
            require(
                    !process.isAlive() && readers.awaitTermination(1000, TimeUnit.MILLISECONDS),
                    "EXPORT_ROOT_WORKER_CLOSURE_UNKNOWN");
            // A failed precheck can close cleanly while producing no private input or HTTP.
            journal.record(
                    "ROOT_CHANNEL_CLOSED",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("ownedWorkerPid", process.pid())
                            .put("workerExit", process.exitValue())
                            .put("allResultStreamsObserved", streamsClosed));
        }
    }

    private static String boundedLine(final BufferedReader input, final int maximum)
            throws java.io.IOException {
        final var line = new StringBuilder();
        for (int ch = input.read(); ch != -1; ch = input.read()) {
            if (ch == '\n') {
                return line.toString();
            }
            if (ch == '\r') {
                require(input.read() == '\n', "EXPORT_ROOT_LINE_ENDING");
                return line.toString();
            }
            require(line.length() < maximum, "EXPORT_ROOT_LINE_BOUND");
            line.append((char) ch);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static long remainingMillis(final Instant deadline) {
        final long remaining = Duration.between(Instant.now(), deadline).toMillis();
        require(remaining > 0, "EXPORT_ROOT_INVOCATION_DEADLINE");
        return remaining;
    }
}
