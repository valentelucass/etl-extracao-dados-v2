package br.com.esl.etl.v2.plataforma.persistencia.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.observabilidade.AlertSeverity;
import br.com.esl.etl.v2.plataforma.observabilidade.CorrelationReference;
import br.com.esl.etl.v2.plataforma.observabilidade.ExecutionMetricsSnapshot;
import br.com.esl.etl.v2.plataforma.observabilidade.ObservabilityPersistenceException;
import br.com.esl.etl.v2.plataforma.observabilidade.ObservabilityPersistenceFailureKind;
import br.com.esl.etl.v2.plataforma.observabilidade.OperationalAlert;
import br.com.esl.etl.v2.plataforma.observabilidade.PlatformHealthStatus;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationException;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationRequest;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityFailureKind;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityRunSummary;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityState;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerObservabilityGatewayTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000626");
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final DataQualityPolicyReference POLICY =
            new DataQualityPolicyReference("synthetic-dq-v1", "a".repeat(64));

    @Test
    void bindsScopedAlertToExecutionAndRejectsDifferentCorrelationBeforeSql() {
        final var jdbc = new RecordingJdbc();
        final var gateway = gateway(jdbc);
        final var alert =
                new OperationalAlert(
                        CorrelationReference.fromExecutionId(EXECUTION_ID),
                        1,
                        AlertSeverity.WARNING,
                        "CONTRACT_COMPATIBLE_DRIFT",
                        "contract-owner",
                        1,
                        NOW);
        gateway.raiseScoped(EXECUTION_ID, alert);
        assertEquals(
                "{call recon.usp_runtime_raise_alert(?, ?, ?, ?, ?, ?, ?)}", jdbc.statementSql);
        assertEquals(EXECUTION_ID.toString(), jdbc.params().get(0));
        assertTrue(jdbc.connectionClosed);
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.raiseScoped(UUID.randomUUID(), alert));
    }

    @Test
    void mapsExactlyOneFixedDataQualitySummaryAndBindsOnlyScalarParameters() {
        final RecordingJdbc jdbc = new RecordingJdbc().rows(List.of(dataQualityRow()));
        final JdbcSqlServerObservabilityGateway gateway = gateway(jdbc);

        final DataQualityRunSummary summary =
                gateway.evaluatePlatformIntegrity(
                        new DataQualityEvaluationRequest(EXECUTION_ID, POLICY));

        assertEquals(
                "{call recon.usp_evaluate_execution_data_quality(?, ?, ?)}", jdbc.statementSql);
        assertEquals(
                List.of(EXECUTION_ID.toString(), POLICY.version(), POLICY.sha256()), jdbc.params());
        assertEquals(EXECUTION_ID, summary.executionId());
        assertEquals(DataQualityState.PASSED, summary.state());
        assertEquals(4, summary.completedChecks());
        assertEquals(30, jdbc.queryTimeoutSeconds);
        assertEquals(2, jdbc.fetchSize);
        assertFalse(jdbc.maximumRowsCalled);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
        assertTrue(jdbc.resultSetClosed);
    }

    @Test
    void refusesAbsentDuplicatedAndMalformedDataQualityResults() {
        final Map<String, Object> malformed = dataQualityRow();
        malformed.put("failed_rows", -1L);
        final Map<String, Object> nullFingerprint = dataQualityRow();
        nullFingerprint.put("evaluation_fingerprint", null);
        final Map<String, Object> uppercaseFingerprint = dataQualityRow();
        uppercaseFingerprint.put("evaluation_fingerprint", "B".repeat(64));
        final Map<String, Object> nullCount = dataQualityRow();
        nullCount.put("expected_checks", null);

        for (final List<Map<String, Object>> rows :
                List.<List<Map<String, Object>>>of(
                        List.<Map<String, Object>>of(),
                        List.of(dataQualityRow(), dataQualityRow()),
                        List.of(malformed),
                        List.of(nullFingerprint),
                        List.of(uppercaseFingerprint),
                        List.of(nullCount))) {
            final JdbcSqlServerObservabilityGateway gateway =
                    gateway(new RecordingJdbc().rows(rows));
            assertThrows(
                    DataQualityEvaluationException.class,
                    () ->
                            gateway.evaluatePlatformIntegrity(
                                    new DataQualityEvaluationRequest(EXECUTION_ID, POLICY)));
        }
    }

    @Test
    void sqlFailureRemainsCriticalAndItsMessageIsNotInterpolated() {
        final SQLException cause = new SQLException("synthetic-sensitive-detail");
        final JdbcSqlServerObservabilityGateway gateway =
                new JdbcSqlServerObservabilityGateway(
                        new RecordingJdbc().failConnection(cause).dataSource(),
                        CLOCK,
                        Duration.ofSeconds(30));

        final DataQualityEvaluationException failure =
                assertThrows(
                        DataQualityEvaluationException.class,
                        () ->
                                gateway.evaluatePlatformIntegrity(
                                        new DataQualityEvaluationRequest(EXECUTION_ID, POLICY)));

        assertSame(cause, failure.getCause());
        assertEquals(DataQualityFailureKind.UNAVAILABLE, failure.kind());
        assertEquals("SQL_UNAVAILABLE", failure.reasonCode());
        assertFalse(failure.getMessage().contains("synthetic-sensitive-detail"));
    }

    @Test
    void classifiesSqlContractLockTimeoutAndAvailabilityWithoutLeakingDriverText() {
        final List<SQLException> failures =
                List.of(
                        new SQLException("secret-contract", "S0001", 51605),
                        new SQLException("secret-deadlock", "40001", 1205),
                        new SQLTimeoutException("secret-timeout", "HYT00", 0),
                        new SQLException("secret-serialization", "40001", 0),
                        new SQLException("secret-cancelled", "HY008", 0),
                        new SQLException("secret-integrity", "23000", 0),
                        new SQLException("secret-network", "08001", 0));
        final List<DataQualityFailureKind> expectedKinds =
                List.of(
                        DataQualityFailureKind.DETERMINISTIC,
                        DataQualityFailureKind.TRANSIENT,
                        DataQualityFailureKind.TRANSIENT,
                        DataQualityFailureKind.TRANSIENT,
                        DataQualityFailureKind.CANCELLED,
                        DataQualityFailureKind.DETERMINISTIC,
                        DataQualityFailureKind.UNAVAILABLE);
        final List<String> expectedReasons =
                List.of(
                        "QUALITY_CONTRACT_REJECTED",
                        "SQL_TRANSIENT",
                        "SQL_TIMEOUT",
                        "SQL_TRANSIENT",
                        "SQL_CANCELLED",
                        "QUALITY_CONTRACT_REJECTED",
                        "SQL_UNAVAILABLE");

        for (int index = 0; index < failures.size(); index++) {
            final SQLException cause = failures.get(index);
            final DataQualityEvaluationException failure =
                    assertThrows(
                            DataQualityEvaluationException.class,
                            () ->
                                    new JdbcSqlServerObservabilityGateway(
                                                    new RecordingJdbc()
                                                            .failConnection(cause)
                                                            .dataSource(),
                                                    CLOCK,
                                                    Duration.ofSeconds(30))
                                            .evaluatePlatformIntegrity(
                                                    new DataQualityEvaluationRequest(
                                                            EXECUTION_ID, POLICY)));
            assertEquals(expectedKinds.get(index), failure.kind());
            assertEquals(expectedReasons.get(index), failure.reasonCode());
            assertFalse(failure.getMessage().contains("secret"));
        }
    }

    @Test
    void writesFixedMetricAndAlertProceduresWithoutFreeText() {
        final RecordingJdbc metricJdbc = new RecordingJdbc();
        final JdbcSqlServerObservabilityGateway metricGateway = gateway(metricJdbc);
        metricGateway.record(
                new ExecutionMetricsSnapshot(
                        EXECUTION_ID,
                        4095,
                        101,
                        103,
                        107,
                        37,
                        30,
                        5,
                        23,
                        7,
                        2,
                        11,
                        23,
                        3,
                        5,
                        7,
                        8,
                        6,
                        13,
                        11,
                        17,
                        Optional.of(NOW.minusSeconds(1)),
                        NOW));

        assertEquals(
                "{call recon.usp_record_execution_metric(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                metricJdbc.statementSql);
        assertEquals(
                List.of(
                        EXECUTION_ID.toString(),
                        4095,
                        101L,
                        103L,
                        107L,
                        37L,
                        30L,
                        5L,
                        23L,
                        7L,
                        2L,
                        11L,
                        23L,
                        3L,
                        5L,
                        7L,
                        8L,
                        6L,
                        13L,
                        11L,
                        17L,
                        Timestamp.from(NOW.minusSeconds(1)),
                        Timestamp.from(NOW)),
                metricJdbc.params());
        assertEquals(30, metricJdbc.queryTimeoutSeconds);
        assertEquals(1, metricJdbc.executeUpdateCount);

        final RecordingJdbc alertJdbc = new RecordingJdbc();
        final JdbcSqlServerObservabilityGateway alertGateway = gateway(alertJdbc);
        final CorrelationReference correlation =
                CorrelationReference.fromTechnicalScope("SYNTHETIC_ALERT_SCOPE");
        alertGateway.raise(
                new OperationalAlert(
                        correlation,
                        4094,
                        AlertSeverity.CRITICAL,
                        "DQ_FAILED",
                        "quality-owner",
                        17,
                        NOW));

        assertEquals(
                "{call recon.usp_raise_observability_alert(?, ?, ?, ?, ?, ?, ?)}",
                alertJdbc.statementSql);
        assertEquals(
                List.of(
                        correlation.sha256(),
                        4094,
                        "CRITICAL",
                        "DQ_FAILED",
                        "quality-owner",
                        17L,
                        Timestamp.from(NOW)),
                alertJdbc.params());
        assertEquals(30, alertJdbc.queryTimeoutSeconds);
        assertEquals(1, alertJdbc.executeUpdateCount);

        final RecordingJdbc nullWatermarkJdbc = new RecordingJdbc();
        gateway(nullWatermarkJdbc)
                .record(
                        new ExecutionMetricsSnapshot(
                                EXECUTION_ID,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                Optional.empty(),
                                NOW));
        assertEquals(Integer.valueOf(Types.TIMESTAMP), nullWatermarkJdbc.nullTypes.get(22));
        assertEquals(23, nullWatermarkJdbc.params().size());
        assertNull(nullWatermarkJdbc.params().get(21));
    }

    @Test
    void metricAndAlertSqlFailuresUseSanitizedPersistenceExceptions() {
        final SQLException cause = new SQLException("synthetic-sensitive-detail");
        final JdbcSqlServerObservabilityGateway gateway =
                new JdbcSqlServerObservabilityGateway(
                        new RecordingJdbc().failConnection(cause).dataSource(),
                        CLOCK,
                        Duration.ofSeconds(30));
        final ExecutionMetricsSnapshot metric =
                new ExecutionMetricsSnapshot(
                        EXECUTION_ID,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Optional.empty(),
                        NOW);
        final OperationalAlert alert =
                new OperationalAlert(
                        CorrelationReference.fromTechnicalScope("FAILURE_SCOPE"),
                        1,
                        AlertSeverity.WARNING,
                        "OBSERVABILITY_FAILED",
                        "operations-owner",
                        1,
                        NOW);

        for (final ObservabilityPersistenceException failure :
                List.of(
                        assertThrows(
                                ObservabilityPersistenceException.class,
                                () -> gateway.record(metric)),
                        assertThrows(
                                ObservabilityPersistenceException.class,
                                () -> gateway.raise(alert)))) {
            assertSame(cause, failure.getCause());
            assertEquals(ObservabilityPersistenceFailureKind.UNAVAILABLE, failure.kind());
            assertFalse(failure.getMessage().contains("synthetic-sensitive-detail"));
            assertInstanceOf(SQLException.class, failure.getCause());
        }
    }

    @Test
    void healthIsDownForSqlAbsentDuplicatedOrMalformedSummary() {
        final RecordingJdbc healthyJdbc = new RecordingJdbc().rows(List.of(healthRow()));
        final var healthy = gateway(healthyJdbc).readiness(Duration.ofMinutes(5));
        assertEquals(PlatformHealthStatus.UP, healthy.status());
        assertEquals("{call ctl.usp_observe_platform_health(?)}", healthyJdbc.statementSql);
        assertEquals(300L, healthyJdbc.params().get(0));
        assertEquals(30, healthyJdbc.queryTimeoutSeconds);

        final Map<String, Object> malformed = healthRow();
        malformed.put("health_status", "UNKNOWN");
        final Map<String, Object> nullReason = healthRow();
        nullReason.put("reason_code", null);
        final Map<String, Object> nullCount = healthRow();
        nullCount.put("failed_data_quality_runs", null);
        final Map<String, Object> contradictory = healthRow();
        contradictory.put("failed_data_quality_runs", 1L);
        for (final RecordingJdbc jdbc :
                List.of(
                        new RecordingJdbc().rows(List.of()),
                        new RecordingJdbc().rows(List.of(healthRow(), healthRow())),
                        new RecordingJdbc().rows(List.of(malformed)),
                        new RecordingJdbc().rows(List.of(nullReason)),
                        new RecordingJdbc().rows(List.of(nullCount)),
                        new RecordingJdbc().rows(List.of(contradictory)),
                        new RecordingJdbc().failConnection(new SQLException("secret")))) {
            final var snapshot = gateway(jdbc).readiness(Duration.ofSeconds(1));
            assertEquals(PlatformHealthStatus.DOWN, snapshot.status());
            assertFalse(jdbc.maximumRowsCalled);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway(new RecordingJdbc()).readiness(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway(new RecordingJdbc()).readiness(Duration.ofDays(31)));
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway(new RecordingJdbc()).readiness(Duration.ofMillis(1500)));
    }

    @Test
    void rejectsNonIntegralOrOutOfRangeSqlTimeoutsAndUsesFetchHintsOnly() {
        final DataSource dataSource = new RecordingJdbc().dataSource();
        assertThrows(
                NullPointerException.class,
                () -> new JdbcSqlServerObservabilityGateway(dataSource, CLOCK, null));
        for (final Duration invalid :
                List.of(
                        Duration.ZERO,
                        Duration.ofMillis(500),
                        Duration.ofMillis(1500),
                        Duration.ofSeconds(3601))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new JdbcSqlServerObservabilityGateway(dataSource, CLOCK, invalid));
        }

        final RecordingJdbc jdbc = new RecordingJdbc().rows(List.of(healthRow()));
        gateway(jdbc).readiness(Duration.ofSeconds(1));
        assertEquals(2, jdbc.fetchSize);
        assertFalse(jdbc.maximumRowsCalled);
    }

    @Test
    void classifiesTimeoutRaisedByStatementExecution() {
        final SQLTimeoutException cause =
                new SQLTimeoutException("synthetic-sensitive-detail", "HYT00", 0);
        final DataQualityEvaluationException failure =
                assertThrows(
                        DataQualityEvaluationException.class,
                        () ->
                                gateway(new RecordingJdbc().failStatement(cause))
                                        .evaluatePlatformIntegrity(
                                                new DataQualityEvaluationRequest(
                                                        EXECUTION_ID, POLICY)));

        assertSame(cause, failure.getCause());
        assertEquals(DataQualityFailureKind.TRANSIENT, failure.kind());
        assertEquals("SQL_TIMEOUT", failure.reasonCode());
        assertFalse(failure.getMessage().contains("synthetic-sensitive-detail"));
    }

    private static JdbcSqlServerObservabilityGateway gateway(final RecordingJdbc jdbc) {
        return new JdbcSqlServerObservabilityGateway(
                jdbc.dataSource(), CLOCK, Duration.ofSeconds(30));
    }

    private static Map<String, Object> dataQualityRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("execution_id", EXECUTION_ID.toString());
        row.put("policy_version", POLICY.version());
        row.put("policy_fingerprint", POLICY.sha256());
        row.put("evaluation_fingerprint", "b".repeat(64));
        row.put("expected_checks", 4);
        row.put("completed_checks", 4);
        row.put("passed_checks", 4);
        row.put("failed_checks", 0);
        row.put("evaluated_rows", 4L);
        row.put("failed_rows", 0L);
        row.put("evaluation_state", "PASSED");
        row.put("evaluated_at_utc", Timestamp.from(NOW));
        return row;
    }

    private static Map<String, Object> healthRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("health_status", "UP");
        row.put("reason_code", "PLATFORM_READY");
        row.put("incomplete_data_quality_runs", 0L);
        row.put("failed_data_quality_runs", 0L);
        row.put("overdue_quarantine_rows", 0L);
        row.put("stale_running_executions", 0L);
        row.put("observed_at_utc", Timestamp.from(NOW));
        return row;
    }

    private static final class RecordingJdbc {

        private String statementSql;
        private final Object[] parameters = new Object[32];
        private int maximumParameter;
        private List<Map<String, Object>> rows = List.of();
        private SQLException connectionFailure;
        private SQLException statementFailure;
        private boolean statementClosed;
        private boolean connectionClosed;
        private boolean resultSetClosed;
        private int executeUpdateCount;
        private int queryTimeoutSeconds;
        private int fetchSize;
        private boolean maximumRowsCalled;
        private final Map<Integer, Integer> nullTypes = new LinkedHashMap<>();

        RecordingJdbc rows(final List<Map<String, Object>> value) {
            rows = value;
            return this;
        }

        RecordingJdbc failConnection(final SQLException failure) {
            connectionFailure = failure;
            return this;
        }

        RecordingJdbc failStatement(final SQLException failure) {
            statementFailure = failure;
            return this;
        }

        List<Object> params() {
            final List<Object> values = new ArrayList<>();
            for (int index = 0; index < maximumParameter; index++) {
                values.add(parameters[index]);
            }
            return java.util.Collections.unmodifiableList(values);
        }

        DataSource dataSource() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("getConnection")) {
                                    if (connectionFailure != null) {
                                        throw connectionFailure;
                                    }
                                    return connection();
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("prepareCall")) {
                                    statementSql = (String) arguments[0];
                                    return statement();
                                }
                                if (method.getName().equals("close")) {
                                    connectionClosed = true;
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private CallableStatement statement() {
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, arguments) -> {
                                final String name = method.getName();
                                if (name.equals("setQueryTimeout")) {
                                    queryTimeoutSeconds = (Integer) arguments[0];
                                    return null;
                                }
                                if (name.equals("setFetchSize")) {
                                    fetchSize = (Integer) arguments[0];
                                    return null;
                                }
                                if (name.equals("setMaxRows")) {
                                    maximumRowsCalled = true;
                                    return null;
                                }
                                if (name.equals("setNull")) {
                                    final int parameter = (Integer) arguments[0];
                                    parameters[parameter - 1] = null;
                                    nullTypes.put(parameter, (Integer) arguments[1]);
                                    maximumParameter = Math.max(maximumParameter, parameter);
                                    return null;
                                }
                                if (name.startsWith("set")
                                        && arguments != null
                                        && arguments.length >= 2
                                        && arguments[0] instanceof Integer parameter) {
                                    parameters[parameter - 1] = arguments[1];
                                    maximumParameter = Math.max(maximumParameter, parameter);
                                    return null;
                                }
                                if (name.equals("executeQuery")) {
                                    if (statementFailure != null) {
                                        throw statementFailure;
                                    }
                                    return resultSet();
                                }
                                if (name.equals("executeUpdate")) {
                                    if (statementFailure != null) {
                                        throw statementFailure;
                                    }
                                    executeUpdateCount++;
                                    return 1;
                                }
                                if (name.equals("close")) {
                                    statementClosed = true;
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private ResultSet resultSet() {
            final int[] index = {-1};
            final boolean[] wasNull = {false};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, arguments) -> {
                                final String name = method.getName();
                                if (name.equals("next")) {
                                    index[0]++;
                                    return index[0] < rows.size();
                                }
                                if (name.equals("close")) {
                                    resultSetClosed = true;
                                    return null;
                                }
                                if (name.equals("wasNull")) {
                                    return wasNull[0];
                                }
                                if (name.equals("getString")
                                        || name.equals("getInt")
                                        || name.equals("getLong")
                                        || name.equals("getTimestamp")) {
                                    final Object value =
                                            rows.get(index[0]).get((String) arguments[0]);
                                    wasNull[0] = value == null;
                                    if (name.equals("getString")) {
                                        return value == null ? null : value.toString();
                                    }
                                    if (name.equals("getInt")) {
                                        return value == null ? 0 : ((Number) value).intValue();
                                    }
                                    if (name.equals("getLong")) {
                                        return value == null ? 0L : ((Number) value).longValue();
                                    }
                                    return value;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private static Object defaultValue(final Class<?> type) {
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
            if (type == double.class) {
                return 0D;
            }
            if (type == float.class) {
                return 0F;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == char.class) {
                return (char) 0;
            }
            throw new IllegalArgumentException("Tipo primitivo inesperado: " + type);
        }
    }
}
