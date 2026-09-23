package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Real SQL materializations consume captured data and return reconciliation receipts. */
public final class JdbcExpansionMaterializations {
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionMaterializations(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public Receipt invoices(
            final UUID run,
            final UUID receipt,
            final int referenceRevision,
            final ExecutionMode mode,
            final boolean full,
            final LocalDate start,
            final LocalDate end)
            throws SQLException {
        return apply(false, run, receipt, referenceRevision, mode, full, start, end);
    }

    public Receipt revenue(
            final UUID run,
            final UUID receipt,
            final int referenceRevision,
            final ExecutionMode mode,
            final boolean full,
            final LocalDate start,
            final LocalDate end)
            throws SQLException {
        return apply(true, run, receipt, referenceRevision, mode, full, start, end);
    }

    private Receipt apply(
            final boolean revenue,
            final UUID run,
            final UUID receipt,
            final int referenceRevision,
            final ExecutionMode mode,
            final boolean full,
            final LocalDate start,
            final LocalDate end)
            throws SQLException {
        if (referenceRevision < 1
                || referenceRevision > 1000
                || mode == null
                || mode == ExecutionMode.SWEEP
                || !start.isBefore(end)
                || start.plusDays(31).isBefore(end)) {
            throw new IllegalArgumentException("EXP_MATERIALIZATION_SCOPE");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                revenue
                                        ? "EXEC mart.usp_materialize_expansion_revenue ?,?,?,?,?,?,?,?"
                                        : "EXEC mart.usp_materialize_expansion_invoices ?,?,?,?,?,?,?,?")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            sql.setString(2, receipt.toString());
            sql.setInt(3, referenceRevision);
            sql.setString(4, mode.name());
            sql.setBoolean(5, full);
            sql.setObject(6, start);
            sql.setObject(7, end);
            sql.setTimestamp(
                    8,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_MATERIALIZATION_RECEIPT_MISSING");
                }
                return new Receipt(
                        rows.getLong(1),
                        rows.getLong(2),
                        rows.getLong(3),
                        rows.getLong(4),
                        rows.getLong(5),
                        rows.getLong(6));
            }
        }
    }

    public record Receipt(
            long candidates, long inserts, long updates, long noops, long ready, long blocked) {
        public Receipt {
            if (candidates != inserts + updates + noops || candidates != ready + blocked) {
                throw new IllegalArgumentException("EXP_MATERIALIZATION_EQUATION");
            }
        }
    }
}
