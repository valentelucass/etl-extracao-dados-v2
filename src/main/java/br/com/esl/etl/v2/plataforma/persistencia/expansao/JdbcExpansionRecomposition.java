package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Bounded SQL plans, six capture slots and persisted materialization receipts. */
public final class JdbcExpansionRecomposition {
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionRecomposition(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public void plan(
            final UUID run,
            final ExecutionMode mode,
            final int revision,
            final Integer replayRevision,
            final List<Slot> slots)
            throws SQLException {
        if (mode == null
                || mode == ExecutionMode.SWEEP
                || revision < 1
                || revision > 1000
                || slots.isEmpty()
                || slots.size() > 31
                || (mode == ExecutionMode.REPLAY) != (replayRevision != null)) {
            throw new IllegalArgumentException("EXP_PLAN_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("ordinal", Types.INTEGER);
        table.addColumnMetadata("partition_date", Types.DATE);
        table.addColumnMetadata("first_root", Types.INTEGER);
        table.addColumnMetadata("root_count", Types.INTEGER);
        table.addColumnMetadata("missing_last_freight", Types.BIT);
        table.addColumnMetadata("source_revision", Types.INTEGER);
        table.addColumnMetadata("reference_revision", Types.INTEGER);
        for (final var slot : slots) {
            table.addRow(
                    slot.ordinal(),
                    Date.valueOf(slot.date()),
                    slot.firstRoot(),
                    slot.roots(),
                    slot.missingLastFreight(),
                    slot.sourceRevision(),
                    slot.referenceRevision());
        }
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC recon.usp_plan_expansion_lab ?,?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setInt(3, revision);
            sql.setObject(4, replayRevision, Types.INTEGER);
            sql.setStructured(5, "recon.expansion_lab_plan_batch", table);
            sql.executeUpdate();
        }
    }

    public Partition partition(
            final UUID run, final ExecutionMode mode, final int revision, final int ordinal)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT partition_id,partition_date,first_root,root_count,missing_last_freight,
                source_revision,reference_revision,invoice_receipt,revenue_receipt,state
                FROM recon.expansion_lab_partition WHERE run_id=? AND mode=? AND revision=? AND ordinal=?
                """)) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setInt(3, revision);
            sql.setInt(4, ordinal);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("EXP_PLAN_PARTITION_MISSING");
                }
                return new Partition(
                        UUID.fromString(row.getString(1)),
                        new Slot(
                                ordinal,
                                row.getDate(2).toLocalDate(),
                                row.getInt(3),
                                row.getInt(4),
                                row.getBoolean(5),
                                row.getInt(6),
                                row.getInt(7)),
                        UUID.fromString(row.getString(8)),
                        UUID.fromString(row.getString(9)),
                        row.getString(10));
            }
        }
    }

    public List<Step> stepsBatch(final UUID partition, final int maximum) throws SQLException {
        if (maximum != 6) {
            throw new IllegalArgumentException("EXP_PLAN_EXACT_SIX_STEP_BOUND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT s.entity,s.execution_id,s.original_execution,s.attached,
                CONVERT(BIT,CASE WHEN c.state='COMPLETE' THEN 1 ELSE 0 END) captured
                FROM recon.expansion_lab_step s LEFT JOIN recon.expansion_lab_step_capture c ON c.execution_id=s.execution_id
                WHERE s.partition_id=? ORDER BY s.entity
                """)) {
            sql.setQueryTimeout(10);
            sql.setString(1, partition.toString());
            final var result = new ArrayList<Step>(maximum);
            try (var row = sql.executeQuery()) {
                while (row.next()) {
                    if (result.size() == maximum) {
                        throw new SQLException("EXP_PLAN_STEP_OVERFLOW");
                    }
                    final String original = row.getString(3);
                    result.add(
                            new Step(
                                    row.getString(1),
                                    UUID.fromString(row.getString(2)),
                                    original == null ? null : UUID.fromString(original),
                                    row.getBoolean(4),
                                    row.getBoolean(5)));
                }
            }
            if (result.size() != 6) {
                throw new SQLException("EXP_PLAN_STEP_MISSING");
            }
            return List.copyOf(result);
        }
    }

    public void attach(final UUID partition, final String entity) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC recon.usp_attach_expansion_lab_step ?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, partition.toString());
            sql.setString(2, entity);
            sql.executeUpdate();
        }
    }

    public void complete(final UUID partition) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC recon.usp_complete_expansion_lab_partition ?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, partition.toString());
            sql.setTimestamp(
                    2,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            sql.executeUpdate();
        }
    }

    public void failure(final UUID partition, final String boundary, final String category)
            throws SQLException {
        if (!boundary.matches("[A-Z_]{1,32}")
                || !List.of("SQL", "CANCELLED", "CONTRACT", "INJECTED").contains(category)) {
            throw new IllegalArgumentException("EXP_FAILURE_SANITIZED_CODE");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT recon.expansion_lab_failure(partition_id,boundary,category,"
                                        + "recorded_at) VALUES(?,?,?,?)")) {
            sql.setQueryTimeout(10);
            sql.setString(1, partition.toString());
            sql.setString(2, boundary);
            sql.setString(3, category);
            sql.setTimestamp(
                    4,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            sql.executeUpdate();
        }
    }

    public Progress progress(final UUID run, final ExecutionMode mode, final int revision)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT COUNT_BIG(*) planned,COALESCE(SUM(CONVERT(BIGINT,CASE WHEN state='COMPLETE' THEN 1 ELSE 0 END)),0) completed,
                COALESCE(SUM(CONVERT(BIGINT,CASE WHEN state='DEGRADED' THEN 1 ELSE 0 END)),0) degraded,
                MIN(CASE WHEN state<>'COMPLETE' THEN partition_date END) first_incomplete
                FROM recon.expansion_lab_partition WHERE run_id=? AND mode=? AND revision=?
                """)) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setInt(3, revision);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("EXP_PROGRESS_MISSING");
                }
                final Date gap = row.getDate(4);
                return new Progress(
                        row.getLong(1),
                        row.getLong(2),
                        row.getLong(3),
                        gap == null ? null : gap.toLocalDate());
            }
        }
    }

    public record Slot(
            int ordinal,
            LocalDate date,
            int firstRoot,
            int roots,
            boolean missingLastFreight,
            int sourceRevision,
            int referenceRevision) {
        public Slot {
            Objects.requireNonNull(date);
            if (ordinal < 1
                    || ordinal > 31
                    || firstRoot < 1
                    || roots < 0
                    || firstRoot + roots > 2049
                    || sourceRevision < 1
                    || sourceRevision > 1000
                    || referenceRevision < 1
                    || referenceRevision > 1000) {
                throw new IllegalArgumentException("EXP_PLAN_SLOT_BOUND");
            }
        }
    }

    public record Partition(
            UUID id, Slot slot, UUID invoiceReceipt, UUID revenueReceipt, String state) {}

    public record Step(
            String entity, UUID execution, UUID original, boolean attached, boolean captured) {}

    public record Progress(long planned, long complete, long degraded, LocalDate firstIncomplete) {}
}
