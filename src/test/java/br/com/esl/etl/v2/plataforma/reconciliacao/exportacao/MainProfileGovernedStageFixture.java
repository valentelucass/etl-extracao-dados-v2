package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.time.Instant;

/**
 * Authored raw references for real Main positive cases; never reads installed data or issues
 * authority.
 */
public final class MainProfileGovernedStageFixture {
    private MainProfileGovernedStageFixture() {}

    public static void configure(final MainProfileCaptureFixture fixture, final String sourceSha)
            throws Exception {
        fixture.useTypedStageCapture();
        final var request = fixture.request;
        final var requestPin = fixture.selection.required("requestPin");
        final var mappingPin = fixture.selection.required("mappingPin");
        final String profile = request.path("profile").asText();
        final boolean coletas = ColetasIndependentRequest.PROFILE.equals(profile);
        final var recipe =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CAPTURED_DASHBOARD_INPUT_RECIPE_V1")
                        .put("profile", profile)
                        .put("sourceConfigurationSha256", sourceSha)
                        .put("grainModel", "UNIQUE_TYPED_SOURCE_ID")
                        .put(
                                "runDirectory",
                                java.nio.file.Path.of(
                                                fixture.selection.path("outputDirectory").asText())
                                        .resolve("typed-stage")
                                        .toString());
        recipe.set("requestPin", requestPin);
        recipe.set("mappingPin", mappingPin);
        recipe.set(
                "consumerContractPin",
                pin(
                        fixture,
                        "consumer-contract.json",
                        CapturedDashboardInputProducer.consumerContract(profile)));
        final var grain =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_GRAIN_BINDING_V1")
                        .put("model", "UNIQUE_TYPED_SOURCE_ID");
        grain.set("requestPin", requestPin);
        grain.set("consumerContractPin", recipe.required("consumerContractPin"));
        recipe.set("grainEvidencePin", pin(fixture, "grain.json", grain));
        final var context =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_RAW_REFERENCE_CONTEXT_V1")
                        .put("profile", profile)
                        .put("sourceConfigurationSha256", sourceSha)
                        .put("idNamespace", coletas ? "DATAEXPORT6908.id" : "DATAEXPORT6389.id");
        context.set("requestPin", requestPin);
        context.set("periodStart", request.required("periodStart"));
        context.set("periodEnd", request.required("periodEnd"));
        final var identity =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DATAEXPORT_LEGACY_IDENTITY_BINDING_V1")
                        .put("sourceNamespace", context.path("idNamespace").asText())
                        .put("sourceConfigurationSha256", sourceSha);
        identity.set("mappingPin", mappingPin);
        context.set("legacyIdentityBindingPin", pin(fixture, "identity.json", identity));
        final var datasets = context.putObject("datasets");
        datasets.set(
                "lifecycle",
                dataset(
                        fixture,
                        "lifecycle",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("sourceKey", "101")
                                .put("excludedAtSource", false)
                                .put("confirmedAbsences", 0)));
        datasets.set(
                "publication",
                dataset(
                        fixture,
                        "publication",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("requestSha256", requestPin.path("sha256").asText())
                                .put("snapshotUtc", Instant.now().toString())
                                .put("eventKind", "ACTUAL_STAGE_PREPARATION_CLOCK")));
        if (coletas) {
            datasets.set(
                    "logisticsRegions",
                    dataset(
                            fixture,
                            "logisticsRegions",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("id", 1)
                                    .put("cep_inicio", "12340000")
                                    .put("cep_fim", "12349999")
                                    .put("regiao_logistica", "SYNTHETIC RAW RULE REGION")));
            datasets.set(
                    "manifestos",
                    dataset(
                            fixture,
                            "manifestos",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("id", 1)
                                    .put("pick_sequence_code", 7)
                                    .put("sequence_code", 9002)
                                    .put("excludedAtSource", false)));
        } else {
            final var raw =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("sourceKey", "101")
                            .put("modal", "SYNTHETIC MODAL");
            raw.putObject("documents")
                    .put("cteId", 301)
                    .put("cteIssuedAt", "2025-03-03T00:00:00-03:00");
            raw.putObject("rawFrete").put("subtotal", new java.math.BigDecimal("100.25"));
            datasets.set("rawFretes", dataset(fixture, "rawFretes", raw));
            datasets.set(
                    "calendar",
                    dataset(
                            fixture,
                            "calendar",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("sourceDay", "2025-03-03")
                                    .put("referenceDay", "2025-02-28")
                                    .put("previousBusinessDaySnapshotVerified", true)));
            datasets.set(
                    "localizacao",
                    dataset(
                            fixture,
                            "localizacao",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("sequenceNumber", 9001)
                                    .put("destinationBranchNickname", "SYNTHETIC BRANCH")
                                    .put("activeJoinSnapshotComplete", true)
                                    .put("excludedAtSource", false)
                                    .put("invoicesVolumes", 3)));
            datasets.set(
                    "rankedFiscal",
                    dataset(
                            fixture,
                            "rankedFiscal",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("cteKey", "SYNTHETICSOURCE")
                                    .put("statusCte", "authorized")
                                    .putNull("statusCteResult")
                                    .put("rankedSnapshotComplete", true)));
            datasets.set(
                    "branchAssignment",
                    dataset(
                            fixture,
                            "branchAssignment",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("pagador_documento_key", "SYNTHETICSOURCE")
                                    .put("ativo", true)
                                    .put("filial_destino_nome", "ASSIGNED BRANCH")));
            final var temporal =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "DASHBOARD_SOURCE_TEMPORAL_BINDING_V1")
                            .put("model", "EXCEL_1900_CIVIL_DAY_AS_DECLARED_LOCAL_MIDNIGHT_V1")
                            .put("sourceSemanticType", "CIVIL_DATE")
                            .put("sourceField", "service_at")
                            .put("capturedPrecision", "DAY")
                            .put("derivedTargetRepresentation", "DECLARED_LOCAL_MIDNIGHT")
                            .put("targetZone", "America/Sao_Paulo")
                            .put("sourceClockObserved", false)
                            .put("nativeCarrier", "XLSX:1900:builtin:0")
                            .put("sourceConfigurationSha256", sourceSha)
                            .put("profile", profile);
            temporal.set("requestPin", requestPin);
            temporal.set("mappingPin", mappingPin);
            final var service = recipe.putObject("nativeSourceBindings").putObject("serviceAt");
            service.set("model", temporal.required("model"));
            service.set("evidencePin", pin(fixture, "temporal.json", temporal));
        }
        recipe.set("referenceContextPin", pin(fixture, "raw-reference-context.json", context));
        fixture.selection.set(
                "dashboardInputRecipePin", pin(fixture, "dashboard-recipe.json", recipe));
    }

    private static JsonNode dataset(
            final MainProfileCaptureFixture fixture, final String kind, final ObjectNode... rows)
            throws Exception {
        final var data =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_RAW_REFERENCE_DATASET_V1")
                        .put("kind", kind)
                        .put("completeForDeclaredJoinScope", true);
        data.set("requestPin", fixture.selection.required("requestPin"));
        final var records = data.putArray("records");
        for (final var row : rows) {
            records.add(row);
        }
        return pin(fixture, "reference-" + kind + ".json", data);
    }

    private static JsonNode pin(
            final MainProfileCaptureFixture fixture, final String name, final JsonNode value)
            throws Exception {
        final var path = fixture.directory.resolve(name);
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        final var pin =
                new ExportValidationSupport.Pin(
                        path, ExportValidationSupport.sha256(bytes), bytes.length);
        return ExportValidationSupport.JSON.readTree(
                ExportValidationSupport.JSON.writeValueAsBytes(pin));
    }
}
