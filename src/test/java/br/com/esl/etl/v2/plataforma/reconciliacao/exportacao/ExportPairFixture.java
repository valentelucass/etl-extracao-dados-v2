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
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic-only books and acquisition observations; no business artifacts or network. */
final class ExportPairFixture {
    private static final String XMLNS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    final Path directory;
    final ObjectNode request = ExportValidationSupport.JSON.createObjectNode();
    final ObjectNode config = request.objectNode();
    final ObjectNode mapping = request.objectNode();
    final ObjectNode api = request.objectNode();
    final ObjectNode ui = request.objectNode();
    final ObjectNode apiObservation = request.objectNode();
    final ObjectNode uiObservation = request.objectNode();
    byte[] apiBook;
    byte[] uiBook;

    ExportPairFixture(final Path directory) throws Exception {
        this.directory = directory;
        request.put("schema", "INDEPENDENT_EXPORT_REQUEST_V1");
        request.put("attemptId", UUID.randomUUID().toString());
        request.put("profile", IndependentExportRequest.PROFILE);
        request.put("entity", "FRETES");
        request.put("reportId", 6389);
        request.put("zone", "America/Sao_Paulo");
        request.put("periodStart", "2025-03-03");
        request.put("periodEnd", "2025-03-09");
        final var filters = request.putObject("filters");
        IndependentExportRequest.FRETES_FILTERS.forEach(name -> filters.put(name, ""));
        filters.put("freights[service_at]", "2025-03-03 - 2025-03-09");
        config.put("schema", "INDEPENDENT_EXPORT_CONFIG_V1");
        config.put("profile", IndependentExportRequest.PROFILE);
        config.put("zone", "America/Sao_Paulo");
        config.put("maximumRequestSeparationSeconds", 120);
        config.putArray("dashboardColumns").add("C1");
        mapping.put("schema", "INDEPENDENT_EXPORT_COLUMNS_V1");
        mapping.put("reportId", 6389);
        mapping.put("idColumnIndex", 0);
        mapping.set("columns", ExportColumnCatalog.fretes().required("columns"));
        config.withArray("dashboardColumns")
                .removeAll()
                .add(mapping.path("columns").get(0).path("name").asText());
        observation(api, apiObservation, "API", "2025-03-10T12:00:00-03:00");
        observation(ui, uiObservation, "UI", "2025-03-10T12:00:20-03:00");
        apiBook = book(false, false, false, "1.1");
        uiBook = book(false, false, false, "1.1");
    }

    private void observation(
            final ObjectNode evidence,
            final ObjectNode observed,
            final String side,
            final String time) {
        evidence.put("schema", "INDEPENDENT_EXPORT_ACQUISITION_V1");
        evidence.put("route", side + "_EXPORT_XLSX");
        evidence.put("attemptId", request.path("attemptId").asText());
        evidence.put("cycleId", request.path("attemptId").asText());
        evidence.put("requestId", side + "_REQUEST_1");
        evidence.put("fileId", side + "_FILE_1");
        evidence.put("periodStart", request.path("periodStart").asText());
        evidence.put("periodEnd", request.path("periodEnd").asText());
        evidence.put("reportId", 6389);
        evidence.put("zone", request.path("zone").asText());
        evidence.put("timePrecision", "SECOND");
        evidence.put("requestedAt", time);
        evidence.set("filters", request.required("filters").deepCopy());
        for (final String field :
                List.of(
                        "requestId",
                        "fileId",
                        "attemptId",
                        "requestedAt",
                        "zone",
                        "timePrecision",
                        "filters")) {
            observed.set(field, evidence.required(field).deepCopy());
        }
        observed.put("event", "UI".equals(side) ? "UI_EXPORT_CLICK" : "API_EXPORT_POST");
    }

    void uiTime(final String time, final String precision) {
        ui.put("requestedAt", time).put("timePrecision", precision);
        uiObservation.put("requestedAt", time).put("timePrecision", precision);
    }

    ObjectNode pin(final String name, final byte[] bytes) throws Exception {
        final Path file = directory.resolve(name).toAbsolutePath();
        Files.write(file, bytes);
        return request.objectNode()
                .put("path", file.toString())
                .put("sha256", ExportValidationSupport.sha256(bytes))
                .put("bytes", bytes.length);
    }

    private ObjectNode jsonPin(final String name, final JsonNode value) throws Exception {
        return pin(name, ExportValidationSupport.JSON.writeValueAsBytes(value));
    }

    ObjectNode manifest() throws Exception {
        final var pair = request.objectNode();
        pair.put("schema", "INDEPENDENT_EXPORT_PAIR_V1");
        final var requestPin = jsonPin("request.json", request);
        pair.set("requestPin", requestPin);
        pair.set("configPin", jsonPin("configuration.json", config));
        pair.set("mappingPin", jsonPin("mapping.json", mapping));
        for (final String side : List.of("api", "ui")) {
            final var evidence = "api".equals(side) ? api : ui;
            final var observed = "api".equals(side) ? apiObservation : uiObservation;
            final var dataPin = pin(side + ".xlsx", "api".equals(side) ? apiBook : uiBook);
            evidence.put("requestSha256", requestPin.path("sha256").asText());
            evidence.set("dataPin", dataPin);
            evidence.set("observationPin", jsonPin(side + "-observation.json", observed));
            final var descriptor = pair.putObject(side);
            descriptor.set("dataPin", dataPin);
            descriptor.set("evidencePin", jsonPin(side + "-evidence.json", evidence));
        }
        final var admission = pair.deepCopy();
        admission.put("schema", "INDEPENDENT_EXPORT_PAIR_BINDING_V1");
        admission.put("attemptId", request.path("attemptId").asText());
        pair.set("admissionPin", jsonPin("admission.json", admission));
        return pair;
    }

    ObjectNode compare() throws Exception {
        return compare(manifest());
    }

    static ObjectNode compare(final JsonNode manifest) throws Exception {
        return IndependentExportValidation.compare(
                manifest, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
    }

    static byte[] book(
            final boolean extra,
            final boolean formula,
            final boolean epoch1904,
            final String firstAmount)
            throws Exception {
        final var sheet =
                new StringBuilder("<worksheet xmlns=\"" + XMLNS + "\"><sheetData><row r=\"1\">");
        final int width = extra ? 115 : 114;
        final var declared = ExportColumnCatalog.fretes().required("columns");
        int amountColumn = 0;
        for (int index = 0; index < declared.size(); index++) {
            if (amountColumn == 0
                    && "BINARY64_ENCODING129"
                            .equals(declared.get(index).path("totalKind").asText())) {
                amountColumn = index + 1;
            }
        }
        for (int column = 1; column <= width; column++) {
            final String title =
                    column > 114 ? "API Extra" : declared.get(column - 1).path("name").asText();
            sheet.append("<c r=\"")
                    .append(letters(column))
                    .append("1\" t=\"inlineStr\"><is><t>")
                    .append(title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .append("</t></is></c>");
        }
        sheet.append("</row><row r=\"2\">");
        for (int column = 1; column <= width; column++) {
            sheet.append("<c r=\"").append(letters(column)).append("2\">");
            if (formula && column == 2) {
                sheet.append("<f>1+1</f>");
            }
            final String serial =
                    Long.toString(
                            ChronoUnit.DAYS.between(
                                    epoch1904
                                            ? LocalDate.of(1904, 1, 1)
                                            : LocalDate.of(1899, 12, 30),
                                    LocalDate.of(2025, 3, 3)));
            sheet.append("<v>")
                    .append(column == 36 ? serial : column == amountColumn ? firstAmount : "1")
                    .append("</v></c>");
        }
        sheet.append("</row></sheetData></worksheet>");
        final var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            part(zip, "[Content_Types].xml", "<Types/>");
            part(
                    zip,
                    "xl/workbook.xml",
                    "<workbook xmlns=\""
                            + XMLNS
                            + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<workbookPr date1904=\""
                            + (epoch1904 ? "1" : "0")
                            + "\"/><sheets>"
                            + "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
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

    private static void part(final ZipOutputStream zip, final String name, final String text)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
