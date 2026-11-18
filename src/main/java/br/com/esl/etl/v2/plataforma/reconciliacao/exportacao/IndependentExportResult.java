package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Diagnostic pin and invocation receipt are fixed before this execution returns to its caller. */
public final class IndependentExportResult {
    private IndependentExportResult() {}

    public static ObjectNode persist(
            final Path destination,
            final ObjectNode diagnostic,
            final ExportValidationSupport.Pin manifestPin,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws IOException {
        return persist(destination, diagnostic, manifestPin, started, clock, budget, false, false);
    }

    public static ObjectNode persistHistorical(
            final Path destination,
            final ObjectNode diagnostic,
            final ExportValidationSupport.Pin manifestPin,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws IOException {
        return persist(destination, diagnostic, manifestPin, started, clock, budget, true, false);
    }

    public static ObjectNode persistControlPreparation(
            final Path destination,
            final ObjectNode diagnostic,
            final ExportValidationSupport.Pin manifestPin,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws IOException {
        return persist(destination, diagnostic, manifestPin, started, clock, budget, false, true);
    }

    private static ObjectNode persist(
            final Path destination,
            final ObjectNode diagnostic,
            final ExportValidationSupport.Pin manifestPin,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget,
            final boolean historical,
            final boolean preparation)
            throws IOException {
        final Path target = destination.toAbsolutePath().normalize();
        final Path receipt = target.resolveSibling(target.getFileName() + ".receipt.json");
        require(
                !Files.exists(target)
                        && !Files.exists(receipt)
                        && Files.isDirectory(target.getParent()),
                "EXPORT_FRESH_RESULT_REQUIRED");
        for (Path part = target.getParent(); part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "EXPORT_RESULT_LINK_REFUSED");
        }
        final String execution = UUID.randomUUID().toString();
        diagnostic.put("executionId", execution);
        diagnostic.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifestPin));
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(diagnostic);
        require(bytes.length <= 262144, "EXPORT_DIAGNOSTIC_BOUND");
        budget.check();
        writeFresh(target, bytes);
        final var pin =
                new ExportValidationSupport.Pin(
                        target, ExportValidationSupport.sha256(bytes), bytes.length);
        pin.read(262144, budget);
        manifestPin.read(262144, budget);
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put(
                "schema",
                preparation
                        ? "INDEPENDENT_EXPORT_HISTORICAL_CONTROLS_EXECUTED_RECEIPT_V1"
                        : historical
                                ? "INDEPENDENT_EXPORT_HISTORICAL_EXECUTED_RECEIPT_V1"
                                : "INDEPENDENT_EXPORT_EXECUTED_RECEIPT_V1");
        result.put("executionId", execution);
        if (historical || preparation) {
            result.put("replayId", diagnostic.path("replayId").asText());
            result.put("historicalContentOnly", true)
                    .put("operationalAcceptanceIssued", false)
                    .put("acquisitionEvidenceAuthenticationVerified", false)
                    .put("entityQualified", false)
                    .put("mainAdopted", false);
        } else {
            result.put("attemptId", diagnostic.path("attemptId").asText());
        }
        result.put("status", diagnostic.path("status").asText());
        result.put("startedAt", started.toString());
        result.put("finishedAt", clock.instant().toString());
        result.put("clockProvenance", "INVOCATION_CLOCK_UTC");
        result.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(pin));
        result.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifestPin));
        if (!preparation) {
            for (final String field :
                    java.util.List.of(
                            "requestPin", "configPin", "mappingPin", "admissionPin", "api", "ui")) {
                result.set(field, diagnostic.required(field));
            }
        } else {
            result.put("controlsMetadataOnly", true);
        }
        result.put("mainResumeAllowed", false);
        result.put("dashboardConsumptionVerified", false);
        result.put("Source", 0);
        result.put("HTTP", 0);
        result.put("SQL", 0);
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
        writeFresh(receipt, receiptBytes);
        final var receiptPin =
                new ExportValidationSupport.Pin(
                        receipt, ExportValidationSupport.sha256(receiptBytes), receiptBytes.length);
        receiptPin.read(262144, budget);
        pin.read(262144, budget);
        final var published = result.deepCopy();
        published.set("receiptPin", ExportValidationSupport.JSON.valueToTree(receiptPin));
        return published;
    }

    private static void writeFresh(final Path path, final byte[] bytes) throws IOException {
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
