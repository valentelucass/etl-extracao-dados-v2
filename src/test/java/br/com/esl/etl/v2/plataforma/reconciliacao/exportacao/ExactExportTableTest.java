package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Counterexamples retained from the qualified ProjectionBag/TwoNativeTotals models. */
class ExactExportTableTest {
    private final List<ExactExportTable.Column> columns =
            List.of(
                    new ExactExportTable.Column("ID", 1, 1, false),
                    new ExactExportTable.Column("Amount", 2, 2, true));

    private ExactExportTable bag(final String... amounts) {
        final var table = table();
        for (final String amount : amounts) {
            table.add(
                    List.of(
                            new ExactExportTable.Atom("TEXT", "same-id"),
                            new ExactExportTable.Atom("NUMBER", amount)));
        }
        table.seal();
        return table;
    }

    private ExactExportTable table() {
        return new ExactExportTable(
                columns, 0, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
    }

    @Test
    void duplicateOccurrencesAreConserved() {
        final var result = bag("1", "1").compare(bag("1", "1"));
        assertTrue(result.contentEqual());
        assertEquals(2, result.apiRows());
    }

    @Test
    void missingDuplicateChangesMultiplicity() {
        final var result = bag("1", "1").compare(bag("1"));
        assertFalse(result.contentEqual());
        assertEquals(1, result.apiOnlyOccurrences());
        assertEquals(1, result.changedMultiplicityIds());
    }

    @Test
    void equalCountsAndEqualSumsDoNotProveContent() {
        final var result = bag("1", "3").compare(bag("2", "2"));
        assertEquals(result.apiRows(), result.uiRows());
        assertTrue(result.totalsEqual().get(0));
        assertFalse(result.contentEqual());
        assertEquals(1, result.changedIds());
    }

    @Test
    void crossRowSwapPassesColumnHistogramsButFailsTuplesAndKeyedContent() {
        final var api = table();
        final var ui = table();
        api.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "a"),
                        new ExactExportTable.Atom("NUMBER", "1")));
        api.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "b"),
                        new ExactExportTable.Atom("NUMBER", "2")));
        ui.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "a"),
                        new ExactExportTable.Atom("NUMBER", "2")));
        ui.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "b"),
                        new ExactExportTable.Atom("NUMBER", "1")));
        api.seal();
        ui.seal();
        final var result = api.compare(ui);
        assertTrue(result.columnsEqual().stream().allMatch(Boolean::booleanValue));
        assertFalse(result.fullTuplesEqual());
        assertEquals(2, result.changedIds());
        assertEquals(2, result.changedIdsByColumn().get(1).longValue());
    }

    @Test
    void decimalTotalsAreExactBeyondBinary64Precision() {
        assertTrue(
                bag("9007199254740993.01", "0.02")
                        .compare(bag("9007199254740993.01", "0.02"))
                        .totalsEqual()
                        .get(0));
        assertFalse(
                bag("9007199254740993.01", "0.02")
                        .compare(bag("9007199254740993.00", "0.02"))
                        .totalsEqual()
                        .get(0));
    }

    @Test
    void nullMissingBlankEmptyStringAndZeroRemainDifferent() {
        final var atoms =
                List.of(
                        new ExactExportTable.Atom("ABSENT", ""),
                        new ExactExportTable.Atom("NULL", ""),
                        new ExactExportTable.Atom("BLANK", ""),
                        new ExactExportTable.Atom("TEXT", ""),
                        new ExactExportTable.Atom("NUMBER", "0"));
        for (int left = 0; left < atoms.size(); left++) {
            for (int right = 0; right < atoms.size(); right++) {
                final var api = table();
                final var ui = table();
                api.add(List.of(new ExactExportTable.Atom("TEXT", "a"), atoms.get(left)));
                ui.add(List.of(new ExactExportTable.Atom("TEXT", "a"), atoms.get(right)));
                api.seal();
                ui.seal();
                assertEquals(left == right, api.compare(ui).fullTuplesEqual());
            }
        }
    }

    @Test
    void numericStringIsNeverNumericValue() {
        final var api = table();
        api.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "same-id"),
                        new ExactExportTable.Atom("TEXT", "1")));
        api.seal();
        assertFalse(api.compare(bag("1")).contentEqual());
    }

    @Test
    void partialBagsCannotPublishComparison() {
        assertThrows(IllegalArgumentException.class, () -> table().compare(bag("1")));
    }

    @Test
    void nullIdCannotBeSilentlyDropped() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        table().add(
                                        List.of(
                                                new ExactExportTable.Atom("NULL", ""),
                                                new ExactExportTable.Atom("NUMBER", "1"))));
    }

    @Test
    void nonvaluesInTotalsDoNotBecomeZero() {
        final var api = table();
        api.add(
                List.of(
                        new ExactExportTable.Atom("TEXT", "same-id"),
                        new ExactExportTable.Atom("NULL", "")));
        api.seal();
        assertFalse(api.compare(bag("0")).totalsEqual().get(0));
    }

    @Test
    void numberScaleAndRowsAreBounded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExactExportTable.Atom("NUMBER", "1e999999"));
        final var table = table();
        final var row =
                List.of(
                        new ExactExportTable.Atom("TEXT", "id"),
                        new ExactExportTable.Atom("NUMBER", "1"));
        for (int index = 0; index < 12000; index++) {
            table.add(row);
        }
        assertThrows(IllegalArgumentException.class, () -> table.add(row));
    }

    @Test
    void deadlineMustBeFiniteAndCannotExceedProfile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExportValidationSupport.Budget(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExportValidationSupport.Budget(Duration.ofSeconds(151)));
    }
}
