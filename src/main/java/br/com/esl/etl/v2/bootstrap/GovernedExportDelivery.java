package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Bounded closure for delivery of the exact validated capture. This is not a dashboard/SQL
 * publication authority. The owner must install the actual governed producer/consumer port.
 */
final class GovernedExportDelivery {
    private GovernedExportDelivery() {}

    @FunctionalInterface
    interface Port {
        /** Returns the durable producer receipt, or empty when delivery has not completed. */
        Optional<ExportValidationSupport.Pin> deliver(Context context) throws Exception;
    }

    record Context(
            JsonNode plan,
            ExportValidationSupport.Pin planPin,
            ExportValidationSupport.Pin selectionPin,
            ExportValidationSupport.Pin captureReceiptPin,
            ExportValidationSupport.Pin diagnosticPin,
            JsonNode diagnostic,
            ExportValidationSupport.Budget budget) {
        Context {
            plan = plan.deepCopy();
            diagnostic = diagnostic.deepCopy();
        }

        @Override
        public JsonNode plan() {
            return plan.deepCopy();
        }

        @Override
        public JsonNode diagnostic() {
            return diagnostic.deepCopy();
        }
    }

    static Port unavailable() {
        return ignored -> Optional.empty();
    }

    static void validatePlan(final JsonNode selection, final String requiredProfile)
            throws Exception {
        final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(5));
        final var request =
                ExportValidationSupport.Pin.from(selection.required("requestPin"))
                        .json(262144, budget);
        final var plan =
                ExportValidationSupport.Pin.from(selection.required("governedDeliveryPlanPin"))
                        .json(262144, budget);
        require(
                "INDEPENDENT_GOVERNED_CAPTURE_DELIVERY_PLAN_V1".equals(text(plan, "schema"))
                        && ("EXACT_VALIDATED_CAPTURE_WITH_CONSUMER_ROUNDTRIP_V1"
                                        .equals(text(plan, "kind"))
                                || CapturedDashboardStageDelivery.KIND.equals(text(plan, "kind")))
                        && requiredProfile.equals(text(plan, "profile"))
                        && text(request, "attemptId").equals(text(plan, "attemptId"))
                        && plan.required("requestPin").equals(selection.required("requestPin")),
                "EXPORT_GOVERNED_DELIVERY_PLAN_SCOPE");
        if (CapturedDashboardStageDelivery.KIND.equals(text(plan, "kind"))) {
            require(
                    ExportValidationSupport.Pin.from(plan.required("dashboardInputRecipePin"))
                            .equals(
                                    ExportValidationSupport.Pin.from(
                                            selection.required("dashboardInputRecipePin"))),
                    "EXPORT_TYPED_DELIVERY_RECIPE_BINDING");
        }
        final var physical =
                ExportValidationSupport.Pin.from(selection.required("physicalContextPin"))
                        .json(262144, budget);
        final var root = Path.of(text(physical, "privateRoot")).toAbsolutePath().normalize();
        final var distinct = new java.util.HashSet<Path>();
        for (final String field :
                List.of(
                        "artifactPath",
                        "producerReceiptPath",
                        "consumerReceiptPath",
                        "consumerRoundtripPath",
                        "resultPath")) {
            final var path = Path.of(text(plan, field));
            require(
                    path.isAbsolute()
                            && path.normalize().startsWith(root)
                            && !path.normalize().equals(root)
                            && distinct.add(path.normalize())
                            && Files.isDirectory(path.getParent())
                            && !Files.exists(path),
                    "EXPORT_GOVERNED_DELIVERY_FRESH_PRIVATE_PATH");
            for (var part = path; part != null; part = part.getParent()) {
                require(!Files.isSymbolicLink(part), "EXPORT_GOVERNED_DELIVERY_PATH_LINK");
            }
        }
        require(
                distinct.stream()
                        .noneMatch(
                                path ->
                                        path.equals(
                                                        Path.of(text(selection, "resultPath"))
                                                                .normalize())
                                                || path.equals(
                                                        Path.of(
                                                                        text(
                                                                                selection,
                                                                                "referenceCallbackPath"))
                                                                .normalize())),
                "EXPORT_GOVERNED_DELIVERY_DISTINCT_CONTROL_PATHS");
    }

    static ObjectNode finish(
            final JsonNode selection,
            final ExportValidationSupport.Pin selectionPin,
            final ObjectNode capture,
            final String profile,
            final Port port,
            final long invocationStartedNanos)
            throws Exception {
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_OPERATIONAL_PROFILE_RESULT_V1")
                        .put("profile", profile)
                        .put("status", "PENDING_GOVERNED_DELIVERY")
                        .put("comparisonStatus", capture.path("status").asText())
                        .put("governedDeliveryComplete", false)
                        .put("mainResumeAllowed", false)
                        .put("dashboardConsumptionVerified", false);
        result.set("captureComparisonReceipt", capture);
        if (!"CONTENT_EQUAL_FOR_THIS_PAIR".equals(capture.path("status").asText())) {
            result.put("status", capture.path("status").asText());
            return result;
        }
        final long remaining =
                Duration.ofSeconds(selection.required("maximumDurationSeconds").longValue())
                                .toNanos()
                        - (System.nanoTime() - invocationStartedNanos);
        require(remaining > 0, "EXPORT_GOVERNED_DELIVERY_INVOCATION_DEADLINE");
        final var budget =
                new ExportValidationSupport.Budget(
                        Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(150).toNanos())));
        final var request =
                ExportValidationSupport.Pin.from(selection.required("requestPin"))
                        .json(262144, budget);
        final var capturePin = ExportValidationSupport.Pin.from(capture.required("receiptPin"));
        final var stored = capturePin.json(262144, budget);
        final var withoutSelfPin = capture.deepCopy();
        withoutSelfPin.remove("receiptPin");
        final var diagnosticPin =
                ExportValidationSupport.Pin.from(capture.required("diagnosticPin"));
        final var diagnostic = diagnosticPin.json(262144, budget);
        require(
                Arrays.equals(
                                ExportValidationSupport.canonicalBytes(stored),
                                ExportValidationSupport.canonicalBytes(withoutSelfPin))
                        && "INDEPENDENT_EXPORT_OPERATIONAL_EXECUTED_RECEIPT_V1"
                                .equals(text(stored, "schema"))
                        && text(request, "attemptId").equals(text(stored, "attemptId"))
                        && text(request, "attemptId").equals(text(diagnostic, "attemptId"))
                        && profile.equals(text(diagnostic, "profile"))
                        && "CONTENT_EQUAL_FOR_THIS_PAIR".equals(text(diagnostic, "status"))
                        && ExportValidationSupport.Pin.from(stored.required("selectionPin"))
                                .equals(selectionPin)
                        && diagnostic
                                .required("selectionPin")
                                .equals(stored.required("selectionPin"))
                        && stored.required("journalOutcomePin")
                                .equals(diagnostic.required("journalOutcomePin"))
                        && !diagnostic.path("wireOutcomeUnknown").asBoolean(true),
                "EXPORT_GOVERNED_DELIVERY_EXECUTED_COMPARISON_BINDING");
        final var planPin =
                ExportValidationSupport.Pin.from(selection.required("governedDeliveryPlanPin"));
        final var plan = planPin.json(262144, budget);
        final var context =
                new Context(
                        plan, planPin, selectionPin, capturePin, diagnosticPin, diagnostic, budget);
        final var delivered = port.deliver(context);
        if (delivered.isEmpty()) {
            return result;
        }
        final var producerPin = delivered.orElseThrow();
        require(
                producerPin.path().equals(Path.of(text(plan, "producerReceiptPath"))),
                "EXPORT_GOVERNED_PRODUCER_RECEIPT_PATH");
        final var producer = producerPin.json(262144, budget);
        final var apiPin =
                ExportValidationSupport.Pin.from(diagnostic.path("api").required("dataPin"));
        final var uiPin =
                ExportValidationSupport.Pin.from(diagnostic.path("ui").required("dataPin"));
        final var artifactPin = ExportValidationSupport.Pin.from(producer.required("artifactPin"));
        final var consumerPin =
                ExportValidationSupport.Pin.from(producer.required("consumerReceiptPin"));
        final var consumer = consumerPin.json(262144, budget);
        final var roundtripPin =
                ExportValidationSupport.Pin.from(consumer.required("roundtripPin"));
        require(
                "INDEPENDENT_GOVERNED_CAPTURE_PRODUCED_V1".equals(text(producer, "schema"))
                        && "INDEPENDENT_GOVERNED_CAPTURE_CONSUMED_V1"
                                .equals(text(consumer, "schema"))
                        && text(request, "attemptId").equals(text(producer, "attemptId"))
                        && text(request, "attemptId").equals(text(consumer, "attemptId"))
                        && profile.equals(text(producer, "profile"))
                        && profile.equals(text(consumer, "profile"))
                        && ExportValidationSupport.Pin.from(producer.required("deliveryPlanPin"))
                                .equals(planPin)
                        && ExportValidationSupport.Pin.from(producer.required("selectionPin"))
                                .equals(selectionPin)
                        && ExportValidationSupport.Pin.from(producer.required("captureReceiptPin"))
                                .equals(capturePin)
                        && ExportValidationSupport.Pin.from(producer.required("diagnosticPin"))
                                .equals(diagnosticPin)
                        && ExportValidationSupport.Pin.from(producer.required("apiDataPin"))
                                .equals(apiPin)
                        && ExportValidationSupport.Pin.from(producer.required("uiDataPin"))
                                .equals(uiPin)
                        && consumer.required("artifactPin").equals(producer.required("artifactPin"))
                        && consumer.required("captureReceiptPin")
                                .equals(producer.required("captureReceiptPin"))
                        && consumer.required("deliveryPlanPin")
                                .equals(producer.required("deliveryPlanPin"))
                        && producer.path("producerClosedKnown").asBoolean()
                        && consumer.path("consumerClosedKnown").asBoolean()
                        && consumer.path("acceptedAndReadBack").asBoolean(),
                "EXPORT_GOVERNED_DURABLE_PRODUCER_CONSUMER_BINDING");
        require(
                artifactPin.path().equals(Path.of(text(plan, "artifactPath")))
                        && consumerPin.path().equals(Path.of(text(plan, "consumerReceiptPath")))
                        && roundtripPin.path().equals(Path.of(text(plan, "consumerRoundtripPath")))
                        && !Files.isSameFile(apiPin.path(), artifactPin.path())
                        && !Files.isSameFile(artifactPin.path(), roundtripPin.path()),
                "EXPORT_GOVERNED_INDEPENDENT_MATERIALIZATION");
        // Verify all bytes, including every field, physical occurrence and any extra API column.
        final byte[] source = apiPin.read(8388608, budget);
        if (CapturedDashboardStageDelivery.KIND.equals(text(plan, "kind"))) {
            final var observed =
                    br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                            .CapturedDashboardDeliveryOutcome.verifyPrepared(
                            capture, selectionPin, profile, budget);
            require(
                    observed.path("actualLocalStageReadbackVerified").asBoolean(),
                    "EXPORT_TYPED_STAGE_REAL_READBACK");
            final var stagePin =
                    ExportValidationSupport.Pin.from(observed.required("typedStagePin"));
            final var stage = stagePin.read(8388608, budget);
            require(
                    Arrays.equals(stage, artifactPin.read(8388608, budget))
                            && Arrays.equals(stage, roundtripPin.read(8388608, budget)),
                    "EXPORT_TYPED_STAGE_EXACT_ROUNDTRIP");
        } else {
            require(
                    Arrays.equals(source, artifactPin.read(8388608, budget))
                            && Arrays.equals(source, roundtripPin.read(8388608, budget)),
                    "EXPORT_GOVERNED_EXACT_CAPTURE_ROUNDTRIP");
        }
        uiPin.read(8388608, budget);
        capturePin.read(262144, budget);
        diagnosticPin.read(262144, budget);
        planPin.read(262144, budget);
        selectionPin.read(262144, budget);
        producerPin.read(262144, budget);
        consumerPin.read(262144, budget);
        result.put("status", "COMPLETED_GOVERNED_CAPTURE_DELIVERY_FOR_THIS_INVOCATION")
                .put("governedDeliveryComplete", true)
                .put("deliveryKind", text(plan, "kind"))
                .put("SQLPublicationVerified", false);
        result.set("producerReceiptPin", ExportValidationSupport.JSON.valueToTree(producerPin));
        result.set("consumerReceiptPin", ExportValidationSupport.JSON.valueToTree(consumerPin));
        final var resultPin = write(Path.of(text(plan, "resultPath")), result, budget);
        result.set("resultPin", ExportValidationSupport.JSON.valueToTree(resultPin));
        return result;
    }

    private static ExportValidationSupport.Pin write(
            final Path path, final ObjectNode value, final ExportValidationSupport.Budget budget)
            throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        require(bytes.length <= 262144, "EXPORT_GOVERNED_RESULT_BOUND");
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
