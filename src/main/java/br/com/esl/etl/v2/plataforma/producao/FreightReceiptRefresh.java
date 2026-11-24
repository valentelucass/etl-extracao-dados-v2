package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Attachment events schedule complete freight reads. Only the freight report supplies proof. */
final class FreightReceiptRefresh {
    private static final int PAGE_SIZE = 100;
    private static final int PAGES_PER_BATCH = 16;
    private final ObjectNode state;
    private final ObjectNode pending;
    private final ObjectNode reads;

    FreightReceiptRefresh(final ObjectNode state) {
        this.state = state;
        pending = child(state, "pendingDays");
        reads = child(state, "readAt");
    }

    private static ObjectNode child(final ObjectNode parent, final String key) {
        return parent.has(key) ? (ObjectNode) parent.required(key) : parent.putObject(key);
    }

    void discover(
            final ReportSnapshotExtractor.Wire wire,
            final LocalDate floor,
            final LocalDate today,
            final Instant cutoff,
            final RelatedFreightRefresh.CheckedAction save)
            throws Exception {
        final ObjectNode window;
        if (state.has("window")) {
            window = (ObjectNode) state.required("window");
        } else {
            final var since =
                    state.has("through")
                            ? Instant.parse(state.required("through").asText())
                            : cutoff.minus(Duration.ofDays(1));
            final var next = ProductionRefreshPolicy.nextWindow(since, cutoff, false, 24);
            if (next == null) {
                return;
            }
            window = state.putObject("window");
            window.put("from", next.query().startInclusive().toString())
                    .put("to", next.commitThrough().toString())
                    .put("page", 1);
        }
        final var updates =
                new SourceDateTimeRange(
                        Instant.parse(window.required("from").asText()),
                        Instant.parse(window.required("to").asText()));
        // The ESL report requires the occurrence date as well as the update range.
        // Use the whole supported history so a backdated event can still signal a recent change.
        final var end =
                updates.endInclusive().atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
        final var earliest = floor.isAfter(end) ? end : floor;
        final var history =
                new BusinessDateRange(
                        earliest.isBefore(end.minusDays(3660)) ? end.minusDays(3660) : earliest,
                        end);
        for (int count = 0; count < PAGES_PER_BATCH; count++) {
            final int page = window.required("page").asInt();
            final var response = wire.receiptPage(history, updates, page, PAGE_SIZE);
            final var rows = response.has("data") ? response.required("data") : response;
            if (!rows.isArray() || rows.size() > PAGE_SIZE) {
                throw new IllegalStateException("FREIGHT_RECEIPT_PAGE_INVALID");
            }
            if (rows.isEmpty()) {
                state.put("through", window.required("to").asText());
                state.remove("window");
                save.run();
                return;
            }
            for (var row : rows) {
                observe(row, floor, today, updates.endInclusive());
            }
            if (page == 200) {
                // Continue dense source windows below the ESL page limit without skipping any
                // notices.
                final long seconds =
                        Duration.between(updates.startInclusive(), updates.endInclusive())
                                .getSeconds();
                if (seconds <= 1) {
                    throw new IllegalStateException("FREIGHT_RECEIPT_SOURCE_SECOND_OVERFULL");
                }
                window.put("to", updates.startInclusive().plusSeconds(seconds / 2).toString())
                        .put("page", 1);
                save.run();
                return;
            }
            window.put("page", page + 1);
            save.run();
        }
    }

    boolean observe(
            final JsonNode occurrence,
            final LocalDate floor,
            final LocalDate today,
            final Instant discoveryThrough) {
        if (!"114".equals(occurrence.path("ice_ore_code").asText())) {
            return false;
        }
        final var key = FreightDependencyIndex.serviceDay(occurrence.path("ice_fit_service_at"));
        if (key == null
                || key.compareTo(floor.toString()) < 0
                || key.compareTo(today.toString()) > 0) {
            return false;
        }
        // ESL filters by update time but need not return it. A backdated event can be new.
        Instant latest = discoveryThrough;
        for (final var field : List.of("occurrence_at", "created_at", "updated_at")) {
            final var time = sourceTime(occurrence.path(field));
            if (time != null && (latest == null || time.isAfter(latest))) {
                latest = time;
            }
        }
        if (latest == null
                || !newerThan(latest, reads.get(key))
                || !newerThan(latest, pending.get(key))) {
            return false;
        }
        pending.put(key, latest.toString());
        return true;
    }

    private static Instant sourceTime(final JsonNode value) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.textValue());
        } catch (final DateTimeParseException missingOffset) {
            try {
                return LocalDateTime.parse(value.textValue())
                        .atZone(ReportUpdateContract.SOURCE_ZONE)
                        .toInstant();
            } catch (final DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static boolean newerThan(final Instant value, final JsonNode old) {
        return old == null || value.isAfter(Instant.parse(old.textValue()));
    }

    void confirmedFreightRead(final LocalDate date, final Instant started) {
        final var day = date.toString();
        if (newerThan(started, reads.get(day))) {
            reads.put(day, started.toString());
        }
        if (pending.has(day)
                && !Instant.parse(pending.required(day).asText())
                        .isAfter(Instant.parse(reads.required(day).asText()))) {
            pending.remove(day);
        }
    }

    List<LocalDate> nextDays(final int limit) {
        final var days = new ArrayList<LocalDate>();
        final var queued = pending.fieldNames();
        while (queued.hasNext() && days.size() < limit) {
            days.add(LocalDate.parse(queued.next()));
        }
        return days;
    }

    int pendingCount() {
        return pending.size();
    }
}
