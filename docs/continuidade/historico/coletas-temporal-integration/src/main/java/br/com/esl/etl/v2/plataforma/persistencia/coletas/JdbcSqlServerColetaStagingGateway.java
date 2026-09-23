package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
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

/** Adaptador JDBC de uma página 6908, com uma transação e sem DML por atributo na JVM. */
public final class JdbcSqlServerColetaStagingGateway implements ColetaStagingGateway {

    private static final String STAGE_COLETA =
            "{call stg.usp_stage_coleta_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private final DataSource dataSource;

    public JdbcSqlServerColetaStagingGateway(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource de Coletas é obrigatório.");
    }

    @Override
    public void stage(final ColetaStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(final ColetaStageBatch batch, final CancellationToken cancellationToken) {
        final ColetaStageBatch required =
                Objects.requireNonNull(batch, "O batch de Coletas é obrigatório.");
        final CancellationToken cancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(STAGE_COLETA)) {
                for (int index = 0; index < required.size(); index++) {
                    bind(statement, required, required.recordAt(index));
                    statement.addBatch();
                }
                cancellation.throwIfCancellationRequested();
                statement.executeBatch();
                cancellation.throwIfCancellationRequested();
                connection.commit();
            } catch (final SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (final RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new StagingPersistenceException("o microbatch tipado de Coletas", exception);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final ColetaStageBatch batch,
            final ColetaStageRecord record)
            throws SQLException {
        statement.setString(1, batch.executionId().toString());
        statement.setInt(2, batch.batchNumber());
        statement.setInt(3, record.inputOrdinal());
        final ScopedSourceIdentity.SourceKey sourceKey = record.sourceKey();
        if (sourceKey == null) {
            nulls(statement, 4, 5);
        } else {
            statement.setString(4, sourceKey.storageValue());
            statement.setString(5, sourceKey.wireType().name());
        }
        if (record.disposition()
                == br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition.QUARANTINE) {
            nulls(statement, 6, 14);
            statement.setNull(15, Types.BIT);
            statement.setNull(16, Types.NVARCHAR);
            statement.setNull(17, Types.SMALLINT);
            statement.setNull(18, Types.NVARCHAR);
            statement.setNull(19, Types.TIMESTAMP);
            statement.setNull(20, Types.NVARCHAR);
        } else {
            statement.setString(6, record.sequenceCodePresence().name());
            // V010 persists the scalar attribute inside a JSON object (ISJSON without VALUE).
            nullableString(
                    statement,
                    7,
                    record.sequenceCodeJson() == null
                            ? null
                            : "{\"value\":" + record.sequenceCodeJson() + "}");
            statement.setString(8, record.payloadJson());
            statement.setString(9, record.fieldPresenceJson());
            statement.setString(10, record.relationCandidatesJson());
            final ColetaStatus.Resolved status = record.status();
            nullableString(statement, 11, status.raw());
            nullableString(statement, 12, status.code());
            nullableString(statement, 13, status.label());
            statement.setString(14, ColetaStatus.CATALOG_VERSION);
            statement.setBoolean(15, status.terminal());
            statement.setString(16, status.occurrenceAction());
            statement.setInt(17, status.attempts());
            nullableString(statement, 18, record.freshnessRaw());
            nullableInstant(statement, 19, record.freshnessAtUtc());
            statement.setString(20, record.freshnessOrigin().name());
        }
        statement.setString(21, record.disposition().name());
        nullableString(statement, 22, record.quarantineReasonCode());
        statement.setTimestamp(23, Timestamp.from(batch.observedAt()), utcCalendar());
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
            statement.setTimestamp(index, Timestamp.from(value), utcCalendar());
        }
    }

    private static void nulls(final CallableStatement statement, final int first, final int last)
            throws SQLException {
        for (int index = first; index <= last; index++) {
            statement.setNull(index, Types.NVARCHAR);
        }
    }

    private static void rollback(final Connection connection, final Exception original) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
