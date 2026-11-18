package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * INFO10633 and literal raw pages with typed mapper validation; no retry, deduplication or source
 * admission.
 */
public final class InventarioIntervalCapture {
    public interface Port {
        CurlExportTransport.Wire info() throws Exception;

        CurlExportTransport.Wire page(JsonNode request, int page, int per) throws Exception;
    }

    private static final int MAX_BYTES = 8388608;
    private static final InventarioDataExportMapper MAPPER = new InventarioDataExportMapper();
    public static final String SHAPE_SHA =
            "03e0e446ea19bb2d70b33056d05a36fef96981f710dfa216e1a8e41e1f3e5794";
    private static final byte[] PREFIX =
            "{\"entity\":\"INVENTARIO\",\"templateId\":10633,\"record\":"
                    .getBytes(StandardCharsets.UTF_8);
    private static final URI SERIALIZATION_ENDPOINT =
            URI.create("https://serialization.invalid/api/analytics/reports/10633/data");

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
        private long rowTypeRejectedOccurrences;
        private long mapperRejectedOccurrences;
        private final Map<String, Long> rowTypeRefusals = new TreeMap<>();
        private final Map<String, Long> mapperRefusals = new TreeMap<>();
        private String reason = "PARTIAL_PAGE_BUDGET_EXHAUSTED";
        private Exception failure;
        private Instant startedAt;
        private final Set<BigInteger> priorIds = new HashSet<>();
        private final ArrayNode responses = ExportValidationSupport.JSON.createArrayNode();
    }

    private InventarioIntervalCapture() {}

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
        InventarioIndependentRequest.validate(request);
        final JsonNode selected = request.deepCopy();
        InventarioIndependentRequest.validate(selected);
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
                "INVENTARIO_CAPTURE_FINITE_LIMITS");
        require(output != null && output.isAbsolute(), "INVENTARIO_CAPTURE_ABSOLUTE_OUTPUT");
        final Path directory = output.normalize();
        checkPath(directory);
        require(
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS),
                "INVENTARIO_CAPTURE_OUTPUT_DIRECTORY");
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
        final var previousStart = journal.latest("INVENTARIO_STARTED");
        if (previousStart != null) {
            final JsonNode binding =
                    strictJson(ExportValidationSupport.JSON.writeValueAsBytes(started));
            for (final var names = binding.fieldNames(); names.hasNext(); ) {
                final String name = names.next();
                require(
                        binding.get(name).equals(previousStart.get(name)),
                        "INVENTARIO_CAPTURE_REOPEN_BINDING");
            }
            final JsonNode complete = journal.latest("INVENTARIO_COMPLETE");
            final JsonNode partial = journal.latest("INVENTARIO_PARTIAL");
            final JsonNode previous = complete == null ? partial : complete;
            require(previous != null, "INVENTARIO_UNCLOSED_CAPTURE_RECONCILIATION_REQUIRED");
            require(
                    text(selected, "attemptId").equals(text(previous, "attemptId"))
                            && requestSha.equals(text(previous, "requestSha256")),
                    "INVENTARIO_CAPTURE_REOPEN_RESULT_BINDING");
            verifyRetained(previous, directory);
            return (ObjectNode) previous;
        }
        final Duration remaining = Duration.between(clock.instant(), deadline);
        require(
                !remaining.isNegative()
                        && !remaining.isZero()
                        && remaining.compareTo(Duration.ofSeconds(210)) <= 0,
                "INVENTARIO_CAPTURE_SOURCE_210_SECONDS");
        final Path partialPath = directory.resolve("inventario-source.partial.jsonl");
        final Path completePath = directory.resolve("inventario-source.complete.jsonl");
        require(
                !Files.exists(partialPath, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(completePath, LinkOption.NOFOLLOW_LINKS),
                "INVENTARIO_FRESH_RETAINED_PAYLOAD");
        final Shape shape = shape();
        journal.record(
                "INVENTARIO_STARTED",
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
                                    selected,
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
                                "INVENTARIO_PAGE_OVERLAP",
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
                "INVENTARIO_CAPTURE_CLOSURE",
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
                        "INVENTARIO_HTTP_RECONCILIATION_REQUIRED");
                final var payload = payload(partialPath);
                require(payload.bytes() == progress.bytes, "INVENTARIO_PAYLOAD_SIZE_CHANGED");
                journal.record(
                        "INVENTARIO_PROMOTION_RESERVED",
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
                        "INVENTARIO_PROMOTED_PAYLOAD_CHANGED");
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
                        .put("schema", "INVENTARIO_INTERVAL_CAPTURE_V1")
                        .put("sourceKind", "DATA_EXPORT")
                        .put("profile", InventarioIndependentRequest.PROFILE)
                        .put("attemptId", text(selected, "attemptId"))
                        .put("requestSha256", requestSha)
                        .put("status", complete ? "INVENTARIO_SOURCE_COMPLETE" : progress.reason)
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
                        .put("startedAtDateVerifiedForRetainedRows", true)
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
                        .put("sequenceCodeUsedAsPageOverlapCandidate", true)
                        .put("sourceRootIdentityQualified", false)
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
        final String outcome = complete ? "INVENTARIO_COMPLETE" : "INVENTARIO_PARTIAL";
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
        require(progress.reserved < 17, "INVENTARIO_HTTP_17_REQUEST_BOUND");
        journal.requireRequestBudget(17);
        final var fact =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("method", "GET")
                        .put("attemptId", text(request, "attemptId"))
                        .put("page", page);
        if (page == 0) {
            fact.put("path", "/api/analytics/reports/10633/info");
        } else {
            fact.put("transport", "GET_WITH_QUERY")
                    .put("per", per)
                    .put(
                            "querySha256",
                            ExportValidationSupport.sha256(
                                    InventarioIndependentRequest.query(
                                                    SERIALIZATION_ENDPOINT, request, page, per)
                                            .getRawQuery()
                                            .getBytes(StandardCharsets.US_ASCII)));
        }
        journal.record(page == 0 ? "INVENTARIO_INFO_RESERVED" : "INVENTARIO_PAGE_RESERVED", fact);
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
                "INVENTARIO_COMPLETE_WIRE_REQUIRED");
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
        journal.record(page == 0 ? "INVENTARIO_INFO_OBSERVED" : "INVENTARIO_PAGE_OBSERVED", fact);
        require(wire.status() == 200, "INVENTARIO_STOP_NON_200_NO_RETRY");
        require(
                !wire.completedAt().isAfter(deadline)
                        && !wire.completedAt().isAfter(clock.instant())
                        && !wire.completedAt().isBefore(progress.startedAt),
                "INVENTARIO_WIRE_COMPLETION_WINDOW");
        checkTime(clock, deadline, monotonicStart);
        return bytes;
    }

    private static Set<BigInteger> retainPage(
            final byte[] bytes,
            final FileChannel file,
            final Progress progress,
            final Shape shape,
            final JsonNode request,
            final long maximumRows,
            final int per,
            final Clock clock,
            final Instant deadline,
            final long monotonicStart)
            throws Exception {
        final var ids = new HashSet<BigInteger>();
        int ordinal = 0;
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            require(
                    parser.nextToken() == JsonToken.START_ARRAY,
                    "INVENTARIO_QUALIFIED_ROOT_ARRAY_PAGE");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                checkTime(clock, deadline, monotonicStart);
                require(
                        parser.currentToken() == JsonToken.START_OBJECT,
                        "INVENTARIO_ROW_OBJECT_REQUIRED");
                final int start = Math.toIntExact(parser.currentTokenLocation().getByteOffset());
                final JsonNode row = ExportValidationSupport.JSON.readTree(parser);
                final int end = Math.toIntExact(parser.currentLocation().getByteOffset());
                final boolean rowTypesQualified = validateRow(row, shape, progress);
                final var key = row.required("sequence_code");
                require(
                        key.isIntegralNumber() && key.canConvertToLong() && key.longValue() > 0,
                        "INVENTARIO_SEQUENCE_CODE_POSITIVE_LONG_WIRE_REQUIRED");
                final LocalDate requestedDate =
                        startedAt(row.required("started_at"))
                                .atZone(ZoneId.of("America/Sao_Paulo"))
                                .toLocalDate();
                require(
                        !requestedDate.isBefore(LocalDate.parse(text(request, "periodStart")))
                                && !requestedDate.isAfter(
                                        LocalDate.parse(text(request, "periodEnd"))),
                        "INVENTARIO_STARTED_AT_OUTSIDE_SCOPE");
                if (!rowTypesQualified) {
                    progress.rowTypeRejectedOccurrences++;
                }
                String mapperReason = null;
                final int mapperOrdinal = (ordinal++ % 100) + 1;
                final var mapperEnvelope = ExportValidationSupport.JSON.createObjectNode();
                mapperEnvelope.set("data", row);
                try {
                    final var mapped = MAPPER.map(mapperOrdinal, mapperEnvelope);
                    if (!mapped.valid()) {
                        mapperReason = "INVENTARIO_TYPED_OBSERVATION_INVALID";
                    }
                } catch (final IllegalArgumentException rejected) {
                    mapperReason = "INVENTARIO_TYPED_OBSERVATION_REFUSED";
                }
                if (mapperReason != null) {
                    progress.mapperRejectedOccurrences++;
                    progress.mapperRefusals.merge(mapperReason, 1L, Long::sum);
                }
                ids.add(key.bigIntegerValue());
                require(ids.size() <= per, "INVENTARIO_PAGE_DISTINCT_SEQUENCE_LIMIT");
                require(progress.rows < maximumRows, "INVENTARIO_ROWS_LIMIT_PARTIAL");
                require(
                        start >= 0
                                && end > start
                                && end <= bytes.length
                                && bytes[start] == '{'
                                && bytes[end - 1] == '}',
                        "INVENTARIO_ORIGINAL_RECORD_BYTE_BOUNDARY");
                final byte[] original = compactRecord(bytes, start, end);
                final int size = PREFIX.length + original.length + 2;
                require(
                        progress.bytes + size <= MAX_BYTES,
                        "INVENTARIO_RETAINED_PAYLOAD_8MIB_BOUND");
                write(file, PREFIX);
                write(file, original);
                write(file, new byte[] {'}', '\n'});
                progress.rows++;
                progress.bytes += size;
            }
            require(parser.nextToken() == null, "INVENTARIO_RESPONSE_TRAILING_JSON");
        }
        return ids;
    }

    private static boolean validateRow(
            final JsonNode row, final Shape shape, final Progress progress) {
        require(
                row != null && row.isObject() && row.size() == 26,
                "INVENTARIO_EXACT_26_ROW_FIELDS");
        final var actual = new HashSet<String>();
        row.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(shape.fields()), "INVENTARIO_UNKNOWN_OR_MISSING_FIELD");
        boolean qualified = true;
        for (final var entries = row.fields(); entries.hasNext(); ) {
            final var entry = entries.next();
            final String path = "/" + entry.getKey();
            if (!qualifiedValue(entry.getValue(), shape.paths().get(path))) {
                qualified = false;
                progress.rowTypeRefusals.merge(
                        path + (entry.getValue().isNull() ? ":NULL" : ":TYPE"), 1L, Long::sum);
            }
        }
        return qualified;
    }

    private static boolean qualifiedValue(final JsonNode value, final JsonNode rule) {
        if (value == null || rule == null || !rule.isObject()) {
            return false;
        }
        final String type =
                value.isNull()
                        ? "NULL"
                        : value.isIntegralNumber()
                                ? "INTEGER"
                                : value.isNumber()
                                        ? "NUMBER"
                                        : value.isTextual()
                                                ? "STRING"
                                                : value.isArray()
                                                        ? "ARRAY"
                                                        : value.isObject() ? "OBJECT" : "BOOLEAN";
        boolean accepted = false;
        for (final var allowed : rule.path("wireTypes")) {
            accepted |= type.equals(allowed.asText());
        }
        if (!accepted) {
            return false;
        }
        if (value.isArray()) {
            for (final var item : value) {
                final String itemType =
                        item.isNull()
                                ? "NULL"
                                : item.isIntegralNumber()
                                        ? "INTEGER"
                                        : item.isNumber()
                                                ? "NUMBER"
                                                : item.isTextual()
                                                        ? "STRING"
                                                        : item.isArray()
                                                                ? "ARRAY"
                                                                : item.isObject()
                                                                        ? "OBJECT"
                                                                        : "BOOLEAN";
                boolean itemAccepted = false;
                for (final var allowed : rule.path("arrayItemTypes")) {
                    itemAccepted |= itemType.equals(allowed.asText());
                }
                if (!itemAccepted) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Instant startedAt(final JsonNode value) {
        require(value.isTextual(), "INVENTARIO_STARTED_AT_TEXT_REQUIRED");
        final String text = value.textValue().trim();
        try {
            return Instant.parse(text);
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (final DateTimeParseException ignored) {
        }
        final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        for (final DateTimeFormatter format :
                java.util.List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                .withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return format == DateTimeFormatter.ISO_LOCAL_DATE
                        ? LocalDate.parse(text, format).atStartOfDay(zone).toInstant()
                        : LocalDateTime.parse(text, format).atZone(zone).toInstant();
            } catch (final DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("INVENTARIO_STARTED_AT_INVALID");
    }

    private static Shape shape() throws IOException {
        try (var input =
                InventarioIntervalCapture.class.getResourceAsStream(
                        "/contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json")) {
            require(input != null, "INVENTARIO_CAPTURE_SHAPE_REQUIRED");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && SHAPE_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "INVENTARIO_CAPTURE_SHAPE_PIN");
            final var contract = strictJson(bytes);
            require(
                    contract.path("templateId").intValue() == 10633
                            && "$".equals(contract.path("recordRoot").asText())
                            && "ARRAY".equals(contract.path("rootCardinality").asText()),
                    "INVENTARIO_CAPTURE_SHAPE_SCOPE");
            final Map<String, JsonNode> paths = new HashMap<>();
            final Set<String> fields = new HashSet<>();
            final Set<String> filters = new HashSet<>();
            for (final var field : contract.required("fields")) {
                final String name = text(field, "path");
                require(
                        !name.contains("/")
                                && !name.contains(".")
                                && paths.putIfAbsent("/" + name, field) == null,
                        "INVENTARIO_CAPTURE_UNIQUE_SHAPE");
                require(
                        field.path("required").isBoolean() && field.path("required").booleanValue(),
                        "INVENTARIO_CAPTURE_REQUIRED_26");
                fields.add(name);
            }
            contract.required("metadata")
                    .forEach(
                            field -> {
                                if ("DATA_FILTER".equals(field.path("kind").asText())) {
                                    filters.add(text(field, "path"));
                                }
                            });
            require(
                    fields.size() == 26 && paths.size() == 26 && filters.size() == 7,
                    "INVENTARIO_CAPTURE_26_7_SHAPE");
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
                "INVENTARIO_INFO_EXACT_26_FIELDS_7_FILTERS");
    }

    private static JsonNode strictJson(final byte[] bytes) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && parser.nextToken() == null,
                    "INVENTARIO_RESPONSE_TRAILING_JSON");
            return value;
        }
    }

    private static void write(final FileChannel file, final byte[] bytes) throws IOException {
        final var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            require(file.write(buffer) > 0, "INVENTARIO_PAYLOAD_WRITE_MADE_NO_PROGRESS");
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
            require(out.size() + PREFIX.length + 2 < 262144, "INVENTARIO_PAYLOAD_LINE_BOUND");
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
        require(!quoted && !escaped, "INVENTARIO_ORIGINAL_RECORD_STRING_BOUNDARY");
        return out.toByteArray();
    }

    private static byte[] read(final ExportValidationSupport.Pin pin) throws IOException {
        return pin.read(MAX_BYTES, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
    }

    private static void verifyResponses(final JsonNode responses) throws IOException {
        require(responses.isArray() && responses.size() <= 17, "INVENTARIO_RESPONSE_COUNT_BOUND");
        for (final var response : responses) {
            read(ExportValidationSupport.Pin.from(response.required("rawBodyPin")));
        }
    }

    private static Payload payload(final Path path) throws IOException {
        checkPath(path);
        require(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                "INVENTARIO_RETAINED_REGULAR_FILE");
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        require(bytes.length <= MAX_BYTES, "INVENTARIO_RETAINED_PAYLOAD_8MIB_BOUND");
        return new Payload(path, bytes.length, ExportValidationSupport.sha256(bytes));
    }

    private static void verifyRetained(final JsonNode result, final Path directory)
            throws IOException {
        verifyResponses(result.required("responses"));
        require(
                result.has("retainedPayloadPath"),
                "INVENTARIO_RETAINED_PAYLOAD_RECONCILIATION_REQUIRED");
        final Path path = Path.of(text(result, "retainedPayloadPath"));
        require(
                path.equals(directory.resolve("inventario-source.partial.jsonl"))
                        || path.equals(directory.resolve("inventario-source.complete.jsonl")),
                "INVENTARIO_RETAINED_OUTPUT_BINDING");
        final var actual = payload(path);
        require(
                actual.bytes() == result.required("payloadBytes").longValue()
                        && actual.sha256().equals(text(result, "payloadSha256")),
                "INVENTARIO_RETAINED_PAYLOAD_CHANGED");
    }

    private static void checkPath(final Path path) {
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "INVENTARIO_CAPTURE_LINK_REFUSED");
        }
    }

    private static void checkTime(
            final Clock clock, final Instant deadline, final long monotonicStart) {
        require(
                clock.instant().isBefore(deadline)
                        && System.nanoTime() - monotonicStart < Duration.ofSeconds(210).toNanos(),
                "INVENTARIO_CAPTURE_DEADLINE_PARTIAL");
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
