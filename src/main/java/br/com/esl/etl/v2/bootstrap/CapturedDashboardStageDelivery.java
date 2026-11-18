package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.CapturedDashboardDeliveryOutcome;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Single production delivery port: actual captured/governed stage -> independent local reader. No
 * SQL.
 */
final class CapturedDashboardStageDelivery implements GovernedExportDelivery.Port {
    static final String KIND = "CAPTURED_GOVERNED_TYPED_STAGE_WITH_LOCAL_CONSUMER_V1";

    @Override
    public Optional<ExportValidationSupport.Pin> deliver(
            final GovernedExportDelivery.Context context) throws Exception {
        if (!KIND.equals(text(context.plan(), "kind"))) {
            return Optional.empty();
        }
        final var receipt = (ObjectNode) context.captureReceiptPin().json(262144, context.budget());
        receipt.set(
                "receiptPin",
                ExportValidationSupport.JSON.valueToTree(context.captureReceiptPin()));
        final var profile = text(context.plan(), "profile");
        final var observed =
                CapturedDashboardDeliveryOutcome.verifyPrepared(
                        receipt, context.selectionPin(), profile, context.budget());
        if (!observed.path("actualLocalStageReadbackVerified").asBoolean()) {
            return Optional.empty();
        }
        final var stagePin = ExportValidationSupport.Pin.from(observed.required("typedStagePin"));
        final var artifact =
                write(
                        Path.of(text(context.plan(), "artifactPath")),
                        stagePin.read(8388608, context.budget()),
                        context.budget());
        // The consumer independently opens the produced artifact and writes its observed bytes.
        final var roundtrip =
                write(
                        Path.of(text(context.plan(), "consumerRoundtripPath")),
                        artifact.read(8388608, context.budget()),
                        context.budget());
        final var consumer =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_GOVERNED_CAPTURE_CONSUMED_V1")
                        .put("attemptId", text(context.plan(), "attemptId"))
                        .put("profile", profile)
                        .put("acceptedAndReadBack", true)
                        .put("consumerClosedKnown", true)
                        .put("SQLPublicationVerified", false)
                        .put("dashboardConsumptionVerified", false);
        consumer.set("artifactPin", node(artifact));
        consumer.set("roundtripPin", node(roundtrip));
        consumer.set("captureReceiptPin", node(context.captureReceiptPin()));
        consumer.set("deliveryPlanPin", node(context.planPin()));
        consumer.set("typedReadback", observed);
        final var consumerPin =
                write(
                        Path.of(text(context.plan(), "consumerReceiptPath")),
                        ExportValidationSupport.JSON.writeValueAsBytes(consumer),
                        context.budget());
        final var producer =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_GOVERNED_CAPTURE_PRODUCED_V1")
                        .put("attemptId", text(context.plan(), "attemptId"))
                        .put("profile", profile)
                        .put("producerClosedKnown", true)
                        .put("deliveryKind", KIND)
                        .put("semanticApprovalIssued", false)
                        .put("productionPublicationPerformed", false);
        producer.set("deliveryPlanPin", node(context.planPin()));
        producer.set("selectionPin", node(context.selectionPin()));
        producer.set("captureReceiptPin", node(context.captureReceiptPin()));
        producer.set("diagnosticPin", node(context.diagnosticPin()));
        producer.set("apiDataPin", context.diagnostic().path("api").required("dataPin"));
        producer.set("uiDataPin", context.diagnostic().path("ui").required("dataPin"));
        producer.set("artifactPin", node(artifact));
        producer.set("consumerReceiptPin", node(consumerPin));
        producer.set("typedStagePin", node(stagePin));
        producer.set("stageProducerResultPin", observed.required("producerResultPin"));
        return Optional.of(
                write(
                        Path.of(text(context.plan(), "producerReceiptPath")),
                        ExportValidationSupport.JSON.writeValueAsBytes(producer),
                        context.budget()));
    }

    private static JsonNode node(final ExportValidationSupport.Pin pin) {
        return ExportValidationSupport.JSON.valueToTree(pin);
    }

    private static ExportValidationSupport.Pin write(
            final Path path, final byte[] value, final ExportValidationSupport.Budget budget)
            throws Exception {
        final var partial = path.resolveSibling(path.getFileName() + ".partial");
        require(
                path.isAbsolute()
                        && value.length > 0
                        && value.length <= 8388608
                        && !Files.exists(path)
                        && !Files.exists(partial),
                "EXPORT_TYPED_DELIVERY_FRESH_BOUND");
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "EXPORT_TYPED_DELIVERY_LINK");
        }
        budget.check();
        try (var channel =
                FileChannel.open(
                        partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var bytes = ByteBuffer.wrap(value);
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
        budget.check();
        Files.move(partial, path, StandardCopyOption.ATOMIC_MOVE);
        final var pin =
                new ExportValidationSupport.Pin(
                        path, ExportValidationSupport.sha256(value), value.length);
        pin.read(8388608, budget);
        return pin;
    }
}
