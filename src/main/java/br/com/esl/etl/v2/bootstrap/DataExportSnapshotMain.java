package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.consolidacoes.aplicacao.ExtrairConsolidacaoDataExport;
import br.com.esl.etl.v2.modulos.ocorrencias.aplicacao.ExtrairOcorrenciaDataExport;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Explicit single finite execution; no scheduler, SQL connection, Main resume or publication. */
public final class DataExportSnapshotMain {
    private DataExportSnapshotMain() {}

    /** Same application composition for finite capture and productive receiving. */
    public static ReportSnapshotExtractor.Result capture(
            final ReportSnapshotTemplate template,
            final BusinessDateRange dates,
            final ReportSnapshotExtractor.Wire wire,
            final ReportSnapshotExtractor.Sink sink,
            final ReportSnapshotExtractor.Limits limits)
            throws Exception {
        return switch (template) {
            case CONSOLIDACAO_DETALHADA ->
                    new ExtrairConsolidacaoDataExport().execute(dates, wire, sink, limits);
            case OCORRENCIAS ->
                    new ExtrairOcorrenciaDataExport().execute(dates, wire, sink, limits);
            default ->
                    new ReportSnapshotExtractor()
                            .extractRecords(
                                    template,
                                    dates,
                                    wire,
                                    sink,
                                    input -> SnapshotMapperCatalog.map(template, input),
                                    limits);
        };
    }

    public static void main(final String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("SNAPSHOT_FIVE_ARGUMENTS");
        }
        final Path env = Path.of(args[0]).toAbsolutePath().normalize();
        final Path root = Path.of(args[1]).toAbsolutePath().normalize();
        final Path receipt = Path.of(args[2]).toAbsolutePath().normalize();
        final LocalDate start = LocalDate.parse(args[3]);
        final LocalDate end = LocalDate.parse(args[4]);
        final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if (end.isBefore(start)
                || end.isAfter(today)
                || ChronoUnit.DAYS.between(start, end) > 7
                || Files.exists(root)
                || Files.exists(receipt)
                || !root.startsWith(
                        Path.of(
                                "C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2"))) {
            throw new IllegalArgumentException("SNAPSHOT_EXPLICIT_FRESH_SCOPE");
        }
        Files.createDirectories(root);
        Files.writeString(
                root.resolve("LEIA-ME.txt"),
                "Captura privada finita0761: respostas literais, registros e observações Java. Não publicar dados.\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        final Path stop =
                Path.of(
                        "C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/"
                                + "quatro-etapas-20261024-01/independent-export-shared-dataexport/STOP429.json");
        final Instant began = Instant.now();
        final var json = CurlSnapshotWire.JSON;
        json.registerModule(new JavaTimeModule());
        json.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        final var report =
                json.createObjectNode()
                        .put("schema", "DATAEXPORT_ALL_ENTITIES0761_V1")
                        .put("periodStart", start.toString())
                        .put("periodEnd", end.toString())
                        .put("zone", "America/Sao_Paulo")
                        .put("startedAt", began.toString())
                        .put("SQL", 0)
                        .put("publication", false)
                        .put("snapshotConsistencyProven", false);
        final var entities = report.putArray("entities");
        final var wire =
                new CurlSnapshotWire(env, root, stop, began.plus(Duration.ofMinutes(60)), 2000);
        for (final var template : ReportSnapshotTemplate.values()) {
            final var result =
                    entities.addObject()
                            .put("entity", template.name())
                            .put("reportId", template.template().templateId())
                            .put("businessDateExposed", template.rowDate() != null);
            final var days = result.putArray("days");
            long total = 0;
            long typed = 0;
            long rejected = 0;
            int pages = 0;
            int fields = 0;
            int completeDays = 0;
            String failure = null;
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                final var dayResult = days.addObject().put("day", day.toString());
                if (failure != null || Files.exists(stop)) {
                    dayResult.put("status", "SKIPPED_AFTER_FAILURE");
                    if (failure == null) {
                        failure = "SNAPSHOT_SHARED_STOP429";
                    }
                    continue;
                }
                final Path rowsPath =
                        root.resolve(template.template().templateId() + "-" + day + ".jsonl");
                final long[] retained = {0};
                try (var output =
                        Files.newBufferedWriter(
                                rowsPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    final ReportSnapshotExtractor.Sink sink =
                            (capture, ordinal, row, observation) -> {
                                final var value =
                                        json.createObjectNode()
                                                .put("capture", capture.toString())
                                                .put("ordinal", ordinal)
                                                .put("reportId", template.template().templateId());
                                value.set("record", row);
                                if (observation != null) {
                                    value.set("observation", json.valueToTree(observation));
                                }
                                output.write(value.toString());
                                output.newLine();
                                retained[0]++;
                            };
                    final var dates = new BusinessDateRange(day, day);
                    final var limits = new ReportSnapshotExtractor.Limits(200, 100, 100000);
                    final var captured = capture(template, dates, wire, sink, limits);
                    dayResult
                            .put("rows", captured.rows())
                            .put("typedRows", captured.typedRows())
                            .put("rejectedRows", captured.rejectedRows())
                            .put("fields", captured.fields())
                            .put("pages", captured.pages())
                            .put("terminal", captured.terminal())
                            .put("currentDay", captured.currentDay())
                            .put(
                                    "status",
                                    captured.success() ? "COMPLETE" : "CAPTURED_WITH_ISSUES");
                    total += captured.rows();
                    typed += captured.typedRows();
                    rejected += captured.rejectedRows();
                    pages += captured.pages();
                    fields = captured.fields();
                    if (captured.terminal()) {
                        completeDays++;
                    } else {
                        failure = "SNAPSHOT_PAGE_BUDGET";
                    }
                } catch (final Exception error) {
                    total += retained[0];
                    final String message = error.getMessage();
                    failure =
                            message != null && message.matches("[A-Z][A-Z0-9_]{1,100}")
                                    ? message
                                    : "SNAPSHOT_INTERNAL_" + error.getClass().getSimpleName();
                    dayResult
                            .put("rows", retained[0])
                            .put("status", "FAILED")
                            .put("reason", failure);
                }
                update(result, total, typed, rejected, pages, fields, completeDays, failure);
                save(receipt, report, wire.requests());
                System.out.println(
                        template.name()
                                + " "
                                + day
                                + " "
                                + dayResult.path("status").asText()
                                + " rows="
                                + dayResult.path("rows").asLong());
            }
            update(result, total, typed, rejected, pages, fields, completeDays, failure);
            result.put(
                    "status",
                    failure != null
                            ? "FAILED"
                            : rejected > 0 ? "CAPTURED_WITH_REJECTIONS" : "COMPLETE");
            save(receipt, report, wire.requests());
        }
        report.put("completedAt", Instant.now().toString())
                .put("durationSeconds", Duration.between(began, Instant.now()).toSeconds());
        save(receipt, report, wire.requests());
    }

    private static void update(
            final ObjectNode result,
            final long total,
            final long typed,
            final long rejected,
            final int pages,
            final int fields,
            final int completeDays,
            final String failure) {
        result.put("rows", total)
                .put("typedRows", typed)
                .put("rejectedRows", rejected)
                .put("pages", pages)
                .put("fields", fields)
                .put("completeDays", completeDays);
        if (failure != null) {
            result.put("reason", failure);
        }
    }

    private static void save(final Path receipt, final ObjectNode report, final int requests)
            throws Exception {
        report.put("http", requests);
        final Path pending = receipt.resolveSibling(receipt.getFileName() + ".partial");
        Files.writeString(
                pending,
                CurlSnapshotWire.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
        Files.move(
                pending,
                receipt,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
