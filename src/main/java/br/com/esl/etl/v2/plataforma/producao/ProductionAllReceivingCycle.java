package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** One bounded productive cycle. The external supervisor owns the delay after completion. */
public final class ProductionAllReceivingCycle {
    @FunctionalInterface
    public interface CapturePort {
        ReportSnapshotExtractor.Result capture(
                ReportSnapshotTemplate template,
                BusinessDateRange dates,
                ReportSnapshotExtractor.Wire wire,
                ReportSnapshotExtractor.Sink sink,
                ReportSnapshotExtractor.Limits limits)
                throws Exception;
    }

    private final CapturePort capture;

    public ProductionAllReceivingCycle(final CapturePort capture) {
        this.capture = java.util.Objects.requireNonNull(capture);
    }

    static List<LocalDate> dates(final LocalDate start, final LocalDate end) {
        require(
                start != null
                        && end != null
                        && !end.isBefore(start)
                        && ChronoUnit.DAYS.between(start, end) <= 7,
                "ALL_RECEIVING_EIGHT_DAYS_REQUIRED");
        return start.datesUntil(end.plusDays(1)).toList();
    }

    static List<LocalDate> plan(
            final List<LocalDate> missing, final LocalDate start, final LocalDate end) {
        final var selected = new LinkedHashSet<LocalDate>();
        missing.stream()
                .filter(day -> day.isBefore(start))
                .sorted()
                .limit(4)
                .forEach(selected::add);
        selected.addAll(dates(start, end));
        return List.copyOf(selected);
    }

    static int pageSize(final ReportSnapshotTemplate template) {
        return template == ReportSnapshotTemplate.FRETES ? 25 : 100;
    }

    public boolean run(final JsonNode config) throws Exception {
        require(
                "ETL_SISTEMA_V2".equals(config.path("database").asText()),
                "ALL_RECEIVING_TARGET_REQUIRED");
        final String runtimeSha = config.required("runtimeSha256").asText();
        require(runtimeSha.matches("[0-9a-f]{64}"), "ALL_RECEIVING_RUNTIME_SHA_REQUIRED");
        final String mode = config.required("mode").asText();
        require("RANGE".equals(mode) || "ROLLING".equals(mode), "ALL_RECEIVING_MODE_REQUIRED");
        final var today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        final var end =
                "ROLLING".equals(mode)
                        ? today
                        : LocalDate.parse(config.required("periodEnd").asText());
        final var start =
                "ROLLING".equals(mode)
                        ? today.minusDays(7)
                        : LocalDate.parse(config.required("periodStart").asText());
        dates(start, end);
        require(!end.isAfter(today), "ALL_RECEIVING_FUTURE_DATE_REFUSED");
        final var anchor = LocalDate.parse(config.required("recoveryAnchor").asText());
        require(
                !anchor.isAfter(start) && ChronoUnit.DAYS.between(anchor, end) <= 3660,
                "ALL_RECEIVING_RECOVERY_ANCHOR_REQUIRED");
        final var root =
                Path.of(config.required("privateRoot").asText()).toAbsolutePath().normalize();
        final var receipt =
                Path.of(config.required("reportPath").asText()).toAbsolutePath().normalize();
        final var stop = Path.of(config.required("stopPath").asText()).toAbsolutePath().normalize();
        require(
                root.startsWith(
                                Path.of(
                                        "C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2"))
                        && !Files.exists(root)
                        && !Files.exists(receipt)
                        && !Files.exists(stop),
                "ALL_RECEIVING_FRESH_SCOPE_REQUIRED");
        final var origin = URI.create(config.required("apiOrigin").asText());
        final String token = config.required("apiToken").asText();
        ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA.info(origin);
        final String scope = sha(origin + "\n" + sha(token));
        final var json = CurlSnapshotWire.JSON;
        json.registerModule(new JavaTimeModule());
        json.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.createDirectories(root);
        final var began = Instant.now();
        final var report =
                json.createObjectNode()
                        .put("schema", "ALL_RECEIVING0762_V1")
                        .put("runtimeRevision", "0762-v6")
                        .put("runtimeSha256", runtimeSha)
                        .put(
                                "consolidacoesContractResource",
                                ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA.contractResource())
                        .put(
                                "ocorrenciasContractResource",
                                ReportSnapshotTemplate.OCORRENCIAS.contractResource())
                        .put("target", "ETL_SISTEMA_V2")
                        .put("mode", mode)
                        .put("periodStart", start.toString())
                        .put("periodEnd", end.toString())
                        .put("recoveryAnchor", anchor.toString())
                        .put("sourceScope", scope)
                        .put("startedAt", began.toString())
                        .put("passed", false)
                        .put("publication", false)
                        .put("snapshotConsistencyProven", false)
                        .put("maximumMinutes", 105)
                        .put("maximumHttp", 2000)
                        .put("waitAfterCompletionMinutes", 60);
        final var entities = report.putArray("entities");
        final var wire =
                new CurlSnapshotWire(
                        origin, token, root, stop, began.plus(Duration.ofMinutes(105)), 2000);
        boolean allPassed = true;
        boolean stopAll = false;
        save(receipt, report, wire.requests());
        try (var store = new AllEntitiesIngestionStore(config)) {
            for (final var template : ReportSnapshotTemplate.values()) {
                final var entity =
                        entities.addObject()
                                .put("entity", template.name())
                                .put("reportId", template.template().templateId())
                                .put("businessDateExposed", template.rowDate() != null);
                final var missing = new ArrayList<LocalDate>();
                store.missing(scope, anchor, start.minusDays(1), template.name(), missing::add);
                entity.put("olderMissingBefore", missing.size());
                final var requestedMissing = new ArrayList<LocalDate>();
                store.missing(scope, start, end, template.name(), requestedMissing::add);
                entity.put("requestedMissingBefore", requestedMissing.size());
                final var selected =
                        "ROLLING".equals(mode) ? plan(missing, start, end) : dates(start, end);
                final var days = entity.putArray("days");
                boolean entityFailed = false;
                if (!stopAll && !Files.exists(stop)) {
                    try {
                        validateMetadata(template, wire.info(template));
                    } catch (final Exception failure) {
                        final String code = safeFailure(failure);
                        entity.put("metadataFailure", code);
                        entityFailed = true;
                        allPassed = false;
                        stopAll =
                                Files.exists(stop)
                                        || code.contains("DEADLINE")
                                        || code.contains("HTTP_BUDGET");
                    }
                }
                long entityRows = 0;
                long cells = 0;
                int storedDays = 0;
                for (final var day : selected) {
                    final var result =
                            days.addObject()
                                    .put("day", day.toString())
                                    .put("stored", false)
                                    .put("sqlEveryOccurrenceExact", false);
                    if (entityFailed || stopAll || Files.exists(stop)) {
                        stopAll = stopAll || Files.exists(stop);
                        result.put("status", "SKIPPED_AFTER_FAILURE");
                        allPassed = false;
                        continue;
                    }
                    final var run = UUID.randomUUID();
                    result.put("runId", run.toString());
                    final var rows = new ArrayList<String>();
                    final var retained = new long[] {0};
                    final var characters = new long[] {0};
                    boolean started = false;
                    boolean storeEntered = false;
                    final var path =
                            root.resolve(
                                    template.template().templateId()
                                            + "-"
                                            + day
                                            + "-"
                                            + run
                                            + ".jsonl");
                    try {
                        final String requestSha =
                                sha(
                                        template.query(
                                                        origin,
                                                        new BusinessDateRange(day, day),
                                                        1,
                                                        pageSize(template))
                                                .toString());
                        store.start(run, scope, day, requestSha, template.name());
                        started = true;
                        ReportSnapshotExtractor.Result captured;
                        try (var output =
                                Files.newBufferedWriter(
                                        path,
                                        StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE_NEW)) {
                            captured =
                                    capture.capture(
                                            template,
                                            new BusinessDateRange(day, day),
                                            wire,
                                            (captureId, ordinal, row, observation) -> {
                                                final var envelope =
                                                        json.createObjectNode()
                                                                .put("entity", template.name())
                                                                .put(
                                                                        "templateId",
                                                                        template.template()
                                                                                .templateId())
                                                                .put(
                                                                        "capture",
                                                                        captureId.toString())
                                                                .put("ordinal", ordinal);
                                                envelope.set("record", row);
                                                if (observation != null) {
                                                    envelope.set(
                                                            "observation",
                                                            json.valueToTree(observation));
                                                }
                                                final String text = envelope.toString();
                                                characters[0] += text.length();
                                                require(
                                                        characters[0] <= 40000000,
                                                        "ALL_RECEIVING_PAYLOAD_BUDGET");
                                                output.write(text);
                                                output.newLine();
                                                rows.add(text);
                                                retained[0]++;
                                            },
                                            new ReportSnapshotExtractor.Limits(
                                                    200, pageSize(template), 100000));
                        }
                        result.put("rows", captured.rows())
                                .put("typedRows", captured.typedRows())
                                .put("rejectedRows", captured.rejectedRows())
                                .put("fields", captured.fields())
                                .put("pages", captured.pages())
                                .put("terminal", captured.terminal())
                                .put("currentDay", captured.currentDay());
                        require(
                                captured.success() && captured.typedRows() == captured.rows(),
                                "ALL_RECEIVING_COMPLETE_TYPED_CAPTURE_REQUIRED");
                        final String captureSha;
                        try (var input = Files.newInputStream(path)) {
                            final var digest = MessageDigest.getInstance("SHA-256");
                            final byte[] buffer = new byte[65536];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                digest.update(buffer, 0, count);
                            }
                            captureSha = HexFormat.of().formatHex(digest.digest());
                        }
                        storeEntered = true;
                        store.storeAllAndVerify(run, rows, captureSha);
                        result.put("stored", true)
                                .put("sqlEveryOccurrenceExact", true)
                                .put("status", "STORED")
                                .put("captureSha256", captureSha)
                                .put("sqlReadbackRows", rows.size())
                                .put("verifiedCells", captured.rows() * captured.fields());
                        entityRows += captured.rows();
                        cells += captured.rows() * captured.fields();
                        storedDays++;
                    } catch (final Exception failure) {
                        final String code = safeFailure(failure);
                        if (started) {
                            try {
                                store.fail(run, code);
                            } catch (final Exception failedToRecord) {
                                result.put("failureStateRecording", "UNCONFIRMED");
                            }
                        }
                        result.put("status", "FAILED")
                                .put("reason", code)
                                .put("retainedRows", retained[0])
                                .put("storeEntered", storeEntered)
                                .put("commitReconciled", false);
                        entityFailed = true;
                        allPassed = false;
                        stopAll =
                                Files.exists(stop)
                                        || code.contains("DEADLINE")
                                        || code.contains("HTTP_BUDGET");
                    }
                    entity.put("rows", entityRows)
                            .put("verifiedCells", cells)
                            .put("storedDays", storedDays);
                    save(receipt, report, wire.requests());
                    System.out.println(
                            template.name() + " " + day + " " + result.path("status").asText());
                }
                final var stillMissing = new ArrayList<LocalDate>();
                store.missing(scope, anchor, end, template.name(), stillMissing::add);
                entity.put("missingAfter", stillMissing.size())
                        .put("status", entityFailed || stopAll ? "FAILED" : "STORED");
                save(receipt, report, wire.requests());
            }
        } catch (final Exception failure) {
            allPassed = false;
            report.put("failure", safeFailure(failure));
        } finally {
            report.put("completedAt", Instant.now().toString())
                    .put("durationSeconds", Duration.between(began, Instant.now()).toSeconds())
                    .put(
                            "passed",
                            allPassed && entities.size() == ReportSnapshotTemplate.values().length);
            save(receipt, report, wire.requests());
        }
        return report.path("passed").asBoolean();
    }

    private static String safeFailure(final Exception failure) {
        if (failure instanceof java.sql.SQLException sql) {
            return "ALL_RECEIVING_SQL_" + Math.abs(sql.getErrorCode());
        }
        final String message = failure.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{1,94}")
                ? message
                : "ALL_RECEIVING_"
                        + failure.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
    }

    static void validateMetadata(final ReportSnapshotTemplate template, final JsonNode response)
            throws Exception {
        final var info =
                response.isObject() && response.has("data") ? response.get("data") : response;
        try (var input =
                ProductionAllReceivingCycle.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "contracts/dataexport/receiving-catalog-0762-v2.json")) {
            require(input != null, "ALL_RECEIVING_CATALOG_REQUIRED");
            final var catalog = CurlSnapshotWire.JSON.readTree(input);
            JsonNode expected = null;
            for (final var entity : catalog.required("entities")) {
                if (template.name().equals(entity.path("entity").asText())) {
                    expected = entity;
                }
            }
            require(
                    expected != null
                            && expected.path("reportId").asInt()
                                    == template.template().templateId(),
                    "ALL_RECEIVING_CATALOG_TEMPLATE");
            final var actualFields = new java.util.HashSet<String>();
            info.path("fields").fieldNames().forEachRemaining(actualFields::add);
            final var expectedFields = new java.util.HashSet<String>();
            for (final var field : expected.required("fields")) {
                expectedFields.add(field.asText());
            }
            require(actualFields.equals(expectedFields), "ALL_RECEIVING_INFO_FIELD_DRIFT");
            require(
                    info.path("filters").equals(expected.path("filters"))
                            && info.path("default_order").equals(expected.path("defaultOrder"))
                            && info.path("required_date_filters")
                                    .equals(expected.path("requiredDateFilters")),
                    "ALL_RECEIVING_INFO_SEMANTIC_DRIFT");
        }
    }

    private static String sha(final String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void require(final boolean condition, final String code) {
        if (!condition) {
            throw new IllegalArgumentException(code);
        }
    }

    private static void save(final Path path, final ObjectNode value, final int http)
            throws Exception {
        value.put("http", http);
        final var pending = path.resolveSibling(path.getFileName() + ".partial");
        Files.writeString(
                pending,
                CurlSnapshotWire.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),
                StandardCharsets.UTF_8);
        Files.move(
                pending,
                path,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
