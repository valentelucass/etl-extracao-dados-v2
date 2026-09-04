package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTraversalVerification;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.JdbcDataExportExtractionAudit;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Exercita a auditoria JDBC somente quando o perfil e a flag explícita são fornecidos pelo
 * operador.
 *
 * <p>A classe nunca recebe URL de propriedade de sistema nem abre conexão quando uma das travas
 * está desabilitada. A única URL aceita vem de {@code V2_SHADOW_JDBC_URL} e passa pela validação do
 * alvo local isolado antes de {@link DriverManager#getConnection(String)}.
 */
class ShadowAuditLocalIntegrationIT {

    private static final String ENABLED_PROPERTY = "shadow.local.integration.enabled";
    private static final String PROFILE_ACTIVE_PROPERTY = "shadow.local.integration.profile.active";
    private static final String COUNT_EXECUTION_AUDIT_ROWS =
            "SELECT COUNT(*) FROM ctl.execution_audit";
    private static final String COUNT_PAGE_AUDIT_ROWS = "SELECT COUNT(*) FROM ctl.page_audit";

    @Test
    void streamsSyntheticPagesToJdbcAuditAndRollsEverythingBackOnOneSharedConnection()
            throws SQLException {
        requireExplicitLocalIntegrationGate();

        final ShadowStorageProperties properties = localPropertiesFromEnvironment();
        try (Connection physicalConnection = DriverManager.getConnection(properties.jdbcUrl())) {
            physicalConnection.setAutoCommit(false);
            final RollbackOnlySharedConnectionDataSource dataSource =
                    new RollbackOnlySharedConnectionDataSource(physicalConnection);
            final JdbcDataExportExtractionAudit audit =
                    new JdbcDataExportExtractionAudit(dataSource);

            try {
                final DataExportExtractionResult result =
                        new DataExportPageStreamer(syntheticGateway(), audit, Clock.systemUTC())
                                .stream(
                                        ContractTestSupport.executionContext(UUID.randomUUID()),
                                        initialRequest(),
                                        new DataExportExtractionLimits(2, 2, 2),
                                        ignored -> {});

                assertEquals(2, result.pagesFetched());
                assertEquals(2L, result.recordsDelivered());
                assertEquals(2, result.terminalPage());
                assertSanitizedMetadata(physicalConnection, result.executionId());
                assertEquals(4, dataSource.connectionRequests());
            } finally {
                physicalConnection.rollback();
            }

            assertNoPersistedAuditRows(physicalConnection);
        }
    }

    private static DataExportGateway syntheticGateway() {
        final List<DataExportPageResponse> pages =
                List.of(
                        new DataExportPageResponse(
                                List.of(
                                        JsonNodeFactory.instance
                                                .objectNode()
                                                .put("id", "synthetic-entity"),
                                        JsonNodeFactory.instance
                                                .objectNode()
                                                .put("id", "synthetic-entity"))),
                        new DataExportPageResponse(List.of()));
        final AtomicInteger fetchedPages = new AtomicInteger();
        return request -> {
            final int responseIndex = fetchedPages.getAndIncrement();
            assertEquals(responseIndex + 1, request.page());
            return pages.get(responseIndex);
        };
    }

    private static DataExportPageRequest initialRequest() {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)),
                Optional.empty(),
                1,
                2,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    private static ShadowStorageProperties localPropertiesFromEnvironment() {
        final String jdbcUrl = System.getenv("V2_SHADOW_JDBC_URL");
        Assumptions.assumeTrue(
                jdbcUrl != null && !jdbcUrl.isBlank(),
                "Defina V2_SHADOW_JDBC_URL somente para a integração local autorizada.");

        final ShadowStorageProperties properties =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL, jdbcUrl, null);

        assertTrue(properties.auditEnabled());
        assertEquals(ShadowStorageTargetKind.LOCAL_EPHEMERAL, properties.targetKind());
        assertTrue(
                properties.usesIntegratedSecurity(),
                "A integração local exige integratedSecurity=true na URL de ambiente.");
        return properties;
    }

    private static void requireExplicitLocalIntegrationGate() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY)),
                "Defina -Dshadow.local.integration.enabled=true para habilitar a integração local.");
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getProperty(PROFILE_ACTIVE_PROPERTY)),
                "A integração local exige o perfil shadow-local-integration.");
    }

    private static void assertSanitizedMetadata(final Connection connection, final UUID executionId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                                SELECT template_id, business_window_start, business_window_end,
                                       updated_at_window_start, updated_at_window_end, status,
                                       pages_fetched, records_delivered, terminal_page,
                                       traversal_verification
                                  FROM ctl.execution_audit
                                 WHERE execution_id = ?
                                """)) {
            statement.setString(1, executionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(6908, result.getInt("template_id"));
                assertEquals(
                        Date.valueOf(LocalDate.of(2026, 1, 1)),
                        result.getDate("business_window_start"));
                assertEquals(
                        Date.valueOf(LocalDate.of(2026, 1, 1)),
                        result.getDate("business_window_end"));
                assertNull(result.getTimestamp("updated_at_window_start"));
                assertNull(result.getTimestamp("updated_at_window_end"));
                assertEquals("COMPLETED", result.getString("status"));
                assertEquals(2, result.getInt("pages_fetched"));
                assertEquals(2L, result.getLong("records_delivered"));
                assertEquals(2, result.getInt("terminal_page"));
                assertEquals(
                        DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED.name(),
                        result.getString("traversal_verification"));
                assertFalse(result.next());
            }
        }

        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                                SELECT page_number, requested_per, record_count, distinct_entity_count,
                                       is_terminal, read_at
                                  FROM ctl.page_audit
                                 WHERE execution_id = ?
                                 ORDER BY page_number
                                """)) {
            statement.setString(1, executionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt("page_number"));
                assertEquals(2, result.getInt("requested_per"));
                assertEquals(2, result.getInt("record_count"));
                assertEquals(1, result.getInt("distinct_entity_count"));
                assertFalse(result.getBoolean("is_terminal"));
                assertTrue(result.getTimestamp("read_at") != null);

                assertTrue(result.next());
                assertEquals(2, result.getInt("page_number"));
                assertEquals(2, result.getInt("requested_per"));
                assertEquals(0, result.getInt("record_count"));
                assertEquals(0, result.getInt("distinct_entity_count"));
                assertTrue(result.getBoolean("is_terminal"));
                assertTrue(result.getTimestamp("read_at") != null);
                assertFalse(result.next());
            }
        }
    }

    private static void assertNoPersistedAuditRows(final Connection connection)
            throws SQLException {
        assertEquals(0, countRows(connection, COUNT_EXECUTION_AUDIT_ROWS));
        assertEquals(0, countRows(connection, COUNT_PAGE_AUDIT_ROWS));
    }

    private static int countRows(final Connection connection, final String statementSql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static final class RollbackOnlySharedConnectionDataSource implements DataSource {

        private final Connection connectionProxy;
        private int connectionRequests;

        private RollbackOnlySharedConnectionDataSource(final Connection sharedConnection) {
            this.connectionProxy = rollbackOnlyProxy(sharedConnection);
        }

        @Override
        public Connection getConnection() {
            connectionRequests++;
            return connectionProxy;
        }

        @Override
        public Connection getConnection(final String username, final String password)
                throws SQLException {
            throw new SQLException("A integração local não aceita credenciais por chamada.");
        }

        @Override
        public <T> T unwrap(final Class<T> interfaceType) throws SQLException {
            throw new SQLException("Não há unwrap para a conexão de integração local.");
        }

        @Override
        public boolean isWrapperFor(final Class<?> interfaceType) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {}

        @Override
        public void setLoginTimeout(final int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        private int connectionRequests() {
            return connectionRequests;
        }
    }

    private static Connection rollbackOnlyProxy(final Connection physicalConnection) {
        final InvocationHandler handler =
                (proxy, method, arguments) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    if ("commit".equals(method.getName())) {
                        throw new SQLException(
                                "A conexão de integração local é somente para rollback.");
                    }
                    if ("setAutoCommit".equals(method.getName())
                            && arguments != null
                            && Boolean.TRUE.equals(arguments[0])) {
                        throw new SQLException(
                                "A conexão de integração local não pode habilitar autocommit.");
                    }
                    return invokePhysicalConnection(physicalConnection, method, arguments);
                };
        return (Connection)
                Proxy.newProxyInstance(
                        ShadowAuditLocalIntegrationIT.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        handler);
    }

    private static Object invokePhysicalConnection(
            final Connection physicalConnection, final Method method, final Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(physicalConnection, arguments);
        } catch (final InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
