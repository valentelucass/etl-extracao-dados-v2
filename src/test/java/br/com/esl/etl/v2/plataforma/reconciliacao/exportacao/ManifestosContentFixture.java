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
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Authored values only. No retained workbook, source row or diagnostic expected-result input. */
final class ManifestosContentFixture {
    record Cell(String type, String value, int style) {}

    record Pins(ExportValidationSupport.Pin source, ExportValidationSupport.Pin workbook) {}

    final Path directory;
    final JsonNode catalog;
    final ObjectNode request;
    final List<ObjectNode> records = new ArrayList<>();
    final List<Map<Integer, Cell>> nativeRows = new ArrayList<>();
    String epoch = "0";
    String timestampFormat = "DD/MM/YYYY HH:MM:SS";
    String entity = "MANIFESTOS";
    JsonNode template = ExportValidationSupport.JSON.getNodeFactory().numberNode(6399);

    ManifestosContentFixture(final Path directory, final int count) throws Exception {
        this.directory = directory;
        catalog = ManifestosContentAdapter.catalog();
        request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("entity", "MANIFESTOS")
                        .put("reportId", 6399)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", "2024-02-01")
                        .put("periodEnd", "2024-02-01")
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        ManifestosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "2024-02-01 - 2024-02-01");
        final Map<String, String> codes =
                Map.of(
                        "cargo_type",
                        "fractioned",
                        "mft_mdr_contract_type",
                        "company",
                        "mdfe_status",
                        "pending",
                        "contract_type",
                        "aggregate",
                        "mft_cat_cot_status",
                        "closed",
                        "calculation_type",
                        "agreed",
                        "status",
                        "closed");
        final Map<Integer, String> labels =
                Map.of(
                        19,
                        "Finalizado",
                        21,
                        "Encerrado",
                        23,
                        "Pendente",
                        45,
                        "Agregado",
                        46,
                        "Próprio",
                        47,
                        "Combinado",
                        48,
                        "Fracionada");
        for (int occurrence = 1; occurrence <= count; occurrence++) {
            final var record = ExportValidationSupport.JSON.createObjectNode();
            final var cells = new LinkedHashMap<Integer, Cell>();
            for (final var field : catalog.required("columns")) {
                final String name = field.required("sourceField").textValue();
                final int column = field.required("nativeColumn").intValue();
                switch (field.required("kind").textValue()) {
                    case "integer" -> {
                        final int value = column == 3 ? occurrence : column;
                        record.put(name, value);
                        cells.put(column, number(Integer.toString(value)));
                    }
                    case "decimal" -> {
                        record.put(name, "0.1");
                        cells.put(column, number("0.1"));
                    }
                    case "timestamp" -> {
                        final String hour =
                                "closed_at".equals(name)
                                        ? "15"
                                        : "finished_at".equals(name) ? "18" : "12";
                        record.put(name, "2024-02-01T" + hour + ":00:00-03:00");
                        cells.put(
                                column,
                                new Cell(
                                        null,
                                        serial(
                                                column == 12
                                                        ? "0.625"
                                                        : column == 13 ? "0.75" : "0.5"),
                                        1));
                    }
                    case "boolean" -> {
                        record.put(name, false);
                        cells.put(column, text("Não"));
                    }
                    case "enum" -> {
                        record.put(name, codes.get(name));
                        cells.put(column, text(labels.get(column)));
                    }
                    case "array" -> {
                        final var array = record.putArray(name);
                        array.addArray().add("Alpha").add("Alpha");
                        array.addArray().add("Beta");
                        cells.put(column, text("Alpha: Alpha: Beta"));
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

    ObjectNode compare() throws Exception {
        final var pins = pins();
        return ManifestosContentAdapter.compare(request, pins.source(), pins.workbook(), budget());
    }

    Pins pins() throws Exception {
        return new Pins(pin("source.jsonl", sourceBytes()), pin("native.xlsx", workbookBytes()));
    }

    ExportValidationSupport.Pin pin(final String suffix, final byte[] bytes) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + "-" + suffix).toAbsolutePath();
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }

    byte[] sourceBytes() throws Exception {
        final var output = new ByteArrayOutputStream();
        for (final var record : records) {
            final var envelope =
                    ExportValidationSupport.JSON.createObjectNode().put("entity", entity);
            envelope.set("templateId", template);
            envelope.set("record", record);
            output.write(ExportValidationSupport.JSON.writeValueAsBytes(envelope));
            output.write('\n');
        }
        return output.toByteArray();
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
                        + "<sheet name=\"AuthoredManifestos91\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put(
                "xl/styles.xml",
                "<styleSheet xmlns=\""
                        + ns
                        + "\"><numFmts count=\"1\">"
                        + "<numFmt numFmtId=\"164\" formatCode=\""
                        + escape(timestampFormat)
                        + "\"/></numFmts><cellXfs count=\"3\">"
                        + "<xf numFmtId=\"0\" xfId=\"0\"/><xf numFmtId=\"164\" applyNumberFormat=\"1\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"164\" applyNumberFormat=\"1\" xfId=\"0\"/>"
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
                ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(2024, 2, 1));
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
