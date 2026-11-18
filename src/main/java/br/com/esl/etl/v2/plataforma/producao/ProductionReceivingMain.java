package br.com.esl.etl.v2.plataforma.producao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.JSON;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.sha256;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteIntervalCapture;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.PersistentExportJournal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit production receiving qualification. No dashboard publication or Shadow authority. */
public final class ProductionReceivingMain {
    private ProductionReceivingMain() {}

    static List<LocalDate> days(final LocalDate start, final LocalDate end) {
        require(
                start != null
                        && end != null
                        && !end.isBefore(start)
                        && !end.isAfter(start.plusDays(30)),
                "PRODUCTION_BOUNDED_DATE_RANGE");
        return start.datesUntil(end.plusDays(1)).toList();
    }

    public static ObjectNode request(final LocalDate day, final UUID run) {
        final var result =
                JSON.createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_INDEPENDENT_REQUEST_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("entity", "FATURAS_POR_CLIENTE")
                        .put("reportId", 4924)
                        .put("attemptId", run.toString())
                        .put("periodStart", day.toString())
                        .put("periodEnd", day.toString())
                        .put("zone", "America/Sao_Paulo");
        final var filters = result.putObject("filters");
        for (final String name : FaturasClienteIndependentRequest.FILTERS) {
            filters.put(
                    name,
                    FaturasClienteIndependentRequest.SERVICE_AT_FILTER.equals(name)
                            ? day + " - " + day
                            : "");
        }
        FaturasClienteIndependentRequest.validate(result);
        return result;
    }

    public static void main(final String[] args) throws Exception {
        // Secrets arrive only on this process's private stdin, never arguments/environment/logs.
        final byte[] input = System.in.readNBytes(65537);
        require(input.length <= 65536 && System.in.read() == -1, "PRODUCTION_INPUT_BOUND");
        final JsonNode configuration = JSON.readTree(input);
        java.util.Arrays.fill(input, (byte) 0);
        final Path reportPath = Path.of(configuration.required("reportPath").asText());
        final var report =
                JSON.createObjectNode()
                        .put("schema", "PRODUCTION_RECEIVING_0759_V1")
                        .put("startedAt", Instant.now().toString())
                        .put("passed", false)
                        .put("dashboardPublication", false)
                        .put("dashboardRouteChanged", false);
        final var results = report.putArray("days");
        try {
            execute(configuration, results, report);
            report.put("passed", true);
        } catch (final Exception failure) {
            final String code = failure.getMessage();
            report.put(
                    "failureCode",
                    code != null && code.matches("[A-Z][A-Z0-9_]{0,95}")
                            ? code
                            : "PRODUCTION_FAILURE_PRIVATE_DETAILS_WITHHELD");
            report.put("failureType", failure.getClass().getSimpleName());
            if (failure instanceof java.sql.SQLException sql) {
                report.put("sqlErrorCode", sql.getErrorCode()).put("sqlState", sql.getSQLState());
            }
        } finally {
            report.put("completedAt", Instant.now().toString());
            Files.writeString(
                    reportPath, JSON.writeValueAsString(report), StandardOpenOption.CREATE_NEW);
        }
        System.out.println(
                report.path("passed").asBoolean()
                        ? "PRODUCTION_RECEIVING_PASSED"
                        : "PRODUCTION_RECEIVING_FAILED");
        if (!report.path("passed").asBoolean()) {
            System.exit(1);
        }
    }

    private static void execute(
            final JsonNode config,
            final com.fasterxml.jackson.databind.node.ArrayNode results,
            final ObjectNode report)
            throws Exception {
        require(
                "ETL_SISTEMA_V2".equals(config.required("database").asText()),
                "PRODUCTION_EXACT_DATABASE_REQUIRED");
        final Path root =
                Path.of(config.required("privateRoot").asText()).toAbsolutePath().normalize();
        require(
                Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS),
                "PRODUCTION_PRIVATE_ROOT_REQUIRED");
        for (Path at = root; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "PRODUCTION_LINK_REFUSED");
        }
        final String scope =
                sha256(
                        (config.required("apiOrigin").asText()
                                        + "\n"
                                        + sha256(
                                                config.required("apiToken")
                                                        .asText()
                                                        .getBytes(StandardCharsets.UTF_8)))
                                .getBytes(StandardCharsets.UTF_8));
        final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        final boolean rolling = "ROLLING".equals(config.required("mode").asText());
        final LocalDate start =
                rolling
                        ? today.minusDays(7)
                        : LocalDate.parse(config.required("periodStart").asText());
        final LocalDate end =
                rolling ? today : LocalDate.parse(config.required("periodEnd").asText());
        require(!end.isAfter(today), "PRODUCTION_FUTURE_DATE_REFUSED");
        final var selected = new ArrayList<>(days(start, end));
        report.put("phase", "CONNECT_TARGET_AND_ACQUIRE_WRITER_LOCK");
        try (var store = new ProductionIngestionStore(config)) {
            if (rolling) {
                // Durable coverage is read on every invocation. Recover older holes first.
                final var anchor = LocalDate.parse(config.required("recoveryAnchor").asText());
                final var missing = new ArrayList<LocalDate>();
                final int[] missingCount = {0};
                store.missing(
                        scope,
                        anchor,
                        start.minusDays(1),
                        day -> {
                            missingCount[0]++;
                            if (missing.size() < 8) {
                                missing.add(day);
                            }
                        });
                report.put("olderMissingDatesAtStart", missingCount[0]);
                report.put("rollingDeferredForRecovery", missingCount[0] > 4);
                if (missingCount[0] > 4) {
                    selected.clear();
                }
                selected.addAll(0, missing);
            }
            for (final LocalDate day : selected) {
                final UUID run = UUID.randomUUID();
                final ObjectNode request = request(day, run);
                final byte[] requestBytes = JSON.writeValueAsBytes(request);
                final String requestSha = sha256(requestBytes);
                final Path directory = Files.createDirectory(root.resolve(run.toString()));
                report.put("phase", "REGISTER_PARTITION_BEFORE_SOURCE");
                store.start(run, scope, day, requestSha);
                final var item =
                        results.addObject()
                                .put("day", day.toString())
                                .put("runId", run.toString())
                                .put("stored", false);
                try (var journal =
                        PersistentExportJournal.create(
                                directory.resolve("journal"),
                                requestSha,
                                config.required("codeSha256").asText(),
                                run)) {
                    Files.write(
                            directory.resolve("request.json"),
                            requestBytes,
                            StandardOpenOption.CREATE_NEW);
                    report.put("phase", "CAPTURE_SOURCE");
                    final var wire = new ProductionFaturasWire(config, directory, journal);
                    final var capture =
                            FaturasClienteIntervalCapture.capture(
                                    request,
                                    wire,
                                    journal,
                                    directory,
                                    16,
                                    100,
                                    12000,
                                    Instant.now().plusSeconds(210));
                    Files.writeString(
                            directory.resolve("capture.json"),
                            JSON.writeValueAsString(capture),
                            StandardOpenOption.CREATE_NEW);
                    item.put("httpRequests", wire.calls())
                            .put("rows", capture.path("rows").asInt())
                            .put("terminal", capture.path("terminalEmptyPageObserved").asBoolean())
                            .put("rowTypesQualified", capture.path("rowTypesQualified").asBoolean())
                            .put("mapperQualified", capture.path("mapperQualified").asBoolean());
                    require(
                            "FATURAS_CLIENTE_SOURCE_COMPLETE"
                                            .equals(capture.path("status").asText())
                                    && capture.path("rowTypesQualified").asBoolean()
                                    && capture.path("mapperQualified").asBoolean(),
                            "PRODUCTION_SOURCE_CAPTURE_INCOMPLETE");
                    final Path payload = directory.resolve("faturas-cliente-source.complete.jsonl");
                    require(
                            Files.size(payload) <= 8388608
                                    && sha256(Files.readAllBytes(payload))
                                            .equals(capture.path("payloadSha256").asText()),
                            "PRODUCTION_SOURCE_CAPTURE_PIN_MISMATCH");
                    final List<String> rows = Files.readAllLines(payload, StandardCharsets.UTF_8);
                    require(
                            rows.size() == capture.path("rows").asInt(),
                            "PRODUCTION_CAPTURE_ROW_COUNT");
                    report.put("phase", "STORE_AND_READ_BACK_EVERY_OCCURRENCE");
                    store.storeAndVerify(run, rows, capture.path("payloadSha256").asText());
                    item.put("stored", true).put("sqlEveryOccurrenceExact", true);
                } catch (final Exception failure) {
                    // A committed response may be uncertain. This update can never downgrade
                    // STORED.
                    store.fail(run, "PRODUCTION_CAPTURE_OR_STORAGE_FAILED");
                    throw failure;
                }
            }
        }
    }
}
