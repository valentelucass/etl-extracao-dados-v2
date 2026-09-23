package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Public reconciliation contains only SQL aggregates and classified outcomes. */
public final class JdbcExpansionStatus {
    private final DataSource source;

    public JdbcExpansionStatus(final DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public Status read(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                DECLARE @run UNIQUEIDENTIFIER=?;
                WITH receipts AS(
                SELECT observations,inserts,updates,noops,stale,quarantine,unbound,duplicates
                FROM ctl.expansion_lab_apply_receipt WHERE run_id=@run
                UNION ALL SELECT observed,inserts,updates,noops,stale,quarantine,CONVERT(BIGINT,0),duplicates
                FROM ctl.expansion_lab_dependency_capture WHERE run_id=@run AND state='COMPLETE')
                SELECT
                (SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=@run AND active=1) roots,
                (SELECT COUNT_BIG(*) FROM core.expansion_lab_component c JOIN core.expansion_lab_root r ON r.root_id=c.root_id
                 WHERE r.run_id=@run AND r.active=1 AND c.active=1) components,
                (SELECT COUNT_BIG(*) FROM core.expansion_lab_link WHERE run_id=@run AND state='RESOLVED') links,
                (SELECT COUNT_BIG(*) FROM core.expansion_lab_link
                WHERE run_id=@run AND state IN('MISSING_SOURCE','MISSING_TARGET')) missing,
                (SELECT COUNT_BIG(*) FROM core.expansion_lab_link WHERE run_id=@run AND state='CONFLICT') conflicts,
                (SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=@run AND state<>'RESOLVED') pending,
                (SELECT COUNT_BIG(*) FROM recon.expansion_lab_step_capture WHERE run_id=@run AND state='COMPLETE') captures,
                (SELECT COUNT_BIG(*) FROM recon.expansion_lab_step_capture WHERE run_id=@run AND state<>'COMPLETE') incomplete,
                COALESCE(SUM(observations),0) observations,COALESCE(SUM(inserts),0) inserts,COALESCE(SUM(updates),0) updates,
                COALESCE(SUM(noops),0) noops,COALESCE(SUM(stale),0) stale,COALESCE(SUM(quarantine),0) quarantine,
                COALESCE(SUM(unbound),0) unbound,COALESCE(SUM(duplicates),0) duplicates,
                (SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice WHERE run_id=@run AND disposition<>'INACTIVE') invoices,
                (SELECT COUNT_BIG(*) FROM mart.expansion_lab_revenue WHERE run_id=@run AND disposition<>'INACTIVE') revenue,
                (SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice WHERE run_id=@run AND disposition NOT IN('READY','INACTIVE'))
                +(SELECT COUNT_BIG(*) FROM mart.expansion_lab_revenue WHERE run_id=@run AND disposition NOT IN('READY','INACTIVE')) blocked,
                (SELECT COUNT_BIG(*) FROM ctl.expansion_lab_materialization_receipt WHERE run_id=@run) materializations
                FROM receipts
                """)) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("EXP_STATUS_MISSING");
                }
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
                        row.getLong(13),
                        row.getLong(14),
                        row.getLong(15),
                        row.getLong(16),
                        row.getLong(17),
                        row.getLong(18),
                        row.getLong(19),
                        row.getLong(20));
            }
        }
    }

    public record Status(
            long roots,
            long components,
            long links,
            long missing,
            long conflicts,
            long pending,
            long captures,
            long incomplete,
            long observations,
            long inserts,
            long updates,
            long noops,
            long stale,
            long quarantine,
            long unbound,
            long duplicates,
            long invoices,
            long revenue,
            long blocked,
            long materializations) {
        public Status {
            if (observations
                    != inserts + updates + noops + stale + quarantine + unbound + duplicates) {
                throw new IllegalArgumentException("EXP_STATUS_EQUATION");
            }
        }

        public boolean complete() {
            return missing + conflicts + pending + incomplete + quarantine + unbound + blocked == 0;
        }
    }
}
