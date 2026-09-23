package br.com.esl.etl.v2.plataforma.persistencia.relacional;

import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

/** Bounded commands and fixed SQL aggregates. Caller owns the rollback-only transaction. */
public final class JdbcRelationalLaboratory {
    public static final String SOURCE = "SYNTHETIC_RELATIONAL_LAB";
    public static final String TENANT = "SYNTHETIC_RELATIONAL_TENANT";
    public static final String VERSION = "synthetic-relational-v1";
    private final DataSource source;
    private final Clock clock;

    public JdbcRelationalLaboratory(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(
            final UUID run,
            final RelationalLaboratoryPolicy policy,
            final RelationalCaptureContracts contracts)
            throws SQLException {
        start(
                run,
                policy,
                contracts,
                new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(SOURCE, TENANT));
    }

    public void start(
            final UUID run,
            final RelationalLaboratoryPolicy policy,
            final RelationalCaptureContracts contracts,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(contracts);
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                    EXEC ctl.usp_relational_lab_lock ?;
                    INSERT ctl.relational_lab_run VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);
                    INSERT ctl.relational_lab_contract VALUES(?,N'manifestos',?),(?,N'coletas',?),(?,N'fretes',?);
                    """)) {
            connection.setSavepoint();
            sql.setString(1, run.toString());
            sql.setString(2, run.toString());
            sql.setString(3, scope.source());
            sql.setString(4, scope.tenant());
            sql.setString(5, VERSION);
            sql.setString(6, contracts.fingerprint());
            sql.setDate(7, Date.valueOf(policy.start()));
            sql.setDate(8, Date.valueOf(policy.end()));
            sql.setInt(9, policy.maximumRows());
            sql.setInt(10, policy.maximumClaim());
            sql.setInt(11, policy.maximumAttempts());
            sql.setInt(12, policy.leaseSeconds());
            sql.setInt(13, policy.retrySeconds());
            sql.setInt(14, policy.expansionDays());
            sql.setTimestamp(15, Timestamp.from(clock.instant()), utc());
            sql.setInt(16, policy.pageSize());
            sql.setInt(17, policy.maximumPages());
            sql.setString(18, run.toString());
            sql.setString(19, contracts.manifestos());
            sql.setString(20, run.toString());
            sql.setString(21, contracts.coletas());
            sql.setString(22, run.toString());
            sql.setString(23, contracts.fretes());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    public void bindBatch(
            final UUID run,
            final List<RelationalBinding> bindings,
            final CancellationToken cancellation)
            throws SQLException {
        if (bindings == null || bindings.size() > 100) {
            throw new IllegalArgumentException("REL_LAB_BINDING_BATCH_BOUND");
        }
        cancellation.throwIfCancellationRequested();
        final var table = new SQLServerDataTable();
        for (final String name :
                List.of(
                        "evidence_id",
                        "relation_kind",
                        "origin_key",
                        "origin_component",
                        "target_key",
                        "target_component")) {
            table.addColumnMetadata(name, Types.NVARCHAR);
        }
        table.addColumnMetadata("target_date", Types.DATE);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("cardinality", Types.NVARCHAR);
        for (final var b : bindings) {
            table.addRow(
                    b.evidenceId(),
                    b.relation().name(),
                    b.origin().storage(),
                    b.originComponent().storage(),
                    b.target().storage(),
                    b.targetComponent().storage(),
                    Date.valueOf(b.targetDate()),
                    b.revision(),
                    b.cardinality().name());
        }
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC stg.usp_relational_lab_bind ?,?,?")) {
            sql.setString(1, run.toString());
            sql.setStructured(2, "stg.relational_lab_binding_batch", table);
            sql.setTimestamp(3, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
            cancellation.throwIfCancellationRequested();
        }
    }

    /** Declares complete MC component sets for the origins included in this revision. */
    public void declareCompleteMcSets(
            final UUID run, final int revision, final CancellationToken cancellation)
            throws SQLException {
        if (revision < 1 || revision > 1000) {
            throw new IllegalArgumentException("REL_LAB_SET_REVISION");
        }
        cancellation.throwIfCancellationRequested();
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC stg.usp_declare_relational_mc_sets ?,?,?")) {
            sql.setString(1, run.toString());
            sql.setInt(2, revision);
            sql.setTimestamp(3, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
            cancellation.throwIfCancellationRequested();
        }
    }

    public CaptureReceipt capture(
            final UUID run,
            final UUID execution,
            final UUID receipt,
            final CancellationToken cancellation)
            throws SQLException {
        cancellation.throwIfCancellationRequested();
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC stg.usp_capture_relational_laboratory ?,?,?,?")) {
            sql.setString(1, run.toString());
            sql.setString(2, execution.toString());
            sql.setString(3, receipt.toString());
            sql.setTimestamp(4, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(20);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                cancellation.throwIfCancellationRequested();
                return new CaptureReceipt(
                        row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4));
            }
        }
    }

    public ResolutionReceipt resolve(
            final UUID run, final UUID receipt, final CancellationToken cancellation)
            throws SQLException {
        cancellation.throwIfCancellationRequested();
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC core.usp_resolve_relational_laboratory ?,?,?")) {
            sql.setString(1, run.toString());
            sql.setString(2, receipt.toString());
            sql.setTimestamp(3, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(20);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                cancellation.throwIfCancellationRequested();
                return new ResolutionReceipt(
                        row.getLong(1),
                        row.getLong(2),
                        row.getLong(3),
                        row.getLong(4),
                        row.getLong(5),
                        row.getLong(6),
                        row.getLong(7));
            }
        }
    }

    public List<Claim> claimBatch(
            final UUID run, final UUID owner, final int limit, final CancellationToken cancellation)
            throws SQLException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("REL_LAB_CLAIM_BOUND");
        }
        cancellation.throwIfCancellationRequested();
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_claim_relational_lab_backlog ?,?,?,?")) {
            connection.setSavepoint();
            sql.setString(1, run.toString());
            sql.setString(2, owner.toString());
            sql.setInt(3, limit);
            sql.setTimestamp(4, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                final var result = new ArrayList<Claim>(limit);
                while (row.next()) {
                    cancellation.throwIfCancellationRequested();
                    if (result.size() == limit) {
                        throw new SQLException("REL_LAB_UNBOUNDED_CLAIM");
                    }
                    result.add(
                            new Claim(
                                    row.getLong(1),
                                    row.getInt(2),
                                    RelationalBinding.Relation.valueOf(row.getString(3)),
                                    row.getString(4),
                                    row.getString(5),
                                    row.getDate(6).toLocalDate(),
                                    row.getTimestamp(7, utc()).toInstant()));
                }
                return List.copyOf(result);
            }
        }
    }

    public void finish(
            final UUID run,
            final long backlog,
            final UUID owner,
            final AttemptResult result,
            final UUID execution)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_finish_relational_lab_attempt ?,?,?,?,?,?")) {
            sql.setString(1, run.toString());
            sql.setLong(2, backlog);
            sql.setString(3, owner.toString());
            sql.setString(4, result.name());
            sql.setString(5, execution == null ? null : execution.toString());
            sql.setTimestamp(6, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    public Status status(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var sql = connection.prepareStatement("EXEC recon.usp_relational_lab_status ?,?")) {
            sql.setString(1, run.toString());
            sql.setTimestamp(2, Timestamp.from(clock.instant()), utc());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                return new Status(
                        row.getLong(1),
                        row.getLong(2),
                        row.getLong(3),
                        row.getLong(4),
                        row.getLong(5),
                        row.getLong(6),
                        row.getLong(7),
                        row.getLong(8),
                        row.getLong(9),
                        row.getLong(10),
                        row.getLong(11),
                        row.getLong(12),
                        row.getInt(13));
            }
        }
    }

    public RelationalLaboratoryPolicy policy(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT window_start,window_end,maximum_rows,maximum_claim,maximum_attempts,
                    lease_seconds,retry_seconds,expansion_days,page_size,maximum_pages
                FROM ctl.relational_lab_run WHERE run_id=?
                """)) {
            sql.setString(1, run.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                return new RelationalLaboratoryPolicy(
                        row.getDate(1).toLocalDate(),
                        row.getDate(2).toLocalDate(),
                        row.getInt(3),
                        row.getInt(4),
                        row.getInt(5),
                        row.getInt(6),
                        row.getInt(7),
                        row.getInt(8),
                        row.getInt(9),
                        row.getInt(10));
            }
        }
    }

    public br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope(final UUID run)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT source_instance,tenant_scope FROM ctl.relational_lab_run WHERE run_id=?")) {
            sql.setString(1, run.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                return new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                        row.getString(1), row.getString(2));
            }
        }
    }

    public AttemptResult claimOutcome(final UUID run, final long backlog, final UUID owner)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                SELECT cause FROM ctl.relational_lab_backlog
                WHERE run_id=? AND backlog_id=? AND owner_id=? AND state=N'CLAIMED'
                """)) {
            sql.setString(1, run.toString());
            sql.setLong(2, backlog);
            sql.setString(3, owner.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                requireRow(row);
                return switch (row.getString(1)) {
                    case "RESOLVED" -> AttemptResult.RESOLVED;
                    case "ORPHAN" -> AttemptResult.TEMPORARY_FAILURE;
                    default -> AttemptResult.CONFLICT;
                };
            }
        }
    }

    private static void requireRow(final ResultSet row) throws SQLException {
        if (!row.next()) {
            throw new SQLException("REL_LAB_RECEIPT_MISSING");
        }
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    public enum AttemptResult {
        RESOLVED,
        TEMPORARY_FAILURE,
        CONTRACT_FAILURE,
        CONFLICT,
        ABANDONED
    }

    public record Claim(
            long backlogId,
            int attempt,
            RelationalBinding.Relation relation,
            String targetKey,
            String targetComponent,
            LocalDate date,
            Instant leaseUntil) {}

    public record CaptureReceipt(long considered, long inserted, long updated, long noop) {
        public CaptureReceipt {
            if (considered < 0
                    || inserted < 0
                    || updated < 0
                    || noop < 0
                    || considered != inserted + updated + noop) {
                throw new IllegalArgumentException("REL_LAB_CAPTURE_CONSERVATION");
            }
        }
    }

    public record ResolutionReceipt(
            long considered,
            long inserted,
            long updated,
            long noop,
            long resolved,
            long orphaned,
            long blocked) {
        public ResolutionReceipt {
            if (considered < 0
                    || inserted < 0
                    || updated < 0
                    || noop < 0
                    || resolved < 0
                    || orphaned < 0
                    || blocked < 0
                    || resolved != inserted + noop
                    || considered < resolved + orphaned + blocked) {
                throw new IllegalArgumentException("REL_LAB_RESOLUTION_CONSERVATION");
            }
        }

        public long superseded() {
            return considered - resolved - orphaned - blocked;
        }
    }

    public record Status(
            long manifestos,
            long coletas,
            long fretes,
            long manifestoColetas,
            long coletaFretes,
            long pending,
            long quarantined,
            long oldestPendingSeconds,
            long unboundCandidates,
            long captures,
            long physicalRows,
            long receipts,
            int capturedEntities) {
        public boolean complete() {
            return capturedEntities == 3
                    && pending == 0
                    && quarantined == 0
                    && unboundCandidates == 0;
        }
    }
}
