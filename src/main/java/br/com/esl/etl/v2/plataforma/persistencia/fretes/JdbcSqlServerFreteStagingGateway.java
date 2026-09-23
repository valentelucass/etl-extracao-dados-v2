package br.com.esl.etl.v2.plataforma.persistencia.fretes;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteStagingGateway;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageBatch;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord;
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

/** JDBC batched; uma chamada fechada por registro e nenhuma DML emitida pelo adapter. */
public final class JdbcSqlServerFreteStagingGateway implements FreteStagingGateway {
    private static final String CALL =
            "{call stg.usp_stage_frete_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private final DataSource dataSource;

    public JdbcSqlServerFreteStagingGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void stage(final FreteStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(final FreteStageBatch batch, final CancellationToken cancellationToken) {
        final FreteStageBatch required =
                Objects.requireNonNull(batch, "O batch de Fretes é obrigatório.");
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
            throw new StagingPersistenceException("o microbatch tipado de Fretes", error);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final FreteStageBatch batch,
            final FreteStageRecord record)
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
        nullableString(statement, 7, record.businessAliasJson());
        nullableString(statement, 8, record.statusRaw());
        nullableString(statement, 9, record.statusCode());
        nullableString(statement, 10, record.statusLabel());
        statement.setBoolean(11, record.terminal());
        nullableString(statement, 12, record.freshnessEvidenceJson());
        nullableInstant(statement, 13, record.freshnessAtUtc());
        nullableString(
                statement,
                14,
                record.freshnessOrigin() == null ? null : record.freshnessOrigin().name());
        nullableInstant(statement, 15, record.serviceAtUtc());
        nullableString(statement, 16, record.performanceEvidenceJson());
        nullableInstant(statement, 17, record.performanceAtUtc());
        nullableString(statement, 18, record.performanceOrigin());
        nullableString(statement, 19, record.cteFinalizationsJson());
        nullableString(statement, 20, record.financialJson());
        nullableString(statement, 21, record.relationCandidatesJson());
        nullableString(statement, 22, record.sidecarJson());
        statement.setString(23, record.quarantined() ? "QUARANTINE" : "VALID");
        nullableString(statement, 24, record.quarantineReasonCode());
        statement.setTimestamp(25, Timestamp.from(batch.observedAt()), utc());
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
