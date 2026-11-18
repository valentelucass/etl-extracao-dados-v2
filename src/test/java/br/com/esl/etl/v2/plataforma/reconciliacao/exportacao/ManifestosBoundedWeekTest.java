package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManifestosBoundedWeekTest {
    @Test
    void plansTheThreeCanonicalWeeksAsThreeAndFourDays() {
        for (final String[] dates :
                new String[][] {
                    {"2026-10-12", "2026-10-14", "2026-10-15", "2026-10-18"},
                    {"2026-10-19", "2026-10-21", "2026-10-22", "2026-10-25"},
                    {"2026-10-26", "2026-10-28", "2026-10-29", "2026-11-01"}
                }) {
            final var plan = ManifestosBoundedWeek.plan(week(dates[0]));
            final var parts = plan.required("parts");
            assertEquals(2, parts.size());
            assertEquals(dates[0], parts.get(0).path("periodStart").textValue());
            assertEquals(dates[1], parts.get(0).path("periodEnd").textValue());
            assertEquals(dates[2], parts.get(1).path("periodStart").textValue());
            assertEquals(dates[3], parts.get(1).path("periodEnd").textValue());
            assertTrue(plan.path("plannedCoverageComplete").booleanValue());
            assertFalse(plan.path("coverageComplete").booleanValue());
            assertFalse(plan.path("comparisonPassed").booleanValue());
        }
    }

    @Test
    void generatesFreshDistinctNoncesOnEveryPlan() {
        final var request = week("2026-10-12");
        final var nonces = new HashSet<String>();
        nonces.add(request.path("attemptId").textValue());
        for (int run = 0; run < 2; run++) {
            for (final var part : ManifestosBoundedWeek.plan(request).required("parts")) {
                final String nonce = part.required("request").required("attemptId").textValue();
                assertEquals(nonce, UUID.fromString(nonce).toString());
                assertTrue(nonces.add(nonce));
            }
        }
        assertEquals(5, nonces.size());
    }

    @Test
    void derivedRequestsKeepAllEighteenFiltersAndDoNotMutateParent() {
        final var request = week("2026-10-12");
        final var original = request.deepCopy();
        final var plan = ManifestosBoundedWeek.plan(request);
        for (final var part : plan.required("parts")) {
            final var derived = part.required("request");
            ManifestosIndependentRequest.validate(derived);
            assertEquals(18, derived.required("filters").size());
            assertNotEquals(request.path("attemptId"), derived.path("attemptId"));
            final var restored = ((ObjectNode) derived).deepCopy();
            restored.put("attemptId", request.path("attemptId").textValue())
                    .put("periodStart", request.path("periodStart").textValue())
                    .put("periodEnd", request.path("periodEnd").textValue());
            ((ObjectNode) restored.required("filters"))
                    .set(
                            ManifestosIndependentRequest.SERVICE_DATE_FILTER,
                            request.required("filters")
                                    .required(ManifestosIndependentRequest.SERVICE_DATE_FILTER));
            assertEquals(original, restored);
        }
        ((ObjectNode) plan.required("parts").get(0).required("request").required("filters"))
                .put("manifests[status]", "altered-copy");
        assertEquals(original, request);
    }

    @Test
    void rejectsSevenDaysStartingOnTuesday() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosBoundedWeek.plan(week("2026-10-13")));
    }

    @Test
    void rejectsShorterWeek() {
        final var request = period("2026-10-12", "2026-10-17");
        assertThrows(IllegalArgumentException.class, () -> ManifestosBoundedWeek.plan(request));
    }

    @Test
    void rejectsLongerWeek() {
        final var request = period("2026-10-12", "2026-10-19");
        assertThrows(IllegalArgumentException.class, () -> ManifestosBoundedWeek.plan(request));
    }

    @Test
    void validatesTheIndependentRequestBeforePlanningOrVerifying() {
        final var request = week("2026-10-12");
        final var summaries = summaries(request);
        ((ObjectNode) request.required("filters")).put("manifests[status]", "closed");
        assertThrows(IllegalArgumentException.class, () -> ManifestosBoundedWeek.plan(request));
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosBoundedWeek.verify(request, summaries));
    }

    @Test
    void verifiesMonthBoundaryWithoutTruncatingTheLastDay() {
        final var request = week("2026-10-26");
        final var result = ManifestosBoundedWeek.verify(request, summaries(request));
        assertEquals("2026-11-01", result.path("periodEnd").textValue());
        assertTrue(result.path("coverageComplete").booleanValue());
    }

    @Test
    void plansAndVerifiesAcrossYearBoundary() {
        final var request = week("2026-12-28");
        final var plan = ManifestosBoundedWeek.plan(request);
        assertEquals("2026-12-30", plan.path("parts").get(0).path("periodEnd").textValue());
        assertEquals("2026-12-31", plan.path("parts").get(1).path("periodStart").textValue());
        assertEquals(
                "2027-01-03",
                ManifestosBoundedWeek.verify(request, summaries(request))
                        .path("periodEnd")
                        .textValue());
    }

    @Test
    void completeProofAddsOccurrencesWithoutAddingDistinctIdsOrGrantingAuthority() {
        final var request = week("2026-10-12");
        final var before = request.deepCopy();
        final var comparisons = summaries(request);
        first(comparisons).put("sourceDistinctIds", 10).put("uiDistinctIds", 10);
        ((ObjectNode) comparisons.get(1)).put("sourceDistinctIds", 10).put("uiDistinctIds", 10);
        final var evidenceBefore = comparisons.deepCopy();
        final var result = ManifestosBoundedWeek.verify(request, comparisons);
        assertTrue(result.path("comparisonPassed").booleanValue());
        assertTrue(result.path("coverageComplete").booleanValue());
        assertEquals(7, result.path("coveredDays").intValue());
        assertEquals(30, result.path("sourceRows").longValue());
        assertEquals(30, result.path("uiRows").longValue());
        assertFalse(result.path("globalDistinctIdsSummed").booleanValue());
        assertFalse(result.has("sourceDistinctIds"));
        assertEquals(
                "EQUAL_BAGS_UNION_OVER_EXACT_DISJOINT_SERVICE_DATE_SCOPES",
                result.path("equalityProof").textValue());
        for (final String flag :
                List.of(
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "sourceQueryAuthorized",
                        "mainResumeAllowed",
                        "serviceDateVerifiedFromRows",
                        "snapshotIdentityProven")) {
            assertTrue(result.path(flag).isBoolean());
            assertFalse(result.path(flag).booleanValue());
        }
        for (final String counter : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, result.path(counter).intValue());
        }
        assertEquals(before, request);
        assertEquals(evidenceBefore, comparisons);
    }

    @Test
    void acceptsBothExactPartsInReversedOrder() {
        final var request = week("2026-10-12");
        final var parts = summaries(request);
        final var reverse =
                ExportValidationSupport.JSON.createArrayNode().add(parts.get(1)).add(parts.get(0));
        assertTrue(
                ManifestosBoundedWeek.verify(request, reverse)
                        .path("comparisonPassed")
                        .booleanValue());
    }

    @Test
    void rejectsGapBetweenParts() {
        final var request = week("2026-10-12");
        final var parts = summaries(request);
        ((ObjectNode) parts.get(1)).put("periodStart", "2026-10-16");
        refused(request, parts);
    }

    @Test
    void rejectsOverlappingParts() {
        final var request = week("2026-10-12");
        final var parts = summaries(request);
        ((ObjectNode) parts.get(1)).put("periodStart", "2026-10-14");
        refused(request, parts);
    }

    @Test
    void rejectsDuplicatePartEvenWhenBothPassed() {
        final var request = week("2026-10-12");
        final var parts = summaries(request);
        parts.set(1, first(parts).deepCopy());
        refused(request, parts);
    }

    @Test
    void rejectsMissingOrAdditionalPart() {
        final var request = week("2026-10-12");
        final var missing = summaries(request);
        missing.remove(1);
        refused(request, missing);
        final var extra = summaries(request);
        extra.add(first(extra).deepCopy());
        refused(request, extra);
    }

    @Test
    void rejectsFalseCompleteFlagsIncludingEitherFailedPartAndMissingEof() {
        final var request = week("2026-10-12");
        for (int part = 0; part < 2; part++) {
            for (final String flag :
                    List.of(
                            "success",
                            "comparisonPassed",
                            "allCompleteProjectionAxesJavaPythonEqual",
                            "allOccurrencesCompared",
                            "sourceTerminalEmptyPageObserved",
                            "nativeReadToEof")) {
                final var parts = summaries(request);
                ((ObjectNode) parts.get(part)).put(flag, false);
                refused(request, parts);
            }
        }
    }

    @Test
    void rejectsEveryOmittedRequiredProof() {
        final var request = week("2026-10-12");
        final var fields = new HashSet<String>();
        first(summaries(request)).fieldNames().forEachRemaining(fields::add);
        for (final String field : fields) {
            final var parts = summaries(request);
            first(parts).remove(field);
            refused(request, parts);
        }
    }

    @Test
    void rejectsIncompleteColumnOrTotalCounts() {
        final var request = week("2026-10-12");
        for (final String field :
                List.of("columns", "equalColumns", "totalColumns", "equalTotals")) {
            final var parts = summaries(request);
            first(parts).put(field, field.contains("Columns") || "columns".equals(field) ? 90 : 49);
            refused(request, parts);
        }
    }

    @Test
    void rejectsDifferentOrOutOfBoundOccurrenceCounts() {
        final var request = week("2026-10-12");
        final var different = summaries(request);
        first(different).put("uiRows", 11);
        refused(request, different);
        final var oversized = summaries(request);
        first(oversized).put("sourceRows", 12001).put("uiRows", 12001);
        refused(request, oversized);
    }

    @Test
    void rejectsAnyOccurrenceRootOrMultiplicityDifference() {
        final var request = week("2026-10-12");
        for (final String field : List.of("onlyOccurrences", "changedRoots", "multiplicityRoots")) {
            final var parts = summaries(request);
            first(parts).put(field, 1);
            refused(request, parts);
        }
    }

    @Test
    void rejectsDivergentCatalogOrSourceConfiguration() {
        final var request = week("2026-10-12");
        for (final String field : List.of("catalogSha256", "sourceConfigurationSha256")) {
            final var parts = summaries(request);
            first(parts).put(field, "c".repeat(64));
            refused(request, parts);
        }
    }

    @Test
    void rejectsMalformedDigestsAndAcceptsEquivalentHexCase() {
        final var request = week("2026-10-12");
        for (final String field : List.of("catalogSha256", "sourceConfigurationSha256")) {
            for (final String digest :
                    List.of("a".repeat(63), "g".repeat(64), " " + "a".repeat(64))) {
                final var parts = summaries(request);
                first(parts).put(field, digest);
                refused(request, parts);
            }
        }
        final var parts = summaries(request);
        first(parts)
                .put("catalogSha256", "A".repeat(64))
                .put("sourceConfigurationSha256", "B".repeat(64));
        assertTrue(
                ManifestosBoundedWeek.verify(request, parts)
                        .path("comparisonPassed")
                        .booleanValue());
    }

    @Test
    void rejectsCoercibleBooleansFractionsNegativeAndOverflowCounts() {
        final var request = week("2026-10-12");
        final var stringFlag = summaries(request);
        first(stringFlag).put("success", "true");
        refused(request, stringFlag);
        for (final JsonNode invalid :
                List.of(
                        ExportValidationSupport.JSON.getNodeFactory().textNode("10"),
                        ExportValidationSupport.JSON.getNodeFactory().numberNode(10.0),
                        ExportValidationSupport.JSON.getNodeFactory().numberNode(-1),
                        ExportValidationSupport.JSON
                                .getNodeFactory()
                                .numberNode(new java.math.BigInteger("9223372036854775808")))) {
            final var parts = summaries(request);
            first(parts).set("sourceRows", invalid);
            first(parts).set("uiRows", invalid);
            refused(request, parts);
        }
    }

    @Test
    void rejectsNullNonArrayAndNonObjectEvidence() {
        final var request = week("2026-10-12");
        assertThrows(
                IllegalArgumentException.class, () -> ManifestosBoundedWeek.verify(request, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosBoundedWeek.verify(
                                request, ExportValidationSupport.JSON.createObjectNode()));
        final var parts = summaries(request);
        parts.set(0, ExportValidationSupport.JSON.nullNode());
        refused(request, parts);
    }

    @Test
    void zeroOccurrencesStillRequireCompleteEvidenceForBothScopes() {
        final var request = week("2026-10-12");
        final var parts = summaries(request);
        for (final var part : parts) {
            ((ObjectNode) part).put("sourceRows", 0).put("uiRows", 0);
        }
        assertEquals(
                0, ManifestosBoundedWeek.verify(request, parts).path("sourceRows").longValue());
        first(parts).remove("nativeReadToEof");
        refused(request, parts);
    }

    @Test
    void dailyPlansCoverAllTwentyOneCanonicalDatesExactlyOnceInOrder() {
        final var dates = new HashSet<String>();
        for (final String start : List.of("2026-10-12", "2026-10-19", "2026-10-26")) {
            final var result = ManifestosBoundedWeek.planDaily(week(start));
            assertEquals("MANIFESTOS_BOUNDED_WEEK_PLAN_V1", result.path("schema").textValue());
            assertEquals(
                    "DAILY_SEVEN_DISJOINT_SERVICE_DATES",
                    result.path("partitionPolicy").textValue());
            assertEquals(7, result.path("partCount").intValue());
            assertEquals(7, result.path("parts").size());
            assertTrue(result.path("plannedCoverageComplete").booleanValue());
            assertFalse(result.path("coverageComplete").booleanValue());
            assertFalse(result.path("comparisonPassed").booleanValue());
            for (int index = 0; index < 7; index++) {
                final String day = LocalDate.parse(start).plusDays(index).toString();
                final var part = result.path("parts").get(index);
                assertEquals(index + 1, part.path("partIndex").intValue());
                assertEquals(day, part.path("periodStart").textValue());
                assertEquals(day, part.path("periodEnd").textValue());
                assertTrue(dates.add(day));
            }
        }
        assertEquals(21, dates.size());
    }

    @Test
    void dailyRequestsKeepAllFieldsAndFiltersExceptDatesAndFreshNoncesWithoutMutatingParent() {
        final var request = week("2026-10-12");
        final var before = request.deepCopy();
        final var nonces = new HashSet<String>();
        nonces.add(request.path("attemptId").textValue());
        for (int run = 0; run < 2; run++) {
            final var result = ManifestosBoundedWeek.planDaily(request);
            for (final var part : result.required("parts")) {
                final var derived = (ObjectNode) part.required("request");
                ManifestosIndependentRequest.validate(derived);
                final String day = part.path("periodStart").textValue();
                assertEquals(day, derived.path("periodStart").textValue());
                assertEquals(day, derived.path("periodEnd").textValue());
                assertEquals(
                        day + " - " + day,
                        derived.required("filters")
                                .path(ManifestosIndependentRequest.SERVICE_DATE_FILTER)
                                .textValue());
                assertEquals(18, derived.required("filters").size());
                assertTrue(nonces.add(derived.path("attemptId").textValue()));
                final var restored = derived.deepCopy();
                for (final String name :
                        List.of("attemptId", "periodStart", "periodEnd", "filters")) {
                    restored.set(name, before.required(name).deepCopy());
                }
                assertEquals(before, restored);
            }
            ((ObjectNode) result.path("parts").get(6).required("request").required("filters"))
                    .put("manifests[status]", "changed-copy");
            assertEquals(before, request);
        }
        assertEquals(15, nonces.size());
    }

    @Test
    void dailyPlansAndVerificationRetainMonthAndYearBoundaryDays() {
        for (final String[] dates :
                new String[][] {{"2026-10-26", "2026-11-01"}, {"2026-12-28", "2027-01-03"}}) {
            final var request = week(dates[0]);
            final var plan = ManifestosBoundedWeek.planDaily(request);
            assertEquals(dates[1], plan.path("parts").get(6).path("periodStart").textValue());
            final var result = ManifestosBoundedWeek.verify(request, dailySummaries(request));
            assertEquals(dates[1], result.path("periodEnd").textValue());
            assertTrue(result.path("coverageComplete").booleanValue());
        }
    }

    @Test
    void dailyPlanStillRequiresQualifiedCompleteMondayThroughSundayRequest() {
        for (final var invalid :
                List.of(
                        week("2026-10-13"),
                        period("2026-10-12", "2026-10-17"),
                        period("2026-10-12", "2026-10-19"),
                        week("2026-10-12").put("entity", "FRETES"))) {
            assertThrows(
                    IllegalArgumentException.class, () -> ManifestosBoundedWeek.planDaily(invalid));
        }
        final var extraFilter = week("2026-10-12");
        ((ObjectNode) extraFilter.required("filters")).put("manifests[status]", "closed");
        assertThrows(
                IllegalArgumentException.class, () -> ManifestosBoundedWeek.planDaily(extraFilter));
    }

    @Test
    void
            dailySevenPartProofAddsOccurrencesWithoutDistinctIdSummationOrAuthorityAndPreservesInputs() {
        final var request = week("2026-10-12");
        final var before = request.deepCopy();
        final var comparisons = dailySummaries(request);
        for (final var part : comparisons) {
            ((ObjectNode) part).put("sourceDistinctIds", 5).put("uiDistinctIds", 5);
        }
        final var evidence = comparisons.deepCopy();
        final var result = ManifestosBoundedWeek.verify(request, comparisons);
        assertTrue(result.path("comparisonPassed").booleanValue());
        assertTrue(result.path("coverageComplete").booleanValue());
        assertEquals(7, result.path("coveredDays").intValue());
        assertEquals(7, result.path("partCount").intValue());
        assertEquals(7, result.path("parts").size());
        assertEquals(280, result.path("sourceRows").longValue());
        assertEquals(280, result.path("uiRows").longValue());
        assertEquals(
                "DAILY_SEVEN_DISJOINT_SERVICE_DATES", result.path("partitionPolicy").textValue());
        assertEquals(
                "EQUAL_BAGS_UNION_OVER_EXACT_DISJOINT_SERVICE_DATE_SCOPES",
                result.path("equalityProof").textValue());
        assertFalse(result.path("globalDistinctIdsSummed").booleanValue());
        assertFalse(result.has("sourceDistinctIds"));
        for (final String flag :
                List.of(
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "sourceQueryAuthorized",
                        "mainResumeAllowed",
                        "serviceDateVerifiedFromRows",
                        "snapshotIdentityProven")) {
            assertTrue(result.path(flag).isBoolean());
            assertFalse(result.path(flag).booleanValue());
        }
        for (final String name : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, result.path(name).intValue());
        }
        assertEquals(before, request);
        assertEquals(evidence, comparisons);
    }

    @Test
    void dailyScopesCannotPassWithMissingExtraOrUnsupportedPartCounts() {
        final var request = week("2026-10-12");
        for (final int count : List.of(0, 1, 2, 3, 4, 5, 6, 8)) {
            final var original = dailySummaries(request);
            final var parts = ExportValidationSupport.JSON.createArrayNode();
            for (int index = 0; index < count; index++) {
                parts.add(original.get(index % 7).deepCopy());
            }
            refused(request, parts);
        }
    }

    @Test
    void dailyProofRejectsDuplicateDateEvenWithOtherwiseCorrectPartIndex() {
        final var request = week("2026-10-12");
        final var parts = dailySummaries(request);
        parts.set(3, ((ObjectNode) parts.get(2)).deepCopy().put("partIndex", 4));
        refused(request, parts);
    }

    @Test
    void dailyProofRejectsGapAndDateOutsideParentWeek() {
        final var request = week("2026-10-12");
        for (final String day : List.of("2026-10-16", "2026-10-19")) {
            final var parts = dailySummaries(request);
            ((ObjectNode) parts.get(3)).put("periodStart", day).put("periodEnd", day);
            refused(request, parts);
        }
    }

    @Test
    void dailyProofRejectsMultiDayOverlappingScope() {
        final var request = week("2026-10-12");
        final var parts = dailySummaries(request);
        ((ObjectNode) parts.get(1)).put("periodStart", "2026-10-12");
        refused(request, parts);
    }

    @Test
    void dailyProofRequiresChronologicalOrderAndExactIntegralIndices() {
        final var request = week("2026-10-12");
        final var original = dailySummaries(request);
        final var reverse = ExportValidationSupport.JSON.createArrayNode();
        for (int index = 6; index >= 0; index--) {
            reverse.add(original.get(index).deepCopy());
        }
        refused(request, reverse);
        for (final JsonNode invalid :
                List.of(
                        ExportValidationSupport.JSON.getNodeFactory().numberNode(3),
                        ExportValidationSupport.JSON.getNodeFactory().numberNode(7.0),
                        ExportValidationSupport.JSON.getNodeFactory().textNode("7"))) {
            final var parts = dailySummaries(request);
            ((ObjectNode) parts.get(6)).set("partIndex", invalid);
            refused(request, parts);
        }
    }

    @Test
    void dailyProofRejectsEveryOmittedRequiredFieldIncludingSeventhIndex() {
        final var request = week("2026-10-12");
        final var fields = new HashSet<String>();
        dailySummaries(request).get(6).fieldNames().forEachRemaining(fields::add);
        for (final String field : fields) {
            final var parts = dailySummaries(request);
            ((ObjectNode) parts.get(6)).remove(field);
            refused(request, parts);
        }
    }

    @Test
    void dailyProofRequiresEveryPartCompleteWithAllColumnsTotalsAndNoDifferences() {
        final var request = week("2026-10-12");
        for (int index = 0; index < 7; index++) {
            for (final String flag :
                    List.of(
                            "success",
                            "comparisonPassed",
                            "allCompleteProjectionAxesJavaPythonEqual",
                            "allOccurrencesCompared",
                            "sourceTerminalEmptyPageObserved",
                            "nativeReadToEof")) {
                final var parts = dailySummaries(request);
                ((ObjectNode) parts.get(index)).put(flag, false);
                refused(request, parts);
            }
        }
        for (final String field :
                List.of("columns", "equalColumns", "totalColumns", "equalTotals", "uiRows")) {
            final var parts = dailySummaries(request);
            final var last = (ObjectNode) parts.get(6);
            last.put(field, last.path(field).longValue() - 1);
            refused(request, parts);
        }
        for (final String field : List.of("onlyOccurrences", "changedRoots", "multiplicityRoots")) {
            final var parts = dailySummaries(request);
            ((ObjectNode) parts.get(6)).put(field, 1);
            refused(request, parts);
        }
    }

    @Test
    void dailyProofRejectsSeventhCatalogOrSourceFingerprintMismatch() {
        final var request = week("2026-10-12");
        for (final String field : List.of("catalogSha256", "sourceConfigurationSha256")) {
            final var parts = dailySummaries(request);
            ((ObjectNode) parts.get(6)).put(field, "c".repeat(64));
            refused(request, parts);
        }
    }

    @Test
    void dailyZeroRowScopesStillRequireAllSevenEofProofs() {
        final var request = week("2026-10-12");
        final var parts = dailySummaries(request);
        for (int index = 0; index < 7; index++) {
            emptyDay(parts, index);
        }
        final var result = ManifestosBoundedWeek.verify(request, parts);
        assertEquals(0, result.path("sourceRows").longValue());
        assertEquals(0, result.path("equalTotals").intValue());
        assertFalse(result.path("totalComparisonsApplicable").booleanValue());
        ((ObjectNode) parts.get(6)).remove("sourceTerminalEmptyPageObserved");
        refused(request, parts);
    }

    @Test
    void dailyWeekAcceptsExplicitEmptySundayWithoutInventingNumericTotals() {
        final var request = week("2026-10-19");
        final var parts = dailySummaries(request);
        emptyDay(parts, 6);
        final var before = parts.deepCopy();
        final var result = ManifestosBoundedWeek.verify(request, parts);
        assertTrue(result.path("comparisonPassed").booleanValue());
        assertTrue(result.path("coverageComplete").booleanValue());
        assertEquals(210, result.path("sourceRows").longValue());
        assertEquals(50, result.path("equalTotals").intValue());
        assertTrue(result.path("totalComparisonsApplicable").booleanValue());
        assertEquals(1, result.path("emptyDayParts").intValue());
        final var sunday = result.path("parts").get(6);
        assertEquals("2026-10-25", sunday.path("periodStart").textValue());
        assertTrue(sunday.path("emptyDayProven").booleanValue());
        assertFalse(sunday.path("totalComparisonsApplicable").booleanValue());
        assertEquals(0, sunday.path("equalTotals").intValue());
        assertEquals(before, parts);
    }

    @Test
    void dailyEmptyProofCannotHideRowsOrMissingOrContradictoryEmptyEvidence() {
        final var request = week("2026-10-19");
        for (final String field :
                List.of(
                        "emptyDayProven",
                        "totalComparisonsApplicable",
                        "equalTotals",
                        "terminalPageIndex",
                        "nonemptyPages")) {
            final var parts = dailySummaries(request);
            emptyDay(parts, 6).remove(field);
            refused(request, parts);
        }
        for (final String field :
                List.of(
                        "sourceRows",
                        "uiRows",
                        "terminalPageIndex",
                        "nonemptyPages",
                        "equalTotals")) {
            final var parts = dailySummaries(request);
            final var empty = emptyDay(parts, 6);
            empty.put(field, empty.path(field).longValue() + 1);
            refused(request, parts);
        }
        for (final String field : List.of("emptyDayProven", "totalComparisonsApplicable")) {
            final var parts = dailySummaries(request);
            final var empty = emptyDay(parts, 6);
            empty.put(field, !empty.path(field).booleanValue());
            refused(request, parts);
        }
        final var falseEmpty = dailySummaries(request);
        emptyDay(falseEmpty, 6).put("sourceRows", 70).put("uiRows", 70).put("equalTotals", 50);
        refused(request, falseEmpty);
        final var nonemptyWithoutTotals = dailySummaries(request);
        ((ObjectNode) nonemptyWithoutTotals.get(6))
                .put("totalComparisonsApplicable", false)
                .put("equalTotals", 0);
        refused(request, nonemptyWithoutTotals);
    }

    @Test
    void dailyEmptyDayStillRequiresEveryIndependentCompletionAndEofFlag() {
        final var request = week("2026-10-19");
        for (final String flag :
                List.of(
                        "success",
                        "comparisonPassed",
                        "allCompleteProjectionAxesJavaPythonEqual",
                        "allOccurrencesCompared",
                        "sourceTerminalEmptyPageObserved",
                        "nativeReadToEof")) {
            final var missing = dailySummaries(request);
            emptyDay(missing, 6).remove(flag);
            refused(request, missing);
            final var incomplete = dailySummaries(request);
            emptyDay(incomplete, 6).put(flag, false);
            refused(request, incomplete);
            final var coercible = dailySummaries(request);
            emptyDay(coercible, 6).put(flag, "true");
            refused(request, coercible);
        }
    }

    private static ObjectNode emptyDay(final ArrayNode parts, final int index) {
        return ((ObjectNode) parts.get(index))
                .put("sourceRows", 0)
                .put("uiRows", 0)
                .put("emptyDayProven", true)
                .put("totalComparisonsApplicable", false)
                .put("equalTotals", 0)
                .put("terminalPageIndex", 1)
                .put("nonemptyPages", 0);
    }

    static ArrayNode dailySummaries(final JsonNode request) {
        final LocalDate start = LocalDate.parse(request.path("periodStart").textValue());
        final var result = ExportValidationSupport.JSON.createArrayNode();
        final var prototype = (ObjectNode) summaries(request).get(0);
        for (int index = 1; index <= 7; index++) {
            final String day = start.plusDays(index - 1).toString();
            result.add(
                    prototype
                            .deepCopy()
                            .put("partIndex", index)
                            .put("periodStart", day)
                            .put("periodEnd", day)
                            .put("sourceRows", index * 10)
                            .put("uiRows", index * 10));
        }
        return result;
    }

    static ObjectNode week(final String first) {
        return period(first, LocalDate.parse(first).plusDays(6).toString());
    }

    private static ObjectNode period(final String first, final String last) {
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("entity", "MANIFESTOS")
                        .put("reportId", 6399)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", first)
                        .put("periodEnd", last)
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        ManifestosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, first + " - " + last);
        return request;
    }

    static ArrayNode summaries(final JsonNode request) {
        final LocalDate start = LocalDate.parse(request.path("periodStart").textValue());
        final var result = ExportValidationSupport.JSON.createArrayNode();
        for (int index = 0; index < 2; index++) {
            result.addObject()
                    .put("periodStart", start.plusDays(index == 0 ? 0 : 3).toString())
                    .put("periodEnd", start.plusDays(index == 0 ? 2 : 6).toString())
                    .put("success", true)
                    .put("comparisonPassed", true)
                    .put("columns", 91)
                    .put("equalColumns", 91)
                    .put("totalColumns", 50)
                    .put("equalTotals", 50)
                    .put("sourceRows", index == 0 ? 10 : 20)
                    .put("uiRows", index == 0 ? 10 : 20)
                    .put("onlyOccurrences", 0)
                    .put("changedRoots", 0)
                    .put("multiplicityRoots", 0)
                    .put("allCompleteProjectionAxesJavaPythonEqual", true)
                    .put("allOccurrencesCompared", true)
                    .put("sourceTerminalEmptyPageObserved", true)
                    .put("nativeReadToEof", true)
                    .put("catalogSha256", "a".repeat(64))
                    .put("sourceConfigurationSha256", "b".repeat(64));
        }
        return result;
    }

    private static ObjectNode first(final ArrayNode parts) {
        return (ObjectNode) parts.get(0);
    }

    private static void refused(final JsonNode request, final JsonNode parts) {
        assertThrows(
                IllegalArgumentException.class, () -> ManifestosBoundedWeek.verify(request, parts));
    }
}
