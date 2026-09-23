package br.com.esl.etl.v2.plataforma.persistencia.localizacaocargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
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

class JdbcSqlServerLocalizacaoCargaGatewaysTest {
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-000000008656");
    private static final Instant OBSERVED_AT = Instant.parse("2036-03-20T12:00:00Z");

    @Test
    void stagesOneBoundedPageThroughTheClosedProcedureAndCommits() throws Exception {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final var record =
                new LocalizacaoCargaDataExportRecordMapper()
                        .map(
                                1,
                                "{\"corporation_sequence_number\":8656,"
                                        + "\"service_at\":\"2036-03-20T12:00:00Z\","
                                        + "\"invoices_volumes\":0}");
        final var batch =
                new LocalizacaoCargaStageBatch(EXECUTION, 1, List.of(record), OBSERVED_AT);

        new JdbcSqlServerLocalizacaoCargaStagingGateway(jdbc.dataSource()).stage(batch);

        assertTrue(jdbc.sql.startsWith("{call stg.usp_stage_localizacao_carga_record("));
        assertEquals("INTEGER:8656", jdbc.parameters.get(4));
        assertEquals(0, jdbc.parameters.get(12));
        assertEquals("VALID", jdbc.parameters.get(25));
        assertEquals(Timestamp.from(OBSERVED_AT), jdbc.parameters.get(27));
        assertEquals(1, jdbc.batchCount);
        assertTrue(jdbc.committed);
        assertFalse(jdbc.autoCommit);
    }

    @Test
    void rollsBackDriverFailureAndSanitizesThePublicMessage() throws Exception {
        final RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.executeFailure = new SQLException("synthetic-sensitive-driver-value");
        final var batch = validBatch();

        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerLocalizacaoCargaStagingGateway(jdbc.dataSource())
                                        .stage(batch));

        assertEquals(1, jdbc.rollbackCount);
        assertFalse(failure.getMessage().contains("synthetic-sensitive-driver-value"));
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    @Test
    void rollbackAlsoCoversCancellationAfterExecuteAndPreservesRollbackFailure() throws Exception {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final SQLException rollbackFailure = new SQLException("synthetic-rollback-detail");
        jdbc.rollbackFailure = rollbackFailure;

        final ResilienceCancelledException failure =
                assertThrows(
                        ResilienceCancelledException.class,
                        () ->
                                new JdbcSqlServerLocalizacaoCargaStagingGateway(jdbc.dataSource())
                                        .stage(validBatch(), new CountingCancellation(3)));

        assertEquals(1, jdbc.batchExecutions);
        assertEquals(1, jdbc.rollbackCount);
        assertEquals(rollbackFailure, failure.getSuppressed()[0]);
        assertFalse(jdbc.committed);
    }

    @Test
    void rejectsMissingOrFailedBatchExecutionCounts() throws Exception {
        final RecordingJdbc missing = new RecordingJdbc();
        missing.batchResults = new int[0];
        assertThrows(
                StagingPersistenceException.class,
                () ->
                        new JdbcSqlServerLocalizacaoCargaStagingGateway(missing.dataSource())
                                .stage(validBatch()));

        final RecordingJdbc failed = new RecordingJdbc();
        failed.batchResults = new int[] {java.sql.Statement.EXECUTE_FAILED};
        assertThrows(
                StagingPersistenceException.class,
                () ->
                        new JdbcSqlServerLocalizacaoCargaStagingGateway(failed.dataSource())
                                .stage(validBatch()));
    }

    @Test
    void prepareUsesTheGenericCandidateSetAndApplyReadsExactlyElevenColumns() {
        final RecordingJdbc jdbc = new RecordingJdbc();
        final var gateway = new JdbcSqlServerLocalizacaoCargaPromotionGateway(jdbc.dataSource());
        final ContractPromotionPermit permit = ContractTestSupport.promotionPermit(EXECUTION);

        gateway.prepareCandidateSet(permit);
        assertEquals("{call core.usp_prepare_staged_execution(?, ?, ?, ?, ?)}", jdbc.sql);
        assertEquals(1, jdbc.updateCount);

        final StagingPublicationResult result =
                gateway.applyReconcileAndPublish(
                        permit, DataQualityTestSupport.promotionPermit(EXECUTION));
        assertEquals(
                "{call core.usp_apply_reconcile_publish_localizacao_cargas(?, ?, ?, ?, ?)}",
                jdbc.sql);
        assertEquals(EXECUTION, result.executionId());
        assertEquals(3, result.candidateRows());
        assertEquals(1, result.insertedRows());
        assertEquals(1, result.updatedRows());
        assertEquals(1, result.noopRows());
        assertEquals(OBSERVED_AT, result.reconciledAt());
        assertTrue(jdbc.resultSetClosed);
    }

    @Test
    void rejectsNonShadowMismatchedAndMalformedPromotionResults() {
        final var gateway =
                new JdbcSqlServerLocalizacaoCargaPromotionGateway(new RecordingJdbc().dataSource());
        final ContractPromotionPermit cutover =
                ContractTestSupport.promotionPermit(
                        EXECUTION,
                        SourceDataEffect.CUTOVER,
                        SourceCompletenessStatus.PROVEN_COMPLETE);
        assertThrows(IllegalArgumentException.class, () -> gateway.prepareCandidateSet(cutover));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        gateway.applyReconcileAndPublish(
                                ContractTestSupport.promotionPermit(EXECUTION),
                                DataQualityTestSupport.promotionPermit(UUID.randomUUID())));

        assertMalformed(List.of());
        assertMalformed(List.of(validRow(), validRow()));
        assertMalformed(List.of(rowWith("execution_id", UUID.randomUUID().toString())));
        assertMalformed(List.of(rowWith("execution_id", "not-a-uuid")));
        assertMalformed(List.of(rowWith("candidate_rows", null)));
        assertMalformed(List.of(rowWith("candidate_rows", -1L)));
        assertMalformed(List.of(rowWith("candidate_rows", 99L)));
        assertMalformed(List.of(rowWith("reconciled_at_utc", null)));
        assertMalformed(List.of(rowWith("incremental_frontier_after_utc", null)));
    }

    @Test
    void nullsCancellationAndConnectionFailureDoNotLeakOrPerformUnexpectedIo() {
        assertThrows(
                NullPointerException.class,
                () -> new JdbcSqlServerLocalizacaoCargaStagingGateway(null));
        assertThrows(
                NullPointerException.class,
                () -> new JdbcSqlServerLocalizacaoCargaPromotionGateway(null));

        final RecordingJdbc jdbc = new RecordingJdbc();
        final var staging = new JdbcSqlServerLocalizacaoCargaStagingGateway(jdbc.dataSource());
        assertThrows(NullPointerException.class, () -> staging.stage(null));
        assertThrows(
                ResilienceCancelledException.class, () -> staging.stage(validBatch(), () -> true));
        assertEquals(0, jdbc.connectionCount);

        final SQLException cause = new SQLException("synthetic-sensitive-connection");
        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerLocalizacaoCargaPromotionGateway(
                                                unavailable(cause))
                                        .prepareCandidateSet(
                                                ContractTestSupport.promotionPermit(EXECUTION)));
        assertEquals(cause, failure.getCause());
        assertFalse(failure.getMessage().contains("synthetic-sensitive-connection"));
    }

    private static LocalizacaoCargaStageBatch validBatch() throws Exception {
        final var record =
                new LocalizacaoCargaDataExportRecordMapper()
                        .map(
                                1,
                                new ObjectMapper()
                                        .readTree(
                                                "{\"corporation_sequence_number\":8656,\"service_at\":\"2036-03-20T12:00:00Z\"}"));
        return new LocalizacaoCargaStageBatch(EXECUTION, 1, List.of(record), OBSERVED_AT);
    }

    private static void assertMalformed(final List<Map<String, Object>> rows) {
        final RecordingJdbc jdbc = new RecordingJdbc().withRows(rows);
        final StagingPersistenceException failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                new JdbcSqlServerLocalizacaoCargaPromotionGateway(jdbc.dataSource())
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
                        JdbcSqlServerLocalizacaoCargaGatewaysTest.class.getClassLoader(),
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
        private int batchExecutions;
        private int rollbackCount;
        private int updateCount;
        private int connectionCount;
        private SQLException executeFailure;
        private SQLException rollbackFailure;
        private int[] batchResults = new int[] {1};
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
                                                    "setBigDecimal",
                                                    "setTimestamp",
                                                    "setNull" ->
                                            parameters.put((Integer) arguments[0], arguments[1]);
                                    case "addBatch" -> batchCount++;
                                    case "executeBatch" -> {
                                        batchExecutions++;
                                        if (executeFailure != null) {
                                            throw executeFailure;
                                        }
                                        return batchResults;
                                    }
                                    case "executeUpdate" -> {
                                        updateCount++;
                                        return 1;
                                    }
                                    case "executeQuery" -> {
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
