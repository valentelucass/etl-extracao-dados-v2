package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Independent monitor counts/states plus exact times from the declared captures' control receipts.
 */
public final class QualificationMonitoring {
    private record Plan(String entity, String family, long rows, String state) {}

    private record Event(UUID id, Plan plan, LocalDateTime start, LocalDateTime finish) {}

    private final Map<UUID, Plan> planned = new HashMap<>();
    private final Map<UUID, Event> observed = new HashMap<>();
    private final List<Event> ordered;

    public QualificationMonitoring(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles)
            throws SQLException {
        if (run.roots() < 2
                || run.roots() > 32
                || cycles.isEmpty()
                || cycles.size() > 8
                || cycles.get(0).mode() != ExecutionMode.BOOTSTRAP) {
            throw new IllegalArgumentException("QUAL_MONITOR_SCOPE");
        }
        final int roots = run.roots();
        int ordinal = 0;
        for (final var cycle : cycles) {
            if (cycle.status().complete() != (run.fault() == AnalyticScenarioRuntime.Fault.NONE)
                    || cycle.expanded().hydrated() != (ordinal == 0 ? 1 : 0)) {
                throw new IllegalArgumentException("QUAL_MONITOR_CAPTURE_EQUATION");
            }
            ordinal++;
            for (final var entity : List.of("CAP", "FAT", "INV", "SIN")) {
                add(
                        cycle.expanded().execution(entity),
                        entity,
                        "EXPANSION_CAPTURE",
                        3L * roots,
                        "COMPLETE");
            }
            // The laboratory explicitly ends these control attempts as DEGRADED/capture-only.
            // Local analytical completion must never rewrite that operational state as PUBLISHED.
            add(cycle.manifest(), "manifestos", "RELATIONAL_CAPTURE", 3L * roots, "DEGRADED");
            add(cycle.collection(), "coletas", "RELATIONAL_CAPTURE", 3L * roots, "DEGRADED");
            add(cycle.relationalFreight(), "fretes", "RELATIONAL_CAPTURE", 2L * roots, "DEGRADED");
            add(
                    cycle.expanded().execution("LOC"),
                    "LOC",
                    "DEPENDENCY_CAPTURE",
                    2L * roots,
                    "DEGRADED");
            final boolean omitted =
                    cycle.mode() == ExecutionMode.BOOTSTRAP || cycle.mode() == ExecutionMode.REPLAY;
            add(
                    cycle.expanded().execution("FRETE"),
                    "FRETE",
                    "DEPENDENCY_CAPTURE",
                    2L * (roots - (omitted ? 1 : 0)),
                    "DEGRADED");
            add(cycle.users(), "USUARIO", "ATTACHED_RUNTIME", roots, "PUBLISHED");
            add(cycle.quotes(), "COT", "ATTACHED_RUNTIME", 3L * roots, "PUBLISHED");
            if (run.fault() != AnalyticScenarioRuntime.Fault.RASTER_INCOMPLETE) {
                add(cycle.raster().capture(), "RASTER", "RASTER_CAPTURE", 6L * roots, "APPLIED");
            } else if (cycle.raster() != null) {
                throw new IllegalArgumentException("QUAL_MONITOR_INCOMPLETE_RASTER_ACCEPTED");
            }
            add(
                    cycle.intent().mat01(),
                    "MAT01",
                    "ANALYTIC_MATERIALIZATION",
                    2L * roots,
                    "COMPLETE");
            // Two manifest contributions (issued/unloaded), one inventory contribution per root.
            add(
                    cycle.intent().mat02(),
                    "MAT02",
                    "ANALYTIC_MATERIALIZATION",
                    3L * roots,
                    "COMPLETE");
            add(
                    cycle.intent().mat05(),
                    "MAT05",
                    "ANALYTIC_MATERIALIZATION",
                    roots,
                    run.fault() == AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING
                            ? "DEGRADED"
                            : "COMPLETE");
            add(
                    cycle.intent().mat03(),
                    "MAT03",
                    "EXPANSION_MATERIALIZATION",
                    roots,
                    financialState(run));
            add(cycle.intent().mat04(), "MAT04", "EXPANSION_MATERIALIZATION", roots, "COMPLETE");
            // Six dimensional contracts contribute 27 validity rows + roots users;
            // the other positive outputs contribute 15*roots. SQL04 is empty in this scope.
            add(
                    cycle.intent().cycle(),
                    "SCENARIO",
                    "ANALYTIC_SCENARIO",
                    scenarioRows(run, ordinal),
                    run.fault() == AnalyticScenarioRuntime.Fault.NONE ? "COMPLETE" : "DEGRADED");
        }
        if (run.fault() == AnalyticScenarioRuntime.Fault.COLLECTION_SNAPSHOT_INVALID) {
            rejectedCollections(session, run, cycles.size());
        }
        partitions(session, run, cycles);
        hydration(session, run);
        observeTimes(session, run);
        if (observed.size() != planned.size()
                || planned.size() != cycles.size() * eventsPerCycle(run) + 1) {
            throw new SQLException("QUAL_MONITOR_MISSING_RECEIPT");
        }
        ordered =
                observed.values().stream()
                        .sorted(
                                Comparator.comparing((Event e) -> e.plan().entity())
                                        .thenComparing(e -> e.plan().family())
                                        .thenComparing(e -> guidOrder(e.id())))
                        .toList();
    }

    public record Expectation(UUID id, String entity, String family, long rows, String state) {}

    public QualificationMonitoring(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<Expectation> declarations,
            final boolean explicit)
            throws SQLException {
        if (!explicit || declarations.isEmpty() || declarations.size() > 256) {
            throw new IllegalArgumentException("INTEGRAL_MONITOR_BOUND");
        }
        for (final var declaration : declarations) {
            add(
                    declaration.id(),
                    declaration.entity(),
                    declaration.family(),
                    declaration.rows(),
                    declaration.state());
        }
        observeTimes(session, run);
        if (observed.size() != planned.size()) {
            throw new SQLException("QUAL_MONITOR_MISSING_RECEIPT");
        }
        ordered =
                observed.values().stream()
                        .sorted(
                                Comparator.comparing((Event e) -> e.plan().entity())
                                        .thenComparing(e -> e.plan().family())
                                        .thenComparing(e -> guidOrder(e.id())))
                        .toList();
    }

    private void observeTimes(
            final ColetaTemporalLaboratorySession session, final AnalyticScenarioRuntime.Run run)
            throws SQLException {
        times(
                session,
                run.expansion(),
                "EXPANSION_CAPTURE",
                """
                SELECT TOP(257) execution_id,created_at,sealed_at
                FROM ctl.expansion_lab_capture WHERE run_id=?
                """);
        times(
                session,
                run.relational(),
                "RELATIONAL_CAPTURE",
                """
                SELECT TOP(257) c.execution_id,e.started_at_utc,e.terminal_at_utc
                FROM ctl.relational_lab_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
                WHERE c.run_id=?
                """);
        times(
                session,
                run.expansion(),
                "DEPENDENCY_CAPTURE",
                """
                SELECT TOP(257) c.execution_id,e.started_at_utc,e.terminal_at_utc
                FROM ctl.expansion_lab_dependency_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
                WHERE c.run_id=?
                """);
        times(
                session,
                run.id(),
                "ATTACHED_RUNTIME",
                """
                SELECT TOP(257) c.execution_id,e.started_at_utc,e.terminal_at_utc
                FROM ctl.analytic_lab_execution_source c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
                WHERE c.run_id=?
                """);
        times(
                session,
                run.id(),
                "RASTER_CAPTURE",
                """
                SELECT TOP(257) capture_id,extracted_at,CONVERT(DATETIME2(7),NULL)
                FROM ctl.analytic_raster_capture WHERE run_id=?
                """);
        times(
                session,
                run.id(),
                "ANALYTIC_MATERIALIZATION",
                """
                SELECT TOP(257) receipt_id,CONVERT(DATETIME2(7),NULL),recorded_at
                FROM ctl.analytic_lab_materialization_receipt WHERE run_id=?
                """);
        times(
                session,
                run.expansion(),
                "EXPANSION_MATERIALIZATION",
                """
                SELECT TOP(257) receipt_id,CONVERT(DATETIME2(7),NULL),recorded_at
                FROM ctl.expansion_lab_materialization_receipt WHERE run_id=?
                """);
        times(
                session,
                run.id(),
                "ANALYTIC_SCENARIO",
                """
                SELECT TOP(257) cycle_id,CONVERT(DATETIME2(7),NULL),completed_at
                FROM ctl.analytic_scenario_cycle WHERE run_id=?
                """);
    }

    private void partitions(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles)
            throws SQLException {
        final var ids = cycles.stream().map(c -> c.expanded().partition()).toList();
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT TOP(9) partition_id,invoice_receipt,revenue_receipt
                FROM recon.expansion_lab_partition WHERE run_id=?
                """)) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.expansion().toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > cycles.size()
                            || !ids.contains(UUID.fromString(rows.getString(1)))) {
                        throw new SQLException("QUAL_MONITOR_FOREIGN_PARTITION");
                    }
                    add(
                            UUID.fromString(rows.getString(2)),
                            "MAT04",
                            "EXPANSION_MATERIALIZATION",
                            run.roots(),
                            "COMPLETE");
                    add(
                            UUID.fromString(rows.getString(3)),
                            "MAT03",
                            "EXPANSION_MATERIALIZATION",
                            run.roots(),
                            financialState(run));
                }
            }
            if (count != cycles.size()) {
                throw new SQLException("QUAL_MONITOR_PARTITION_COUNT");
            }
        }
    }

    private static String financialState(final AnalyticScenarioRuntime.Run run) {
        return run.fault() == AnalyticScenarioRuntime.Fault.FINANCIAL_REFERENCE_MISSING
                ? "DEGRADED"
                : "COMPLETE";
    }

    private static int eventsPerCycle(final AnalyticScenarioRuntime.Run run) {
        return switch (run.fault()) {
            case RASTER_INCOMPLETE -> 19;
            case COLLECTION_SNAPSHOT_INVALID -> 24;
            default -> 20;
        };
    }

    private static long scenarioRows(final AnalyticScenarioRuntime.Run run, final int ordinal) {
        final int businessPerRoot =
                switch (run.fault()) {
                    case RASTER_INCOMPLETE -> 15;
                    case MANIFEST_FLEET_MISSING -> 14;
                    default -> 16;
                };
        final int dimensions =
                run.fault() == AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING ? 21 : 27;
        return (long) businessPerRoot * run.roots()
                + dimensions
                + (long) eventsPerCycle(run) * ordinal
                + 1;
    }

    private void rejectedCollections(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final int cycles)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(65) c.execution_id,c.entity_name,c.physical_rows,p.execution_mode"
                                        + " FROM ctl.relational_lab_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id"
                                        + " JOIN ctl.execution_partition p ON p.partition_id=e.partition_id"
                                        + " WHERE c.run_id=?")) {
            sql.setQueryTimeout(15);
            sql.setString(1, run.relational().toString());
            int extras = 0;
            int visited = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++visited > 64) {
                        throw new SQLException("QUAL_MONITOR_REJECTED_CAPTURE_LIMIT");
                    }
                    final var id = UUID.fromString(rows.getString(1));
                    if (planned.containsKey(id)) {
                        continue;
                    }
                    if (!"coletas".equals(rows.getString(2))
                            || rows.getLong(3) != 3L * (run.roots() - 1)
                            || !"BACKFILL".equals(rows.getString(4))
                            || ++extras > 4 * cycles) {
                        throw new SQLException("QUAL_MONITOR_REJECTED_CAPTURE_SCOPE");
                    }
                    add(id, "coletas", "RELATIONAL_CAPTURE", 3L * (run.roots() - 1), "DEGRADED");
                }
            }
            if (extras != 4 * cycles) {
                throw new SQLException("QUAL_MONITOR_REJECTED_CAPTURE_COUNT");
            }
        }
    }

    private void hydration(
            final ColetaTemporalLaboratorySession session, final AnalyticScenarioRuntime.Run run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT TOP(2) a.execution_id FROM ctl.expansion_lab_queue_attempt a
                JOIN ctl.expansion_lab_queue q ON q.queue_id=a.queue_id
                WHERE q.run_id=? AND a.outcome='CAPTURED'
                """)) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.expansion().toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("QUAL_MONITOR_HYDRATION_MISSING");
                }
                add(
                        UUID.fromString(rows.getString(1)),
                        "FRETE",
                        "DEPENDENCY_CAPTURE",
                        1,
                        "DEGRADED");
                if (rows.next()) {
                    throw new SQLException("QUAL_MONITOR_HYDRATION_COUNT");
                }
            }
        }
    }

    private void add(
            final UUID id,
            final String entity,
            final String family,
            final long rows,
            final String state) {
        if (planned.size() >= 256
                || planned.putIfAbsent(id, new Plan(entity, family, rows, state)) != null) {
            throw new IllegalArgumentException("QUAL_MONITOR_DUPLICATE_OR_LIMIT");
        }
    }

    private void times(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String family,
            final String statement)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql = connection.prepareStatement(statement)) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    final UUID id = UUID.fromString(rows.getString(1));
                    final var plan = planned.get(id);
                    if (++count > 256 || plan == null || !plan.family().equals(family)) {
                        throw new SQLException("QUAL_MONITOR_FOREIGN_CONTROL_RECEIPT_" + family);
                    }
                    final var event =
                            new Event(
                                    id,
                                    plan,
                                    rows.getObject(2, LocalDateTime.class),
                                    rows.getObject(3, LocalDateTime.class));
                    if (observed.putIfAbsent(id, event) != null) {
                        throw new SQLException("QUAL_MONITOR_DUPLICATE_RECEIPT");
                    }
                }
            }
        }
    }

    public int count() {
        return ordered.size();
    }

    public List<AnalyticSqlValue> row(final long ordinal) {
        if (ordinal < 0 || ordinal >= ordered.size()) {
            throw new IllegalArgumentException("QUAL_MONITOR_ROW_SCOPE");
        }
        final var event = ordered.get(Math.toIntExact(ordinal));
        final var values = new ArrayList<AnalyticSqlValue>(9);
        values.add(
                new AnalyticSqlValue.Text(
                        event.id().toString().toUpperCase(java.util.Locale.ROOT)));
        values.add(time(event.start()));
        values.add(time(event.finish()));
        if (event.start() == null || event.finish() == null) {
            values.add(new AnalyticSqlValue.Missing());
        } else {
            final long micros =
                    ChronoUnit.MICROS.between(
                            event.start().truncatedTo(ChronoUnit.MICROS),
                            event.finish().truncatedTo(ChronoUnit.MICROS));
            values.add(new AnalyticSqlValue.Decimal(BigDecimal.valueOf(micros, 6).setScale(8)));
        }
        final var date = event.start() == null ? event.finish() : event.start();
        values.add(
                date == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.Date(date.toLocalDate()));
        values.add(new AnalyticSqlValue.Text(event.plan().state()));
        values.add(new AnalyticSqlValue.IntegerValue(event.plan().rows()));
        final boolean captureOnly = event.plan().state().equals("DEGRADED");
        values.add(
                captureOnly
                        ? new AnalyticSqlValue.Text("EXECUTION_STATE")
                        : new AnalyticSqlValue.Missing());
        values.add(
                captureOnly
                        ? new AnalyticSqlValue.Text("EXECUTION_DEGRADED")
                        : new AnalyticSqlValue.Missing());
        return List.copyOf(values);
    }

    private static AnalyticSqlValue time(final LocalDateTime value) {
        return value == null
                ? new AnalyticSqlValue.Missing()
                : new AnalyticSqlValue.CivilDateTime(value);
    }

    /** SQL uniqueidentifier orders the binary GUID groups, unlike Java UUID signed-long order. */
    private static String guidOrder(final UUID id) {
        final var groups = id.toString().split("-");
        return groups[4]
                + groups[3]
                + reverseBytes(groups[2])
                + reverseBytes(groups[1])
                + reverseBytes(groups[0]);
    }

    private static String reverseBytes(final String hex) {
        final var value = new StringBuilder();
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            value.append(hex, i, i + 2);
        }
        return value.toString();
    }
}
