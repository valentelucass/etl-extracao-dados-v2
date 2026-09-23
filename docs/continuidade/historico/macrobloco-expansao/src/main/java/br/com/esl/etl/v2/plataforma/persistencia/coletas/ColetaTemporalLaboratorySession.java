package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageTargetKind;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** One physical connection; adapter commits are suppressed and close always rolls back. */
public final class ColetaTemporalLaboratorySession implements DataSource, AutoCloseable {
    private final Connection physical;
    private boolean closed;
    private int suppressedCommits;
    private int releasedConnections;

    private ColetaTemporalLaboratorySession(final Connection physical) {
        this.physical = physical;
    }

    public static ColetaTemporalLaboratorySession openFromEnvironment() throws SQLException {
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")) {
            throw new IllegalStateException("COL_LAB_OPT_IN_REQUIRED");
        }
        final var properties =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        System.getenv("V2_SHADOW_JDBC_URL"),
                        null);
        if (!properties.usesIntegratedSecurity()) {
            throw new IllegalArgumentException("COL_LAB_WINDOWS_REQUIRED");
        }
        final var connection = DriverManager.getConnection(properties.jdbcUrl());
        try {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                statement.execute("SET LOCK_TIMEOUT 1800; SET XACT_ABORT ON;");
                try (var row =
                        statement.executeQuery(
                                "SELECT DB_NAME(), CONVERT(nvarchar(32),CONNECTIONPROPERTY('auth_scheme'))")) {
                    if (!row.next()
                            || !"ETL_SISTEMA_V2_SHADOW".equals(row.getString(1))
                            || !("NTLM".equals(row.getString(2))
                                    || "KERBEROS".equals(row.getString(2)))) {
                        throw new SQLException("COL_LAB_TARGET_MISMATCH");
                    }
                }
            }
            return new ColetaTemporalLaboratorySession(connection);
        } catch (final SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (final SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("COL_LAB_SESSION_CLOSED");
        }
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "commit" -> {
                                    suppressedCommits++;
                                    return null;
                                }
                                case "close" -> {
                                    releasedConnections++;
                                    return null;
                                }
                                case "unwrap", "abort", "setCatalog", "setSchema" ->
                                        throw new SQLException("COL_LAB_CONNECTION_ESCAPE_DENIED");
                                case "isWrapperFor" -> {
                                    return false;
                                }
                                case "setAutoCommit" -> {
                                    if (Boolean.TRUE.equals(args[0])) {
                                        throw new SQLException("COL_LAB_COMMIT_DENIED");
                                    }
                                    return null;
                                }
                                default -> {
                                    try {
                                        return method.invoke(physical, args);
                                    } catch (final InvocationTargetException failure) {
                                        throw failure.getCause();
                                    }
                                }
                            }
                        });
    }

    public int suppressedCommits() {
        return suppressedCommits;
    }

    public int releasedConnections() {
        return releasedConnections;
    }

    public void rollback() throws SQLException {
        physical.rollback();
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try {
                physical.rollback();
            } finally {
                physical.close();
            }
        }
    }

    @Override
    public Connection getConnection(final String username, final String password)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("COL_LAB_CREDENTIALS_DENIED");
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(final PrintWriter writer) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(final Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isWrapperFor(final Class<?> type) {
        return false;
    }
}
