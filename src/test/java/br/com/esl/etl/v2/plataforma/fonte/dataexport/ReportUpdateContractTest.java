package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReportUpdateContractTest {
    private static final URI ORIGIN = URI.create("https://rodogarcia.eslcloud.com.br");

    @ParameterizedTest
    @EnumSource(ReportSnapshotTemplate.class)
    void serializesOnlyObservedUpdateCapabilities(final ReportSnapshotTemplate template) {
        final var contract = ReportUpdateContract.forTemplate(template);
        if (template == ReportSnapshotTemplate.CONTAS_A_PAGAR
                || template == ReportSnapshotTemplate.SINISTROS) {
            assertNull(contract);
            return;
        }
        assertNotNull(contract);
        final var query =
                URLDecoder.decode(
                        contract.query(
                                        template,
                                        ORIGIN,
                                        new BusinessDateRange(
                                                LocalDate.parse("2026-08-13"),
                                                LocalDate.parse("2026-11-10")),
                                        new SourceDateTimeRange(
                                                Instant.parse("2026-11-10T02:59:00Z"),
                                                Instant.parse("2026-11-10T03:01:00Z")),
                                        2,
                                        25)
                                .getRawQuery(),
                        StandardCharsets.UTF_8);
        assertTrue(query.contains("page=2&per=25"));
        assertTrue(
                query.contains(
                        template.template().businessDateFilter().asQueryParameterName()
                                + "=2026-08-13 - 2026-11-10"));
        assertTrue(
                query.contains(
                        contract.path().asQueryParameterName()
                                + "="
                                + (contract.dateOnly()
                                        ? "2026-11-09 - 2026-11-10"
                                        : "2026-11-09 23:59:00 - 2026-11-10 00:01:00")));
        assertEquals(
                template == ReportSnapshotTemplate.COTACOES
                        || template == ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA,
                contract.dateOnly());
    }

    @Test
    void rejectsWrongEndpointAndContractInsteadOfBroadeningSource() {
        final var dates =
                new BusinessDateRange(LocalDate.parse("2026-08-13"), LocalDate.parse("2026-11-10"));
        final var window =
                new SourceDateTimeRange(
                        Instant.parse("2026-11-10T03:00:00Z"),
                        Instant.parse("2026-11-10T04:00:00Z"));
        final var contract = ReportUpdateContract.forTemplate(ReportSnapshotTemplate.COLETAS);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        contract.query(
                                ReportSnapshotTemplate.COLETAS,
                                URI.create("https://example.invalid"),
                                dates,
                                window,
                                1,
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        contract.query(
                                ReportSnapshotTemplate.SINISTROS, ORIGIN, dates, window, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        contract.query(
                                ReportSnapshotTemplate.COLETAS, ORIGIN, dates, window, 201, 1));
    }

    @Test
    void currentMetadataIsStrictAndLeavesPreviousDescriptorsInPlace() throws Exception {
        final var infos = infos();
        final var catalog = new ReportMetadataCatalog();
        int fields = 0;
        for (final var template : ReportSnapshotTemplate.values()) {
            fields += catalog.validate(template, infos.get(template)).size();
        }
        assertEquals(508, fields);
        final var changed = infos.get(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA).deepCopy();
        ((ObjectNode) changed.required("filters").get(0)).put("type", "datetime");
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.validate(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA, changed));
        try (var previous =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "contracts/dataexport/consolidacoes-11067-0762-v2.json")) {
            assertNotNull(previous);
            assertEquals(9, CurlSnapshotWire.JSON.readTree(previous).required("filters").size());
        }
    }

    @Test
    void updateCaptureUsesItsOwnTransportAndRequiresTheEmptyTerminalPage() throws Exception {
        final var infos = infos();
        final var template = ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA;
        final var row = CurlSnapshotWire.JSON.createObjectNode();
        infos.get(template).required("fields").fieldNames().forEachRemaining(row::putNull);
        row.put("issue_date", "2026-08-20");
        final var calls = new AtomicInteger();
        final var wire =
                new ReportSnapshotExtractor.Wire() {
                    @Override
                    public JsonNode info(final ReportSnapshotTemplate selected) {
                        return infos.get(selected);
                    }

                    @Override
                    public JsonNode page(
                            final ReportSnapshotTemplate selected,
                            final BusinessDateRange dates,
                            final int page,
                            final int per) {
                        fail("Full snapshot transport must not handle a delta request");
                        return null;
                    }

                    @Override
                    public JsonNode updatedPage(
                            final ReportSnapshotTemplate selected,
                            final BusinessDateRange dates,
                            final SourceDateTimeRange window,
                            final int page,
                            final int per) {
                        calls.incrementAndGet();
                        return page == 1
                                ? CurlSnapshotWire.JSON
                                        .createArrayNode()
                                        .add(row)
                                        .add(row.deepCopy())
                                : CurlSnapshotWire.JSON.createArrayNode();
                    }
                };
        final var dates =
                new BusinessDateRange(LocalDate.parse("2026-08-13"), LocalDate.parse("2026-11-10"));
        final var updates =
                new SourceDateTimeRange(
                        Instant.parse("2026-11-10T03:00:00Z"),
                        Instant.parse("2026-11-10T04:00:00Z"));
        final var extractor = new ReportSnapshotExtractor(new ReportMetadataCatalog());
        final var result =
                extractor.extractUpdatedRecords(
                        template,
                        dates,
                        updates,
                        wire,
                        (id, ordinal, value, observation) -> {},
                        input -> new Valid(),
                        new ReportSnapshotExtractor.Limits(3, 100, 10));
        assertTrue(result.success());
        assertEquals(2, result.rows());
        assertEquals(2, calls.get());
        assertFalse(
                extractor
                        .extractUpdatedRecords(
                                template,
                                dates,
                                updates,
                                wire,
                                (id, ordinal, value, observation) -> {},
                                input -> new Valid(),
                                new ReportSnapshotExtractor.Limits(1, 100, 10))
                        .terminal());
    }

    private record Valid() implements br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation {
        @Override
        public String vertical() {
            return "SYNTHETIC_TEST";
        }

        @Override
        public boolean valid() {
            return true;
        }
    }

    static EnumMap<ReportSnapshotTemplate, JsonNode> infos() throws Exception {
        final var map = new EnumMap<ReportSnapshotTemplate, JsonNode>(ReportSnapshotTemplate.class);
        try (var input =
                ReportUpdateContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "contracts/dataexport/receiving-catalog-0766-v1.json")) {
            assertNotNull(input);
            for (final var entity : CurlSnapshotWire.JSON.readTree(input).required("entities")) {
                final var info = CurlSnapshotWire.JSON.createObjectNode();
                final var fields = info.putObject("fields");
                entity.required("fields").forEach(field -> fields.putObject(field.asText()));
                info.set("filters", entity.required("filters").deepCopy());
                info.set("default_order", entity.required("defaultOrder").deepCopy());
                info.set(
                        "required_date_filters", entity.required("requiredDateFilters").deepCopy());
                map.put(ReportSnapshotTemplate.valueOf(entity.required("entity").asText()), info);
            }
        }
        return map;
    }
}
