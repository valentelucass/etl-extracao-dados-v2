package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Read the actual local stage and join it to the persisted operational receipt; never grants
 * publication.
 */
public final class CapturedDashboardDeliveryOutcome {
    private CapturedDashboardDeliveryOutcome() {}

    public static ObjectNode describe(
            final JsonNode receipt,
            final ExportValidationSupport.Pin expectedSelection,
            final String profile,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var result = verifyPrepared(receipt, expectedSelection, profile, budget);
        if (result.path("actualLocalStageReadbackVerified").asBoolean()) {
            final var selection = expectedSelection.json(262144, budget);
            final var operationalResult = Path.of(text(selection, "resultPath"));
            final var readbackPath =
                    operationalResult.resolveSibling(
                            operationalResult.getFileName() + ".local-stage-readback.json");
            final var readbackPin =
                    CapturedDashboardInputProducer.writeJson(readbackPath, result, 262144, budget);
            result.set(
                    "localReadbackReceiptPin",
                    ExportValidationSupport.JSON.valueToTree(readbackPin));
        }
        return result;
    }

    public static ObjectNode verifyPrepared(
            final JsonNode receipt,
            final ExportValidationSupport.Pin expectedSelection,
            final String profile,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CAPTURED_DASHBOARD_LOCAL_STAGE_READBACK_V1")
                        .put("profile", profile)
                        .put("status", "PENDING_CAPTURE_GOVERNED_INPUT_PRODUCTION")
                        .put("actualLocalStageReadbackVerified", false)
                        .put("governedDeliveryComplete", false)
                        .put("authorityCreated", false)
                        .put("productionPersistencePerformed", false)
                        .put("dashboardConsumptionVerified", false)
                        .put("mainResumeAllowed", false);
        require(
                "INDEPENDENT_EXPORT_OPERATIONAL_EXECUTED_RECEIPT_V1".equals(text(receipt, "schema"))
                        && expectedSelection.equals(
                                ExportValidationSupport.Pin.from(receipt.required("selectionPin"))),
                "DASHBOARD_DELIVERY_OPERATIONAL_SELECTION");
        final var selection = expectedSelection.json(262144, budget);
        require(profile.equals(text(selection, "profile")), "DASHBOARD_DELIVERY_PROFILE");
        final var receiptPin = ExportValidationSupport.Pin.from(receipt.required("receiptPin"));
        final var persistedReceipt = receiptPin.json(262144, budget);
        for (final String field :
                List.of(
                        "schema",
                        "executionId",
                        "attemptId",
                        "status",
                        "startedAt",
                        "finishedAt")) {
            require(
                    receipt.required(field).equals(persistedReceipt.required(field)),
                    "DASHBOARD_DELIVERY_RECEIPT_BINDING");
        }
        for (final String field : List.of("selectionPin", "diagnosticPin", "journalOutcomePin")) {
            samePin(receipt.required(field), persistedReceipt.required(field));
        }
        final var diagnosticPin =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"));
        final var diagnostic = diagnosticPin.json(262144, budget);
        final var journalOutcomePin =
                ExportValidationSupport.Pin.from(receipt.required("journalOutcomePin"));
        journalOutcomePin.json(262144, budget);
        require(
                "CONTENT_EQUAL_FOR_THIS_PAIR".equals(text(receipt, "status"))
                        && "CONTENT_EQUAL_FOR_THIS_PAIR".equals(text(diagnostic, "status"))
                        && text(receipt, "executionId").equals(text(diagnostic, "executionId"))
                        && profile.equals(text(diagnostic, "profile")),
                "DASHBOARD_DELIVERY_REAL_COMPARISON_REQUIRED");
        samePin(receipt.required("journalOutcomePin"), diagnostic.required("journalOutcomePin"));
        samePin(selection.required("requestPin"), diagnostic.required("requestPin"));
        final var requestPin = ExportValidationSupport.Pin.from(selection.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        require(
                text(request, "attemptId").equals(text(receipt, "attemptId")),
                "DASHBOARD_DELIVERY_ATTEMPT");
        final var bridge = diagnostic.path("dashboardBridge");
        if (!bridge.has("producerResultPin")
                || !"TYPED_INPUT_PRODUCED_AND_CONSUMED_PUBLICATION_PENDING"
                        .equals(bridge.path("status").asText())) {
            result.set(
                    "captureComparisonReceiptPin",
                    ExportValidationSupport.JSON.valueToTree(receiptPin));
            result.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
            if (bridge.path("pendingReasonCounts").isObject()) {
                result.set("pendingReasonCounts", bridge.required("pendingReasonCounts"));
            }
            return result;
        }
        final var producerPin =
                ExportValidationSupport.Pin.from(bridge.required("producerResultPin"));
        final var producer = producerPin.json(262144, budget);
        require(
                "TYPED_INPUT_PRODUCED_AND_CONSUMED_PUBLICATION_PENDING"
                                .equals(text(producer, "status"))
                        && text(request, "attemptId").equals(text(producer, "attemptId"))
                        && profile.equals(text(producer, "profile"))
                        && text(selection, "sourceConfigurationSha256")
                                .equals(text(producer, "sourceConfigurationSha256")),
                "DASHBOARD_DELIVERY_PRODUCER_SCOPE");
        samePin(selection.required("requestPin"), producer.required("requestPin"));
        samePin(selection.required("dashboardInputRecipePin"), producer.required("recipePin"));
        samePin(diagnostic.path("api").required("dataPin"), producer.required("apiDataPin"));
        samePin(diagnostic.path("ui").required("dataPin"), producer.required("uiDataPin"));
        samePin(diagnostic.required("admissionPin"), producer.required("pairAdmissionPin"));
        samePin(bridge.required("generatedRowsPin"), producer.required("generatedRowsPin"));
        final var rowsPin = ExportValidationSupport.Pin.from(producer.required("generatedRowsPin"));
        final var stagePin =
                ExportValidationSupport.Pin.from(
                        producer.path("consumerStage").required("stagePin"));
        samePin(
                bridge.path("consumerStage").required("stagePin"),
                producer.path("consumerStage").required("stagePin"));
        final var sourceRows = rows(rowsPin.read(8388608, budget), budget);
        final var actualRows = rows(stagePin.read(8388608, budget), budget);
        require(
                !sourceRows.isEmpty() && sourceRows.size() == actualRows.size(),
                "DASHBOARD_DELIVERY_READBACK_COUNT");
        final boolean coletas = ColetasIndependentRequest.PROFILE.equals(profile);
        require(
                coletas || IndependentExportRequest.PROFILE.equals(profile),
                "DASHBOARD_DELIVERY_ENTITY");
        for (int index = 0; index < sourceRows.size(); index++) {
            budget.check();
            final var input = sourceRows.get(index);
            final var expected =
                    coletas
                            ? IndependentDashboardBridge.coleta(
                                    input.required("source"), input.required("governance"))
                            : IndependentDashboardBridge.frete(
                                    input.required("source"), input.required("governance"));
            final JsonNode expectedJson = ExportValidationSupport.JSON.valueToTree(expected);
            require(
                    java.util.Arrays.equals(
                            ExportValidationSupport.canonicalBytes(expectedJson),
                            ExportValidationSupport.canonicalBytes(actualRows.get(index))),
                    "DASHBOARD_DELIVERY_ALL_TYPED_VALUES");
        }
        // Recheck the source chain as well as the files actually read by this local consumer.
        for (final String field :
                List.of(
                        "capturePin",
                        "apiDataPin",
                        "uiDataPin",
                        "requestPin",
                        "pairAdmissionPin",
                        "governanceInputPin",
                        "recipePin",
                        "generatedBridgePlanPin")) {
            ExportValidationSupport.Pin.from(producer.required(field)).read(8388608, budget);
        }
        for (final var pin :
                List.of(
                        expectedSelection,
                        receiptPin,
                        diagnosticPin,
                        journalOutcomePin,
                        requestPin,
                        producerPin,
                        rowsPin,
                        stagePin)) {
            pin.read(8388608, budget);
        }
        result.put("status", "LOCAL_TYPED_STAGE_READ_BACK_PUBLICATION_PENDING")
                .put("actualLocalStageReadbackVerified", true)
                .put("typedRowsReadBack", actualRows.size())
                .put("executionId", text(receipt, "executionId"))
                .put("attemptId", text(request, "attemptId"));
        result.set(
                "captureComparisonReceiptPin",
                ExportValidationSupport.JSON.valueToTree(receiptPin));
        result.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
        result.set("producerResultPin", ExportValidationSupport.JSON.valueToTree(producerPin));
        result.set("generatedRowsPin", ExportValidationSupport.JSON.valueToTree(rowsPin));
        result.set("typedStagePin", ExportValidationSupport.JSON.valueToTree(stagePin));
        final var operationalResult = Path.of(text(selection, "resultPath"));
        require(
                operationalResult.isAbsolute()
                        && operationalResult.normalize().equals(diagnosticPin.path()),
                "DASHBOARD_DELIVERY_RESULT_PATH");
        return result;
    }

    private static void samePin(final JsonNode left, final JsonNode right) {
        require(
                ExportValidationSupport.Pin.from(left)
                        .equals(ExportValidationSupport.Pin.from(right)),
                "DASHBOARD_DELIVERY_EXACT_PIN_BINDING");
    }

    private static List<JsonNode> rows(
            final byte[] bytes, final ExportValidationSupport.Budget budget) throws Exception {
        require(
                bytes.length > 0 && bytes[bytes.length - 1] == '\n',
                "DASHBOARD_DELIVERY_CLOSED_ROWS");
        final var result = new ArrayList<JsonNode>();
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(
                                                java.nio.charset.CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(
                        !line.isBlank() && line.length() <= 262144 && result.size() < 12000,
                        "DASHBOARD_DELIVERY_ROWS_BOUND");
                try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                    final JsonNode row = ExportValidationSupport.JSON.readTree(parser);
                    require(
                            row != null && row.isObject() && parser.nextToken() == null,
                            "DASHBOARD_DELIVERY_ROW_OBJECT");
                    result.add(row);
                }
            }
        }
        return result;
    }
}
