package br.com.esl.etl.v2.plataforma.persistencia.manifestos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageBatch;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerManifestoStagingGatewayTest {
    private static final Instant NOW = Instant.parse("2032-02-29T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void bindsRawObservationsWithDistinctPresenceAndChildrenInOneJdbcBatch() throws Exception {
        final var jdbc = new RecordingJdbc();
        final var batch = batch(false);
        new JdbcSqlServerManifestoStagingGateway(jdbc.source()).stage(batch);
        assertTrue(jdbc.sql.startsWith("{call stg.usp_stage_manifesto_observation("));
        assertEquals(1, jdbc.executions);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(2, jdbc.batches.size());
        final var first = jdbc.batches.get(0);
        final var second = jdbc.batches.get(1);
        assertEquals(20, first.size());
        assertEquals(batch.executionId().toString(), first.get(1));
        assertEquals("INTEGER:1", first.get(4));
        final var root = JSON.readTree((String) first.get(7));
        assertTrue(root.get("operational_comments").isNull());
        assertFalse(root.has("mft_uer_name"));
        final var metrics = JSON.readTree((String) first.get(8));
        assertEquals("VALUE", metrics.path("KM").path("presence").asText());
        assertEquals("0", metrics.path("KM").path("value").asText());
        assertEquals("VALUE", first.get(10));
        assertEquals("authorized", first.get(11));
        assertEquals("INTEGER:100", first.get(12));
        assertEquals("1".repeat(44), first.get(13));
        assertEquals("1", first.get(14));
        assertEquals(Timestamp.from(NOW), first.get(15));
        assertEquals("CREATED_AT", first.get(16));
        assertEquals("VALID", first.get(18));
        assertNull(first.get(19));
        assertEquals(Timestamp.from(NOW), first.get(20));
        assertEquals("NULL", second.get(10));
        assertNull(second.get(11));
        assertNull(second.get(12));
        assertNull(second.get(13));
        assertTrue(jdbc.utcOnly);
        assertTrue(jdbc.closed);
    }

    @Test
    void bindsQuarantineWithoutInventingAnIdentityOrTypedPayload() {
        final var jdbc = new RecordingJdbc();
        new JdbcSqlServerManifestoStagingGateway(jdbc.source()).stage(batch(true));
        final var row = jdbc.batches.get(0);
        assertNull(row.get(4));
        assertEquals("{}", row.get(5));
        assertEquals("{}", row.get(6));
        assertNull(row.get(7));
        assertNull(row.get(8));
        assertNull(row.get(10));
        assertNull(row.get(15));
        assertNull(row.get(16));
        assertNull(row.get(17));
        assertEquals("QUARANTINE", row.get(18));
        assertTrue(row.get(19) instanceof String);
    }

    @Test
    void preservesTheSqlFailureAndSuppressedRollbackCause() {
        final var jdbc = new RecordingJdbc();
        jdbc.failure = new SQLException("synthetic driver detail");
        jdbc.rollbackFailure = true;
        final var failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerManifestoStagingGateway(jdbc.source())
                                        .stage(batch(false)));
        assertEquals(jdbc.failure, failure.getCause());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertFalse(failure.getMessage().contains("driver detail"));
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closed);
    }

    @Test
    void cancellationAfterExecutionRollsBackBeforeCommit() {
        final var jdbc = new RecordingJdbc();
        final var cancellation = new CancellationSignal();
        jdbc.cancelAfterBatch = cancellation;
        assertThrows(
                RuntimeException.class,
                () ->
                        new JdbcSqlServerManifestoStagingGateway(jdbc.source())
                                .stage(batch(false), cancellation));
        assertEquals(1, jdbc.executions);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertTrue(jdbc.closed);
    }

    @Test
    void invalidArgumentsAndPriorCancellationNeverOpenJdbc() {
        final var jdbc = new RecordingJdbc();
        final var gateway = new JdbcSqlServerManifestoStagingGateway(jdbc.source());
        final var cancellation = new CancellationSignal();
        cancellation.cancel();
        assertThrows(
                NullPointerException.class, () -> new JdbcSqlServerManifestoStagingGateway(null));
        assertThrows(NullPointerException.class, () -> gateway.stage(null));
        assertThrows(NullPointerException.class, () -> gateway.stage(batch(false), null));
        assertThrows(RuntimeException.class, () -> gateway.stage(batch(false), cancellation));
        assertEquals(0, jdbc.opens);
    }

    private static ManifestoStageBatch batch(final boolean quarantine) {
        final var mapper = new ManifestoDataExportRecordMapper();
        final var first =
                JSON.createObjectNode()
                        .put("sequence_code", 1)
                        .put("created_at", NOW.toString())
                        .put("status", "closed")
                        .put("mdfe_status", "authorized")
                        .put("km", "0")
                        .putNull("operational_comments")
                        .put("mft_pfs_pck_sequence_code", 100)
                        .put("mft_mfs_key", "1".repeat(44))
                        .put("mft_mfs_number", 1);
        final var second =
                JSON.createObjectNode()
                        .put("sequence_code", 2)
                        .put("created_at", NOW.toString())
                        .put("status", "closed")
                        .putNull("mdfe_status")
                        .putNull("km");
        return new ManifestoStageBatch(
                UUID.randomUUID(),
                1,
                quarantine
                        ? List.of(mapper.map(1, JSON.createObjectNode()))
                        : List.of(mapper.map(1, first), mapper.map(2, second)),
                NOW);
    }

    private static final class RecordingJdbc {
        final Map<Integer, Object> parameters = new LinkedHashMap<>();
        final List<Map<Integer, Object>> batches = new ArrayList<>();
        int opens, executions, commits, rollbacks;
        boolean closed, rollbackFailure, utcOnly = true;
        String sql;
        SQLException failure;
        CancellationSignal cancelAfterBatch;

        DataSource source() {
            final CallableStatement statement =
                    proxy(
                            CallableStatement.class,
                            (p, method, args) -> {
                                final var name = method.getName();
                                if (name.startsWith("set")
                                        && args != null
                                        && args[0] instanceof Integer) {
                                    parameters.put(
                                            (Integer) args[0],
                                            name.equals("setNull") ? null : args[1]);
                                    if (name.equals("setTimestamp")) {
                                        utcOnly &=
                                                ((Calendar) args[2])
                                                        .getTimeZone()
                                                        .getID()
                                                        .equals("UTC");
                                    }
                                } else if (name.equals("addBatch")) {
                                    batches.add(new LinkedHashMap<>(parameters));
                                } else if (name.equals("executeBatch")) {
                                    executions++;
                                    if (failure != null) {
                                        throw failure;
                                    }
                                    if (cancelAfterBatch != null) {
                                        cancelAfterBatch.cancel();
                                    }
                                    return new int[batches.size()];
                                }
                                return defaultValue(method.getReturnType());
                            });
            final Connection connection =
                    proxy(
                            Connection.class,
                            (p, method, args) -> {
                                switch (method.getName()) {
                                    case "prepareCall" -> {
                                        sql = (String) args[0];
                                        return statement;
                                    }
                                    case "commit" -> commits++;
                                    case "rollback" -> {
                                        rollbacks++;
                                        if (rollbackFailure) {
                                            throw new SQLException("rollback detail");
                                        }
                                    }
                                    case "close" -> closed = true;
                                    default -> {
                                        return defaultValue(method.getReturnType());
                                    }
                                }
                                return defaultValue(method.getReturnType());
                            });
            return proxy(
                    DataSource.class,
                    (p, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            opens++;
                            return connection;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(final Class<?> type) {
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
}
