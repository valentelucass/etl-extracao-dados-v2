package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Registers only the fixed packaged contract extension, not arbitrary caller fingerprints. */
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
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_analytic_lab_lock ?,'DIMENSION';"
                                        + " IF EXISTS(SELECT 1 FROM ctl.analytic_lab_freight_contract WHERE run_id=? AND contrac"
                                        + "t_fingerprint<>?)"
                                        + " THROW 53587,N'ANA_FREIGHT_CONTRACT_DIVERGENT',1;"
                                        + " INSERT ctl.analytic_lab_freight_contract SELECT ?,'analytic-freight-performance-v1',"
                                        + "?,'synthetic-freight-contract-v1'"
                                        + " WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_freight_contract WHERE run_id=?)")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, run.toString());
            sql.setString(3, release.contractFingerprint().sha256());
            sql.setString(4, run.toString());
            sql.setString(5, release.contractFingerprint().sha256());
            sql.setString(6, run.toString());
            sql.executeUpdate();
        }
    }
}
