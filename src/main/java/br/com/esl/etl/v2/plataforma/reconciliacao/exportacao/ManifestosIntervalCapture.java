package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * INFO6399 and complete raw pages; no presentation mapper, retry, deduplication or source
 * admission.
 */
public final class ManifestosIntervalCapture {
    public interface Port {
        CurlExportTransport.Wire info() throws Exception;

        CurlExportTransport.Wire page(JsonNode request, int page, int per) throws Exception;
    }

    private static final int MAX_BYTES = 8388608;
    private static final String SHAPE_SHA =
            "fbb1d4b349b02dbf9450cb2ec113eacf55643a219e837bf4ce624d7b7b43cccc";
    // Root-closed Source0 observation: manifestos0747-rawshape04/raw-shape-metadata.json,
    // SHA below; page2 SHA9d5f200c26c32f650f9f272128b31350f28949f436686f3166e1e79ecbf26590.
    // /finished_at (8 observations) and /closed_at (one Root-closed observation) admit raw NULL.
    // Keep the historical shape and all raw bytes;
    // this is no presentation rule, count ceiling, enum label, or qualification of another path.
    private static final Set<String> ADDITIONAL_RAW_NULL_PATHS =
            Set.of("/finished_at", "/closed_at");
    private static final String RAW_NULL_QUALIFICATION = "MANIFESTOS_RAW_FINISHED_AT_NULL_0747_V1";
    private static final String RAW_NULL_OBSERVATION_SHA =
            "595156699c94be522727198dbaed75b163b492b0360dc699a956bc13b749758c";
    private static final String CLOSED_AT_RAW_NULL_QUALIFICATION =
            "MANIFESTOS_RAW_CLOSED_AT_NULL_0747_V1";
    private static final String CLOSED_AT_RAW_NULL_OBSERVATION_SHA =
            "6eb8efe6808633a0f59bddb7690610b901d3c1d25c7907cf18e7f242b592f510";
    private static final byte[] PREFIX =
            "{\"entity\":\"MANIFESTOS\",\"templateId\":6399,\"record\":"
                    .getBytes(StandardCharsets.UTF_8);
    private static final URI SERIALIZATION_ENDPOINT =
            URI.create("https://serialization.invalid/api/analytics/reports/6399/data");

    private record Shape(Map<String, JsonNode> paths, Set<String> fields, Set<String> filters) {}

    private record Payload(Path path, long bytes, String sha256) {}

    private static final class Progress {
        private long rows;
        private long bytes;
        private int pages;
        private int reserved;
        private boolean terminal;
        private boolean promoted;
        private boolean payloadCreated;
        private String reason = "PARTIAL_PAGE_BUDGET_EXHAUSTED";
        private Exception failure;
        private Instant startedAt;
        private final Set<BigInteger> priorIds = new HashSet<>();
        private final ArrayNode responses = ExportValidationSupport.JSON.createArrayNode();
    }

    private ManifestosIntervalCapture() {}

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
        return capture(
                request,
                transport,
                journal,
                output,
                maximumPages,
                per,
                maximumRows,
                deadline,
                Clock.systemUTC());
    }

    static ObjectNode capture(
            final JsonNode request,
            final Port transport,
            final PersistentExportJournal journal,
            final Path output,
            final int maximumPages,
            final int per,
            final long maximumRows,
            final Instant deadline,
            final Clock clock)
            throws Exception {
        ManifestosIndependentRequest.validate(request);
        final JsonNode selected = request.deepCopy();
        ManifestosIndependentRequest.validate(selected);
        require(
                transport != null
                        && journal != null
                        && clock != null
                        && deadline != null
                        && maximumPages > 0
                        && maximumPages <= 16
                        && per > 0
                        && per <= 100
                        && maximumRows > 0
                        && maximumRows <= 12000,
                "MANIFESTOS_CAPTURE_FINITE_LIMITS");
        require(output != null && output.isAbsolute(), "MANIFESTOS_CAPTURE_ABSOLUTE_OUTPUT");
        final Path directory = output.normalize();
        checkPath(directory);
        require(
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS),
                "MANIFESTOS_CAPTURE_OUTPUT_DIRECTORY");
        final String requestSha =
                ExportValidationSupport.sha256(ExportValidationSupport.canonicalBytes(selected));
        final var started =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("attemptId", text(selected, "attemptId"))
                        .put("requestSha256", requestSha)
                        .put("output", directory.toString())
                        .put("maximumPages", maximumPages)
                        .put("per", per)
                        .put("maximumRows", maximumRows);
        final var previousStart = journal.latest("MANIFESTOS_STARTED");
        if (previousStart != null) {
            final JsonNode binding =
                    strictJson(ExportValidationSupport.JSON.writeValueAsBytes(started));
            for (final var names = binding.fieldNames(); names.hasNext(); ) {
                final String name = names.next();
                require(
                        binding.get(name).equals(previousStart.get(name)),
                        "MANIFESTOS_CAPTURE_REOPEN_BINDING");
            }
            final JsonNode complete = journal.latest("MANIFESTOS_COMPLETE");
            final JsonNode partial = journal.latest("MANIFESTOS_PARTIAL");
            final JsonNode previous = complete == null ? partial : complete;
            require(previous != null, "MANIFESTOS_UNCLOSED_CAPTURE_RECONCILIATION_REQUIRED");
            require(
                    text(selected, "attemptId").equals(text(previous, "attemptId"))
                            && requestSha.equals(text(previous, "requestSha256")),
                    "MANIFESTOS_CAPTURE_REOPEN_RESULT_BINDING");
            verifyRetained(previous, directory);
            return (ObjectNode) previous;
        }
        final Duration remaining = Duration.between(clock.instant(), deadline);
        require(
                !remaining.isNegative()
                        && !remaining.isZero()
                        && remaining.compareTo(Duration.ofSeconds(210)) <= 0,
                "MANIFESTOS_CAPTURE_SOURCE_210_SECONDS");
        final Path partialPath = directory.resolve("manifestos-source.partial.jsonl");
        final Path completePath = directory.resolve("manifestos-source.complete.jsonl");
        require(
                !Files.exists(partialPath, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(completePath, LinkOption.NOFOLLOW_LINKS),
                "MANIFESTOS_FRESH_RETAINED_PAYLOAD");
        final Shape shape = shape();
        journal.record(
                "MANIFESTOS_STARTED",
                started.put("deadline", deadline.toString())
                        .put("maximumHttpRequests", 17)
                        .put("maximumResponseBytes", MAX_BYTES)
                        .put("maximumRetainedPayloadBytes", MAX_BYTES)
                        .put("shapeSha256", SHAPE_SHA)
                        .put("additionalRawNullQualification", RAW_NULL_QUALIFICATION)
                        .put("closedAtRawNullQualification", CLOSED_AT_RAW_NULL_QUALIFICATION)
                        .put("closedAtRawNullObservationSha256", CLOSED_AT_RAW_NULL_OBSERVATION_SHA)
                        .put("rawNullObservationSha256", RAW_NULL_OBSERVATION_SHA));
        final long monotonicStart = System.nanoTime();
        final var progress = new Progress();
        progress.startedAt = clock.instant();
        try (var file =
                FileChannel.open(
                        partialPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            progress.payloadCreated = true;
            try {
                checkTime(clock, deadline, monotonicStart);
                reserve(journal, progress, selected, 0, per);
                checkTime(clock, deadline, monotonicStart);
                final byte[] info =
                        observe(
                                transport.info(),
                                journal,
                                progress,
                                0,
                                clock,
                                deadline,
                                monotonicStart);
                validateInfo(strictJson(info), shape);
                for (int page = 1; page <= maximumPages; page++) {
                    checkTime(clock, deadline, monotonicStart);
                    reserve(journal, progress, selected, page, per);
                    checkTime(clock, deadline, monotonicStart);
                    final byte[] bytes =
                            observe(
                                    transport.page(selected.deepCopy(), page, per),
                                    journal,
                                    progress,
                                    page,
                                    clock,
                                    deadline,
                                    monotonicStart);
                    final var ids =
                            retainPage(
                                    bytes,
                                    file,
                                    progress,
                                    shape,
                                    maximumRows,
                                    per,
                                    clock,
                                    deadline,
                                    monotonicStart);
                    file.force(true);
                    if (ids.isEmpty()) {
                        progress.terminal = true;
                        break;
                    }
                    final long overlap = ids.stream().filter(progress.priorIds::contains).count();
                    if (overlap > 0) {
                        progress.reason = "PARTIAL_PAGE_ID_OVERLAP_RECONCILIATION_REQUIRED";
                        journal.record(
                                "MANIFESTOS_PAGE_OVERLAP",
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("page", page)
                                        .put("overlappingDistinctIds", overlap)
                                        .put("allReceivedRowsRetained", true)
                                        .put("deduplicationPerformed", false));
                        break;
                    }
                    progress.priorIds.addAll(ids);
                }
            } finally {
                file.force(true);
            }
        } catch (final Exception failure) {
            fail(progress, failure);
        }
        journal.record(
                "MANIFESTOS_CAPTURE_CLOSURE",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("rowsRetained", progress.rows)
                        .put("pagesObserved", progress.pages)
                        .put("payloadBytes", progress.bytes)
                        .put("terminalEmptyPageObserved", progress.terminal)
                        .put("deduplicationPerformed", false));
        boolean complete = false;
        if (progress.failure == null && progress.terminal) {
            try {
                checkTime(clock, deadline, monotonicStart);
                verifyResponses(progress.responses);
                require(
                        journal.count("HTTP_ADMITTED") == journal.count("HTTP_COMPLETE"),
                        "MANIFESTOS_HTTP_RECONCILIATION_REQUIRED");
                final var payload = payload(partialPath);
                require(payload.bytes() == progress.bytes, "MANIFESTOS_PAYLOAD_SIZE_CHANGED");
                journal.record(
                        "MANIFESTOS_PROMOTION_RESERVED",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("path", payload.path().toString())
                                .put("bytes", payload.bytes())
                                .put("sha256", payload.sha256()));
                Files.move(partialPath, completePath, StandardCopyOption.ATOMIC_MOVE);
                progress.promoted = true;
                final var promoted = payload(completePath);
                require(
                        payload.bytes() == promoted.bytes()
                                && payload.sha256().equals(promoted.sha256()),
                        "MANIFESTOS_PROMOTED_PAYLOAD_CHANGED");
                verifyResponses(progress.responses);
                checkTime(clock, deadline, monotonicStart);
                complete = true;
            } catch (final Exception failure) {
                fail(progress, failure);
            }
        }
        final Path retainedPath = progress.promoted ? completePath : partialPath;
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INTERVAL_CAPTURE_V1")
                        .put("sourceKind", "DATA_EXPORT")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("attemptId", text(selected, "attemptId"))
                        .put("requestSha256", requestSha)
                        .put("status", complete ? "MANIFESTOS_SOURCE_COMPLETE" : progress.reason)
                        .put("rows", progress.rows)
                        .put("pages", progress.pages)
                        .put("httpRequestsReserved", progress.reserved)
                        .put("responsesObserved", progress.responses.size())
                        .put("terminalEmptyPageObserved", progress.terminal)
                        .put("payloadPromoted", complete)
                        .put("promotionNeedsReconciliation", progress.promoted && !complete)
                        .put("deduplicationPerformed", false)
                        .put("contentQualified", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("serviceDateVerifiedFromRows", false)
                        .put("sourceAdmissionGrantedByCapture", false)
                        .put("SQL", 0)
                        .put("additionalRawNullQualification", RAW_NULL_QUALIFICATION)
                        .put("closedAtRawNullQualification", CLOSED_AT_RAW_NULL_QUALIFICATION)
                        .put("closedAtRawNullObservationSha256", CLOSED_AT_RAW_NULL_OBSERVATION_SHA)
                        .put("rawNullObservationSha256", RAW_NULL_OBSERVATION_SHA);
        result.set(
                "additionalRawNullPaths",
                ExportValidationSupport.JSON.valueToTree(ADDITIONAL_RAW_NULL_PATHS));
        result.set("responses", progress.responses);
        if (progress.payloadCreated && Files.exists(retainedPath, LinkOption.NOFOLLOW_LINKS)) {
            final var retained = payload(retainedPath);
            result.put("retainedPayloadPath", retained.path().toString())
                    .put("payloadBytes", retained.bytes())
                    .put("payloadSha256", retained.sha256())
                    .put("emptyInterval", retained.bytes() == 0);
            if (retained.bytes() > 0) {
                result.set(
                        complete ? "dataPin" : "partialDataPin",
                        ExportValidationSupport.JSON.valueToTree(
                                new ExportValidationSupport.Pin(
                                        retained.path(), retained.sha256(), retained.bytes())));
            }
        }
        final String outcome = complete ? "MANIFESTOS_COMPLETE" : "MANIFESTOS_PARTIAL";
        journal.record(outcome, result);
        if (progress.failure != null) {
            throw progress.failure;
        }
        return (ObjectNode) journal.latest(outcome);
    }

    private static void reserve(
            final PersistentExportJournal journal,
            final Progress progress,
            final JsonNode request,
            final int page,
            final int per)
            throws IOException {
        require(progress.reserved < 17, "MANIFESTOS_HTTP_17_REQUEST_BOUND");
        journal.requireRequestBudget(17);
        final var fact =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("method", "GET")
                        .put("attemptId", text(request, "attemptId"))
                        .put("page", page);
        if (page == 0) {
            fact.put("path", "/api/analytics/reports/6399/info");
        } else {
            fact.put("transport", "GET_WITH_QUERY")
                    .put("per", per)
                    .put(
                            "querySha256",
                            ExportValidationSupport.sha256(
                                    ManifestosIndependentRequest.query(
                                                    SERIALIZATION_ENDPOINT, request, page, per)
                                            .getRawQuery()
                                            .getBytes(StandardCharsets.US_ASCII)));
        }
        journal.record(page == 0 ? "MANIFESTOS_INFO_RESERVED" : "MANIFESTOS_PAGE_RESERVED", fact);
        progress.reserved++;
    }

    private static byte[] observe(
            final CurlExportTransport.Wire wire,
            final PersistentExportJournal journal,
            final Progress progress,
            final int page,
            final Clock clock,
            final Instant deadline,
            final long monotonicStart)
            throws Exception {
        require(
                wire != null && wire.body() != null && wire.completedAt() != null,
                "MANIFESTOS_COMPLETE_WIRE_REQUIRED");
        final byte[] bytes = read(wire.body());
        final var fact =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("page", page)
                        .put("status", wire.status())
                        .put("completedAt", wire.completedAt().toString())
                        .put("rawBodyBytesVerified", true);
        fact.set("rawBodyPin", ExportValidationSupport.JSON.valueToTree(wire.body()));
        progress.responses.add(fact);
        progress.pages += page == 0 ? 0 : 1;
        journal.record(page == 0 ? "MANIFESTOS_INFO_OBSERVED" : "MANIFESTOS_PAGE_OBSERVED", fact);
        require(wire.status() == 200, "MANIFESTOS_STOP_NON_200_NO_RETRY");
        require(
                !wire.completedAt().isAfter(deadline)
                        && !wire.completedAt().isAfter(clock.instant())
                        && !wire.completedAt().isBefore(progress.startedAt),
                "MANIFESTOS_WIRE_COMPLETION_WINDOW");
        checkTime(clock, deadline, monotonicStart);
        return bytes;
    }

    private static Set<BigInteger> retainPage(
            final byte[] bytes,
            final FileChannel file,
            final Progress progress,
            final Shape shape,
            final long maximumRows,
            final int per,
            final Clock clock,
            final Instant deadline,
            final long monotonicStart)
            throws Exception {
        final var ids = new HashSet<BigInteger>();
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            require(
                    parser.nextToken() == JsonToken.START_ARRAY,
                    "MANIFESTOS_QUALIFIED_ROOT_ARRAY_PAGE");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                checkTime(clock, deadline, monotonicStart);
                require(
                        parser.currentToken() == JsonToken.START_OBJECT,
                        "MANIFESTOS_ROW_OBJECT_REQUIRED");
                final int start = Math.toIntExact(parser.currentTokenLocation().getByteOffset());
                final JsonNode row = ExportValidationSupport.JSON.readTree(parser);
                final int end = Math.toIntExact(parser.currentLocation().getByteOffset());
                validateRow(row, shape);
                final var key = row.required("sequence_code");
                require(key.isNumber(), "MANIFESTOS_SEQUENCE_CODE_NUMERIC_REQUIRED");
                final var identity = new BigDecimal(key.asText());
                require(
                        identity.stripTrailingZeros().scale() <= 0,
                        "MANIFESTOS_SEQUENCE_CODE_INTEGRAL_REQUIRED");
                ids.add(identity.toBigIntegerExact());
                require(ids.size() <= per, "MANIFESTOS_PAGE_DISTINCT_SEQUENCE_LIMIT");
                require(progress.rows < maximumRows, "MANIFESTOS_ROWS_LIMIT_PARTIAL");
                require(
                        start >= 0
                                && end > start
                                && end <= bytes.length
                                && bytes[start] == '{'
                                && bytes[end - 1] == '}',
                        "MANIFESTOS_ORIGINAL_RECORD_BYTE_BOUNDARY");
                final byte[] original = compactRecord(bytes, start, end);
                final int size = PREFIX.length + original.length + 2;
                require(
                        progress.bytes + size <= MAX_BYTES,
                        "MANIFESTOS_RETAINED_PAYLOAD_8MIB_BOUND");
                write(file, PREFIX);
                write(file, original);
                write(file, new byte[] {'}', '\n'});
                progress.rows++;
                progress.bytes += size;
            }
            require(parser.nextToken() == null, "MANIFESTOS_RESPONSE_TRAILING_JSON");
        }
        return ids;
    }

    private static void validateRow(final JsonNode row, final Shape shape) {
        require(
                row != null && row.isObject() && row.size() == 91,
                "MANIFESTOS_EXACT_91_ROW_FIELDS");
        final var actual = new HashSet<String>();
        row.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(shape.fields()), "MANIFESTOS_UNKNOWN_OR_MISSING_FIELD");
        row.fields()
                .forEachRemaining(
                        entry -> validateValue(entry.getValue(), "/" + entry.getKey(), shape));
    }

    private static void validateValue(final JsonNode value, final String path, final Shape shape) {
        final JsonNode rule = shape.paths().get(path);
        require(rule != null, "MANIFESTOS_UNKNOWN_NESTED_PATH");
        if (value.isNull()) {
            require(
                    rule.required("nullable").booleanValue()
                            || ADDITIONAL_RAW_NULL_PATHS.contains(path),
                    "MANIFESTOS_NULL_TYPE_NOT_QUALIFIED");
            return;
        }
        final String type =
                value.isTextual()
                        ? "STRING"
                        : value.isIntegralNumber()
                                ? "INTEGER"
                                : value.isNumber()
                                        ? "NUMBER"
                                        : value.isBoolean()
                                                ? "BOOLEAN"
                                                : value.isArray() ? "ARRAY" : "OBJECT";
        boolean admitted = false;
        for (final var expected : rule.required("jsonTypes")) {
            admitted |= type.equals(expected.asText());
        }
        require(admitted, "MANIFESTOS_DATA_TYPE_NOT_QUALIFIED");
        if (value.isNumber()) {
            new ExactExportTable.Atom("NUMBER", value.asText());
        } else if (value.isArray()) {
            for (final var child : value) {
                validateValue(child, path + "/*", shape);
            }
        }
    }

    private static Shape shape() throws IOException {
        try (var input =
                ManifestosIntervalCapture.class.getResourceAsStream(
                        "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json")) {
            require(input != null, "MANIFESTOS_CAPTURE_SHAPE_REQUIRED");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && SHAPE_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "MANIFESTOS_CAPTURE_SHAPE_PIN");
            final var contract = strictJson(bytes);
            require(
                    contract.path("templateId").intValue() == 6399
                            && "$".equals(contract.path("response").path("recordRoot").asText())
                            && "ARRAY"
                                    .equals(
                                            contract.path("response")
                                                    .path("rootCardinality")
                                                    .asText())
                            && "/sequence_code"
                                    .equals(contract.path("response").path("keyPath").asText()),
                    "MANIFESTOS_CAPTURE_SHAPE_SCOPE");
            final Map<String, JsonNode> paths = new HashMap<>();
            final Set<String> fields = new HashSet<>();
            final Set<String> filters = new HashSet<>();
            for (final var field : contract.path("response").required("fields")) {
                final String path = text(field, "path");
                require(
                        "RECORD".equals(text(field, "scope"))
                                && paths.putIfAbsent(path, field) == null,
                        "MANIFESTOS_CAPTURE_UNIQUE_SHAPE");
                if (path.indexOf('/', 1) < 0) {
                    require(
                            "REQUIRED".equals(text(field, "presence")),
                            "MANIFESTOS_CAPTURE_REQUIRED_91");
                    fields.add(path.substring(1));
                }
            }
            contract.required("metadata")
                    .forEach(
                            field -> {
                                if ("DATA_FILTER".equals(field.path("kind").asText())) {
                                    filters.add(text(field, "path"));
                                }
                            });
            require(
                    fields.size() == 91 && paths.size() == 95 && filters.size() == 18,
                    "MANIFESTOS_CAPTURE_91_18_SHAPE");
            return new Shape(Map.copyOf(paths), Set.copyOf(fields), Set.copyOf(filters));
        }
    }

    private static void validateInfo(final JsonNode info, final Shape shape) {
        final var parsed = new DataExportTemplateInfoParser().parse(info);
        final Set<String> fields = new HashSet<>();
        final Set<String> filters = new HashSet<>();
        parsed.fields().forEach(field -> fields.add(field.technicalName()));
        parsed.filters().forEach(field -> filters.add(field.technicalName()));
        require(
                fields.equals(shape.fields()) && filters.equals(shape.filters()),
                "MANIFESTOS_INFO_EXACT_91_FIELDS_18_FILTERS");
    }

    private static JsonNode strictJson(final byte[] bytes) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && parser.nextToken() == null,
                    "MANIFESTOS_RESPONSE_TRAILING_JSON");
            return value;
        }
    }

    private static void write(final FileChannel file, final byte[] bytes) throws IOException {
        final var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            require(file.write(buffer) > 0, "MANIFESTOS_PAYLOAD_WRITE_MADE_NO_PROGRESS");
        }
    }

    /**
     * JSONL removes only whitespace outside strings; numeric lexemes and escaped/raw UTF8 stay
     * intact.
     */
    private static byte[] compactRecord(final byte[] bytes, final int start, final int end) {
        final var out = new ByteArrayOutputStream(Math.min(end - start, 262144));
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < end; index++) {
            final byte value = bytes[index];
            if (!quoted && (value == ' ' || value == '\t' || value == '\r' || value == '\n')) {
                continue;
            }
            require(out.size() + PREFIX.length + 2 < 262144, "MANIFESTOS_PAYLOAD_LINE_BOUND");
            out.write(value);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
            } else if (value == '"') {
                quoted = true;
            }
        }
        require(!quoted && !escaped, "MANIFESTOS_ORIGINAL_RECORD_STRING_BOUNDARY");
        return out.toByteArray();
    }

    private static byte[] read(final ExportValidationSupport.Pin pin) throws IOException {
        return pin.read(MAX_BYTES, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
    }

    private static void verifyResponses(final JsonNode responses) throws IOException {
        require(responses.isArray() && responses.size() <= 17, "MANIFESTOS_RESPONSE_COUNT_BOUND");
        for (final var response : responses) {
            read(ExportValidationSupport.Pin.from(response.required("rawBodyPin")));
        }
    }

    private static Payload payload(final Path path) throws IOException {
        checkPath(path);
        require(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                "MANIFESTOS_RETAINED_REGULAR_FILE");
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        require(bytes.length <= MAX_BYTES, "MANIFESTOS_RETAINED_PAYLOAD_8MIB_BOUND");
        return new Payload(path, bytes.length, ExportValidationSupport.sha256(bytes));
    }

    private static void verifyRetained(final JsonNode result, final Path directory)
            throws IOException {
        verifyResponses(result.required("responses"));
        require(
                result.has("retainedPayloadPath"),
                "MANIFESTOS_RETAINED_PAYLOAD_RECONCILIATION_REQUIRED");
        final Path path = Path.of(text(result, "retainedPayloadPath"));
        require(
                path.equals(directory.resolve("manifestos-source.partial.jsonl"))
                        || path.equals(directory.resolve("manifestos-source.complete.jsonl")),
                "MANIFESTOS_RETAINED_OUTPUT_BINDING");
        final var actual = payload(path);
        require(
                actual.bytes() == result.required("payloadBytes").longValue()
                        && actual.sha256().equals(text(result, "payloadSha256")),
                "MANIFESTOS_RETAINED_PAYLOAD_CHANGED");
    }

    private static void checkPath(final Path path) {
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "MANIFESTOS_CAPTURE_LINK_REFUSED");
        }
    }

    private static void checkTime(
            final Clock clock, final Instant deadline, final long monotonicStart) {
        require(
                clock.instant().isBefore(deadline)
                        && System.nanoTime() - monotonicStart < Duration.ofSeconds(210).toNanos(),
                "MANIFESTOS_CAPTURE_DEADLINE_PARTIAL");
    }

    private static void fail(final Progress progress, final Exception failure) {
        progress.failure = failure;
        final String code = failure.getMessage();
        progress.reason =
                code != null && code.matches("[A-Z0-9_]{1,100}")
                        ? "PARTIAL_" + code
                        : "PARTIAL_CAPTURE_FAILED_RECONCILIATION_REQUIRED";
    }
}
