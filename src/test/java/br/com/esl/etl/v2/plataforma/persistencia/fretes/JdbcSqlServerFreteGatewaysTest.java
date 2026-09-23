package br.com.esl.etl.v2.plataforma.persistencia.fretes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageBatch;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerFreteGatewaysTest {
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-000000006389");
    private static final Instant OBSERVED_AT = Instant.parse("2036-03-20T12:00:00Z");

    @Test
    void stagesOnlyThroughTheClosedProcedureWithBatchAndTransaction() throws Exception {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final var mapper = new FreteDataExportRecordMapper();
        final var record =
                mapper.map(
                        1,
                        new ObjectMapper()
                                .readTree(
                                        "{\"id\":6389,\"cte_created_at\":\"2036-03-20T12:00:00Z\"}"));
        final var batch = new FreteStageBatch(EXECUTION, 1, java.util.List.of(record), OBSERVED_AT);

        new JdbcSqlServerFreteStagingGateway(jdbc.dataSource()).stage(batch);

        assertEquals(
                "{call stg.usp_stage_frete_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                jdbc.sql);
        assertEquals("INTEGER:6389", jdbc.parameters.get(4));
        assertEquals("VALID", jdbc.parameters.get(23));
        assertEquals(Timestamp.from(OBSERVED_AT), jdbc.parameters.get(25));
        assertEquals(1, jdbc.batchCount);
        assertTrue(jdbc.committed);
        assertFalse(jdbc.autoCommit);
    }

    @Test
    void rollsBackAndSanitizesJdbcFailure() throws Exception {
        final RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.executeFailure = new SQLException("synthetic-sensitive-driver-message");
        final var record =
                new FreteDataExportRecordMapper()
                        .map(
                                1,
                                new ObjectMapper()
                                        .readTree(
                                                "{\"id\":1,\"cte_created_at\":\"2036-03-20T12:00:00Z\"}"));
        final var batch = new FreteStageBatch(EXECUTION, 1, java.util.List.of(record), OBSERVED_AT);

        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () -> new JdbcSqlServerFreteStagingGateway(jdbc.dataSource()).stage(batch));
        assertEquals(1, jdbc.rollbackCount);
        assertFalse(failure.getMessage().contains("synthetic-sensitive-driver-message"));
    }

    @Test
    void preservesRollbackFailureAndRollsBackRuntimeCancellationAfterOpeningJdbc()
            throws Exception {
        final SQLException rollbackFailure = new SQLException("synthetic-sensitive-rollback");
        final RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.rollbackFailure = rollbackFailure;
        final var record =
                new FreteDataExportRecordMapper()
                        .map(
                                1,
                                new ObjectMapper()
                                        .readTree(
                                                "{\"id\":1,\"cte_created_at\":\"2036-03-20T12:00:00Z\"}"));
        final var batch = new FreteStageBatch(EXECUTION, 1, List.of(record), OBSERVED_AT);
        final CountingCancellation cancellation = new CountingCancellation(2);

        final ResilienceCancelledException failure =
                assertThrows(
                        ResilienceCancelledException.class,
                        () ->
                                new JdbcSqlServerFreteStagingGateway(jdbc.dataSource())
                                        .stage(batch, cancellation));

        assertEquals(1, jdbc.connectionCount);
        assertEquals(1, jdbc.rollbackCount);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(rollbackFailure, failure.getSuppressed()[0]);
        assertFalse(jdbc.committed);
    }

    @Test
    void stagesQuarantineWithTypedNullsInsteadOfInventedValues() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final var quarantine =
                br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord.quarantine(
                        1, "MISSING_SOURCE_KEY");
        final var batch = new FreteStageBatch(EXECUTION, 2, List.of(quarantine), OBSERVED_AT);

        new JdbcSqlServerFreteStagingGateway(jdbc.dataSource()).stage(batch);

        assertEquals("QUARANTINE", jdbc.parameters.get(23));
        assertEquals("MISSING_SOURCE_KEY", jdbc.parameters.get(24));
        assertEquals(java.sql.Types.NVARCHAR, jdbc.parameters.get(4));
        assertEquals(java.sql.Types.TIMESTAMP, jdbc.parameters.get(13));
        assertEquals(java.sql.Types.TIMESTAMP, jdbc.parameters.get(15));
        assertEquals(java.sql.Types.TIMESTAMP, jdbc.parameters.get(17));
    }

    @Test
    void typedPrepareUsesItsEntrypointAndPromotionRejectsIncoherentOrNonShadowPermits() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerFretePromotionGateway gateway =
                new JdbcSqlServerFretePromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit shadow = ContractTestSupport.promotionPermit(EXECUTION);

        gateway.prepareCandidateSet(shadow);
        assertEquals("{call core.usp_prepare_frete_candidate_set(?, ?, ?, ?, ?)}", jdbc.sql);
        assertEquals(1, jdbc.updateCount);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                shadow, DataQualityTestSupport.promotionPermit(UUID.randomUUID())));

        final ContractPromotionPermit cutover =
                ContractTestSupport.promotionPermit(
                        EXECUTION,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);
        assertThrows(IllegalArgumentException.class, () -> gateway.prepareCandidateSet(cutover));
    }

    @Test
    void appliesShadowCandidateSetAndReadsOneClosedAggregate() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerFretePromotionGateway gateway =
                new JdbcSqlServerFretePromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit shadow = ContractTestSupport.promotionPermit(EXECUTION);

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        shadow, DataQualityTestSupport.promotionPermit(EXECUTION));

        assertEquals("{call core.usp_apply_reconcile_publish_fretes(?, ?, ?, ?, ?)}", jdbc.sql);
        assertEquals(EXECUTION, result.executionId());
        assertEquals(3, result.candidateRows());
        assertEquals(1, result.insertedRows());
        assertEquals(1, result.updatedRows());
        assertEquals(0, result.reactivatedRows());
        assertEquals(1, result.noopRows());
        assertEquals(0, result.staleNoopRows());
        assertEquals(OBSERVED_AT, result.reconciledAt());
        assertEquals(OBSERVED_AT.plusSeconds(1), result.publishedAt());
        assertEquals(
                OBSERVED_AT.minusSeconds(60), result.incrementalFrontierBefore().orElseThrow());
        assertEquals(OBSERVED_AT, result.incrementalFrontierAfter().orElseThrow());
        assertEquals(EXECUTION.toString(), jdbc.parameters.get(1));
        assertEquals(1, jdbc.queryCount);
        assertTrue(jdbc.resultSetClosed);
    }

    @Test
    void acceptsAnAggregateWithoutIncrementalFrontiers() {
        final RecordingJdbc jdbc =
                new RecordingJdbc()
                        .withRows(List.of(rowWith("incremental_frontier_before_utc", null)));
        jdbc.rows.get(0).put("incremental_frontier_after_utc", null);

        final StagingPublicationResult result =
                new JdbcSqlServerFretePromotionGateway(jdbc.dataSource())
                        .applyReconcileAndPublish(
                                ContractTestSupport.promotionPermit(EXECUTION),
                                DataQualityTestSupport.promotionPermit(EXECUTION));

        assertTrue(result.incrementalFrontierBefore().isEmpty());
        assertTrue(result.incrementalFrontierAfter().isEmpty());
    }

    @Test
    void rejectsMissingExtraOrIncoherentPromotionAggregates() {
        assertMalformedAggregate(List.of());
        assertMalformedAggregate(List.of(validRow(), validRow()));
        assertMalformedAggregate(List.of(rowWith("execution_id", UUID.randomUUID().toString())));
        assertMalformedAggregate(List.of(rowWith("execution_id", null)));
        assertMalformedAggregate(List.of(rowWith("execution_id", "not-a-uuid")));
        assertMalformedAggregate(List.of(rowWith("candidate_rows", null)));
        assertMalformedAggregate(List.of(rowWith("candidate_rows", -1L)));
        assertMalformedAggregate(List.of(rowWith("candidate_rows", 99L)));
        assertMalformedAggregate(List.of(rowWith("reconciled_at_utc", null)));
        assertMalformedAggregate(List.of(rowWith("incremental_frontier_after_utc", null)));
    }

    @Test
    void wrapsPromotionSqlFailureWithoutLeakingDriverDetails() {
        final SQLException sqlFailure = new SQLException("synthetic-sensitive-promotion");
        final JdbcSqlServerFretePromotionGateway gateway =
                new JdbcSqlServerFretePromotionGateway(unavailable(sqlFailure));

        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                gateway.prepareCandidateSet(
                                        ContractTestSupport.promotionPermit(EXECUTION)));

        assertEquals(sqlFailure, failure.getCause());
        assertFalse(failure.getMessage().contains("synthetic-sensitive-promotion"));
    }

    @Test
    void promotionNullsAndCancellationFailBeforeOrDuringJdbcWithoutExecuting() {
        assertThrows(NullPointerException.class, () -> new JdbcSqlServerFreteStagingGateway(null));
        assertThrows(
                NullPointerException.class, () -> new JdbcSqlServerFretePromotionGateway(null));

        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerFretePromotionGateway gateway =
                new JdbcSqlServerFretePromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION);
        assertThrows(NullPointerException.class, () -> gateway.prepareCandidateSet(null));
        assertThrows(NullPointerException.class, () -> gateway.prepareCandidateSet(permit, null));
        assertThrows(
                NullPointerException.class, () -> gateway.applyReconcileAndPublish(permit, null));
        assertEquals(0, jdbc.connectionCount);

        assertThrows(
                ResilienceCancelledException.class,
                () -> gateway.prepareCandidateSet(permit, () -> true));
        assertEquals(0, jdbc.connectionCount);

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                permit,
                                DataQualityTestSupport.promotionPermit(EXECUTION),
                                new CountingCancellation(2)));
        assertEquals(1, jdbc.connectionCount);
        assertEquals(0, jdbc.queryCount);
    }

    @Test
    void nullOrCancelledWorkNeverOpensJdbc() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final JdbcSqlServerFreteStagingGateway staging =
                new JdbcSqlServerFreteStagingGateway(jdbc.dataSource());
        assertThrows(NullPointerException.class, () -> staging.stage(null));
        assertEquals(0, jdbc.connectionCount);
        assertThrows(
                RuntimeException.class,
                () ->
                        staging.stage(
                                new FreteStageBatch(
                                        EXECUTION,
                                        1,
                                        java.util.List.of(
                                                br.com.esl.etl.v2.modulos.fretes.domain
                                                        .FreteStageRecord.quarantine(
                                                        1, "SYNTHETIC_QUARANTINE")),
                                        OBSERVED_AT),
                                () -> true));
        assertEquals(0, jdbc.connectionCount);
    }

    private static void assertMalformedAggregate(final List<Map<String, Object>> rows) {
        final RecordingJdbc jdbc = new RecordingJdbc().withRows(rows);
        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerFretePromotionGateway(jdbc.dataSource())
                                        .applyReconcileAndPublish(
                                                ContractTestSupport.promotionPermit(EXECUTION),
                                                DataQualityTestSupport.promotionPermit(EXECUTION)));
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    private static Map<String, Object> rowWith(final String column, final Object value) {
        final Map<String, Object> row = new LinkedHashMap<>(validRow());
        row.put(column, value);
        return row;
    }

    private static Map<String, Object> validRow() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("execution_id", EXECUTION.toString());
        row.put("candidate_rows", 3L);
        row.put("inserted_rows", 1L);
        row.put("updated_rows", 1L);
        row.put("reactivated_rows", 0L);
        row.put("noop_rows", 1L);
        row.put("stale_noop_rows", 0L);
        row.put("reconciled_at_utc", Timestamp.from(OBSERVED_AT));
        row.put("published_at_utc", Timestamp.from(OBSERVED_AT.plusSeconds(1)));
        row.put("incremental_frontier_before_utc", Timestamp.from(OBSERVED_AT.minusSeconds(60)));
        row.put("incremental_frontier_after_utc", Timestamp.from(OBSERVED_AT));
        return row;
    }

    private static DataSource unavailable(final SQLException failure) {
        return (DataSource)
                Proxy.newProxyInstance(
                        JdbcSqlServerFreteGatewaysTest.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("getConnection")) {
                                throw failure;
                            }
                            return defaultValue(method);
                        });
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

    private static final class CountingCancellation implements CancellationToken {
        private final int cancelOnCall;
        private int calls;

        private CountingCancellation(final int cancelOnCall) {
            this.cancelOnCall = cancelOnCall;
        }

        @Override
        public boolean isCancellationRequested() {
            calls++;
            return calls >= cancelOnCall;
        }
    }

    private static final class RecordingJdbc {
        private final Map<Integer, Object> parameters = new LinkedHashMap<>();
        private String sql;
        private boolean autoCommit = true;
        private boolean committed;
        private int batchCount;
        private int rollbackCount;
        private int updateCount;
        private int queryCount;
        private int connectionCount;
        private SQLException executeFailure;
        private SQLException rollbackFailure;
        private boolean resultSetClosed;
        private List<Map<String, Object>> rows = List.of(validRow());

        private RecordingJdbc withRows(final List<Map<String, Object>> values) {
            rows = new ArrayList<>(values);
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
                                return switch (method.getName()) {
                                    case "prepareCall" -> {
                                        sql = (String) arguments[0];
                                        yield statement();
                                    }
                                    case "setAutoCommit" -> {
                                        autoCommit = (Boolean) arguments[0];
                                        yield null;
                                    }
                                    case "commit" -> {
                                        committed = true;
                                        yield null;
                                    }
                                    case "rollback" -> {
                                        rollbackCount++;
                                        if (rollbackFailure != null) {
                                            throw rollbackFailure;
                                        }
                                        yield null;
                                    }
                                    default -> defaultValue(method);
                                };
                            });
        }

        private CallableStatement statement() {
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, arguments) -> {
                                switch (method.getName()) {
                                    case "setString",
                                                    "setInt",
                                                    "setBoolean",
                                                    "setTimestamp",
                                                    "setNull" ->
                                            parameters.put((Integer) arguments[0], arguments[1]);
                                    case "addBatch" -> batchCount++;
                                    case "executeBatch" -> {
                                        if (executeFailure != null) {
                                            throw executeFailure;
                                        }
                                        return new int[] {1};
                                    }
                                    case "executeUpdate" -> {
                                        updateCount++;
                                        return 1;
                                    }
                                    case "executeQuery" -> {
                                        queryCount++;
                                        return resultSet();
                                    }
                                    default -> {
                                        return defaultValue(method);
                                    }
                                }
                                return null;
                            });
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

        private Object value(final int rowIndex, final String column) throws SQLException {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                throw new SQLException("Synthetic result cursor is invalid.");
            }
            return rows.get(rowIndex).get(column);
        }
    }
}
