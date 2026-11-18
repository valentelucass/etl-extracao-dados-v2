package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Real 6908 INFO -> bounded complete pages -> existing unchanged mapper -> immutable retained
 * payload.
 */
public final class ColetasIntervalCapture {
    public interface Port {
        CurlExportTransport.Wire info() throws Exception;

        CurlExportTransport.Wire page(JsonNode request, int page, int per) throws Exception;
    }

    private ColetasIntervalCapture() {}

    public static ObjectNode capture(
            final JsonNode request,
            final CurlExportTransport transport,
            final PersistentExportJournal journal,
            final Path output,
            final int maximumPages,
            final int per,
            final long maximumRows,
            final Instant deadline)
            throws Exception {
        return capture(
                request,
                new Port() {
                    @Override
                    public CurlExportTransport.Wire info() throws Exception {
                        return transport.api(
                                "GET",
                                "/api/analytics/reports/6908/info",
                                null,
                                Duration.ofSeconds(30));
                    }

                    @Override
                    public CurlExportTransport.Wire page(
                            final JsonNode selection, final int page, final int size)
                            throws Exception {
                        return transport.coletasPage(selection, page, size);
                    }
                },
                journal,
                output,
                maximumPages,
                per,
                maximumRows,
                deadline);
    }

    public static ObjectNode capture(
            final JsonNode request,
            final Port transport,
            final PersistentExportJournal journal,
            final Path output,
            final int maximumPages,
            final int per,
            final long maximumRows,
            final Instant deadline)
            throws Exception {
        ColetasIndependentRequest.validate(request);
        require(
                maximumPages > 0
                        && maximumPages <= 63
                        && per > 0
                        && per <= 100
                        && maximumRows > 0
                        && maximumRows <= 12000,
                "COLETAS_CAPTURE_FINITE_LIMITS");
        final var previous = journal.latest("COLETAS_COMPLETE");
        if (previous != null) {
            return (ObjectNode) previous;
        }
        // A partial record is retained; it is never relabelled complete or overwritten/re-fetched.
        require(
                journal.latest("COLETAS_STARTED") == null,
                "COLETAS_PARTIAL_CAPTURE_RECONCILIATION_REQUIRED");
        journal.record(
                "COLETAS_STARTED",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("attemptId", text(request, "attemptId"))
                        .put("maximumPages", maximumPages)
                        .put("per", per)
                        .put("maximumRows", maximumRows)
                        .put("deadline", deadline.toString()));
        require(Instant.now().isBefore(deadline), "COLETAS_CAPTURE_DEADLINE_PARTIAL");
        final var info = transport.info();
        require(info.status() == 200, "COLETAS_INFO_STOP_NON_200");
        final var parsed = new DataExportTemplateInfoParser().parse(strictJson(info.bytes()));
        final Set<String> expectedFields = new HashSet<>();
        ColetasCommonAdapter.catalog()
                .required("columns")
                .forEach(field -> expectedFields.add(text(field, "sourceField")));
        final Set<String> actualFields = new HashSet<>();
        parsed.fields().forEach(field -> actualFields.add(field.technicalName()));
        require(actualFields.containsAll(expectedFields), "COLETAS_INFO_ALL31_BOUND_FIELDS");
        final Set<String> actualFilters = new HashSet<>();
        parsed.filters().forEach(field -> actualFilters.add(field.technicalName()));
        require(
                actualFilters.equals(
                        Set.of(
                                "picks.finish_date",
                                "picks.service_date",
                                "picks.request_date",
                                "picks.corporation_id",
                                "picks.status",
                                "scopes.by_updated_at")),
                "COLETAS_INFO_FULL_FILTER_CATALOG");
        final Path payload = output.resolve("coletas-mapped.partial.jsonl");
        require(!Files.exists(payload), "COLETAS_FRESH_RETAINED_PAYLOAD");
        final var mapper = new ColetaDataExportRecordMapper();
        long rows = 0;
        long bytes = 0;
        boolean complete = false;
        int pages = 0;
        String partialReason = "PARTIAL_PAGE_BUDGET_EXHAUSTED";
        final Set<String> priorPageIds = new HashSet<>();
        final var start = LocalDate.parse(text(request, "periodStart"));
        final var end = LocalDate.parse(text(request, "periodEnd"));
        try (var file =
                FileChannel.open(
                        payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (int page = 1; page <= maximumPages; page++) {
                final byte[] body =
                        ExportValidationSupport.JSON.writeValueAsBytes(
                                ColetasIndependentRequest.body(request, page, per));
                journal.record(
                        "COLETAS_PAGE_RESERVED",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("page", page)
                                .put("attemptId", text(request, "attemptId"))
                                .put("transport", "GET_WITH_QUERY")
                                .put("method", "GET")
                                .put(
                                        "querySha256",
                                        ExportValidationSupport.sha256(
                                                ColetasIndependentRequest.query(
                                                                java.net.URI.create(
                                                                        "https://serialization.invalid/api/analytics/reports/6908/data"),
                                                                request,
                                                                page,
                                                                per)
                                                        .getRawQuery()
                                                        .getBytes(StandardCharsets.US_ASCII))));
                require(Instant.now().isBefore(deadline), "COLETAS_CAPTURE_DEADLINE_PARTIAL");
                final var wire = transport.page(request, page, per);
                if (page == 1 && journal.latest("ROOT_WIRE_RECEIPT") != null) {
                    journal.record("API_REQUEST_EVENT", journal.latest("ROOT_WIRE_RECEIPT"));
                }
                require(wire.status() == 200, "COLETAS_DATA_STOP_NON_200");
                final JsonNode data = strictJson(wire.bytes());
                require(data.isArray(), "COLETAS_QUALIFIED_ROOT_ARRAY_PAGE");
                final var distinct = new HashSet<String>();
                for (final var row : data) {
                    final var id = row.get("id");
                    require(
                            row.isObject()
                                    && id != null
                                    && !id.isNull()
                                    && (id.isTextual() && !id.textValue().isEmpty()
                                            || id.isIntegralNumber()),
                            "COLETAS_PAGE_SCALAR_ID_REQUIRED");
                    distinct.add((id.isTextual() ? "TEXT:" : "NUMBER:") + id.asText());
                    require(distinct.size() <= per, "COLETAS_PAGE_DISTINCT_ID_LIMIT");
                }
                final long overlappingIds =
                        distinct.stream().filter(priorPageIds::contains).count();
                pages++;
                journal.record(
                        "COLETAS_PAGE_OBSERVED",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("page", page)
                                .put("rows", data.size())
                                .set(
                                        "rawBodyPin",
                                        ExportValidationSupport.JSON.valueToTree(wire.body())));
                if (data.isEmpty()) {
                    complete = true;
                    break;
                }
                for (int index = 0; index < data.size(); index++) {
                    require(rows < maximumRows, "COLETAS_ROWS_LIMIT_PARTIAL");
                    final var raw = data.get(index);
                    require(
                            raw.isObject() && raw.path("request_date").isTextual(),
                            "COLETAS_REQUIRED_REQUEST_DATE");
                    final var day = LocalDate.parse(raw.get("request_date").textValue());
                    require(
                            !day.isBefore(start) && !day.isAfter(end),
                            "COLETAS_CAPTURE_ROW_OUTSIDE_PERIOD");
                    // The existing mapper's ordinal is limited to100. Chunk expanded physical
                    // occurrences locally; page/raw pin + JSONL line retain the global locator.
                    final var mapped = mapper.map(index % 100 + 1, raw);
                    require(
                            mapped.disposition() == ColetaStageDisposition.VALID,
                            "COLETAS_EXISTING_MAPPER_REFUSED");
                    final byte[] line =
                            (mapped.payloadJson() + "\n").getBytes(StandardCharsets.UTF_8);
                    require(bytes + line.length <= 8388608, "COLETAS_PAYLOAD_BYTES_LIMIT_PARTIAL");
                    final var buffer = ByteBuffer.wrap(line);
                    while (buffer.hasRemaining()) {
                        file.write(buffer);
                    }
                    rows++;
                    bytes += line.length;
                }
                file.force(true);
                if (overlappingIds > 0) {
                    partialReason = "PARTIAL_PAGE_ID_OVERLAP_RECONCILIATION_REQUIRED";
                    journal.record(
                            "COLETAS_PAGE_OVERLAP",
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("page", page)
                                    .put("overlappingDistinctIds", overlappingIds)
                                    .put("allReceivedRowsRetained", true)
                                    .put("deduplicationPerformed", false));
                    break;
                }
                priorPageIds.addAll(distinct);
            }
            file.force(true);
        } finally {
            journal.record(
                    "COLETAS_CAPTURE_CLOSURE",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("rowsRetained", rows)
                            .put("pagesObserved", pages)
                            .put("payloadBytes", bytes)
                            .put("terminalEmptyPageObserved", complete)
                            .put("deduplicationPerformed", false));
        }
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("status", complete ? "COLETAS_SOURCE_COMPLETE" : partialReason)
                        .put("attemptId", text(request, "attemptId"))
                        .put("rows", rows)
                        .put("pages", pages)
                        .put("terminalEmptyPageObserved", complete)
                        .put("sourceKind", "DATA_EXPORT");
        if (bytes > 0) {
            final byte[] retained = Files.readAllBytes(payload);
            final var pin =
                    new ExportValidationSupport.Pin(
                            payload, ExportValidationSupport.sha256(retained), retained.length);
            result.set("dataPin", ExportValidationSupport.JSON.valueToTree(pin));
        }
        final String outcome = complete ? "COLETAS_COMPLETE" : "COLETAS_PARTIAL";
        journal.record(outcome, result);
        // Return the persisted JSON representation on both first execution and journal reopen.
        // Small integral values otherwise change from LongNode to IntNode only when resumed.
        return (ObjectNode) journal.latest(outcome);
    }

    private static JsonNode strictJson(final byte[] bytes) throws java.io.IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode node = ExportValidationSupport.JSON.readTree(parser);
            require(node != null && parser.nextToken() == null, "COLETAS_RESPONSE_TRAILING_JSON");
            return node;
        }
    }
}
