package br.com.esl.etl.v2.plataforma.persistencia.fretes;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FretePromotionGateway;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

/** Candidate set e aplicação set-based em shadow; sem SQL por registro. */
public final class JdbcSqlServerFretePromotionGateway implements FretePromotionGateway {
    private static final String PREPARE =
            "{call core.usp_prepare_frete_candidate_set(?, ?, ?, ?, ?)}";
    private static final String APPLY =
            "{call core.usp_apply_reconcile_publish_fretes(?, ?, ?, ?, ?)}";
    private final DataSource dataSource;

    public JdbcSqlServerFretePromotionGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void prepareCandidateSet(final ContractPromotionPermit permit) {
        prepareCandidateSet(permit, CancellationToken.none());
    }

    @Override
    public void prepareCandidateSet(
            final ContractPromotionPermit permit, final CancellationToken cancellationToken) {
        call(PREPARE, requireShadow(permit), false, requireCancellation(cancellationToken));
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit permit, final DataQualityPromotionPermit quality) {
        return applyReconcileAndPublish(permit, quality, CancellationToken.none());
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit permit,
            final DataQualityPromotionPermit quality,
            final CancellationToken cancellationToken) {
        final ContractPromotionPermit required = requireShadow(permit);
        if (!required.executionId()
                .equals(
                        Objects.requireNonNull(quality, "A autorização DQ é obrigatória.")
                                .executionId())) {
            throw new IllegalArgumentException("As autorizações de Fretes divergem.");
        }
        return call(APPLY, required, true, requireCancellation(cancellationToken));
    }

    private StagingPublicationResult call(
            final String sql,
            final ContractPromotionPermit permit,
            final boolean readsResult,
            final CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(sql)) {
            bind(statement, permit);
            if (!readsResult) {
                cancellation.throwIfCancellationRequested();
                statement.executeUpdate();
                return null;
            }
            cancellation.throwIfCancellationRequested();
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    failInvalidResult();
                }
                final StagingPublicationResult publication = read(result);
                if (result.next() || !permit.executionId().equals(publication.executionId())) {
                    failInvalidResult();
                }
                return publication;
            }
        } catch (final SQLException error) {
            throw new StagingPersistenceException("a promoção set-based de Fretes", error);
        }
    }

    private static ContractPromotionPermit requireShadow(final ContractPromotionPermit permit) {
        final ContractPromotionPermit required =
                Objects.requireNonNull(permit, "O permit é obrigatório.");
        if (required.dataEffect() != SourceDataEffect.SHADOW_UPSERT) {
            throw new IllegalArgumentException("Fretes aceita somente shadow upsert.");
        }
        return required;
    }

    private static CancellationToken requireCancellation(
            final CancellationToken cancellationToken) {
        return Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
    }

    private static void bind(
            final CallableStatement statement, final ContractPromotionPermit permit)
            throws SQLException {
        statement.setString(1, permit.executionId().toString());
        statement.setString(2, permit.contractFingerprint().version());
        statement.setString(3, permit.contractFingerprint().sha256());
        statement.setString(4, permit.configurationFingerprint().version());
        statement.setString(5, permit.configurationFingerprint().sha256());
    }

    private static StagingPublicationResult read(final ResultSet value) throws SQLException {
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        try {
            return new StagingPublicationResult(
                    requiredUuid(value, "execution_id"),
                    count(value, "candidate_rows"),
                    count(value, "inserted_rows"),
                    count(value, "updated_rows"),
                    count(value, "reactivated_rows"),
                    count(value, "noop_rows"),
                    count(value, "stale_noop_rows"),
                    instant(value, "reconciled_at_utc", calendar),
                    instant(value, "published_at_utc", calendar),
                    optional(value, "incremental_frontier_before_utc", calendar),
                    optional(value, "incremental_frontier_after_utc", calendar));
        } catch (final IllegalArgumentException error) {
            throw new SQLException("O resultado agregado de Fretes é inválido.", error);
        }
    }

    private static UUID requiredUuid(final ResultSet value, final String column)
            throws SQLException {
        final String raw = value.getString(column);
        if (raw == null) {
            failInvalidResult();
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException error) {
            throw new SQLException("O resultado agregado de Fretes é inválido.", error);
        }
    }

    private static long count(final ResultSet value, final String column) throws SQLException {
        final long count = value.getLong(column);
        if (value.wasNull() || count < 0) {
            throw new SQLException("Contagem de Fretes inválida.");
        }
        return count;
    }

    private static Instant instant(
            final ResultSet value, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp time = value.getTimestamp(column, calendar);
        if (time == null) {
            throw new SQLException("Timestamp de Fretes ausente.");
        }
        return time.toInstant();
    }

    private static Optional<Instant> optional(
            final ResultSet value, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp time = value.getTimestamp(column, calendar);
        return time == null ? Optional.empty() : Optional.of(time.toInstant());
    }

    private static void failInvalidResult() throws SQLException {
        throw new SQLException("O resultado agregado de Fretes é inválido.");
    }
}
