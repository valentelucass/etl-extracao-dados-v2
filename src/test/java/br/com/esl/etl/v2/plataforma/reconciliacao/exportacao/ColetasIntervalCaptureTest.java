package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColetasIntervalCaptureTest {
    @TempDir Path directory;
    private int sequence;

    private PersistentExportJournal journal() throws Exception {
        return PersistentExportJournal.create(
                directory.resolve("journal"),
                "a".repeat(64),
                "b".repeat(64),
                UUID.fromString(ColetasCommon31SyntheticFixture.ATTEMPT));
    }

    private CurlExportTransport.Wire wire(final int status, final JsonNode value) throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        final var path = directory.resolve("wire-" + ++sequence + ".json");
        Files.write(path, bytes);
        return new CurlExportTransport.Wire(
                status,
                new ExportValidationSupport.Pin(
                        path.toAbsolutePath(), ExportValidationSupport.sha256(bytes), bytes.length),
                Instant.now());
    }

    private ObjectNode info() {
        final var info = ExportValidationSupport.JSON.createObjectNode();
        final var fields = info.putArray("fields");
        ColetasCommon31SyntheticFixture.FIELDS.forEach(field -> fields.add(field.source()));
        fields.add("synthetic_extra");
        info.putArray("filters")
                .add("picks.finish_date")
                .add("picks.service_date")
                .add("picks.request_date")
                .add("picks.corporation_id")
                .add("picks.status")
                .add("scopes.by_updated_at");
        return info;
    }

    private final class Port implements ColetasIntervalCapture.Port {
        private final List<JsonNode> pages;
        private final ObjectNode metadata;
        private int infoCalls;
        private int pageCalls;
        private int pageStatus = 200;

        Port(final List<JsonNode> pages, final ObjectNode metadata) {
            this.pages = pages;
            this.metadata = metadata;
        }

        @Override
        public CurlExportTransport.Wire info() throws Exception {
            infoCalls++;
            return wire(200, metadata);
        }

        @Override
        public CurlExportTransport.Wire page(final JsonNode request, final int page, final int per)
                throws Exception {
            assertEquals(pageCalls + 1, page);
            return wire(pageStatus, pages.get(pageCalls++));
        }
    }

    private ObjectNode run(
            final Port port,
            final PersistentExportJournal journal,
            final int cap,
            final int per,
            final long physical)
            throws Exception {
        return ColetasIntervalCapture.capture(
                ColetasCommon31SyntheticFixture.request(),
                port,
                journal,
                directory,
                cap,
                per,
                physical,
                Instant.now().plusSeconds(60));
    }

    @Test
    void completeExpandedPagePreservesEveryOccurrenceAndRequiresTerminalEmptyPage()
            throws Exception {
        final var rows = ExportValidationSupport.JSON.createArrayNode();
        for (int index = 0; index < 105; index++) {
            rows.add(ColetasCommon31SyntheticFixture.sourceRow());
        }
        final var port =
                new Port(List.of(rows, ExportValidationSupport.JSON.createArrayNode()), info());
        try (var journal = journal()) {
            final var result = run(port, journal, 2, 1, 106);
            assertEquals("COLETAS_SOURCE_COMPLETE", result.path("status").asText());
            assertEquals(105, result.path("rows").asInt());
            assertTrue(result.path("terminalEmptyPageObserved").asBoolean());
            assertEquals(1, port.infoCalls);
            assertEquals(2, port.pageCalls);
            final var data = ExportValidationSupport.Pin.from(result.required("dataPin"));
            assertEquals(105, Files.readAllLines(data.path()).size());
            assertEquals(2, journal.count("COLETAS_PAGE_OBSERVED"));
            assertFalse(result.path("deduplicationPerformed").asBoolean());
        }
    }

    @Test
    void pageCapRetainsPartialDataAndCannotPretendToHaveReachedEof() throws Exception {
        final var port =
                new Port(
                        List.of(
                                ExportValidationSupport.JSON
                                        .createArrayNode()
                                        .add(ColetasCommon31SyntheticFixture.sourceRow())),
                        info());
        try (var journal = journal()) {
            final var result = run(port, journal, 1, 1, 2);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", result.path("status").asText());
            assertFalse(result.path("terminalEmptyPageObserved").asBoolean());
            assertEquals(1, port.pageCalls);
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 1, 1, 2));
            assertEquals(1, port.pageCalls);
        }
    }

    @Test
    void anAbsentIdIsRejectedWithoutDiscardingTheRetainedRawResponse() throws Exception {
        final var row = ColetasCommon31SyntheticFixture.sourceRow();
        row.remove("id");
        final var port =
                new Port(List.of(ExportValidationSupport.JSON.createArrayNode().add(row)), info());
        try (var journal = journal()) {
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 2, 1, 2));
            assertTrue(Files.exists(directory.resolve("wire-2.json")));
            assertEquals(1, port.pageCalls);
        }
    }

    @Test
    void nullBooleanAndCompositeIdsCannotBecomeOneValidDistinctId() throws Exception {
        for (final var invalid :
                List.of(
                        ExportValidationSupport.JSON.nullNode(),
                        ExportValidationSupport.JSON.getNodeFactory().booleanNode(true),
                        ExportValidationSupport.JSON.createObjectNode())) {
            final var own = Files.createDirectory(directory.resolve("case-" + ++sequence));
            final var row = ColetasCommon31SyntheticFixture.sourceRow();
            row.set("id", invalid);
            final var port =
                    new Port(
                            List.of(ExportValidationSupport.JSON.createArrayNode().add(row)),
                            info());
            try (var journal =
                    PersistentExportJournal.create(
                            own.resolve("journal"),
                            "a".repeat(64),
                            "b".repeat(64),
                            UUID.fromString(ColetasCommon31SyntheticFixture.ATTEMPT))) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ColetasIntervalCapture.capture(
                                        ColetasCommon31SyntheticFixture.request(),
                                        port,
                                        journal,
                                        own,
                                        2,
                                        1,
                                        2,
                                        Instant.now().plusSeconds(60)));
            }
        }
    }

    @Test
    void distinctIdCountAbovePerFailsEvenWhenPhysicalBudgetWouldAllowIt() throws Exception {
        final var second = ColetasCommon31SyntheticFixture.sourceRow().put("id", 202);
        final var port =
                new Port(
                        List.of(
                                ExportValidationSupport.JSON
                                        .createArrayNode()
                                        .add(ColetasCommon31SyntheticFixture.sourceRow())
                                        .add(second)),
                        info());
        try (var journal = journal()) {
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 2, 1, 100));
            assertEquals(1, port.pageCalls);
        }
    }

    @Test
    void physicalLimitDoesNotDeduplicateOrGrantAnotherPage() throws Exception {
        final var row = ColetasCommon31SyntheticFixture.sourceRow();
        final var port =
                new Port(
                        List.of(ExportValidationSupport.JSON.createArrayNode().add(row).add(row)),
                        info());
        try (var journal = journal()) {
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 2, 1, 1));
            assertEquals(1, journal.latest("COLETAS_CAPTURE_CLOSURE").path("rowsRetained").asInt());
            assertEquals(1, port.pageCalls);
        }
    }

    @Test
    void missingInfoFieldStopsBeforeAnyDataRequest() throws Exception {
        final var metadata = info();
        metadata.withArray("fields").remove(30);
        final var port = new Port(List.of(), metadata);
        try (var journal = journal()) {
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 2, 1, 10));
            assertEquals(0, port.pageCalls);
        }
    }

    @Test
    void non200ResponseStopsWithoutRetryOrNextPage() throws Exception {
        final var port = new Port(List.of(ExportValidationSupport.JSON.createArrayNode()), info());
        port.pageStatus = 429;
        try (var journal = journal()) {
            assertThrows(IllegalArgumentException.class, () -> run(port, journal, 2, 1, 10));
            assertEquals(1, port.pageCalls);
        }
    }

    @Test
    void completedCaptureReusesItsPinnedResultWithoutRepeatingInfoOrData() throws Exception {
        final var port =
                new Port(
                        List.of(
                                ExportValidationSupport.JSON
                                        .createArrayNode()
                                        .add(ColetasCommon31SyntheticFixture.sourceRow()),
                                ExportValidationSupport.JSON.createArrayNode()),
                        info());
        try (var journal = journal()) {
            final var first = run(port, journal, 2, 1, 10);
            assertEquals(first, run(port, journal, 2, 1, 10));
            assertEquals(1, port.infoCalls);
            assertEquals(2, port.pageCalls);
        }
    }

    @Test
    void completedCaptureReopensJournalWithIdenticalResultPayloadAndPins() throws Exception {
        final var port =
                new Port(
                        List.of(
                                ExportValidationSupport.JSON
                                        .createArrayNode()
                                        .add(ColetasCommon31SyntheticFixture.sourceRow()),
                                ExportValidationSupport.JSON.createArrayNode()),
                        info());
        final ObjectNode first;
        final ExportValidationSupport.Pin dataPin;
        final ExportValidationSupport.Pin completePin;
        final byte[] payload;
        final byte[] persistedResult;
        try (var journal = journal()) {
            first = run(port, journal, 2, 1, 10);
            dataPin = ExportValidationSupport.Pin.from(first.required("dataPin"));
            completePin = journal.latestPin("COLETAS_COMPLETE");
            payload = Files.readAllBytes(dataPin.path());
            persistedResult = Files.readAllBytes(completePin.path());
            assertEquals(ExportValidationSupport.JSON.readTree(persistedResult), first);
        }
        try (var reopened =
                PersistentExportJournal.open(
                        directory.resolve("journal"),
                        "a".repeat(64),
                        "b".repeat(64),
                        UUID.fromString(ColetasCommon31SyntheticFixture.ATTEMPT))) {
            final var resumed = run(port, reopened, 2, 1, 10);
            assertEquals(first, resumed);
            assertEquals(first.path("rows").getClass(), resumed.path("rows").getClass());
            assertEquals(
                    first.path("dataPin").path("bytes").getClass(),
                    resumed.path("dataPin").path("bytes").getClass());
            assertEquals(dataPin, ExportValidationSupport.Pin.from(resumed.required("dataPin")));
            assertEquals(completePin, reopened.latestPin("COLETAS_COMPLETE"));
            assertArrayEquals(payload, Files.readAllBytes(dataPin.path()));
            assertArrayEquals(persistedResult, Files.readAllBytes(completePin.path()));
            assertEquals(dataPin.sha256(), ExportValidationSupport.sha256(payload));
            assertEquals(dataPin.bytes(), (long) payload.length);
            assertEquals(1, reopened.count("COLETAS_STARTED"));
            assertEquals(1, reopened.count("COLETAS_COMPLETE"));
            assertEquals(2, reopened.count("COLETAS_PAGE_OBSERVED"));
            assertEquals(1, port.infoCalls);
            assertEquals(2, port.pageCalls);
        }
    }

    @Test
    void queryEncodingUsesTheExistingEncoderAndNeverAnHttpGetBody() {
        final var query =
                ColetasIndependentRequest.query(
                        java.net.URI.create(
                                "https://serialization.invalid/api/analytics/reports/6908/data"),
                        ColetasCommon31SyntheticFixture.request(),
                        2,
                        25);
        assertEquals(
                "search%5Bpicks%5D%5Brequest_date%5D=2036-03-20%20-%202036-03-26&page=2&per=25",
                query.getRawQuery());
        assertEquals("/api/analytics/reports/6908/data", query.getRawPath());
    }

    @Test
    void anIdAcrossPagesPreservesAllRowsAndStopsAsPartialBeforeTheEmptyPage() throws Exception {
        final var row = ColetasCommon31SyntheticFixture.sourceRow();
        final var later = ColetasCommon31SyntheticFixture.sourceRow().put("id", 202);
        final var port =
                new Port(
                        List.of(
                                ExportValidationSupport.JSON.createArrayNode().add(row),
                                ExportValidationSupport.JSON.createArrayNode().add(row).add(later),
                                ExportValidationSupport.JSON.createArrayNode()),
                        info());
        try (var journal = journal()) {
            final var result = run(port, journal, 3, 2, 10);
            assertEquals(
                    "PARTIAL_PAGE_ID_OVERLAP_RECONCILIATION_REQUIRED",
                    result.path("status").asText());
            assertEquals(3, result.path("rows").asInt());
            assertEquals(2, port.pageCalls);
            assertFalse(result.path("terminalEmptyPageObserved").asBoolean());
            assertEquals(
                    3,
                    Files.readAllLines(
                                    ExportValidationSupport.Pin.from(result.required("dataPin"))
                                            .path())
                            .size());
            assertEquals(1, journal.count("COLETAS_PAGE_OVERLAP"));
            assertEquals(0, journal.count("COLETAS_COMPLETE"));
        }
    }
}
