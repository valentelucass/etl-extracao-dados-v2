package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteObservedWire52.Shape;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.Map;
import java.util.TreeMap;

/**
 * Finite literal service-date capture with independent archived row-type and current typed-mapper
 * qualifications.
 */
public final class FaturasClienteIntervalCapture {
    public interface Port {
        CurlExportTransport.Wire info() throws Exception;

        CurlExportTransport.Wire page(JsonNode request, int page, int per) throws Exception;
    }

    private static final int MAX_BYTES = 8388608;
    private static final java.net.URI SERIALIZATION_ENDPOINT =
            java.net.URI.create("https://serialization.invalid/api/analytics/reports/4924/data");
    private static final FaturaClienteDataExportMapper MAPPER = new FaturaClienteDataExportMapper();
    public static final String SHAPE_SHA = FaturasClienteObservedWire52.P2_SHA256;
    private static final byte[] PREFIX =
            "{\"entity\":\"FATURAS_POR_CLIENTE\",\"templateId\":4924,\"record\":"
                    .getBytes(StandardCharsets.UTF_8);

    private record Payload(Path path, long bytes, String sha256) {}

    private static final class Progress {
        private long rows;
        private long bytes;
        private int pages;
        private int reserved;
        private boolean terminal;
        private boolean promoted;
        private boolean payloadCreated;
        private long rowTypeRejectedOccurrences;
        private long mapperRejectedOccurrences;
        private final Map<String, Long> rowTypeRefusals = new TreeMap<>();
        private final Map<String, Long> mapperRefusals = new TreeMap<>();
        private String reason = "PARTIAL_PAGE_BUDGET_EXHAUSTED";
        private String infoNameBindingVariant = "NOT_VERIFIED";
        private Exception failure;
        private Instant startedAt;
        private final ArrayNode responses = ExportValidationSupport.JSON.createArrayNode();
    }

    private FaturasClienteIntervalCapture() {}

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
        FaturasClienteIndependentRequest.validate(request);
        final JsonNode selected = request.deepCopy();
        FaturasClienteIndependentRequest.validate(selected);
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
                "FATURAS_CLIENTE_CAPTURE_FINITE_LIMITS");
        require(output != null && output.isAbsolute(), "FATURAS_CLIENTE_CAPTURE_ABSOLUTE_OUTPUT");
        final Path directory = output.normalize();
        checkPath(directory);
        require(
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS),
                "FATURAS_CLIENTE_CAPTURE_OUTPUT_DIRECTORY");
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
        final var previousStart = journal.latest("FATURAS_CLIENTE_STARTED");
        if (previousStart != null) {
            final JsonNode binding =
                    strictJson(ExportValidationSupport.JSON.writeValueAsBytes(started));
            for (final var names = binding.fieldNames(); names.hasNext(); ) {
                final String name = names.next();
                require(
                        binding.get(name).equals(previousStart.get(name)),
                        "FATURAS_CLIENTE_CAPTURE_REOPEN_BINDING");
            }
            final JsonNode complete = journal.latest("FATURAS_CLIENTE_COMPLETE");
            final JsonNode partial = journal.latest("FATURAS_CLIENTE_PARTIAL");
            final JsonNode previous = complete == null ? partial : complete;
            require(previous != null, "FATURAS_CLIENTE_UNCLOSED_CAPTURE_RECONCILIATION_REQUIRED");
            require(
                    text(selected, "attemptId").equals(text(previous, "attemptId"))
                            && requestSha.equals(text(previous, "requestSha256")),
                    "FATURAS_CLIENTE_CAPTURE_REOPEN_RESULT_BINDING");
            verifyRetained(previous, directory);
            return (ObjectNode) previous;
        }
        final Duration remaining = Duration.between(clock.instant(), deadline);
        require(
                !remaining.isNegative()
                        && !remaining.isZero()
                        && remaining.compareTo(Duration.ofSeconds(210)) <= 0,
                "FATURAS_CLIENTE_CAPTURE_SOURCE_210_SECONDS");
        final Path partialPath = directory.resolve("faturas-cliente-source.partial.jsonl");
        final Path completePath = directory.resolve("faturas-cliente-source.complete.jsonl");
        require(
                !Files.exists(partialPath, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(completePath, LinkOption.NOFOLLOW_LINKS),
                "FATURAS_CLIENTE_FRESH_RETAINED_PAYLOAD");
        final Shape shape = shape();
        journal.record(
                "FATURAS_CLIENTE_STARTED",
                started.put("deadline", deadline.toString())
                        .put("maximumHttpRequests", 17)
                        .put("maximumResponseBytes", MAX_BYTES)
                        .put("maximumRetainedPayloadBytes", MAX_BYTES)
                        .put("shapeSha256", SHAPE_SHA));
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
                progress.infoNameBindingVariant = validateInfo(strictJson(info), shape);
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
                    final int receivedRows =
                            retainPage(
                                    bytes,
                                    file,
                                    progress,
                                    shape,
                                    selected,
                                    maximumRows,
                                    clock,
                                    deadline,
                                    monotonicStart);
                    file.force(true);
                    if (receivedRows == 0) {
                        progress.terminal = true;
                        break;
                    }
                }
            } finally {
                file.force(true);
            }
        } catch (final Exception failure) {
            fail(progress, failure);
        }
        journal.record(
                "FATURAS_CLIENTE_CAPTURE_CLOSURE",
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
                        "FATURAS_CLIENTE_HTTP_RECONCILIATION_REQUIRED");
                final var payload = payload(partialPath);
                require(payload.bytes() == progress.bytes, "FATURAS_CLIENTE_PAYLOAD_SIZE_CHANGED");
                journal.record(
                        "FAT_PROMOTION_RESERVED",
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
                        "FATURAS_CLIENTE_PROMOTED_PAYLOAD_CHANGED");
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
                        .put("schema", "FATURAS_CLIENTE_INTERVAL_CAPTURE_V1")
                        .put("sourceKind", "DATA_EXPORT")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("attemptId", text(selected, "attemptId"))
                        .put("requestSha256", requestSha)
                        .put(
                                "status",
                                complete ? "FATURAS_CLIENTE_SOURCE_COMPLETE" : progress.reason)
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
                        .put("serviceAtVerifiedForRetainedRows", true)
                        .put("serviceAtScopeEnforced", true)
                        .put("serviceAtOriginalPreserved", true)
                        .put(
                                "filterScopePolicy",
                                FaturasClienteIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("updatedAtFilter", "")
                        .put("updatedAtScopeEnforced", false)
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("sourceQueryAuthorized", false)
                        .put("historicalInfoNamesVerified", complete)
                        .put(
                                "infoNameBindingPolicy",
                                FaturasClienteObservedWire52.INFO_NAME_BINDING_POLICY)
                        .put("infoNameBindingVariant", progress.infoNameBindingVariant)
                        .put("infoDeclaredTypesQualified", false)
                        .put("typedMapperValidationPerformed", true)
                        .put("mapperPayloadUsedForRetention", false)
                        .put("rowTypesContractSha256", SHAPE_SHA)
                        .put(
                                "rowTypesQualified",
                                complete && progress.rowTypeRejectedOccurrences == 0)
                        .put("mapperQualified", complete && progress.mapperRejectedOccurrences == 0)
                        .put("rowTypeRejectedOccurrences", progress.rowTypeRejectedOccurrences)
                        .put("mapperRejectedOccurrences", progress.mapperRejectedOccurrences)
                        .put("rawRecordLexemesPreserved", true)
                        .put("corporationSequenceUsedAsGlobalIdentity", false)
                        .put("idUsedAsGlobalIdentity", false)
                        .put("sourceRootIdentityQualified", false)
                        .put("pageDistinctRootCountEnforced", false)
                        .put("pageOverlapStopsCapture", false)
                        .put("terminalPolicy", "ACTUAL_EMPTY_DATA_ARRAY_ONLY_V1")
                        .put("sourceAdmissionGrantedByCapture", false)
                        .put("SQL", 0);
        result.set("responses", progress.responses);
        result.set(
                "rowTypeRefusalCounts",
                ExportValidationSupport.JSON.valueToTree(progress.rowTypeRefusals));
        result.set(
                "mapperRefusalCounts",
                ExportValidationSupport.JSON.valueToTree(progress.mapperRefusals));
        if (progress.payloadCreated && Files.exists(retainedPath, LinkOption.NOFOLLOW_LINKS)) {
            final var retained = payload(retainedPath);
            result.put("retainedPayloadPath", retained.path().toString())
                    .put("payloadBytes", retained.bytes())
                    .put("payloadSha256", retained.sha256())
                    .put("retainedPayloadEmpty", retained.bytes() == 0)
                    .put("emptyInterval", complete && progress.terminal && progress.rows == 0);
            if (retained.bytes() > 0) {
                result.set(
                        complete ? "dataPin" : "partialDataPin",
                        ExportValidationSupport.JSON.valueToTree(
                                new ExportValidationSupport.Pin(
                                        retained.path(), retained.sha256(), retained.bytes())));
            }
        }
        final String outcome = complete ? "FATURAS_CLIENTE_COMPLETE" : "FATURAS_CLIENTE_PARTIAL";
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
        require(progress.reserved < 17, "FATURAS_CLIENTE_HTTP_17_REQUEST_BOUND");
        journal.requireRequestBudget(17);
        final var fact =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("method", "GET")
                        .put("attemptId", text(request, "attemptId"))
                        .put("page", page);
        if (page == 0) {
            fact.put("path", "/api/analytics/reports/4924/info");
        } else {
            fact.put("transport", "GET_WITH_QUERY")
                    .put("per", per)
                    .put(
                            "querySha256",
                            ExportValidationSupport.sha256(
                                    FaturasClienteIndependentRequest.query(
                                                    SERIALIZATION_ENDPOINT, request, page, per)
                                            .getRawQuery()
                                            .getBytes(StandardCharsets.US_ASCII)));
        }
        journal.record(
                page == 0 ? "FATURAS_CLIENTE_INFO_RESERVED" : "FATURAS_CLIENTE_PAGE_RESERVED",
                fact);
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
                "FATURAS_CLIENTE_COMPLETE_WIRE_REQUIRED");
        final byte[] bytes = read(wire.body());
        require(page != 0 || bytes.length <= 262144, "FATURAS_CLIENTE_INFO_BODY_256K_BOUND");
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
        journal.record(
                page == 0 ? "FATURAS_CLIENTE_INFO_OBSERVED" : "FATURAS_CLIENTE_PAGE_OBSERVED",
                fact);
        require(wire.status() == 200, "FATURAS_CLIENTE_STOP_NON_200_NO_RETRY");
        require(
                !wire.completedAt().isAfter(deadline)
                        && !wire.completedAt().isAfter(clock.instant())
                        && !wire.completedAt().isBefore(progress.startedAt),
                "FATURAS_CLIENTE_WIRE_COMPLETION_WINDOW");
        checkTime(clock, deadline, monotonicStart);
        return bytes;
    }

    private static int retainPage(
            final byte[] bytes,
            final FileChannel file,
            final Progress progress,
            final Shape shape,
            final JsonNode request,
            final long maximumRows,
            final Clock clock,
            final Instant deadline,
            final long monotonicStart)
            throws Exception {
        int ordinal = 0;
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            require(
                    parser.nextToken() == JsonToken.START_ARRAY,
                    "FATURAS_CLIENTE_QUALIFIED_ROOT_ARRAY_PAGE");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                checkTime(clock, deadline, monotonicStart);
                require(
                        parser.currentToken() == JsonToken.START_OBJECT,
                        "FATURAS_CLIENTE_ROW_OBJECT_REQUIRED");
                final int start = Math.toIntExact(parser.currentTokenLocation().getByteOffset());
                final JsonNode row = ExportValidationSupport.JSON.readTree(parser);
                final int end = Math.toIntExact(parser.currentLocation().getByteOffset());
                final boolean rowTypesQualified = validateRow(row, shape, progress);
                FaturasClienteIndependentRequest.verifyServiceAt(row, request);

                if (!rowTypesQualified) {
                    progress.rowTypeRejectedOccurrences++;
                }
                String mapperReason = null;
                final int mapperOrdinal = (ordinal++ % 100) + 1;
                require(
                        start >= 0
                                && end > start
                                && end <= bytes.length
                                && bytes[start] == '{'
                                && bytes[end - 1] == '}',
                        "FATURAS_CLIENTE_ORIGINAL_RECORD_BYTE_BOUNDARY");
                final byte[] original = compactRecord(bytes, start, end);
                final var mapperEnvelope = ExportValidationSupport.JSON.createObjectNode();
                mapperEnvelope.set("data", row);
                try {
                    final var mapped = MAPPER.map(mapperOrdinal, mapperEnvelope);
                    if (!mapped.valid()) {
                        mapperReason = "FATURAS_CLIENTE_TYPED_OBSERVATION_INVALID";
                    }
                } catch (final IllegalArgumentException rejected) {
                    mapperReason = "FATURAS_CLIENTE_TYPED_OBSERVATION_REFUSED";
                }
                if (mapperReason != null) {
                    progress.mapperRejectedOccurrences++;
                    progress.mapperRefusals.merge(mapperReason, 1L, Long::sum);
                }
                require(progress.rows < maximumRows, "FATURAS_CLIENTE_ROWS_LIMIT_PARTIAL");

                final int size = PREFIX.length + original.length + 2;
                require(
                        progress.bytes + size <= MAX_BYTES,
                        "FATURAS_CLIENTE_RETAINED_PAYLOAD_8MIB_BOUND");
                write(file, PREFIX);
                write(file, original);
                write(file, new byte[] {'}', '\n'});
                progress.rows++;
                progress.bytes += size;
            }
            require(parser.nextToken() == null, "FATURAS_CLIENTE_RESPONSE_TRAILING_JSON");
        }
        return ordinal;
    }

    private static boolean validateRow(
            final JsonNode row, final Shape shape, final Progress progress) {
        return FaturasClienteObservedWire52.qualified(row, shape, progress.rowTypeRefusals);
    }

    private static Shape shape() throws IOException {
        return FaturasClienteObservedWire52.observed20261108P2();
    }

    private static String validateInfo(final JsonNode info, final Shape shape) {
        return FaturasClienteObservedWire52.validateInfoNames(info, shape);
    }

    private static JsonNode strictJson(final byte[] bytes) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && parser.nextToken() == null,
                    "FATURAS_CLIENTE_RESPONSE_TRAILING_JSON");
            return value;
        }
    }

    private static void write(final FileChannel file, final byte[] bytes) throws IOException {
        final var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            require(file.write(buffer) > 0, "FATURAS_CLIENTE_PAYLOAD_WRITE_MADE_NO_PROGRESS");
        }
    }

    /**
     * JSONL removes only whitespace outside strings; numeric lexemes and escaped/raw UTF8 stay
     * intact.
     */
    static byte[] compactRecord(final byte[] bytes, final int start, final int end) {
        final var out = new ByteArrayOutputStream(Math.min(end - start, 262144));
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < end; index++) {
            final byte value = bytes[index];
            if (!quoted && (value == ' ' || value == '\t' || value == '\r' || value == '\n')) {
                continue;
            }
            require(
                    out.size() + PREFIX.length + 2 < MAX_BYTES,
                    "FATURAS_CLIENTE_RECORD_WITHIN_RETAINED_8MIB_BOUND");
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
        require(!quoted && !escaped, "FATURAS_CLIENTE_ORIGINAL_RECORD_STRING_BOUNDARY");
        return out.toByteArray();
    }

    private static byte[] read(final ExportValidationSupport.Pin pin) throws IOException {
        return pin.read(MAX_BYTES, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
    }

    private static void verifyResponses(final JsonNode responses) throws IOException {
        require(
                responses.isArray() && responses.size() <= 17,
                "FATURAS_CLIENTE_RESPONSE_COUNT_BOUND");
        for (final var response : responses) {
            read(ExportValidationSupport.Pin.from(response.required("rawBodyPin")));
        }
    }

    private static Payload payload(final Path path) throws IOException {
        checkPath(path);
        require(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                "FATURAS_CLIENTE_RETAINED_REGULAR_FILE");
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        require(bytes.length <= MAX_BYTES, "FATURAS_CLIENTE_RETAINED_PAYLOAD_8MIB_BOUND");
        return new Payload(path, bytes.length, ExportValidationSupport.sha256(bytes));
    }

    private static void verifyRetained(final JsonNode result, final Path directory)
            throws IOException {
        verifyResponses(result.required("responses"));
        require(
                result.has("retainedPayloadPath"),
                "FATURAS_CLIENTE_RETAINED_PAYLOAD_RECONCILIATION_REQUIRED");
        final Path path = Path.of(text(result, "retainedPayloadPath"));
        require(
                path.equals(directory.resolve("faturas-cliente-source.partial.jsonl"))
                        || path.equals(directory.resolve("faturas-cliente-source.complete.jsonl")),
                "FATURAS_CLIENTE_RETAINED_OUTPUT_BINDING");
        final var actual = payload(path);
        require(
                actual.bytes() == result.required("payloadBytes").longValue()
                        && actual.sha256().equals(text(result, "payloadSha256")),
                "FATURAS_CLIENTE_RETAINED_PAYLOAD_CHANGED");
    }

    private static void checkPath(final Path path) {
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "FATURAS_CLIENTE_CAPTURE_LINK_REFUSED");
        }
    }

    private static void checkTime(
            final Clock clock, final Instant deadline, final long monotonicStart) {
        require(
                clock.instant().isBefore(deadline)
                        && System.nanoTime() - monotonicStart < Duration.ofSeconds(210).toNanos(),
                "FATURAS_CLIENTE_CAPTURE_DEADLINE_PARTIAL");
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
