package br.com.esl.etl.v2.plataforma.persistencia.cotacoes;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoFreshnessOrigin;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageBatch;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcSqlServerCotacaoGatewaysTest {
    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000027");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-04T20:00:00Z");

    @Test
    void stagesOneBoundedPageThroughOnlyTheClosedProcedure() {
        final StagingJdbc jdbc = new StagingJdbc();
        new JdbcSqlServerCotacaoStagingGateway(jdbc.dataSource()).stage(batch());

        assertEquals(
                "{call stg.usp_stage_cotacao_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(2, jdbc.batches.size());
        assertEquals("INTEGER:6906", jdbc.batches.get(0).get(4));
        assertEquals("José", jdbc.batches.get(0).get(7));
        assertEquals(LocalDate.of(2026, 9, 4), jdbc.batches.get(0).get(11));
        assertEquals("VALID", jdbc.batches.get(0).get(16));
        assertEquals("QUARANTINE", jdbc.batches.get(1).get(16));
        assertEquals("EQUAL_FRESHNESS_CONFLICT", jdbc.batches.get(1).get(17));
        assertAll(
                () -> assertNull(jdbc.batches.get(1).get(4)),
                () -> assertNull(jdbc.batches.get(1).get(5)),
                () -> assertNull(jdbc.batches.get(1).get(6)));
        assertEquals(Timestamp.from(OBSERVED_AT), jdbc.batches.get(0).get(18));
        assertEquals(List.of(false), jdbc.autoCommitTransitions);
        assertTrue(jdbc.committed);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
    }

    @Test
    void rollsBackAStagingFailureWithoutLeakingDriverDetails() {
        final SQLException failure = new SQLException("synthetic-sensitive-page");
        final SQLException rollbackFailure = new SQLException("synthetic-sensitive-rollback");
        final StagingJdbc jdbc =
                new StagingJdbc().failExecute(failure).failRollback(rollbackFailure);

        final StagingPersistenceException exception =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerCotacaoStagingGateway(jdbc.dataSource())
                                        .stage(batch()));

        assertSame(failure, exception.getCause());
        assertEquals(1, jdbc.rollbackAttempts);
        assertEquals(1, exception.getCause().getSuppressed().length);
        assertSame(rollbackFailure, exception.getCause().getSuppressed()[0]);
        assertFalse(exception.getMessage().contains("synthetic-sensitive-page"));
        assertThrows(
                NullPointerException.class, () -> new JdbcSqlServerCotacaoStagingGateway(null));
    }

    @Test
    void validatesTheBatchBeforeOpeningJdbcResources() {
        final StagingJdbc jdbc = new StagingJdbc();
        final JdbcSqlServerCotacaoStagingGateway gateway =
                new JdbcSqlServerCotacaoStagingGateway(jdbc.dataSource());

        assertThrows(NullPointerException.class, () -> gateway.stage(null));
        assertEquals(0, jdbc.connectionAttempts);
    }

    @Test
    void promotesOnlyWithMatchingShadowPermitsAndExplicitRelease() {
        final PromotionJdbc jdbc = new PromotionJdbc();
        final JdbcSqlServerCotacaoPromotionGateway gateway =
                new JdbcSqlServerCotacaoPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        gateway.prepareCandidateSet(permit);
        assertEquals("{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}", jdbc.statementSql);
        assertEquals(1, jdbc.executeUpdateCount);

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        permit, DataQualityTestSupport.promotionPermit(EXECUTION_ID), 77L);
        assertEquals(
                "{call core.usp_apply_reconcile_publish_cotacoes(?, ?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(77L, jdbc.parameters.get(6));
        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals(1, result.insertedRows());
        assertEquals(1, result.updatedRows());
        assertTrue(jdbc.resultSetClosed);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                permit, DataQualityTestSupport.promotionPermit(EXECUTION_ID), 0L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                permit,
                                DataQualityTestSupport.promotionPermit(UUID.randomUUID()),
                                77L));
        final ContractPromotionPermit cutover =
                ContractTestSupport.promotionPermit(
                        EXECUTION_ID,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);
        assertThrows(IllegalArgumentException.class, () -> gateway.prepareCandidateSet(cutover));
    }

    @Test
    void wrapsSqlAndMalformedAggregateFailures() {
        final SQLException failure = new SQLException("synthetic-sensitive-connect");
        final StagingPersistenceException connectionException =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerCotacaoPromotionGateway(unavailable(failure))
                                        .prepareCandidateSet(
                                                ContractTestSupport.promotionPermit(EXECUTION_ID)));
        assertEquals(failure, connectionException.getCause());
        assertFalse(connectionException.getMessage().contains("synthetic-sensitive-connect"));

        final StagingPersistenceException malformed =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerCotacaoPromotionGateway(
                                                new PromotionJdbc()
                                                        .withRows(List.of())
                                                        .dataSource())
                                        .applyReconcileAndPublish(
                                                ContractTestSupport.promotionPermit(EXECUTION_ID),
                                                DataQualityTestSupport.promotionPermit(
                                                        EXECUTION_ID),
                                                77L));
        assertInstanceOf(SQLException.class, malformed.getCause());
    }

    @Test
    void rejectsAmbiguousOrIncoherentPromotionAggregates() {
        final Map<String, Object> wrongExecution =
                rowWith("execution_id", "00000000-0000-0000-0000-000000000099");
        final Map<String, Object> invalidExecution =
                rowWith("execution_id", "synthetic-sensitive-execution");
        final Map<String, Object> inconsistentCounts = rowWith("candidate_rows", 99L);

        assertAll(
                () -> assertMalformedAggregate(List.of(PromotionJdbc.row(), PromotionJdbc.row())),
                () -> assertMalformedAggregate(List.of(wrongExecution)),
                () -> {
                    final StagingPersistenceException exception =
                            assertMalformedAggregate(List.of(invalidExecution));
                    assertFalse(exception.toString().contains("synthetic-sensitive-execution"));
                },
                () -> assertMalformedAggregate(List.of(inconsistentCounts)));
    }

    @Test
    void cancellationStopsStagingAndPromotionBeforeJdbcAccess() {
        final Method cancellableStage =
                assertDoesNotThrow(
                        () ->
                                JdbcSqlServerCotacaoStagingGateway.class.getMethod(
                                        "stage", CotacaoStageBatch.class, CancellationToken.class));
        final StagingJdbc staging = new StagingJdbc();
        final InvocationTargetException stagingCancellation =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                cancellableStage.invoke(
                                        new JdbcSqlServerCotacaoStagingGateway(
                                                staging.dataSource()),
                                        batch(),
                                        (CancellationToken) () -> true));
        assertInstanceOf(ResilienceCancelledException.class, stagingCancellation.getCause());
        assertEquals(0, staging.connectionAttempts);

        final Method cancellablePrepare =
                assertDoesNotThrow(
                        () ->
                                JdbcSqlServerCotacaoPromotionGateway.class.getMethod(
                                        "prepareCandidateSet",
                                        ContractPromotionPermit.class,
                                        CancellationToken.class));
        final Method cancellableApply =
                assertDoesNotThrow(
                        () ->
                                JdbcSqlServerCotacaoPromotionGateway.class.getMethod(
                                        "applyReconcileAndPublish",
                                        ContractPromotionPermit.class,
                                        DataQualityPromotionPermit.class,
                                        long.class,
                                        CancellationToken.class));
        final PromotionJdbc promotion = new PromotionJdbc();
        final JdbcSqlServerCotacaoPromotionGateway promotionGateway =
                new JdbcSqlServerCotacaoPromotionGateway(promotion.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);
        final InvocationTargetException prepareCancellation =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                cancellablePrepare.invoke(
                                        promotionGateway, permit, (CancellationToken) () -> true));
        assertInstanceOf(ResilienceCancelledException.class, prepareCancellation.getCause());
        final InvocationTargetException applyCancellation =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                cancellableApply.invoke(
                                        promotionGateway,
                                        permit,
                                        DataQualityTestSupport.promotionPermit(EXECUTION_ID),
                                        77L,
                                        (CancellationToken) () -> true));
        assertInstanceOf(ResilienceCancelledException.class, applyCancellation.getCause());
        assertEquals(0, promotion.connectionAttempts);
    }

    private static StagingPersistenceException assertMalformedAggregate(
            final List<Map<String, Object>> rows) {
        final StagingPersistenceException exception =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerCotacaoPromotionGateway(
                                                new PromotionJdbc().withRows(rows).dataSource())
                                        .applyReconcileAndPublish(
                                                ContractTestSupport.promotionPermit(EXECUTION_ID),
                                                DataQualityTestSupport.promotionPermit(
                                                        EXECUTION_ID),
                                                77L));
        assertInstanceOf(SQLException.class, exception.getCause());
        return exception;
    }

    private static Map<String, Object> rowWith(final String column, final Object value) {
        final Map<String, Object> row = new LinkedHashMap<>(PromotionJdbc.row());
        row.put(column, value);
        return row;
    }

    private static CotacaoStageBatch batch() {
        final CotacaoStageRecord valid =
                new CotacaoStageRecord(
                        1,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:6906"),
                        "{}",
                        "{}",
                        "  José  ",
                        null,
                        null,
                        OBSERVED_AT,
                        OBSERVED_AT,
                        CotacaoFreshnessOrigin.REQUESTED_AT,
                        LocalDate.of(2026, 9, 4),
                        new BigDecimal("10.2500"),
                        null,
                        "SP",
                        "RJ",
                        null);
        return new CotacaoStageBatch(
                EXECUTION_ID,
                1,
                List.of(valid, CotacaoStageRecord.quarantine(2, "EQUAL_FRESHNESS_CONFLICT")),
                OBSERVED_AT);
    }

    private static DataSource unavailable(final SQLException failure) {
        return (DataSource)
                Proxy.newProxyInstance(
                        JdbcSqlServerCotacaoGatewaysTest.class.getClassLoader(),
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

    private static final class StagingJdbc {
        private final List<Map<Integer, Object>> batches = new ArrayList<>();
        private final List<Boolean> autoCommitTransitions = new ArrayList<>();
        private String statementSql;
        private boolean committed;
        private boolean statementClosed;
        private boolean connectionClosed;
        private int rollbackAttempts;
        private int connectionAttempts;
        private SQLException executeFailure;
        private SQLException rollbackFailure;

        private StagingJdbc failExecute(final SQLException failure) {
            executeFailure = failure;
            return this;
        }

        private StagingJdbc failRollback(final SQLException failure) {
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
                                    connectionAttempts++;
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
                                    case "setString", "setInt", "setBigDecimal" -> {
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "setObject" -> {
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "setNull" -> {
                                        parameters.put((Integer) arguments[0], null);
                                        return null;
                                    }
                                    case "setTimestamp" -> {
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "addBatch" -> {
                                        batches.add(new LinkedHashMap<>(parameters));
                                        return null;
                                    }
                                    case "executeBatch" -> {
                                        if (executeFailure != null) {
                                            throw executeFailure;
                                        }
                                        return new int[0];
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

    private static final class PromotionJdbc {
        private String statementSql;
        private int executeUpdateCount;
        private boolean resultSetClosed;
        private int connectionAttempts;
        private Map<Integer, Object> parameters = new LinkedHashMap<>();
        private List<Map<String, Object>> rows = List.of(row());

        private PromotionJdbc withRows(final List<Map<String, Object>> values) {
            rows = values;
            return this;
        }

        private DataSource dataSource() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("getConnection")) {
                                    connectionAttempts++;
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
                                    parameters = new LinkedHashMap<>();
                                    return statement();
                                }
                                return defaultValue(method);
                            });
        }

        private CallableStatement statement() {
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().startsWith("set") && arguments != null) {
                                    parameters.put((Integer) arguments[0], arguments[1]);
                                    return null;
                                }
                                if (method.getName().equals("executeUpdate")) {
                                    executeUpdateCount++;
                                    return 0;
                                }
                                if (method.getName().equals("executeQuery")) {
                                    return resultSet();
                                }
                                return defaultValue(method);
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
                                if (method.getName().equals("next")) {
                                    cursor[0]++;
                                    return cursor[0] < rows.size();
                                }
                                if (method.getName().equals("getString")) {
                                    final Object value = value(cursor[0], (String) arguments[0]);
                                    wasNull[0] = value == null;
                                    return value == null ? null : value.toString();
                                }
                                if (method.getName().equals("getLong")) {
                                    final Object value = value(cursor[0], (String) arguments[0]);
                                    wasNull[0] = value == null;
                                    return value == null ? 0L : ((Number) value).longValue();
                                }
                                if (method.getName().equals("getTimestamp")) {
                                    final Object value = value(cursor[0], (String) arguments[0]);
                                    wasNull[0] = value == null;
                                    return value;
                                }
                                if (method.getName().equals("wasNull")) {
                                    return wasNull[0];
                                }
                                if (method.getName().equals("close")) {
                                    resultSetClosed = true;
                                    return null;
                                }
                                return defaultValue(method);
                            });
        }

        private Object value(final int rowIndex, final String column) throws SQLException {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                throw new SQLException("Synthetic result cursor is invalid.");
            }
            return rows.get(rowIndex).get(column);
        }

        private static Map<String, Object> row() {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("execution_id", EXECUTION_ID.toString());
            row.put("candidate_rows", 3L);
            row.put("inserted_rows", 1L);
            row.put("updated_rows", 1L);
            row.put("reactivated_rows", 0L);
            row.put("noop_rows", 1L);
            row.put("stale_noop_rows", 0L);
            row.put("reconciled_at_utc", Timestamp.from(OBSERVED_AT));
            row.put("published_at_utc", Timestamp.from(OBSERVED_AT.plusSeconds(1)));
            row.put("incremental_frontier_before_utc", null);
            row.put("incremental_frontier_after_utc", null);
            return row;
        }
    }
}
