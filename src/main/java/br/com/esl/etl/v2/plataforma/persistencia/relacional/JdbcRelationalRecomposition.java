package br.com.esl.etl.v2.plataforma.persistencia.relacional;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Rehydrates bounded partition intents and receipts, with the execution control plane as source.
 */
public final class JdbcRelationalRecomposition {
    private final DataSource source;

    public JdbcRelationalRecomposition(final DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public void planBatch(final UUID run, final ExecutionMode mode, final List<PlanSlot> slots)
            throws SQLException {
        if (slots.isEmpty() || slots.size() > 64 || mode == ExecutionMode.SWEEP) {
            throw new IllegalArgumentException("REL_LAB_PLAN_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("ordinal", Types.INTEGER);
        table.addColumnMetadata("business_date", Types.DATE);
        table.addColumnMetadata("first_root", Types.INTEGER);
        table.addColumnMetadata("root_count", Types.INTEGER);
        for (final var slot : slots) {
            table.addRow(
                    slot.ordinal(), Date.valueOf(slot.date()), slot.firstRoot(), slot.rootCount());
        }
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC recon.usp_plan_relational_lab_partitions ?,?,?")) {
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setStructured(3, "recon.relational_lab_partition_batch", table);
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    public Partition partition(final UUID run, final ExecutionMode mode, final int ordinal)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT business_date,first_root,root_count,manifesto_execution,coleta_execution,frete_execution,completion_receipt
                FROM recon.relational_lab_partition WHERE run_id=? AND execution_mode=? AND ordinal=?
                """)) {
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setInt(3, ordinal);
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("REL_LAB_PARTITION_MISSING");
                }
                return new Partition(
                        new PlanSlot(
                                ordinal,
                                row.getDate(1).toLocalDate(),
                                row.getInt(2),
                                row.getInt(3)),
                        uuid(row.getString(4)),
                        uuid(row.getString(5)),
                        uuid(row.getString(6)),
                        uuid(row.getString(7)));
            }
        }
    }

    public UUID recoverCapture(
            final UUID run, final ExecutionMode mode, final LocalDate date, final String entity)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT c.execution_id FROM ctl.relational_lab_capture c
                JOIN ctl.execution_attempt a ON a.execution_id=c.execution_id
                JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
                WHERE c.run_id=? AND p.execution_mode=? AND c.business_date=? AND c.entity_name=?
                """)) {
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setDate(3, Date.valueOf(date));
            sql.setString(4, entity);
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                final UUID result = uuid(row.getString(1));
                if (row.next()) {
                    throw new SQLException("REL_LAB_RECOVERY_CAPTURE_AMBIGUOUS");
                }
                return result;
            }
        }
    }

    public void attach(
            final UUID run, final ExecutionMode mode, final int ordinal, final UUID execution)
            throws SQLException {
        command(
                "EXEC recon.usp_attach_relational_lab_capture ?,?,?,?",
                run,
                mode,
                ordinal,
                execution);
    }

    public void complete(
            final UUID run, final ExecutionMode mode, final int ordinal, final UUID receipt)
            throws SQLException {
        command(
                "EXEC recon.usp_complete_relational_lab_partition ?,?,?,?",
                run,
                mode,
                ordinal,
                receipt);
    }

    private void command(
            final String command,
            final UUID run,
            final ExecutionMode mode,
            final int ordinal,
            final UUID id)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql = connection.prepareStatement(command)) {
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setInt(3, ordinal);
            sql.setString(4, id.toString());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    public Progress progress(final UUID run, final ExecutionMode mode) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC recon.usp_relational_lab_partition_status ?,?")) {
            sql.setString(1, run.toString());
            sql.setString(2, mode.name());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("REL_LAB_PROGRESS_MISSING");
                }
                final Date incomplete = row.getDate(4);
                return new Progress(
                        row.getLong(1),
                        row.getLong(2),
                        row.getLong(3),
                        incomplete == null ? null : incomplete.toLocalDate());
            }
        }
    }

    private static UUID uuid(final String value) {
        return value == null ? null : UUID.fromString(value);
    }

    public record PlanSlot(int ordinal, LocalDate date, int firstRoot, int rootCount) {
        public PlanSlot {
            Objects.requireNonNull(date);
            if (ordinal < 1
                    || ordinal > 366
                    || firstRoot < 1
                    || rootCount < 0
                    || rootCount > 4096
                    || firstRoot + rootCount > 8193) {
                throw new IllegalArgumentException("REL_LAB_PLAN_SLOT_BOUND");
            }
        }
    }

    public record Partition(
            PlanSlot slot, UUID manifesto, UUID coleta, UUID frete, UUID completion) {}

    public record Progress(
            long planned, long completed, long contiguousCompleted, LocalDate firstIncomplete) {}
}
