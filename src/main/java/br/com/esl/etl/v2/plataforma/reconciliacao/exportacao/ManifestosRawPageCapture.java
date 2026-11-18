package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** One complete raw page, including [], never a claim of complete interval coverage. */
public final class ManifestosRawPageCapture {
    private static final String SHAPE_SHA =
            "fbb1d4b349b02dbf9450cb2ec113eacf55643a219e837bf4ce624d7b7b43cccc";

    private ManifestosRawPageCapture() {}

    /** Narrows the existing physical boundary to INFO followed by one exact page URL. */
    static OwnedExportWire scopedWire(
            final OwnedExportWire physical,
            final URI origin,
            final JsonNode request,
            final int page) {
        require(physical != null && page >= 1 && page <= 16, "MANIFESTOS_RAW_PAGE_SCOPE");
        ManifestosIndependentRequest.validate(request);
        final URI info = origin.resolve("/api/analytics/reports/6399/info");
        final URI data =
                ManifestosIndependentRequest.query(
                        origin.resolve("/api/analytics/reports/6399/data"),
                        request.deepCopy(),
                        page,
                        100);
        return new OwnedExportWire() {
            private int calls;

            @Override
            public synchronized CurlExportTransport.Wire exchange(
                    final String method,
                    final URI target,
                    final byte[] body,
                    final boolean authentication,
                    final int maximumBodyBytes,
                    final Duration timeout,
                    final Instant deadline)
                    throws Exception {
                require(
                        calls < 2
                                && "GET".equals(method)
                                && body == null
                                && authentication
                                && (calls == 0 ? info : data).equals(target)
                                && maximumBodyBytes == (calls == 0 ? 262144 : 8388608),
                        "MANIFESTOS_RAW_PAGE_EXACT_TWO_ROUTES");
                calls++;
                return physical.exchange(
                        method, target, body, authentication, maximumBodyBytes, timeout, deadline);
            }
        };
    }

    public static ObjectNode capture(
            final JsonNode request,
            final int page,
            final ManifestosIntervalCapture.Port transport,
            final PersistentExportJournal journal,
            final Instant deadline)
            throws Exception {
        ManifestosIndependentRequest.validate(request);
        require(
                page >= 1
                        && page <= 16
                        && transport != null
                        && journal != null
                        && deadline != null
                        && Instant.now().isBefore(deadline)
                        && Duration.between(Instant.now(), deadline)
                                        .compareTo(Duration.ofSeconds(210))
                                <= 0,
                "MANIFESTOS_RAW_PAGE_FINITE_SCOPE");
        require(
                journal.count("HTTP_ADMITTED") == 0
                        && journal.count("HTTP_COMPLETE") == 0
                        && journal.latest("MANIFESTOS_RAW_PAGE_STARTED") == null,
                "MANIFESTOS_RAW_PAGE_FRESH_JOURNAL");
        journal.record(
                "MANIFESTOS_RAW_PAGE_STARTED",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("attemptId", text(request, "attemptId"))
                        .put("page", page)
                        .put("per", 100)
                        .put("maximumHttpRequests", 2)
                        .put("maximumRetainedBytes", 8388608)
                        .put("deadline", deadline.toString()));
        final Instant started = Instant.now();
        final var shape = shape();
        final var info = transport.info();
        final var parsed =
                new DataExportTemplateInfoParser().parse(response(info, 262144, started, deadline));
        final Set<String> fields = new HashSet<>();
        final Set<String> filters = new HashSet<>();
        parsed.fields().forEach(field -> fields.add(field.technicalName()));
        parsed.filters().forEach(field -> filters.add(field.technicalName()));
        final Set<String> expectedFilters = new HashSet<>();
        shape.required("metadata")
                .forEach(
                        field -> {
                            if ("DATA_FILTER".equals(field.path("kind").asText())) {
                                expectedFilters.add(text(field, "path"));
                            }
                        });
        final Set<String> expectedFields = new HashSet<>();
        for (final var field : shape.path("response").required("fields")) {
            final String path = text(field, "path");
            if (path.indexOf('/', 1) < 0) {
                expectedFields.add(path.substring(1));
            }
        }
        require(
                expectedFields.size() == 91
                        && expectedFilters.size() == 18
                        && fields.equals(expectedFields)
                        && filters.equals(expectedFilters),
                "MANIFESTOS_RAW_PAGE_INFO_91_18");
        final var wire = transport.page(request.deepCopy(), page, 100);
        final var rows = response(wire, 8388608, started, deadline);
        require(rows.isArray() && rows.size() <= 12000, "MANIFESTOS_RAW_PAGE_ARRAY_BOUND");
        final Set<BigInteger> roots = new HashSet<>();
        for (final var row : rows) {
            require(Instant.now().isBefore(deadline), "MANIFESTOS_RAW_PAGE_DEADLINE");
            require(row.isObject() && row.size() == 91, "MANIFESTOS_RAW_PAGE_EXACT_91_FIELDS");
            final Set<String> names = new HashSet<>();
            row.fieldNames().forEachRemaining(names::add);
            require(names.equals(expectedFields), "MANIFESTOS_RAW_PAGE_FIELD_SET");
            final var key = row.required("sequence_code");
            require(
                    key.isIntegralNumber() && key.bigIntegerValue().signum() >= 0,
                    "MANIFESTOS_RAW_PAGE_INTEGRAL_ROOT");
            roots.add(key.bigIntegerValue());
            require(roots.size() <= 100, "MANIFESTOS_RAW_PAGE_100_ROOTS");
        }
        require(
                journal.count("HTTP_ADMITTED") == 2 && journal.count("HTTP_COMPLETE") == 2,
                "MANIFESTOS_RAW_PAGE_HTTP_CLOSURE");
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_RAW_PAGE_CAPTURE_V1")
                        .put("pageComplete", true)
                        .put("pageIndex", page)
                        .put("per", 100)
                        .put("rows", rows.size())
                        .put("roots", roots.size())
                        .put("terminalEmptyPageObserved", rows.isEmpty())
                        .put("intervalComplete", page == 1 && rows.isEmpty())
                        .put("rawBytesRetained", true)
                        .put("rowTypesQualified", false)
                        .put("deduplicationPerformed", false)
                        .put("serviceDateVerifiedFromRows", false)
                        .put("comparisonPassed", false)
                        .put("contentAccepted", false)
                        .put("HTTP", 2)
                        .put("httpAdmitted", 2)
                        .put("wireOutcomeUnknown", false)
                        .put("Source", 1)
                        .put("SQL", 0)
                        .put("shapeSha256", SHAPE_SHA);
        result.set("infoPin", ExportValidationSupport.JSON.valueToTree(info.body()));
        result.set("dataPin", ExportValidationSupport.JSON.valueToTree(wire.body()));
        journal.record("MANIFESTOS_RAW_PAGE_COMPLETE", result);
        return result;
    }

    private static JsonNode response(
            final CurlExportTransport.Wire wire,
            final int maximum,
            final Instant started,
            final Instant deadline)
            throws Exception {
        require(
                wire != null
                        && wire.status() == 200
                        && wire.body() != null
                        && wire.body().bytes() <= maximum
                        && !wire.completedAt().isBefore(started)
                        && !wire.completedAt().isAfter(deadline)
                        && Instant.now().isBefore(deadline),
                "MANIFESTOS_RAW_PAGE_RESPONSE");
        final byte[] bytes =
                wire.body()
                        .read(maximum, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && parser.nextToken() == null,
                    "MANIFESTOS_RAW_PAGE_RESPONSE_EOF");
            return value;
        }
    }

    private static JsonNode shape() throws Exception {
        try (var input =
                ManifestosRawPageCapture.class.getResourceAsStream(
                        "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json")) {
            require(input != null, "MANIFESTOS_RAW_PAGE_SHAPE_REQUIRED");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && SHAPE_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "MANIFESTOS_RAW_PAGE_SHAPE_PIN");
            return ExportValidationSupport.JSON.readTree(bytes);
        }
    }
}
