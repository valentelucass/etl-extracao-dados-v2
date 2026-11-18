package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Source0 scope planning and verification of pinned comparison assertions, never collection
 * authority.
 */
public final class ManifestosBoundedWeek {
    public static final String DAILY_PARTITION_POLICY = "DAILY_SEVEN_DISJOINT_SERVICE_DATES";
    private static final Set<String> COMPLETE_FLAGS =
            Set.of(
                    "success",
                    "comparisonPassed",
                    "allCompleteProjectionAxesJavaPythonEqual",
                    "allOccurrencesCompared",
                    "sourceTerminalEmptyPageObserved",
                    "nativeReadToEof");

    private ManifestosBoundedWeek() {}

    /**
     * A fresh nonce is generated for each derived request; the caller must retain this plan before
     * capture.
     */
    public static ObjectNode plan(final JsonNode weeklyRequest) {
        return partition(weeklyRequest, false);
    }

    /**
     * Seven independently bounded daily requests, ordered Monday through Sunday, without changing
     * the parent.
     */
    public static ObjectNode planDaily(final JsonNode weeklyRequest) {
        return partition(weeklyRequest, true);
    }

    private static ObjectNode partition(final JsonNode weeklyRequest, final boolean daily) {
        final LocalDate start = weekStart(weeklyRequest);
        final int partCount = daily ? 7 : 2;
        final var result =
                metadata(weeklyRequest, "MANIFESTOS_BOUNDED_WEEK_PLAN_V1", partCount)
                        .put("plannedCoverageComplete", true)
                        .put("coverageComplete", false)
                        .put("comparisonPassed", false);
        if (daily) {
            result.put("partitionPolicy", DAILY_PARTITION_POLICY);
        }
        final var nonces = new HashSet<String>();
        nonces.add(text(weeklyRequest, "attemptId"));
        final var parts = result.putArray("parts");
        for (int index = 0; index < partCount; index++) {
            final LocalDate first = start.plusDays(daily ? index : index == 0 ? 0 : 3);
            final LocalDate last = daily ? first : start.plusDays(index == 0 ? 2 : 6);
            String nonce;
            do {
                nonce = UUID.randomUUID().toString();
            } while (!nonces.add(nonce));
            final var request = ((ObjectNode) weeklyRequest).deepCopy();
            request.put("periodStart", first.toString())
                    .put("periodEnd", last.toString())
                    .put("attemptId", nonce);
            ((ObjectNode) request.required("filters"))
                    .put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, first + " - " + last);
            ManifestosIndependentRequest.validate(request);
            final var part =
                    parts.addObject()
                            .put("partIndex", index + 1)
                            .put("periodStart", first.toString())
                            .put("periodEnd", last.toString());
            part.set("request", request);
        }
        return result;
    }

    /**
     * Summaries require 91 equal columns and 50 qualified totals, except explicitly proven empty
     * daily scopes. Seven-part evidence additionally requires partIndex 1..7 in chronological
     * order, one exact day each. This verifies supplied comparison evidence; it does not re-read
     * business rows or prove a common snapshot.
     */
    public static ObjectNode verify(final JsonNode weeklyRequest, final JsonNode comparisons) {
        final LocalDate start = weekStart(weeklyRequest);
        require(
                comparisons != null
                        && comparisons.isArray()
                        && (comparisons.size() == 2 || comparisons.size() == 7),
                "MANIFESTOS_WEEK_EXACT_TWO_OR_SEVEN_PARTS");
        final int partCount = comparisons.size();
        final boolean daily = partCount == 7;
        final var seen = new HashSet<Integer>();
        String catalog = null;
        String configuration = null;
        long rows = 0;
        int emptyDays = 0;
        int position = 0;
        final var scopes = ExportValidationSupport.JSON.createArrayNode();
        for (final var part : comparisons) {
            require(part.isObject(), "MANIFESTOS_WEEK_PART_OBJECT");
            final String first = text(part, "periodStart");
            final String last = text(part, "periodEnd");
            final int index;
            if (daily) {
                index = ++position;
                final String day = start.plusDays(index - 1).toString();
                require(
                        number(part, "partIndex") == index && day.equals(first) && day.equals(last),
                        "MANIFESTOS_WEEK_DAILY_EXACT_ORDERED_COVERAGE");
            } else if (start.toString().equals(first)
                    && start.plusDays(2).toString().equals(last)) {
                index = 1;
            } else {
                require(
                        start.plusDays(3).toString().equals(first)
                                && start.plusDays(6).toString().equals(last),
                        "MANIFESTOS_WEEK_EXACT_DISJOINT_COVERAGE");
                index = 2;
            }
            require(seen.add(index), "MANIFESTOS_WEEK_DUPLICATE_PART");
            for (final String flag : COMPLETE_FLAGS) {
                require(
                        part.path(flag).isBoolean() && part.get(flag).booleanValue(),
                        "MANIFESTOS_WEEK_INCOMPLETE_PROOF");
            }
            require(
                    number(part, "columns") == 91
                            && number(part, "equalColumns") == 91
                            && number(part, "totalColumns") == 50,
                    "MANIFESTOS_WEEK_ALL_COLUMNS_AND_TOTALS");
            final long sourceRows = number(part, "sourceRows");
            require(
                    sourceRows <= 12000 && sourceRows == number(part, "uiRows"),
                    "MANIFESTOS_WEEK_ROW_COUNTS");
            final boolean emptyDay = daily && sourceRows == 0;
            if (emptyDay) {
                require(
                        part.path("emptyDayProven").isBoolean()
                                && part.get("emptyDayProven").booleanValue()
                                && part.path("totalComparisonsApplicable").isBoolean()
                                && !part.get("totalComparisonsApplicable").booleanValue()
                                && number(part, "equalTotals") == 0
                                && number(part, "terminalPageIndex") == 1
                                && number(part, "nonemptyPages") == 0,
                        "MANIFESTOS_WEEK_EMPTY_DAY_PROOF");
                emptyDays++;
            } else {
                require(
                        (!part.has("emptyDayProven")
                                        || (part.get("emptyDayProven").isBoolean()
                                                && !part.get("emptyDayProven").booleanValue()))
                                && (!part.has("totalComparisonsApplicable")
                                        || (part.get("totalComparisonsApplicable").isBoolean()
                                                && part.get("totalComparisonsApplicable")
                                                        .booleanValue()))
                                && number(part, "equalTotals") == 50,
                        "MANIFESTOS_WEEK_ALL_COLUMNS_AND_TOTALS");
            }
            require(
                    number(part, "onlyOccurrences") == 0
                            && number(part, "changedRoots") == 0
                            && number(part, "multiplicityRoots") == 0,
                    "MANIFESTOS_WEEK_DIFFERENCES_REMAIN");
            final String partCatalog = digest(part, "catalogSha256");
            final String partConfiguration = digest(part, "sourceConfigurationSha256");
            require(
                    catalog == null || catalog.equals(partCatalog),
                    "MANIFESTOS_WEEK_CATALOG_MISMATCH");
            require(
                    configuration == null || configuration.equals(partConfiguration),
                    "MANIFESTOS_WEEK_CONFIGURATION_MISMATCH");
            catalog = partCatalog;
            configuration = partConfiguration;
            rows = Math.addExact(rows, sourceRows);
            scopes.addObject()
                    .put("partIndex", index)
                    .put("periodStart", first)
                    .put("periodEnd", last)
                    .put("sourceRows", sourceRows)
                    .put("uiRows", sourceRows)
                    .put("emptyDayProven", emptyDay)
                    .put("totalComparisonsApplicable", !emptyDay)
                    .put("equalTotals", emptyDay ? 0 : 50);
        }
        require(
                daily ? seen.size() == 7 : seen.equals(Set.of(1, 2)),
                "MANIFESTOS_WEEK_EXACT_DISJOINT_COVERAGE");
        final var result =
                metadata(weeklyRequest, "MANIFESTOS_BOUNDED_WEEK_VERIFICATION_V1", partCount)
                        .put("success", true)
                        .put("comparisonPassed", true)
                        .put("coverageComplete", true)
                        .put("coveredDays", 7)
                        .put("sourceRows", rows)
                        .put("uiRows", rows)
                        .put("columns", 91)
                        .put("equalColumns", 91)
                        .put("totalColumns", 50)
                        .put("equalTotals", emptyDays == partCount ? 0 : 50)
                        .put("totalComparisonsApplicable", emptyDays != partCount)
                        .put("emptyDayParts", emptyDays)
                        .put("catalogSha256", catalog)
                        .put("sourceConfigurationSha256", configuration)
                        .put("onlyOccurrences", 0)
                        .put("changedRoots", 0)
                        .put("multiplicityRoots", 0)
                        .put("allCompleteProjectionAxesJavaPythonEqual", true)
                        .put("allOccurrencesCompared", true)
                        .put("sourceTerminalEmptyPageObserved", true)
                        .put("nativeReadToEof", true)
                        .put(
                                "equalityProof",
                                "EQUAL_BAGS_UNION_OVER_EXACT_DISJOINT_SERVICE_DATE_SCOPES")
                        .put("globalDistinctIdsSummed", false);
        if (daily) {
            result.put("partitionPolicy", DAILY_PARTITION_POLICY);
        }
        result.set("parts", scopes);
        return result;
    }

    private static LocalDate weekStart(final JsonNode request) {
        ManifestosIndependentRequest.validate(request);
        final LocalDate first = LocalDate.parse(text(request, "periodStart"));
        require(
                first.getDayOfWeek() == DayOfWeek.MONDAY
                        && first.plusDays(6).toString().equals(text(request, "periodEnd")),
                "MANIFESTOS_WEEK_MONDAY_THROUGH_SUNDAY");
        return first;
    }

    private static long number(final JsonNode part, final String field) {
        final var value = part.path(field);
        require(
                value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0,
                "MANIFESTOS_WEEK_NONNEGATIVE_INTEGER_PROOF");
        return value.longValue();
    }

    private static String digest(final JsonNode part, final String field) {
        final String value = text(part, field);
        require(value.matches("[a-fA-F0-9]{64}"), "MANIFESTOS_WEEK_DIGEST");
        return value.toLowerCase(Locale.ROOT);
    }

    private static ObjectNode metadata(
            final JsonNode request, final String schema, final int partCount) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("schema", schema)
                .put("profile", ManifestosIndependentRequest.PROFILE)
                .put("entity", "MANIFESTOS")
                .put("reportId", 6399)
                .put("attemptId", text(request, "attemptId"))
                .put("periodStart", text(request, "periodStart"))
                .put("periodEnd", text(request, "periodEnd"))
                .put("zone", text(request, "zone"))
                .put("partCount", partCount)
                .put(
                        "scopeBindingBasis",
                        "QUALIFIED_REQUEST_SERVICE_DATE_FILTER_AND_PART_SUMMARY_PERIOD")
                .put("serviceDateVerifiedFromRows", false)
                .put("snapshotIdentityProven", false)
                .put("sourceQueryAuthorized", false)
                .put("contentAccepted", false)
                .put("entityQualified", false)
                .put("productionAuthorized", false)
                .put("mainResumeAllowed", false)
                .put("Source", 0)
                .put("HTTP", 0)
                .put("SQL", 0);
    }
}
