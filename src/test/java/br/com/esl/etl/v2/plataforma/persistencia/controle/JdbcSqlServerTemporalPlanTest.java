package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerTemporalPlanTest {
    private static final Instant START = Instant.parse("2024-02-01T03:00:00Z");

    @Test
    void readsOverlappingMonthAndDaysInSqlOrderWithoutLoadingMoreThanTheBound() {
        final var rows = List.of(new long[] {0, 1}, new long[] {0, 29}, new long[] {27, 28});
        final var result = store(rows).readGapPage("a".repeat(64), 3, START);
        assertEquals(3, result.size());
        assertEquals(START.plusSeconds(29 * 86400L), result.get(1).endExclusive());
        assertThrows(
                IllegalStateException.class,
                () -> store(rows).readGapPage("a".repeat(64), 2, START));
    }

    @Test
    void stillRejectsReversedStartReversedEndAndDuplicateIntervals() {
        for (final var rows :
                List.of(
                        List.of(new long[] {2, 3}, new long[] {1, 2}),
                        List.of(new long[] {0, 29}, new long[] {0, 1}),
                        List.of(new long[] {0, 1}, new long[] {0, 1}))) {
            assertThrows(
                    IllegalStateException.class,
                    () -> store(rows).readGapPage("a".repeat(64), 3, START));
        }
    }

    private static JdbcSqlServerTemporalPlan store(final List<long[]> rows) {
        final int[] index = {-1};
        final var result =
                proxy(
                        ResultSet.class,
                        (method, args) ->
                                switch (method) {
                                    case "next" -> ++index[0] < rows.size();
                                    case "getString" ->
                                            args[0].equals("execution_id")
                                                    ? UUID.nameUUIDFromBytes(
                                                                    new byte[] {(byte) index[0]})
                                                            .toString()
                                                    : "NOT_STARTED";
                                    case "getTimestamp" ->
                                            Timestamp.from(
                                                    START.plusSeconds(
                                                            rows.get(index[0])[
                                                                            args[0].equals(
                                                                                            "partition_start_utc")
                                                                                    ? 0
                                                                                    : 1]
                                                                    * 86400L));
                                    case "close" -> null;
                                    default -> throw new AssertionError(method);
                                });
        final var statement =
                proxy(
                        CallableStatement.class,
                        (method, args) ->
                                switch (method) {
                                    case "executeQuery" -> result;
                                    case "setQueryTimeout" -> {
                                        assertEquals(20, args[0]);
                                        yield null;
                                    }
                                    case "setFetchSize",
                                                    "setString",
                                                    "setInt",
                                                    "setTimestamp",
                                                    "close" ->
                                            null;
                                    default -> throw new AssertionError(method);
                                });
        final var connection =
                proxy(
                        Connection.class,
                        (method, args) ->
                                switch (method) {
                                    case "prepareCall" -> {
                                        assertEquals(
                                                "{call ctl.usp_runtime_temporal_gaps(?,?,?)}",
                                                args[0]);
                                        yield statement;
                                    }
                                    case "close" -> null;
                                    default -> throw new AssertionError(method);
                                });
        return new JdbcSqlServerTemporalPlan(
                proxy(
                        DataSource.class,
                        (method, args) -> {
                            assertEquals("getConnection", method);
                            return connection;
                        }));
    }

    private static <T> T proxy(
            final Class<T> type,
            final java.util.function.BiFunction<String, Object[], Object> invocation) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> invocation.apply(method.getName(), args)));
    }
}
