package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteDayPartitionAdapterTest {
    @TempDir Path directory;

    @Test
    void oneDayPreservesAll52FieldsFourAxesAndFalseWeeklyAuthority() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 2);
        final var result = compare(fixture, "2026-10-13", false);
        assertTrue(result.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(2, result.path("sourceDayRows").longValue());
        assertEquals(2, result.path("selectedNativeDayRows").longValue());
        assertEquals(2, result.path("nativeWholeWeekRows").longValue());
        assertEquals(2, result.path("weekdayIndex").intValue());
        assertEquals(52, result.required("comparison").required("columnsEqual").size());
        assertEquals(4, result.required("totalNumericCounts").size());
        for (final String axis :
                java.util.List.of("fullValueProjection", "originalValueProjection")) {
            assertTrue(result.required(axis).has("api") && result.required(axis).has("ui"));
        }
        assertFalse(result.path("periodComparisonComplete").booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("idOrMinutaCandidateUsedAsIdentity").booleanValue());
    }

    @Test
    void wholeNativeWeekIsReopenedAndEveryOccurrenceAssignedToExactlyOneCivilDay()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var other = new LinkedHashMap<>(fixture.nativeRows.get(0));
        other.put(
                coordinate(fixture, "service_at"),
                new FaturasClienteContentFixture.Cell(null, serial("2026-10-14", "0.5"), 2));
        fixture.nativeRows.add(other);
        final var result = compare(fixture, "2026-10-13", false);
        assertTrue(result.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(2, result.path("nativeWholeWeekRows").longValue());
        assertEquals(1, result.path("selectedNativeDayRows").longValue());
        final var counts = result.required("nativeWholeWeekDayCounts");
        assertEquals(7, counts.size());
        assertEquals(1, counts.get(1).path("rows").longValue());
        assertEquals(1, counts.get(2).path("rows").longValue());
        long sum = 0;
        for (final var count : counts) {
            sum += count.required("rows").longValue();
        }
        assertEquals(result.path("nativeWholeWeekRows").longValue(), sum);
        assertTrue(result.path("allNativeRowsReadToEof").booleanValue());
    }

    @Test
    void sourceDayUsesOffsetCivilDateWithoutUtcConversionOrFractionLoss() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.get(0).put("service_at", "2026-10-13T00:00:00.123+14:00");
        field(fixture, "service_at")
                .put("sourceTimestampEncoding", "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1");
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "service_at"),
                        new FaturasClienteContentFixture.Cell(null, serial("2026-10-13", "0"), 2));
        final var result = compare(fixture, "2026-10-13", false);
        assertTrue(result.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-12", false));
        field(fixture, "service_at")
                .put("sourceTimestampEncoding", "SOURCE_OFFSET_CIVIL_WHOLE_SECOND_V1");
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-13", false));
    }

    @Test
    void unchangedColumnBagsCannotHideChangedAssociationsInsideTheSelectedDay() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 2);
        final int payer = coordinate(fixture, "fit_pyr_name");
        final var first = fixture.nativeRows.get(0).get(payer);
        fixture.nativeRows.get(0).put(payer, fixture.nativeRows.get(1).get(payer));
        fixture.nativeRows.get(1).put(payer, first);
        final var result = compare(fixture, "2026-10-13", false);
        for (final var equal : result.required("comparison").required("columnsEqual")) {
            assertTrue(equal.booleanValue());
        }
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
        assertFalse(result.path("dayContentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void repeatedIdsAndFullDuplicatesRemainOccurrencesAndInvoiceOrderIsNotSorted()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertTrue(
                compare(fixture, "2026-10-13", false)
                        .path("dayContentEqualUnderCandidatePresentation")
                        .booleanValue());
        fixture.nativeRows.remove(1);
        final var missing = compare(fixture, "2026-10-13", false);
        assertEquals(1, missing.path("comparison").path("apiOnlyOccurrences").longValue());
        fixture.records.remove(1);
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "invoices_mapping"),
                        FaturasClienteContentFixture.text("Authored A | Authored B | Authored A"));
        assertFalse(
                compare(fixture, "2026-10-13", false)
                        .path("dayContentEqualUnderCandidatePresentation")
                        .booleanValue());
    }

    @Test
    void decimalChangesFailAndNonselectedNativeRowsStillRequireEveryCarrierAndWeekScope()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "total"),
                        FaturasClienteContentFixture.number("0.10000000000000002"));
        assertFalse(
                compare(fixture, "2026-10-13", false)
                        .path("dayContentEqualUnderCandidatePresentation")
                        .booleanValue());
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "service_at"),
                        new FaturasClienteContentFixture.Cell(
                                null, serial("2026-10-14", "0.5"), 2));
        fixture.nativeRows
                .get(0)
                .put(coordinate(fixture, "total"), FaturasClienteContentFixture.text("0.1"));
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-13", false));
        fixture.nativeRows
                .get(0)
                .put(coordinate(fixture, "total"), FaturasClienteContentFixture.number("0.1"));
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "service_at"),
                        new FaturasClienteContentFixture.Cell(
                                null, serial("2026-10-19", "0.5"), 2));
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-13", false));
    }

    @Test
    void zeroOccurrenceDayRequiresProofAndDoesNotInventNumericReadinessOrAnEmptyRawPin()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.clear();
        final var workbook = fixture.pin("native.xlsx", fixture.workbookBytes());
        final var catalog =
                fixture.pin(
                        "catalog.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.catalog));
        final var empty = ExportValidationSupport.JSON.createArrayNode();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FaturasClienteDayPartitionAdapter.compare(
                                fixture.request,
                                empty,
                                workbook,
                                catalog,
                                "2026-10-12",
                                false,
                                FaturasClienteContentFixture.budget()));
        final var result =
                FaturasClienteDayPartitionAdapter.compare(
                        fixture.request,
                        empty,
                        workbook,
                        catalog,
                        "2026-10-12",
                        true,
                        FaturasClienteContentFixture.budget());
        assertTrue(result.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(0, result.path("sourceDayRows").longValue());
        assertEquals(0, result.path("selectedNativeDayRows").longValue());
        for (final var ready : result.required("comparison").required("totalsReady")) {
            assertFalse(ready.booleanValue());
        }
        final var zero =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("path", directory.resolve("empty.jsonl").toString())
                        .put("bytes", 0)
                        .put(
                                "sha256",
                                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FaturasClienteDayPartitionAdapter.sources(
                                ExportValidationSupport.JSON.createArrayNode().add(zero)));
    }

    @Test
    void originalPinsAreUniqueBoundedAndFinalLfAndExactSevenDayScopeRemainMandatory()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var pins = fixture.pins();
        final var source = ExportValidationSupport.JSON.valueToTree(pins.source());
        final var duplicate =
                ExportValidationSupport.JSON.createArrayNode().add(source).add(source);
        assertThrows(
                IllegalArgumentException.class,
                () -> FaturasClienteDayPartitionAdapter.sources(duplicate));
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-19", false));
        fixture.request.put("periodEnd", "2026-10-17");
        fixture.request
                .withObject("/filters")
                .put("freights[service_at]", "2026-10-12 - 2026-10-17");
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-13", false));
        fixture.request.put("periodEnd", "2026-10-18");
        fixture.request
                .withObject("/filters")
                .put("freights[service_at]", "2026-10-12 - 2026-10-18");
        fixture.finalLf = false;
        assertThrows(IllegalArgumentException.class, () -> compare(fixture, "2026-10-13", false));
    }

    private ObjectNode compare(
            final FaturasClienteContentFixture fixture, final String day, final boolean empty)
            throws Exception {
        final var pins = fixture.pins();
        final var sources = ExportValidationSupport.JSON.createArrayNode();
        sources.add(ExportValidationSupport.JSON.valueToTree(pins.source()));
        return FaturasClienteDayPartitionAdapter.compare(
                fixture.request,
                sources,
                pins.workbook(),
                pins.catalog(),
                day,
                empty,
                FaturasClienteContentFixture.budget());
    }

    static int coordinate(final FaturasClienteContentFixture fixture, final String name) {
        for (final var field : fixture.catalog.required("columns")) {
            if (name.equals(field.path("sourceField").textValue())) {
                return field.path("nativeColumn").intValue();
            }
        }
        throw new IllegalArgumentException("AUTHORED_FIXTURE_FIELD_MISSING");
    }

    static ObjectNode field(final FaturasClienteContentFixture fixture, final String name) {
        return fixture.field(coordinate(fixture, name));
    }

    static String serial(final String day, final String fraction) {
        return java.math.BigDecimal.valueOf(
                        ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.parse(day)))
                .add(new java.math.BigDecimal(fraction))
                .toPlainString();
    }
}
