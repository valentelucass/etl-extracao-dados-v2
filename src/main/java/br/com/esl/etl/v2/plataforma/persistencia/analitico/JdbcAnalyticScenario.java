package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Rehydrates fixed-size intentions and terminal evidence; counts and frontier stay in SQL. */
public final class JdbcAnalyticScenario {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticScenario(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public Intent begin(
            final UUID run,
            final ExecutionMode mode,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final UUID replay,
            final int referenceRevision,
            final int roots,
            final int pageSize,
            final boolean correction)
            throws SQLException {
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "EXEC ctl.usp_begin_analytic_scenario ?,?,?,?,?,?,?,?,?,?")) {
            statement.setQueryTimeout(15);
            statement.setString(1, run.toString());
            statement.setString(2, mode.name());
            statement.setInt(3, revision);
            statement.setObject(4, start);
            statement.setObject(5, end);
            statement.setString(6, replay == null ? null : replay.toString());
            statement.setInt(7, referenceRevision);
            statement.setInt(8, roots);
            statement.setInt(9, pageSize);
            statement.setBoolean(10, correction);
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_SCENARIO_INTENT_MISSING");
                }
                final var result =
                        new Intent(
                                UUID.fromString(row.getString(1)),
                                UUID.fromString(row.getString(2)),
                                UUID.fromString(row.getString(3)),
                                UUID.fromString(row.getString(4)),
                                UUID.fromString(row.getString(5)),
                                UUID.fromString(row.getString(6)),
                                row.getString(7));
                if (row.next()) {
                    throw new SQLException("ANA_SCENARIO_INTENT_MULTIPLE");
                }
                return result;
            }
        }
    }

    public Status complete(
            final UUID cycle,
            final List<Source> sources,
            final String failure,
            final CancellationToken token)
            throws SQLException {
        token.throwIfCancellationRequested();
        if (sources == null || sources.size() != 11) {
            throw new IllegalArgumentException("ANA_SCENARIO_SOURCE_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("entity", Types.VARCHAR);
        table.addColumnMetadata("execution_id", Types.VARCHAR);
        for (final var source : sources) {
            table.addRow(
                    source.entity(),
                    source.execution() == null ? null : source.execution().toString());
        }
        try (var connection = session.getConnection();
                var statement =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC ctl.usp_complete_analytic_scenario ?,?,?")) {
            statement.setQueryTimeout(60);
            statement.setString(1, cycle.toString());
            statement.setStructured(2, "ctl.analytic_scenario_source_batch", table);
            statement.setString(3, failure);
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_SCENARIO_STATUS_MISSING");
                }
                final var result =
                        new Status(
                                row.getString(1),
                                row.getString(2),
                                row.getObject(3, LocalDate.class));
                if (row.next()) {
                    throw new SQLException("ANA_SCENARIO_STATUS_MULTIPLE");
                }
                token.throwIfCancellationRequested();
                return result;
            }
        }
    }

    public Status status(final UUID cycle) throws SQLException {
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT c.state,c.failure,f.next_date FROM ctl.analytic_scenario_cycle c"
                                        + " JOIN ctl.analytic_scenario_frontier f ON f.run_id=c.run_id WHERE c.cycle_id=?")) {
            statement.setQueryTimeout(15);
            statement.setString(1, cycle.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_SCENARIO_STATUS_MISSING");
                }
                return new Status(
                        row.getString(1), row.getString(2), row.getObject(3, LocalDate.class));
            }
        }
    }

    /**
     * Manual fixture constants are checked against current SQL facts, independently of receipts.
     */
    public void verifyFixtureFacts(final UUID run, final int roots) throws SQLException {
        if (roots < 2 || roots > 480) {
            throw new IllegalArgumentException("ANA_SCENARIO_ORACLE_BOUND");
        }
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT (SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational f WHERE f.run_id=g"
                                        + ".run_id),"
                                        + "(SELECT SUM(o.total_value) FROM mart.analytic_freight_operational f JOIN mart.analyti"
                                        + "c_freight_operational_observation o"
                                        + " ON o.observation_id=f.observation_id WHERE f.run_id=g.run_id),"
                                        + "(SELECT COUNT_BIG(*) FROM pub.analytic_lab_manifests m WHERE m.run_id=g.run_id),"
                                        + "(SELECT SUM(m.total_revenue) FROM pub.analytic_lab_manifests m WHERE m.run_id=g.run_id),"
                                        + "(SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice i WHERE i.run_id=g.expansion_run),"
                                        + "(SELECT SUM(i.operational_value) FROM mart.expansion_lab_invoice i WHERE i.run_id=g.e"
                                        + "xpansion_run),"
                                        + "(SELECT COUNT_BIG(*) FROM mart.expansion_lab_revenue r WHERE r.run_id=g.expansion_run),"
                                        + "(SELECT SUM(r.revenue_value) FROM mart.expansion_lab_revenue r WHERE r.run_id=g.expan"
                                        + "sion_run)"
                                        + " FROM ctl.analytic_lab_source_group g WHERE g.run_id=?")) {
            statement.setQueryTimeout(30);
            statement.setString(1, run.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()
                        || row.getLong(1) != 2L * roots
                        || row.getLong(3) != roots
                        || row.getLong(5) != roots
                        || row.getLong(7) != roots
                        || !equalsAmount(row.getBigDecimal(2), roots, 240)
                        || !equalsAmount(row.getBigDecimal(4), roots, 120)
                        || !equalsAmount(row.getBigDecimal(6), roots, 100)
                        || !equalsAmount(row.getBigDecimal(8), roots, 120)
                        || row.next()) {
                    throw new SQLException("ANA_SCENARIO_MANUAL_FACT_ORACLE");
                }
            }
        }
    }

    private static boolean equalsAmount(
            final BigDecimal actual, final int roots, final int unitValue) {
        return actual != null
                && actual.compareTo(BigDecimal.valueOf((long) roots * unitValue)) == 0;
    }

    /**
     * Daily business tuples are invariant even when replay adds extraction lineage to
     * contributions.
     */
    public void verifyCollectorReplay(final UUID before, final UUID after) throws SQLException {
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "DECLARE @before UNIQUEIDENTIFIER=?,@after UNIQUEIDENTIFIER=?,@run UNIQUEIDENTIFIER,@a"
                                        + " BIGINT,@b BIGINT; "
                                        + "SELECT @run=a.run_id FROM ctl.analytic_lab_materialization_receipt a "
                                        + "JOIN ctl.analytic_lab_materialization_receipt b ON b.run_id=a.run_id "
                                        + "WHERE a.receipt_id=@before AND b.receipt_id=@after AND a.kind='MAT02' AND b.kind='MAT02' "
                                        + "AND a.ready>0 AND b.ready>0; "
                                        + "SELECT @a=MAX(observation_id) FROM mart.analytic_collector_daily_observation WHERE re"
                                        + "ceipt_id=@before; "
                                        + "SELECT @b=MAX(observation_id) FROM mart.analytic_collector_daily_observation WHERE re"
                                        + "ceipt_id=@after; "
                                        + "IF @run IS NULL OR @a IS NULL OR @b IS NULL OR @a>=@b "
                                        + "THROW 53824,N'ANA_SCENARIO_COLLECTOR_HISTORY_BOUND',1; "
                                        + "WITH prior_history AS(SELECT reference_date,branch_key,classification,issued,unloaded"
                                        + ",scanned,incomplete,total,percentage,has_inventory,active, "
                                        + "ROW_NUMBER() OVER(PARTITION BY reference_date,branch_key ORDER BY observation_id DESC"
                                        + ") ordinal "
                                        + "FROM mart.analytic_collector_daily_observation WHERE run_id=@run AND observation_id<=@a), "
                                        + "next_history AS(SELECT reference_date,branch_key,classification,issued,unloaded,scann"
                                        + "ed,incomplete,total,percentage,has_inventory,active, "
                                        + "ROW_NUMBER() OVER(PARTITION BY reference_date,branch_key ORDER BY observation_id DESC"
                                        + ") ordinal "
                                        + "FROM mart.analytic_collector_daily_observation WHERE run_id=@run AND observation_id<=@b) "
                                        + "SELECT CASE WHEN NOT EXISTS(SELECT reference_date,branch_key,classification,issued,un"
                                        + "loaded,scanned,incomplete,total,percentage,has_inventory,active FROM prior_history WH"
                                        + "ERE ordinal=1 "
                                        + "EXCEPT SELECT reference_date,branch_key,classification,issued,unloaded,scanned,incomp"
                                        + "lete,total,percentage,has_inventory,active FROM next_history WHERE ordinal=1) "
                                        + "AND NOT EXISTS(SELECT reference_date,branch_key,classification,issued,unloaded,scanne"
                                        + "d,incomplete,total,percentage,has_inventory,active FROM next_history WHERE ordinal=1 "
                                        + "EXCEPT SELECT reference_date,branch_key,classification,issued,unloaded,scanned,incomp"
                                        + "lete,total,percentage,has_inventory,active FROM prior_history WHERE ordinal=1) THEN 1"
                                        + " ELSE 0 END ")) {
            statement.setQueryTimeout(30);
            statement.setString(1, before.toString());
            statement.setString(2, after.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next() || row.getInt(1) != 1 || row.next()) {
                    throw new SQLException("ANA_SCENARIO_COLLECTOR_REPLAY_BUSINESS_CHANGED");
                }
            }
        }
    }

    public record Intent(
            UUID cycle, UUID mat01, UUID mat02, UUID mat03, UUID mat04, UUID mat05, String state) {}

    public record Source(String entity, UUID execution) {
        public Source {
            Objects.requireNonNull(entity, "ANA_SCENARIO_SOURCE_ENTITY");
            final boolean supported =
                    switch (entity) {
                        case "CAP",
                                        "FAT",
                                        "INV",
                                        "SIN",
                                        "FRETE",
                                        "LOC",
                                        "MAN",
                                        "COL",
                                        "COT",
                                        "USUARIO",
                                        "RASTER" ->
                                true;
                        default -> false;
                    };
            if (!supported) {
                throw new IllegalArgumentException("ANA_SCENARIO_SOURCE_ENTITY");
            }
        }
    }

    public record Status(String state, String failure, LocalDate nextDate) {
        public boolean complete() {
            return "COMPLETE".equals(state) && failure == null;
        }
    }
}
