package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Entirely authored fixture; enum names/labels are invented test data, never business evidence. */
final class SinistrosContentFixture {
    record Cell(String type, String value, int style) {}

    record Pins(
            ExportValidationSupport.Pin source,
            ExportValidationSupport.Pin workbook,
            ExportValidationSupport.Pin catalog) {}

    final Path directory;
    final ObjectNode catalog;
    final ObjectNode request;
    final List<ObjectNode> records = new ArrayList<>();
    final List<Map<Integer, Cell>> nativeRows = new ArrayList<>();
    String epoch = "0";
    String timestampFormat = "DD/MM/YYYY HH:MM:SS";
    String dateFormat = "DD/MM/YYYY";
    String timeFormat = "HH:MM:SS";
    String entity = "SINISTROS";
    JsonNode template = ExportValidationSupport.JSON.getNodeFactory().numberNode(6392);
    String numericLexeme;
    String sequenceCodeLexeme;
    String sourceSuffix = "";
    boolean finalLf = true;

    SinistrosContentFixture(final Path directory, final int count) throws Exception {
        this.directory = directory;
        catalog = authoredCatalog();
        request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "SINISTROS_INDEPENDENT_REQUEST_V1")
                        .put("profile", SinistrosIndependentRequest.PROFILE)
                        .put("entity", "SINISTROS")
                        .put("reportId", 6392)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", "2026-10-12")
                        .put("periodEnd", "2026-10-18")
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        SinistrosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(SinistrosIndependentRequest.OPENING_AT_DATE_FILTER, "2026-10-12 - 2026-10-18");
        for (int occurrence = 1; occurrence <= count; occurrence++) {
            final var record = ExportValidationSupport.JSON.createObjectNode();
            final var cells = new LinkedHashMap<Integer, Cell>();
            for (final var field : catalog.required("columns")) {
                final String name = field.required("sourceField").textValue();
                final int column = field.required("nativeColumn").intValue();
                switch (field.required("kind").textValue()) {
                    case "positiveIntegralNumber" -> {
                        record.put(name, occurrence);
                        cells.put(column, number(Integer.toString(occurrence)));
                    }
                    case "numericDecimal" -> {
                        record.put(name, new java.math.BigDecimal("0.1"));
                        cells.put(column, number("0.1"));
                    }
                    case "decimalString" -> {
                        record.put(name, "0.1");
                        cells.put(column, number("0.1"));
                    }
                    case "timestamp" -> {
                        record.put(name, "2026-10-13T12:00:00-03:00");
                        cells.put(column, new Cell(null, serial("0.5"), 2));
                    }
                    case "date" -> {
                        record.put(name, "2026-10-13");
                        cells.put(column, new Cell(null, serial("0"), 3));
                    }
                    case "time" -> {
                        record.put(name, "12:00:00");
                        cells.put(column, new Cell(null, "0.5", 4));
                    }
                    case "enum" -> {
                        record.put(name, "authored_pending");
                        cells.put(column, text("Authored pending"));
                    }
                    case "stringArray" -> {
                        record.putArray(name).add("Authored A").add("Authored A").add("Authored B");
                        cells.put(column, text("Authored A | Authored A | Authored B"));
                    }
                    case "text" -> {
                        record.put(name, "authored-" + column + "-" + occurrence);
                        cells.put(column, text("authored-" + column + "-" + occurrence));
                    }
                    default -> throw new IllegalArgumentException("FIXTURE_UNKNOWN_KIND");
                }
            }
            records.add(record);
            nativeRows.add(cells);
        }
    }

    ObjectNode field(final int column) {
        return (ObjectNode) catalog.required("columns").get(column - 1);
    }

    ObjectNode compare() throws Exception {
        final var pins = pins();
        return SinistrosContentAdapter.compare(
                request, pins.source(), pins.workbook(), pins.catalog(), budget());
    }

    Pins pins() throws Exception {
        return new Pins(
                pin("source.jsonl", sourceBytes()),
                pin("native.xlsx", workbookBytes()),
                pin(
                        "authored-catalog.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(catalog)));
    }

    /** Every carrier, enum label, array separator, kind and total here is authored test data. */
    static ObjectNode authoredCatalog() {
        final var catalog =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "SINISTROS_NATIVE44_CANDIDATE_CATALOG_V1")
                        .put("profile", SinistrosIndependentRequest.PROFILE)
                        .put("entity", "SINISTROS")
                        .put("reportId", 6392)
                        .put("idColumnIndex", 0)
                        .put("totalColumnCount", 12)
                        .put("catalogNature", "AUTHORED_SYNTHETIC")
                        .put("presentationQualified", false)
                        .put("contentAccepted", false);
        final var columns = catalog.putArray("columns");
        int coordinate = 0;
        for (final String binding : SinistrosContentAdapter.BINDINGS) {
            final String[] parts = binding.split("\\|", -1);
            final String kind = parts[2];
            final var field =
                    columns.addObject()
                            .put("nativeColumn", ++coordinate)
                            .put("sourceField", parts[0])
                            .put("title", parts[1])
                            .put("kind", kind)
                            .put("total", Set.of("numericDecimal", "decimalString").contains(kind))
                            .put("nullPresentation", "STRICT")
                            .put("emptyStringPresentation", "STRICT");
            if (Set.of(
                            "positiveIntegralNumber",
                            "numericDecimal",
                            "decimalString",
                            "date",
                            "timestamp",
                            "time")
                    .contains(kind)) {
                final String carrier =
                        switch (kind) {
                            case "timestamp" ->
                                    "XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164";
                            case "date" -> "XLSX:1900:DD/MM/YYYY:apply=1:base=0:fmtId=165";
                            case "time" -> "XLSX:1900:HH:MM:SS:apply=1:base=0:fmtId=166";
                            default -> "XLSX:1900:builtin:0:apply=null:base=0:fmtId=0";
                        };
                field.put("nativeCarrier", carrier);
            }
            if (Set.of("numericDecimal", "decimalString").contains(kind)) {
                field.put("nativeNumberEncoding", "DIRECT_BINARY64_NEAREST_EVEN_V1");
            }
            if ("stringArray".equals(kind)) {
                field.put("arraySeparator", " | ").put("emptyArrayPresentation", "STRICT");
            }
            if ("time".equals(kind)) {
                field.put("sourceTimeEncoding", "ISO_LOCAL_TIME_SECOND_V1")
                        .put("nativeTimeEncoding", "FRACTION_DAY_NEAREST_SECOND_V1");
            }
        }
        final var enums = catalog.putObject("enumLabels");
        for (final String name : List.of("icm_ttt_dealing_type", "icm_ttt_solution_type")) {
            enums.putObject(name)
                    .put("authored_pending", "Authored pending")
                    .put("authored_other", "Authored other");
        }
        return catalog;
    }

    ExportValidationSupport.Pin pin(final String suffix, final byte[] bytes) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + "-" + suffix).toAbsolutePath();
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }

    byte[] sourceBytes() throws Exception {
        final var output = new StringBuilder();
        for (int index = 0; index < records.size(); index++) {
            final var envelope =
                    ExportValidationSupport.JSON.createObjectNode().put("entity", entity);
            envelope.set("templateId", template);
            envelope.set("record", records.get(index));
            String line = ExportValidationSupport.JSON.writeValueAsString(envelope);
            if (numericLexeme != null) {
                line =
                        line.replace(
                                "\"invoices_volumes\":0.1",
                                "\"invoices_volumes\":" + numericLexeme);
            }
            if (sequenceCodeLexeme != null) {
                line =
                        line.replace(
                                "\"sequence_code\":1", "\"sequence_code\":" + sequenceCodeLexeme);
            }
            output.append(line);
            if (finalLf || index + 1 < records.size()) {
                output.append('\n');
            }
        }
        return output.append(sourceSuffix).toString().getBytes(StandardCharsets.UTF_8);
    }

    byte[] workbookBytes() throws Exception {
        final String ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        final var sheet =
                new StringBuilder("<worksheet xmlns=\"" + ns + "\"><sheetData><row r=\"1\">");
        for (final var field : catalog.required("columns")) {
            sheet.append(
                    cell(
                            field.required("nativeColumn").intValue(),
                            1,
                            text(field.required("title").textValue())));
        }
        sheet.append("</row>");
        int row = 2;
        for (final var cells : nativeRows) {
            sheet.append("<row r=\"").append(row).append("\">");
            for (final var entry : cells.entrySet()) {
                sheet.append(cell(entry.getKey(), row, entry.getValue()));
            }
            sheet.append("</row>");
            row++;
        }
        sheet.append("</sheetData></worksheet>");
        final var parts = new LinkedHashMap<String, String>();
        parts.put("[Content_Types].xml", "<Types/>");
        parts.put(
                "xl/workbook.xml",
                "<workbook xmlns=\""
                        + ns
                        + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<workbookPr date1904=\""
                        + epoch
                        + "\"/><sheets>"
                        + "<sheet name=\"AuthoredSinistros44\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put(
                "xl/styles.xml",
                "<styleSheet xmlns=\""
                        + ns
                        + "\"><numFmts count=\"3\">"
                        + "<numFmt numFmtId=\"164\" formatCode=\""
                        + escape(timestampFormat)
                        + "\"/>"
                        + "<numFmt numFmtId=\"165\" formatCode=\""
                        + escape(dateFormat)
                        + "\"/>"
                        + "<numFmt numFmtId=\"166\" formatCode=\""
                        + escape(timeFormat)
                        + "\"/></numFmts><cellXfs count=\"5\">"
                        + "<xf numFmtId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"164\" applyNumberFormat=\"1\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"165\" applyNumberFormat=\"1\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"166\" applyNumberFormat=\"1\" xfId=\"0\"/>"
                        + "</cellXfs></styleSheet>");
        parts.put("xl/worksheets/sheet1.xml", sheet.toString());
        final var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (final var part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(150));
    }

    static Cell number(final String value) {
        return new Cell(null, value, 0);
    }

    static Cell text(final String value) {
        return new Cell("inlineStr", value, 0);
    }

    static String serial(final String fraction) {
        final long day =
                ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(2026, 10, 13));
        return java.math.BigDecimal.valueOf(day)
                .add(new java.math.BigDecimal(fraction))
                .toPlainString();
    }

    private static String cell(final int column, final int row, final Cell cell) {
        return "<c r=\""
                + letters(column)
                + row
                + "\" s=\""
                + cell.style()
                + "\""
                + (cell.type() == null ? "" : " t=\"" + cell.type() + "\"")
                + ">"
                + (cell.value() == null
                        ? ""
                        : "inlineStr".equals(cell.type())
                                ? "<is><t>" + escape(cell.value()) + "</t></is>"
                                : "<v>" + cell.value() + "</v>")
                + "</c>";
    }

    private static String letters(final int column) {
        return column <= 26
                ? Character.toString((char) ('A' + column - 1))
                : letters((column - 1) / 26) + (char) ('A' + (column - 1) % 26);
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
