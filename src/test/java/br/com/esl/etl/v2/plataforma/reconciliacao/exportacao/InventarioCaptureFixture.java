package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic authored records and transport only; never a provider or physical Source attestation.
 */
final class InventarioCaptureFixture implements AutoCloseable {
    static final String SELECTED = "a".repeat(64);
    static final String CODE = "b".repeat(64);
    static final Instant NOW = Instant.parse("2026-11-06T20:00:00Z");
    final Path root;
    final ObjectNode request = request("2026-10-12", "2026-10-18");
    final MutableClock clock = new MutableClock();
    final Port port = new Port();
    PersistentExportJournal journal;
    Instant deadline = NOW.plusSeconds(120);

    InventarioCaptureFixture(final Path parent, final boolean durable) throws Exception {
        root = Files.createDirectory(parent.resolve(UUID.randomUUID().toString())).toAbsolutePath();
        journal =
                durable
                        ? PersistentExportJournal.create(
                                root.resolve("journal"), SELECTED, CODE, attempt())
                        : PersistentExportJournal.syntheticFixture(
                                root.resolve("journal"),
                                SELECTED,
                                CODE,
                                attempt(),
                                true,
                                new Events());
    }

    static ObjectNode request(final String start, final String end) {
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INVENTARIO_INDEPENDENT_REQUEST_V1")
                        .put("profile", InventarioIndependentRequest.PROFILE)
                        .put("entity", "INVENTARIO")
                        .put("reportId", 10633)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", start)
                        .put("periodEnd", end)
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        InventarioIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(InventarioIndependentRequest.STARTED_AT_FILTER, start + " - " + end);
        return request;
    }

    static JsonNode contract() throws Exception {
        try (var stream =
                InventarioCaptureFixture.class.getResourceAsStream(
                        "/contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json")) {
            if (stream == null) {
                throw new IllegalStateException("FIXTURE_CONTRACT_REQUIRED");
            }
            return ExportValidationSupport.JSON.readTree(stream);
        }
    }

    static ObjectNode info() throws Exception {
        final var info = ExportValidationSupport.JSON.createObjectNode();
        final var fields = info.putObject("fields");
        final var filters = info.putArray("filters");
        for (final var metadata : contract().required("metadata")) {
            final String path = metadata.path("path").asText();
            if ("DATA_FIELD".equals(metadata.path("kind").asText())) {
                fields.putObject(path);
            } else {
                final String[] parts = path.split("\\.", -1);
                filters.addObject().put("table", parts[0]).put("field", parts[1]);
            }
        }
        return info;
    }

    static ObjectNode row(final long key) throws Exception {
        final var row = ExportValidationSupport.JSON.createObjectNode();
        for (final var field : contract().required("fields")) {
            final String name = field.path("path").textValue();
            boolean nullable = false;
            for (final var type : field.required("wireTypes")) {
                nullable |= "NULL".equals(type.asText());
            }
            if (nullable) {
                row.putNull(name);
            } else {
                row.put(name, "raw-" + name);
            }
        }
        row.put("sequence_code", key)
                .put("started_at", "2026-10-13T12:34:56-03:00")
                .put("cnr_c_s_fit_invoices_value", "12.3400")
                .put("cnr_c_s_fit_real_weight", "1.2500")
                .put("cnr_c_s_fit_invoices_volumes", 7)
                .put("cnr_crn_psn_nickname", "  A\u0301  ");
        return row;
    }

    static ArrayNode array(final JsonNode... records) {
        final var result = ExportValidationSupport.JSON.createArrayNode();
        for (final var record : records) {
            result.add(record);
        }
        return result;
    }

    static byte[] json(final JsonNode value) throws Exception {
        return ExportValidationSupport.JSON.writeValueAsBytes(value);
    }

    ExportValidationSupport.Pin pin(final JsonNode value) throws Exception {
        return pin(json(value));
    }

    ExportValidationSupport.Pin pin(final byte[] bytes) throws Exception {
        final Path path = root.resolve(UUID.randomUUID() + ".json");
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }

    UUID attempt() {
        return UUID.fromString(request.path("attemptId").textValue());
    }

    ObjectNode capture(final int pages, final int per, final long rows) throws Exception {
        return InventarioIntervalCapture.capture(
                request, port, journal, root, pages, per, rows, deadline, clock);
    }

    void reopen() throws Exception {
        journal.close();
        journal = PersistentExportJournal.open(root.resolve("journal"), SELECTED, CODE, attempt());
    }

    @Override
    public void close() throws IOException {
        journal.close();
    }

    final class Port implements InventarioIntervalCapture.Port {
        byte[] infoBytes = json(InventarioCaptureFixture.info());
        final List<byte[]> pages = new ArrayList<>();
        int infoCalls;
        int pageCalls;
        int status = 200;

        Port() throws Exception {}

        @Override
        public CurlExportTransport.Wire info() throws Exception {
            infoCalls++;
            return wire(200, infoBytes);
        }

        @Override
        public CurlExportTransport.Wire page(final JsonNode selected, final int page, final int per)
                throws Exception {
            if (page != pageCalls + 1) {
                throw new IllegalStateException("FIXTURE_CONTIGUOUS_PAGE_REQUIRED");
            }
            pageCalls++;
            return wire(status, pages.get(page - 1));
        }

        private CurlExportTransport.Wire wire(final int code, final byte[] bytes) throws Exception {
            journal.record(
                    "HTTP_ADMITTED",
                    ExportValidationSupport.JSON.createObjectNode().put("syntheticFixture", true));
            final var body = pin(bytes);
            journal.record(
                    "HTTP_COMPLETE",
                    ExportValidationSupport.JSON.createObjectNode().put("status", code));
            return new CurlExportTransport.Wire(code, body, clock.instant());
        }
    }

    static final class MutableClock extends Clock {
        Instant value = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return Clock.fixed(value, zone);
        }

        @Override
        public Instant instant() {
            return value;
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
            final String hash =
                    ExportValidationSupport.sha256(
                            (sequence + "|" + id + "|" + nonce + "|" + kind + "|" + detail + "|"
                                            + previous)
                                    .getBytes(StandardCharsets.UTF_8));
            final var event =
                    new CampaignJournal.Event(sequence, id, nonce, kind, detail, previous, hash);
            events.add(event);
            return event;
        }

        @Override
        public List<CampaignJournal.Event> read() {
            return List.copyOf(events);
        }
    }
}
