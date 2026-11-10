package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Finite read-only curl owner. Credentials travel only on stdin; literal bodies remain private. */
public final class CurlSnapshotWire implements ReportSnapshotExtractor.Wire {
    public static final ObjectMapper JSON =
            JsonMapper.builder()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();
    private final URI origin;
    private final String token;
    private final Path privateRoot;
    private final Path stop;
    private final Instant deadline;
    private final int maximumRequests;
    private final Map<ReportSnapshotTemplate, JsonNode> metadata =
            new EnumMap<>(ReportSnapshotTemplate.class);
    private int requests;
    private long lastCompleted;
    private DataExportRawRecordSlices literalRecords;
    private JsonNode literalPage;

    public CurlSnapshotWire(
            final Path env,
            final Path privateRoot,
            final Path stop,
            final Instant deadline,
            final int maximumRequests)
            throws Exception {
        this(credentials(env), privateRoot, stop, deadline, maximumRequests);
    }

    public CurlSnapshotWire(
            final URI apiOrigin,
            final String apiToken,
            final Path privateRoot,
            final Path stop,
            final Instant deadline,
            final int maximumRequests) {
        this(new Credentials(apiOrigin, apiToken), privateRoot, stop, deadline, maximumRequests);
    }

    private CurlSnapshotWire(
            final Credentials credentials,
            final Path privateRoot,
            final Path stop,
            final Instant deadline,
            final int maximumRequests) {
        if (!Files.isDirectory(privateRoot)
                || stop == null
                || deadline == null
                || !deadline.isAfter(Instant.now())
                || maximumRequests < 1
                || maximumRequests > 2000) {
            throw new IllegalArgumentException("SNAPSHOT_WIRE_INPUT");
        }
        ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA.info(credentials.origin());
        if (credentials.token() == null
                || credentials.token().isBlank()
                || credentials.token().chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("SNAPSHOT_CREDENTIAL_INPUT");
        }
        origin = credentials.origin();
        token = credentials.token();
        this.privateRoot = privateRoot;
        this.stop = stop;
        this.deadline = deadline;
        this.maximumRequests = maximumRequests;
    }

    private static Credentials credentials(final Path env) throws Exception {
        if (!Files.isRegularFile(env)) {
            throw new IllegalArgumentException("SNAPSHOT_CREDENTIAL_INPUT");
        }
        String base = null;
        String secret = null;
        for (final String line : Files.readAllLines(env, StandardCharsets.UTF_8)) {
            final var matcher =
                    java.util.regex.Pattern.compile(
                                    "^\\uFEFF?\\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\\s*=\\s*(.*)$")
                            .matcher(line);
            if (matcher.matches()) {
                String value = matcher.group(2).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.isBlank()) {
                    if ("API_BASE_URL".equals(matcher.group(1))) {
                        base = value;
                    } else {
                        secret = value;
                    }
                }
            }
        }
        if (base == null || secret == null || secret.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("SNAPSHOT_CREDENTIAL_INPUT");
        }
        return new Credentials(URI.create(base), secret);
    }

    private record Credentials(URI origin, String token) {
        @Override
        public String toString() {
            return "Credentials[WITHHELD]";
        }
    }

    @Override
    public DataExportRecordInput recordInput(final JsonNode row, final int index) {
        if (literalRecords == null) {
            return ReportSnapshotExtractor.Wire.super.recordInput(row, index);
        }
        if (index < 0 || index >= literalRecords.size() || literalPage.get(index) != row) {
            throw new IllegalArgumentException("SNAPSHOT_LITERAL_RECORD_BINDING");
        }
        return new DataExportRecordInput(row, java.util.Optional.of(literalRecords.record(index)));
    }

    @Override
    public JsonNode info(final ReportSnapshotTemplate template) throws Exception {
        if (!metadata.containsKey(template)) {
            metadata.put(template, get(template, template.info(origin), "info", 0));
        }
        return metadata.get(template);
    }

    @Override
    public JsonNode page(
            final ReportSnapshotTemplate template,
            final BusinessDateRange dates,
            final int page,
            final int per)
            throws Exception {
        return get(
                template,
                template.query(origin, dates, page, per),
                dates.startInclusive().toString(),
                page);
    }

    public int requests() {
        return requests;
    }

    private JsonNode get(
            final ReportSnapshotTemplate template,
            final URI target,
            final String day,
            final int page)
            throws Exception {
        if (Files.exists(stop)) {
            throw new IllegalStateException("SNAPSHOT_SHARED_STOP429");
        }
        if (requests >= maximumRequests || !Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("SNAPSHOT_HTTP_BUDGET");
        }
        final long wait = TimeUnit.SECONDS.toNanos(3) - (System.nanoTime() - lastCompleted);
        if (lastCompleted != 0 && wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
        if (!Instant.now().plusSeconds(32).isBefore(deadline)) {
            throw new IllegalStateException("SNAPSHOT_DEADLINE");
        }
        requests++;
        final String stem =
                String.format(
                        java.util.Locale.ROOT,
                        "%04d-%d-%s-%03d",
                        requests,
                        template.template().templateId(),
                        day,
                        page);
        final Path body = privateRoot.resolve(stem + ".json");
        final Path statusFile = privateRoot.resolve(stem + ".status");
        final Path error = privateRoot.resolve(stem + ".stderr");
        journal(
                JSON.createObjectNode()
                        .put("state", "RESERVED")
                        .put("request", requests)
                        .put("reportId", template.template().templateId())
                        .put("day", day)
                        .put("page", page));
        final String config =
                "url = "
                        + quote(target.toASCIIString())
                        + "\nheader = "
                        + quote("Authorization: Bearer " + token)
                        + "\nheader = \"Accept: application/json\"\n";
        final Process process =
                new ProcessBuilder(
                                "curl.exe",
                                "--disable",
                                "--config",
                                "-",
                                "--globoff",
                                "--silent",
                                "--show-error",
                                "--request",
                                "GET",
                                "--proto",
                                "=https",
                                "--max-redirs",
                                "0",
                                "--connect-timeout",
                                "10",
                                "--max-time",
                                "30",
                                "--max-filesize",
                                "10485760",
                                "--output",
                                body.toString(),
                                "--write-out",
                                "%{http_code}")
                        .redirectOutput(statusFile.toFile())
                        .redirectError(error.toFile())
                        .start();
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(config.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(32, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("SNAPSHOT_CURL_TIMEOUT");
            }
        } finally {
            lastCompleted = System.nanoTime();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        final String statusText = Files.readString(statusFile, StandardCharsets.UTF_8).trim();
        final int status = statusText.matches("[0-9]{3}") ? Integer.parseInt(statusText) : 0;
        journal(
                JSON.createObjectNode()
                        .put("state", "OBSERVED")
                        .put("request", requests)
                        .put("reportId", template.template().templateId())
                        .put("day", day)
                        .put("page", page)
                        .put("http", status)
                        .put("curlExit", process.exitValue())
                        .put("bytes", Files.exists(body) ? Files.size(body) : 0));
        if (status == 429) {
            Files.writeString(
                    stop,
                    JSON.createObjectNode()
                            .put("schema", "SOURCE_STOP429")
                            .put("reportId", template.template().templateId())
                            .put("at", Instant.now().toString())
                            .toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        if (status != 200 || process.exitValue() != 0) {
            throw new IllegalStateException(
                    "SNAPSHOT_HTTP_" + status + "_CURL_" + process.exitValue());
        }
        if (!Files.isRegularFile(body) || Files.size(body) > 10485760) {
            throw new IllegalStateException("SNAPSHOT_RESPONSE_BOUND");
        }
        final String document = Files.readString(body, StandardCharsets.UTF_8);
        final JsonNode parsed = JSON.readTree(document);
        literalRecords = null;
        literalPage = null;
        if (page > 0 && template == ReportSnapshotTemplate.LOCALIZACAO_CARGAS) {
            if (!parsed.isArray()) {
                throw new IllegalArgumentException("SNAPSHOT_LITERAL_ROOT_ARRAY");
            }
            literalRecords = DataExportRawRecordSlices.capture(document, parsed.size(), 100000);
            literalPage = parsed;
        }
        return parsed;
    }

    private void journal(final JsonNode event) throws Exception {
        Files.writeString(
                privateRoot.resolve("ledger.jsonl"),
                event.toString() + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static String quote(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
