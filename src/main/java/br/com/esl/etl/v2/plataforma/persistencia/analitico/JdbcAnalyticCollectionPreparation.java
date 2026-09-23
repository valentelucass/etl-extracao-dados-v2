package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** One bounded call prepares typed observations and coherent roots in the rollback session. */
public final class JdbcAnalyticCollectionPreparation {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticCollectionPreparation(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public Receipt prepare(
            final UUID run, final UUID execution, final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        final var release =
                new RelationalSyntheticSource(page -> "[]")
                        .withAnalyticCollectionDetails()
                        .contractRelease(DataExportTemplate.COLETAS);
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    connection.prepareStatement(
                            "EXEC core.usp_prepare_analytic_collections ?,?,?")) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.toString());
                sql.setString(2, execution.toString());
                sql.setString(3, release.contractFingerprint().sha256());

                try (var rows = sql.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("ANA_COLLECTION_PREPARATION_RECEIPT_MISSING");
                    }
                    final var receipt =
                            new Receipt(
                                    rows.getLong(1),
                                    rows.getLong(2),
                                    rows.getLong(3),
                                    rows.getLong(4));
                    if (rows.next()) {
                        throw new SQLException("ANA_COLLECTION_PREPARATION_RECEIPT_MULTIPLE");
                    }
                    cancellation.throwIfCancellationRequested();
                    return receipt;
                }
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    public void requireAssociation(final UUID run, final UUID relationalRun) throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(relationalRun);
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT 1 FROM ctl.analytic_lab_source_group WHERE run_id=? AND relational_run=?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, relationalRun.toString());
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_COLLECTION_PREPARATION_SCOPE", "HY000", 53742);
                }
            }
        }
    }

    public record Receipt(long physicalRows, long roots, long updates, long noops) {
        public Receipt {
            if (physicalRows < roots
                    || roots < 0
                    || updates < 0
                    || noops < 0
                    || roots != updates + noops) {
                throw new IllegalArgumentException("ANA_COLLECTION_PREPARATION_EQUATION");
            }
        }
    }
}
