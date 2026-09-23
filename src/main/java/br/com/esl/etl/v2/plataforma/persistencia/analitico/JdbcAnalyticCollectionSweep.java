package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Four SQL-verified captures per observation; kernel eligibility is evaluated before application.
 */
public final class JdbcAnalyticCollectionSweep {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticCollectionSweep(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public Prepared prepare(
            final SyntheticCollectionSnapshot snapshot,
            final UUID cycle,
            final List<UUID> captures,
            final CancellationToken token)
            throws SQLException {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(cycle);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        if (captures == null || captures.size() != 4 || captures.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_PROOF_BOUND");
        }
        final var actual = List.copyOf(captures);
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("ordinal", Types.TINYINT);
        table.addColumnMetadata("execution_id", Types.NVARCHAR);
        for (int index = 0; index < 4; index++) {
            table.addRow(index + 1, actual.get(index).toString());
        }
        final var scope = snapshot.scope();
        final var contract =
                new RelationalSyntheticSource(page -> "[]")
                        .withAnalyticCollectionDetails()
                        .contractRelease(DataExportTemplate.COLETAS)
                        .contractFingerprint()
                        .sha256();
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC recon.usp_prepare_analytic_collection_sweep ?,?,?,?,?,?,?,?,?,?,?,?")) {
                sql.setQueryTimeout(30);
                sql.setString(1, snapshot.run().toString());
                sql.setString(2, cycle.toString());
                sql.setObject(3, snapshot.date());
                sql.setInt(4, snapshot.universeRoots());
                sql.setBoolean(5, snapshot.omitFirst());
                sql.setString(6, contract);
                sql.setString(7, snapshot.fingerprint());
                sql.setString(8, scope.policyFingerprint());
                sql.setString(9, scope.scopeFingerprint());
                sql.setString(10, scope.bindingFingerprint());
                sql.setStructured(11, "recon.analytic_collection_sweep_proofs", table);
                sql.setNString(12, snapshot.universeJson());
                try (var row = sql.executeQuery()) {
                    if (!row.next()) {
                        throw new SQLException("ANA_COLLECTION_SWEEP_RECEIPT_MISSING");
                    }
                    final String receipt = row.getString(1);
                    final int roots = row.getInt(2);
                    final int pages = row.getInt(3);
                    if (roots != snapshot.expectedRoots() || pages < 2 || row.next()) {
                        throw new SQLException("ANA_COLLECTION_SWEEP_RECEIPT_EQUATION");
                    }
                    final var assessment = snapshot.assessVerifiedCaptures(actual, pages);
                    if (assessment.disposition()
                            != SweepPreviewAssessment.Disposition
                                    .PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY) {
                        throw new SQLException("ANA_COLLECTION_SWEEP_KERNEL_BLOCKED");
                    }
                    token.throwIfCancellationRequested();
                    return new Prepared(snapshot, cycle, actual, receipt, roots, pages);
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

    public Receipt apply(final Prepared proof, final CancellationToken token) throws SQLException {
        Objects.requireNonNull(proof);
        if (proof.snapshot().declaredUniverse() != null) {
            throw new IllegalArgumentException("ANA_COLLECTION_DECLARED_PREVIEW_ONLY");
        }
        Objects.requireNonNull(token).throwIfCancellationRequested();
        final var assessment =
                proof.snapshot().assessVerifiedCaptures(proof.captures(), proof.pages());
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    connection.prepareStatement(
                            "EXEC recon.usp_apply_analytic_collection_sweep ?,?,?,?")) {
                sql.setQueryTimeout(30);
                sql.setString(1, proof.snapshot().run().toString());
                sql.setString(2, proof.cycle().toString());
                sql.setString(3, proof.receiptFingerprint());
                sql.setString(4, assessment.disposition().name());
                try (var row = sql.executeQuery()) {
                    if (!row.next()) {
                        throw new SQLException("ANA_COLLECTION_SWEEP_APPLICATION_RECEIPT");
                    }
                    final var result =
                            new Receipt(
                                    row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4));
                    if (row.next()) {
                        throw new SQLException("ANA_COLLECTION_SWEEP_APPLICATION_RECEIPT");
                    }
                    token.throwIfCancellationRequested();
                    return result;
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

    public record Prepared(
            SyntheticCollectionSnapshot snapshot,
            UUID cycle,
            List<UUID> captures,
            String receiptFingerprint,
            int roots,
            int pages) {
        public Prepared {
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(cycle);
            if (captures == null
                    || captures.size() != 4
                    || receiptFingerprint == null
                    || !receiptFingerprint.matches("[0-9a-f]{64}")
                    || roots != snapshot.expectedRoots()
                    || pages < 2
                    || pages > 10000) {
                throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_PREPARED_INVALID");
            }
            captures = List.copyOf(captures);
        }
    }

    public record Receipt(long candidates, long confirmations, long reactivated, long unchanged) {
        public Receipt {
            if (candidates < 0 || confirmations < 0 || reactivated < 0 || unchanged < 0) {
                throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_APPLICATION_EQUATION");
            }
        }
    }
}
