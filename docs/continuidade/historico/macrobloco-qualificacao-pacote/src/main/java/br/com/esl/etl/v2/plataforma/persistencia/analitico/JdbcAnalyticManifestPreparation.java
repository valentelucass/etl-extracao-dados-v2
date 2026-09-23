package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** One bounded call prepares typed observations and coherent roots in the rollback session. */
public final class JdbcAnalyticManifestPreparation {
    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;

    public JdbcAnalyticManifestPreparation(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
    }

    public Receipt prepare(
            final UUID run, final UUID execution, final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        final var release =
                new RelationalSyntheticSource(page -> "[]")
                        .withAnalyticManifestDetails()
                        .contractRelease(DataExportTemplate.MANIFESTOS);
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    connection.prepareStatement(
                            "EXEC core.usp_prepare_analytic_manifest ?,?,?,?")) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.toString());
                sql.setString(2, execution.toString());
                sql.setString(3, release.contractFingerprint().sha256());
                sql.setTimestamp(4, Timestamp.from(clock.instant()));
                try (var rows = sql.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("ANA_MANIFEST_PREPARATION_RECEIPT_MISSING");
                    }
                    final var receipt =
                            new Receipt(rows.getLong(1), rows.getLong(2), rows.getLong(3));
                    if (rows.next()) {
                        throw new SQLException("ANA_MANIFEST_PREPARATION_RECEIPT_MULTIPLE");
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
                    throw new SQLException("ANA_MANIFEST_PREPARATION_SCOPE", "HY000", 53601);
                }
            }
        }
    }

    public record Receipt(long physicalRows, long roots, long blockedRoots) {
        public Receipt {
            if (physicalRows < 0 || roots < 0 || blockedRoots < 0 || blockedRoots > roots) {
                throw new IllegalArgumentException("ANA_MANIFEST_PREPARATION_EQUATION");
            }
        }
    }
}
