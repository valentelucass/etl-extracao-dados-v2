package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class RuntimeSqlObservationTest {
    @Test
    void returnsOnlyMeasuredScopedValuesAndClosesAllJdbcResources() {
        final var jdbc = new Fixture();
        final UUID execution = UUID.randomUUID();
        final String summary = RuntimeSqlObservation.read(jdbc.source(), execution);
        assertTrue(summary.contains("candidate_rows=UNKNOWN"));
        assertTrue(summary.contains("duration_ms=UNKNOWN"));
        assertTrue(summary.contains("source_lag=UNKNOWN watermark=UNKNOWN"));
        assertEquals(execution.toString(), jdbc.bound);
        assertEquals(20, jdbc.timeout);
        assertEquals(2, jdbc.fetch);
        assertEquals(3, jdbc.closed);
    }

    @Test
    void rejectsMissingDuplicateNegativeAndUnexpectedResultsWithoutInventingMeasures() {
        for (final String fault :
                List.of(
                        "empty",
                        "duplicate",
                        "shape",
                        "negative",
                        "null",
                        "state",
                        "health",
                        "quality",
                        "clock",
                        "extra",
                        "update",
                        "sql")) {
            final var jdbc = new Fixture();
            jdbc.fault = fault;
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> RuntimeSqlObservation.read(jdbc.source(), UUID.randomUUID()),
                            fault);
            assertEquals("RUNTIME_OBSERVATION_UNCONFIRMED", failure.getMessage());
            assertInstanceOf(SQLException.class, failure.getCause());
            assertTrue(jdbc.closed >= 2);
        }
    }

    @Test
    void preservesPresentTerminalDurationAndCandidateCounts() {
        final var jdbc = new Fixture();
        jdbc.values.put("candidate_rows", 2L);
        jdbc.values.put("duration_milliseconds", 15L);
        final String summary = RuntimeSqlObservation.read(jdbc.source(), UUID.randomUUID());
        assertTrue(summary.contains("candidate_rows=2"));
        assertTrue(summary.contains("duration_ms=15"));
    }

    private static final class Fixture {
        final Map<String, Object> values = new HashMap<>();
        String fault = "", bound;
        int cursor, closed, timeout, fetch;
        boolean wasNull;

        Fixture() {
            values.put("state", "NOT_FOUND");
            values.put("health", "UNKNOWN");
            values.put("quality", "ABSENT");
            values.put("pages", 0L);
            values.put("physical_rows", 0L);
            values.put("response_bytes", 0L);
            values.put("publications", 0L);
        }

        DataSource source() {
            return proxy(
                    DataSource.class,
                    (method, args) -> {
                        if (method.equals("getConnection")) {
                            return connection();
                        }
                        throw new AssertionError(method);
                    });
        }

        Connection connection() {
            return proxy(
                    Connection.class,
                    (method, args) -> {
                        if (method.equals("close")) {
                            closed++;
                            return null;
                        }
                        if (method.equals("prepareCall")) {
                            assertEquals("{call ctl.usp_runtime_observation(?)}", args[0]);
                            return statement();
                        }
                        throw new AssertionError(method);
                    });
        }

        CallableStatement statement() {
            return proxy(
                    CallableStatement.class,
                    (method, args) -> {
                        return switch (method) {
                            case "close" -> {
                                closed++;
                                yield null;
                            }
                            case "setQueryTimeout" -> {
                                timeout = (int) args[0];
                                yield null;
                            }
                            case "setFetchSize" -> {
                                fetch = (int) args[0];
                                yield null;
                            }
                            case "setString" -> {
                                bound = (String) args[1];
                                yield null;
                            }
                            case "getMoreResults" -> fault.equals("extra");
                            case "getUpdateCount" -> fault.equals("update") ? 1 : -1;
                            case "executeQuery" -> {
                                if (fault.equals("sql")) {
                                    throw new SQLException("sensitive-detail");
                                }
                                yield rows();
                            }
                            default -> throw new AssertionError(method);
                        };
                    });
        }

        ResultSet rows() {
            return proxy(
                    ResultSet.class,
                    (method, args) -> {
                        return switch (method) {
                            case "close" -> {
                                closed++;
                                yield null;
                            }
                            case "next" ->
                                    ++cursor
                                            <= (fault.equals("empty")
                                                    ? 0
                                                    : fault.equals("duplicate") ? 2 : 1);
                            case "getMetaData" ->
                                    proxy(
                                            ResultSetMetaData.class,
                                            (name, parameters) -> {
                                                assertEquals("getColumnCount", name);
                                                return fault.equals("shape") ? 11 : 10;
                                            });
                            case "getString" -> fault.equals(args[0]) ? "BAD" : values.get(args[0]);
                            case "getTimestamp" ->
                                    fault.equals("clock")
                                            ? null
                                            : Timestamp.valueOf("2026-09-08 00:00:00");
                            case "getLong" -> {
                                Object value = values.get(args[0]);
                                if (args[0].equals("pages") && fault.equals("null")) {
                                    value = null;
                                }
                                wasNull = value == null;
                                yield args[0].equals("pages") && fault.equals("negative")
                                        ? -1L
                                        : wasNull ? 0L : (long) value;
                            }
                            case "wasNull" -> wasNull;
                            default -> throw new AssertionError(method);
                        };
                    });
        }
    }

    private interface Call {
        Object invoke(String method, Object[] args) throws Exception;
    }

    private static <T> T proxy(final Class<T> type, final Call call) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (object, method, args) -> call.invoke(method.getName(), args)));
    }
}
