package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** DRAFT synthetic controls only; these additions never substitute for original129 replay. */
class EncoderWorkbookDifferentialCandidateTest {
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    private static BigInteger decodeUnits(final String hexadecimal) {
        final var word = new BigInteger(hexadecimal, 16);
        final int exponent = word.shiftRight(52).and(BigInteger.valueOf(2047)).intValueExact();
        if (exponent == 2047) {
            throw new IllegalArgumentException("NONFINITE_ORACLE_CARRIER");
        }
        final var fraction = word.and(BigInteger.ONE.shiftLeft(52).subtract(BigInteger.ONE));
        final var units = exponent == 0 ? fraction : fraction.setBit(52).shiftLeft(exponent - 1);
        return word.testBit(63) ? units.negate() : units;
    }

    @Test
    void originalEncodingLexemesHaveTheExactOriginalCarrierUnits() {
        final String[][] vectors = {
            {"0", "0000000000000000"},
            {"-0.000", "8000000000000000"},
            {"-1", "BFF0000000000000"},
            {"0.1", "3FB999999999999A"},
            {"0.10000000000000001", "3FB999999999999A"},
            {"0.10000000000000002", "3FB999999999999B"},
            {"9007199254740993", "4340000000000000"},
            {"9007199254740995", "4340000000000002"}
        };
        for (final var vector : vectors) {
            assertEquals(
                    decodeUnits(vector[1]),
                    ExactBinary64.units(new BigDecimal(vector[0])),
                    vector[0]);
        }
        // Units cannot prove the sign of zero. Full bits are required in JavaPort replay.
        assertEquals(decodeUnits("0000000000000000"), decodeUnits("8000000000000000"));
    }

    @Test
    void roundingEachCellBeforeSummingDiffersFromEncodingTheDecimalTotal() {
        assertFalse(
                ExactBinary64.units(new BigDecimal("0.1"))
                        .add(ExactBinary64.units(new BigDecimal("0.2")))
                        .equals(ExactBinary64.units(new BigDecimal("0.3"))));
    }

    @Test
    void exactRationalHalfwayCasesNeedBothEvenDirections() {
        final var half = BigDecimal.ONE.divide(new BigDecimal(BigInteger.ONE.shiftLeft(53)));
        assertEquals(
                decodeUnits("3FF0000000000000"), ExactBinary64.units(BigDecimal.ONE.add(half)));
        assertEquals(
                decodeUnits("3FF0000000000002"),
                ExactBinary64.units(BigDecimal.ONE.add(half.multiply(new BigDecimal("3")))));
    }

    @Test
    void decimalPrecisionAndCarrierPrecisionRemainSeparateAxes() {
        final var result =
                project(false, "1", "1900", "46314", false, false, false, 1)
                        .compare(project(false, "1.0", "1900", "46314", false, false, false, 1));
        assertTrue(result.exactDecimalTotalsEqual().get(0));
        assertTrue(result.carrierTotalsEqual().get(0));
        assertFalse(result.fullTuplesEqual());
    }

    @Test
    void bothSupportedEpochCarriersRemainPhysicallyDistinct() {
        final var result =
                project(false, "1", "1900", "46314", false, false, false, 1)
                        .compare(project(false, "1", "1904", "44852", false, false, false, 1));
        assertEquals(1, result.apiRows());
        assertFalse(result.fullTuplesEqual());
        // This reader accepting 1904 is NOT approval of the civil qualifier's 1900-only policy.
    }

    @Test
    void fractionalScopeDayAndMidnightCarrierAmbiguityDoNotDiscardRows() {
        assertEquals(
                1,
                project(false, "1", "1900", "46314.5", false, false, false, 1)
                        .compare(project(false, "1", "1900", "46314.5", false, false, false, 1))
                        .apiRows());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        project(
                                false,
                                "1",
                                "1900",
                                "46314.99999999999999999",
                                false,
                                false,
                                false,
                                1));
    }

    @Test
    void phantomDateAndOutOfPeriodFailTheWholeProjection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "60", false, false, false, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "46313", false, false, false, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "46315", false, false, false, 1));
    }

    @Test
    void missingCellPresentBlankEmptyTextZeroAndBooleanAreNotInterchangeable() {
        final String[] cells = {"@ABSENT", "", "<is><t></t></is>", "<v>0</v>", "<v>1</v>"};
        final String[] types = {"", "", "inlineStr", "", "b"};
        for (int left = 0; left < cells.length; left++) {
            for (int right = 0; right < cells.length; right++) {
                final var a = projectCustom(cells[left], types[left]);
                final var b = projectCustom(cells[right], types[right]);
                assertEquals(left == right, a.compare(b).fullTuplesEqual());
            }
        }
    }

    @Test
    void formulaCachedValueCannotBecomeANumericLiteral() {
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "46314", true, false, false, 1));
    }

    @Test
    void unknownCellTypeAndDuplicatePhysicalCoordinateFail() {
        assertThrows(IllegalArgumentException.class, () -> projectCustom("<v>#VALUE!</v>", "e"));
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "46314", false, false, true, 1));
    }

    @Test
    void wholeZipMustReachEofEvenIfWorksheetAlreadyContainsAllRows() throws Exception {
        final var bytes = book(false, "1", "1900", "46314", false, false, false, 1, null, null);
        final var truncated = java.util.Arrays.copyOf(bytes, bytes.length - 1);
        assertThrows(Exception.class, () -> read(truncated, false));
    }

    @Test
    void duplicateOccurrencesAndMissingDuplicateAreObservable() {
        final var two = project(false, "1", "1900", "46314", false, false, false, 2);
        final var one = project(false, "1", "1900", "46314", false, false, false, 1);
        final var result = two.compare(one);
        assertEquals(2, result.apiRows());
        assertEquals(1, result.apiOnlyOccurrences());
        assertEquals(1, result.changedMultiplicityIds());
        assertFalse(result.fullTuplesEqual());
    }

    @Test
    void extraApiColumnIsAllowedButDoesNotReplaceAnyCommonField() {
        final var api = project(true, "1", "1900", "46314", false, true, false, 1);
        final var ui = project(false, "1", "1900", "46314", false, false, false, 1);
        assertTrue(api.compare(ui).contentEqual());
        assertFalse(
                project(true, "2", "1900", "46314", false, true, false, 1)
                        .compare(ui)
                        .contentEqual());
        assertThrows(
                IllegalArgumentException.class,
                () -> project(false, "1", "1900", "46314", false, true, false, 1));
    }

    @Test
    void sameColumnBagsDoNotPermitCrossRowAssociationChanges() {
        final var columns =
                List.of(
                        new ExactExportTable.Column("ID", 1, 1, false),
                        new ExactExportTable.Column("A", 2, 2, false),
                        new ExactExportTable.Column("B", 3, 3, false));
        final var api = new ExactExportTable(columns, 0, budget());
        final var ui = new ExactExportTable(columns, 0, budget());
        api.add(atoms("same", "0.1", "1"));
        api.add(atoms("same", "0.2", "2"));
        ui.add(atoms("same", "0.1", "2"));
        ui.add(atoms("same", "0.2", "1"));
        api.seal();
        ui.seal();
        final var result = api.compare(ui);
        assertTrue(result.columnsEqual().stream().allMatch(Boolean::booleanValue));
        assertFalse(result.fullTuplesEqual());
        assertEquals(1, result.changedIds());
    }

    private static List<ExactExportTable.Atom> atoms(
            final String id, final String a, final String b) {
        return List.of(
                new ExactExportTable.Atom("TEXT", id),
                new ExactExportTable.Atom("NUMBER", a),
                new ExactExportTable.Atom("NUMBER", b));
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(10));
    }

    private static List<ExactExportTable.Column> columns() {
        final var columns = new ArrayList<ExactExportTable.Column>();
        for (int column = 1; column <= 36; column++) {
            columns.add(
                    new ExactExportTable.Column(
                            "C" + column,
                            column,
                            column,
                            column == 2,
                            column == 2 ? "BINARY64_ENCODING129" : "NONE"));
        }
        return columns;
    }

    private static ExactExportTable project(
            final boolean api,
            final String amount,
            final String epoch,
            final String serial,
            final boolean formula,
            final boolean extra,
            final boolean duplicate,
            final int rows) {
        try {
            return read(
                    book(api, amount, epoch, serial, formula, extra, duplicate, rows, null, null),
                    api);
        } catch (final IllegalArgumentException error) {
            throw error;
        } catch (final Exception error) {
            throw new AssertionError(error);
        }
    }

    private static ExactExportTable projectCustom(final String payload, final String type) {
        try {
            return read(
                    book(false, "1", "1900", "46314", false, false, false, 1, payload, type),
                    false);
        } catch (final IllegalArgumentException error) {
            throw error;
        } catch (final Exception error) {
            throw new AssertionError(error);
        }
    }

    private static ExactExportTable read(final byte[] bytes, final boolean api) throws Exception {
        final var budget = budget();
        final var table = new ExactExportTable(columns(), 0, budget);
        BoundedExportWorkbook.project(bytes, columns(), api, table, budget, DAY, DAY);
        return table;
    }

    private static byte[] book(
            final boolean api,
            final String amount,
            final String epoch,
            final String serial,
            final boolean formula,
            final boolean extra,
            final boolean duplicate,
            final int rows,
            final String custom,
            final String customType)
            throws Exception {
        final int width = extra ? 37 : 36;
        final var sheet =
                new StringBuilder("<worksheet xmlns=\"" + NS + "\"><sheetData><row r=\"1\">");
        for (int column = 1; column <= width; column++) {
            sheet.append("<c r=\"")
                    .append(letters(column))
                    .append("1\" t=\"inlineStr\"><is><t>C")
                    .append(column)
                    .append("</t></is></c>");
        }
        sheet.append("</row>");
        for (int row = 2; row < rows + 2; row++) {
            sheet.append("<row r=\"").append(row).append("\">");
            for (int column = 1; column <= width; column++) {
                if (column == 3 && "@ABSENT".equals(custom)) {
                    continue;
                }
                sheet.append("<c r=\"").append(letters(column)).append(row).append("\"");
                if (column == 3 && custom != null && !customType.isEmpty()) {
                    sheet.append(" t=\"").append(customType).append("\"");
                }
                sheet.append('>');
                if (column == 3 && custom != null) {
                    sheet.append(custom);
                } else {
                    if (formula && column == 2) {
                        sheet.append("<f>1+1</f>");
                    }
                    sheet.append("<v>")
                            .append(column == 36 ? serial : column == 2 ? amount : "1")
                            .append("</v>");
                }
                sheet.append("</c>");
            }
            if (duplicate) {
                sheet.append("<c r=\"A").append(row).append("\"><v>1</v></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        final var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            part(zip, "[Content_Types].xml", "<Types/>");
            part(
                    zip,
                    "xl/workbook.xml",
                    "<workbook xmlns=\""
                            + NS
                            + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<workbookPr date1904=\""
                            + ("1904".equals(epoch) ? "1" : "0")
                            + "\"/><sheets><sheet name=\"Synthetic\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(
                    zip,
                    "xl/_rels/workbook.xml.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            part(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return bytes.toByteArray();
    }

    private static String letters(final int column) {
        return column <= 26
                ? Character.toString((char) ('A' + column - 1))
                : letters((column - 1) / 26) + (char) ('A' + (column - 1) % 26);
    }

    private static void part(final ZipOutputStream zip, final String name, final String value)
            throws Exception {
        final var entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
