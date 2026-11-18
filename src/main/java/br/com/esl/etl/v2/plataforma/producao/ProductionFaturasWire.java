package br.com.esl.etl.v2.plataforma.producao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.JSON;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.sha256;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.CurlExportTransport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.Pin;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteIntervalCapture;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.PersistentExportJournal;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Production-only bounded read channel; does not change the frozen Root/Shadow worker. */
final class ProductionFaturasWire implements FaturasClienteIntervalCapture.Port {
    private final JsonNode config;
    private final Path directory;
    private final PersistentExportJournal journal;
    private final URI origin;
    private int count;
    private long previousStarted;

    ProductionFaturasWire(
            final JsonNode config, final Path directory, final PersistentExportJournal journal) {
        this.config = config;
        this.directory = directory;
        this.journal = journal;
        origin = URI.create(config.required("apiOrigin").asText());
        require(
                "https".equals(origin.getScheme())
                        && origin.getHost() != null
                        && (origin.getPort() == -1 || origin.getPort() == 443)
                        && origin.getUserInfo() == null
                        && origin.getQuery() == null
                        && origin.getFragment() == null
                        && (origin.getPath().isEmpty() || "/".equals(origin.getPath())),
                "PRODUCTION_HTTPS_API_ORIGIN_REQUIRED");
        require(
                config.required("apiToken").asText().matches("[^\\x00-\\x20\\x7f]{1,4096}"),
                "PRODUCTION_TOKEN_FORMAT");
    }

    int calls() {
        return count;
    }

    @Override
    public CurlExportTransport.Wire info() throws Exception {
        return exchange(origin.resolve("/api/analytics/reports/4924/info"), 262144);
    }

    @Override
    public CurlExportTransport.Wire page(final JsonNode request, final int page, final int per)
            throws Exception {
        return exchange(
                FaturasClienteIndependentRequest.query(
                        origin.resolve("/api/analytics/reports/4924/data"), request, page, per),
                8388608);
    }

    private CurlExportTransport.Wire exchange(final URI target, final int maximumBytes)
            throws Exception {
        final Path hold = Path.of(config.required("stopPath").asText());
        require(!Files.exists(hold), "PRODUCTION_SOURCE_STOP_REQUIRES_REVIEW");
        require(++count <= 17, "PRODUCTION_HTTP_BUDGET_EXHAUSTED");
        if (previousStarted != 0) {
            final long remaining =
                    TimeUnit.SECONDS.toNanos(3) - (System.nanoTime() - previousStarted);
            if (remaining > 0) {
                TimeUnit.NANOSECONDS.sleep(remaining);
            }
        }
        final Path body = directory.resolve("response-" + count + ".private.json");
        final Path error = directory.resolve("response-" + count + ".private.stderr");
        require(!Files.exists(body) && !Files.exists(error), "PRODUCTION_FRESH_WIRE_PATH_REQUIRED");
        final String instructions =
                "silent\nshow-error\nrequest = \"GET\"\n"
                        + "proto = \"=https\"\nmax-redirs = 0\nretry = 0\nconnect-timeout = 10\nmax-time = 30\n"
                        + "max-filesize = "
                        + maximumBytes
                        + "\nurl = "
                        + quote(target.toASCIIString())
                        + "\nheader = "
                        + quote("Authorization: Bearer " + config.required("apiToken").asText())
                        + "\noutput = "
                        + quote(body.toString())
                        + "\nwrite-out = \"%{http_code}\"\n";
        journal.record(
                "HTTP_ADMITTED",
                JSON.createObjectNode()
                        .put("ordinal", count)
                        .put("maximumBodyBytes", maximumBytes)
                        .put("startedAt", Instant.now().toString()));
        final Process process =
                new ProcessBuilder(config.required("curlPath").asText(), "--config", "-")
                        .redirectError(error.toFile())
                        .start();
        previousStarted = System.nanoTime();
        try {
            try (var input = process.getOutputStream()) {
                input.write(instructions.getBytes(StandardCharsets.UTF_8));
            }
            require(process.waitFor(35, TimeUnit.SECONDS), "PRODUCTION_OWNED_CURL_TIMEOUT");
            final byte[] statusBytes = process.getInputStream().readNBytes(33);
            require(statusBytes.length <= 32, "PRODUCTION_STATUS_CHANNEL_BOUND");
            final String statusText = new String(statusBytes, StandardCharsets.US_ASCII).trim();
            require(statusText.matches("[0-9]{3}"), "PRODUCTION_HTTP_STATUS_REQUIRED");
            final int status = Integer.parseInt(statusText);
            if (status == 429) {
                Files.writeString(
                        hold,
                        "HTTP429 " + Instant.now(),
                        java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            require(
                    process.exitValue() == 0
                            && Files.exists(body)
                            && Files.size(body) <= maximumBytes,
                    "PRODUCTION_CURL_OR_BODY_BOUND_FAILED");
            final var pin = new Pin(body, sha256(Files.readAllBytes(body)), Files.size(body));
            journal.record(
                    "HTTP_COMPLETE",
                    JSON.createObjectNode()
                            .put("ordinal", count)
                            .put("status", status)
                            .put("completedAt", Instant.now().toString()));
            return new CurlExportTransport.Wire(status, pin, Instant.now());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                require(
                        process.waitFor(5, TimeUnit.SECONDS),
                        "PRODUCTION_OWNED_CURL_CLOSE_UNCERTAIN");
            }
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    private static String quote(final String value) {
        require(
                !value.contains("\n") && !value.contains("\r") && !value.contains("\0"),
                "PRODUCTION_CURL_CONFIG_LINE_REQUIRED");
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
