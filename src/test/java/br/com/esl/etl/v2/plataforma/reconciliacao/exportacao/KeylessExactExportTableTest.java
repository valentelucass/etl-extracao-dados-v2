package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class KeylessExactExportTableTest {
    private static final List<ExactExportTable.Column> COLUMNS =
            List.of(
                    new ExactExportTable.Column("Parcela candidata", 1, 1, false),
                    new ExactExportTable.Column("Valor", 2, 2, true, "BINARY64_UNITS_129"));

    private static ExactExportTable.Atom text(final String value) {
        return new ExactExportTable.Atom("TEXT", value);
    }

    private static ExactExportTable.Atom units(final int value) {
        return new ExactExportTable.Atom("NUMBER", Integer.toString(value));
    }

    private static KeylessExactExportTable table(final List<List<ExactExportTable.Atom>> rows) {
        final var table =
                new KeylessExactExportTable(
                        COLUMNS, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        rows.forEach(table::add);
        table.seal();
        return table;
    }

    @Test
    void repeatedOrMissingParcelCandidateIsNeverAnIdentityRequirement() {
        final var rows =
                List.of(
                        List.of(text("7"), units(11)),
                        List.of(text("7"), units(22)),
                        List.of(new ExactExportTable.Atom("NULL", ""), units(33)),
                        List.of(new ExactExportTable.Atom("ABSENT", ""), units(44)),
                        List.of(text(""), units(55)));
        final var comparison = table(rows).compare(table(rows));
        assertTrue(comparison.contentEqual());
        assertEquals(5, comparison.apiRows());
    }

    @Test
    void swappingRateioValuesBetweenRowsFailsEvenWithEqualColumnsAndTotals() {
        final var left =
                table(List.of(List.of(text("7"), units(11)), List.of(text("8"), units(22))));
        final var right =
                table(List.of(List.of(text("7"), units(22)), List.of(text("8"), units(11))));
        final var comparison = left.compare(right);
        assertEquals(List.of(true, true), comparison.columnsEqual());
        assertEquals(List.of(true), comparison.totalsEqual());
        assertFalse(comparison.contentEqual());
        assertEquals(2, comparison.apiOnlyOccurrences());
        assertEquals(2, comparison.uiOnlyOccurrences());
    }

    @Test
    void droppingOneDuplicateIsVisibleWithoutDeduplication() {
        final var row = List.of(text("7"), units(11));
        final var comparison = table(List.of(row, row)).compare(table(List.of(row)));
        assertFalse(comparison.fullTuplesEqual());
        assertEquals(2, comparison.apiRows());
        assertEquals(1, comparison.apiOnlyOccurrences());
        assertEquals(0, comparison.uiOnlyOccurrences());
    }

    @Test
    void rowOrderDoesNotChangeTheMultisetOrItsCompleteProjection() {
        final var first = List.of(text("A😀"), units(11));
        final var second = List.of(text("A\uE000"), units(22));
        final var left = table(List.of(first, first, second));
        final var right = table(List.of(second, first, first));
        assertTrue(left.compare(right).contentEqual());
        assertEquals(left.fullValueProjection(), right.fullValueProjection());
    }

    @Test
    void nullAbsentBlankAndEmptyTextRemainDifferent() {
        final var rows =
                List.of(
                        List.of(new ExactExportTable.Atom("NULL", ""), units(1)),
                        List.of(new ExactExportTable.Atom("ABSENT", ""), units(1)),
                        List.of(new ExactExportTable.Atom("BLANK", "", "style"), units(1)),
                        List.of(text(""), units(1)));
        for (int left = 0; left < rows.size(); left++) {
            for (int right = left + 1; right < rows.size(); right++) {
                assertFalse(
                        table(List.of(rows.get(left)))
                                .compare(table(List.of(rows.get(right))))
                                .contentEqual());
            }
        }
    }

    @Test
    void typedCarrierAndNumericLexemeAreRetained() {
        final var left =
                table(List.of(List.of(new ExactExportTable.Atom("NUMBER", "1", "a"), units(1))));
        final var carrier =
                table(List.of(List.of(new ExactExportTable.Atom("NUMBER", "1", "b"), units(1))));
        final var spelling =
                table(List.of(List.of(new ExactExportTable.Atom("NUMBER", "1.0", "a"), units(1))));
        final var text = table(List.of(List.of(text("1"), units(1))));
        assertFalse(left.compare(carrier).contentEqual());
        assertFalse(left.compare(spelling).contentEqual());
        assertFalse(left.compare(text).contentEqual());
    }

    @Test
    void totalsUseExactEncodedUnitsIncludingRepeatedNegativeValues() {
        final var table =
                table(
                        List.of(
                                List.of(text("A"), units(-11)),
                                List.of(text("A"), units(-11)),
                                List.of(text("B"), units(3))));
        final var total = table.fullValueProjection().path("totals").get(0);
        assertTrue(total.path("ready").booleanValue());
        assertEquals("-19", total.path("sumOfEncodedCellUnits2PowMinus1074").textValue());
        assertEquals("-19", total.path("exactDecimalNumerator").textValue());
    }

    @Test
    void allNullTotalDoesNotPretendToBeAQualifiedNumericSum() {
        final var left = table(List.of(List.of(text("A"), new ExactExportTable.Atom("NULL", ""))));
        final var comparison = left.compare(left);
        assertTrue(comparison.fullTuplesEqual());
        assertEquals(List.of(false), comparison.totalsReady());
        assertFalse(comparison.contentEqual());
    }

    @Test
    void projectionHasNoInventedIdentityAxisOrDistinctAccountCount() {
        final var table =
                table(List.of(List.of(text("A"), units(1)), List.of(text("A"), units(1))));
        final var projection = table.fullValueProjection();
        assertTrue(projection.path("keylessPhysicalOccurrences").booleanValue());
        assertFalse(projection.path("rootIdentityQualified").booleanValue());
        assertFalse(projection.has("distinctIds"));
        assertFalse(projection.has("keyedTupleBags"));
        assertFalse(projection.has("idMultiplicityBag"));
        assertEquals(1, projection.path("fullTupleBag").path("distinctKeys").intValue());
        assertEquals(2, projection.path("fullTupleBag").path("occurrences").intValue());
    }

    @Test
    void requiresSealAndMatchingMappingsAndRejectsModificationAfterSeal() {
        final var unsealed =
                new KeylessExactExportTable(
                        COLUMNS, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        final var sealed = table(List.of(List.of(text("A"), units(1))));
        assertThrows(IllegalArgumentException.class, () -> unsealed.compare(sealed));
        assertThrows(IllegalArgumentException.class, unsealed::fullValueProjection);
        assertThrows(
                IllegalArgumentException.class, () -> sealed.add(List.of(text("A"), units(1))));
        final var different =
                new KeylessExactExportTable(
                        List.of(new ExactExportTable.Column("Other", 1, 1, false)),
                        new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        different.seal();
        assertThrows(IllegalArgumentException.class, () -> sealed.compare(different));
    }

    @Test
    void rejectsInvalidUnicodeBeforeMutatingAnyColumn() {
        final var table =
                new KeylessExactExportTable(
                        COLUMNS, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        assertThrows(
                IllegalArgumentException.class,
                () -> table.add(List.of(text("A"), text("\uD800"))));
        table.add(List.of(text("A"), units(1)));
        table.seal();
        assertEquals(
                table(List.of(List.of(text("A"), units(1)))).fullValueProjection(),
                table.fullValueProjection());
    }

    @Test
    void retainsTwelveThousandOccurrencesAndRefusesTheNext() {
        final var table =
                new KeylessExactExportTable(
                        COLUMNS, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        final var row = List.of(text("A"), units(1));
        for (int index = 0; index < 12000; index++) {
            table.add(row);
        }
        assertThrows(IllegalArgumentException.class, () -> table.add(row));
        table.seal();
        assertEquals(12000, table.fullValueProjection().path("rows").intValue());
    }

    @Test
    void copyingInputRowsPreventsLaterMutation() {
        final var row = new ArrayList<>(List.of(text("A"), units(1)));
        final var table =
                new KeylessExactExportTable(
                        COLUMNS, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        table.add(row);
        row.set(0, text("changed"));
        table.seal();
        assertEquals(
                table(List.of(List.of(text("A"), units(1)))).fullValueProjection(),
                table.fullValueProjection());
    }
}
