package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.consolidacoes.aplicacao.ConsolidacaoDataExportDto;
import br.com.esl.etl.v2.modulos.consolidacoes.aplicacao.ConsolidacaoDataExportMapper;
import br.com.esl.etl.v2.modulos.consolidacoes.aplicacao.ExtrairConsolidacaoDataExport;
import br.com.esl.etl.v2.modulos.ocorrencias.aplicacao.ExtrairOcorrenciaDataExport;
import br.com.esl.etl.v2.modulos.ocorrencias.aplicacao.OcorrenciaDataExportDto;
import br.com.esl.etl.v2.modulos.ocorrencias.aplicacao.OcorrenciaDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappedReports0761Test {
    private static final BusinessDateRange DAY =
            new BusinessDateRange(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6));
    private static final ReportSnapshotExtractor.Limits LIMITS =
            new ReportSnapshotExtractor.Limits(4, 100, 1000);

    @Test
    void all59ColumnsHaveTypedDispositionAndRetainNullPresence() throws Exception {
        final var nullableConsolidation = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        nullableConsolidation.putNull("ctd_ant_document");
        final var con = new ConsolidacaoDataExportMapper().map(nullableConsolidation);
        final var nullableOccurrence = row(ReportSnapshotTemplate.OCORRENCIAS);
        nullableOccurrence.putNull("ice_ttt_solution_comments");
        final var oco = new OcorrenciaDataExportMapper().map(nullableOccurrence);
        assertTrue(con.valid());
        assertTrue(oco.valid());
        assertEquals(39, con.getClass().getRecordComponents().length);
        assertEquals(20, oco.getClass().getRecordComponents().length);
        assertEquals(ExpansionValue.Presence.NULL, con.ctdAntDocument().presence());
        assertEquals(ExpansionValue.Presence.NULL, oco.iceTttSolutionComments().presence());
        assertEquals("0000123", con.ctdSdrDocument().value());
    }

    @Test
    void exactDecimalDynamicChargeKeysAndDuplicatesSurvive() throws Exception {
        final var input = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        input.put("charged_value", "12345678901234567890.12345678");
        final var mapped = new ConsolidacaoDataExportMapper().map(input);
        assertTrue(mapped.valid());
        assertEquals(
                new BigDecimal("12345678901234567890.12345678"), mapped.chargedValue().value());
        assertEquals(
                new BigDecimal("0.1234567890123456789"),
                mapped.chargedValueDetails().value().amounts().get("TAXA NOVA"));
        assertEquals(3, mapped.ctdFgsInvoicesMapping().value().items().size());
        assertEquals(
                mapped.ctdFgsInvoicesMapping().value().items().get(0),
                mapped.ctdFgsInvoicesMapping().value().items().get(1));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mapped.chargedValueDetails()
                                .value()
                                .amounts()
                                .put("ALTERADA", BigDecimal.ONE));
    }

    @Test
    void timestampFractionOffsetAndFutureUnknownEnumRemainIntact() throws Exception {
        final var input = row(ReportSnapshotTemplate.OCORRENCIAS);
        input.put("delivery_status", "future_status");
        final var result = new OcorrenciaDataExportMapper().map(input);
        assertTrue(result.valid());
        assertEquals("future_status", result.deliveryStatus().value());
        assertEquals("2026-11-06T12:30:12.123456-03:00", result.occurrenceAt().raw());
        assertEquals(Instant.parse("2026-11-06T15:30:12.123456Z"), result.occurrenceAt().value());
    }

    @Test
    void all59ColumnsNowHaveNonNullQualification() throws Exception {
        for (final var template :
                List.of(
                        ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA,
                        ReportSnapshotTemplate.OCORRENCIAS)) {
            final var input = row(template);
            for (final var field : contract(template).path("fields")) {
                assertTrue(field.path("nonNullQualified").booleanValue());
            }
            assertTrue(
                    template == ReportSnapshotTemplate.OCORRENCIAS
                            ? new OcorrenciaDataExportMapper().map(input).valid()
                            : new ConsolidacaoDataExportMapper().map(input).valid());
        }
    }

    @Test
    void antDocumentPreservesTextZerosWhitespaceAndNulls() throws Exception {
        final var input = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        input.put("ctd_ant_document", " 00012 ");
        final var mapped = new ConsolidacaoDataExportMapper().map(input);
        assertTrue(mapped.valid());
        assertEquals(" 00012 ", mapped.ctdAntDocument().value());
        assertEquals(mapped.ctdAntDocument().raw(), mapped.ctdAntDocument().value());
        assertEquals(ExpansionValue.Wire.STRING, mapped.ctdAntDocument().wire());
        input.putNull("ctd_ant_document");
        assertEquals(
                ExpansionValue.Presence.NULL,
                new ConsolidacaoDataExportMapper().map(input).ctdAntDocument().presence());
    }

    @Test
    void antDocumentRejectsOtherWiresAndBoundsWithoutTruncation() throws Exception {
        final var input = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        input.put("ctd_ant_document", 12);
        assertFalse(new ConsolidacaoDataExportMapper().map(input).valid());
        input.putObject("ctd_ant_document").put("value", "0012");
        assertFalse(new ConsolidacaoDataExportMapper().map(input).valid());
        input.put("ctd_ant_document", "x".repeat(4000));
        assertEquals(
                4000,
                new ConsolidacaoDataExportMapper().map(input).ctdAntDocument().value().length());
        input.put("ctd_ant_document", "x".repeat(4001));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsolidacaoDataExportMapper().map(input));
    }

    @Test
    void completeWindowQualifiesOptionalTimestampsAndTextWithoutLosingRaw() throws Exception {
        final var input = row(ReportSnapshotTemplate.OCORRENCIAS);
        input.put("occurrence_schedule_at", "2026-11-06T12:30:12.123456789-03:00");
        input.put("ice_ttt_solution_deadline", "2026-11-07T01:00:00.000-03:00");
        input.put("ice_ttt_solution_at", "2026-11-07T04:00:00Z");
        input.put("ice_ttt_solution_type", "synthetic_future_code");
        input.put("ice_ttt_soe_description", "SYNTHETIC descrição");
        input.put("ice_ttt_solution_comments", "SYNTHETIC\ncomment");
        final var mapped = new OcorrenciaDataExportMapper().map(input);
        assertTrue(mapped.valid());
        assertEquals(
                Instant.parse("2026-11-06T15:30:12.123456789Z"),
                mapped.occurrenceScheduleAt().value());
        assertEquals("2026-11-06T12:30:12.123456789-03:00", mapped.occurrenceScheduleAt().raw());
        assertEquals(
                Instant.parse("2026-11-07T04:00:00Z"), mapped.iceTttSolutionDeadline().value());
        assertEquals(mapped.iceTttSolutionDeadline().value(), mapped.iceTttSolutionAt().value());
        assertEquals("synthetic_future_code", mapped.iceTttSolutionType().value());
        assertEquals("SYNTHETIC descrição", mapped.iceTttSoeDescription().value());
        assertEquals("SYNTHETIC\ncomment", mapped.iceTttSolutionComments().value());
        input.put("ice_ttt_solution_comments", "x".repeat(4000));
        final var wide = new OcorrenciaDataExportMapper().map(input);
        assertTrue(wide.valid());
        assertEquals(4000, wide.iceTttSolutionComments().value().length());
        input.put("ice_ttt_solution_comments", "x".repeat(4001));
        assertThrows(
                IllegalArgumentException.class, () -> new OcorrenciaDataExportMapper().map(input));
    }

    @Test
    void nowQualifiedOptionalFieldsRejectWrongShapesAndStillAcceptNulls() throws Exception {
        for (final String name :
                List.of(
                        "occurrence_schedule_at",
                        "ice_ttt_solution_deadline",
                        "ice_ttt_solution_at",
                        "ice_ttt_solution_type",
                        "ice_ttt_soe_description",
                        "ice_ttt_solution_comments")) {
            final var input = row(ReportSnapshotTemplate.OCORRENCIAS);
            input.set(name, CurlSnapshotWire.JSON.readTree("{\"future\":[1,null]}"));
            assertFalse(new OcorrenciaDataExportMapper().map(input).valid());
            input.putNull(name);
            assertTrue(new OcorrenciaDataExportMapper().map(input).valid());
        }
    }

    @Test
    void missingAndExtraColumnsRefuseInsteadOfSilentlyDroppingData() throws Exception {
        final var con = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        con.remove("ctd_ant_document");
        assertThrows(
                IllegalArgumentException.class, () -> new ConsolidacaoDataExportMapper().map(con));
        final var oco = row(ReportSnapshotTemplate.OCORRENCIAS);
        oco.put("new_column", 1);
        assertThrows(
                IllegalArgumentException.class, () -> new OcorrenciaDataExportMapper().map(oco));
    }

    @Test
    void dtoOwnsDefensiveCopyAndDoesNotExposeValuesInToString() throws Exception {
        final var row = row(ReportSnapshotTemplate.OCORRENCIAS);
        final var dto = new OcorrenciaDataExportDto(row);
        row.removeAll();
        ((ObjectNode) dto.data()).removeAll();
        assertEquals(20, dto.data().size());
        assertEquals("OcorrenciaDataExportDto[redacted]", dto.toString());
        assertEquals(39, ConsolidacaoDataExportDto.FIELDS.size());
    }

    @Test
    void invalidMoneyIsQuarantinedWithRawAndWithoutRounding() throws Exception {
        final var row = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        row.put("charged_value", "1.123456789");
        final var value = new ConsolidacaoDataExportMapper().map(row);
        assertFalse(value.valid());
        assertEquals("1.123456789", value.chargedValue().raw());
        assertEquals(null, value.chargedValue().value());
    }

    @Test
    void stringBooleanAndNestedChargeCannotBeCoerced() throws Exception {
        final var row = row(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        row.put("ctd_rpt_charge_tde", "true");
        row.set(
                "charged_value_details",
                CurlSnapshotWire.JSON.readTree("{\"future\":{\"nested\":1}}"));
        final var value = new ConsolidacaoDataExportMapper().map(row);
        assertFalse(value.ctdRptChargeTde().valid());
        assertFalse(value.chargedValueDetails().valid());
        assertTrue(value.chargedValueDetails().raw().contains("nested"));
    }

    @Test
    void existingRawAndTextBoundsStayInForce() throws Exception {
        final var row = row(ReportSnapshotTemplate.OCORRENCIAS);
        row.put("ice_ore_description", "x".repeat(1025));
        assertFalse(new OcorrenciaDataExportMapper().map(row).valid());
        row.put("ice_ttt_solution_comments", "x".repeat(4001));
        assertThrows(
                IllegalArgumentException.class, () -> new OcorrenciaDataExportMapper().map(row));
    }

    @Test
    void requestIsClosedInclusiveUnorderedAndOmitsUnqualifiedAuxiliaryFilters() {
        final var origin = URI.create("https://rodogarcia.eslcloud.com.br");
        final String con =
                ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA
                        .query(origin, DAY, 1, 100)
                        .toASCIIString();
        final String oco =
                ReportSnapshotTemplate.OCORRENCIAS.query(origin, DAY, 2, 100).toASCIIString();
        assertTrue(
                con.contains(
                        "search%5Bconsolidations%5D%5Bissue_date%5D=2026-11-06%20-%202026-11-06"));
        assertFalse(con.contains("order_by"));
        assertFalse(oco.contains("updated_at"));
        assertTrue(oco.contains("page=2"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ReportSnapshotTemplate.OCORRENCIAS.query(
                                URI.create("http://rodogarcia.eslcloud.com.br"), DAY, 1, 100));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ReportSnapshotTemplate.OCORRENCIAS.query(
                                URI.create("https://other.example"), DAY, 1, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportSnapshotTemplate.OCORRENCIAS.query(origin, DAY, 1, 101));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportSnapshotTemplate.OCORRENCIAS.query(origin, DAY, 201, 100));
    }

    @Test
    void shortPagesAndDuplicateRowsContinueUntilLiteralEmptyTerminal() throws Exception {
        final var template = ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA;
        final var row = row(template);
        final var pages =
                List.of(
                        CurlSnapshotWire.JSON.createArrayNode().add(row),
                        CurlSnapshotWire.JSON.createArrayNode().add(row).add(row),
                        CurlSnapshotWire.JSON.createArrayNode());
        final var ordinals = new ArrayList<Long>();
        final var result =
                new ExtrairConsolidacaoDataExport()
                        .execute(
                                DAY,
                                wire(template, pages),
                                (capture, ordinal, value, observation) -> ordinals.add(ordinal),
                                LIMITS);
        assertTrue(result.success());
        assertEquals(3, result.rows());
        assertEquals(3, result.typedRows());
        assertEquals(List.of(1L, 2L, 3L), ordinals);
        assertEquals(3, result.pages());
    }

    @Test
    void legitimateEmptyHasCurrentMetadataAndTerminal() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var result =
                new ExtrairOcorrenciaDataExport()
                        .execute(
                                DAY,
                                wire(template, List.of(CurlSnapshotWire.JSON.createArrayNode())),
                                (a, b, c, d) -> {},
                                LIMITS);
        assertTrue(result.success());
        assertEquals(0, result.rows());
        assertEquals(20, result.fields());
        assertEquals(1, result.pages());
    }

    @Test
    void noTerminalNeverClaimsComplete() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var result =
                new ExtrairOcorrenciaDataExport()
                        .execute(
                                DAY,
                                wire(
                                        template,
                                        List.of(
                                                CurlSnapshotWire.JSON
                                                        .createArrayNode()
                                                        .add(row(template)))),
                                (a, b, c, d) -> {},
                                new ReportSnapshotExtractor.Limits(1, 100, 100));
        assertFalse(result.terminal());
        assertFalse(result.success());
    }

    @Test
    void semanticMetadataDriftRefusesBeforeData() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var info = info(template);
        info.put("default_order", "id asc");
        final var wire =
                new ReportSnapshotExtractor.Wire() {
                    @Override
                    public JsonNode info(final ReportSnapshotTemplate ignored) {
                        return info;
                    }

                    @Override
                    public JsonNode page(
                            final ReportSnapshotTemplate t,
                            final BusinessDateRange d,
                            final int p,
                            final int n) {
                        throw new AssertionError("data must not be requested");
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExtrairOcorrenciaDataExport()
                                .execute(DAY, wire, (a, b, c, d) -> {}, LIMITS));
    }

    @Test
    void outsideDateAndErrorEnvelopeRefuse() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var row = row(template);
        row.put("occurrence_at", "2026-11-07T00:00:00-03:00");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExtrairOcorrenciaDataExport()
                                .execute(
                                        DAY,
                                        wire(
                                                template,
                                                List.of(
                                                        CurlSnapshotWire.JSON
                                                                .createArrayNode()
                                                                .add(row))),
                                        (a, b, c, d) -> {},
                                        LIMITS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExtrairOcorrenciaDataExport()
                                .execute(
                                        DAY,
                                        wire(
                                                template,
                                                List.of(
                                                        CurlSnapshotWire.JSON
                                                                .createObjectNode()
                                                                .put("error", "synthetic"))),
                                        (a, b, c, d) -> {},
                                        LIMITS));
    }

    @Test
    void stop429PreventsAnyPhysicalCall(@TempDir final Path temporary) throws Exception {
        final var env = temporary.resolve("fixture.env");
        Files.writeString(
                env,
                "API_BASE_URL=https://rodogarcia.eslcloud.com.br\nAPI_DATAEXPORT_TOKEN=synthetic\n");
        final var stop = temporary.resolve("STOP429.json");
        Files.writeString(stop, "{}");
        final var wire =
                new CurlSnapshotWire(env, temporary, stop, Instant.now().plusSeconds(90), 2);
        assertThrows(
                IllegalStateException.class, () -> wire.info(ReportSnapshotTemplate.OCORRENCIAS));
        assertEquals(0, wire.requests());
    }

    @Test
    void newReportsCannotAccidentallyUseTheUnqualifiedLegacyPipeline() {
        for (final var template :
                List.of(
                        DataExportTemplate.CONSOLIDACAO_DETALHADA,
                        DataExportTemplate.OCORRENCIAS)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DataExportPageRequest.forTemplate(template, DAY, null, 1));
        }
    }

    @Test
    void sinkFailureCannotBecomeACompleteResult() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        assertThrows(
                java.io.IOException.class,
                () ->
                        new ExtrairOcorrenciaDataExport()
                                .execute(
                                        DAY,
                                        wire(
                                                template,
                                                List.of(
                                                        CurlSnapshotWire.JSON
                                                                .createArrayNode()
                                                                .add(row(template)),
                                                        CurlSnapshotWire.JSON.createArrayNode())),
                                        (a, b, c, d) -> {
                                            throw new java.io.IOException("synthetic sink failure");
                                        },
                                        LIMITS));
    }

    @Test
    void metadataFilterTypeDriftRefuses() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var metadata = info(template);
        ((ObjectNode) metadata.path("filters").get(0)).put("type", "string");
        final var wire =
                new ReportSnapshotExtractor.Wire() {
                    @Override
                    public JsonNode info(final ReportSnapshotTemplate ignored) {
                        return metadata;
                    }

                    @Override
                    public JsonNode page(
                            final ReportSnapshotTemplate t,
                            final BusinessDateRange d,
                            final int p,
                            final int n) {
                        throw new AssertionError("type drift before data");
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExtrairOcorrenciaDataExport()
                                .execute(DAY, wire, (a, b, c, d) -> {}, LIMITS));
    }

    @Test
    void bomAndLastNonemptyConfigurationWorkWithoutExposingCredential(@TempDir final Path temporary)
            throws Exception {
        final var env = temporary.resolve("fixture.env");
        Files.writeString(
                env,
                "\uFEFFAPI_BASE_URL=https://invalid.example\nAPI_BASE_URL=https://rodogarcia.eslcloud.com.br\nAPI_BASE_URL=\nAPI_DATAEXPORT_TOKEN=synthetic-private\nAPI_DATAEXPORT_TOKEN=\n");
        final var wire =
                new CurlSnapshotWire(
                        env,
                        temporary,
                        temporary.resolve("STOP429.json"),
                        Instant.now().plusSeconds(90),
                        2);
        assertEquals(0, wire.requests());
        assertFalse(wire.toString().contains("synthetic-private"));
    }

    @Test
    void physicalCapturePreservesExpansionWithoutInventingAPaginationRoot() throws Exception {
        final var template = ReportSnapshotTemplate.OCORRENCIAS;
        final var input = row(template);
        final var result =
                new ExtrairOcorrenciaDataExport()
                        .execute(
                                DAY,
                                wire(
                                        template,
                                        List.of(
                                                CurlSnapshotWire.JSON
                                                        .createArrayNode()
                                                        .add(input)
                                                        .add(input)
                                                        .add(input),
                                                CurlSnapshotWire.JSON.createArrayNode())),
                                (a, b, c, d) -> {},
                                new ReportSnapshotExtractor.Limits(2, 1, 10));
        assertTrue(result.success());
        assertEquals(3, result.rows());
    }

    static ObjectNode row(final ReportSnapshotTemplate template) throws Exception {
        final var row = CurlSnapshotWire.JSON.createObjectNode();
        for (final var field : contract(template).path("fields")) {
            final String name = field.path("name").asText();
            switch (field.path("parser").asText()) {
                case "integer" -> row.put(name, 12);
                case "date" -> row.put(name, "2026-11-06");
                case "instant" -> row.put(name, "2026-11-06T12:30:12.123456-03:00");
                case "decimal" -> row.put(name, "123.45000001");
                case "bool" -> row.put(name, true);
                case "strings" -> row.putArray(name).add("001").add("001").add("002");
                case "charges" ->
                        row.set(
                                name,
                                CurlSnapshotWire.JSON.readTree(
                                        "{\"TAXA NOVA\":0.1234567890123456789,\"NULL\":null}"));
                case "opaque" -> row.putNull(name);
                default -> row.put(name, "0000123");
            }
        }
        return row;
    }

    static JsonNode contract(final ReportSnapshotTemplate template) throws Exception {
        try (var resource =
                MappedReports0761Test.class
                        .getClassLoader()
                        .getResourceAsStream(template.contractResource())) {
            return CurlSnapshotWire.JSON.readTree(resource);
        }
    }

    static ObjectNode info(final ReportSnapshotTemplate template) throws Exception {
        final var contract = contract(template);
        final var info = CurlSnapshotWire.JSON.createObjectNode();
        final var fields = info.putObject("fields");
        for (final var field : contract.path("fields")) {
            fields.putObject(field.path("name").asText())
                    .put("title", field.path("title").asText());
        }
        info.set("filters", contract.path("filters").deepCopy());
        info.set("default_order", contract.path("defaultOrder").deepCopy());
        info.set("required_date_filters", contract.path("requiredDateFilters").deepCopy());
        return info;
    }

    static ReportSnapshotExtractor.Wire wire(
            final ReportSnapshotTemplate template, final List<? extends JsonNode> pages)
            throws Exception {
        final var info = info(template);
        return new ReportSnapshotExtractor.Wire() {
            @Override
            public JsonNode info(final ReportSnapshotTemplate ignored) {
                return info;
            }

            @Override
            public JsonNode page(
                    final ReportSnapshotTemplate t,
                    final BusinessDateRange d,
                    final int p,
                    final int n) {
                return pages.get(p - 1);
            }
        };
    }
}
