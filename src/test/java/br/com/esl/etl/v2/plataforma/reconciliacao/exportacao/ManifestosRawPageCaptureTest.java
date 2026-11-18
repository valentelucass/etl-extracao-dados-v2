package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosRawPageCaptureTest {
    @TempDir Path directory;

    @Test
    void expandedOccurrencesRetainRawBytesWithoutPretendingToCompleteTheDay() throws Exception {
        try (var fixture = new Fixture()) {
            final var records = ExportValidationSupport.JSON.createArrayNode();
            for (int count = 0; count < 120; count++) {
                records.add(fixture.values.records.get(0));
            }
            fixture.body = " \n" + records + " \n";
            final var result = fixture.capture(3);
            assertTrue(result.path("pageComplete").booleanValue());
            assertFalse(result.path("intervalComplete").booleanValue());
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("comparisonPassed").booleanValue());
            assertEquals(120, result.path("rows").intValue());
            assertEquals(1, result.path("roots").intValue());
            assertEquals(3, fixture.selectedPage);
            assertEquals(
                    fixture.body,
                    Files.readString(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path()));
        }
    }

    @Test
    void emptyFirstPageAndEmptyLaterPageHaveDifferentCoverageMeaning() throws Exception {
        for (final int page : new int[] {1, 2}) {
            try (var fixture = new Fixture()) {
                fixture.body = "[]";
                final var result = fixture.capture(page);
                assertEquals(0, result.path("rows").intValue());
                assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
                assertEquals(page == 1, result.path("intervalComplete").booleanValue());
                assertEquals(
                        2, ExportValidationSupport.Pin.from(result.required("dataPin")).bytes());
            }
        }
    }

    @Test
    void shapeDriftMissingAndExtraFieldsRefuseWithoutQualifyingTheRawPage() throws Exception {
        for (final boolean extra : new boolean[] {false, true}) {
            try (var fixture = new Fixture()) {
                final var record = fixture.values.records.get(0);
                if (extra) {
                    record.put("not_observed", true);
                } else {
                    record.remove("sequence_code");
                }
                fixture.body = "[" + record + "]";
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1));
                assertEquals(0, fixture.journal.count("MANIFESTOS_RAW_PAGE_COMPLETE"));
            }
        }
    }

    @Test
    void oneHundredDistinctRootsIsThePageLimitRegardlessOfExpandedOccurrenceCount()
            throws Exception {
        try (var fixture = new Fixture()) {
            final var rows = ExportValidationSupport.JSON.createArrayNode();
            for (int index = 1; index <= 101; index++) {
                rows.add(fixture.values.records.get(0).deepCopy().put("sequence_code", index));
            }
            fixture.body = rows.toString();
            assertEquals(
                    "MANIFESTOS_RAW_PAGE_100_ROOTS",
                    assertThrows(IllegalArgumentException.class, () -> fixture.capture(1))
                            .getMessage());
        }
    }

    @Test
    void trailingJsonWrongCardinalityAndNonIntegralRootAreRefused() throws Exception {
        for (final int mutation : new int[] {0, 1, 2}) {
            try (var fixture = new Fixture()) {
                if (mutation == 0) {
                    fixture.body = "[] {}";
                }
                if (mutation == 1) {
                    fixture.body = "{}";
                }
                if (mutation == 2) {
                    fixture.body =
                            "[" + fixture.values.records.get(0).put("sequence_code", "7") + "]";
                }
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1));
            }
        }
    }

    @Test
    void metadataDriftStopsBeforeDataAndNon200DataNeverQualifies() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.infoBody = "{}";
            assertThrows(Exception.class, () -> fixture.capture(1));
            assertEquals(0, fixture.selectedPage);
        }
        try (var fixture = new Fixture()) {
            fixture.dataStatus = 429;
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(1));
            assertEquals(0, fixture.journal.count("MANIFESTOS_RAW_PAGE_COMPLETE"));
        }
    }

    @Test
    void refusedJournalCannotBeUsedAsAnAutomaticReplayAndDeadlineStopsBeforeHttp()
            throws Exception {
        try (var fixture = new Fixture()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ManifestosRawPageCapture.capture(
                                    fixture.values.request,
                                    1,
                                    fixture,
                                    fixture.journal,
                                    Instant.now().minusSeconds(1)));
            assertEquals(0, fixture.journal.count("HTTP_ADMITTED"));
            fixture.capture(1);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(1));
            assertEquals(2, fixture.journal.count("HTTP_ADMITTED"));
        }
    }

    @Test
    void routeWrapperAllowsOnlyInfoThenTheExactSelectedPageAndNeverRetries() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final URI origin = URI.create("https://capture.invalid");
        final URI info = origin.resolve("/api/analytics/reports/6399/info");
        final URI data =
                ManifestosIndependentRequest.query(
                        origin.resolve("/api/analytics/reports/6399/data"),
                        fixture.request,
                        4,
                        100);
        final int[] calls = {0};
        final OwnedExportWire physical =
                (method, target, body, auth, cap, timeout, deadline) -> {
                    calls[0]++;
                    return new CurlExportTransport.Wire(200, null, Instant.now());
                };
        final var wire = ManifestosRawPageCapture.scopedWire(physical, origin, fixture.request, 4);
        final Duration timeout = Duration.ofSeconds(10);
        final Instant deadline = Instant.now().plusSeconds(30);
        assertThrows(
                IllegalArgumentException.class,
                () -> wire.exchange("GET", data, null, true, 8388608, timeout, deadline));
        wire.exchange("GET", info, null, true, 262144, timeout, deadline);
        assertThrows(
                IllegalArgumentException.class,
                () -> wire.exchange("POST", data, null, true, 8388608, timeout, deadline));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        wire.exchange(
                                "GET",
                                URI.create(data + "&x=1"),
                                null,
                                true,
                                8388608,
                                timeout,
                                deadline));
        wire.exchange("GET", data, null, true, 8388608, timeout, deadline);
        assertThrows(
                IllegalArgumentException.class,
                () -> wire.exchange("GET", data, null, true, 8388608, timeout, deadline));
        assertEquals(2, calls[0]);
    }

    @Test
    void rawPageRetainsClosedAtNullAndCiotCloseErrorWithoutClaimingPresentationQualification()
            throws Exception {
        try (var fixture = new Fixture()) {
            fixture.values
                    .records
                    .get(0)
                    .putNull("closed_at")
                    .put("mft_cat_cot_status", "close_error");
            fixture.body = "[" + fixture.values.records.get(0) + "]";
            final var result = fixture.capture(2);
            assertTrue(result.path("pageComplete").booleanValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("comparisonPassed").booleanValue());
            assertEquals(
                    "fbb1d4b349b02dbf9450cb2ec113eacf55643a219e837bf4ce624d7b7b43cccc",
                    result.path("shapeSha256").textValue());
            assertEquals(
                    fixture.body,
                    Files.readString(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path()));
            assertEquals(2, fixture.wireCount);
        }
    }

    private final class Fixture implements ManifestosIntervalCapture.Port, AutoCloseable {
        private final ManifestosContentFixture values;
        private final PersistentExportJournal journal;
        private String body;
        private String infoBody;
        private int selectedPage;
        private int dataStatus = 200;
        private int wireCount;

        Fixture() throws Exception {
            final Path root =
                    Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
            values = new ManifestosContentFixture(root, 1);
            body = "[" + values.records.get(0) + "]";
            final var info = ExportValidationSupport.JSON.createObjectNode();
            final var fields = info.putArray("fields");
            final var filters = info.putArray("filters");
            for (final var column : values.catalog.required("columns")) {
                fields.add(column.path("sourceField").asText());
            }
            try (var shape =
                    ManifestosRawPageCaptureTest.class.getResourceAsStream(
                            "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json")) {
                for (final var metadata :
                        ExportValidationSupport.JSON.readTree(shape).required("metadata")) {
                    if ("DATA_FILTER".equals(metadata.path("kind").asText())) {
                        final String[] parts = metadata.path("path").asText().split("\\.", -1);
                        filters.addObject().put("table", parts[0]).put("field", parts[1]);
                    }
                }
            }
            infoBody = info.toString();
            journal =
                    PersistentExportJournal.syntheticFixture(
                            root.resolve("journal"),
                            "a".repeat(64),
                            "b".repeat(64),
                            UUID.fromString(values.request.path("attemptId").asText()),
                            true,
                            new Events());
        }

        ObjectNode capture(final int page) throws Exception {
            return ManifestosRawPageCapture.capture(
                    values.request, page, this, journal, Instant.now().plusSeconds(30));
        }

        @Override
        public CurlExportTransport.Wire info() throws Exception {
            return wire(200, infoBody);
        }

        @Override
        public CurlExportTransport.Wire page(
                final com.fasterxml.jackson.databind.JsonNode request,
                final int page,
                final int per)
                throws Exception {
            selectedPage = page;
            assertEquals(100, per);
            return wire(dataStatus, body);
        }

        private CurlExportTransport.Wire wire(final int status, final String value)
                throws Exception {
            journal.record(
                    "HTTP_ADMITTED",
                    ExportValidationSupport.JSON.createObjectNode().put("fixture", true));
            final var pin =
                    values.pin(
                            "wire-" + ++wireCount + ".json",
                            value.getBytes(StandardCharsets.UTF_8));
            journal.record(
                    "HTTP_COMPLETE",
                    ExportValidationSupport.JSON.createObjectNode().put("status", status));
            return new CurlExportTransport.Wire(status, pin, Instant.now());
        }

        @Override
        public void close() throws IOException {
            journal.close();
        }
    }

    private static final class Events implements PersistentExportJournal.EventChain {
        private final List<CampaignJournal.Event> events = new ArrayList<>();

        @Override
        public CampaignJournal.Event append(
                final String id,
                final UUID nonce,
                final CampaignJournal.Kind kind,
                final String detail) {
            final String previous =
                    events.isEmpty() ? "0".repeat(64) : events.get(events.size() - 1).sha256();
            final int sequence = events.size() + 1;
            final String sha =
                    ExportValidationSupport.sha256(
                            (sequence + "|" + id + "|" + nonce + "|" + kind + "|" + detail + "|"
                                            + previous)
                                    .getBytes(StandardCharsets.UTF_8));
            final var event =
                    new CampaignJournal.Event(sequence, id, nonce, kind, detail, previous, sha);
            events.add(event);
            return event;
        }

        @Override
        public List<CampaignJournal.Event> read() {
            return List.copyOf(events);
        }
    }
}
