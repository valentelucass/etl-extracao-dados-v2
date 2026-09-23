package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColetaExactTimeWriterTest {
    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "NULL", "INVALID", "VALID"})
    void preservesExactPairRawPresenceAndFallbackInTheSameTransaction(final String parse) {
        final var jdbc = new Jdbc();
        final var row =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("id", 17)
                        .put("request_date", "2036-01-20");
        switch (parse) {
            case "NULL" -> row.putNull("status_updated_at");
            case "INVALID" -> row.put("status_updated_at", "invalid");
            case "VALID" -> row.put("status_updated_at", "2036-01-20T10:00:00.123456789Z");
            default -> {
                assertEquals("ABSENT", parse);
            }
        }
        final var record = new ColetaDataExportRecordMapper().map(1, row);
        new JdbcSqlServerColetaStagingGateway(jdbc.source(), true)
                .stage(
                        new ColetaStageBatch(UUID.randomUUID(), 1, List.of(record), Instant.now()),
                        jdbc.signal);
        assertEquals(2, jdbc.sql.size());
        assertTrue(jdbc.sql.get(0).contains("usp_stage_coleta_record("));
        assertTrue(jdbc.sql.get(1).contains("usp_stage_coleta_exact_time("));
        assertEquals(record.freshnessAtUtc().getEpochSecond(), jdbc.exact.get(4));
        assertEquals(record.freshnessAtUtc().getNano(), jdbc.exact.get(5));
        assertEquals(parse, jdbc.exact.get(8));
        assertEquals(parse.equals("VALID") ? "NONE" : "NATIVE_" + parse, jdbc.exact.get(9));
        assertEquals(
                parse.equals("VALID") || parse.equals("INVALID") ? "VALUE" : parse,
                jdbc.exact.get(6));
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(2, jdbc.closedStatements);
        assertEquals(1, jdbc.closedConnections);
        assertEquals(List.of(20, 20), jdbc.timeouts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SQL_FAILURE", "CANCEL_AFTER_TYPED_WRITE"})
    void exactFailureOrCancellationRollsBackTheTypedWriteAndClosesResources(final String failure) {
        final var jdbc = new Jdbc();
        jdbc.failure = failure;
        final var record =
                new ColetaDataExportRecordMapper()
                        .map(
                                1,
                                JsonNodeFactory.instance
                                        .objectNode()
                                        .put("id", 17)
                                        .put("request_date", "2036-01-20"));
        assertThrows(
                RuntimeException.class,
                () ->
                        new JdbcSqlServerColetaStagingGateway(jdbc.source(), true)
                                .stage(
                                        new ColetaStageBatch(
                                                UUID.randomUUID(),
                                                1,
                                                List.of(record),
                                                Instant.now()),
                                        jdbc.signal));
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(2, jdbc.closedStatements);
        assertEquals(1, jdbc.closedConnections);
    }

    @Test
    void defaultBindingNeverActivatesTheExactComplement() {
        final var jdbc = new Jdbc();
        final var record =
                new ColetaDataExportRecordMapper()
                        .map(1, JsonNodeFactory.instance.objectNode().put("id", 17));
        new JdbcSqlServerColetaStagingGateway(jdbc.source())
                .stage(new ColetaStageBatch(UUID.randomUUID(), 1, List.of(record), Instant.now()));
        assertEquals(1, jdbc.sql.size());
        assertFalse(jdbc.sql.get(0).contains("exact_time"));
    }

    private static final class Jdbc {
        private final CancellationSignal signal = new CancellationSignal();
        private final List<String> sql = new ArrayList<>();
        private final List<Integer> timeouts = new ArrayList<>();
        private final Map<Integer, Object> exact = new HashMap<>();
        private int commits;
        private int rollbacks;
        private int closedStatements;
        private int closedConnections;
        private String failure = "NONE";

        private DataSource source() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getConnection")) {
                                    return connection();
                                }
                                throw new AssertionError(method.getName());
                            });
        }

        private Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "setAutoCommit" -> {
                                        assertEquals(false, args[0]);
                                        return null;
                                    }
                                    case "prepareCall" -> {
                                        sql.add((String) args[0]);
                                        return statement(sql.size() == 2);
                                    }
                                    case "commit" -> {
                                        commits++;
                                        return null;
                                    }
                                    case "rollback" -> {
                                        rollbacks++;
                                        return null;
                                    }
                                    case "close" -> {
                                        closedConnections++;
                                        return null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                }
                            });
        }

        private CallableStatement statement(final boolean temporal) {
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            CallableStatement.class.getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, args) -> {
                                final String name = method.getName();
                                if (name.equals("setQueryTimeout")) {
                                    timeouts.add((Integer) args[0]);
                                    return null;
                                }
                                if (name.startsWith("set")) {
                                    if (temporal) {
                                        exact.put(
                                                (Integer) args[0],
                                                name.equals("setNull") ? null : args[1]);
                                    }
                                    return null;
                                }
                                switch (name) {
                                    case "addBatch" -> {
                                        return null;
                                    }
                                    case "close" -> {
                                        closedStatements++;
                                        return null;
                                    }
                                    case "executeBatch" -> {
                                        if (temporal && failure.equals("SQL_FAILURE")) {
                                            throw new SQLException("synthetic-exact-failure");
                                        }
                                        if (!temporal
                                                && failure.equals("CANCEL_AFTER_TYPED_WRITE")) {
                                            signal.cancel();
                                        }
                                        return new int[] {1};
                                    }
                                    default -> throw new AssertionError(name);
                                }
                            });
        }
    }
}
