package br.com.esl.etl.v2.plataforma.persistencia.localizacaocargas;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaStagingGateway;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import javax.sql.DataSource;

/** JDBC batched de uma página 8656; nenhuma DML por registro existe no adapter. */
public final class JdbcSqlServerLocalizacaoCargaStagingGateway
        implements LocalizacaoCargaStagingGateway {
    private static final String CALL =
            "{call stg.usp_stage_localizacao_carga_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                    + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private final DataSource dataSource;

    public JdbcSqlServerLocalizacaoCargaStagingGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void stage(final LocalizacaoCargaStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(
            final LocalizacaoCargaStageBatch batch, final CancellationToken cancellationToken) {
        final LocalizacaoCargaStageBatch required =
                Objects.requireNonNull(batch, "O batch 8656 é obrigatório.");
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
                final int[] results = statement.executeBatch();
                validateBatchResults(results, required.size());
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
            throw new StagingPersistenceException(
                    "o microbatch tipado de Localização de Cargas", error);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final LocalizacaoCargaStageBatch batch,
            final LocalizacaoCargaStageRecord record)
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
        nullableString(statement, 7, record.serviceAtRaw());
        nullableInstant(statement, 8, record.serviceAtUtc());
        nullableEnum(statement, 9, record.serviceAtPresence());
        nullableEnum(statement, 10, record.serviceAtParseState());
        nullableString(statement, 11, record.invoicesVolumesRaw());
        nullableInteger(statement, 12, record.invoicesVolumes());
        nullableEnum(statement, 13, record.invoicesVolumesPresence());
        nullableEnum(statement, 14, record.invoicesVolumesParseState());
        nullableString(statement, 15, record.taxedWeightRaw());
        nullableDecimal(statement, 16, record.taxedWeight());
        nullableString(statement, 17, record.invoicesValueRaw());
        nullableDecimal(statement, 18, record.invoicesValue());
        nullableString(statement, 19, record.totalRaw());
        nullableDecimal(statement, 20, record.total());
        nullableString(statement, 21, record.statusRaw());
        nullableString(statement, 22, record.statusNormalized());
        statement.setBoolean(23, record.statusTerminal());
        nullableString(statement, 24, record.statusBranchNicknameProvenance());
        statement.setString(25, record.disposition().name());
        nullableString(statement, 26, record.quarantineReasonCode());
        statement.setTimestamp(27, Timestamp.from(batch.observedAt()), utc());
    }

    private static void validateBatchResults(final int[] results, final int expected)
            throws SQLException {
        if (results == null || results.length != expected) {
            throw new SQLException("Resultado agregado do microbatch 8656 inválido.");
        }
        for (final int result : results) {
            if (result == Statement.EXECUTE_FAILED) {
                throw new SQLException("Resultado agregado do microbatch 8656 inválido.");
            }
        }
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

    private static void nullableInteger(
            final CallableStatement statement, final int index, final Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void nullableDecimal(
            final CallableStatement statement, final int index, final java.math.BigDecimal value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void nullableEnum(
            final CallableStatement statement, final int index, final Enum<?> value)
            throws SQLException {
        nullableString(statement, index, value == null ? null : value.name());
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
