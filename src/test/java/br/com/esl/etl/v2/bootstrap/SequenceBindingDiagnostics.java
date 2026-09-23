package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.util.UUID;

/** Aggregate-only evidence; diagnostics never turn a failed campaign into an accepted test. */
final class SequenceBindingDiagnostics {
    private SequenceBindingDiagnostics() {}

    static SQLException observe(
            final ColetaTemporalLaboratorySession session, final UUID run, final int completed)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                    WITH scope AS (
                        SELECT relational_run FROM ctl.analytic_lab_source_group WHERE run_id=?
                    ), ranked AS (
                        SELECT b.*, DENSE_RANK() OVER (
                            PARTITION BY relation_kind,origin_key,origin_component
                            ORDER BY revision DESC) AS revision_rank
                        FROM stg.relational_lab_binding b JOIN scope s ON s.relational_run=b.run_id
                    ), current_bindings AS (
                        SELECT DISTINCT relation_kind,origin_key,origin_component,target_key,
                            target_component,revision FROM ranked WHERE revision_rank=1
                    )
                    SELECT
                        (SELECT COUNT_BIG(*) FROM core.relational_lab_link l JOIN scope s
                            ON s.relational_run=l.run_id WHERE l.relation_kind='MC' AND l.active=1),
                        (SELECT COUNT_BIG(*) FROM current_bindings WHERE relation_kind='MC'),
                        (SELECT COUNT_BIG(*) FROM current_bindings b WHERE b.relation_kind='MC'
                            AND EXISTS (SELECT 1 FROM current_bindings x
                                WHERE x.relation_kind=b.relation_kind AND x.target_key=b.target_key
                                AND x.target_component=b.target_component
                                AND (x.origin_key<>b.origin_key OR x.origin_component<>b.origin_component))),
                        (SELECT COUNT_BIG(*) FROM current_bindings b WHERE b.relation_kind='MC'
                            AND EXISTS (SELECT 1 FROM current_bindings x
                                WHERE x.relation_kind=b.relation_kind AND x.origin_key=b.origin_key
                                AND x.origin_component<>b.origin_component AND x.revision>b.revision))
                    """)) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("SEQUENCE_DIAGNOSTIC_MISSING");
                }
                return new SQLException(
                        "SEQUENCE_BINDING_DIAGNOSTIC completed="
                                + completed
                                + " activeMC="
                                + rows.getLong(1)
                                + " latestMC="
                                + rows.getLong(2)
                                + " conflictingMC="
                                + rows.getLong(3)
                                + " olderComponents="
                                + rows.getLong(4));
            }
        }
    }
}
