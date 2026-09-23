package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoPromotionGateway;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.cotacoes.JdbcSqlServerCotacaoPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Scope and typed-field checks surround the original contract/DQ-bound promotion. */
public final class JdbcAnalyticQuotePromotion implements CotacaoPromotionGateway {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final int revision;
    private final JdbcSqlServerCotacaoPromotionGateway original;

    public JdbcAnalyticQuotePromotion(
            final ColetaTemporalLaboratorySession session, final UUID run, final int revision) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        if (revision < 1 || revision > 100000) {
            throw new IllegalArgumentException("ANA_QUOTE_REFERENCE_REVISION");
        }
        this.revision = revision;
        original = new JdbcSqlServerCotacaoPromotionGateway(session);
    }

    public void bindReference(final UUID execution, final long release) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_bind_analytic_quote_runtime_reference ?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, execution.toString());
            sql.setInt(3, revision);
            sql.setLong(4, release);
            sql.execute();
        }
    }

    @Override
    public void prepareCandidateSet(final ContractPromotionPermit permit) {
        original.prepareCandidateSet(permit);
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit permit,
            final DataQualityPromotionPermit quality,
            final long release) {
        return applyReconcileAndPublish(permit, quality, release, CancellationToken.none());
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit permit,
            final DataQualityPromotionPermit quality,
            final long release,
            final CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC core.usp_preflight_analytic_quotes ?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, permit.executionId().toString());
            sql.setInt(3, revision);
            sql.setLong(4, release);
            sql.execute();
        } catch (final SQLException failure) {
            throw new StagingPersistenceException("analytic quote preflight", failure);
        }
        return original.applyReconcileAndPublish(permit, quality, release, cancellation);
    }

    public Receipt prepare(final UUID execution) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement("EXEC core.usp_prepare_analytic_quotes ?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, execution.toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("ANA_QUOTE_PREPARATION_RECEIPT");
                }
                final var result =
                        new Receipt(
                                rows.getLong(1),
                                rows.getLong(2),
                                rows.getLong(3),
                                rows.getLong(4),
                                rows.getLong(5),
                                rows.getLong(6),
                                rows.getLong(7));
                if (rows.next()
                        || result.roots()
                                != result.inserts()
                                        + result.updates()
                                        + result.noops()
                                        + result.stale()
                                        + result.blocked()
                        || result.physical() < result.roots()) {
                    throw new SQLException("ANA_QUOTE_PREPARATION_EQUATION");
                }
                return result;
            }
        }
    }

    public record Receipt(
            long physical,
            long roots,
            long inserts,
            long updates,
            long noops,
            long stale,
            long blocked) {}
}
