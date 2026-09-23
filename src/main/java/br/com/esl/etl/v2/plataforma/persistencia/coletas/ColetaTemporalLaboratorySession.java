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
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** One physical connection; adapter commits are suppressed and close always rolls back. */
public final class ColetaTemporalLaboratorySession implements DataSource, AutoCloseable {
    public static final int MAXIMUM_OPEN_STATEMENTS = 64;
    private final Connection physical;
    private boolean closed;
    private int suppressedCommits;
    private int releasedConnections;
    private long preparedStatements;
    private long createdStatements;
    private int maximumQuerySeconds;
    private long maximumJdbcCalls;
    private LaboratoryJdbcBudget statementBudget;
    private final Set<Statement> controlledStatements = ConcurrentHashMap.newKeySet();

    private ColetaTemporalLaboratorySession(final Connection physical) {
        this.physical = physical;
    }

    public static ColetaTemporalLaboratorySession openFromEnvironment() throws SQLException {
        return open(System.getenv("V2_SHADOW_JDBC_URL"));
    }

    /** The typed package configuration uses this same target and opt-in boundary. */
    public static ColetaTemporalLaboratorySession open(final String jdbcUrl) throws SQLException {
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")) {
            throw new IllegalStateException("COL_LAB_OPT_IN_REQUIRED");
        }
        final var properties =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL, jdbcUrl, null);
        if (!properties.usesIntegratedSecurity()) {
            throw new IllegalArgumentException("COL_LAB_WINDOWS_REQUIRED");
        }
        final String boundedUrl =
                properties.jdbcUrl().replaceAll("(?i);loginTimeout=[^;]*", "") + ";loginTimeout=5";
        final var connection = DriverManager.getConnection(boundedUrl);
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
                            final boolean prepared =
                                    method.getName().equals("prepareStatement")
                                            || method.getName().equals("prepareCall");
                            final boolean created = method.getName().equals("createStatement");
                            if (prepared) {
                                preparedStatements++;
                            } else if (method.getName().equals("createStatement")) {
                                createdStatements++;
                            }
                            if (maximumJdbcCalls > 0
                                    && (prepared || created)
                                    && preparedStatements + createdStatements > maximumJdbcCalls) {
                                throw new SQLException("COL_LAB_JDBC_CALL_LIMIT");
                            }
                            if (statementBudget != null && (prepared || created)) {
                                statementBudget.reserve();
                            }
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
                                        final Object value = method.invoke(physical, args);
                                        if (maximumQuerySeconds > 0
                                                && value instanceof Statement statement) {
                                            return control(statement);
                                        }
                                        return value;
                                    } catch (final InvocationTargetException failure) {
                                        throw failure.getCause();
                                    }
                                }
                            }
                        });
    }

    /**
     * Optional package policy; legacy laboratory callers retain their existing statement behavior.
     */
    public void controlStatements(final int querySeconds) {
        if (querySeconds < 1 || querySeconds > 60 || maximumQuerySeconds != 0) {
            throw new IllegalArgumentException("COL_LAB_STATEMENT_POLICY");
        }
        maximumQuerySeconds = querySeconds;
    }

    public void controlStatements(final int querySeconds, final long maximumCalls) {
        if (maximumCalls < 1 || maximumCalls > 100000) {
            throw new IllegalArgumentException("COL_LAB_JDBC_CALL_POLICY");
        }
        controlStatements(querySeconds);
        maximumJdbcCalls = maximumCalls;
        statementBudget = new LaboratoryJdbcBudget(maximumCalls);
    }

    public void controlStatements(final int querySeconds, final LaboratoryJdbcBudget budget) {
        java.util.Objects.requireNonNull(budget);
        controlStatements(querySeconds);
        statementBudget = budget;
    }

    public LaboratoryJdbcBudget statementBudget() {
        if (statementBudget == null) {
            throw new IllegalStateException("COL_LAB_JDBC_BUDGET_REQUIRED");
        }
        return statementBudget;
    }

    /**
     * Called by the owning campaign's deadline/cancellation watcher, including during blocking
     * JDBC.
     */
    public int cancelActiveStatements() throws SQLException {
        pruneClosedStatements();
        int cancelled = 0;
        SQLException failure = null;
        for (final var statement : controlledStatements) {
            try {
                if (!statement.isClosed()) {
                    statement.cancel();
                    cancelled++;
                }
            } catch (final SQLException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return cancelled;
    }

    public int openControlledStatements() throws SQLException {
        pruneClosedStatements();
        return controlledStatements.size();
    }

    private void pruneClosedStatements() throws SQLException {
        for (final var statement : controlledStatements) {
            if (statement.isClosed()) {
                controlledStatements.remove(statement);
            }
        }
    }

    private synchronized Statement control(final Statement statement) throws SQLException {
        try {
            pruneClosedStatements();
            if (controlledStatements.size() >= MAXIMUM_OPEN_STATEMENTS) {
                throw new SQLException("COL_LAB_OPEN_STATEMENT_LIMIT");
            }
            statement.setQueryTimeout(maximumQuerySeconds);
            controlledStatements.add(statement);
        } catch (final SQLException | RuntimeException | Error failure) {
            try {
                statement.close();
            } catch (final SQLException | RuntimeException | Error closeFailure) {
                if (closeFailure != failure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
        // Existing TVP adapters require the Microsoft concrete statement. Retain its identity;
        // only open handles are tracked, and cancellation reaches the driver's real statement.
        return statement;
    }

    public int suppressedCommits() {
        return suppressedCommits;
    }

    public int releasedConnections() {
        return releasedConnections;
    }

    public long preparedStatements() {
        return preparedStatements;
    }

    public long createdStatements() {
        return createdStatements;
    }

    public void rollback() throws SQLException {
        physical.rollback();
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try {
                try (physical) {
                    physical.rollback();
                }
            } finally {
                controlledStatements.clear();
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
        return 5;
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
