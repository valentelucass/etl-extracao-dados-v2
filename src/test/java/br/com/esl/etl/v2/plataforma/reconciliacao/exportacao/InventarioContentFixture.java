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
final class InventarioContentFixture {
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
    String entity = "INVENTARIO";
    JsonNode template = ExportValidationSupport.JSON.getNodeFactory().numberNode(10633);
    String numericLexeme;
    String sequenceCodeLexeme;
    String sourceSuffix = "";
    boolean finalLf = true;

    InventarioContentFixture(final Path directory, final int count) throws Exception {
        this.directory = directory;
        catalog = authoredCatalog();
        request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INVENTARIO_INDEPENDENT_REQUEST_V1")
                        .put("profile", InventarioIndependentRequest.PROFILE)
                        .put("entity", "INVENTARIO")
                        .put("reportId", 10633)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", "2026-10-12")
                        .put("periodEnd", "2026-10-18")
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        InventarioIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(InventarioIndependentRequest.STARTED_AT_FILTER, "2026-10-12 - 2026-10-18");
        for (int occurrence = 1; occurrence <= count; occurrence++) {
            final var record = ExportValidationSupport.JSON.createObjectNode();
            final var cells = new LinkedHashMap<Integer, Cell>();
            for (final var field : catalog.required("columns")) {
                final String name = field.required("sourceField").textValue();
                final int column = field.required("nativeColumn").intValue();
                switch (field.required("kind").textValue()) {
                    case "integer" -> {
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
        return InventarioContentAdapter.compare(
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
                        .put("schema", "INVENTARIO_NATIVE26_CANDIDATE_CATALOG_V1")
                        .put("profile", InventarioIndependentRequest.PROFILE)
                        .put("entity", "INVENTARIO")
                        .put("reportId", 10633)
                        .put("idColumnIndex", 8)
                        .put("totalColumnCount", 6)
                        .put("catalogNature", "AUTHORED_SYNTHETIC")
                        .put("presentationQualified", false)
                        .put("contentAccepted", false);
        final List<String> bindings =
                List.of(
                        "cnr_c_s_fit_corporation_sequence_number|Nº Minuta|numericDecimal",
                        "cnr_c_s_fit_pyr_nickname|Pagador|text",
                        "cnr_c_s_fit_sdr_nickname|Remetente|text",
                        "cnr_c_s_fit_sdr_ads_cty_name|Origem|text",
                        "cnr_c_s_fit_rpt_nickname|Destinatário|text",
                        "cnr_c_s_fit_rpt_ads_cty_name|Destino|text",
                        "cnr_c_s_fit_dyn_drt_nickname|Região Entrega|text",
                        "cnr_c_s_fit_dyn_name|Filial Entregadora|text",
                        "sequence_code|N° Ordem|integer",
                        "cnr_crn_psn_nickname|Filial|text",
                        "type|Tipo|enum",
                        "started_at|Data/Hora início|timestamp",
                        "finished_at|Data/Hora fim|timestamp",
                        "status|Status|enum",
                        "cnr_cis_eoe_psn_name|Conferente|text",
                        "cnr_c_s_fit_invoices_mapping|Notas Fiscais|stringArray",
                        "cnr_c_s_fit_invoices_value|Valor de NF|decimalString",
                        "cnr_c_s_fit_real_weight|Peso Real|decimalString",
                        "cnr_c_s_fit_total_cubic_volume|M³|decimalString",
                        "cnr_c_s_fit_taxed_weight|Peso Taxado|decimalString",
                        "cnr_c_s_fit_invoices_volumes|Volumes|numericDecimal",
                        "cnr_c_s_read_volumes|Volumes Lidos|numericDecimal",
                        "cnr_c_s_fit_dpn_delivery_prediction_at|Previsão de Entrega|timestamp",
                        "cnr_c_s_fit_dpn_performance_finished_at|Finalização da Performance|timestamp",
                        "cnr_c_s_fit_fte_lce_occurrence_at|Data Última Ocorrência|timestamp",
                        "cnr_c_s_fit_fte_lce_ore_description|Ocorrência|text");
        final var columns = catalog.putArray("columns");
        int coordinate = 0;
        for (final String binding : bindings) {
            final String[] parts = binding.split("\\|", -1);
            final String kind = parts[2];
            final var field =
                    columns.addObject()
                            .put("nativeColumn", ++coordinate)
                            .put("sourceField", parts[0])
                            .put("title", parts[1])
                            .put("kind", kind)
                            .put("total", coordinate >= 17 && coordinate <= 22)
                            .put("nullPresentation", "STRICT")
                            .put("emptyStringPresentation", "STRICT");
            if (Set.of("integer", "numericDecimal", "decimalString", "timestamp").contains(kind)) {
                field.put(
                        "nativeCarrier",
                        "timestamp".equals(kind)
                                ? "XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164"
                                : "XLSX:1900:builtin:0:apply=null:base=0:fmtId=0");
            }
            if (Set.of("numericDecimal", "decimalString").contains(kind)) {
                field.put("nativeNumberEncoding", "DIRECT_BINARY64_NEAREST_EVEN_V1");
            }
            if ("stringArray".equals(kind)) {
                field.put("arraySeparator", " | ");
            }
        }
        final var enums = catalog.putObject("enumLabels");
        enums.putObject("status").put("authored_pending", "Authored pending");
        enums.putObject("type").put("authored_pending", "Authored pending");
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
                                "\"cnr_c_s_fit_invoices_volumes\":0.1",
                                "\"cnr_c_s_fit_invoices_volumes\":" + numericLexeme);
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
                        + "<sheet name=\"AuthoredInventario26\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put(
                "xl/styles.xml",
                "<styleSheet xmlns=\""
                        + ns
                        + "\"><numFmts count=\"2\">"
                        + "<numFmt numFmtId=\"164\" formatCode=\""
                        + escape(timestampFormat)
                        + "\"/>"
                        + "<numFmt numFmtId=\"165\" formatCode=\""
                        + escape(dateFormat)
                        + "\"/></numFmts><cellXfs count=\"4\">"
                        + "<xf numFmtId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"164\" applyNumberFormat=\"1\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"165\" applyNumberFormat=\"1\" xfId=\"0\"/>"
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
