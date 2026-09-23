package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Fixed TVP writes, bounded claims and SQL reconciliation; no universe joins in Java. */
public final class JdbcExpansionRelations {
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionRelations(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public void bind(
            final UUID run,
            final List<ExpansionRelation> batch,
            final CancellationToken cancellation)
            throws SQLException {
        if (batch == null || batch.size() > 100) {
            throw new IllegalArgumentException("EXP_RELATION_BATCH_BOUND");
        }
        cancellation.throwIfCancellationRequested();
        final var data = new SQLServerDataTable();
        data.addColumnMetadata("binding_key", Types.VARCHAR);
        data.addColumnMetadata("revision", Types.INTEGER);
        for (final String name :
                List.of(
                        "kind",
                        "root_type",
                        "root_key",
                        "part_type",
                        "part_key",
                        "component_type",
                        "component_key",
                        "document_type",
                        "document_key",
                        "target_key")) {
            data.addColumnMetadata(name, Types.NVARCHAR);
        }
        data.addColumnMetadata("target_date", Types.DATE);
        data.addColumnMetadata("cardinality", Types.VARCHAR);
        data.addColumnMetadata("active", Types.BIT);
        data.addColumnMetadata("evidence", Types.VARCHAR);
        for (final var b : batch) {
            data.addRow(
                    b.bindingKey(),
                    b.revision(),
                    b.kind().name(),
                    b.root().kind().name(),
                    b.root().value(),
                    b.part().kind().name(),
                    b.part().value(),
                    b.component().kind().name(),
                    b.component().value(),
                    b.document().kind().name(),
                    b.document().value(),
                    b.targetKey(),
                    Date.valueOf(b.targetDate()),
                    b.cardinality().name(),
                    b.active(),
                    b.evidence());
        }
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC stg.usp_bind_expansion_relations ?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setStructured(2, "stg.expansion_lab_relation_batch", data);
            sql.setTimestamp(3, Timestamp.from(clock.instant()));
            sql.executeUpdate();
            cancellation.throwIfCancellationRequested();
        }
    }

    public Resolution resolve(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC core.usp_resolve_expansion_relations ?,?")) {
            sql.setQueryTimeout(20);
            sql.setString(1, run.toString());
            sql.setTimestamp(2, Timestamp.from(clock.instant()));
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_RELATION_RECEIPT_MISSING");
                }
                return new Resolution(
                        rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4));
            }
        }
    }

    public List<Claim> claimBatch(
            final UUID run, final UUID owner, final int maximum, final int leaseSeconds)
            throws SQLException {
        if (maximum < 1 || maximum > 100 || leaseSeconds < 1 || leaseSeconds > 60) {
            throw new IllegalArgumentException("EXP_QUEUE_BOUND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_claim_expansion_queue ?,?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, owner.toString());
            sql.setInt(3, maximum);
            sql.setInt(4, leaseSeconds);
            sql.setTimestamp(5, Timestamp.from(clock.instant()));
            try (var rows = sql.executeQuery()) {
                final var claims = new ArrayList<Claim>();
                while (rows.next()) {
                    if (claims.size() == maximum) {
                        throw new SQLException("EXP_QUEUE_RESULT_BOUND");
                    }
                    claims.add(
                            new Claim(
                                    rows.getLong(1),
                                    rows.getString(2),
                                    rows.getDate(3).toLocalDate(),
                                    rows.getInt(4),
                                    rows.getTimestamp(5).toInstant()));
                }
                return List.copyOf(claims);
            }
        }
    }

    public void finish(
            final UUID run,
            final Claim claim,
            final UUID owner,
            final Outcome outcome,
            final UUID execution)
            throws SQLException {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(outcome);
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_finish_expansion_queue ?,?,?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setLong(2, claim.queueId());
            sql.setString(3, owner.toString());
            sql.setString(4, outcome.name());
            sql.setString(5, execution == null ? null : execution.toString());
            sql.setTimestamp(6, Timestamp.from(clock.instant()));
            sql.executeUpdate();
        }
    }

    public enum Outcome {
        CAPTURED,
        EMPTY,
        TEMPORARY,
        INVALID,
        CONFLICT,
        ABANDONED
    }

    public record Resolution(long candidates, long resolved, long missing, long conflicts) {}

    public record Claim(
            long queueId, String targetKey, LocalDate targetDate, int attempt, Instant leaseUntil) {
        @Override
        public String toString() {
            return "ExpansionClaim[attempt=" + attempt + "]";
        }
    }
}
