package br.com.esl.etl.v2.plataforma.persistencia.staging;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
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

/**
 * Adaptador SQL Server síncrono. Cada chamada envia no máximo um lote explicitamente limitado em
 * uma única transação JDBC e fecha statement/conexão antes de devolver o controle ao streamer.
 */
public final class JdbcSqlServerStagingPromotionKernel implements StagingPromotionKernel {

    private static final String STAGE_RECORD =
            "{call stg.usp_stage_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String PREPARE_CANDIDATE_SET =
            "{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}";
    private static final String APPLY_RECONCILE_AND_PUBLISH =
            "{call core.usp_apply_reconcile_publish_execution(?, ?, ?, ?, ?)}";

    private final DataSource dataSource;

    public JdbcSqlServerStagingPromotionKernel(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource de staging é obrigatório.");
    }

    @Override
    public void stage(final StagingBatch batch) {
        Objects.requireNonNull(batch, "O lote de staging é obrigatório.");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(STAGE_RECORD)) {
                for (final StagingRecord record : batch.records()) {
                    bindRecord(statement, batch, record);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new StagingPersistenceException("o lote de staging", exception);
        }
    }

    @Override
    public void prepareCandidateSet(final ContractPromotionPermit contractPermit) {
        final ContractPromotionPermit requiredPermit =
                requireShadowUpsert(
                        contractPermit, "A autorização de contrato para promoção é obrigatória.");
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(PREPARE_CANDIDATE_SET)) {
            bindContractPermit(statement, requiredPermit);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new StagingPersistenceException("a preparação do candidate set", exception);
        }
    }

    @Override
    public StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit contractPermit,
            final DataQualityPromotionPermit dataQualityPermit) {
        final ContractPromotionPermit requiredPermit =
                requireShadowUpsert(
                        contractPermit, "A autorização de contrato para publicação é obrigatória.");
        final DataQualityPromotionPermit requiredDataQualityPermit =
                Objects.requireNonNull(
                        dataQualityPermit, "A autorização de Data Quality é obrigatória.");
        final UUID executionId = requiredPermit.executionId();
        if (!executionId.equals(requiredDataQualityPermit.executionId())) {
            throw new IllegalArgumentException(
                    "As autorizações de contrato e Data Quality divergem da execução.");
        }
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(APPLY_RECONCILE_AND_PUBLISH)) {
            bindContractPermit(statement, requiredPermit);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("O resultado agregado de publicação é inválido.");
                }
                final StagingPublicationResult result = readPublicationResult(resultSet);
                if (resultSet.next()) {
                    throw new SQLException("O resultado agregado de publicação é inválido.");
                }
                if (!executionId.equals(result.executionId())) {
                    throw new SQLException("O resultado agregado de publicação é inválido.");
                }
                return result;
            }
        } catch (final SQLException exception) {
            throw new StagingPersistenceException(
                    "a aplicação, reconciliação e publicação atômicas", exception);
        }
    }

    private static StagingPublicationResult readPublicationResult(final ResultSet resultSet)
            throws SQLException {
        final Calendar calendar = utcCalendar();
        try {
            return new StagingPublicationResult(
                    requiredUuid(resultSet, "execution_id"),
                    requiredNonNegativeLong(resultSet, "candidate_rows"),
                    requiredNonNegativeLong(resultSet, "inserted_rows"),
                    requiredNonNegativeLong(resultSet, "updated_rows"),
                    requiredNonNegativeLong(resultSet, "reactivated_rows"),
                    requiredNonNegativeLong(resultSet, "noop_rows"),
                    requiredNonNegativeLong(resultSet, "stale_noop_rows"),
                    requiredInstant(resultSet, "reconciled_at_utc", calendar),
                    requiredInstant(resultSet, "published_at_utc", calendar),
                    optionalInstant(resultSet, "incremental_frontier_before_utc", calendar),
                    optionalInstant(resultSet, "incremental_frontier_after_utc", calendar));
        } catch (final IllegalArgumentException exception) {
            throw new SQLException("O resultado agregado de publicação é inválido.", exception);
        }
    }

    private static ContractPromotionPermit requireShadowUpsert(
            final ContractPromotionPermit permit, final String nullMessage) {
        final ContractPromotionPermit required = Objects.requireNonNull(permit, nullMessage);
        if (required.dataEffect() != SourceDataEffect.SHADOW_UPSERT) {
            throw new IllegalArgumentException(
                    "O kernel de staging aceita somente autorização de shadow upsert.");
        }
        return required;
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

    private static UUID requiredUuid(final ResultSet resultSet, final String column)
            throws SQLException {
        final String value = resultSet.getString(column);
        if (value == null) {
            throw new SQLException("O resultado agregado de publicação é inválido.");
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException exception) {
            throw new SQLException("O resultado agregado de publicação é inválido.", exception);
        }
    }

    private static long requiredNonNegativeLong(final ResultSet resultSet, final String column)
            throws SQLException {
        final long value = resultSet.getLong(column);
        if (resultSet.wasNull() || value < 0) {
            throw new SQLException("O resultado agregado de publicação é inválido.");
        }
        return value;
    }

    private static Instant requiredInstant(
            final ResultSet resultSet, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column, calendar);
        if (value == null) {
            throw new SQLException("O resultado agregado de publicação é inválido.");
        }
        return value.toInstant();
    }

    private static Optional<Instant> optionalInstant(
            final ResultSet resultSet, final String column, final Calendar calendar)
            throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column, calendar);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static void bindRecord(
            final CallableStatement statement, final StagingBatch batch, final StagingRecord record)
            throws SQLException {
        statement.setString(1, batch.executionId().toString());
        statement.setInt(2, batch.batchNumber());
        statement.setInt(3, record.inputOrdinal());
        statement.setString(4, record.sourceKey());
        statement.setString(
                5, record.rowFingerprint() == null ? null : record.rowFingerprint().version());
        statement.setString(
                6, record.rowFingerprint() == null ? null : record.rowFingerprint().sha256());
        statement.setString(
                7,
                record.presenceFingerprint() == null
                        ? null
                        : record.presenceFingerprint().version());
        statement.setString(
                8,
                record.presenceFingerprint() == null
                        ? null
                        : record.presenceFingerprint().sha256());
        if (record.sourceFreshnessAt() == null) {
            statement.setNull(9, java.sql.Types.TIMESTAMP);
        } else {
            bindInstant(statement, 9, record.sourceFreshnessAt());
        }
        statement.setString(10, record.disposition().name());
        statement.setString(11, record.quarantineReasonCode());
        bindInstant(statement, 12, batch.stagedAt());
    }

    private static void rollback(final Connection connection, final SQLException original) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void bindInstant(
            final CallableStatement statement, final int parameter, final Instant instant)
            throws SQLException {
        statement.setTimestamp(parameter, Timestamp.from(instant), utcCalendar());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
