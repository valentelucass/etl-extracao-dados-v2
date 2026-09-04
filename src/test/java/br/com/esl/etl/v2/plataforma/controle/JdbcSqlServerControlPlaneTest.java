package br.com.esl.etl.v2.plataforma.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerControlPlaneTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID CYCLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final String FINGERPRINT = "d".repeat(64);

    @Test
    void invokesOnlyParameterizedControlPlaneProcedures() {
        final RecordingDataSource dataSource = new RecordingDataSource();
        final JdbcSqlServerControlPlane controlPlane = new JdbcSqlServerControlPlane(dataSource);
        final ExecutionPartitionKey key = partition(ExecutionMode.INCREMENTAL);

        controlPlane.registerSource(new ControlPlaneSource("SYNTHETIC_SOURCE", "DATA_EXPORT", NOW));
        controlPlane.startCycle(
                new ControlPlaneCycle(
                        CYCLE_ID, new ImmutableFingerprint("plan-v1", FINGERPRINT), NOW));
        controlPlane.startExecution(start(key));
        controlPlane.heartbeat(EXECUTION_ID, NOW.plusSeconds(1), Duration.ofMinutes(2));
        controlPlane.recordPage(
                new ControlPlanePage(EXECUTION_ID, 1, 1, 3, 2, 2, 512, false, NOW.plusSeconds(2)));
        controlPlane.recordCounts(
                new ControlPlaneCounts(
                        EXECUTION_ID, "STAGE", 3, 2, 0, 2, 0, 1, NOW.plusSeconds(3)));
        controlPlane.transition(
                new ControlPlaneTransition(
                        EXECUTION_ID,
                        ExecutionState.EXTRACTING,
                        ExecutionState.EXTRACTED,
                        "STAGE_OK",
                        NOW.plusSeconds(4)));
        controlPlane.registerIncrementalFrontier(key, NOW, NOW.plusSeconds(5));
        final ControlPlaneRecoveryResult recovery = controlPlane.recoverStaleExecutions();

        assertEquals(2, recovery.recoveredExecutions());
        assertEquals(9, dataSource.calls().size());
        assertEquals(
                "{call ctl.usp_control_plane_register_source(?, ?, ?)}",
                dataSource.calls().get(0).statementSql());
        assertEquals("SYNTHETIC_SOURCE", dataSource.calls().get(0).parameters().get(1));
        assertEquals(
                "{call ctl.usp_control_plane_start_cycle(?, ?, ?, ?)}",
                dataSource.calls().get(1).statementSql());
        assertEquals("plan-v1", dataSource.calls().get(1).parameters().get(2));
        assertEquals(FINGERPRINT, dataSource.calls().get(1).parameters().get(3));
        assertEquals(
                "{call ctl.usp_control_plane_start_execution(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(2).statementSql());
        assertEquals(EXECUTION_ID.toString(), dataSource.calls().get(2).parameters().get(1));
        assertEquals("LOCAL_SHADOW", dataSource.calls().get(2).parameters().get(3));
        assertEquals("INCREMENTAL", dataSource.calls().get(2).parameters().get(7));
        assertEquals(Timestamp.from(NOW), dataSource.calls().get(2).parameters().get(8));
        assertEquals(60, dataSource.calls().get(2).parameters().get(17));
        assertEquals(
                "{call ctl.usp_control_plane_heartbeat_lease(?, ?, ?, ?)}",
                dataSource.calls().get(3).statementSql());
        assertEquals(120, dataSource.calls().get(3).parameters().get(3));
        assertEquals(
                "{call ctl.usp_control_plane_record_page(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(4).statementSql());
        assertEquals(512L, dataSource.calls().get(4).parameters().get(7));
        assertEquals("NONE", dataSource.calls().get(4).parameters().get(10));
        assertEquals(
                "{call ctl.usp_control_plane_record_counts(?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(5).statementSql());
        assertEquals(1L, dataSource.calls().get(5).parameters().get(9));
        assertEquals("STAGE_OK", dataSource.calls().get(6).parameters().get(4));
        assertEquals(
                "{call ctl.usp_control_plane_register_incremental_frontier(?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(7).statementSql());
        assertEquals(
                "{call ctl.usp_control_plane_recover_stale_executions()}",
                dataSource.calls().get(8).statementSql());
        assertEquals(Map.of(), dataSource.calls().get(8).parameters());
    }

    @Test
    void retainsTheSqlCauseWhileKeepingTheOperationMessageSanitized() {
        final String driverDetail = "synthetic-driver-detail";
        final JdbcSqlServerControlPlane controlPlane =
                new JdbcSqlServerControlPlane(new FailingDataSource(driverDetail));

        final ControlPlanePersistenceException exception =
                assertThrows(
                        ControlPlanePersistenceException.class,
                        () ->
                                controlPlane.startExecution(
                                        start(partition(ExecutionMode.INCREMENTAL))));

        assertEquals(
                "O control plane não pôde concluir o início da execução.", exception.getMessage());
        assertEquals(driverDetail, exception.getCause().getMessage());
        assertFalse(exception.getMessage().contains(driverDetail));
    }

    @Test
    void refusesAnIncrementalFrontierForAnyOtherModeAndAnUnsafeLeaseExtension() {
        final JdbcSqlServerControlPlane controlPlane =
                new JdbcSqlServerControlPlane(new RecordingDataSource());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        controlPlane.registerIncrementalFrontier(
                                partition(ExecutionMode.SWEEP), NOW, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> controlPlane.heartbeat(EXECUTION_ID, NOW, Duration.ofMillis(500)));
    }

    @Test
    void refusesAnAbsentDuplicatedNullOrNegativeRecoverySummary() {
        final List<List<Long>> invalidRows =
                List.of(List.of(), List.of(1L, 2L), Collections.singletonList(null), List.of(-1L));

        for (final List<Long> rows : invalidRows) {
            final JdbcSqlServerControlPlane controlPlane =
                    new JdbcSqlServerControlPlane(new RecordingDataSource(rows));
            final ControlPlanePersistenceException exception =
                    assertThrows(
                            ControlPlanePersistenceException.class,
                            controlPlane::recoverStaleExecutions);

            assertEquals(
                    "O control plane não pôde concluir a recuperação de leases expiradas.",
                    exception.getMessage());
            assertFalse(exception.getMessage().contains("recovered_executions"));
        }
    }

    private static ControlPlaneStart start(final ExecutionPartitionKey key) {
        return new ControlPlaneStart(
                EXECUTION_ID,
                CYCLE_ID,
                key,
                "INTERVAL",
                new ImmutableFingerprint("contract-v1", FINGERPRINT),
                new ImmutableFingerprint("config-v1", FINGERPRINT),
                "synthetic-idempotency-key",
                Optional.empty(),
                Duration.ofMinutes(1),
                NOW);
    }

    private static ExecutionPartitionKey partition(final ExecutionMode mode) {
        return new ExecutionPartitionKey(
                "LOCAL_SHADOW",
                "SYNTHETIC_SOURCE",
                "SYNTHETIC_TENANT",
                "SYNTHETIC_ENTITY",
                mode,
                NOW,
                NOW.plusSeconds(60));
    }

    private record RecordedCall(String statementSql, Map<Integer, Object> parameters) {}

    private static class RecordingDataSource implements DataSource {

        private final List<RecordedCall> calls = new ArrayList<>();
        private final List<Long> recoveryRows;

        private RecordingDataSource() {
            this(List.of(2L));
        }

        private RecordingDataSource(final List<Long> recoveryRows) {
            this.recoveryRows = Collections.unmodifiableList(new ArrayList<>(recoveryRows));
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connection(calls, recoveryRows);
        }

        @Override
        public Connection getConnection(final String username, final String password)
                throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(final Class<T> interfaceType) throws SQLException {
            throw new SQLException("Unsupported test operation.");
        }

        @Override
        public boolean isWrapperFor(final Class<?> interfaceType) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(final int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        private List<RecordedCall> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class FailingDataSource extends RecordingDataSource {

        private final String detail;

        private FailingDataSource(final String detail) {
            this.detail = detail;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException(detail);
        }
    }

    private static Connection connection(
            final List<RecordedCall> calls, final List<Long> recoveryRows) {
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    if ("prepareCall".equals(method.getName())) {
                        return callableStatement(calls, recoveryRows, (String) arguments[0]);
                    }
                    return defaultValue(method);
                };
        return (Connection)
                Proxy.newProxyInstance(
                        JdbcSqlServerControlPlaneTest.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        handler);
    }

    private static CallableStatement callableStatement(
            final List<RecordedCall> calls,
            final List<Long> recoveryRows,
            final String statementSql) {
        final Map<Integer, Object> parameters = new LinkedHashMap<>();
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "setString", "setInt", "setLong", "setBoolean" -> {
                            parameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        case "setTimestamp" -> {
                            assertEquals(3, arguments.length);
                            assertEquals(
                                    "UTC",
                                    ((java.util.Calendar) arguments[2]).getTimeZone().getID());
                            parameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        case "setNull" -> {
                            parameters.put((Integer) arguments[0], null);
                            return null;
                        }
                        case "executeUpdate" -> {
                            calls.add(
                                    new RecordedCall(
                                            statementSql,
                                            Collections.unmodifiableMap(
                                                    new LinkedHashMap<>(parameters))));
                            return 0;
                        }
                        case "executeQuery" -> {
                            calls.add(
                                    new RecordedCall(
                                            statementSql,
                                            Collections.unmodifiableMap(
                                                    new LinkedHashMap<>(parameters))));
                            return recoveryResultSet(recoveryRows);
                        }
                        default -> {
                            return defaultValue(method);
                        }
                    }
                };
        return (CallableStatement)
                Proxy.newProxyInstance(
                        JdbcSqlServerControlPlaneTest.class.getClassLoader(),
                        new Class<?>[] {CallableStatement.class},
                        handler);
    }

    private static ResultSet recoveryResultSet(final List<Long> rows) {
        final int[] cursor = {-1};
        final boolean[] wasNull = {false};
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "next" -> {
                            cursor[0]++;
                            return cursor[0] < rows.size();
                        }
                        case "getLong" -> {
                            assertEquals("recovered_executions", arguments[0]);
                            final Long value = rows.get(cursor[0]);
                            wasNull[0] = value == null;
                            return value == null ? 0L : value;
                        }
                        case "wasNull" -> {
                            return wasNull[0];
                        }
                        default -> {
                            return defaultValue(method);
                        }
                    }
                };
        return (ResultSet)
                Proxy.newProxyInstance(
                        JdbcSqlServerControlPlaneTest.class.getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        handler);
    }

    private static Object defaultValue(final Method method) {
        if (method.getReturnType() == boolean.class) {
            return false;
        }
        if (method.getReturnType() == int.class) {
            return 0;
        }
        if (method.getReturnType() == long.class) {
            return 0L;
        }
        return null;
    }
}
