package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Fresh private result plus read-back receipt, fixed before returning metadata to Main. */
public final class OperationalExportReceipt {
    private OperationalExportReceipt() {}

    public static ObjectNode persist(
            final Path destination,
            final ObjectNode diagnostic,
            final ExportValidationSupport.Pin selection,
            final ExportValidationSupport.Pin outcome,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var receiptPath =
                destination.resolveSibling(destination.getFileName() + ".receipt.json");
        require(
                destination.isAbsolute()
                        && !Files.exists(destination)
                        && !Files.exists(receiptPath),
                "EXPORT_OPERATIONAL_FRESH_RECEIPT");
        final String execution = UUID.randomUUID().toString();
        diagnostic.put("executionId", execution);
        diagnostic.set("journalOutcomePin", ExportValidationSupport.JSON.valueToTree(outcome));
        final var data = write(destination, diagnostic, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_OPERATIONAL_EXECUTED_RECEIPT_V1")
                        .put("executionId", execution)
                        .put("attemptId", diagnostic.path("attemptId").asText())
                        .put("status", diagnostic.path("status").asText())
                        .put("startedAt", started.toString())
                        .put("finishedAt", clock.instant().toString())
                        .put("mainResumeAllowed", false)
                        .put("dashboardConsumptionVerified", false);
        receipt.set("selectionPin", ExportValidationSupport.JSON.valueToTree(selection));
        receipt.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(data));
        receipt.set("journalOutcomePin", ExportValidationSupport.JSON.valueToTree(outcome));
        final var receiptPin = write(receiptPath, receipt, budget);
        data.read(262144, budget);
        outcome.read(262144, budget);
        selection.read(262144, budget);
        receipt.set("receiptPin", ExportValidationSupport.JSON.valueToTree(receiptPin));
        return receipt;
    }

    private static ExportValidationSupport.Pin write(
            final Path path, final ObjectNode value, final ExportValidationSupport.Budget budget)
            throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        require(bytes.length > 0 && bytes.length <= 262144, "EXPORT_OPERATIONAL_DIAGNOSTIC_BOUND");
        for (Path part = path.getParent(); part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "EXPORT_OPERATIONAL_RECEIPT_LINK");
        }
        budget.check();
        try (var channel =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        final var pin =
                new ExportValidationSupport.Pin(
                        path, ExportValidationSupport.sha256(bytes), bytes.length);
        pin.read(262144, budget);
        return pin;
    }
}
