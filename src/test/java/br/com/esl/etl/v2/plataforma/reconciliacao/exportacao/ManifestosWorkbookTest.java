package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;

class ManifestosWorkbookTest {
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    @Test
    void capturesAllNinetyOneCoordinatesIncluding64And91WithoutLongMaskTruncation()
            throws Exception {
        final var values = values();
        values.put(64, text("column-64"));
        values.put(91, text("column-91"));
        final var row = read(book(columns(), List.of(values))).get(0);
        assertEquals(91, row.headers().size());
        assertEquals(91, row.cells().size());
        assertEquals("column-64", row.cells().get(64).value());
        assertEquals("column-91", row.cells().get(91).value());
        for (int column = 1; column <= 91; column++) {
            assertTrue(row.headers().containsKey(column));
            assertTrue(row.cells().containsKey(column));
        }
        assertEquals(2, row.physicalRow());
        assertEquals("xl/worksheets/sheet1.xml", row.sheet());
        assertEquals("1900", row.epoch());
    }

    @Test
    void omissionAndPhysicalBlankRemainDifferentAtBoth64And91() throws Exception {
        for (final int column : new int[] {64, 91}) {
            final var absent = values();
            absent.remove(column);
            final var absentRow = read(book(columns(), List.of(absent))).get(0);
            assertFalse(absentRow.cells().containsKey(column));
            assertTrue(absentRow.headers().containsKey(column));
            assertEquals(90, absentRow.cells().size());
            final var blank = values();
            blank.put(column, new Cell(null, ""));
            final var blankRow = read(book(columns(), List.of(blank))).get(0);
            assertTrue(blankRow.cells().containsKey(column));
            assertEquals(91, blankRow.cells().size());
            assertEquals("BLANK", blankRow.cells().get(column).type());
            assertEquals("", blankRow.cells().get(column).value());
            assertEquals("XLSX:1900:builtin:0", blankRow.cells().get(column).carrier());
            assertNotEquals(absentRow.cells(), blankRow.cells());
        }
    }

    @Test
    void emptyTextAndNumericZeroAreNotCollapsedIntoBlankAtHighColumns() throws Exception {
        for (final int column : new int[] {64, 91}) {
            final var values = values();
            values.put(column, text(""));
            final var empty = read(book(columns(), List.of(values))).get(0).cells().get(column);
            assertEquals("TEXT", empty.type());
            assertEquals("", empty.value());
            values.put(column, number("0"));
            final var zero = read(book(columns(), List.of(values))).get(0).cells().get(column);
            assertEquals("NUMBER", zero.type());
            assertEquals("0", zero.value());
            assertNotEquals(empty, zero);
        }
    }

    @Test
    void repeatedTitlesKeepDifferentItemsBoundToTheirOwnCoordinatesIncluding60() throws Exception {
        final var values = values();
        values.put(50, text("item-at-50"));
        values.put(60, text("item-at-60"));
        final var row = read(book(columns(), List.of(values))).get(0);
        assertEquals("Retiradas", row.headers().get(50));
        assertEquals("Retiradas", row.headers().get(60));
        assertEquals("item-at-50", row.cells().get(50).value());
        assertEquals("item-at-60", row.cells().get(60).value());
        assertEquals(91, row.headers().size());
        assertNotEquals(row.cells().get(50), row.cells().get(60));
    }

    @Test
    void repeatedRowsKeepBothPhysicalOccurrences() throws Exception {
        final var values = values();
        final var rows = read(book(columns(), List.of(values, values)));
        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).physicalRow());
        assertEquals(3, rows.get(1).physicalRow());
        assertEquals(rows.get(0).cells(), rows.get(1).cells());
    }

    @Test
    void capturedMapsAndCompletedResultAreImmutable() throws Exception {
        final var rows = read(book(columns(), List.of(values())));
        assertThrows(UnsupportedOperationException.class, () -> rows.clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> rows.get(0).headers().put(91, "changed"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> rows.get(0).cells().put(64, new ExactExportTable.Atom("TEXT", "changed")));
    }

    @Test
    void mappingListOrderDoesNotReplacePhysicalColumnIdentity() throws Exception {
        final var declared = new ArrayList<>(columns());
        Collections.reverse(declared);
        final var rows =
                BoundedExportWorkbook.readManifestosRows(
                        book(columns(), List.of(values())), declared, budget());
        assertEquals("64", rows.get(0).cells().get(64).value());
        assertEquals("91", rows.get(0).cells().get(91).value());
    }

    @Test
    void manifestosDoesNotApplyFretesServiceDatePredicateToColumn36() throws Exception {
        final var values = values();
        values.put(36, text("ordinary-manifestos-value"));
        assertEquals(
                "ordinary-manifestos-value",
                read(book(columns(), List.of(values))).get(0).cells().get(36).value());
        values.put(36, number("60"));
        assertEquals("60", read(book(columns(), List.of(values))).get(0).cells().get(36).value());
    }

    @Test
    void epochIsCapturedWithoutReinterpretingNativeNumbers() throws Exception {
        final var row =
                read(archive(sheet(columns(), List.of(values()), "", ""), "1", Map.of())).get(0);
        assertEquals("1904", row.epoch());
        assertEquals("NUMBER", row.cells().get(64).type());
        assertEquals("XLSX:1904:builtin:0", row.cells().get(64).carrier());
    }

    @Test
    void declarationMustContainExactly91Columns() throws Exception {
        final byte[] bytes = book(columns(), List.of(values()));
        assertEquals(
                "MANIFESTOS_NATIVE_EXACT91",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        BoundedExportWorkbook.readManifestosRows(
                                                bytes, columns().subList(0, 90), budget()))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedExportWorkbook.readManifestosRows(bytes, null, budget()));
        final var extra = new ArrayList<>(columns());
        extra.add(new ExactExportTable.Column("extra", 92, 92, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedExportWorkbook.readManifestosRows(bytes, extra, budget()));
    }

    @Test
    void repeatedOrOutOf91RangeIndicesAreRefusedOnBothMappingSides() throws Exception {
        final byte[] bytes = book(columns(), List.of(values()));
        for (final int[] indices :
                new int[][] {{1, 91}, {91, 1}, {92, 91}, {91, 92}, {256, 91}, {91, 256}}) {
            final var declared = new ArrayList<>(columns());
            declared.set(90, new ExactExportTable.Column("Campo91", indices[0], indices[1], false));
            assertEquals(
                    "MANIFESTOS_NATIVE_COLUMN_BINDING",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            BoundedExportWorkbook.readManifestosRows(
                                                    bytes, declared, budget()))
                            .getMessage());
        }
        final var declared = new ArrayList<>(columns());
        declared.set(63, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedExportWorkbook.readManifestosRows(bytes, declared, budget()));
    }

    @Test
    void missingPhysicalHeadersAt64Or91AreRefused() throws Exception {
        for (final int column : new int[] {64, 91}) {
            final var headers = new ArrayList<>(columns());
            headers.remove(column - 1);
            final byte[] bytes = book(headers, List.of(values()));
            assertThrows(IllegalArgumentException.class, () -> read(bytes));
        }
    }

    @Test
    void ninetySecondHeaderIsRefusedEvenIfEveryExpectedHeaderMatches() throws Exception {
        final var headers = new ArrayList<>(columns());
        headers.add(new ExactExportTable.Column("extra", 92, 92, false));
        final byte[] bytes = book(headers, List.of(values()));
        assertEquals(
                "EXPORT_ALL_UI_COLUMNS_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
    }

    @Test
    void everyExpectedHeaderIsCheckedIncluding64And91() throws Exception {
        for (int column = 1; column <= 91; column++) {
            final var headers = new ArrayList<>(columns());
            headers.set(
                    column - 1, new ExactExportTable.Column("divergent", column, column, false));
            final byte[] bytes = book(headers, List.of(values()));
            assertEquals(
                    "EXPORT_HEADER_BINDING",
                    assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
        }
    }

    @Test
    void duplicatePhysicalHeaderCoordinateIsRefusedDespiteRepeatedTitlesBeingAllowed()
            throws Exception {
        final byte[] bytes =
                archive(
                        sheet(columns(), List.of(values()), cell(64, 1, text("Campo64")), ""),
                        "0",
                        Map.of());
        assertEquals(
                "EXPORT_CELL_COLUMN",
                assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
    }

    @Test
    void formulasAt64And91RemainRefused() throws Exception {
        for (final int column : new int[] {64, 91}) {
            final var values = values();
            values.put(column, new Cell(null, "<f>1+1</f><v>2</v>"));
            final byte[] bytes = book(columns(), List.of(values));
            assertEquals(
                    "EXPORT_FORMULA_OR_OPAQUE_CELL",
                    assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
        }
    }

    @Test
    void rowCannotContainColumn92OrARepeated64Coordinate() throws Exception {
        final var outside = values();
        outside.put(92, number("92"));
        final byte[] outsideBytes = book(columns(), List.of(outside));
        assertEquals(
                "EXPORT_CELL_WITHOUT_HEADER",
                assertThrows(IllegalArgumentException.class, () -> read(outsideBytes))
                        .getMessage());
        final byte[] duplicate =
                archive(
                        sheet(columns(), List.of(values()), "", cell(64, 2, number("64"))),
                        "0",
                        Map.of());
        assertEquals(
                "EXPORT_CELL_COLUMN",
                assertThrows(IllegalArgumentException.class, () -> read(duplicate)).getMessage());
    }

    @Test
    void highColumnCellMustPointAtItsActualPhysicalRow() throws Exception {
        final var values = values();
        values.remove(91);
        final byte[] bytes =
                archive(
                        sheet(columns(), List.of(values), "", cell(91, 3, number("91"))),
                        "0",
                        Map.of());
        assertEquals(
                "EXPORT_CELL_ROW_COORDINATE",
                assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
    }

    @Test
    void typedBlankAndOversizedTextKeepExistingStrictCellGuards() throws Exception {
        final var values = values();
        values.put(91, new Cell("inlineStr", ""));
        final byte[] typedBlank = book(columns(), List.of(values));
        assertEquals(
                "EXPORT_TYPED_NON_NUMERIC_BLANK_UNQUALIFIED",
                assertThrows(IllegalArgumentException.class, () -> read(typedBlank)).getMessage());
        values.put(91, text("x".repeat(32769)));
        final byte[] oversized = book(columns(), List.of(values));
        assertEquals(
                "EXPORT_TEXT_BOUND",
                assertThrows(IllegalArgumentException.class, () -> read(oversized)).getMessage());
    }

    @Test
    void truncatedZipAndCorruptedUnconsumedAuxiliaryPartAreRefusedBeforeReturningRows()
            throws Exception {
        final byte[] valid = book(columns(), List.of(values()));
        assertEquals(
                "EXPORT_ZIP_EOF",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> read(Arrays.copyOf(valid, valid.length - 1)))
                        .getMessage());
        final String sentinel = "AUXILIARY_CRC_SENTINEL_91";
        final byte[] corrupted =
                archive(
                        sheet(columns(), List.of(values()), "", ""),
                        "0",
                        Map.of("custom/aux.txt", sentinel));
        final byte[] marker = sentinel.getBytes(StandardCharsets.UTF_8);
        int position = -1;
        for (int offset = 0; offset <= corrupted.length - marker.length; offset++) {
            if (Arrays.equals(
                    marker, Arrays.copyOfRange(corrupted, offset, offset + marker.length))) {
                position = offset;
                break;
            }
        }
        assertTrue(position >= 0);
        corrupted[position] ^= 1;
        assertThrows(IOException.class, () -> read(corrupted));
    }

    @Test
    void existingZipPartLimitIsRetained() throws Exception {
        final var extra = new LinkedHashMap<String, String>();
        for (int part = 0; part < 29; part++) {
            extra.put("custom/part" + part + ".txt", "bounded");
        }
        final byte[] bytes = archive(sheet(columns(), List.of(values()), "", ""), "0", extra);
        assertEquals(
                "EXPORT_ZIP_PART_BOUND",
                assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
    }

    @Test
    void sparseRowsCannotBypassTheLocalTwelveThousandRowBound() throws Exception {
        final Map<Integer, Cell> sparse = Map.of(64, number("64"), 91, number("91"));
        final byte[] bytes = book(columns(), Collections.nCopies(12001, sparse));
        assertEquals(
                "MANIFESTOS_NATIVE_ROW_BOUND",
                assertThrows(IllegalArgumentException.class, () -> read(bytes)).getMessage());
    }

    @Test
    void worksheetMustCloseCompletelyBeforeRowsCanBeReturned() throws Exception {
        final String incomplete =
                sheet(columns(), List.of(values()), "", "").replace("</sheetData></worksheet>", "");
        final byte[] bytes = archive(incomplete, "0", Map.of());
        assertThrows(XMLStreamException.class, () -> read(bytes));
    }

    private static List<BoundedExportWorkbook.CapturedRow> read(final byte[] bytes)
            throws Exception {
        return BoundedExportWorkbook.readManifestosRows(bytes, columns(), budget());
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(150));
    }

    private static List<ExactExportTable.Column> columns() {
        final var columns = new ArrayList<ExactExportTable.Column>();
        for (int column = 1; column <= 91; column++) {
            final String title = column == 50 || column == 60 ? "Retiradas" : "Campo" + column;
            columns.add(new ExactExportTable.Column(title, column, column, false));
        }
        return List.copyOf(columns);
    }

    private static Map<Integer, Cell> values() {
        final var values = new LinkedHashMap<Integer, Cell>();
        for (int column = 1; column <= 91; column++) {
            values.put(column, number(Integer.toString(column)));
        }
        return values;
    }

    private record Cell(String type, String xml) {}

    private static Cell number(final String value) {
        return new Cell(null, "<v>" + value + "</v>");
    }

    private static Cell text(final String value) {
        return new Cell("inlineStr", "<is><t>" + value + "</t></is>");
    }

    private static String cell(final int column, final int row, final Cell value) {
        return "<c r=\""
                + letters(column)
                + row
                + "\""
                + (value.type() == null ? "" : " t=\"" + value.type() + "\"")
                + ">"
                + value.xml()
                + "</c>";
    }

    private static String letters(final int column) {
        return column <= 26
                ? Character.toString((char) ('A' + column - 1))
                : letters((column - 1) / 26) + (char) ('A' + (column - 1) % 26);
    }

    private static byte[] book(
            final List<ExactExportTable.Column> headers, final List<Map<Integer, Cell>> rows)
            throws Exception {
        return archive(sheet(headers, rows, "", ""), "0", Map.of());
    }

    private static String sheet(
            final List<ExactExportTable.Column> headers,
            final List<Map<Integer, Cell>> rows,
            final String extraHeader,
            final String extraRow) {
        final var xml =
                new StringBuilder("<worksheet xmlns=\"" + NS + "\"><sheetData><row r=\"1\">");
        for (final var header : headers) {
            xml.append(cell(header.uiColumn(), 1, text(header.name())));
        }
        xml.append(extraHeader).append("</row>");
        int physicalRow = 2;
        for (final var row : rows) {
            xml.append("<row r=\"").append(physicalRow).append("\">");
            for (final var value : row.entrySet()) {
                xml.append(cell(value.getKey(), physicalRow, value.getValue()));
            }
            xml.append(extraRow).append("</row>");
            physicalRow++;
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static byte[] archive(
            final String sheet, final String date1904, final Map<String, String> auxiliary)
            throws Exception {
        final var parts = new LinkedHashMap<String, String>();
        parts.put("[Content_Types].xml", "<Types/>");
        parts.put(
                "xl/workbook.xml",
                "<workbook xmlns=\""
                        + NS
                        + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<workbookPr date1904=\""
                        + date1904
                        + "\"/><sheets>"
                        + "<sheet name=\"SyntheticManifestos91\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put("xl/worksheets/sheet1.xml", sheet);
        parts.putAll(auxiliary);
        final var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (final var part : parts.entrySet()) {
                final byte[] content = part.getValue().getBytes(StandardCharsets.UTF_8);
                final var crc = new CRC32();
                crc.update(content);
                final var entry = new ZipEntry(part.getKey());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
