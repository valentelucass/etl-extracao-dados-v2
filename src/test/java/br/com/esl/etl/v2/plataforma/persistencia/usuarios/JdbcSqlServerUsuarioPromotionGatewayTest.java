package br.com.esl.etl.v2.plataforma.persistencia.usuarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

class JdbcSqlServerUsuarioPromotionGatewayTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000334");
    private static final Instant RECONCILED_AT = Instant.parse("2026-09-01T17:30:01Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-01T17:30:02Z");

    @Test
    void preparesOnlyTheGenericCandidateSetWithTheExactContractPermit() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerUsuarioPromotionGateway gateway =
                new JdbcSqlServerUsuarioPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        gateway.prepareCandidateSet(permit);

        assertEquals("{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}", jdbc.statementSql);
        assertEquals(expectedParameters(permit), jdbc.parameters);
        assertEquals(1, jdbc.executeUpdateCount);
        assertEquals(0, jdbc.executeQueryCount);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
    }

    @Test
    void appliesTheTypedCurrentHistoryProcedureAndReturnsOnlyAnAggregate() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerUsuarioPromotionGateway gateway =
                new JdbcSqlServerUsuarioPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        permit, DataQualityTestSupport.promotionPermit(EXECUTION_ID));

        assertEquals(
                "{call core.usp_apply_reconcile_publish_usuarios(?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(expectedParameters(permit), jdbc.parameters);
        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals(10, result.candidateRows());
        assertEquals(2, result.insertedRows());
        assertEquals(3, result.updatedRows());
        assertEquals(1, result.reactivatedRows());
        assertEquals(4, result.noopRows());
        assertEquals(2, result.staleNoopRows());
        assertEquals(RECONCILED_AT, result.reconciledAt());
        assertEquals(PUBLISHED_AT, result.publishedAt());
        assertEquals(
                Optional.of(Instant.parse("2026-09-01T17:00:00Z")),
                result.incrementalFrontierBefore());
        assertEquals(
                Optional.of(Instant.parse("2026-09-01T18:00:00Z")),
                result.incrementalFrontierAfter());
        assertEquals(1, jdbc.executeQueryCount);
        assertTrue(jdbc.resultSetClosed);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
    }

    @Test
    void acceptsAnAggregateWithoutIncrementalFrontiers() {
        final Map<String, Object> row = publicationRow();
        row.put("incremental_frontier_before_utc", null);
        row.put("incremental_frontier_after_utc", null);
        final JdbcSqlServerUsuarioPromotionGateway gateway =
                new JdbcSqlServerUsuarioPromotionGateway(
                        new RecordingJdbc().publicationRows(List.of(row)).dataSource());

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        ContractTestSupport.promotionPermit(EXECUTION_ID),
                        DataQualityTestSupport.promotionPermit(EXECUTION_ID));

        assertTrue(result.incrementalFrontierBefore().isEmpty());
        assertTrue(result.incrementalFrontierAfter().isEmpty());
    }

    @Test
    void rejectsEveryNonShadowOrMismatchedPermitBeforeOpeningJdbc() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerUsuarioPromotionGateway gateway =
                new JdbcSqlServerUsuarioPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit cutover =
                ContractTestSupport.promotionPermit(
                        EXECUTION_ID,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);
        final ContractPromotionPermit shadow = ContractTestSupport.promotionPermit(EXECUTION_ID);

        assertThrows(NullPointerException.class, () -> gateway.prepareCandidateSet(null));
        assertThrows(IllegalArgumentException.class, () -> gateway.prepareCandidateSet(cutover));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                cutover, DataQualityTestSupport.promotionPermit(EXECUTION_ID)));
        assertThrows(
                NullPointerException.class, () -> gateway.applyReconcileAndPublish(shadow, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                shadow, DataQualityTestSupport.promotionPermit(UUID.randomUUID())));
        assertEquals(0, jdbc.connectionCount);
        assertThrows(
                NullPointerException.class, () -> new JdbcSqlServerUsuarioPromotionGateway(null));
    }

    @Test
    void rejectsAbsentDuplicatedMalformedOrInconsistentAggregateRows() {
        final List<List<Map<String, Object>>> invalidResults = new ArrayList<>();
        invalidResults.add(List.of());
        invalidResults.add(List.of(publicationRow(), publicationRow()));
        invalidResults.add(rowsWith("execution_id", null));
        invalidResults.add(rowsWith("execution_id", "not-a-uuid"));
        invalidResults.add(rowsWith("execution_id", UUID.randomUUID().toString()));
        invalidResults.add(rowsWith("candidate_rows", null));
        invalidResults.add(rowsWith("inserted_rows", -1L));
        invalidResults.add(rowsWith("candidate_rows", 11L));
        invalidResults.add(rowsWith("stale_noop_rows", 5L));
        invalidResults.add(rowsWith("reconciled_at_utc", null));
        invalidResults.add(
                rowsWith("published_at_utc", Timestamp.from(RECONCILED_AT.minusSeconds(1))));
        invalidResults.add(rowsWith("incremental_frontier_after_utc", null));

        for (final List<Map<String, Object>> rows : invalidResults) {
            final JdbcSqlServerUsuarioPromotionGateway gateway =
                    new JdbcSqlServerUsuarioPromotionGateway(
                            new RecordingJdbc().publicationRows(rows).dataSource());

            final StagingPersistenceException exception =
                    assertThrows(
                            StagingPersistenceException.class,
                            () ->
                                    gateway.applyReconcileAndPublish(
                                            ContractTestSupport.promotionPermit(EXECUTION_ID),
                                            DataQualityTestSupport.promotionPermit(EXECUTION_ID)));

            assertInstanceOf(SQLException.class, exception.getCause());
            assertFalse(exception.getMessage().contains(EXECUTION_ID.toString()));
        }
    }

    @Test
    void preservesTheSqlCauseButKeepsBothOperationsSanitized() {
        final SQLException cause = new SQLException("synthetic-sensitive-driver-detail");
        final DataSource unavailable =
                (DataSource)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("getConnection")) {
                                        throw cause;
                                    }
                                    return defaultValue(method);
                                });
        final JdbcSqlServerUsuarioPromotionGateway gateway =
                new JdbcSqlServerUsuarioPromotionGateway(unavailable);

        final StagingPersistenceException prepareFailure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                gateway.prepareCandidateSet(
                                        ContractTestSupport.promotionPermit(EXECUTION_ID)));
        final StagingPersistenceException applyFailure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                gateway.applyReconcileAndPublish(
                                        ContractTestSupport.promotionPermit(EXECUTION_ID),
                                        DataQualityTestSupport.promotionPermit(EXECUTION_ID)));

        assertEquals(cause, prepareFailure.getCause());
        assertEquals(cause, applyFailure.getCause());
        assertFalse(prepareFailure.getMessage().contains("synthetic-sensitive-driver-detail"));
        assertFalse(applyFailure.getMessage().contains("synthetic-sensitive-driver-detail"));
    }

    private static List<Object> expectedParameters(final ContractPromotionPermit permit) {
        return List.of(
                EXECUTION_ID.toString(),
                permit.contractFingerprint().version(),
                permit.contractFingerprint().sha256(),
                permit.configurationFingerprint().version(),
                permit.configurationFingerprint().sha256());
    }

    private static List<Map<String, Object>> rowsWith(final String column, final Object value) {
        final Map<String, Object> row = publicationRow();
        row.put(column, value);
        return List.of(row);
    }

    private static Map<String, Object> publicationRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("execution_id", EXECUTION_ID.toString());
        row.put("candidate_rows", 10L);
        row.put("inserted_rows", 2L);
        row.put("updated_rows", 3L);
        row.put("reactivated_rows", 1L);
        row.put("noop_rows", 4L);
        row.put("stale_noop_rows", 2L);
        row.put("reconciled_at_utc", Timestamp.from(RECONCILED_AT));
        row.put("published_at_utc", Timestamp.from(PUBLISHED_AT));
        row.put(
                "incremental_frontier_before_utc",
                Timestamp.from(Instant.parse("2026-09-01T17:00:00Z")));
        row.put(
                "incremental_frontier_after_utc",
                Timestamp.from(Instant.parse("2026-09-01T18:00:00Z")));
        return row;
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

        private final List<Object> parameters = new ArrayList<>();
        private String statementSql;
        private int connectionCount;
        private int executeUpdateCount;
        private int executeQueryCount;
        private boolean statementClosed;
        private boolean resultSetClosed;
        private boolean connectionClosed;
        private List<Map<String, Object>> rows = List.of(publicationRow());

        private RecordingJdbc publicationRows(final List<Map<String, Object>> sourceRows) {
            final List<Map<String, Object>> copy = new ArrayList<>();
            for (final Map<String, Object> row : sourceRows) {
                copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            }
            rows = Collections.unmodifiableList(copy);
            return this;
        }

        private DataSource dataSource() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("getConnection")) {
                                    connectionCount++;
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
                                if (method.getName().equals("prepareCall")) {
                                    statementSql = (String) arguments[0];
                                    parameters.clear();
                                    return statement();
                                }
                                if (method.getName().equals("close")) {
                                    connectionClosed = true;
                                    return null;
                                }
                                return defaultValue(method);
                            });
        }

        private CallableStatement statement() {
            final Map<Integer, Object> indexed = new LinkedHashMap<>();
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "setString" -> {
                                        indexed.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "executeUpdate" -> {
                                        copyParameters(indexed);
                                        executeUpdateCount++;
                                        return 0;
                                    }
                                    case "executeQuery" -> {
                                        copyParameters(indexed);
                                        executeQueryCount++;
                                        return resultSet();
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

        private void copyParameters(final Map<Integer, Object> indexed) {
            for (int parameter = 1; parameter <= indexed.size(); parameter++) {
                parameters.add(indexed.get(parameter));
            }
        }

        private ResultSet resultSet() {
            final int[] cursor = {-1};
            final boolean[] wasNull = {false};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "next" -> {
                                        cursor[0]++;
                                        return cursor[0] < rows.size();
                                    }
                                    case "getString" -> {
                                        final Object value =
                                                value(cursor[0], (String) arguments[0]);
                                        wasNull[0] = value == null;
                                        return value == null ? null : value.toString();
                                    }
                                    case "getLong" -> {
                                        final Object value =
                                                value(cursor[0], (String) arguments[0]);
                                        wasNull[0] = value == null;
                                        return value == null ? 0L : ((Number) value).longValue();
                                    }
                                    case "getTimestamp" -> {
                                        assertEquals(
                                                "UTC",
                                                ((java.util.Calendar) arguments[1])
                                                        .getTimeZone()
                                                        .getID());
                                        final Object value =
                                                value(cursor[0], (String) arguments[0]);
                                        wasNull[0] = value == null;
                                        return value;
                                    }
                                    case "wasNull" -> {
                                        return wasNull[0];
                                    }
                                    case "close" -> {
                                        resultSetClosed = true;
                                        return null;
                                    }
                                    default -> {
                                        return defaultValue(method);
                                    }
                                }
                            });
        }

        private Object value(final int row, final String column) throws SQLException {
            if (row < 0 || row >= rows.size() || !rows.get(row).containsKey(column)) {
                throw new SQLException("Synthetic result column is absent.");
            }
            return rows.get(row).get(column);
        }
    }
}
