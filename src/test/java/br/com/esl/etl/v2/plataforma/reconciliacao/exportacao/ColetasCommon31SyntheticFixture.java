package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Authored fixtures only. Never loads a retained export, a supplier record or a real workbook. */
final class ColetasCommon31SyntheticFixture {
    static final String ATTEMPT = "00000000-0000-4000-8000-000000000001";
    static final String CATALOG_SHA =
            "87841247dc337e37f25c7cef8f1b12f026ba9f1de80ee83ab38cf70c09e56a9c";

    record Field(String item, String title, String source, String kind) {}

    static final List<Field> FIELDS =
            List.of(
                    new Field("item_221149", "ID", "id", "integer"),
                    new Field("item_123127", "Filial", "pck_crn_psn_nickname", "text"),
                    new Field("item_123120", "N° coleta", "sequence_code", "integer"),
                    new Field("item_135309", "Data criação", "created_at", "timestamp"),
                    new Field("item_124818", "Data Solicitação", "request_date", "date"),
                    new Field("item_123121", "Data Agendamento", "service_date", "date"),
                    new Field("item_123134", "Data Coleta", "finish_date", "date"),
                    new Field("item_123130", "Cliente", "pck_cor_nickname", "text"),
                    new Field("item_123122", "Volumes", "invoices_volumes", "integer"),
                    new Field("item_135308", "Peso real", "invoices_weight", "decimal"),
                    new Field("item_123123", "Peso taxado", "taxed_weight", "decimal"),
                    new Field("item_123124", "Valor total", "invoices_value", "decimal"),
                    new Field("item_123125", "Status", "status", "status"),
                    new Field(
                            "item_123133", "Endereço de Coleta/Cidade", "pck_pds_cty_name", "text"),
                    new Field(
                            "item_135313", "Endereço de Coleta/UF", "pck_pds_cty_sae_code", "text"),
                    new Field(
                            "item_135311", "Endereço de coleta/CEP", "pck_pds_postal_code", "text"),
                    new Field(
                            "item_135312",
                            "Endereço de coleta/Bairro",
                            "pck_pds_neighborhood",
                            "text"),
                    new Field("item_167859", "Região da Coleta", "pck_prn_name", "text"),
                    new Field("item_167860", "Filial Responsável", "pck_prn_drt_nickname", "text"),
                    new Field("item_131395", "Data atualização", "updated_at", "timestamp"),
                    new Field(
                            "item_167861", "Última Ocorrência", "pck_loe_ore_description", "text"),
                    new Field("item_123131", "Ocorrência", "pck_pus_ore_description", "text"),
                    new Field("item_123132", "Ação da Ocorrência", "pck_pus_ore_trigger", "text"),
                    new Field("item_135306", "Usuário/Nome", "pck_uer_name", "text"),
                    new Field(
                            "item_131392", "Usuário do cancelamento/Nome", "pck_ctr_name", "text"),
                    new Field(
                            "item_123126", "Motivo de cancelamento", "cancellation_reason", "text"),
                    new Field("item_135300", "Nº Tentativas", "pck_mik_attempt_number", "integer"),
                    new Field(
                            "item_135301",
                            "Manifesto/Filial Origem",
                            "pck_mik_mft_crn_psn_nickname",
                            "text"),
                    new Field(
                            "item_135302",
                            "Manifesto/Número",
                            "pck_mik_mft_sequence_code",
                            "integer"),
                    new Field(
                            "item_135303",
                            "Manifesto/Veículo",
                            "pck_mik_mft_vie_license_plate",
                            "text"),
                    new Field(
                            "item_135305",
                            "Manifesto/Tipo do Veículo",
                            "pck_mik_mft_vie_vee_name",
                            "text"));

    record Cell(String type, String value, int style) {
        static Cell text(String value) {
            return new Cell("inlineStr", value, 0);
        }

        static Cell number(String value) {
            return new Cell("n", value, 0);
        }

        static Cell number(String value, int style) {
            return new Cell("n", value, style);
        }

        static Cell blank(int style) {
            return new Cell("BLANK", "", style);
        }
    }

    final ObjectNode source = sourceRow();
    final List<ObjectNode> sources = new ArrayList<>(List.of(source));
    final List<Cell> row = nativeRow();
    final List<List<Cell>> rows = new ArrayList<>(List.of(row));
    final List<String> headers = new ArrayList<>(FIELDS.stream().map(Field::title).toList());
    final ObjectNode policy = ExportValidationSupport.JSON.createObjectNode();
    final ObjectNode evidence = ExportValidationSupport.JSON.createObjectNode();
    final Path directory;
    Consumer<ObjectNode> policyMutation = ignored -> {};
    Consumer<ObjectNode> evidenceMutation = ignored -> {};
    String rawOverride;
    String epoch = "1900";
    ExportValidationSupport.Pin sourcePin;
    ExportValidationSupport.Pin workbookPin;
    byte[] writtenSource;

    ColetasCommon31SyntheticFixture(Path directory) {
        this.directory = directory;
        policy.put("sourceFormat", "MAPPED_PAYLOAD_JSONL_V1");
        policy.putArray("nullOmissionItems");
        policy.putArray("dateBlankItems");
        policy.putArray("timestampIntegerSecondDisplayItems").add("item_135309").add("item_131395");
        ObjectNode carriers = evidence.putObject("columnCarriers");
        for (Field field : FIELDS) {
            int style =
                    switch (field.kind()) {
                        case "date" -> 1;
                        case "timestamp" -> 2;
                        case "decimal" -> 3;
                        default -> -1;
                    };
            if (style >= 0) {
                carriers.putArray(field.item()).add(carrier(style));
            }
        }
    }

    byte[] nativeWorkbookBytes() throws Exception {
        return workbook(headers, rows, epoch);
    }

    ObjectNode compare() throws Exception {
        StringBuilder lines = new StringBuilder();
        sources.forEach(value -> lines.append(value).append('\n'));
        writtenSource =
                (rawOverride == null ? lines.toString() : rawOverride)
                        .getBytes(StandardCharsets.UTF_8);
        sourcePin = pin(writtenSource, "source", ".jsonl");
        workbookPin = pin(workbook(headers, rows, epoch), "native", ".xlsx");
        evidence.put("schema", "COLETAS_COMMON31_PRESENTATION_EVIDENCE_V1")
                .put("sourceSha256", sourcePin.sha256())
                .put("workbookSha256", workbookPin.sha256())
                .put("catalogSha256", CATALOG_SHA);
        evidenceMutation.accept(evidence);
        var evidencePin =
                pin(ExportValidationSupport.JSON.writeValueAsBytes(evidence), "evidence", ".json");
        policy.put("schema", "COLETAS_COMMON31_POLICY_V1")
                .put("attemptId", ATTEMPT)
                .put("sourceSha256", sourcePin.sha256())
                .put("workbookSha256", workbookPin.sha256());
        policy.set("independentPresentationEvidencePin", pinNode(evidencePin));
        policyMutation.accept(policy);
        var policyPin =
                pin(ExportValidationSupport.JSON.writeValueAsBytes(policy), "policy", ".json");
        return ColetasCommonAdapter.compare(
                request(),
                sourcePin,
                workbookPin,
                policyPin,
                new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
    }

    static ObjectNode request() {
        ObjectNode request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COLETAS_INDEPENDENT_REQUEST_V1")
                        .put("profile", "COLETAS_6908_NATIVE31_V1")
                        .put("reportId", 6908)
                        .put("entity", "COLETAS")
                        .put("zone", "America/Sao_Paulo")
                        .put("attemptId", ATTEMPT)
                        .put("periodStart", "2036-03-20")
                        .put("periodEnd", "2036-03-26");
        ObjectNode filters = request.putObject("filters");
        for (String key :
                List.of(
                        "picks[finish_date]",
                        "picks[service_date]",
                        "picks[request_date]",
                        "picks[corporation_id]",
                        "picks[status]",
                        "scopes[by_updated_at]")) {
            filters.put(key, "");
        }
        filters.put("picks[request_date]", "2036-03-20 - 2036-03-26");
        return request;
    }

    static ObjectNode sourceRow() {
        ObjectNode value = ExportValidationSupport.JSON.createObjectNode();
        for (int index = 0; index < FIELDS.size(); index++) {
            value.put(FIELDS.get(index).source(), "SYNTHETIC-" + (index + 1));
        }
        value.put("id", 101)
                .put("sequence_code", 7)
                .put("invoices_volumes", 2)
                .put("invoices_weight", new BigDecimal("1.25"))
                .put("taxed_weight", new BigDecimal("2.5"))
                .put("invoices_value", new BigDecimal("9.75"))
                .put("pck_mik_attempt_number", 1)
                .put("pck_mik_mft_sequence_code", 42)
                .put("status", "pending")
                .put("pck_pus_ore_trigger", "confirm_scheduling")
                .put("request_date", "2036-03-20")
                .put("service_date", "2036-03-21")
                .put("finish_date", "2036-03-22")
                .put("created_at", "2036-03-20T10:11:12.123456789-03:00")
                .put("updated_at", "2036-03-20T10:12:13.987654321-03:00");
        return value;
    }

    static List<Cell> nativeRow() {
        List<Cell> result = new ArrayList<>();
        for (int index = 0; index < FIELDS.size(); index++) {
            result.add(Cell.text("SYNTHETIC-" + (index + 1)));
        }
        result.set(0, Cell.number("101"));
        result.set(2, Cell.number("7"));
        result.set(3, Cell.number(serial("2036-03-20T10:11:12"), 2));
        result.set(4, Cell.number(serial("2036-03-20T00:00:00"), 1));
        result.set(5, Cell.number(serial("2036-03-21T00:00:00"), 1));
        result.set(6, Cell.number(serial("2036-03-22T00:00:00"), 1));
        result.set(8, Cell.number("2"));
        result.set(9, Cell.number("1.25", 3));
        result.set(10, Cell.number("2.5", 3));
        result.set(11, Cell.number("9.75", 3));
        result.set(12, Cell.text("Pendente"));
        result.set(19, Cell.number(serial("2036-03-20T10:12:13"), 2));
        result.set(22, Cell.text("Confirmar Agendamento"));
        result.set(26, Cell.number("1"));
        result.set(28, Cell.number("42"));
        return result;
    }

    private ExportValidationSupport.Pin pin(byte[] bytes, String prefix, String suffix)
            throws Exception {
        Path path = Files.createTempFile(directory, prefix, suffix);
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path.toAbsolutePath(), ExportValidationSupport.sha256(bytes), bytes.length);
    }

    private static ObjectNode pinNode(ExportValidationSupport.Pin pin) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("path", pin.path().toString())
                .put("sha256", pin.sha256())
                .put("bytes", pin.bytes());
    }

    private static String carrier(int style) {
        return "XLSX:1900:"
                + switch (style) {
                    case 1 -> "DD/MM/YYYY:apply=1:base=0:fmtId=165";
                    case 2 -> "DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164";
                    case 3 -> "0.00:apply=1:base=0:fmtId=166";
                    default -> "builtin:0:apply=1:base=0:fmtId=0";
                };
    }

    private static String serial(String literal) {
        LocalDateTime time = LocalDateTime.parse(literal);
        long days = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), time.toLocalDate());
        return BigDecimal.valueOf(days)
                .add(
                        BigDecimal.valueOf(time.toLocalTime().toSecondOfDay())
                                .divide(BigDecimal.valueOf(86400), 18, RoundingMode.HALF_EVEN))
                .toPlainString();
    }

    private static byte[] workbook(List<String> headers, List<List<Cell>> rows, String epoch)
            throws Exception {
        StringBuilder sheet =
                new StringBuilder(
                        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">");
        for (int index = 0; index < headers.size(); index++) {
            cell(sheet, letters(index + 1) + "1", Cell.text(headers.get(index)));
        }
        sheet.append("</row>");
        for (int index = 0; index < rows.size(); index++) {
            int number = index + 2;
            sheet.append("<row r=\"").append(number).append("\">");
            for (int col = 0; col < rows.get(index).size(); col++) {
                cell(sheet, letters(col + 1) + number, rows.get(index).get(col));
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            part(
                    zip,
                    "[Content_Types].xml",
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            part(
                    zip,
                    "_rels/.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(
                    zip,
                    "xl/workbook.xml",
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\""
                            + ("1904".equals(epoch) ? "1" : "0")
                            + "\"/><sheets><sheet name=\"SYNTHETIC\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(
                    zip,
                    "xl/_rels/workbook.xml.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            part(
                    zip,
                    "xl/styles.xml",
                    "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"DD/MM/YYYY HH:MM:SS\"/><numFmt numFmtId=\"165\" formatCode=\"DD/MM/YYYY\"/><numFmt numFmtId=\"166\" formatCode=\"0.00\"/></numFmts><cellXfs count=\"4\"><xf numFmtId=\"0\" applyNumberFormat=\"1\" xfId=\"0\"/><xf numFmtId=\"165\" applyNumberFormat=\"1\" xfId=\"0\"/><xf numFmtId=\"164\" applyNumberFormat=\"1\" xfId=\"0\"/><xf numFmtId=\"166\" applyNumberFormat=\"1\" xfId=\"0\"/></cellXfs></styleSheet>");
            part(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return bytes.toByteArray();
    }

    private static void part(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void cell(StringBuilder xml, String coordinate, Cell cell) {
        if (cell == null) {
            return;
        }
        xml.append("<c r=\"")
                .append(coordinate)
                .append("\" s=\"")
                .append(cell.style())
                .append("\"");
        if ("BLANK".equals(cell.type())) {
            xml.append("/>");
            return;
        }
        xml.append(" t=\"").append(cell.type()).append("\">");
        if ("inlineStr".equals(cell.type())) {
            xml.append("<is><t xml:space=\"preserve\">")
                    .append(escape(cell.value()))
                    .append("</t></is>");
        } else {
            xml.append("<v>").append(escape(cell.value())).append("</v>");
        }
        xml.append("</c>");
    }

    private static String letters(int column) {
        StringBuilder value = new StringBuilder();
        while (column > 0) {
            value.insert(0, (char) ('A' + (column - 1) % 26));
            column = (column - 1) / 26;
        }
        return value.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
