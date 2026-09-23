package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
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

/** Adaptador dos entrypoints set-based de Coletas, limitado a shadow upsert. */
public final class JdbcSqlServerColetaPromotionGateway implements ColetaPromotionGateway {

    private static final String PREPARE_CANDIDATE_SET =
            "{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}";
    private static final String APPLY_RECONCILE_AND_PUBLISH =
            "{call core.usp_apply_reconcile_publish_coletas(?, ?, ?, ?, ?)}";

    private final DataSource dataSource;

    public JdbcSqlServerColetaPromotionGateway(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource de Coletas é obrigatório.");
    }

    @Override
    public void prepareCandidateSet(final ContractPromotionPermit contractPermit) {
        prepareCandidateSet(contractPermit, CancellationToken.none());
    }

    @Override
    public void prepareCandidateSet(
            final ContractPromotionPermit contractPermit,
            final CancellationToken cancellationToken) {
        final ContractPromotionPermit permit = requireShadowUpsert(contractPermit);
        final CancellationToken cancellation = requireCancellation(cancellationToken);
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(PREPARE_CANDIDATE_SET)) {
            bindContractPermit(statement, permit);
            cancellation.throwIfCancellationRequested();
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new StagingPersistenceException(
                    "a preparação tipada do candidate set de Coletas", exception);
        }
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit contractPermit,
            final DataQualityPromotionPermit dataQualityPermit) {
        return applyReconcileAndPublish(
                contractPermit, dataQualityPermit, CancellationToken.none());
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit contractPermit,
            final DataQualityPromotionPermit dataQualityPermit,
            final CancellationToken cancellationToken) {
        final ContractPromotionPermit permit = requireShadowUpsert(contractPermit);
        final DataQualityPromotionPermit dataQuality =
                Objects.requireNonNull(
                        dataQualityPermit, "A autorização de Data Quality é obrigatória.");
        if (!permit.executionId().equals(dataQuality.executionId())) {
            throw new IllegalArgumentException("As autorizações de Coletas divergem da execução.");
        }
        final CancellationToken cancellation = requireCancellation(cancellationToken);
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(APPLY_RECONCILE_AND_PUBLISH)) {
            bindContractPermit(statement, permit);
            cancellation.throwIfCancellationRequested();
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    failInvalidResult();
                }
                final StagingPublicationResult result = readResult(resultSet);
                if (resultSet.next() || !permit.executionId().equals(result.executionId())) {
                    failInvalidResult();
                }
                return result;
            }
        } catch (final SQLException exception) {
            throw new StagingPersistenceException(
                    "a aplicação de sombra atômica de Coletas", exception);
        }
    }

    private static ContractPromotionPermit requireShadowUpsert(
            final ContractPromotionPermit contractPermit) {
        final ContractPromotionPermit permit =
                Objects.requireNonNull(contractPermit, "A autorização de contrato é obrigatória.");
        if (permit.dataEffect() != SourceDataEffect.SHADOW_UPSERT) {
            throw new IllegalArgumentException(
                    "A vertical de Coletas aceita somente shadow upsert.");
        }
        return permit;
    }

    private static CancellationToken requireCancellation(
            final CancellationToken cancellationToken) {
        return Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
    }

    private static void bindContractPermit(
            final CallableStatement statement, final ContractPromotionPermit permit)
            throws SQLException {
        statement.setString(1, permit.executionId().toString());
        statement.setString(2, permit.contractFingerprint().version());
        statement.setString(3, permit.contractFingerprint().sha256());
        statement.setString(4, permit.configurationFingerprint().version());
        statement.setString(5, permit.configurationFingerprint().sha256());
    }

    private static StagingPublicationResult readResult(final ResultSet resultSet)
            throws SQLException {
        final Calendar calendar = utcCalendar();
        try {
            return new StagingPublicationResult(
                    requiredUuid(resultSet, "execution_id"),
                    requiredCount(resultSet, "candidate_rows"),
                    requiredCount(resultSet, "inserted_rows"),
                    requiredCount(resultSet, "updated_rows"),
                    requiredCount(resultSet, "reactivated_rows"),
                    requiredCount(resultSet, "noop_rows"),
                    requiredCount(resultSet, "stale_noop_rows"),
                    requiredInstant(resultSet, "reconciled_at_utc", calendar),
                    requiredInstant(resultSet, "published_at_utc", calendar),
                    optionalInstant(resultSet, "incremental_frontier_before_utc", calendar),
                    optionalInstant(resultSet, "incremental_frontier_after_utc", calendar));
        } catch (final IllegalArgumentException exception) {
            throw new SQLException("O resultado agregado de Coletas é inválido.", exception);
        }
    }

    private static UUID requiredUuid(final ResultSet resultSet, final String column)
            throws SQLException {
        final String value = resultSet.getString(column);
        if (value == null) {
            failInvalidResult();
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException exception) {
            throw new SQLException("O resultado agregado de Coletas é inválido.", exception);
        }
    }

    private static long requiredCount(final ResultSet resultSet, final String column)
            throws SQLException {
        final long value = resultSet.getLong(column);
        if (resultSet.wasNull() || value < 0) {
            failInvalidResult();
        }
        return value;
    }

    private static Instant requiredInstant(
            final ResultSet resultSet, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column, calendar);
        if (value == null) {
            failInvalidResult();
        }
        return value.toInstant();
    }

    private static Optional<Instant> optionalInstant(
            final ResultSet resultSet, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column, calendar);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static void failInvalidResult() throws SQLException {
        throw new SQLException("O resultado agregado de Coletas é inválido.");
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
