package br.com.esl.etl.v2.plataforma.persistencia.sombra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit.ExecutionFailed;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit.ExecutionStarted;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit.PageRead;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFailureCategory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTraversalVerification;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcDataExportExtractionAuditTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant STARTED_AT = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void persistsOnlySanitizedExecutionAndPageMetadataThroughProcedures() {
        final RecordingDataSource dataSource = new RecordingDataSource();
        final JdbcDataExportExtractionAudit audit = new JdbcDataExportExtractionAudit(dataSource);

        audit.executionStarted(startedEvent());
        audit.pageRead(new PageRead(EXECUTION_ID, 1, 10, 12, 10, STARTED_AT.plusSeconds(5)));
        audit.executionCompleted(completedResult());
        audit.executionFailed(
                new ExecutionFailed(
                        EXECUTION_ID,
                        DataExportTemplate.COLETAS,
                        2,
                        12L,
                        STARTED_AT.plusSeconds(10),
                        DataExportFailureCategory.RUNTIME_FAILURE));

        assertEquals(4, dataSource.calls().size());
        assertEquals(
                "{call ctl.usp_audit_execution_started(?, ?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(0).statementSql());
        assertEquals(EXECUTION_ID.toString(), dataSource.calls().get(0).parameters().get(1));
        assertEquals(6908, dataSource.calls().get(0).parameters().get(2));
        assertEquals(
                Date.valueOf(LocalDate.of(2026, 8, 24)),
                dataSource.calls().get(0).parameters().get(3));
        assertEquals(
                Date.valueOf(LocalDate.of(2026, 8, 25)),
                dataSource.calls().get(0).parameters().get(4));
        assertEquals(Timestamp.from(STARTED_AT), dataSource.calls().get(0).parameters().get(5));
        assertEquals(
                Timestamp.from(STARTED_AT.plusSeconds(60)),
                dataSource.calls().get(0).parameters().get(6));
        assertEquals(Timestamp.from(STARTED_AT), dataSource.calls().get(0).parameters().get(7));
        assertEquals(
                "{call ctl.usp_audit_page_read(?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(1).statementSql());
        assertEquals(10, dataSource.calls().get(1).parameters().get(3));
        assertEquals(12, dataSource.calls().get(1).parameters().get(4));
        assertEquals(10, dataSource.calls().get(1).parameters().get(5));
        assertEquals(
                "{call ctl.usp_audit_execution_completed(?, ?, ?, ?, ?, ?)}",
                dataSource.calls().get(2).statementSql());
        assertEquals(2, dataSource.calls().get(2).parameters().get(2));
        assertEquals(12L, dataSource.calls().get(2).parameters().get(3));
        assertEquals(
                DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED.name(),
                dataSource.calls().get(2).parameters().get(6));
        assertEquals(
                "{call ctl.usp_audit_execution_failed(?, ?, ?, ?, ?)}",
                dataSource.calls().get(3).statementSql());
        assertEquals("RUNTIME_FAILURE", dataSource.calls().get(3).parameters().get(5));
    }

    @Test
    void storesNullForAnAbsentUpdateWindowInsteadOfInventingAWatermark() {
        final RecordingDataSource dataSource = new RecordingDataSource();
        final JdbcDataExportExtractionAudit audit = new JdbcDataExportExtractionAudit(dataSource);
        final ExecutionStarted withoutUpdateWindow =
                new ExecutionStarted(
                        EXECUTION_ID,
                        DataExportTemplate.FRETES,
                        new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 25)),
                        Optional.empty(),
                        STARTED_AT);

        audit.executionStarted(withoutUpdateWindow);

        assertNull(dataSource.calls().get(0).parameters().get(5));
        assertNull(dataSource.calls().get(0).parameters().get(6));
    }

    @Test
    void redactsDriverDetailsWhenTheAuditStoreIsUnavailable() {
        final String driverDetail = "driver-detail-that-must-not-leak";
        final JdbcDataExportExtractionAudit audit =
                new JdbcDataExportExtractionAudit(new FailingDataSource(driverDetail));

        final ShadowAuditPersistenceException exception =
                assertThrows(
                        ShadowAuditPersistenceException.class,
                        () -> audit.executionStarted(startedEvent()));

        assertEquals(
                "A auditoria de sombra não pôde registrar o início da execução.",
                exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
        assertFalse(exception.getMessage().contains("driver-detail-that-must-not-leak"));
        assertFalse(exception.toString().contains(driverDetail));
    }

    private static ExecutionStarted startedEvent() {
        return new ExecutionStarted(
                EXECUTION_ID,
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 25)),
                Optional.of(new SourceDateTimeRange(STARTED_AT, STARTED_AT.plusSeconds(60))),
                STARTED_AT);
    }

    private static DataExportExtractionResult completedResult() {
        return new DataExportExtractionResult(
                EXECUTION_ID,
                DataExportTemplate.COLETAS,
                2,
                12L,
                2,
                STARTED_AT,
                STARTED_AT.plusSeconds(10),
                DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED);
    }

    private record RecordedCall(String statementSql, Map<Integer, Object> parameters) {}

    private static class RecordingDataSource implements DataSource {

        private final List<RecordedCall> calls = new ArrayList<>();

        @Override
        public Connection getConnection() throws SQLException {
            return connection(calls);
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

        private final String driverDetail;

        private FailingDataSource(final String driverDetail) {
            this.driverDetail = driverDetail;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException(driverDetail);
        }
    }

    private static Connection connection(final List<RecordedCall> calls) {
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    if ("prepareCall".equals(method.getName())) {
                        return callableStatement(calls, (String) arguments[0]);
                    }
                    return defaultValue(method);
                };
        return (Connection)
                Proxy.newProxyInstance(
                        JdbcDataExportExtractionAuditTest.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        handler);
    }

    private static CallableStatement callableStatement(
            final List<RecordedCall> calls, final String statementSql) {
        final Map<Integer, Object> parameters = new LinkedHashMap<>();
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "setString", "setInt", "setLong", "setDate" -> {
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
                        default -> {
                            return defaultValue(method);
                        }
                    }
                };
        return (CallableStatement)
                Proxy.newProxyInstance(
                        JdbcDataExportExtractionAuditTest.class.getClassLoader(),
                        new Class<?>[] {CallableStatement.class},
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
