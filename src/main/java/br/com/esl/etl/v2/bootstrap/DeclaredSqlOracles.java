package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Literal expected tuples for all nineteen outputs, with narrowly scoped technical evidence. */
public final class DeclaredSqlOracles {
    private record Output(int count, List<PinnedLocalJson> files) {}

    private record Monitor(String selector, String entity, String family, int rows, String state) {}

    private final PinnedLocalJson manifest;
    private final Map<AnalyticSqlContract, Output> outputs =
            new EnumMap<>(AnalyticSqlContract.class);
    private final List<Monitor> monitors;
    private final Map<String, Long> factCandidates;

    public DeclaredSqlOracles(final PinnedLocalJson manifest, final CancellationToken token)
            throws IOException {
        this.manifest = manifest;
        final var root = manifest.read();
        QualificationJson.fields(root, "version", "origin", "facts", "monitor", "outputs");
        if (!"local-sql-oracles-v1".equals(QualificationJson.text(root, "version", 40))
                || !"INDEPENDENT_SYNTHETIC_RULES_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("INTEGRAL_SQL_ORACLE_ORIGIN");
        }
        QualificationJson.fields(root.path("facts"), "MAT01", "MAT02", "MAT03", "MAT04", "MAT05");
        final var facts = new java.util.LinkedHashMap<String, Long>();
        for (final var name : List.of("MAT01", "MAT02", "MAT03", "MAT04", "MAT05")) {
            facts.put(name, (long) QualificationJson.number(root.path("facts"), name, 0, 100000));
        }
        factCandidates = Map.copyOf(facts);
        QualificationJson.array(root.path("monitor"), 1, 256);
        final var monitorRows = new ArrayList<Monitor>();
        final var selectors = new HashSet<String>();
        for (final var row : root.path("monitor")) {
            QualificationJson.fields(row, "selector", "entity", "family", "rows", "state");
            final var value =
                    new Monitor(
                            QualificationJson.text(row, "selector", 32),
                            QualificationJson.text(row, "entity", 32),
                            QualificationJson.text(row, "family", 32),
                            QualificationJson.number(row, "rows", 0, 100000),
                            QualificationJson.text(row, "state", 32));
            if (!Set.of(
                                    "CAP",
                                    "FAT",
                                    "INV",
                                    "SIN",
                                    "FRETE",
                                    "LOC",
                                    "MAN",
                                    "COL",
                                    "REL_FRE",
                                    "USER",
                                    "COT",
                                    "RAS",
                                    "MAT01",
                                    "MAT02",
                                    "MAT03",
                                    "MAT04",
                                    "MAT05",
                                    "PARTITION_INVOICE",
                                    "PARTITION_REVENUE",
                                    "SCENARIO")
                            .contains(value.selector())
                    || !selectors.add(value.selector())) {
                throw new IllegalArgumentException("INTEGRAL_MONITOR_SELECTOR");
            }
            monitorRows.add(value);
        }
        monitors = List.copyOf(monitorRows);
        QualificationJson.array(root.path("outputs"), 19, 19);
        for (final var entry : root.path("outputs")) {
            QualificationJson.fields(entry, "id", "rows", "batches");
            final var contract =
                    AnalyticSqlContract.valueOf(
                            QualificationJson.text(entry, "id", 6).replace('-', '_'));
            final int count = QualificationJson.number(entry, "rows", 0, 4096);
            QualificationJson.array(
                    entry.path("batches"),
                    contract == AnalyticSqlContract.SQL_10 ? 0 : 1,
                    contract == AnalyticSqlContract.SQL_10 ? 0 : 128);
            final var files = new ArrayList<PinnedLocalJson>();
            for (final var file : entry.path("batches")) {
                files.add(
                        PinnedLocalJson.reference(
                                manifest.file().toPath().getParent(), file, 524288));
            }
            if (outputs.putIfAbsent(contract, new Output(count, List.copyOf(files))) != null
                    || contract == AnalyticSqlContract.SQL_10 && count != monitors.size()) {
                throw new IllegalArgumentException("INTEGRAL_SQL_ORACLE_DUPLICATE");
            }
        }
        verifyFiles(token);
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        manifest.verify();
        for (final var entry : outputs.entrySet()) {
            if (entry.getKey() == AnalyticSqlContract.SQL_10) {
                continue;
            }
            long count = 0;
            for (final var file : entry.getValue().files()) {
                token.throwIfCancellationRequested();
                final var rows = file.read();
                QualificationJson.array(rows, 0, 64);
                for (final var row : rows) {
                    QualificationJson.array(
                            row, entry.getKey().columns(), entry.getKey().columns());
                    for (int i = 0; i < row.size(); i++) {
                        cell(row.path(i), AnalyticSqlCatalog.columns(entry.getKey()).get(i));
                    }
                    count++;
                }
            }
            if (count != entry.getValue().count()) {
                throw new IllegalArgumentException("INTEGRAL_SQL_ORACLE_ROWS");
            }
        }
    }

    public QualificationComparator.ExpectedRows expected(
            final AnalyticSqlContract contract,
            final UUID run,
            final QualificationLineageEvidence lineage,
            final QualificationMonitoring monitoring,
            final Instant started,
            final Instant finished) {
        final var output = outputs.get(contract);
        return new QualificationComparator.ExpectedRows() {
            private long cachedOrdinal = -1;
            private JsonNode cachedRow;

            private JsonNode current(final long ordinal) {
                if (cachedOrdinal != ordinal) {
                    cachedRow = row(contract, ordinal);
                    cachedOrdinal = ordinal;
                }
                return cachedRow;
            }

            @Override
            public long count() {
                return contract == AnalyticSqlContract.SQL_10 ? monitoring.count() : output.count();
            }

            @Override
            public List<AnalyticSqlValue> at(final long ordinal) {
                if (contract == AnalyticSqlContract.SQL_10) {
                    return monitoring.row(ordinal);
                }
                final var row = current(ordinal);
                final var values = new ArrayList<AnalyticSqlValue>(row.size());
                for (int i = 0; i < row.size(); i++) {
                    final var value =
                            cell(row.path(i), AnalyticSqlCatalog.columns(contract).get(i));
                    values.add(
                            row.path(i).path("kind").asText().equals("runKey")
                                    ? new AnalyticSqlValue.Text(
                                            run.toString().toUpperCase(java.util.Locale.ROOT)
                                                    + QualificationJson.text(
                                                            row.path(i), "suffix", 512))
                                    : value);
                }
                return List.copyOf(values);
            }

            @Override
            public boolean equivalent(
                    final long ordinal,
                    final AnalyticSqlCatalog.Column column,
                    final AnalyticSqlValue wanted,
                    final AnalyticSqlValue actual) {
                if (contract == AnalyticSqlContract.SQL_10) {
                    return wanted.equals(actual);
                }
                final var rule = current(ordinal).path(column.ordinal() - 1);
                if (!rule.isObject()) {
                    return wanted.equals(actual);
                }
                final String kind = QualificationJson.text(rule, "kind", 20);
                if (kind.equals("runKey")) {
                    return wanted.equals(actual);
                }
                if (kind.equals("observedTime")) {
                    final Instant value =
                            actual instanceof AnalyticSqlValue.CivilDateTime time
                                    ? time.value().toInstant(ZoneOffset.UTC)
                                    : null;
                    return value != null
                            && !value.isBefore(started.minusMillis(1))
                            && !value.isAfter(finished.plusMillis(1));
                }
                if (kind.equals("lineage") && actual instanceof AnalyticSqlValue.Text text) {
                    try {
                        return lineage.compare(
                                QualificationJson.text(rule, "entity", 3),
                                QualificationJson.number(rule, "root", 1, 32),
                                QualificationJson.number(rule, "component", 1, 2),
                                QualificationJson.parse(
                                        text.value().getBytes(StandardCharsets.UTF_8), 131072));
                    } catch (final IOException | IllegalArgumentException failure) {
                        return false;
                    }
                }
                return false;
            }
        };
    }

    private JsonNode row(final AnalyticSqlContract contract, final long ordinal) {
        if (ordinal < 0 || ordinal >= outputs.get(contract).count()) {
            throw new IllegalArgumentException("INTEGRAL_SQL_ORACLE_ORDINAL");
        }
        long remaining = ordinal;
        try {
            for (final var pin : outputs.get(contract).files()) {
                final var rows = pin.read();
                if (remaining < rows.size()) {
                    return rows.path((int) remaining);
                }
                remaining -= rows.size();
            }
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        throw new IllegalArgumentException("INTEGRAL_SQL_ORACLE_ROW_MISSING");
    }

    private static AnalyticSqlValue cell(
            final JsonNode node, final AnalyticSqlCatalog.Column column) {
        if (node.isNull()) {
            return new AnalyticSqlValue.Missing();
        }
        if (node.isObject()) {
            final String kind = QualificationJson.text(node, "kind", 20);
            if (kind.equals("runKey")) {
                QualificationJson.fields(node, "kind", "suffix");
                if (!Set.of("ID Único", "Identificador Único").contains(column.name())
                        || !QualificationJson.text(node, "suffix", 512).startsWith("/")) {
                    throw new IllegalArgumentException("INTEGRAL_ORACLE_RUN_KEY_COLUMN");
                }
                return new AnalyticSqlValue.Text("DECLARED_RUN_KEY");
            }
            if (kind.equals("observedTime")) {
                QualificationJson.fields(node, "kind");
                if (!column.type().equals("datetime2")
                        || !Set.of(
                                        "Data de extracao",
                                        "data_extracao_raster",
                                        "Data da Última Atualização",
                                        "Data Atualizacao")
                                .contains(column.name())) {
                    throw new IllegalArgumentException("INTEGRAL_ORACLE_TECHNICAL_COLUMN");
                }
                return new AnalyticSqlValue.CivilDateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
            }
            if (kind.equals("lineage")) {
                QualificationJson.fields(node, "kind", "entity", "root", "component");
                if (!column.name().equals("Metadata")
                        || !Set.of("CAP", "FAT", "INV", "SIN", "FRE", "LOC", "MAN", "COL", "COT")
                                .contains(QualificationJson.text(node, "entity", 3))) {
                    throw new IllegalArgumentException("INTEGRAL_ORACLE_LINEAGE_COLUMN");
                }
                QualificationJson.number(node, "root", 1, 32);
                QualificationJson.number(node, "component", 1, 2);
                return new AnalyticSqlValue.Text("DECLARED_LINEAGE");
            }
            throw new IllegalArgumentException("INTEGRAL_ORACLE_RULE");
        }
        if (column.type().equals("bit")) {
            if (!node.isBoolean()) {
                throw new IllegalArgumentException("INTEGRAL_ORACLE_BOOLEAN");
            }
            return new AnalyticSqlValue.Flag(node.booleanValue());
        }
        if (Set.of("bigint", "int", "smallint", "tinyint").contains(column.type())) {
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw new IllegalArgumentException("INTEGRAL_ORACLE_INTEGER");
            }
            return new AnalyticSqlValue.IntegerValue(node.longValue());
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("INTEGRAL_ORACLE_SCALAR");
        }
        final String text = node.textValue();
        return switch (column.type()) {
            case "nvarchar", "varchar", "char" -> new AnalyticSqlValue.Text(text);
            case "decimal" ->
                    new AnalyticSqlValue.Decimal(
                            new BigDecimal(text)
                                    .setScale(column.scale(), RoundingMode.UNNECESSARY));
            case "date" -> new AnalyticSqlValue.Date(LocalDate.parse(text));
            case "time" -> new AnalyticSqlValue.Time(LocalTime.parse(text));
            case "datetime2" -> new AnalyticSqlValue.CivilDateTime(LocalDateTime.parse(text));
            case "datetimeoffset" ->
                    new AnalyticSqlValue.OffsetDateTimeValue(OffsetDateTime.parse(text));
            case "uniqueidentifier" -> new AnalyticSqlValue.Identifier(UUID.fromString(text));
            default -> throw new IllegalArgumentException("INTEGRAL_ORACLE_TYPE");
        };
    }

    public QualificationMonitoring monitoring(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles)
            throws SQLException {
        return monitoring(session, run, cycles, List.of());
    }

    QualificationMonitoring monitoring(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final List<SequenceRecomposition.Receipt> recompositions)
            throws SQLException {
        if (cycles.isEmpty() || cycles.size() > 8) {
            throw new IllegalArgumentException("INTEGRAL_MONITOR_CYCLE_BOUND");
        }
        final var declarations = new ArrayList<QualificationMonitoring.Expectation>();
        for (int index = 0; index < cycles.size(); index++) {
            declarations.addAll(monitorDeclarations(session, run, cycles.get(index), index));
        }
        for (final var receipt : recompositions) {
            receipt.facts()
                    .forEach(
                            (id, fact) ->
                                    declarations.add(
                                            new QualificationMonitoring.Expectation(
                                                    id,
                                                    fact,
                                                    fact.equals("MAT03") || fact.equals("MAT04")
                                                            ? "EXPANSION_MATERIALIZATION"
                                                            : "ANALYTIC_MATERIALIZATION",
                                                    receipt.candidates().get(fact),
                                                    "COMPLETE")));
        }
        return new QualificationMonitoring(session, run, declarations, true);
    }

    private List<QualificationMonitoring.Expectation> monitorDeclarations(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final AnalyticScenarioRuntime.Cycle cycle,
            final int priorCycles)
            throws SQLException {
        final var ids = new java.util.HashMap<String, UUID>();
        for (final var step : cycle.expanded().steps()) {
            ids.put(step.entity(), step.execution());
        }
        ids.putAll(
                Map.of(
                        "MAN",
                        cycle.manifest(),
                        "COL",
                        cycle.collection(),
                        "REL_FRE",
                        cycle.relationalFreight(),
                        "USER",
                        cycle.users(),
                        "COT",
                        cycle.quotes(),
                        "RAS",
                        cycle.raster().capture(),
                        "SCENARIO",
                        cycle.intent().cycle()));
        ids.putAll(
                Map.of(
                        "MAT01",
                        cycle.intent().mat01(),
                        "MAT02",
                        cycle.intent().mat02(),
                        "MAT03",
                        cycle.intent().mat03(),
                        "MAT04",
                        cycle.intent().mat04(),
                        "MAT05",
                        cycle.intent().mat05()));
        new SqlPartitionReceipts().read(session, run, cycle, ids);
        return expectedMonitorRows(ids, priorCycles);
    }

    List<QualificationMonitoring.Expectation> expectedMonitorRows(
            final Map<String, UUID> ids, final int priorCycles) {
        final var declarations = new ArrayList<QualificationMonitoring.Expectation>();
        for (final var row : monitors) {
            declarations.add(
                    new QualificationMonitoring.Expectation(
                            ids.get(row.selector()),
                            row.entity(),
                            row.family(),
                            // The scenario sums 19 query counts, including each prior SQL10 event.
                            row.rows()
                                    + (row.selector().equals("SCENARIO")
                                            ? (long) priorCycles * monitors.size()
                                            : 0),
                            row.state()));
        }
        return List.copyOf(declarations);
    }

    private static final class SqlPartitionReceipts {
        private void read(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final AnalyticScenarioRuntime.Cycle cycle,
                final Map<String, UUID> ids)
                throws SQLException {
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT invoice_receipt,revenue_receipt FROM recon.expansion_lab_partition"
                                            + " WHERE partition_id=? AND run_id=?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, cycle.expanded().partition().toString());
                sql.setString(2, run.expansion().toString());
                try (var row = sql.executeQuery()) {
                    if (!row.next()) {
                        throw new SQLException("INTEGRAL_ORACLE_PARTITION_MISSING");
                    }
                    ids.put("PARTITION_INVOICE", UUID.fromString(row.getString(1)));
                    ids.put("PARTITION_REVENUE", UUID.fromString(row.getString(2)));
                    if (row.next()) {
                        throw new SQLException("INTEGRAL_ORACLE_PARTITION_AMBIGUOUS");
                    }
                }
            }
        }
    }

    public Map<String, Long> factCandidates() {
        return factCandidates;
    }
}
