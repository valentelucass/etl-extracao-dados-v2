package br.com.esl.etl.v2.plataforma.persistencia.usuarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioNamePresence;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageBatch;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerUsuarioStagingGatewayTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000333");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T17:00:00Z");

    @Test
    void bindsOneTypedPageInOneSynchronousTransaction() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerUsuarioStagingGateway gateway =
                new JdbcSqlServerUsuarioStagingGateway(jdbc.dataSource());

        gateway.stage(batch());

        assertEquals(
                "{call stg.usp_stage_usuario_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(3, jdbc.batches.size());
        final Map<Integer, Object> valid = jdbc.batches.get(0);
        assertEquals(EXECUTION_ID.toString(), valid.get(1));
        assertEquals(7, valid.get(2));
        assertEquals(1, valid.get(3));
        assertEquals("INTEGER:1", valid.get(4));
        assertEquals("INTEGER", valid.get(5));
        assertEquals("VALUE", valid.get(6));
        assertEquals("Nome sintético", valid.get(7));
        assertEquals("VALID", valid.get(8));
        assertEquals(null, valid.get(9));
        assertEquals(Timestamp.from(OBSERVED_AT), valid.get(10));

        final Map<Integer, Object> absent = jdbc.batches.get(1);
        assertEquals("STRING:synthetic-user", absent.get(4));
        assertEquals("STRING", absent.get(5));
        assertEquals("ABSENT", absent.get(6));
        assertEquals(null, absent.get(7));

        final Map<Integer, Object> quarantine = jdbc.batches.get(2);
        assertEquals(null, quarantine.get(4));
        assertEquals(null, quarantine.get(5));
        assertEquals(null, quarantine.get(6));
        assertEquals(null, quarantine.get(7));
        assertEquals("QUARANTINE", quarantine.get(8));
        assertEquals("MISSING_SOURCE_KEY", quarantine.get(9));
        assertEquals(List.of(false), jdbc.autoCommitTransitions);
        assertEquals(1, jdbc.executeBatchCount);
        assertTrue(jdbc.committed);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
    }

    @Test
    void rollsBackAndPreservesBothSqlFailuresWithoutLeakingTheirDetails() {
        final SQLException batchFailure = new SQLException("synthetic-sensitive-batch");
        final SQLException rollbackFailure = new SQLException("synthetic-sensitive-rollback");
        final RecordingJdbc jdbc =
                new RecordingJdbc()
                        .failExecuteBatchWith(batchFailure)
                        .failRollbackWith(rollbackFailure);
        final JdbcSqlServerUsuarioStagingGateway gateway =
                new JdbcSqlServerUsuarioStagingGateway(jdbc.dataSource());

        final StagingPersistenceException exception =
                assertThrows(StagingPersistenceException.class, () -> gateway.stage(batch()));

        assertSame(batchFailure, exception.getCause());
        assertEquals(1, jdbc.rollbackAttempts);
        assertEquals(1, exception.getCause().getSuppressed().length);
        assertSame(rollbackFailure, exception.getCause().getSuppressed()[0]);
        assertFalse(jdbc.committed);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
        assertFalse(exception.getMessage().contains("synthetic-sensitive-batch"));
        assertFalse(exception.getMessage().contains("synthetic-sensitive-rollback"));
    }

    @Test
    void validatesTheBoundaryAndWrapsConnectionFailure() {
        final SQLException connectionFailure = new SQLException("synthetic-sensitive-connect");
        final DataSource unavailable =
                (DataSource)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("getConnection")) {
                                        throw connectionFailure;
                                    }
                                    return defaultValue(method);
                                });
        final JdbcSqlServerUsuarioStagingGateway gateway =
                new JdbcSqlServerUsuarioStagingGateway(unavailable);

        assertThrows(NullPointerException.class, () -> gateway.stage(null));
        final StagingPersistenceException exception =
                assertThrows(StagingPersistenceException.class, () -> gateway.stage(batch()));
        assertSame(connectionFailure, exception.getCause());
        assertInstanceOf(SQLException.class, exception.getCause());
        assertFalse(exception.getMessage().contains("synthetic-sensitive-connect"));
        assertThrows(
                NullPointerException.class, () -> new JdbcSqlServerUsuarioStagingGateway(null));
    }

    private static UsuarioStageBatch batch() {
        final ScopedSourceIdentity.SourceKey integerKey =
                new ScopedSourceIdentity.SourceKey(
                        ScopedSourceIdentity.WireType.INTEGER, "INTEGER:1");
        final ScopedSourceIdentity.SourceKey stringKey =
                new ScopedSourceIdentity.SourceKey(
                        ScopedSourceIdentity.WireType.STRING, "STRING:synthetic-user");
        return new UsuarioStageBatch(
                EXECUTION_ID,
                7,
                List.of(
                        UsuarioStageRecord.valid(
                                1, integerKey, UsuarioNamePresence.VALUE, "Nome sintético"),
                        UsuarioStageRecord.valid(2, stringKey, UsuarioNamePresence.ABSENT, null),
                        UsuarioStageRecord.quarantine(3, null, "MISSING_SOURCE_KEY")),
                OBSERVED_AT);
    }

    private static Object defaultValue(final Method method) {
        final Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static final class RecordingJdbc {

        private final List<Map<Integer, Object>> batches = new ArrayList<>();
        private final List<Boolean> autoCommitTransitions = new ArrayList<>();
        private String statementSql;
        private boolean committed;
        private boolean statementClosed;
        private boolean connectionClosed;
        private int executeBatchCount;
        private int rollbackAttempts;
        private SQLException executeBatchFailure;
        private SQLException rollbackFailure;

        private RecordingJdbc failExecuteBatchWith(final SQLException failure) {
            executeBatchFailure = failure;
            return this;
        }

        private RecordingJdbc failRollbackWith(final SQLException failure) {
            rollbackFailure = failure;
            return this;
        }

        private DataSource dataSource() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("getConnection")) {
                                    return connection();
                                }
                                return defaultValue(method);
                            });
        }

        private Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "prepareCall" -> {
                                        statementSql = (String) arguments[0];
                                        return statement();
                                    }
                                    case "setAutoCommit" -> {
                                        autoCommitTransitions.add((Boolean) arguments[0]);
                                        return null;
                                    }
                                    case "commit" -> {
                                        committed = true;
                                        return null;
                                    }
                                    case "rollback" -> {
                                        rollbackAttempts++;
                                        if (rollbackFailure != null) {
                                            throw rollbackFailure;
                                        }
                                        return null;
                                    }
                                    case "close" -> {
                                        connectionClosed = true;
                                        return null;
                                    }
                                    default -> {
                                        return defaultValue(method);
                                    }
                                }
                            });
        }

        private CallableStatement statement() {
            final Map<Integer, Object> parameters = new LinkedHashMap<>();
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "setString", "setInt" -> {
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "setNull" -> {
                                        parameters.put((Integer) arguments[0], null);
                                        return null;
                                    }
                                    case "setTimestamp" -> {
                                        assertEquals(
                                                "UTC",
                                                ((java.util.Calendar) arguments[2])
                                                        .getTimeZone()
                                                        .getID());
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "addBatch" -> {
                                        batches.add(
                                                Collections.unmodifiableMap(
                                                        new LinkedHashMap<>(parameters)));
                                        parameters.clear();
                                        return null;
                                    }
                                    case "executeBatch" -> {
                                        executeBatchCount++;
                                        if (executeBatchFailure != null) {
                                            throw executeBatchFailure;
                                        }
                                        return new int[batches.size()];
                                    }
                                    case "close" -> {
                                        statementClosed = true;
                                        return null;
                                    }
                                    default -> {
                                        return defaultValue(method);
                                    }
                                }
                            });
        }
    }
}
