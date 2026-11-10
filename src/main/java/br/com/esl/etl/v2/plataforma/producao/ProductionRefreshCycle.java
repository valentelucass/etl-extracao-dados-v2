package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRecordInput;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportMetadataCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Bounded initial import, update discovery and atomic replacement of complete business dates. */
public final class ProductionRefreshCycle {
    @FunctionalInterface
    public interface CapturePort {
        ReportSnapshotExtractor.Result capture(
                ReportSnapshotTemplate template,
                BusinessDateRange dates,
                SourceDateTimeRange updates,
                ReportSnapshotExtractor.Wire wire,
                ReportSnapshotExtractor.Sink sink,
                ReportSnapshotExtractor.Limits limits)
                throws Exception;
    }

    public interface Receiving extends AutoCloseable {
        LocalDate earliestCovered(String scope, LocalDate today, String entity) throws Exception;

        void start(UUID run, String scope, LocalDate date, String requestSha, String entity)
                throws Exception;

        void storeAndVerify(UUID run, List<String> rows, String captureSha) throws Exception;

        void fail(UUID run, String reason) throws Exception;

        @Override
        void close() throws java.sql.SQLException;
    }

    @FunctionalInterface
    public interface StoreFactory {
        Receiving open(JsonNode config) throws Exception;
    }

    public record Session(ReportSnapshotExtractor.Wire wire, IntSupplier requests) {}

    @FunctionalInterface
    public interface WireFactory {
        Session open(URI origin, String token, Path root, Path stop, Instant deadline)
                throws Exception;
    }

    private static final Path STORAGE =
            Path.of("C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2");
    private final CapturePort capture;
    private final StoreFactory stores;
    private final WireFactory wires;
    private final Clock clock;
    private final Path allowedRoot;

    public ProductionRefreshCycle(final CapturePort capture) {
        this(
                capture,
                ProductionRefreshCycle::openStore,
                (origin, token, root, stop, deadline) -> {
                    final var wire =
                            new CurlSnapshotWire(origin, token, root, stop, deadline, 2000);
                    return new Session(wire, wire::requests);
                },
                Clock.systemUTC(),
                STORAGE);
    }

    ProductionRefreshCycle(
            final CapturePort capture,
            final StoreFactory stores,
            final WireFactory wires,
            final Clock clock,
            final Path allowedRoot) {
        this.capture = java.util.Objects.requireNonNull(capture);
        this.stores = java.util.Objects.requireNonNull(stores);
        this.wires = java.util.Objects.requireNonNull(wires);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    private static Receiving openStore(final JsonNode config) throws Exception {
        final var store = new AllEntitiesIngestionStore(config);
        return new Receiving() {
            @Override
            public LocalDate earliestCovered(
                    final String scope, final LocalDate today, final String entity)
                    throws Exception {
                return store.earliestCovered(scope, today, entity);
            }

            @Override
            public void start(
                    final UUID run,
                    final String scope,
                    final LocalDate date,
                    final String requestSha,
                    final String entity)
                    throws Exception {
                store.start(run, scope, date, requestSha, entity);
            }

            @Override
            public void storeAndVerify(
                    final UUID run, final List<String> rows, final String captureSha)
                    throws Exception {
                store.storeAndVerify(run, rows, captureSha);
            }

            @Override
            public void fail(final UUID run, final String reason) throws Exception {
                store.fail(run, reason);
            }

            @Override
            public void close() throws java.sql.SQLException {
                store.close();
            }
        };
    }

    private record Context(
            RefreshCheckpoint checkpoint,
            ProductionRefreshPolicy policy,
            Receiving store,
            Session session,
            URI origin,
            String scope,
            Path root,
            LocalDate today,
            Instant cutoff,
            ReportMetadataCatalog catalog,
            FreightDependencyIndex dependencyIndexes,
            ArrayNode operations,
            Map<String, UUID> confirmedDates,
            Map<String, Integer> changedDays) {}

    private static final class DeferredWork extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DeferredWork() {
            super("REFRESH_BATCH_DEFERRED");
        }
    }

    private Session bounded(final Session original, final Instant deadline) {
        final var wire =
                new ReportSnapshotExtractor.Wire() {
                    private void admit() {
                        if (original.requests().getAsInt() >= 1750
                                || !clock.instant().isBefore(deadline.minusSeconds(90))) {
                            throw new DeferredWork();
                        }
                    }

                    @Override
                    public JsonNode info(final ReportSnapshotTemplate template) throws Exception {
                        admit();
                        return original.wire().info(template);
                    }

                    @Override
                    public JsonNode page(
                            final ReportSnapshotTemplate template,
                            final BusinessDateRange dates,
                            final int page,
                            final int per)
                            throws Exception {
                        admit();
                        return original.wire().page(template, dates, page, per);
                    }

                    @Override
                    public JsonNode updatedPage(
                            final ReportSnapshotTemplate template,
                            final BusinessDateRange dates,
                            final SourceDateTimeRange updates,
                            final int page,
                            final int per)
                            throws Exception {
                        admit();
                        return original.wire().updatedPage(template, dates, updates, page, per);
                    }

                    @Override
                    public DataExportRecordInput recordInput(final JsonNode row, final int index) {
                        return original.wire().recordInput(row, index);
                    }
                };
        return new Session(wire, original.requests());
    }

    public boolean run(final JsonNode config) throws Exception {
        require(
                "ETL_SISTEMA_V2".equals(config.path("database").asText()),
                "REFRESH_DATABASE_REQUIRED");
        require(
                config.path("runtimeSha256").asText().matches("[0-9a-f]{64}"),
                "REFRESH_RUNTIME_SHA_REQUIRED");
        final var began = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        final var today = began.atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
        final var policy = ProductionRefreshPolicy.from(config, today);
        final var root = admitted(config, "privateRoot");
        final var reportPath = admitted(config, "reportPath");
        final var checkpointPath = admitted(config, "checkpointPath");
        final var stop = admitted(config, "sourceStopPath");
        require(
                !Files.exists(root)
                        && !Files.exists(reportPath)
                        && !checkpointPath.startsWith(root)
                        && !checkpointPath.equals(reportPath)
                        && !Files.exists(stop),
                "REFRESH_FRESH_PRIVATE_SCOPE");
        final var origin = URI.create(config.required("apiOrigin").asText());
        ReportSnapshotTemplate.COLETAS.info(origin);
        final var token = config.required("apiToken").asText();
        require(!token.isBlank(), "REFRESH_SOURCE_CREDENTIAL_REQUIRED");
        final var scope = hash(origin + "\n" + hash(token));
        final var json = CurlSnapshotWire.JSON;
        json.registerModule(new JavaTimeModule());
        json.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        final var checkpoint = new RefreshCheckpoint(checkpointPath, scope, began);
        checkpoint.requireEligible(clock.instant());
        Files.createDirectories(root);
        final var deadline = began.plus(Duration.ofMinutes(105));
        final var session = bounded(wires.open(origin, token, root, stop, deadline), deadline);
        final var report =
                json.createObjectNode()
                        .put("schema", "PRODUCTION_REFRESH0766")
                        .put("relatedFieldRefreshRevision", "0768")
                        .put("startedAt", began.toString())
                        .put("sourceScope", scope)
                        .put("runtimeSha256", config.required("runtimeSha256").asText())
                        .put("requestedHistoryStart", policy.requestedStart().toString())
                        .put("minimumInitialDays", 90)
                        .put("waitAfterCompletionMinutes", 60)
                        .put("passed", false)
                        .put("initialComplete", false)
                        .put("publication", false);
        final var entities = report.putArray("entities");
        final var operations = report.putArray("operations");
        boolean passed = true;
        saveReport(reportPath, report, session);
        try (var store = stores.open(config)) {
            final var context =
                    new Context(
                            checkpoint,
                            policy,
                            store,
                            session,
                            origin,
                            scope,
                            root,
                            today,
                            began.minusSeconds(120),
                            new ReportMetadataCatalog(),
                            new FreightDependencyIndex(
                                    checkpointPath
                                            .getParent()
                                            .resolve("dependency-index")
                                            .resolve(scope),
                                    allowedRoot,
                                    scope),
                            operations,
                            new HashMap<>(),
                            new HashMap<>());
            for (final var template : ReportSnapshotTemplate.values()) {
                final var result =
                        entities.addObject()
                                .put("entity", template.name())
                                .put("reportId", template.template().templateId());
                try {
                    final var response = session.wire().info(template);
                    context.catalog()
                            .validate(
                                    template,
                                    response.has("data") ? response.required("data") : response);
                    final var state =
                            checkpoint.entity(
                                    template,
                                    policy.requestedStart(),
                                    store.earliestCovered(scope, today, template.name()),
                                    today,
                                    began);
                    require(
                            !LocalDate.parse(state.required("floor").asText()).isAfter(today),
                            "REFRESH_FLOOR_IN_FUTURE");
                    process(context, template, state);
                    result.put("initialComplete", state.path("initialComplete").asBoolean())
                            .put("historyStart", state.required("floor").asText())
                            .put("watermark", state.required("watermark").asText())
                            .put(
                                    "initialRemainingDays",
                                    state.path("initialComplete").asBoolean()
                                            ? 0
                                            : Math.max(
                                                    0,
                                                    ChronoUnit.DAYS.between(
                                                                    LocalDate.parse(
                                                                            state.required("floor")
                                                                                    .asText()),
                                                                    LocalDate.parse(
                                                                            state.required(
                                                                                            "initialNext")
                                                                                    .asText()))
                                                            + 1))
                            .put("nextReconciliationDay", state.required("sweepNext").asText())
                            .put("sweepsCompleted", state.path("sweepsCompleted").asInt())
                            .put("pendingUpdateWork", pending(state))
                            .put("relatedWatermark", state.path("relatedWatermark").asText())
                            .put(
                                    "relatedCaughtUp",
                                    relatedCaughtUp(state, template, context.cutoff()))
                            .put(
                                    "status",
                                    pending(state)
                                                    || (state.path("initialComplete").asBoolean()
                                                            && !relatedCaughtUp(
                                                                    state,
                                                                    template,
                                                                    context.cutoff()))
                                            ? "BATCH_DEFERRED"
                                            : "BATCH_COMPLETED");
                } catch (final DeferredWork deferred) {
                    result.put("status", "BATCH_DEFERRED");
                } catch (final Exception failure) {
                    passed = false;
                    result.put("status", "FAILED").put("reason", safe(failure));
                    break;
                }
                saveReport(reportPath, report, session);
            }
        } catch (final Exception failure) {
            passed = false;
            report.put("reason", safe(failure));
        } finally {
            final var completed = clock.instant();
            if (passed && entities.size() == 11) {
                try {
                    checkpoint.completed(completed);
                } catch (final Exception failure) {
                    passed = false;
                    report.put("reason", safe(failure));
                }
            }
            report.put("completedAt", completed.toString())
                    .put(
                            "nextEligibleAt",
                            ProductionRefreshPolicy.nextInvocation(completed).toString())
                    .put("durationSeconds", Duration.between(began, completed).toSeconds())
                    .put("initialComplete", checkpoint.allInitialized())
                    .put("passed", passed && entities.size() == 11);
            saveReport(reportPath, report, session);
        }
        return report.path("passed").asBoolean();
    }

    private void process(
            final Context context, final ReportSnapshotTemplate template, final ObjectNode state)
            throws Exception {
        final var floor = LocalDate.parse(state.required("floor").asText());
        if (!state.path("initialComplete").asBoolean()) {
            var next = LocalDate.parse(state.required("initialNext").asText());
            for (int count = 0;
                    count < context.policy().initialBatchDays() && !next.isBefore(floor);
                    count++) {
                fullDay(context, template, next, "INITIAL");
                next = next.minusDays(1);
                state.put("initialNext", next.toString());
                context.checkpoint().save();
            }
            if (next.isBefore(floor)) {
                state.put("initialComplete", true)
                        .put("watermark", state.required("bootstrapCutoff").asText());
                context.checkpoint().save();
            }
            return;
        }
        var lastDay = LocalDate.parse(state.required("lastCalendarDay").asText());
        int newDates = 0;
        while (lastDay.isBefore(context.today()) && newDates++ < 8) {
            lastDay = lastDay.plusDays(1);
            fullDay(context, template, lastDay, "NEW_CALENDAR_DAY");
            state.put("lastCalendarDay", lastDay.toString());
            context.checkpoint().save();
        }
        if (lastDay.isBefore(context.today())) {
            return;
        }
        if (template == ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA) {
            reconcileConsolidations(context, state);
            return;
        }
        if (FreightDependencyIndex.supports(template)) {
            int rebuilt = 0;
            state.put("relatedIndexPending", true);
            for (var day = floor; !day.isAfter(context.today()); day = day.plusDays(1)) {
                if (!context.dependencyIndexes().exists(template, day)) {
                    if (rebuilt++ >= context.policy().changedDaysPerBatch()) {
                        context.checkpoint().save();
                        return;
                    }
                    fullDay(context, template, day, "RELATED_INDEX_BOOTSTRAP");
                }
            }
            state.put("relatedIndexPending", false);
            context.checkpoint().save();
            final var related =
                    new RelatedFreightRefresh(
                            context.dependencyIndexes(),
                            capture,
                            context.session().wire(),
                            context.root(),
                            context.operations());
            if (!related.refresh(
                    template,
                    state,
                    context.today(),
                    context.cutoff(),
                    context.policy().changedDaysPerBatch(),
                    context.checkpoint()::save,
                    day -> fullDay(context, template, day, "RELATED_UPDATED"))) {
                return;
            }
        }
        final var update = ReportUpdateContract.forTemplate(template);
        // Rotating complete snapshots also repair deletions, moved dates and related-table edits.
        sweep(
                context,
                template,
                state,
                update == null ? context.policy().reconciliationBatchDays() : 1);
        if (update == null) {
            return;
        }
        for (int windowCount = 0;
                windowCount
                        < (template == ReportSnapshotTemplate.MANIFESTOS || update.dateOnly()
                                ? 1
                                : 24);
                windowCount++) {
            if (!state.has("pending")) {
                final var window =
                        ProductionRefreshPolicy.nextWindow(
                                Instant.parse(state.required("watermark").asText()),
                                context.cutoff(),
                                update.dateOnly(),
                                template == ReportSnapshotTemplate.MANIFESTOS ? 24 : 1);
                if (window == null) {
                    return;
                }
                final var pending = state.putObject("pending");
                pending.put("from", window.query().startInclusive().toString())
                        .put("through", window.query().endInclusive().toString())
                        .put("commitThrough", window.commitThrough().toString())
                        .put("scopeEnd", context.today().toString())
                        .put("discovered", false)
                        .put("manifestNext", floor.toString());
                pending.putArray("dates");
                context.checkpoint().save();
            }
            final var pending = (ObjectNode) state.required("pending");
            final var window =
                    new SourceDateTimeRange(
                            Instant.parse(pending.required("from").asText()),
                            Instant.parse(pending.required("through").asText()));
            final var scopeEnd = LocalDate.parse(pending.required("scopeEnd").asText());
            if (template == ReportSnapshotTemplate.MANIFESTOS) {
                var next = LocalDate.parse(pending.required("manifestNext").asText());
                int replaced = 0;
                for (int count = 0;
                        count < context.policy().manifestScanDays()
                                && !next.isAfter(scopeEnd)
                                && replaced < context.policy().changedDaysPerBatch();
                        count++) {
                    final var changed =
                            discover(context, template, new BusinessDateRange(next, next), window);
                    if (changed.rows() > 0) {
                        fullDay(context, template, next, "UPDATED");
                        replaced++;
                    }
                    next = next.plusDays(1);
                    pending.put("manifestNext", next.toString());
                    context.checkpoint().save();
                }
                if (next.isAfter(scopeEnd)) {
                    commitWindow(context, state, pending);
                }
                return;
            }
            if (!pending.path("discovered").asBoolean()) {
                final var changed =
                        discover(context, template, new BusinessDateRange(floor, scopeEnd), window);
                final var dates = pending.putArray("dates");
                changed.dates().forEach(day -> dates.add(day.toString()));
                pending.put("discovered", true);
                context.checkpoint().save();
            }
            final var dates = (ArrayNode) pending.required("dates");
            for (int count = 0;
                    count < context.policy().changedDaysPerBatch() && !dates.isEmpty();
                    count++) {
                final var day = LocalDate.parse(dates.get(0).asText());
                fullDay(context, template, day, "UPDATED");
                dates.remove(0);
                context.checkpoint().save();
            }
            if (dates.isEmpty()) {
                commitWindow(context, state, pending);
            } else {
                return;
            }
        }
    }

    private static void commitWindow(
            final Context context, final ObjectNode state, final ObjectNode pending)
            throws Exception {
        state.put("watermark", pending.required("commitThrough").asText());
        state.remove("pending");
        context.checkpoint().save();
    }

    private void reconcileConsolidations(final Context context, final ObjectNode state)
            throws Exception {
        final var floor = LocalDate.parse(state.required("floor").asText());
        if (!state.has("consolidationSweep")
                || !state.path("consolidationSweep")
                        .path("floor")
                        .asText()
                        .equals(floor.toString())) {
            state.putObject("consolidationSweep")
                    .put("floor", floor.toString())
                    .put("through", context.today().toString())
                    .put("next", floor.toString())
                    .put("cutoff", context.cutoff().toString());
            context.checkpoint().save();
        }
        final var round = (ObjectNode) state.required("consolidationSweep");
        final var through = LocalDate.parse(round.required("through").asText());
        var next = LocalDate.parse(round.required("next").asText());
        while (!next.isAfter(through)) {
            fullDay(
                    context,
                    ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA,
                    next,
                    "CONSOLIDATION_RECONCILIATION");
            next = next.plusDays(1);
            round.put("next", next.toString());
            state.put("sweepNext", next.toString());
            context.checkpoint().save();
        }
        final var confirmedThrough = Instant.parse(round.required("cutoff").asText());
        state.put("watermark", confirmedThrough.toString())
                .put("relatedWatermark", confirmedThrough.toString())
                .put("sweepNext", floor.toString())
                .put("sweepsCompleted", state.path("sweepsCompleted").asInt() + 1)
                .put("lastSweepCompletedAt", clock.instant().toString())
                .put("relatedIndexPending", false);
        for (final var oldQueue : List.of("pending", "relatedPending")) {
            if (state.has(oldQueue)
                    && !Instant.parse(state.path(oldQueue).required("commitThrough").asText())
                            .isAfter(confirmedThrough)) {
                state.remove(oldQueue);
            }
        }
        state.remove("consolidationSweep");
        context.checkpoint().save();
    }

    private void sweep(
            final Context context,
            final ReportSnapshotTemplate template,
            final ObjectNode state,
            final int limit)
            throws Exception {
        final var floor = LocalDate.parse(state.required("floor").asText());
        var next = LocalDate.parse(state.required("sweepNext").asText());
        for (int count = 0; count < limit; count++) {
            if (next.isAfter(context.today())) {
                next = floor;
            }
            fullDay(context, template, next, "RECONCILIATION");
            next = next.plusDays(1);
            if (next.isAfter(context.today())) {
                state.put("sweepsCompleted", state.path("sweepsCompleted").asInt() + 1)
                        .put("lastSweepCompletedAt", clock.instant().toString());
                next = floor;
                state.put("sweepNext", next.toString());
                context.checkpoint().save();
                break;
            }
            state.put("sweepNext", next.toString());
            context.checkpoint().save();
        }
    }

    private record Changes(long rows, TreeSet<LocalDate> dates) {}

    private Changes discover(
            final Context context,
            final ReportSnapshotTemplate template,
            final BusinessDateRange scope,
            final SourceDateTimeRange window)
            throws Exception {
        final int scopeDays = template == ReportSnapshotTemplate.FRETES ? 1 : 8;
        if (ChronoUnit.DAYS.between(scope.startInclusive(), scope.endInclusive()) >= scopeDays) {
            final var days = new TreeSet<LocalDate>();
            long rows = 0;
            for (var first = scope.startInclusive();
                    !first.isAfter(scope.endInclusive());
                    first = first.plusDays(scopeDays)) {
                final var last =
                        first.plusDays(scopeDays - 1L).isAfter(scope.endInclusive())
                                ? scope.endInclusive()
                                : first.plusDays(scopeDays - 1L);
                final var part =
                        discover(context, template, new BusinessDateRange(first, last), window);
                rows += part.rows();
                days.addAll(part.dates());
            }
            return new Changes(rows, days);
        }
        final var dates = new TreeSet<LocalDate>();
        final var characters = new long[] {0};
        final var path =
                context.root()
                        .resolve(template.name() + "-changes-" + UUID.randomUUID() + ".jsonl");
        final var operation =
                context.operations()
                        .addObject()
                        .put("entity", template.name())
                        .put("phase", "UPDATE_DISCOVERY")
                        .put("acceptedAsCompleteSnapshot", false);
        try (var output =
                Files.newBufferedWriter(
                        path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            final var result =
                    capture.capture(
                            template,
                            scope,
                            window,
                            context.session().wire(),
                            (id, ordinal, row, observation) -> {
                                final var text = row.toString();
                                characters[0] += text.length();
                                require(
                                        characters[0] <= 40000000,
                                        "REFRESH_DISCOVERY_OUTPUT_BOUND");
                                output.write(text);
                                output.newLine();
                                if (template.rowDate() != null) {
                                    final var day =
                                            ReportSnapshotExtractor.businessDate(template, row);
                                    require(
                                            !day.isBefore(scope.startInclusive())
                                                    && !day.isAfter(scope.endInclusive()),
                                            "REFRESH_DISCOVERY_DATE_OUTSIDE_SCOPE");
                                    dates.add(day);
                                }
                            },
                            limits(template));
            require(
                    result.success()
                            && result.typedRows() == result.rows()
                            && result.fields() == context.catalog().fieldCount(template),
                    "REFRESH_UPDATE_CAPTURE_INCOMPLETE");
            operation
                    .put("rows", result.rows())
                    .put("terminal", result.terminal())
                    .put("changedDates", dates.size());
            return new Changes(result.rows(), dates);
        }
    }

    private void fullDay(
            final Context context,
            final ReportSnapshotTemplate template,
            final LocalDate day,
            final String phase)
            throws Exception {
        require(!day.isAfter(context.today()), "REFRESH_FUTURE_SNAPSHOT");
        final var key = template.name() + ":" + day;
        if (!phase.equals("RELATED_UPDATED") && context.confirmedDates().containsKey(key)) {
            context.operations()
                    .addObject()
                    .put("entity", template.name())
                    .put("day", day.toString())
                    .put("phase", phase)
                    .put("reusedConfirmedRun", context.confirmedDates().get(key).toString());
            return;
        }
        if (updated(phase)
                && context.changedDays().getOrDefault(template.name(), 0)
                        >= context.policy().changedDaysPerBatch()) {
            throw new DeferredWork();
        }
        final var dates = new BusinessDateRange(day, day);
        final var run = UUID.randomUUID();
        final var operation =
                context.operations()
                        .addObject()
                        .put("entity", template.name())
                        .put("day", day.toString())
                        .put("phase", phase)
                        .put("runId", run.toString())
                        .put("stored", false);
        final var path = context.root().resolve(template.name() + "-" + day + "-" + run + ".jsonl");
        final var rows = new ArrayList<String>();
        final var characters = new long[] {0};
        context.store()
                .start(
                        run,
                        context.scope(),
                        day,
                        hash(
                                template.query(context.origin(), dates, 1, limits(template).per())
                                        .toString()),
                        template.name());
        try {
            ReportSnapshotExtractor.Result result;
            try (var output =
                    Files.newBufferedWriter(
                            path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                result =
                        capture.capture(
                                template,
                                dates,
                                null,
                                context.session().wire(),
                                (id, ordinal, row, observation) -> {
                                    final var envelope =
                                            CurlSnapshotWire.JSON
                                                    .createObjectNode()
                                                    .put("entity", template.name())
                                                    .put(
                                                            "templateId",
                                                            template.template().templateId())
                                                    .put("capture", id.toString())
                                                    .put("ordinal", ordinal);
                                    envelope.set("record", row);
                                    if (observation != null) {
                                        envelope.set(
                                                "observation",
                                                CurlSnapshotWire.JSON.valueToTree(observation));
                                    }
                                    final var text = envelope.toString();
                                    characters[0] += text.length();
                                    require(
                                            characters[0] <= 40000000,
                                            "REFRESH_SNAPSHOT_MEMORY_BOUND");
                                    output.write(text);
                                    output.newLine();
                                    rows.add(text);
                                },
                                limits(template));
            }
            require(
                    result.success()
                            && result.typedRows() == result.rows()
                            && result.rows() == rows.size()
                            && result.fields() == context.catalog().fieldCount(template),
                    "REFRESH_SNAPSHOT_INCOMPLETE");
            final var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                final var buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            final var captureSha = java.util.HexFormat.of().formatHex(digest.digest());
            context.store().storeAndVerify(run, rows, captureSha);
            context.dependencyIndexes()
                    .remember(template, day, run, captureSha, rows, context.root());
            context.confirmedDates().put(key, run);
            if (updated(phase)) {
                context.changedDays().merge(template.name(), 1, Integer::sum);
            }
            operation.put("stored", true).put("sqlReadbackExact", true).put("rows", result.rows());
        } catch (final Exception failure) {
            try {
                context.store().fail(run, safe(failure));
            } catch (final Exception unconfirmed) {
                operation.put("failureStateUnconfirmed", true);
            }
            operation.put("reason", safe(failure));
            throw failure;
        }
    }

    private static ReportSnapshotExtractor.Limits limits(final ReportSnapshotTemplate template) {
        return new ReportSnapshotExtractor.Limits(
                200, template == ReportSnapshotTemplate.FRETES ? 25 : 100, 100000);
    }

    private static boolean updated(final String phase) {
        return phase.equals("UPDATED") || phase.equals("RELATED_UPDATED");
    }

    private static boolean pending(final ObjectNode state) {
        return state.has("pending")
                || state.has("relatedPending")
                || state.has("consolidationSweep")
                || state.path("relatedIndexPending").asBoolean();
    }

    private static boolean relatedCaughtUp(
            final ObjectNode state, final ReportSnapshotTemplate template, final Instant cutoff) {
        return !FreightDependencyIndex.supports(template)
                || (state.hasNonNull("relatedWatermark")
                        && !Instant.parse(state.required("relatedWatermark").asText())
                                .isBefore(cutoff));
    }

    private Path admitted(final JsonNode config, final String field) {
        final var path = Path.of(config.required(field).asText()).toAbsolutePath().normalize();
        require(
                path.startsWith(allowedRoot) && !path.equals(allowedRoot),
                "REFRESH_PRIVATE_PATH_SCOPE");
        return path;
    }

    private static void saveReport(final Path path, final ObjectNode report, final Session session)
            throws Exception {
        report.put("http", session.requests().getAsInt());
        RefreshCheckpoint.atomic(
                path,
                CurlSnapshotWire.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
    }

    private static String hash(final String value) throws Exception {
        return RefreshCheckpoint.hash(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(final Exception failure) {
        final var message = failure.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{1,94}")
                ? message
                : "REFRESH_"
                        + failure.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
    }

    private static void require(final boolean condition, final String reason) {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }
}
