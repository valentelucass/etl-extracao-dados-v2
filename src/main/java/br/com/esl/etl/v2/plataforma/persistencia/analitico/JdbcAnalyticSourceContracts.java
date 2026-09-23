package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Registers the packaged or explicitly declared local contract under the run's synthetic scope. */
public final class JdbcAnalyticSourceContracts {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticSourceContracts(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public void freightPerformance(final UUID run) throws SQLException {
        Objects.requireNonNull(run);
        final var release =
                new ExpansionDependencySource(page -> "[]")
                        .withAnalyticFreightPerformance()
                        .contractRelease(DataExportTemplate.FRETES);
        register(run, "analytic-freight-performance-v1", release.contractFingerprint().sha256());
    }

    public void integralFreight(final UUID run, final String fingerprint) throws SQLException {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ANA_FREIGHT_CONTRACT_HASH");
        }
        register(run, "integral-freight-pages-v1", fingerprint);
    }

    /** Refuse another manifest's run before creating a cycle or capturing any source. */
    public void requireIntegralContext(
            final UUID run,
            final UUID expansion,
            final UUID relational,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope,
            final java.time.LocalDate start,
            final java.time.LocalDate end)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT 1 FROM ctl.analytic_lab_run r JOIN ctl.analytic_lab_source_group g"
                                        + " ON g.run_id=r.run_id WHERE r.run_id=? AND g.expansion_run=? AND g.relational_run=?"
                                        + " AND r.source_instance=? AND r.tenant_scope=? AND r.window_start=? AND r.window_end_exclusive=?"
                                        + " AND r.zone_id=N'America/Sao_Paulo'")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, expansion.toString());
            sql.setString(3, relational.toString());
            sql.setNString(4, scope.source());
            sql.setNString(5, scope.tenant());
            sql.setObject(6, start);
            sql.setObject(7, end);
            try (var rows = sql.executeQuery()) {
                if (!rows.next() || rows.next()) {
                    throw new SQLException("INTEGRAL_RUN_CONTEXT");
                }
            }
        }
    }

    /** The complete manifest explicitly requires both protocols for this local synthetic source. */
    public void integralProtocols(final UUID run) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                    DECLARE @source NVARCHAR(128),@now DATETIME2(3)=SYSUTCDATETIME();
                    SELECT @source=source_instance FROM ctl.analytic_lab_run WHERE run_id=?
                    AND ctl.fn_local_synthetic_scope(source_instance)=1
                    AND ctl.fn_local_synthetic_scope(tenant_scope)=1;
                    IF @@TRANCOUNT=0 OR @source IS NULL OR DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW'
                        THROW 53850,N'INTEGRAL_PROTOCOL_LOCAL_RUN_REQUIRED',1;
                    EXEC ctl.usp_control_plane_register_source @source,N'DATA_EXPORT',@now;
                    INSERT ctl.source_protocol_binding(source_instance,source_kind,registered_at_utc)
                    SELECT @source,N'GRAPHQL',@now WHERE NOT EXISTS(
                        SELECT 1 FROM ctl.source_protocol_binding
                        WHERE source_instance=@source AND source_kind=N'GRAPHQL');
                    """)) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.executeUpdate();
        }
    }

    private void register(final UUID run, final String version, final String fingerprint)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_analytic_lab_lock ?,'DIMENSION';"
                                        + " IF EXISTS(SELECT 1 FROM ctl.analytic_lab_freight_contract WHERE run_id=? AND contrac"
                                        + "t_fingerprint<>?)"
                                        + " THROW 53587,N'ANA_FREIGHT_CONTRACT_DIVERGENT',1;"
                                        + " INSERT ctl.analytic_lab_freight_contract SELECT ?,?,"
                                        + "?,'synthetic-freight-contract-v1'"
                                        + " WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_freight_contract WHERE run_id=?)")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, run.toString());
            sql.setString(3, fingerprint);
            sql.setString(4, run.toString());
            sql.setString(5, version);
            sql.setString(6, fingerprint);
            sql.setString(7, run.toString());
            sql.executeUpdate();
        }
    }
}
