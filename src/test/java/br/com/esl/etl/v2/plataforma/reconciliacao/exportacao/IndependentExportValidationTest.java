package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndependentExportValidationTest {
    @TempDir Path directory;

    @Test
    void comparesEveryCommonColumnAndFiftyTotalsAndReportsDashboardContractGaps() throws Exception {
        final var result = new ExportPairFixture(directory).compare();
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", result.path("status").asText());
        assertEquals(114, result.path("comparison").path("columnsEqual").size());
        assertEquals(50, result.path("comparison").path("totalsEqual").size());
        assertEquals(113, result.path("dashboardMissingColumns").size());
        assertFalse(result.path("mainResumeAllowed").asBoolean());
        assertFalse(result.path("dashboardConsumptionVerified").asBoolean());
        assertFalse(result.path("snapshotIdentityProven").asBoolean());
    }

    @Test
    void apiExtraColumnsAreAllowedWithoutIgnoringAnyReferenceColumn() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.apiBook = ExportPairFixture.book(true, false, false, "1.1");
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", fixture.compare().path("status").asText());
    }

    @Test
    void sameCountsDifferentValueRequiresInvestigationAndNewPair() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiBook = ExportPairFixture.book(false, false, false, "1.100000000000001");
        final var result = fixture.compare();
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR", result.path("status").asText());
        assertEquals(
                result.path("comparison").path("apiRows"),
                result.path("comparison").path("uiRows"));
        assertFalse(result.path("temporalCausalityProven").asBoolean());
    }

    @Test
    void olderReferenceCannotBeApprovedByContentEquality() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiTime("2025-03-10T12:25:00-03:00", "SECOND");
        assertEquals("PENDING_CONTROLLED_NEAR_PAIR", fixture.compare().path("status").asText());
    }

    @Test
    void sameDisplayedMinuteIsAnUncertaintyIntervalAndNeverSnapshotProof() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.api.put("timePrecision", "MINUTE");
        fixture.apiObservation.put("timePrecision", "MINUTE");
        fixture.uiTime("2025-03-10T12:00:00-03:00", "MINUTE");
        fixture.config.put("maximumRequestSeparationSeconds", 30);
        final var result = fixture.compare();
        assertEquals("PENDING_CONTROLLED_NEAR_PAIR", result.path("status").asText());
        assertFalse(result.path("snapshotIdentityProven").asBoolean());
    }

    @Test
    void timezoneOffsetMustAgreeWithNamedZone() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiTime("2025-03-10T12:00:20+00:00", "SECOND");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void differentObservedPeriodIsRefused() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.ui.put("periodEnd", "2025-03-08");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void differentAuxiliaryFilterIsRefusedEvenWithEqualFiles() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.ui.get("filters"))
                .put("freights[status]", "pending");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void missingEmptyFilterIsAlsoRefused() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.request.get("filters"))
                .remove("freights[courtesy]");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void clickObservationMustBindTheFileAndRequest() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiObservation.put("fileId", "ANOTHER_FILE");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void uiSessionCannotBePretendedByAnApiEvent() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiObservation.put("event", "API_EXPORT_POST");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void swappedPairDoesNotReuseAdmission() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        final var pair = fixture.manifest();
        pair.set("api", pair.required("ui").deepCopy());
        assertThrows(IllegalArgumentException.class, () -> ExportPairFixture.compare(pair));
    }

    @Test
    void inputPinDriftIsRefusedBeforeComparison() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        final var pair = fixture.manifest();
        Files.write(directory.resolve("api.xlsx"), new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> ExportPairFixture.compare(pair));
    }

    @Test
    void missingColumnAndRepeatedMappingAreRefused() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.mapping.path("columns").get(1))
                .put("uiColumn", 1);
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void formulasCannotBeComparedThroughCachedValues() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiBook = ExportPairFixture.book(false, true, false, "1.1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void differentDateSystemsAreNotSilentlyNormalized() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiBook = ExportPairFixture.book(false, false, true, "1.1");
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR",
                fixture.compare().path("status").asText());
    }

    @Test
    void corruptZipNeverProducesAResult() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.uiBook = Arrays.copyOf(fixture.uiBook, 24);
        assertThrows(Exception.class, fixture::compare);
    }

    @Test
    void arbitraryExportProfileCannotReplaceHistoricalContract() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.request.put("profile", "GET_WITH_BODY");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void planIsExplicitAndUiRemainsPending() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        final var plan = IndependentExportRequest.plan(fixture.request);
        assertTrue(plan.path("uiState").asText().startsWith("PENDING_OPERATOR"));
        assertEquals("/api/analytics/reports/6389/export", plan.path("path").asText());
        assertFalse(plan.path("body").path("export_to_ftp").asBoolean());
        assertEquals(0, plan.path("HTTP").asInt());
    }

    @Test
    void sameFiltersOnTwoBooksDoNotExcuseRowsOutsideRequestedPeriod() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        fixture.request.put("periodStart", "2025-04-03").put("periodEnd", "2025-04-09");
        for (final var node : java.util.List.of(fixture.request, fixture.api, fixture.ui)) {
            node.put("periodStart", "2025-04-03").put("periodEnd", "2025-04-09");
        }
        for (final var node :
                java.util.List.of(
                        fixture.request,
                        fixture.api,
                        fixture.ui,
                        fixture.apiObservation,
                        fixture.uiObservation)) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node.required("filters"))
                    .put("freights[service_at]", "2025-04-03 - 2025-04-09");
        }
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void observedBrazilianDatePresentationKeepsTheSameExactPeriodAndOtherFilters()
            throws Exception {
        final var fixture = new ExportPairFixture(directory);
        for (final var node : java.util.List.of(fixture.ui, fixture.uiObservation)) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node.required("filters"))
                    .put("freights[service_at]", "03/03/2025 - 09/03/2025");
        }
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", fixture.compare().path("status").asText());
    }
}
