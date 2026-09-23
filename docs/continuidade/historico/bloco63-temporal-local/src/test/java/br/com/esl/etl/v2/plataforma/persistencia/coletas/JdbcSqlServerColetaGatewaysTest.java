package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
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

class JdbcSqlServerColetaGatewaysTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-04T20:00:00Z");

    @Test
    void stagesOneBoundedPageInOneTransactionAndKeepsQuarantineTyped() {
        final StagingJdbc jdbc = new StagingJdbc();
        final JdbcSqlServerColetaStagingGateway gateway =
                new JdbcSqlServerColetaStagingGateway(jdbc.dataSource());

        gateway.stage(batch());

        assertEquals(
                "{call stg.usp_stage_coleta_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(2, jdbc.batches.size());
        final Map<Integer, Object> valid = jdbc.batches.get(0);
        assertEquals(EXECUTION_ID.toString(), valid.get(1));
        assertEquals("INTEGER:10", valid.get(4));
        assertEquals("INTEGER", valid.get(5));
        assertEquals("VALUE", valid.get(6));
        assertEquals("{\"value\":10}", valid.get(7));
        assertEquals("done", valid.get(12));
        assertEquals("Coletada", valid.get(13));
        assertEquals(true, valid.get(15));
        assertEquals("STATUS_UPDATED_AT", valid.get(20));
        assertEquals("VALID", valid.get(21));
        assertEquals(Timestamp.from(OBSERVED_AT), valid.get(23));
        final Map<Integer, Object> quarantined = jdbc.batches.get(1);
        assertEquals(null, quarantined.get(4));
        assertEquals("QUARANTINE", quarantined.get(21));
        assertEquals("MISSING_SOURCE_KEY", quarantined.get(22));
        assertEquals(List.of(false), jdbc.autoCommitTransitions);
        assertTrue(jdbc.committed);
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
    }

    @Test
    void rollsBackStagingFailureWithoutLeakingDriverDetails() {
        final SQLException failure = new SQLException("synthetic-sensitive-batch");
        final StagingJdbc jdbc = new StagingJdbc().failExecute(failure);
        final JdbcSqlServerColetaStagingGateway gateway =
                new JdbcSqlServerColetaStagingGateway(jdbc.dataSource());

        final StagingPersistenceException exception =
                assertThrows(StagingPersistenceException.class, () -> gateway.stage(batch()));

        assertEquals(failure, exception.getCause());
        assertEquals(1, jdbc.rollbackAttempts);
        assertFalse(exception.getMessage().contains("synthetic-sensitive-batch"));
        assertTrue(jdbc.statementClosed);
        assertTrue(jdbc.connectionClosed);
        assertThrows(NullPointerException.class, () -> gateway.stage(null));
        assertThrows(NullPointerException.class, () -> new JdbcSqlServerColetaStagingGateway(null));
    }

    @Test
    void preparesAndAppliesOnlyWithMatchingShadowPermits() {
        final PromotionJdbc jdbc = new PromotionJdbc();
        final JdbcSqlServerColetaPromotionGateway gateway =
                new JdbcSqlServerColetaPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);

        gateway.prepareCandidateSet(permit);
        assertEquals("{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}", jdbc.statementSql);
        assertEquals(1, jdbc.executeUpdateCount);

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        permit, DataQualityTestSupport.promotionPermit(EXECUTION_ID));
        assertEquals(
                "{call core.usp_apply_reconcile_publish_coletas(?, ?, ?, ?, ?)}",
                jdbc.statementSql);
        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals(3, result.candidateRows());
        assertEquals(1, result.insertedRows());
        assertEquals(1, result.updatedRows());
        assertEquals(1, result.noopRows());
        assertEquals(1, jdbc.executeQueryCount);
        assertTrue(jdbc.resultSetClosed);

        final ContractPromotionPermit cutover =
                ContractTestSupport.promotionPermit(
                        EXECUTION_ID,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);
        assertThrows(IllegalArgumentException.class, () -> gateway.prepareCandidateSet(cutover));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                permit, DataQualityTestSupport.promotionPermit(UUID.randomUUID())));
        assertThrows(
                NullPointerException.class, () -> gateway.applyReconcileAndPublish(permit, null));
    }

    @Test
    void wrapsSqlAndMalformedAggregateFailures() {
        final SQLException connectionFailure = new SQLException("synthetic-sensitive-connect");
        final DataSource unavailable = unavailable(connectionFailure);
        final JdbcSqlServerColetaPromotionGateway gateway =
                new JdbcSqlServerColetaPromotionGateway(unavailable);
        final StagingPersistenceException exception =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                gateway.prepareCandidateSet(
                                        ContractTestSupport.promotionPermit(EXECUTION_ID)));
        assertEquals(connectionFailure, exception.getCause());
        assertFalse(exception.getMessage().contains("synthetic-sensitive-connect"));

        final PromotionJdbc malformed = new PromotionJdbc().withRows(List.of());
        final StagingPersistenceException malformedException =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerColetaPromotionGateway(malformed.dataSource())
                                        .applyReconcileAndPublish(
                                                ContractTestSupport.promotionPermit(EXECUTION_ID),
                                                DataQualityTestSupport.promotionPermit(
                                                        EXECUTION_ID)));
        assertInstanceOf(SQLException.class, malformedException.getCause());
    }

    @Test
    void cancellationStopsStagingAndPromotionBeforeJdbcAccess() {
        final StagingJdbc staging = new StagingJdbc();
        final JdbcSqlServerColetaStagingGateway stagingGateway =
                new JdbcSqlServerColetaStagingGateway(staging.dataSource());
        assertThrows(
                ResilienceCancelledException.class,
                () -> stagingGateway.stage(batch(), () -> true));
        assertEquals(0, staging.connectionAttempts);

        final PromotionJdbc promotion = new PromotionJdbc();
        final JdbcSqlServerColetaPromotionGateway promotionGateway =
                new JdbcSqlServerColetaPromotionGateway(promotion.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION_ID);
        assertThrows(
                ResilienceCancelledException.class,
                () -> promotionGateway.prepareCandidateSet(permit, () -> true));
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        promotionGateway.applyReconcileAndPublish(
                                permit,
                                DataQualityTestSupport.promotionPermit(EXECUTION_ID),
                                () -> true));
        assertEquals(0, promotion.connectionAttempts);
    }

    private static ColetaStageBatch batch() {
        final ColetaStageRecord valid =
                ColetaStageRecord.valid(
                        1,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:10"),
                        ColetaAttributePresence.VALUE,
                        "10",
                        "{}",
                        "{}",
                        "{}",
                        ColetaStatus.resolve("done", null),
                        "2026-09-04T17:00:00-03:00",
                        OBSERVED_AT,
                        ColetaFreshnessOrigin.STATUS_UPDATED_AT);
        return new ColetaStageBatch(
                EXECUTION_ID,
                1,
                List.of(valid, ColetaStageRecord.quarantine(2, null, "MISSING_SOURCE_KEY")),
                OBSERVED_AT);
    }

    private static DataSource unavailable(final SQLException failure) {
        return (DataSource)
                Proxy.newProxyInstance(
                        JdbcSqlServerColetaGatewaysTest.class.getClassLoader(),
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

        private StagingJdbc failExecute(final SQLException failure) {
            executeFailure = failure;
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
                                    case "setString", "setInt", "setBoolean" -> {
                                        parameters.put((Integer) arguments[0], arguments[1]);
                                        return null;
                                    }
                                    case "setNull" -> {
                                        parameters.put((Integer) arguments[0], null);
                                        return null;
                                    }
                                    case "setTimestamp" -> {
                                        assertEquals(
                                                "UTC",
                                                ((java.util.Calendar) arguments[2])
                                                        .getTimeZone()
                                                        .getID());
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
        private int executeQueryCount;
        private int connectionAttempts;
        private boolean resultSetClosed;
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
                                if (method.getName().equals("executeUpdate")) {
                                    executeUpdateCount++;
                                    return 0;
                                }
                                if (method.getName().equals("executeQuery")) {
                                    executeQueryCount++;
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
