package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** Same pair contract as Fretes, with independent Data Export JSONL and native6908 XLSX. */
public final class ColetasIndependentValidation {
    private ColetasIndependentValidation() {}

    public static ObjectNode compare(
            final JsonNode manifest, final ExportValidationSupport.Budget budget) throws Exception {
        final var requestPin = ExportValidationSupport.Pin.from(manifest.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        ColetasIndependentRequest.validate(request);
        final var configPin = ExportValidationSupport.Pin.from(manifest.required("configPin"));
        final var mappingPin = ExportValidationSupport.Pin.from(manifest.required("mappingPin"));
        final var policyPin = ExportValidationSupport.Pin.from(manifest.required("policyPin"));
        final var admissionPin =
                ExportValidationSupport.Pin.from(manifest.required("admissionPin"));
        final var config = configPin.json(262144, budget);
        final var mapping = mappingPin.json(262144, budget);
        require(
                "INDEPENDENT_EXPORT_PAIR_V1".equals(text(manifest, "schema"))
                        && "INDEPENDENT_EXPORT_CONFIG_V1".equals(text(config, "schema"))
                        && ColetasIndependentRequest.PROFILE.equals(text(config, "profile"))
                        && text(config, "zone").equals(text(request, "zone"))
                        && mapping.equals(ColetasCommonAdapter.catalog()),
                "COLETAS_PAIR_CONFIG_CATALOG");
        final int skew = config.path("maximumRequestSeparationSeconds").asInt();
        require(
                config.path("maximumRequestSeparationSeconds").isIntegralNumber()
                        && skew > 0
                        && skew <= 300,
                "COLETAS_PROXIMITY_BOUND");
        final var admission = admissionPin.json(262144, budget);
        require(
                "INDEPENDENT_EXPORT_PAIR_BINDING_V1".equals(text(admission, "schema"))
                        && text(request, "attemptId").equals(text(admission, "attemptId")),
                "COLETAS_PAIR_BINDING");
        for (final String field :
                List.of("requestPin", "configPin", "mappingPin", "policyPin", "api", "ui")) {
            require(
                    manifest.required(field).equals(admission.required(field)),
                    "COLETAS_EXACT_PAIR_ADMISSION");
        }
        final var api = manifest.required("api");
        final var ui = manifest.required("ui");
        final var sourcePin = ExportValidationSupport.Pin.from(api.required("dataPin"));
        final var nativePin = ExportValidationSupport.Pin.from(ui.required("dataPin"));
        final var apiEvidence = evidence(api, "API_DATA_JSONL", requestPin, request, budget);
        final var uiEvidence = evidence(ui, "UI_EXPORT_XLSX", requestPin, request, budget);
        require(
                !sourcePin.path().equals(nativePin.path())
                        && !Files.isSameFile(sourcePin.path(), nativePin.path())
                        && !text(apiEvidence, "fileId").equals(text(uiEvidence, "fileId"))
                        && !text(apiEvidence, "requestId").equals(text(uiEvidence, "requestId"))
                        && !api.required("evidencePin").equals(ui.required("evidencePin")),
                "COLETAS_INDEPENDENT_CAPTURE_AND_CLICK");
        final var result =
                ColetasCommonAdapter.compare(request, sourcePin, nativePin, policyPin, budget);
        final boolean near = near(apiEvidence, uiEvidence, skew);
        result.put("attemptId", text(request, "attemptId"));
        if (manifest.has("dashboardInputRecipePin")
                && near
                && "CONTENT_EQUAL_FOR_THIS_PAIR".equals(result.path("status").asText())) {
            require(
                    ExportValidationSupport.Pin.from(manifest.required("dashboardInputRecipePin"))
                            .equals(
                                    ExportValidationSupport.Pin.from(
                                            admission.required("dashboardInputRecipePin"))),
                    "COLETAS_DASHBOARD_RECIPE_PAIR_BINDING");
            try {
                result.set(
                        "dashboardBridge",
                        CapturedDashboardInputProducer.produceAndCheck(manifest, budget));
            } catch (final Exception incomplete) {
                result.put("dashboardBridgeStatus", "PENDING_CAPTURE_GOVERNED_TYPED_STAGE");
                final String code = incomplete.getMessage();
                final var pending = IndependentDashboardBridge.contract();
                pending.put(
                        "reasonCode",
                        code != null && code.matches("[A-Z][A-Z0-9_]{0,119}")
                                ? code
                                : "DASHBOARD_PRIVATE_FAILURE_DETAILS_WITHHELD");
                result.set("dashboardBridge", pending);
            }
        }
        if (manifest.has("dashboardBridgePin")
                && near
                && "CONTENT_EQUAL_FOR_THIS_PAIR".equals(result.path("status").asText())) {
            require(
                    manifest.required("dashboardBridgePin")
                            .equals(admission.required("dashboardBridgePin")),
                    "COLETAS_DASHBOARD_PLAN_PAIR_BINDING");
            try {
                result.set("dashboardBridge", IndependentDashboardBridge.prepare(manifest, budget));
            } catch (final Exception incomplete) {
                result.put("dashboardBridgeStatus", "PENDING_GOVERNED_TYPED_STAGE");
            }
        }
        if ("CONTENT_EQUAL_FOR_THIS_PAIR".equals(result.path("status").asText()) && !near) {
            result.put("status", "PENDING_CONTROLLED_NEAR_PAIR");
        }
        result.put("schema", "INDEPENDENT_EXPORT_RESULT_V1");
        result.put("profile", ColetasIndependentRequest.PROFILE);
        result.put("periodStart", text(request, "periodStart"))
                .put("periodEnd", text(request, "periodEnd"));
        result.put("apiFileId", text(apiEvidence, "fileId"))
                .put("uiFileId", text(uiEvidence, "fileId"));
        result.put("requestProximityVerified", near).put("temporalCausalityProven", false);
        result.put("commonColumns", 31).put("totalFields", 8);
        result.put("acquisitionEvidenceAuthenticationVerified", false)
                .put("entityQualified", false);
        result.put("Source", 0).put("HTTP", 0).put("SQL", 0);
        for (final String field :
                List.of(
                        "requestPin",
                        "configPin",
                        "mappingPin",
                        "policyPin",
                        "admissionPin",
                        "api",
                        "ui")) {
            result.set(field, manifest.required(field));
        }
        for (final var pin :
                List.of(
                        requestPin,
                        configPin,
                        mappingPin,
                        policyPin,
                        admissionPin,
                        sourcePin,
                        nativePin)) {
            pin.read(8388608, budget);
        }
        return result;
    }

    private static JsonNode evidence(
            final JsonNode descriptor,
            final String route,
            final ExportValidationSupport.Pin requestPin,
            final JsonNode request,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var pin = ExportValidationSupport.Pin.from(descriptor.required("evidencePin"));
        final var evidence = pin.json(262144, budget);
        require(
                "INDEPENDENT_EXPORT_ACQUISITION_V1".equals(text(evidence, "schema"))
                        && route.equals(text(evidence, "route"))
                        && text(request, "attemptId").equals(text(evidence, "attemptId"))
                        && requestPin.sha256().equals(text(evidence, "requestSha256"))
                        && descriptor.required("dataPin").equals(evidence.required("dataPin"))
                        && ColetasIndependentRequest.sameFilters(
                                request.required("filters"), evidence.required("filters"))
                        && text(request, "periodStart").equals(text(evidence, "periodStart"))
                        && text(request, "periodEnd").equals(text(evidence, "periodEnd"))
                        && text(request, "zone").equals(text(evidence, "zone"))
                        && evidence.path("reportId").asInt() == 6908
                        && text(request, "attemptId").equals(text(evidence, "cycleId")),
                "COLETAS_CAPTURE_SCOPE");
        require(
                text(evidence, "fileId").matches("[A-Za-z0-9._:-]{1,128}")
                        && text(evidence, "requestId").matches("[A-Za-z0-9._:-]{1,128}"),
                "COLETAS_CAPTURE_IDS");
        final var observationPin =
                ExportValidationSupport.Pin.from(evidence.required("observationPin"));
        final var observation = observationPin.json(262144, budget);
        for (final String field :
                List.of("requestId", "fileId", "requestedAt", "zone", "timePrecision")) {
            require(
                    evidence.required(field).equals(observation.required(field)),
                    "COLETAS_OBSERVATION_BINDING");
        }
        require(
                text(request, "attemptId").equals(text(observation, "attemptId"))
                        && ColetasIndependentRequest.sameFilters(
                                evidence.required("filters"), observation.required("filters"))
                        && (route.startsWith("UI") ? "UI_EXPORT_CLICK" : "API_DATA_INTERVAL_START")
                                .equals(text(observation, "event")),
                "COLETAS_ACTUAL_CAPTURE_EVENT");
        observed(evidence);
        observationPin.read(262144, budget);
        pin.read(262144, budget);
        return evidence;
    }

    private static OffsetDateTime observed(final JsonNode evidence) {
        final var time = OffsetDateTime.parse(text(evidence, "requestedAt"));
        require(
                ZoneId.of(text(evidence, "zone"))
                        .getRules()
                        .getOffset(time.toInstant())
                        .equals(time.getOffset()),
                "COLETAS_TIMEZONE");
        final String precision = text(evidence, "timePrecision");
        require("SECOND".equals(precision) || "MINUTE".equals(precision), "COLETAS_TIME_PRECISION");
        require(
                !"MINUTE".equals(precision) || time.getSecond() == 0 && time.getNano() == 0,
                "COLETAS_MINUTE_INTERVAL");
        return time;
    }

    private static boolean near(final JsonNode left, final JsonNode right, final int maximum) {
        final var a = observed(left).toInstant();
        final var b = observed(right).toInstant();
        final var aEnd = a.plusSeconds("MINUTE".equals(text(left, "timePrecision")) ? 60 : 1);
        final var bEnd = b.plusSeconds("MINUTE".equals(text(right, "timePrecision")) ? 60 : 1);
        return Duration.between(a.isBefore(b) ? a : b, aEnd.isAfter(bEnd) ? aEnd : bEnd)
                        .compareTo(Duration.ofSeconds(maximum))
                <= 0;
    }
}
