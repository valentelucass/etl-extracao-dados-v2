package br.com.esl.etl.v2.plataforma.persistencia.cotacoes;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoStagingGateway;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageBatch;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageRecord;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import javax.sql.DataSource;

/** JDBC batched para uma página 6906; o SQL conserva a deduplicação set-based. */
public final class JdbcSqlServerCotacaoStagingGateway implements CotacaoStagingGateway {
    private static final String CALL =
            "{call stg.usp_stage_cotacao_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private final DataSource dataSource;

    public JdbcSqlServerCotacaoStagingGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void stage(final CotacaoStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(final CotacaoStageBatch batch, final CancellationToken cancellationToken) {
        final CotacaoStageBatch required =
                Objects.requireNonNull(batch, "O batch de Cotações é obrigatório.");
        final CancellationToken cancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(CALL)) {
                for (int index = 0; index < required.size(); index++) {
                    bind(statement, required, required.recordAt(index));
                    statement.addBatch();
                }
                cancellation.throwIfCancellationRequested();
                statement.executeBatch();
                cancellation.throwIfCancellationRequested();
                connection.commit();
            } catch (final SQLException error) {
                rollback(connection, error);
                throw error;
            } catch (final RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (final SQLException error) {
            throw new StagingPersistenceException("o microbatch tipado de Cotações", error);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final CotacaoStageBatch batch,
            final CotacaoStageRecord record)
            throws SQLException {
        statement.setString(1, batch.executionId().toString());
        statement.setInt(2, batch.batchNumber());
        statement.setInt(3, record.inputOrdinal());
        nullableString(
                statement,
                4,
                record.sourceKey() == null ? null : record.sourceKey().storageValue());
        nullableString(statement, 5, record.payloadJson());
        nullableString(statement, 6, record.fieldPresenceJson());
        nullableString(statement, 7, record.userNameNormalized());
        nullableInstant(statement, 8, record.nfseIssuedAtUtc());
        nullableInstant(statement, 9, record.cteIssuedAtUtc());
        nullableInstant(statement, 10, record.requestedAtUtc());
        if (record.freshnessBusinessDate() == null) {
            statement.setNull(11, Types.DATE);
        } else {
            statement.setObject(11, record.freshnessBusinessDate(), Types.DATE);
        }
        if (record.totalAmount() == null) {
            statement.setNull(12, Types.DECIMAL);
        } else {
            statement.setBigDecimal(12, record.totalAmount());
        }
        nullableString(statement, 13, record.currencyCode());
        nullableString(statement, 14, record.originUf());
        nullableString(statement, 15, record.destinationUf());
        statement.setString(16, record.quarantined() ? "QUARANTINE" : "VALID");
        nullableString(statement, 17, record.quarantineReasonCode());
        statement.setTimestamp(18, Timestamp.from(batch.observedAt()), utc());
    }

    private static void nullableString(
            final CallableStatement statement, final int index, final String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NVARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void nullableInstant(
            final CallableStatement statement, final int index, final java.time.Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value), utc());
        }
    }

    private static void rollback(final Connection connection, final Exception original) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
