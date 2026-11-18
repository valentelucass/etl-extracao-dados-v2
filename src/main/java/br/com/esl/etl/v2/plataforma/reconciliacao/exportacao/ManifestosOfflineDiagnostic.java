package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Offline diagnostic entry point. Standard output receives only its bounded invocation receipt. */
public final class ManifestosOfflineDiagnostic {
    private static final int DIAGNOSTIC_BOUND = 2097152;

    private ManifestosOfflineDiagnostic() {}

    public static ObjectNode execute(
            final JsonNode input,
            final ExportValidationSupport.Pin manifest,
            final Path destination,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(input != null && input.isObject(), "MANIFESTOS_DIAGNOSTIC_INPUT_OBJECT");
        final var names = new HashSet<String>();
        input.fieldNames().forEachRemaining(names::add);
        require(
                names.equals(Set.of("schema", "requestPin", "sourcePin", "workbookPin"))
                        && "MANIFESTOS_OFFLINE_DIAGNOSTIC_INPUT_V1"
                                .equals(input.path("schema").asText()),
                "MANIFESTOS_DIAGNOSTIC_INPUT_SCOPE");
        final var request = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var source = ExportValidationSupport.Pin.from(input.required("sourcePin"));
        final var workbook = ExportValidationSupport.Pin.from(input.required("workbookPin"));
        final Path target = destination.toAbsolutePath().normalize();
        final Path receiptPath = target.resolveSibling(target.getFileName() + ".receipt.json");
        require(
                Files.isDirectory(target.getParent())
                        && !Files.exists(target)
                        && !Files.exists(receiptPath),
                "MANIFESTOS_FRESH_DIAGNOSTIC_REQUIRED");
        for (Path part = target; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "MANIFESTOS_OUTPUT_LINK_REFUSED");
        }
        for (final var pin : java.util.List.of(manifest, request, source, workbook)) {
            require(
                    !pin.path().equals(target) && !pin.path().equals(receiptPath),
                    "MANIFESTOS_OUTPUT_INPUT_COLLISION");
        }
        final var diagnostic =
                ManifestosContentAdapter.compare(
                        request.json(262144, budget), source, workbook, budget);
        request.read(262144, budget);
        manifest.read(262144, budget);
        final String execution = UUID.randomUUID().toString();
        diagnostic.put("executionId", execution);
        diagnostic.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        diagnostic.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        final byte[] diagnosticBytes = ExportValidationSupport.JSON.writeValueAsBytes(diagnostic);
        require(diagnosticBytes.length <= DIAGNOSTIC_BOUND, "MANIFESTOS_DIAGNOSTIC_SIZE");
        budget.check();
        writeFresh(target, diagnosticBytes);
        final var diagnosticPin =
                new ExportValidationSupport.Pin(
                        target,
                        ExportValidationSupport.sha256(diagnosticBytes),
                        diagnosticBytes.length);
        diagnosticPin.read(DIAGNOSTIC_BOUND, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_OFFLINE_DIAGNOSTIC_EXECUTED_RECEIPT_V1")
                        .put("executionId", execution)
                        .put("attemptId", diagnostic.path("attemptId").asText())
                        .put("status", diagnostic.path("status").asText())
                        .put("startedAt", started.toString())
                        .put("finishedAt", clock.instant().toString())
                        .put("sourceRows", diagnostic.required("sourceRows").intValue())
                        .put("uiRows", diagnostic.required("uiRows").intValue())
                        .put("columns", 91)
                        .put("totalColumns", diagnostic.required("totalColumns").intValue())
                        .put(
                                "contentEqualUnderCandidatePresentation",
                                diagnostic
                                        .required("contentEqualUnderCandidatePresentation")
                                        .booleanValue())
                        .put("diagnosticOnly", true)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        receipt.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        receipt.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        receipt.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
        for (final var pin : java.util.List.of(manifest, request, source, workbook)) {
            pin.read(pin == manifest || pin == request ? 262144 : 8388608, budget);
        }
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(receipt);
        require(receiptBytes.length <= 262144, "MANIFESTOS_RECEIPT_SIZE");
        writeFresh(receiptPath, receiptBytes);
        final var receiptPin =
                new ExportValidationSupport.Pin(
                        receiptPath,
                        ExportValidationSupport.sha256(receiptBytes),
                        receiptBytes.length);
        receiptPin.read(262144, budget);
        diagnosticPin.read(DIAGNOSTIC_BOUND, budget);
        final var published = receipt.deepCopy();
        published.set("receiptPin", ExportValidationSupport.JSON.valueToTree(receiptPin));
        return published;
    }

    private static void writeFresh(final Path path, final byte[] bytes) throws Exception {
        try (var channel =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
