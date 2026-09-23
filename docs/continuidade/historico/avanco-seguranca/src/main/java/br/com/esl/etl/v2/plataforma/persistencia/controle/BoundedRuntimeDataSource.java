package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Lazy Windows-only workload connections, distinct from the authority connection. */
public final class BoundedRuntimeDataSource implements DataSource {
    private final String url;

    @FunctionalInterface
    interface Connections {
        Connection open(String target) throws SQLException;
    }

    private final Connections connections;
    private final Semaphore permits = new Semaphore(2);

    public BoundedRuntimeDataSource(final ShadowStorageProperties target) {
        this(
                target,
                ignored ->
                        br.com.esl.etl.v2.plataforma.autorizacao.AdministeredSqlConnection.open());
    }

    BoundedRuntimeDataSource(final ShadowStorageProperties target, final Connections connections) {
        validateTarget(target);
        url =
                target.jdbcUrl().replaceAll("(?i);(?:loginTimeout|socketTimeout)=[^;]*", "")
                        + ";loginTimeout=10;socketTimeout=20000";
        this.connections = java.util.Objects.requireNonNull(connections);
    }

    /** Pure validation, also used before acquiring an authorization receipt. */
    public static void validateTarget(final ShadowStorageProperties target) {
        java.util.Objects.requireNonNull(target);
        if (!target.auditEnabled() || !target.usesIntegratedSecurity()) {
            throw new IllegalArgumentException("WINDOWS_WORKLOAD_TARGET_REQUIRED");
        }
        if (!target.jdbcUrl().matches("(?i)jdbc:sqlserver://(?:localhost|127\\.0\\.0\\.1);.*")
                || !target.jdbcUrl().matches("(?is).*;encrypt=true(?:;.*)?")
                || !target.jdbcUrl().matches("(?is).*;trustServerCertificate=false(?:;.*)?")) {
            throw new IllegalArgumentException("VERIFIED_LOCAL_WORKLOAD_TLS_REQUIRED");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!permits.tryAcquire()) {
            throw new SQLException("RUNTIME_CONNECTION_LIMIT");
        }
        Connection opened = null;
        try {
            opened = connections.open(url);
            try (var check = opened.createStatement()) {
                check.setQueryTimeout(10);
                try (var row =
                        check.executeQuery(
                                "SELECT CASE WHEN DB_NAME()=N'ETL_SISTEMA_V2_SHADOW'"
                                        + " AND CONNECTIONPROPERTY('auth_scheme') IN(N'NTLM',N'KERBEROS') THEN 1 ELSE 0 END")) {
                    if (!row.next() || row.getInt(1) != 1 || row.next()) {
                        throw new SQLException("WORKLOAD_TARGET_REFUSED");
                    }
                }
            }
        } catch (final SQLException failure) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (final SQLException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            permits.release();
            throw failure;
        }
        final Connection connection = opened;
        final boolean[] closed = {false};
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("close")) {
                                if (!closed[0]) {
                                    try {
                                        connection.close();
                                    } finally {
                                        closed[0] = true;
                                        permits.release();
                                    }
                                }
                                return null;
                            }
                            try {
                                final Object result = method.invoke(connection, args);
                                if (result instanceof Statement statement) {
                                    statement.setQueryTimeout(20);
                                }
                                return result;
                            } catch (final InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    @Override
    public Connection getConnection(final String user, final String password) throws SQLException {
        throw new SQLException("WINDOWS_ONLY");
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(final PrintWriter writer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoginTimeout(final int seconds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLoginTimeout() {
        return 10;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("runtime-jdbc");
    }

    @Override
    public <T> T unwrap(final Class<T> type) throws SQLException {
        throw new SQLException("UNWRAP_REFUSED");
    }

    @Override
    public boolean isWrapperFor(final Class<?> type) {
        return false;
    }

    @Override
    public String toString() {
        return "BoundedRuntimeDataSource[redacted]";
    }
}
