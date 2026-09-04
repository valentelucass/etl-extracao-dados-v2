package br.com.esl.etl.v2.plataforma.persistencia.usuarios;

import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioStagingGateway;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageBatch;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import javax.sql.DataSource;

/** Adapter JDBC limitado a uma página e uma transação por chamada. */
public final class JdbcSqlServerUsuarioStagingGateway implements UsuarioStagingGateway {

    private static final String STAGE_USUARIO =
            "{call stg.usp_stage_usuario_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private final DataSource dataSource;

    public JdbcSqlServerUsuarioStagingGateway(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource de Usuários é obrigatório.");
    }

    @Override
    public void stage(final UsuarioStageBatch batch) {
        final UsuarioStageBatch required =
                Objects.requireNonNull(batch, "O batch de Usuários é obrigatório.");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(STAGE_USUARIO)) {
                for (int index = 0; index < required.size(); index++) {
                    bind(statement, required, required.recordAt(index));
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new StagingPersistenceException("o microbatch tipado de Usuários", exception);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final UsuarioStageBatch batch,
            final UsuarioStageRecord record)
            throws SQLException {
        statement.setString(1, batch.executionId().toString());
        statement.setInt(2, batch.batchNumber());
        statement.setInt(3, record.inputOrdinal());
        final ScopedSourceIdentity.SourceKey sourceKey = record.sourceKey();
        if (sourceKey == null) {
            statement.setNull(4, Types.NVARCHAR);
            statement.setNull(5, Types.NVARCHAR);
        } else {
            statement.setString(4, sourceKey.storageValue());
            statement.setString(5, sourceKey.wireType().name());
        }
        if (record.namePresence() == null) {
            statement.setNull(6, Types.NVARCHAR);
        } else {
            statement.setString(6, record.namePresence().name());
        }
        if (record.name() == null) {
            statement.setNull(7, Types.NVARCHAR);
        } else {
            statement.setString(7, record.name());
        }
        statement.setString(8, record.disposition().name());
        statement.setString(9, record.quarantineReasonCode());
        statement.setTimestamp(10, Timestamp.from(batch.observedAt()), utcCalendar());
    }

    private static void rollback(final Connection connection, final SQLException original) {
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
