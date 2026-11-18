package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit Source0 content replay of an exact historically accepted catalog pair. It never creates
 * acquisition observations or substitutes for the operational comparison.
 */
public final class HistoricalExportContentValidation {
    private HistoricalExportContentValidation() {}

    public static ObjectNode compare(
            final JsonNode manifest, final ExportValidationSupport.Budget budget) throws Exception {
        require(
                "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_PAIR_V1".equals(text(manifest, "schema"))
                        && manifest.path("historicalContentOnly").isBoolean()
                        && manifest.path("historicalContentOnly").asBoolean()
                        && !manifest.has("dashboardBridgePin")
                        && !manifest.has("acquisitionOverride")
                        && !manifest.has("dashboardInputRecipePin")
                        && !manifest.has("governedDeliveryPlanPin")
                        && !manifest.path("mainResumeAllowed").asBoolean()
                        && !manifest.path("operationalAcceptanceIssued").asBoolean(),
                "HISTORICAL_CONTENT_ONLY_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(manifest.required("requestPin"));
        final var configPin = ExportValidationSupport.Pin.from(manifest.required("configPin"));
        final var mappingPin = ExportValidationSupport.Pin.from(manifest.required("mappingPin"));
        final var admissionPin =
                ExportValidationSupport.Pin.from(manifest.required("admissionPin"));
        final var catalogPin = ExportValidationSupport.Pin.from(manifest.required("catalogPin"));
        final var request = requestPin.json(262144, budget);
        final Set<String> requestKeys = new HashSet<>();
        request.fieldNames().forEachRemaining(requestKeys::add);
        require(
                requestKeys.equals(
                                Set.of(
                                        "schema",
                                        "replayId",
                                        "pairId",
                                        "profile",
                                        "entity",
                                        "reportId",
                                        "zone",
                                        "periodStart",
                                        "periodEnd",
                                        "filters"))
                        && "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_REQUEST_V1"
                                .equals(text(request, "schema")),
                "HISTORICAL_NO_FABRICATED_ACQUISITION_EVENT");
        UUID.fromString(text(request, "replayId"));
        final var catalog = catalogPin.json(262144, budget);
        require(
                "ROOT_JAVA_REALPAIR_CATALOG0725_V3_FINAL".equals(text(catalog, "schema")),
                "HISTORICAL_CATALOG_SCHEMA");
        JsonNode selected = null;
        for (final var entry : catalog.required("pairs")) {
            if (text(request, "pairId").equals(text(entry, "pairId"))) {
                require(selected == null, "HISTORICAL_UNIQUE_CATALOG_PAIR");
                selected = entry;
            }
        }
        require(
                selected != null
                        && selected.path("historicalContentAccepted").isBoolean()
                        && selected.path("historicalContentAccepted").asBoolean()
                        && selected.path("replayPreparationBlocked").isBoolean()
                        && !selected.path("replayPreparationBlocked").asBoolean(),
                "HISTORICAL_REJECTED_PAIR_NOT_ADMITTED");
        final boolean coletas = "COLETAS".equals(text(selected, "entity"));
        require(coletas || "FRETES".equals(text(selected, "entity")), "HISTORICAL_ENTITY_SCOPE");
        final String profile =
                coletas ? ColetasIndependentRequest.PROFILE : IndependentExportRequest.PROFILE;
        require(
                profile.equals(text(request, "profile"))
                        && text(request, "entity").equals(text(selected, "entity"))
                        && request.path("reportId").asInt() == selected.path("reportId").asInt()
                        && text(request, "periodStart").equals(text(selected, "periodStart"))
                        && text(request, "periodEnd").equals(text(selected, "periodEnd"))
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "HISTORICAL_EXACT_PERIOD_PROFILE");
        // Only a content-comparison context UUID. It is never emitted as an API/UI cycle/event.
        final ObjectNode coreRequest = ((ObjectNode) request).deepCopy();
        coreRequest.remove(List.of("replayId", "pairId"));
        coreRequest
                .put("attemptId", text(request, "replayId"))
                .put(
                        "schema",
                        coletas
                                ? "COLETAS_INDEPENDENT_REQUEST_V1"
                                : "INDEPENDENT_EXPORT_REQUEST_V1");
        if (coletas) {
            ColetasIndependentRequest.validate(coreRequest);
        } else {
            IndependentExportRequest.validate(coreRequest);
        }
        final var binding = manifest.required("catalogBinding");
        require(
                "ROOT_JAVA_REALPAIR_PRODUCT_CATALOG_BINDING0728_V1".equals(text(binding, "schema"))
                        && text(request, "pairId").equals(text(binding, "pairId"))
                        && rootPin(binding.required("pairCatalog")).equals(catalogPin),
                "HISTORICAL_CATALOG_BINDING");
        final var sourceExpected =
                rootPin(
                        coletas
                                ? selected.path("businessPins")
                                        .required("projectedSourceMappedJsonl")
                                : selected.path("api").required("workbookPin"));
        final var uiExpected =
                rootPin(
                        coletas
                                ? selected.path("businessPins").required("excel")
                                : selected.path("ui").required("workbookPin"));
        final var api = manifest.required("api");
        final var ui = manifest.required("ui");
        require(
                api.isObject() && api.size() == 1 && ui.isObject() && ui.size() == 1,
                "HISTORICAL_NO_ACQUISITION_CLAIM_IN_DATA_DESCRIPTOR");
        final var sourcePin = ExportValidationSupport.Pin.from(api.required("dataPin"));
        final var uiPin = ExportValidationSupport.Pin.from(ui.required("dataPin"));
        require(
                sourcePin.equals(sourceExpected)
                        && uiPin.equals(uiExpected)
                        && rootPin(binding.required("sourceRootPin")).equals(sourceExpected)
                        && rootPin(binding.required("uiRootPin")).equals(uiExpected)
                        && !sourcePin.path().equals(uiPin.path())
                        && !Files.isSameFile(sourcePin.path(), uiPin.path()),
                "HISTORICAL_EXACT_CATALOG_API_AND_UI");
        final var admission = admissionPin.json(262144, budget);
        require(
                "INDEPENDENT_EXPORT_HISTORICAL_PAIR_BINDING_V1".equals(text(admission, "schema"))
                        && text(request, "replayId").equals(text(admission, "replayId")),
                "HISTORICAL_PAIR_ADMISSION");
        for (final String field :
                List.of(
                        "requestPin",
                        "configPin",
                        "mappingPin",
                        "catalogPin",
                        "api",
                        "ui",
                        "catalogBinding")) {
            require(
                    manifest.required(field).equals(admission.required(field)),
                    "HISTORICAL_EXACT_PAIR_ADMISSION");
        }
        final var config = configPin.json(262144, budget);
        require(
                "INDEPENDENT_EXPORT_HISTORICAL_CONFIG_V1".equals(text(config, "schema"))
                        && config.path("historicalContentOnly").asBoolean()
                        && profile.equals(text(config, "profile"))
                        && text(request, "zone").equals(text(config, "zone"))
                        && !config.has("maximumRequestSeparationSeconds")
                        && !config.has("nearOverride"),
                "HISTORICAL_CONFIG_NOT_OPERATIONAL_NEAR_OVERRIDE");
        final var mapping = mappingPin.json(262144, budget);
        final ObjectNode result;
        if (coletas) {
            require(
                    mapping.equals(ColetasCommonAdapter.catalog()),
                    "HISTORICAL_QUALIFIED31_MAPPING");
            require(
                    manifest.required("policyPin").equals(admission.required("policyPin")),
                    "HISTORICAL_POLICY_ADMISSION");
            final var policyPin = ExportValidationSupport.Pin.from(manifest.required("policyPin"));
            final var policy = policyPin.json(262144, budget);
            require(
                    policy.required("catalogOrigins")
                            .equals(binding.required("historicalEvidence")),
                    "HISTORICAL_POLICY_QUALIFIED_ORIGINS");
            final var qualificationPin =
                    ExportValidationSupport.Pin.from(policy.required("qualificationPin"));
            require(
                    qualificationPin.equals(rootPin(selected.required("pythonAcceptance"))),
                    "HISTORICAL_QUALIFICATION_EXACT");
            final var qualification = qualificationPin.json(262144, budget);
            require(
                    qualification.path("accepted").asBoolean()
                            && qualification.path("periodQualified").asBoolean()
                            && qualification.path("all31AssociatedTogether").asBoolean()
                            && qualification.path("mechanismCases").asInt() == 131
                            && text(request, "periodStart")
                                    .equals(qualification.path("scope").path("from").asText())
                            && text(request, "periodEnd")
                                    .equals(
                                            qualification
                                                    .path("scope")
                                                    .path("toInclusive")
                                                    .asText()),
                    "HISTORICAL_ORIGINAL_131_ALL31_QUALIFIED_POLICY");
            HistoricalContentControlPreparation.requireQualifiedPolicy(policy, selected, budget);
            // Includes qualified source codec/header, carriers and per-field presentation rules.
            // It does not parse a RAW API body or substitute a different source artifact.
            result = ColetasCommonAdapter.compare(coreRequest, sourcePin, uiPin, policyPin, budget);
            result.set("policyPin", manifest.required("policyPin"));
            result.put(
                    "status",
                    "CONTENT_EQUAL_FOR_THIS_PAIR".equals(result.path("status").asText())
                            ? "HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR"
                            : "HISTORICAL_CONTENT_PENDING_OR_DIFFERENT");
        } else {
            final var content =
                    IndependentExportValidation.content(
                            coreRequest,
                            IndependentExportValidation.columns(mapping),
                            mapping.required("idColumnIndex").intValue(),
                            sourcePin,
                            uiPin,
                            budget);
            result = ExportValidationSupport.JSON.createObjectNode();
            result.set(
                    "comparison", ExportValidationSupport.JSON.valueToTree(content.comparison()));
            result.set("fullValueProjection", content.fullValueProjection());
            result.put(
                    "status",
                    content.comparison().contentEqual()
                            ? "HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR"
                            : "HISTORICAL_CONTENT_PENDING_OR_DIFFERENT");
        }
        result.put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_RESULT_V1")
                .put("replayId", text(request, "replayId"))
                .put("pairId", text(request, "pairId"))
                .put("profile", profile)
                .put("periodStart", text(request, "periodStart"))
                .put("periodEnd", text(request, "periodEnd"))
                .put("commonColumns", coletas ? 31 : 114)
                .put("totalFields", coletas ? 8 : 50)
                .put("historicalContentOnly", true)
                .put(
                        "acquisitionStatus",
                        "PENDING_CONTROLLED_NEAR_PAIR_NOT_ESTABLISHED_BY_HISTORICAL_CONTENT_REPLAY")
                .put("requestProximityVerified", false)
                .put("acquisitionEvidenceAuthenticationVerified", false)
                .put("snapshotIdentityProven", false)
                .put("temporalCausalityProven", false)
                .put("operationalAcceptanceIssued", false)
                .put("semanticParityApproved", false)
                .put("entityQualified", false)
                .put("mainAdopted", false)
                .put("mainResumeAllowed", false)
                .put("dashboardConsumptionVerified", false)
                .put("deduplicationPerformed", false)
                .put("Source", 0)
                .put("HTTP", 0)
                .put("SQL", 0);
        result.remove("attemptId");
        for (final String field :
                List.of(
                        "requestPin",
                        "configPin",
                        "mappingPin",
                        "admissionPin",
                        "catalogPin",
                        "api",
                        "ui")) {
            result.set(field, manifest.required(field));
        }
        for (final var pin :
                List.of(
                        requestPin,
                        configPin,
                        mappingPin,
                        admissionPin,
                        catalogPin,
                        sourcePin,
                        uiPin)) {
            pin.read(8388608, budget);
        }
        return result;
    }

    /** Root catalog descriptor conversion only; normal Java Pin remains strictly lowercase. */
    private static ExportValidationSupport.Pin rootPin(final JsonNode pin) {
        require(
                pin.isObject()
                        && pin.size() == 3
                        && text(pin, "sha256").matches("[A-F0-9]{64}")
                        && pin.path("bytes").isIntegralNumber(),
                "HISTORICAL_ROOT_UPPERCASE_PIN_REQUIRED");
        return new ExportValidationSupport.Pin(
                Path.of(text(pin, "path")),
                text(pin, "sha256").toLowerCase(Locale.ROOT),
                pin.path("bytes").longValue());
    }
}
