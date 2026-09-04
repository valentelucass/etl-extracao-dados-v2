package br.com.esl.etl.v2.plataforma.persistencia.staging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import java.lang.reflect.InvocationHandler;
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

class JdbcSqlServerStagingPromotionKernelTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final Instant STAGED_AT = Instant.parse("2026-08-30T00:00:01Z");

    @Test
    void sendsOneBoundedJdbcBatchInOneTransactionAndReleasesResources() {
        final RecordingJdbc recording = new RecordingJdbc();
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());

        kernel.stage(
                new StagingBatch(
                        EXECUTION_ID,
                        7,
                        2,
                        List.of(validRecord(1), quarantineRecord(2)),
                        STAGED_AT));

        assertEquals(
                "{call stg.usp_stage_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                recording.statementSql());
        assertEquals(2, recording.batches().size());
        assertEquals(EXECUTION_ID.toString(), recording.batches().get(0).get(0));
        assertEquals(7, recording.batches().get(0).get(1));
        assertEquals(1, recording.batches().get(0).get(2));
        assertEquals("row-v1", recording.batches().get(0).get(4));
        assertEquals("presence-v1", recording.batches().get(0).get(6));
        assertEquals("QUARANTINE", recording.batches().get(1).get(9));
        assertEquals(Timestamp.from(STAGED_AT), recording.batches().get(1).get(11));
        assertTrue(recording.committed());
        assertEquals(List.of(false), recording.autoCommitTransitions());
        assertTrue(recording.statementClosed());
        assertTrue(recording.connectionClosed());
        assertEquals(1, recording.executeBatchCount());
    }

    @Test
    void invokesCandidateSetPreparationAsOneParameterizedProcedure() {
        final RecordingJdbc recording = new RecordingJdbc();
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        kernel.prepareCandidateSet(permit);

        assertEquals(
                "{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}",
                recording.statementSql());
        assertEquals(
                List.of(
                        EXECUTION_ID.toString(),
                        permit.contractFingerprint().version(),
                        permit.contractFingerprint().sha256(),
                        permit.configurationFingerprint().version(),
                        permit.configurationFingerprint().sha256()),
                recording.parameters());
        assertEquals(1, recording.executeUpdateCount());
        assertTrue(recording.statementClosed());
        assertTrue(recording.connectionClosed());
    }

    @Test
    void rejectsNonShadowEffectsBeforeObtainingAConnection() {
        final RecordingJdbc recording = new RecordingJdbc();
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());
        final ContractPromotionPermit cutoverPermit =
                ContractTestSupport.promotionPermit(
                        EXECUTION_ID,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);

        assertThrows(
                IllegalArgumentException.class, () -> kernel.prepareCandidateSet(cutoverPermit));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        kernel.applyReconcileAndPublish(
                                cutoverPermit,
                                DataQualityTestSupport.promotionPermit(EXECUTION_ID)));

        assertEquals(null, recording.statementSql());
        assertEquals(0, recording.executeUpdateCount());
        assertEquals(0, recording.executeQueryCount());
    }

    @Test
    void mapsOneAggregateAtomicPublicationResultWithoutMaterializingKeys() {
        final RecordingJdbc recording = new RecordingJdbc();
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        final StagingPublicationResult result =
                kernel.applyReconcileAndPublish(
                        permit, DataQualityTestSupport.promotionPermit(EXECUTION_ID));

        assertEquals(
                "{call core.usp_apply_reconcile_publish_execution(?, ?, ?, ?, ?)}",
                recording.statementSql());
        assertEquals(
                List.of(
                        EXECUTION_ID.toString(),
                        permit.contractFingerprint().version(),
                        permit.contractFingerprint().sha256(),
                        permit.configurationFingerprint().version(),
                        permit.configurationFingerprint().sha256()),
                recording.parameters());
        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals(10, result.candidateRows());
        assertEquals(2, result.insertedRows());
        assertEquals(3, result.updatedRows());
        assertEquals(1, result.reactivatedRows());
        assertEquals(4, result.noopRows());
        assertEquals(2, result.staleNoopRows());
        assertEquals(
                Optional.of(Instant.parse("2026-08-30T01:00:00Z")),
                result.incrementalFrontierBefore());
        assertEquals(
                Optional.of(Instant.parse("2026-08-30T02:00:00Z")),
                result.incrementalFrontierAfter());
        assertEquals(1, recording.executeQueryCount());
        assertTrue(recording.statementClosed());
        assertTrue(recording.connectionClosed());
    }

    @Test
    void preservesTheSqlCauseWithoutLeakingItsMessage() {
        final SQLException cause = new SQLException("synthetic-sensitive-detail");
        final DataSource unavailable =
                (DataSource)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("getConnection")) {
                                        throw cause;
                                    }
                                    return defaultValue(method.getReturnType());
                                });
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(unavailable);

        final StagingPersistenceException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                kernel.applyReconcileAndPublish(
                                        ContractTestSupport.promotionPermit(EXECUTION_ID),
                                        DataQualityTestSupport.promotionPermit(EXECUTION_ID)));

        assertFalse(exception.getMessage().contains("synthetic-sensitive-detail"));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void requiresAnExactDataQualityPermitBeforeCallingSql() {
        final RecordingJdbc recording = new RecordingJdbc();
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());
        final ContractPromotionPermit contractPermit =
                ContractTestSupport.promotionPermit(EXECUTION_ID);

        assertThrows(
                NullPointerException.class,
                () -> kernel.applyReconcileAndPublish(contractPermit, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        kernel.applyReconcileAndPublish(
                                contractPermit,
                                DataQualityTestSupport.promotionPermit(UUID.randomUUID())));
        assertEquals(null, recording.statementSql());
    }

    @Test
    void refusesAnAbsentDuplicatedMalformedOrMismatchedPublicationSummary() {
        final Map<String, Object> nullCandidateRows = publicationRow();
        nullCandidateRows.put("candidate_rows", null);
        final Map<String, Object> mismatchedEquation = publicationRow();
        mismatchedEquation.put("candidate_rows", 11L);
        final Map<String, Object> mismatchedExecution = publicationRow();
        mismatchedExecution.put("execution_id", UUID.randomUUID().toString());

        final List<List<Map<String, Object>>> invalidResults =
                List.of(
                        List.of(),
                        List.of(publicationRow(), publicationRow()),
                        List.of(nullCandidateRows),
                        List.of(mismatchedEquation),
                        List.of(mismatchedExecution));

        for (final List<Map<String, Object>> rows : invalidResults) {
            final RecordingJdbc recording = new RecordingJdbc().publicationRows(rows);
            final JdbcSqlServerStagingPromotionKernel kernel =
                    new JdbcSqlServerStagingPromotionKernel(recording.dataSource());

            final StagingPersistenceException exception =
                    assertThrows(
                            StagingPersistenceException.class,
                            () ->
                                    kernel.applyReconcileAndPublish(
                                            ContractTestSupport.promotionPermit(EXECUTION_ID),
                                            DataQualityTestSupport.promotionPermit(EXECUTION_ID)));
            assertInstanceOf(SQLException.class, exception.getCause());
            assertFalse(exception.getMessage().contains(EXECUTION_ID.toString()));
        }
    }

    @Test
    void rollsBackAFailedBatchWithoutRestoringAutoCommitOnTheOwnedConnection() {
        final SQLException batchFailure = new SQLException("synthetic-batch-sensitive-detail");
        final RecordingJdbc recording = new RecordingJdbc().failExecuteBatchWith(batchFailure);
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());

        final StagingPersistenceException exception =
                assertThrows(
                        StagingPersistenceException.class, () -> kernel.stage(singleRecordBatch()));

        assertSame(batchFailure, exception.getCause());
        assertEquals(1, recording.rollbackAttempts());
        assertFalse(recording.committed());
        assertEquals(List.of(false), recording.autoCommitTransitions());
        assertTrue(recording.statementClosed());
        assertTrue(recording.connectionClosed());
        assertFalse(exception.getMessage().contains("synthetic-batch-sensitive-detail"));
        assertFalse(exception.toString().contains("synthetic-batch-sensitive-detail"));
    }

    @Test
    void preservesARollbackFailureAsSuppressedWithoutLeakingItsDetails() {
        final SQLException batchFailure = new SQLException("synthetic-batch-sensitive-detail");
        final SQLException rollbackFailure =
                new SQLException("synthetic-rollback-sensitive-detail");
        final RecordingJdbc recording =
                new RecordingJdbc()
                        .failExecuteBatchWith(batchFailure)
                        .failRollbackWith(rollbackFailure);
        final JdbcSqlServerStagingPromotionKernel kernel =
                new JdbcSqlServerStagingPromotionKernel(recording.dataSource());

        final StagingPersistenceException exception =
                assertThrows(
                        StagingPersistenceException.class, () -> kernel.stage(singleRecordBatch()));

        assertSame(batchFailure, exception.getCause());
        assertEquals(1, exception.getCause().getSuppressed().length);
        assertSame(rollbackFailure, exception.getCause().getSuppressed()[0]);
        assertEquals(1, recording.rollbackAttempts());
        assertFalse(recording.committed());
        assertEquals(List.of(false), recording.autoCommitTransitions());
        assertTrue(recording.statementClosed());
        assertTrue(recording.connectionClosed());
        assertFalse(exception.getMessage().contains("synthetic-batch-sensitive-detail"));
        assertFalse(exception.getMessage().contains("synthetic-rollback-sensitive-detail"));
        assertFalse(exception.toString().contains("synthetic-batch-sensitive-detail"));
        assertFalse(exception.toString().contains("synthetic-rollback-sensitive-detail"));
    }

    private static StagingBatch singleRecordBatch() {
        return new StagingBatch(EXECUTION_ID, 7, 1, List.of(validRecord(1)), STAGED_AT);
    }

    private static StagingRecord validRecord(final int ordinal) {
        return new StagingRecord(
                ordinal,
                "synthetic-key-" + ordinal,
                new ImmutableFingerprint("row-v1", "a".repeat(64)),
                new ImmutableFingerprint("presence-v1", "b".repeat(64)),
                Instant.parse("2026-08-30T00:00:00Z"),
                StagingDisposition.VALID,
                null);
    }

    private static StagingRecord quarantineRecord(final int ordinal) {
        return new StagingRecord(
                ordinal,
                null,
                null,
                null,
                null,
                StagingDisposition.QUARANTINE,
                "SOURCE_KEY_MISSING");
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
        row.put("reconciled_at_utc", Timestamp.from(STAGED_AT.plusSeconds(1)));
        row.put("published_at_utc", Timestamp.from(STAGED_AT.plusSeconds(2)));
        row.put(
                "incremental_frontier_before_utc",
                Timestamp.from(Instant.parse("2026-08-30T01:00:00Z")));
        row.put(
                "incremental_frontier_after_utc",
                Timestamp.from(Instant.parse("2026-08-30T02:00:00Z")));
        return row;
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
            return '\0';
        }
        return null;
    }

    private static final class RecordingJdbc {

        private final List<Object> parameters = new ArrayList<>();
        private final List<List<Object>> batches = new ArrayList<>();
        private final List<Boolean> autoCommitTransitions = new ArrayList<>();
        private String statementSql;
        private boolean statementClosed;
        private boolean connectionClosed;
        private boolean committed;
        private int rollbackAttempts;
        private int executeBatchCount;
        private int executeUpdateCount;
        private int executeQueryCount;
        private SQLException executeBatchFailure;
        private SQLException rollbackFailure;
        private List<Map<String, Object>> publicationRows = List.of(publicationRow());

        RecordingJdbc failExecuteBatchWith(final SQLException failure) {
            executeBatchFailure = failure;
            return this;
        }

        RecordingJdbc failRollbackWith(final SQLException failure) {
            rollbackFailure = failure;
            return this;
        }

        RecordingJdbc publicationRows(final List<Map<String, Object>> rows) {
            final List<Map<String, Object>> copy = new ArrayList<>();
            for (final Map<String, Object> row : rows) {
                copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            }
            publicationRows = Collections.unmodifiableList(copy);
            return this;
        }

        DataSource dataSource() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("getConnection")) {
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
                                if (method.getName().equals("getAutoCommit")) {
                                    return true;
                                }
                                if (method.getName().equals("setAutoCommit")) {
                                    autoCommitTransitions.add((Boolean) arguments[0]);
                                    return null;
                                }
                                if (method.getName().equals("commit")) {
                                    committed = true;
                                    return null;
                                }
                                if (method.getName().equals("rollback")) {
                                    rollbackAttempts++;
                                    if (rollbackFailure != null) {
                                        throw rollbackFailure;
                                    }
                                    return null;
                                }
                                if (method.getName().equals("close")) {
                                    connectionClosed = true;
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private CallableStatement statement() {
            final InvocationHandler handler =
                    (proxy, method, arguments) -> handleStatement(method, arguments);
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            handler);
        }

        private Object handleStatement(final Method method, final Object[] arguments)
                throws SQLException {
            final String methodName = method.getName();
            if (methodName.equals("setString") || methodName.equals("setInt")) {
                setParameter((Integer) arguments[0], arguments[1]);
                return null;
            }
            if (methodName.equals("setTimestamp")) {
                assertEquals(3, arguments.length);
                assertEquals("UTC", ((java.util.Calendar) arguments[2]).getTimeZone().getID());
                setParameter((Integer) arguments[0], arguments[1]);
                return null;
            }
            if (methodName.equals("setNull")) {
                setParameter((Integer) arguments[0], null);
                return null;
            }
            if (methodName.equals("addBatch")) {
                batches.add(new ArrayList<>(parameters));
                return null;
            }
            if (methodName.equals("executeBatch")) {
                executeBatchCount++;
                if (executeBatchFailure != null) {
                    throw executeBatchFailure;
                }
                return new int[batches.size()];
            }
            if (methodName.equals("executeUpdate")) {
                executeUpdateCount++;
                return 0;
            }
            if (methodName.equals("executeQuery")) {
                executeQueryCount++;
                return publicationResultSet();
            }
            if (methodName.equals("close")) {
                statementClosed = true;
                return null;
            }
            return defaultValue(method.getReturnType());
        }

        private ResultSet publicationResultSet() {
            final int[] cursor = {-1};
            final boolean[] wasNull = {false};
            final InvocationHandler handler =
                    (proxy, method, arguments) -> {
                        final String methodName = method.getName();
                        if (methodName.equals("next")) {
                            cursor[0]++;
                            return cursor[0] < publicationRows.size();
                        }
                        if (methodName.equals("getString")) {
                            final Object value = valueAt(cursor[0], (String) arguments[0]);
                            wasNull[0] = value == null;
                            return value == null ? null : value.toString();
                        }
                        if (methodName.equals("getLong")) {
                            final Object value = valueAt(cursor[0], (String) arguments[0]);
                            wasNull[0] = value == null;
                            return value == null ? 0L : ((Number) value).longValue();
                        }
                        if (methodName.equals("getTimestamp")) {
                            assertEquals(2, arguments.length);
                            assertEquals(
                                    "UTC",
                                    ((java.util.Calendar) arguments[1]).getTimeZone().getID());
                            final Object value = valueAt(cursor[0], (String) arguments[0]);
                            wasNull[0] = value == null;
                            return value;
                        }
                        if (methodName.equals("wasNull")) {
                            return wasNull[0];
                        }
                        return defaultValue(method.getReturnType());
                    };
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
        }

        private Object valueAt(final int rowIndex, final String column) throws SQLException {
            if (rowIndex < 0 || rowIndex >= publicationRows.size()) {
                throw new SQLException("Synthetic cursor outside the result set.");
            }
            return publicationRows.get(rowIndex).get(column);
        }

        private void setParameter(final int position, final Object value) {
            while (parameters.size() < position) {
                parameters.add(null);
            }
            parameters.set(position - 1, value);
        }

        String statementSql() {
            return statementSql;
        }

        List<Object> parameters() {
            return parameters;
        }

        List<List<Object>> batches() {
            return batches;
        }

        List<Boolean> autoCommitTransitions() {
            return autoCommitTransitions;
        }

        boolean statementClosed() {
            return statementClosed;
        }

        boolean connectionClosed() {
            return connectionClosed;
        }

        boolean committed() {
            return committed;
        }

        int rollbackAttempts() {
            return rollbackAttempts;
        }

        int executeBatchCount() {
            return executeBatchCount;
        }

        int executeUpdateCount() {
            return executeUpdateCount;
        }

        int executeQueryCount() {
            return executeQueryCount;
        }
    }
}
