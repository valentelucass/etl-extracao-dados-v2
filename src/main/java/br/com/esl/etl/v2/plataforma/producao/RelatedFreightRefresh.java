package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportMetadataCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Reconciles joined values that do not advance the root report's update timestamp. */
final class RelatedFreightRefresh {
    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface Replacement {
        void replace(LocalDate day) throws Exception;
    }

    private record Source(
            Set<String> keys, Map<String, Set<String>> locations, boolean unassignedLocation) {}

    private final FreightDependencyIndex indexes;
    private final ProductionRefreshCycle.CapturePort capture;
    private final ReportSnapshotExtractor.Wire wire;
    private final Path root;
    private final ArrayNode operations;

    RelatedFreightRefresh(
            final FreightDependencyIndex indexes,
            final ProductionRefreshCycle.CapturePort capture,
            final ReportSnapshotExtractor.Wire wire,
            final Path root,
            final ArrayNode operations) {
        this.indexes = indexes;
        this.capture = capture;
        this.wire = wire;
        this.root = root;
        this.operations = operations;
    }

    boolean refresh(
            final ReportSnapshotTemplate template,
            final ObjectNode state,
            final LocalDate through,
            final Instant cutoff,
            final int replacementLimit,
            final CheckedAction save,
            final Replacement replace)
            throws Exception {
        final var floor = LocalDate.parse(state.required("floor").asText());
        if (!state.has("relatedWatermark")) {
            state.put("relatedWatermark", state.required("bootstrapCutoff").asText());
            save.run();
        }
        if (!state.has("relatedPending")) {
            final var window =
                    ProductionRefreshPolicy.nextWindow(
                            Instant.parse(state.required("relatedWatermark").asText()),
                            cutoff,
                            false,
                            24);
            if (window == null) {
                return true;
            }
            final var dates = indexes.serviceDays(template, floor, through);
            final var cutoffDay = cutoff.atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
            final var sourceEnd = cutoffDay.isAfter(through) ? cutoffDay : through;
            dates.removeIf(
                    day -> day.isAfter(sourceEnd) || day.isBefore(sourceEnd.minusDays(3660)));
            final var pending = state.putObject("relatedPending");
            pending.put("from", window.query().startInclusive().toString())
                    .put("through", window.query().endInclusive().toString())
                    .put("commitThrough", window.commitThrough().toString())
                    .put("scopeEnd", through.toString())
                    .put("sourceEnd", sourceEnd.toString())
                    .put("floor", floor.toString())
                    .put(
                            "serviceFloor",
                            dates.isEmpty() ? sourceEnd.toString() : dates.first().toString())
                    .put("emptyReferences", dates.isEmpty())
                    .put("freightsDone", false)
                    .put("freightNext", 0)
                    .put("occurrencesDone", template == ReportSnapshotTemplate.OCORRENCIAS)
                    .put("locationNext", 0);
            pending.putArray("dates");
            final var freightRanges = pending.putArray("freightRanges");
            dates.forEach(
                    day ->
                            freightRanges
                                    .addObject()
                                    .put("start", day.toString())
                                    .put("end", day.toString()));
            final var ranges = pending.putArray("locationRanges");
            while (!dates.isEmpty()) {
                final var start = dates.first();
                var end = start;
                while (!dates.isEmpty() && !dates.first().isAfter(start.plusDays(7))) {
                    end = dates.pollFirst();
                }
                ranges.addObject().put("start", start.toString()).put("end", end.toString());
            }
            save.run();
        }
        final var pending = (ObjectNode) state.required("relatedPending");
        final var end = LocalDate.parse(pending.required("scopeEnd").asText());
        final var workFloor = LocalDate.parse(pending.required("floor").asText());
        final var scope =
                new BusinessDateRange(
                        LocalDate.parse(pending.required("serviceFloor").asText()),
                        LocalDate.parse(pending.required("sourceEnd").asText()));
        final var updates =
                new SourceDateTimeRange(
                        Instant.parse(pending.required("from").asText()),
                        Instant.parse(pending.required("through").asText()));
        if (!pending.path("freightsDone").asBoolean()) {
            addKeyDates(template, workFloor, end, Set.of(), pending);
            if (!pending.has("freightRanges")) {
                final var previous = pending.required("locationRanges");
                final int confirmed = pending.path("freightNext").asInt();
                require(
                        confirmed >= 0 && confirmed <= previous.size(),
                        "RELATED_FREIGHT_CURSOR_BOUND");
                final var lastConfirmed =
                        confirmed == 0
                                ? null
                                : LocalDate.parse(
                                        previous.get(confirmed - 1).required("end").asText());
                final var rebuilt = pending.putArray("freightRanges");
                final var sourceEnd = LocalDate.parse(pending.required("sourceEnd").asText());
                for (final var day : indexes.serviceDays(template, workFloor, end)) {
                    if ((lastConfirmed == null || day.isAfter(lastConfirmed))
                            && !day.isAfter(sourceEnd)
                            && !day.isBefore(sourceEnd.minusDays(3660))) {
                        rebuilt.addObject().put("start", day.toString()).put("end", day.toString());
                    }
                }
                pending.put("freightNext", 0);
                save.run();
            }
            final var freightRanges = pending.required("freightRanges");
            for (int next = pending.path("freightNext").asInt();
                    next < freightRanges.size();
                    next++) {
                final var range = freightRanges.get(next);
                final var dates =
                        new BusinessDateRange(
                                LocalDate.parse(range.required("start").asText()),
                                LocalDate.parse(range.required("end").asText()));
                final var keys = source(ReportSnapshotTemplate.FRETES, dates, updates).keys();
                addKeyDates(template, workFloor, end, keys, pending);
                pending.put("freightNext", next + 1);
                save.run();
            }
            pending.put("freightsDone", true);
            save.run();
        }
        if (!pending.path("occurrencesDone").asBoolean()) {
            final var keys =
                    pending.path("emptyReferences").asBoolean()
                            ? Set.<String>of()
                            : source(ReportSnapshotTemplate.OCORRENCIAS, scope, updates).keys();
            addKeyDates(template, workFloor, end, keys, pending);
            pending.put("occurrencesDone", true);
            save.run();
        }
        final var ranges = pending.required("locationRanges");
        for (int next = pending.required("locationNext").asInt(); next < ranges.size(); next++) {
            boolean entirePeriodQueued = true;
            final var queued = queued(pending);
            for (var day = workFloor; !day.isAfter(end); day = day.plusDays(1)) {
                if (!queued.contains(day)) {
                    entirePeriodQueued = false;
                    break;
                }
            }
            if (entirePeriodQueued) {
                // Additional lookups cannot select another date. Each queued date still
                // requires a complete mapped snapshot and exact SQL confirmation below.
                operations
                        .addObject()
                        .put("entity", template.name())
                        .put("phase", "RELATED_COMPLETE_PERIOD_QUEUED")
                        .put("remainingLocationRangesNotNeeded", ranges.size() - next)
                        .put("acceptedAsCompleteSnapshot", false);
                pending.put("locationNext", ranges.size());
                save.run();
                break;
            }
            final var range = ranges.get(next);
            final var dates =
                    new BusinessDateRange(
                            LocalDate.parse(range.required("start").asText()),
                            LocalDate.parse(range.required("end").asText()));
            final var locations = source(ReportSnapshotTemplate.LOCALIZACAO_CARGAS, dates, null);
            addLocationDates(
                    template,
                    workFloor,
                    end,
                    dates,
                    locations.locations(),
                    locations.unassignedLocation(),
                    pending);
            pending.put("locationNext", next + 1);
            save.run();
        }
        final var dates = (ArrayNode) pending.required("dates");
        for (int count = 0; count < replacementLimit && !dates.isEmpty(); count++) {
            replace.replace(LocalDate.parse(dates.get(0).asText()));
            dates.remove(0);
            save.run();
        }
        if (!dates.isEmpty()) {
            return false;
        }
        state.put("relatedWatermark", pending.required("commitThrough").asText());
        state.remove("relatedPending");
        save.run();
        return true;
    }

    private Source source(
            final ReportSnapshotTemplate template,
            final BusinessDateRange scope,
            final SourceDateTimeRange updates)
            throws Exception {
        final var keys = new HashSet<String>();
        final var locations = new HashMap<String, Set<String>>();
        final var characters = new long[] {0};
        final var projected = new long[] {0};
        final var unassigned = new long[] {0};
        final var operation =
                operations
                        .addObject()
                        .put("entity", template.name())
                        .put(
                                "phase",
                                updates == null
                                        ? "RELATED_LOCATION_SCAN"
                                        : "RELATED_UPDATE_DISCOVERY")
                        .put("scopeStart", scope.startInclusive().toString())
                        .put("scopeEnd", scope.endInclusive().toString())
                        .put("acceptedAsCompleteSnapshot", false);
        final var file = root.resolve(template.name() + "-related-" + UUID.randomUUID() + ".jsonl");
        try (var output =
                Files.newBufferedWriter(
                        file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            final var result =
                    capture.capture(
                            template,
                            scope,
                            updates,
                            wire,
                            (id, ordinal, row, observation) -> {
                                final var envelope =
                                        CurlSnapshotWire.JSON
                                                .createObjectNode()
                                                .put("ordinal", ordinal);
                                envelope.set("record", row);
                                envelope.set(
                                        "observation",
                                        CurlSnapshotWire.JSON.valueToTree(observation));
                                final var text = envelope.toString();
                                characters[0] += text.length();
                                require(characters[0] <= 40000000, "RELATED_CAPTURE_SIZE_BOUND");
                                output.write(text);
                                output.newLine();
                                final boolean occurrences =
                                        template == ReportSnapshotTemplate.OCORRENCIAS;
                                final var draft =
                                        FreightDependencyIndex.number(
                                                row.path(
                                                        occurrences
                                                                ? "ice_f_e_fit_corporation_sequence_number"
                                                                : "corporation_sequence_number"));
                                final var cte =
                                        FreightDependencyIndex.number(
                                                row.path(
                                                        occurrences
                                                                ? "ice_fit_fhe_cte_number"
                                                                : "fit_fhe_cte_number"));
                                if (draft != null) {
                                    keys.add("D:" + draft);
                                }
                                if (cte != null) {
                                    keys.add("C:" + cte);
                                }
                                if (template == ReportSnapshotTemplate.LOCALIZACAO_CARGAS) {
                                    final var day =
                                            FreightDependencyIndex.serviceDay(
                                                    row.required("service_at"));
                                    final var location =
                                            FreightDependencyIndex.location(
                                                    row.required("fit_fln_cln_nickname"));
                                    require(day != null, "RELATED_REFERENCE_SERVICE_DATE");
                                    projected[0]++;
                                    if (draft == null) {
                                        // Retain the row and refresh every matching service date:
                                        // no identity is invented for an unassignable reference.
                                        unassigned[0]++;
                                    } else {
                                        locations
                                                .computeIfAbsent(
                                                        draft + ":" + day,
                                                        ignored -> new HashSet<>())
                                                .add(location);
                                    }
                                }
                            },
                            new ReportSnapshotExtractor.Limits(
                                    200,
                                    template == ReportSnapshotTemplate.FRETES ? 25 : 100,
                                    100000));
            final boolean locationProjection =
                    template == ReportSnapshotTemplate.LOCALIZACAO_CARGAS;
            require(
                    result.terminal()
                            && result.fields() == new ReportMetadataCatalog().fieldCount(template)
                            && (locationProjection
                                    ? projected[0] == result.rows()
                                            && result.typedRows() + result.rejectedRows()
                                                    == result.rows()
                                    : result.success() && result.typedRows() == result.rows()),
                    "RELATED_SOURCE_INCOMPLETE");
            operation
                    .put("rows", result.rows())
                    .put("terminal", result.terminal())
                    .put("typedRows", result.typedRows())
                    .put("legacyRejectedRows", result.rejectedRows());
            if (locationProjection) {
                // Auxiliary lookup only; never publishes a legacy quarantined entity row.
                operation
                        .put("referenceProjectionRows", projected[0])
                        .put("unassignedReferenceRows", unassigned[0]);
            }
            return new Source(keys, locations, unassigned[0] > 0);
        }
    }

    private void addKeyDates(
            final ReportSnapshotTemplate template,
            final LocalDate floor,
            final LocalDate through,
            final Set<String> keys,
            final ObjectNode pending)
            throws Exception {
        final var dates = queued(pending);
        for (var day = floor; !day.isAfter(through); day = day.plusDays(1)) {
            for (final var reference : indexes.read(template, day).required("references")) {
                if (unsafe(reference, LocalDate.parse(pending.required("sourceEnd").asText()))
                        || (reference.hasNonNull("draft")
                                && keys.contains("D:" + reference.path("draft").asText()))
                        || (reference.hasNonNull("cte")
                                && keys.contains("C:" + reference.path("cte").asText()))) {
                    dates.add(day);
                    break;
                }
            }
        }
        putDates(pending, dates);
    }

    private void addLocationDates(
            final ReportSnapshotTemplate template,
            final LocalDate floor,
            final LocalDate through,
            final BusinessDateRange range,
            final Map<String, Set<String>> locations,
            final boolean unassigned,
            final ObjectNode pending)
            throws Exception {
        final var dates = queued(pending);
        for (var day = floor; !day.isAfter(through); day = day.plusDays(1)) {
            for (final var reference : indexes.read(template, day).required("references")) {
                if (unsafe(reference, LocalDate.parse(pending.required("sourceEnd").asText()))) {
                    dates.add(day);
                    break;
                }
                final var service = LocalDate.parse(reference.required("serviceDay").asText());
                if (service.isBefore(range.startInclusive())
                        || service.isAfter(range.endInclusive())) {
                    continue;
                }
                final var current =
                        locations.get(reference.required("draft").asText() + ":" + service);
                if (unassigned
                        || current == null
                        || (template == ReportSnapshotTemplate.OCORRENCIAS
                                && (current.size() != 1
                                        || !current.contains(
                                                FreightDependencyIndex.location(
                                                        reference.required("location")))))) {
                    dates.add(day);
                    break;
                }
            }
        }
        putDates(pending, dates);
    }

    private static boolean unsafe(final JsonNode reference, final LocalDate through) {
        if (!reference.hasNonNull("draft") || !reference.hasNonNull("serviceDay")) {
            return true;
        }
        final var day = LocalDate.parse(reference.required("serviceDay").asText());
        return day.isAfter(through) || day.isBefore(through.minusDays(3660));
    }

    private static TreeSet<LocalDate> queued(final ObjectNode pending) {
        final var dates = new TreeSet<LocalDate>();
        pending.required("dates").forEach(day -> dates.add(LocalDate.parse(day.asText())));
        return dates;
    }

    private static void putDates(final ObjectNode pending, final Set<LocalDate> dates) {
        final var target = pending.putArray("dates");
        dates.forEach(day -> target.add(day.toString()));
    }

    private static void require(final boolean accepted, final String reason) {
        if (!accepted) {
            throw new IllegalStateException(reason);
        }
    }
}
