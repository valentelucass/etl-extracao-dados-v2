package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog.Column;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Frozen independent literals and closed synthetic rules. No SQL expression can enter an oracle.
 */
public final class QualificationOracles {
    public enum Rule {
        LITERAL,
        VARIANT_LITERAL,
        CONTEXT,
        OBSERVED_TIME,
        STRUCTURED_LINEAGE
    }

    public enum Count {
        TWO_COMPONENTS_PER_ROOT,
        ONE_PER_ROOT,
        CONFIRMED_ABSENCE,
        MONITOR_EVENTS,
        BRANCHES,
        CLIENTS,
        VEHICLES,
        DRIVERS,
        ACCOUNTS
    }

    public enum Selector {
        USER_NAME,
        ROOT,
        ALLOCATION,
        BRANCH,
        COMPONENT_ID,
        COMPONENT_SEQUENCE,
        INCIDENT_NUMBER,
        MANIFEST_ID,
        MANIFEST_DEPARTURE,
        MANIFEST_FINISH,
        MDFE_KEY,
        COLLECTION_NUMBER,
        DIMENSION_NAME,
        VEHICLE_PLATE,
        VEHICLE_TYPE,
        DRIVER_BRANCH,
        ACCOUNT_CLASS,
        USER_ID,
        QUOTE_SEQUENCE,
        COLLECTION_ID,
        COLLECTION_STATUS,
        COLLECTION_ABSENT,
        BRANCH_ID,
        RAW_BRANCH,
        ABSENT_COLLECTION_ID,
        ABSENT_COLLECTION_NUMBER,
        ABSENCE_CONFIRMATION,
        RASTER_SEQUENCE,
        EVENT_ID,
        EVENT_DURATION,
        EVENT_DATE,
        EVENT_STATE,
        EVENT_ROWS,
        EVENT_ERROR_CATEGORY,
        EVENT_ERROR_MESSAGE,
        FREIGHT_ID,
        FREIGHT_SEQUENCE,
        BRANCH_KEY,
        FREIGHT_FORECAST,
        PERFORMANCE_DAYS,
        FREIGHT_KM,
        FREIGHT_ISSUING_BRANCH,
        LOCATION_HASH
    }

    public record Cell(Column column, Rule rule, JsonNode value, String origin, String example) {}

    public record Output(AnalyticSqlContract contract, Count count, List<Cell> cells) {
        public Output {
            if (contract == null || cells == null || cells.size() != contract.columns()) {
                throw new IllegalArgumentException("QUAL_ORACLE_CELLS_BOUND");
            }
            cells = List.copyOf(cells);
        }
    }

    /**
     * Technical/control context is supplied by the capture, never seeded with selected query rows.
     */
    public interface Evidence {
        List<AnalyticSqlValue> monitor(long row);

        int monitorCount();

        boolean lineage(String entity, int root, int component, JsonNode actual);

        String locationHash(int root);
    }

    public record Context(
            UUID run,
            int roots,
            int sourceRevision,
            boolean correction,
            int absenceStage,
            UUID confirmation,
            Instant started,
            Instant finished,
            Evidence evidence,
            AnalyticScenarioVariant variant) {
        public Context(
                final UUID run,
                final int roots,
                final int sourceRevision,
                final boolean correction,
                final int absenceStage,
                final UUID confirmation,
                final Instant started,
                final Instant finished,
                final Evidence evidence) {
            this(
                    run,
                    roots,
                    sourceRevision,
                    correction,
                    absenceStage,
                    confirmation,
                    started,
                    finished,
                    evidence,
                    AnalyticScenarioVariant.BASELINE);
        }

        public Context {
            Objects.requireNonNull(variant);
            Objects.requireNonNull(run);
            Objects.requireNonNull(started);
            Objects.requireNonNull(finished);
            Objects.requireNonNull(evidence);
            if (roots < 2
                    || roots > 32
                    || sourceRevision < 1
                    || sourceRevision > 1000
                    || absenceStage < 0
                    || absenceStage > 2
                    || finished.isBefore(started)
                    || finished.isAfter(started.plusSeconds(300))) {
                throw new IllegalArgumentException("QUAL_ORACLE_CONTEXT");
            }
        }
    }

    private final Map<AnalyticSqlContract, Output> outputs;

    private QualificationOracles(final Map<AnalyticSqlContract, Output> outputs) {
        this.outputs = Map.copyOf(outputs);
    }

    public static QualificationOracles read(final Path path) throws IOException {
        return parse(QualificationJson.read(path, 524288));
    }

    public static QualificationOracles parse(final JsonNode root) {
        QualificationJson.fields(root, "version", "origin", "contracts");
        if (!"qualification-oracles-v1".equals(QualificationJson.text(root, "version", 40))
                || !"INDEPENDENT_SYNTHETIC_RULES_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN_VERSION");
        }
        QualificationJson.array(root.path("contracts"), 19, 19);
        final var outputs = new EnumMap<AnalyticSqlContract, Output>(AnalyticSqlContract.class);
        final var origins = new QualificationOracleOrigins();
        for (final var item : root.path("contracts")) {
            QualificationJson.fields(item, "id", "count", "columns");
            final var contract =
                    AnalyticSqlContract.valueOf(
                            QualificationJson.text(item, "id", 6).replace('-', '_'));
            final var count = Count.valueOf(QualificationJson.text(item, "count", 32));
            QualificationJson.array(item.path("columns"), contract.columns(), contract.columns());
            final var cells = new ArrayList<Cell>();
            for (final var cell : item.path("columns")) {
                QualificationJson.fields(
                        cell,
                        "ordinal",
                        "name",
                        "type",
                        "precision",
                        "scale",
                        "nullable",
                        "rule",
                        "value",
                        "origin",
                        "example");
                final var column =
                        new Column(
                                QualificationJson.number(cell, "ordinal", 1, 123),
                                QualificationJson.text(cell, "name", 128),
                                QualificationJson.text(cell, "type", 24),
                                QualificationJson.number(cell, "precision", 0, 38),
                                QualificationJson.number(cell, "scale", 0, 38),
                                QualificationJson.flag(cell, "nullable"));
                if (!column.equals(AnalyticSqlCatalog.columns(contract).get(cells.size()))) {
                    throw new IllegalArgumentException("QUAL_ORACLE_COLUMN_CONTRACT");
                }
                final var rule = Rule.valueOf(QualificationJson.text(cell, "rule", 32));
                final var value = cell.get("value");
                if (rule == Rule.CONTEXT) {
                    Selector.valueOf(QualificationJson.text(cell, "value", 40));
                } else if (rule == Rule.LITERAL) {
                    typed(column, value.isNull() ? null : value.asText());
                    if (value.isContainerNode()) {
                        throw new IllegalArgumentException("QUAL_ORACLE_LITERAL_TYPE");
                    }
                } else if (rule == Rule.VARIANT_LITERAL) {
                    QualificationJson.fields(value, "BASELINE", "VALUES_AND_NULLS");
                    for (final var variant : AnalyticScenarioVariant.values()) {
                        final var literal = value.path(variant.name());
                        if (literal.isContainerNode()) {
                            throw new IllegalArgumentException("QUAL_ORACLE_LITERAL_TYPE");
                        }
                        typed(column, literal.isNull() ? null : literal.asText());
                    }
                } else if (rule == Rule.OBSERVED_TIME && !"CASE_INTERVAL".equals(value.asText())) {
                    throw new IllegalArgumentException("QUAL_ORACLE_TECHNICAL_RULE");
                } else if (rule == Rule.STRUCTURED_LINEAGE
                        && !Set.of("CAP", "FAT", "INV", "SIN", "MAN", "COL", "COT", "FRE", "LOC")
                                .contains(value.asText())) {
                    throw new IllegalArgumentException("QUAL_ORACLE_LINEAGE_SCOPE");
                }
                cells.add(
                        new Cell(
                                column,
                                rule,
                                value.deepCopy(),
                                origins.verify(QualificationJson.text(cell, "origin", 256)),
                                QualificationJson.text(cell, "example", 384)));
            }
            if (outputs.put(contract, new Output(contract, count, cells)) != null) {
                throw new IllegalArgumentException("QUAL_ORACLE_DUPLICATE_OUTPUT");
            }
        }
        if (outputs.size() != 19) {
            throw new IllegalArgumentException("QUAL_ORACLE_INCOMPLETE");
        }
        return new QualificationOracles(outputs);
    }

    public QualificationComparator.ExpectedRows expected(
            final AnalyticSqlContract contract, final Context context) {
        return new Rows(outputs.get(Objects.requireNonNull(contract)), context);
    }

    private static final class Rows implements QualificationComparator.ExpectedRows {
        private final Output output;
        private final Context context;
        private final List<Integer> roots;

        Rows(final Output output, final Context context) {
            this.output = output;
            this.context = context;
            final boolean lexical =
                    output.count() == Count.TWO_COMPONENTS_PER_ROOT
                            || output.contract() == AnalyticSqlContract.SQL_08
                            || output.contract() == AnalyticSqlContract.SQL_09
                            || output.contract() == AnalyticSqlContract.SQL_13
                            || output.contract() == AnalyticSqlContract.SQL_19;
            roots =
                    IntStream.rangeClosed(1, context.roots())
                            .boxed()
                            .sorted(
                                    lexical
                                            ? Comparator.<Integer, String>comparing(
                                                    value ->
                                                            Integer.toString(
                                                                    output.contract()
                                                                                    == AnalyticSqlContract
                                                                                            .SQL_19
                                                                            ? value - 1
                                                                            : value))
                                            : Comparator.naturalOrder())
                            .toList();
        }

        @Override
        public long count() {
            return switch (output.count()) {
                case TWO_COMPONENTS_PER_ROOT -> 2L * context.roots();
                case ONE_PER_ROOT -> context.roots();
                case CONFIRMED_ABSENCE -> context.absenceStage() == 2 ? 1 : 0;
                case BRANCHES, CLIENTS -> 6;
                case VEHICLES -> 9;
                case DRIVERS, ACCOUNTS -> 3;
                case MONITOR_EVENTS -> context.evidence().monitorCount();
            };
        }

        private int root(final long row) {
            return roots.get(
                    (int) (output.count() == Count.TWO_COMPONENTS_PER_ROOT ? row / 2 : row));
        }

        private int component(final long row) {
            return output.count() == Count.TWO_COMPONENTS_PER_ROOT ? (int) (row % 2) + 1 : 1;
        }

        @Override
        public List<AnalyticSqlValue> at(final long row) {
            if (row < 0 || row >= count()) {
                throw new IllegalArgumentException("QUAL_ORACLE_ORDINAL");
            }
            if (output.contract() == AnalyticSqlContract.SQL_10) {
                return context.evidence().monitor(row);
            }
            final var result = new ArrayList<AnalyticSqlValue>(output.cells().size());
            for (final var cell : output.cells()) {
                final String text =
                        switch (cell.rule()) {
                            case LITERAL -> cell.value().isNull() ? null : cell.value().asText();
                            case VARIANT_LITERAL ->
                                    cell.value().path(context.variant().name()).isNull()
                                            ? null
                                            : cell.value().path(context.variant().name()).asText();
                            case CONTEXT -> select(Selector.valueOf(cell.value().asText()), row);
                            case STRUCTURED_LINEAGE -> "{}";
                            case OBSERVED_TIME ->
                                    context.started().atOffset(ZoneOffset.UTC).toString();
                        };
                result.add(typed(cell.column(), text));
            }
            return List.copyOf(result);
        }

        private String select(final Selector selector, final long row) {
            final var contract = output.contract();
            final long dimensionIndex =
                    switch (output.count()) {
                        case BRANCHES, CLIENTS, VEHICLES, DRIVERS, ACCOUNTS -> row / 3;
                        default -> row;
                    };
            final int index = Math.toIntExact(dimensionIndex);
            return switch (selector) {
                case USER_NAME ->
                        context.variant() == AnalyticScenarioVariant.VALUES_AND_NULLS
                                        && root(row) == 1
                                ? null
                                : "USUÁRIO SINTÉTICO";
                case ROOT -> Integer.toString(root(row));
                case ALLOCATION -> component(row) == 1 ? "40.00000000" : "60.00000000";
                case BRANCH ->
                        contract == AnalyticSqlContract.SQL_12
                                        || contract == AnalyticSqlContract.SQL_07
                                        || contract == AnalyticSqlContract.SQL_02
                                        || context.correction()
                                                && (contract == AnalyticSqlContract.SQL_08
                                                        || contract == AnalyticSqlContract.SQL_09)
                                ? "SYNTHETIC BRANCH B"
                                : "SYNTHETIC BRANCH A";
                case BRANCH_KEY -> "synthetic-branch-b";
                case RAW_BRANCH -> " SYNTHETIC BRANCH A ";
                case BRANCH_ID -> "7";
                case COMPONENT_ID ->
                        context.run().toString().toUpperCase(java.util.Locale.ROOT)
                                + "/STRING:"
                                + switch (contract) {
                                    case SQL_01 -> "FAT";
                                    case SQL_06 -> "CAP";
                                    case SQL_11 -> "INV";
                                    case SQL_12 -> "SIN";
                                    default ->
                                            throw new IllegalArgumentException(
                                                    "QUAL_ORACLE_COMPONENT_SCOPE");
                                }
                                + "-root-"
                                + root(row)
                                + "/STRING:part-"
                                + root(row)
                                + "/STRING:component-"
                                + component(row);
                case COMPONENT_SEQUENCE -> Integer.toString(root(row) * 10 + component(row));
                case INCIDENT_NUMBER -> "synthetic-occurrence-" + component(row);
                case MANIFEST_ID ->
                        context.run().toString().toUpperCase(java.util.Locale.ROOT)
                                + "/INTEGER:"
                                + root(row);
                case MANIFEST_DEPARTURE ->
                        context.correction()
                                ? "2036-04-02T09:00:00-03:00"
                                : "2036-04-01T09:00:00.1234567-03:00";
                case MANIFEST_FINISH ->
                        "2036-04-0"
                                + (context.correction() ? 2 : 1)
                                + "T09:00:00."
                                + String.format(
                                        java.util.Locale.ROOT, "%09d", context.sourceRevision())
                                + "-03:00";
                case MDFE_KEY ->
                        "1".repeat(40) + String.format(java.util.Locale.ROOT, "%04d", root(row));
                case COLLECTION_NUMBER -> Integer.toString(100000 + root(row));
                case COLLECTION_ID -> Integer.toString(200000 + root(row));
                case QUOTE_SEQUENCE -> Integer.toString(9999 + root(row));
                case FREIGHT_ID -> Integer.toString(300000 + root(row));
                case FREIGHT_SEQUENCE -> Integer.toString(600000 + root(row));
                case RASTER_SEQUENCE -> Integer.toString(10000 + root(row));
                case COLLECTION_STATUS ->
                        context.absenceStage() > 0 && root(row) == 1 ? "Excluída" : "Pendente";
                case COLLECTION_ABSENT ->
                        Boolean.toString(context.absenceStage() == 2 && root(row) == 1);
                case ABSENT_COLLECTION_ID -> "200001";
                case ABSENT_COLLECTION_NUMBER -> "100001";
                case ABSENCE_CONFIRMATION ->
                        Objects.requireNonNull(context.confirmation()).toString();
                case DIMENSION_NAME ->
                        switch (contract) {
                            case SQL_14 -> index == 0 ? "SYNTHETIC BRANCH A" : "SYNTHETIC BRANCH B";
                            case SQL_15 -> "SYNTHETIC CLIENT";
                            case SQL_17 -> index < 2 ? "SYNTHETIC DRIVER" : "MOTORISTA SYNTHETIC";
                            case SQL_18 ->
                                    index == 0
                                            ? "SYNTHETIC ACCOUNT"
                                            : "SYNTHETIC UNCLASSIFIED ACCOUNT";
                            default ->
                                    throw new IllegalArgumentException(
                                            "QUAL_ORACLE_DIMENSION_SCOPE");
                        };
                case VEHICLE_PLATE -> "SYN000" + (index + 1);
                case VEHICLE_TYPE -> index == 0 ? "TRATOR" : "REBOQUE";
                case DRIVER_BRANCH -> index == 1 ? "SYNTHETIC BRANCH B" : "SYNTHETIC BRANCH A";
                case ACCOUNT_CLASS -> index == 0 ? "Custos variáveis" : "SEM_CLASSIFICACAO";
                case USER_ID ->
                        "STRING:synthetic-analytic-" + context.run() + "-" + (root(row) - 1);
                case FREIGHT_FORECAST -> context.correction() ? "2036-04-03" : "2036-04-02";
                case FREIGHT_ISSUING_BRANCH ->
                        context.correction() ? "SYNTHETIC BRANCH B" : "SYNTHETIC BRANCH A";
                case PERFORMANCE_DAYS -> context.correction() ? "-2" : "-1";
                case FREIGHT_KM ->
                        context.correction()
                                ? "25.25000000"
                                : context.sourceRevision() > 1 ? "18.12500000" : "12.12500000";
                case LOCATION_HASH -> context.evidence().locationHash(root(row));
                default -> throw new IllegalArgumentException("QUAL_ORACLE_EVENT_CONTEXT_REQUIRED");
            };
        }

        @Override
        public boolean equivalent(
                final long row,
                final Column column,
                final AnalyticSqlValue wanted,
                final AnalyticSqlValue actual) {
            if (output.contract() == AnalyticSqlContract.SQL_10) {
                return wanted.equals(actual);
            }
            final var cell = output.cells().get(column.ordinal() - 1);
            if (cell.rule() == Rule.OBSERVED_TIME) {
                final Instant observed =
                        actual instanceof AnalyticSqlValue.CivilDateTime time
                                ? time.value().toInstant(ZoneOffset.UTC)
                                : actual instanceof AnalyticSqlValue.OffsetDateTimeValue time
                                        ? time.value().toInstant()
                                        : null;
                return observed != null
                        && !observed.isBefore(context.started().minusMillis(1))
                        && !observed.isAfter(context.finished().plusMillis(1));
            }
            if (cell.rule() == Rule.STRUCTURED_LINEAGE
                    && actual instanceof AnalyticSqlValue.Text text) {
                try {
                    return context.evidence()
                            .lineage(
                                    cell.value().asText(),
                                    root(row),
                                    component(row),
                                    QualificationJson.parse(
                                            text.value().getBytes(StandardCharsets.UTF_8), 131072));
                } catch (final IOException | IllegalArgumentException invalid) {
                    return false;
                }
            }
            return wanted.equals(actual);
        }
    }

    static AnalyticSqlValue typed(final Column column, final String value) {
        if (value == null) {
            return new AnalyticSqlValue.Missing();
        }
        return switch (column.type()) {
            case "nvarchar", "varchar", "char" -> new AnalyticSqlValue.Text(value);
            case "decimal" ->
                    new AnalyticSqlValue.Decimal(
                            new BigDecimal(value)
                                    .setScale(column.scale(), RoundingMode.UNNECESSARY));
            case "bigint", "int", "smallint", "tinyint" ->
                    new AnalyticSqlValue.IntegerValue(Long.parseLong(value));
            case "bit" -> {
                if (!Set.of("true", "false", "0", "1").contains(value)) {
                    throw new IllegalArgumentException("QUAL_ORACLE_BOOLEAN");
                }
                yield new AnalyticSqlValue.Flag(value.equals("true") || value.equals("1"));
            }
            case "date" -> new AnalyticSqlValue.Date(LocalDate.parse(value));
            case "time" ->
                    new AnalyticSqlValue.Time(truncate(LocalTime.parse(value), column.scale()));
            case "datetime2" -> {
                final var time =
                        value.endsWith("Z") || value.matches(".*[+-][0-9]{2}:[0-9]{2}$")
                                ? OffsetDateTime.parse(value).toLocalDateTime()
                                : LocalDateTime.parse(value);
                yield new AnalyticSqlValue.CivilDateTime(
                        LocalDateTime.of(
                                time.toLocalDate(), truncate(time.toLocalTime(), column.scale())));
            }
            case "datetimeoffset" -> {
                final var time = OffsetDateTime.parse(value);
                yield new AnalyticSqlValue.OffsetDateTimeValue(
                        OffsetDateTime.of(
                                time.toLocalDate(),
                                truncate(time.toLocalTime(), column.scale()),
                                time.getOffset()));
            }
            case "uniqueidentifier" -> new AnalyticSqlValue.Identifier(UUID.fromString(value));
            default -> throw new IllegalArgumentException("QUAL_ORACLE_SQL_TYPE");
        };
    }

    private static LocalTime truncate(final LocalTime value, final int scale) {
        final int divisor = (int) Math.pow(10, 9 - scale);
        return value.withNano(value.getNano() / divisor * divisor);
    }
}
