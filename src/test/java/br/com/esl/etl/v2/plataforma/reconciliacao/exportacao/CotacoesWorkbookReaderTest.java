package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class CotacoesWorkbookReaderTest {
    @Test
    void complete37ColumnsRetainDuplicateOccurrencesAndHighPhysicalMaskBits() throws Exception {
        final var read =
                BoundedExportWorkbook.readCotacoesRows(
                        book(37, false, false), columns(37), budget());
        assertEquals("1900", read.epoch());
        assertEquals(2, read.rows().size());
        assertEquals(37, read.rows().get(0).cells().size());
        assertEquals("1", read.rows().get(0).cells().get(35).value());
        assertEquals((1L << 37) - 1, read.rows().get(0).physicalMask());
        assertEquals(read.rows().get(0).cells(), read.rows().get(1).cells());
    }

    @Test
    void cotacoesNeverAppliesTheFretesColumn36ServiceDatePredicate() throws Exception {
        final var read =
                BoundedExportWorkbook.readCotacoesRows(
                        book(37, false, false), columns(37), budget());
        assertEquals("NUMBER", read.rows().get(0).cells().get(35).type());
        assertEquals("1", read.rows().get(0).cells().get(35).value());
    }

    @Test
    void wrongWidthAndMissingHeaderCannotProduceAnAcceptedSubset() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                book(37, false, false), columns(36), budget()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                book(36, false, false), columns(37), budget()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                book(38, false, false), columns(37), budget()));
    }

    @Test
    void formulasAndIncompleteZipNeverCountAsLiteralFullRows() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                book(37, true, false), columns(37), budget()));
        final byte[] complete = book(37, false, false);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                Arrays.copyOf(complete, complete.length - 1),
                                columns(37),
                                budget()));
    }

    @Test
    void repeatedCoordinatesCannotSilentlySkipAnActualNativeColumn() throws Exception {
        final var repeated = new ArrayList<>(columns(37));
        repeated.set(36, repeated.get(0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BoundedExportWorkbook.readCotacoesRows(
                                book(37, false, false), repeated, budget()));
    }

    @Test
    void epochIsRetainedForTheAdapterToDecideExplicitly() throws Exception {
        final var read =
                BoundedExportWorkbook.readCotacoesRows(
                        book(37, false, true), columns(37), budget());
        assertEquals("1904", read.epoch());
        assertTrue(read.rows().get(0).cells().get(0).carrier().startsWith("XLSX:1904:"));
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }

    private static List<ExactExportTable.Column> columns(final int width) {
        final var result = new ArrayList<ExactExportTable.Column>();
        for (int i = 1; i <= width; i++) {
            result.add(new ExactExportTable.Column("C" + i, i, i, false));
        }
        return result;
    }

    private static byte[] book(final int width, final boolean formula, final boolean epoch1904)
            throws Exception {
        final String ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        final var xml =
                new StringBuilder("<worksheet xmlns=\"" + ns + "\"><sheetData><row r=\"1\">");
        for (int i = 1; i <= width; i++) {
            xml.append("<c r=\"")
                    .append(letters(i))
                    .append("1\" t=\"inlineStr\"><is><t>C")
                    .append(i)
                    .append("</t></is></c>");
        }
        xml.append("</row>");
        for (int row = 2; row <= 3; row++) {
            xml.append("<row r=\"").append(row).append("\">");
            for (int i = 1; i <= width; i++) {
                xml.append("<c r=\"")
                        .append(letters(i))
                        .append(row)
                        .append("\">")
                        .append(formula && i == 1 ? "<f>1+0</f>" : "")
                        .append("<v>1</v></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        final var parts = new LinkedHashMap<String, String>();
        parts.put("[Content_Types].xml", "<Types/>");
        parts.put(
                "xl/workbook.xml",
                "<workbook xmlns=\""
                        + ns
                        + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<workbookPr date1904=\""
                        + (epoch1904 ? "1" : "0")
                        + "\"/><sheets>"
                        + "<sheet name=\"SyntheticCotacoes37\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put("xl/worksheets/sheet1.xml", xml.toString());
        final var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (final var entry : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String letters(final int column) {
        return column <= 26
                ? Character.toString((char) ('A' + column - 1))
                : letters((column - 1) / 26) + (char) ('A' + (column - 1) % 26);
    }
}
