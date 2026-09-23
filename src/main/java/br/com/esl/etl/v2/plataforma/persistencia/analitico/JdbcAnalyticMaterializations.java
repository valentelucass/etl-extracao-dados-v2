package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

/** Opt-in rollback session, set-based SQL and a single reconciled receipt. */
public final class JdbcAnalyticMaterializations {
    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;

    public JdbcAnalyticMaterializations(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
    }

    public Receipt freight(
            final AnalyticMaterializationRequest request, final CancellationToken cancellation)
            throws SQLException {
        return execute(
                request,
                cancellation,
                "EXEC mart.usp_materialize_analytic_freight ?,?,?,?,?,?,?,?");
    }

    public Receipt collectors(
            final AnalyticMaterializationRequest request, final CancellationToken cancellation)
            throws SQLException {
        return execute(
                request,
                cancellation,
                "EXEC mart.usp_materialize_analytic_collectors ?,?,?,?,?,?,?,?");
    }

    public Receipt manifests(
            final AnalyticMaterializationRequest request, final CancellationToken cancellation)
            throws SQLException {
        return execute(
                request,
                cancellation,
                "EXEC mart.usp_materialize_analytic_manifests ?,?,?,?,?,?,?,?");
    }

    private Receipt execute(
            final AnalyticMaterializationRequest request,
            final CancellationToken cancellation,
            final String statement)
            throws SQLException {
        Objects.requireNonNull(request);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql = connection.prepareStatement(statement)) {
                sql.setQueryTimeout(30);
                sql.setString(1, request.run().toString());
                sql.setString(2, request.receipt().toString());
                sql.setInt(3, request.referenceRevision());
                sql.setString(4, request.mode().name());
                sql.setBoolean(5, request.full());
                sql.setObject(6, request.start());
                sql.setObject(7, request.endExclusive());
                sql.setTimestamp(
                        8,
                        Timestamp.from(clock.instant()),
                        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
                try (var rows = sql.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("ANA_MATERIALIZATION_RECEIPT_MISSING");
                    }
                    final var receipt =
                            new Receipt(
                                    rows.getLong(1),
                                    rows.getLong(2),
                                    rows.getLong(3),
                                    rows.getLong(4),
                                    rows.getLong(5),
                                    rows.getLong(6));
                    if (rows.next()) {
                        throw new SQLException("ANA_MATERIALIZATION_RECEIPT_MULTIPLE");
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

    public record Receipt(
            long candidates, long inserts, long updates, long noops, long ready, long blocked) {
        public Receipt {
            if (candidates < 0
                    || inserts < 0
                    || updates < 0
                    || noops < 0
                    || ready < 0
                    || blocked < 0
                    || candidates != inserts + updates + noops
                    || candidates != ready + blocked) {
                throw new IllegalArgumentException("ANA_MATERIALIZATION_EQUATION");
            }
        }
    }
}
