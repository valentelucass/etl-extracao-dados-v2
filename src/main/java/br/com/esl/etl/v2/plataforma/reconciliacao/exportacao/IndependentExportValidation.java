package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.xml.stream.XMLStreamException;

/** Product path for a pinned independently acquired pair. Equality never adopts Main or SQL. */
public final class IndependentExportValidation {
    private static final int CONTROL_BOUND = 262144;

    private IndependentExportValidation() {}

    private record Side(
            ExportValidationSupport.Pin data,
            JsonNode evidence,
            ExportValidationSupport.Pin evidencePin,
            ExportValidationSupport.Pin observationPin) {}

    public static ObjectNode compare(
            final JsonNode manifest, final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        require(
                "INDEPENDENT_EXPORT_PAIR_V1".equals(text(manifest, "schema")),
                "EXPORT_PAIR_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(manifest.required("requestPin"));
        final var configPin = ExportValidationSupport.Pin.from(manifest.required("configPin"));
        final var mappingPin = ExportValidationSupport.Pin.from(manifest.required("mappingPin"));
        final var admissionPin =
                ExportValidationSupport.Pin.from(manifest.required("admissionPin"));
        final var request = requestPin.json(CONTROL_BOUND, budget);
        if (ColetasIndependentRequest.PROFILE.equals(request.path("profile").asText())) {
            try {
                return ColetasIndependentValidation.compare(manifest, budget);
            } catch (final IOException | XMLStreamException failure) {
                throw failure;
            } catch (final RuntimeException failure) {
                throw failure;
            } catch (final Exception failure) {
                throw new IOException("COLETAS_COMPARISON_REFUSED", failure);
            }
        }
        IndependentExportRequest.validate(request);
        final var config = configPin.json(CONTROL_BOUND, budget);
        require(
                "INDEPENDENT_EXPORT_CONFIG_V1".equals(text(config, "schema"))
                        && IndependentExportRequest.PROFILE.equals(text(config, "profile"))
                        && text(config, "zone").equals(text(request, "zone")),
                "EXPORT_CONFIG_BINDING");
        final int skew = config.required("maximumRequestSeparationSeconds").intValue();
        require(
                config.get("maximumRequestSeparationSeconds").isIntegralNumber()
                        && skew > 0
                        && skew <= 300,
                "EXPORT_PROXIMITY_BOUND");
        final var mapping = mappingPin.json(CONTROL_BOUND, budget);
        final var columns = columns(mapping);
        final int key = mapping.required("idColumnIndex").intValue();
        final var admission = admissionPin.json(CONTROL_BOUND, budget);
        require(
                "INDEPENDENT_EXPORT_PAIR_BINDING_V1".equals(text(admission, "schema"))
                        && text(request, "attemptId").equals(text(admission, "attemptId")),
                "EXPORT_ADMISSION_ATTEMPT_BINDING");
        for (final String field : List.of("requestPin", "configPin", "mappingPin", "api", "ui")) {
            require(
                    manifest.required(field).equals(admission.required(field)),
                    "EXPORT_ADMISSION_PAIR_BINDING");
        }
        final var api =
                side(manifest.required("api"), "API_EXPORT_XLSX", requestPin, request, budget);
        final var ui = side(manifest.required("ui"), "UI_EXPORT_XLSX", requestPin, request, budget);
        require(
                !api.data().path().equals(ui.data().path())
                        && !Files.isSameFile(api.data().path(), ui.data().path())
                        && !text(api.evidence(), "fileId").equals(text(ui.evidence(), "fileId"))
                        && !text(api.evidence(), "requestId")
                                .equals(text(ui.evidence(), "requestId"))
                        && !api.evidencePin().equals(ui.evidencePin()),
                "EXPORT_INDEPENDENT_ACQUISITIONS_REQUIRED");
        final var content = content(request, columns, key, api.data(), ui.data(), budget);
        final var comparison = content.comparison();
        final boolean near = nearRequests(api.evidence(), ui.evidence(), skew);
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put("schema", "INDEPENDENT_EXPORT_RESULT_V1");
        result.put("attemptId", text(request, "attemptId"));
        result.put("profile", IndependentExportRequest.PROFILE);
        result.put("commonColumns", 114).put("totalFields", 50);
        result.put("periodStart", text(request, "periodStart"));
        result.put("periodEnd", text(request, "periodEnd"));
        result.put("apiFileId", text(api.evidence(), "fileId"));
        result.put("uiFileId", text(ui.evidence(), "fileId"));
        result.set("requestPin", manifest.required("requestPin"));
        result.set("configPin", manifest.required("configPin"));
        result.set("mappingPin", manifest.required("mappingPin"));
        result.set("admissionPin", manifest.required("admissionPin"));
        result.set("api", manifest.required("api"));
        result.set("ui", manifest.required("ui"));
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        result.set("fullValueProjection", content.fullValueProjection());
        result.set("dashboardMissingColumns", dashboardMissing(config, columns));
        result.put(
                "status",
                comparison.fullTuplesEqual()
                                && comparison.totalsReady().stream().anyMatch(ready -> !ready)
                        ? "PENDING_TOTAL_MODEL_OR_NUMERIC_OBSERVATION"
                        : !comparison.contentEqual()
                                ? "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR"
                                : !near
                                        ? "PENDING_CONTROLLED_NEAR_PAIR"
                                        : "CONTENT_EQUAL_FOR_THIS_PAIR");
        result.put("requestProximityVerified", near);
        result.put("snapshotIdentityProven", false);
        result.put("temporalCausalityProven", false);
        result.put("acquisitionEvidenceAuthenticationVerified", false);
        result.put("independenceBasis", "SEPARATE_PINNED_REQUEST_AND_UI_CLICK_OBSERVATIONS");
        result.put("semanticParityApproved", false);
        result.put("entityQualified", false);
        result.put("mainResumeAllowed", false);
        result.put("dashboardConsumptionVerified", false);
        result.set("dashboardBridge", IndependentDashboardBridge.contract());
        result.put("attemptId", text(request, "attemptId"));
        if (manifest.has("dashboardInputRecipePin") && comparison.contentEqual() && near) {
            require(
                    ExportValidationSupport.Pin.from(manifest.required("dashboardInputRecipePin"))
                            .equals(
                                    ExportValidationSupport.Pin.from(
                                            admission.required("dashboardInputRecipePin"))),
                    "EXPORT_DASHBOARD_RECIPE_PAIR_BINDING");
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
        if (manifest.has("dashboardBridgePin") && comparison.contentEqual() && near) {
            require(
                    manifest.required("dashboardBridgePin")
                            .equals(admission.required("dashboardBridgePin")),
                    "EXPORT_DASHBOARD_PLAN_PAIR_BINDING");
            try {
                result.set("dashboardBridge", IndependentDashboardBridge.prepare(manifest, budget));
            } catch (final Exception incomplete) {
                result.put("dashboardBridgeStatus", "PENDING_GOVERNED_TYPED_STAGE");
            }
        }
        result.put("dashboardContractVerifiedAgainstConsumer", false);
        result.put("dashboardColumnCoverageBasis", "PINNED_CONFIGURATION_DECLARATION");
        result.put("deduplicationPerformed", false);
        result.put("identityGrouping", "EXACT_TYPED_VALUE_WITH_OCCURRENCES_PRESERVED");
        result.put("Source", 0);
        result.put("HTTP", 0);
        result.put("SQL", 0);
        // Recheck all inputs after both passes. A partial pass or pin drift emits no result.
        for (final var pin :
                List.of(
                        requestPin,
                        configPin,
                        mappingPin,
                        admissionPin,
                        api.evidencePin(),
                        ui.evidencePin(),
                        api.observationPin(),
                        ui.observationPin(),
                        api.data(),
                        ui.data())) {
            pin.read(8388608, budget);
        }
        return result;
    }

    // One kernel for operational comparison and explicitly historical Source0 comparison.
    // The normal caller above retains every acquisition/near<=300/admission guard unchanged.
    record Content(ExactExportTable.Comparison comparison, ObjectNode fullValueProjection) {}

    static Content content(
            final JsonNode request,
            final List<ExactExportTable.Column> columns,
            final int key,
            final ExportValidationSupport.Pin api,
            final ExportValidationSupport.Pin ui,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        final var apiBag = new ExactExportTable(columns, key, budget);
        final var uiBag = new ExactExportTable(columns, key, budget);
        final var periodStart = LocalDate.parse(text(request, "periodStart"));
        final var periodEnd = LocalDate.parse(text(request, "periodEnd"));
        BoundedExportWorkbook.project(
                api.read(8388608, budget), columns, true, apiBag, budget, periodStart, periodEnd);
        BoundedExportWorkbook.project(
                ui.read(8388608, budget), columns, false, uiBag, budget, periodStart, periodEnd);
        final var comparison = apiBag.compare(uiBag);
        final var projection = ExportValidationSupport.JSON.createObjectNode();
        projection.set("api", apiBag.fullValueProjection());
        projection.set("ui", uiBag.fullValueProjection());
        return new Content(comparison, projection);
    }

    public static List<ExactExportTable.Column> columns(final JsonNode mapping) throws IOException {
        ExportColumnCatalog.verify(mapping);
        require(
                "INDEPENDENT_EXPORT_COLUMNS_V1".equals(text(mapping, "schema"))
                        && mapping.path("reportId").asInt() == 6389,
                "EXPORT_MAPPING_SCHEMA");
        final var entries = mapping.required("columns");
        require(entries.isArray() && entries.size() == 114, "EXPORT_FRETES_114_COLUMNS_REQUIRED");
        final List<ExactExportTable.Column> columns = new ArrayList<>();
        final var names = new HashSet<String>();
        final var sourceIndexes = new HashSet<Integer>();
        final var referenceIndexes = new HashSet<Integer>();
        int totalCount = 0;
        for (final var entry : entries) {
            require(entry.required("total").isBoolean(), "EXPORT_TOTAL_DECLARATION");
            final var column =
                    new ExactExportTable.Column(
                            text(entry, "name"),
                            entry.required("apiColumn").intValue(),
                            entry.required("uiColumn").intValue(),
                            entry.get("total").booleanValue(),
                            text(entry, "totalKind"));
            require(
                    names.add(column.name())
                            && sourceIndexes.add(column.apiColumn())
                            && referenceIndexes.add(column.uiColumn()),
                    "EXPORT_MAPPING_DUPLICATE");
            totalCount += column.total() ? 1 : 0;
            columns.add(column);
        }
        require(
                totalCount == 50 && referenceIndexes.stream().allMatch(index -> index <= 114),
                "EXPORT_FRETES_50_TOTALS_ALL_COLUMNS");
        return List.copyOf(columns);
    }

    private static Side side(
            final JsonNode descriptor,
            final String route,
            final ExportValidationSupport.Pin requestPin,
            final JsonNode request,
            final ExportValidationSupport.Budget budget)
            throws IOException {
        final var data = ExportValidationSupport.Pin.from(descriptor.required("dataPin"));
        final var evidencePin =
                ExportValidationSupport.Pin.from(descriptor.required("evidencePin"));
        final var evidence = evidencePin.json(CONTROL_BOUND, budget);
        require(
                "INDEPENDENT_EXPORT_ACQUISITION_V1".equals(text(evidence, "schema"))
                        && route.equals(text(evidence, "route"))
                        && text(evidence, "attemptId").equals(text(request, "attemptId"))
                        && text(evidence, "requestSha256").equals(requestPin.sha256())
                        && descriptor.required("dataPin").equals(evidence.required("dataPin")),
                "EXPORT_ACQUISITION_BINDING");
        require(
                IndependentExportRequest.filtersEquivalent(
                                evidence.required("filters"), request.required("filters"))
                        && text(evidence, "periodStart").equals(text(request, "periodStart"))
                        && text(evidence, "periodEnd").equals(text(request, "periodEnd"))
                        && evidence.path("reportId").asInt() == request.path("reportId").asInt()
                        && text(evidence, "zone").equals(text(request, "zone")),
                "EXPORT_OBSERVED_FILTER_SCOPE_BINDING");
        require(
                text(evidence, "cycleId").equals(text(request, "attemptId")),
                "EXPORT_SAME_CYCLE_REQUIRED");
        require(
                text(evidence, "fileId").matches("[A-Za-z0-9._:-]{1,128}")
                        && text(evidence, "requestId").matches("[A-Za-z0-9._:-]{1,128}"),
                "EXPORT_SAFE_IDS");
        final var observationPin =
                ExportValidationSupport.Pin.from(evidence.required("observationPin"));
        final var observation = observationPin.json(CONTROL_BOUND, budget);
        require(
                IndependentExportRequest.filtersEquivalent(
                                observation.required("filters"), evidence.required("filters"))
                        && text(observation, "requestId").equals(text(evidence, "requestId"))
                        && text(observation, "fileId").equals(text(evidence, "fileId"))
                        && text(observation, "attemptId").equals(text(request, "attemptId"))
                        && text(observation, "requestedAt").equals(text(evidence, "requestedAt"))
                        && text(observation, "zone").equals(text(evidence, "zone"))
                        && text(observation, "timePrecision")
                                .equals(text(evidence, "timePrecision"))
                        && text(observation, "event")
                                .equals(
                                        route.startsWith("UI")
                                                ? "UI_EXPORT_CLICK"
                                                : "API_EXPORT_POST"),
                "EXPORT_FILTER_TO_REQUEST_EVENT_BINDING");
        require(
                "SECOND".equals(text(evidence, "timePrecision"))
                        || "MINUTE".equals(text(evidence, "timePrecision")),
                "EXPORT_TIME_PRECISION");
        observedTime(evidence);
        observationPin.read(CONTROL_BOUND, budget);
        return new Side(data, evidence, evidencePin, observationPin);
    }

    private static OffsetDateTime observedTime(final JsonNode side) {
        final var time = OffsetDateTime.parse(text(side, "requestedAt"));
        require(
                ZoneId.of(text(side, "zone"))
                        .getRules()
                        .getOffset(time.toInstant())
                        .equals(time.getOffset()),
                "EXPORT_TIMEZONE_OFFSET");
        if ("MINUTE".equals(text(side, "timePrecision"))) {
            require(time.getSecond() == 0 && time.getNano() == 0, "EXPORT_MINUTE_INTERVAL_START");
        }
        return time;
    }

    private static boolean nearRequests(final JsonNode api, final JsonNode ui, final int maximum) {
        final var left = observedTime(api).toInstant();
        final var right = observedTime(ui).toInstant();
        // Compare the entire uncertainty intervals; equal displayed minutes are not snapshots.
        final long leftWidth = "MINUTE".equals(text(api, "timePrecision")) ? 60 : 1;
        final long rightWidth = "MINUTE".equals(text(ui, "timePrecision")) ? 60 : 1;
        final var first = left.isBefore(right) ? left : right;
        final var last =
                left.plusSeconds(leftWidth).isAfter(right.plusSeconds(rightWidth))
                        ? left.plusSeconds(leftWidth)
                        : right.plusSeconds(rightWidth);
        return Duration.between(first, last).compareTo(Duration.ofSeconds(maximum)) <= 0;
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode dashboardMissing(
            final JsonNode config, final List<ExactExportTable.Column> columns) {
        final var declared = config.required("dashboardColumns");
        require(declared.isArray(), "EXPORT_DASHBOARD_CONTRACT_REQUIRED");
        final var available = new HashSet<String>();
        declared.forEach(
                value -> {
                    require(value.isTextual(), "EXPORT_DASHBOARD_COLUMN");
                    available.add(value.textValue());
                });
        final var missing = ExportValidationSupport.JSON.createArrayNode();
        columns.stream()
                .filter(column -> !available.contains(column.name()))
                .forEach(column -> missing.add(column.name()));
        return missing;
    }
}
