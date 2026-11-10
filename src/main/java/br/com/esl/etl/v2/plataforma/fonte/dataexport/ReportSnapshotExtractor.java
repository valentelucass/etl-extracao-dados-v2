package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** One page in memory, literal empty terminal, no deduplication and no publication side effects. */
public final class ReportSnapshotExtractor {
    public interface Wire {
        JsonNode info(ReportSnapshotTemplate template) throws Exception;

        JsonNode page(ReportSnapshotTemplate template, BusinessDateRange dates, int page, int per)
                throws Exception;

        default DataExportRecordInput recordInput(final JsonNode row, final int index) {
            return new DataExportRecordInput(row, java.util.Optional.empty());
        }
    }

    @FunctionalInterface
    public interface Sink {
        void accept(UUID capture, long ordinal, JsonNode row, ExpansionObservation observation)
                throws Exception;
    }

    public record Limits(int pages, int per, long rows) {
        public Limits {
            if (pages < 1 || pages > 200 || per < 1 || per > 100 || rows < 1 || rows > 100000) {
                throw new IllegalArgumentException("SNAPSHOT_LIMITS");
            }
        }
    }

    public record Result(
            UUID capture,
            long rows,
            long typedRows,
            long rejectedRows,
            int fields,
            int pages,
            boolean terminal,
            boolean currentDay) {
        public boolean success() {
            return terminal && rejectedRows == 0;
        }
    }

    public Result extract(
            final ReportSnapshotTemplate template,
            final BusinessDateRange dates,
            final Wire wire,
            final Sink sink,
            final Function<JsonNode, ? extends ExpansionObservation> mapper,
            final Limits limits)
            throws Exception {
        return extractRecords(
                template,
                dates,
                wire,
                sink,
                mapper == null ? null : input -> mapper.apply(input.value()),
                limits);
    }

    /**
     * Supplies literal record slices when the transport retained them; never reconstructs wire
     * JSON.
     */
    public Result extractRecords(
            final ReportSnapshotTemplate template,
            final BusinessDateRange dates,
            final Wire wire,
            final Sink sink,
            final Function<DataExportRecordInput, ? extends ExpansionObservation> mapper,
            final Limits limits)
            throws Exception {
        java.util.Objects.requireNonNull(template);
        java.util.Objects.requireNonNull(dates);
        java.util.Objects.requireNonNull(wire);
        java.util.Objects.requireNonNull(sink);
        java.util.Objects.requireNonNull(limits);
        final var info = unwrap(wire.info(template));
        final var fields = validateInfo(template, info);
        final UUID capture = UUID.randomUUID();
        long rows = 0;
        long typed = 0;
        long rejected = 0;
        for (int page = 1; page <= limits.pages(); page++) {
            final var response = wire.page(template, dates, page, limits.per());
            final var data = unwrap(response);
            require(data != null && data.isArray(), "SNAPSHOT_DATA_ARRAY");
            if (data.isEmpty()) {
                return new Result(
                        capture,
                        rows,
                        typed,
                        rejected,
                        fields.size(),
                        page,
                        true,
                        !dates.endInclusive()
                                .isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo"))));
            }
            require(rows + data.size() <= limits.rows(), "SNAPSHOT_ROW_BUDGET");
            validatePagination(template, data, limits.per());
            int recordIndex = 0;
            for (final var row : data) {
                MappedReportFields.requireExact(row, fields);
                requireDate(template, dates, row);
                rows++;
                ExpansionObservation observation = null;
                if (mapper != null) {
                    try {
                        observation = mapper.apply(wire.recordInput(row, recordIndex));
                        if (observation == null || !observation.valid()) {
                            rejected++;
                        } else {
                            typed++;
                        }
                    } catch (final IllegalArgumentException invalid) {
                        rejected++;
                    }
                }
                sink.accept(capture, rows, row, observation);
                recordIndex++;
            }
        }
        return new Result(
                capture,
                rows,
                typed,
                rejected,
                fields.size(),
                limits.pages(),
                false,
                !dates.endInclusive().isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo"))));
    }

    private static Set<String> validateInfo(
            final ReportSnapshotTemplate template, final JsonNode info) throws Exception {
        require(
                info != null
                        && info.isObject()
                        && info.path("fields").isObject()
                        && info.path("filters").isArray(),
                "SNAPSHOT_INFO_SHAPE");
        final var fields = new HashSet<String>();
        info.path("fields").fieldNames().forEachRemaining(fields::add);
        require(!fields.isEmpty() && fields.size() <= 512, "SNAPSHOT_INFO_FIELDS");
        final var filters = new HashSet<String>();
        for (final var filter : info.path("filters")) {
            require(
                    filter.path("table").isTextual() && filter.path("field").isTextual(),
                    "SNAPSHOT_INFO_FILTER_SHAPE");
            require(
                    filters.add(
                            filter.path("table").textValue()
                                    + "."
                                    + filter.path("field").textValue()),
                    "SNAPSHOT_DUPLICATE_FILTER");
        }
        final var path = template.template().businessDateFilter();
        require(filters.contains(path.root() + "." + path.field()), "SNAPSHOT_BUSINESS_FILTER");
        if (template.contractResource() != null) {
            try (var resource =
                    ReportSnapshotExtractor.class
                            .getClassLoader()
                            .getResourceAsStream(template.contractResource())) {
                require(resource != null, "SNAPSHOT_CONTRACT_RESOURCE");
                final var contract = new ObjectMapper().readTree(resource);
                final var expected = new HashSet<String>();
                for (final var field : contract.path("fields")) {
                    expected.add(field.path("name").asText());
                }
                require(fields.equals(expected), "SNAPSHOT_INFO_FIELD_DRIFT");
                final var expectedFilters = new HashSet<String>();
                for (final var filter : contract.path("filters")) {
                    expectedFilters.add(
                            filter.path("table").asText() + "." + filter.path("field").asText());
                    boolean typeMatched = false;
                    for (final var actualFilter : info.path("filters")) {
                        if (actualFilter.path("table").equals(filter.path("table"))
                                && actualFilter.path("field").equals(filter.path("field"))) {
                            typeMatched = actualFilter.path("type").equals(filter.path("type"));
                        }
                    }
                    require(typeMatched, "SNAPSHOT_INFO_FILTER_TYPE_DRIFT");
                }
                require(
                        filters.equals(expectedFilters)
                                && info.path("required_date_filters")
                                        .equals(contract.path("requiredDateFilters"))
                                && info.path("default_order").equals(contract.path("defaultOrder")),
                        "SNAPSHOT_INFO_SEMANTIC_DRIFT");
            }
        }
        return Set.copyOf(fields);
    }

    private static void requireDate(
            final ReportSnapshotTemplate template,
            final BusinessDateRange dates,
            final JsonNode row) {
        if (template.rowDate() == null) {
            // Native91 exposes created_at, not the service_date used by this filter.
            require(template == ReportSnapshotTemplate.MANIFESTOS, "SNAPSHOT_DATE_CONTRACT");
            return;
        }
        final var value = row.path(template.rowDate());
        require(value.isTextual(), "SNAPSHOT_BUSINESS_DATE_REQUIRED");
        final String text = value.textValue();
        final LocalDate date;
        if (text.length() == 10) {
            date = LocalDate.parse(text);
        } else {
            LocalDate parsed;
            try {
                parsed =
                        OffsetDateTime.parse(text)
                                .atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
                                .toLocalDate();
            } catch (final java.time.format.DateTimeParseException local) {
                require(template.contractResource() == null, "SNAPSHOT_OFFSET_REQUIRED");
                parsed = java.time.LocalDateTime.parse(text.replace(' ', 'T')).toLocalDate();
            }
            date = parsed;
        }
        require(
                !date.isBefore(dates.startInclusive()) && !date.isAfter(dates.endInclusive()),
                "SNAPSHOT_DATE_OUTSIDE_REQUEST");
    }

    private static void validatePagination(
            final ReportSnapshotTemplate template, final JsonNode data, final int per) {
        if (data.size() > per) {
            final String key =
                    switch (template) {
                        case COLETAS, FRETES -> "id";
                        case MANIFESTOS, COTACOES, INVENTARIO, SINISTROS -> "sequence_code";
                        default -> null;
                    };
            if (key == null) {
                // Physical capture has an independent row ceiling; unknown roots cannot prove per.
                return;
            }
            final var identities = new HashSet<String>();
            for (final var row : data) {
                final var id = row.path(key);
                require(id.isTextual() || id.isNumber(), "SNAPSHOT_PAGINATION_ID_REQUIRED");
                identities.add(
                        id.isNumber()
                                ? id.decimalValue().toBigIntegerExact().toString()
                                : id.textValue());
            }
            require(identities.size() <= per, "SNAPSHOT_ENTITY_PAGE_BOUND");
        }
    }

    private static JsonNode unwrap(final JsonNode value) {
        if (value != null && value.isObject()) {
            require(!value.has("error") && !value.has("errors"), "SNAPSHOT_ERROR_ENVELOPE");
        }
        return value != null && value.isObject() && value.has("data") ? value.get("data") : value;
    }

    private static void require(final boolean valid, final String reason) {
        if (!valid) {
            throw new IllegalArgumentException(reason);
        }
    }
}
