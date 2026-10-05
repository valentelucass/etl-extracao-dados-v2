package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** One physical connection; adapter commits are suppressed and close always rolls back. */
public final class ColetaTemporalLaboratorySession implements DataSource, AutoCloseable {
    public static final int MAXIMUM_OPEN_STATEMENTS = 64;
    public static final int PILOT_QUERY_SECONDS = 20;
    private final Connection physical;
    private final boolean sampleSaOnly;
    private final boolean sharedSampleOnly;
    private final long sharedAdmissionStartedNanos;
    private boolean closed;
    private int suppressedCommits;
    private int releasedConnections;
    private long preparedStatements;
    private long createdStatements;
    private int maximumQuerySeconds;
    private long maximumJdbcCalls;
    private LaboratoryJdbcBudget statementBudget;
    private final Set<Statement> controlledStatements = ConcurrentHashMap.newKeySet();

    @FunctionalInterface
    interface Connections {
        Connection open(String url) throws SQLException;
    }

    @FunctionalInterface
    interface SqlConnections {
        Connection open(String url, Properties credentials) throws SQLException;
    }

    private ColetaTemporalLaboratorySession(final Connection physical) {
        this(physical, false);
    }

    private ColetaTemporalLaboratorySession(final Connection physical, final boolean sampleSaOnly) {
        this(physical, sampleSaOnly, false);
    }

    private ColetaTemporalLaboratorySession(
            final Connection physical, final boolean sampleSaOnly, final boolean sharedSampleOnly) {
        this(physical, sampleSaOnly, sharedSampleOnly, System.nanoTime());
    }

    private ColetaTemporalLaboratorySession(
            final Connection physical,
            final boolean sampleSaOnly,
            final boolean sharedSampleOnly,
            final long sharedAdmissionStartedNanos) {
        this.physical = physical;
        this.sampleSaOnly = sampleSaOnly;
        this.sharedSampleOnly = sharedSampleOnly;
        this.sharedAdmissionStartedNanos = sharedAdmissionStartedNanos;
    }

    /** Pure validation; never reads a secret or opens a connection. */
    public static void validateSampleSaTarget(final ShadowStorageProperties target) {
        if (target == null
                || !target.auditEnabled()
                || !target.usesSampleSaAuthentication()
                || target.usesSampleSqlPinAuthentication()
                || target.targetKind() != ShadowStorageTargetKind.LOCAL_EPHEMERAL) {
            throw new IllegalArgumentException("COL_SAMPLE_SA_EXPLICIT_TARGET_REQUIRED");
        }
        ShadowStorageProperties.enabledSampleSa(target.jdbcUrl());
    }

    static ColetaTemporalLaboratorySession openSampleSa(
            final ShadowStorageProperties target, final SecretProvider secrets)
            throws SQLException {
        return openSampleSa(target, secrets, DriverManager::getConnection);
    }

    static ColetaTemporalLaboratorySession openSampleSa(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SqlConnections connections)
            throws SQLException {
        return openSampleSa(target, secrets, connections, false);
    }

    static ColetaTemporalLaboratorySession openSharedSampleSa(
            final ShadowStorageProperties target, final SecretProvider secrets)
            throws SQLException {
        return openSharedSampleSa(target, secrets, DriverManager::getConnection);
    }

    static ColetaTemporalLaboratorySession openSharedSampleSa(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SqlConnections connections)
            throws SQLException {
        if (!Boolean.getBoolean("coletas6908.sample.shared.enabled")) {
            throw new IllegalStateException("COL_SAMPLE_SHARED_OPT_IN_REQUIRED");
        }
        return openSampleSa(target, secrets, connections, true);
    }

    static ColetaTemporalLaboratorySession openPinnedSharedSampleSa(
            final ShadowStorageProperties target, final SecretProvider secrets)
            throws SQLException {
        return openSampleSa(
                target,
                secrets,
                (url, credentials) ->
                        PinnedSampleSaJdbcConnector.openCredentials(target, credentials),
                true,
                true);
    }

    static ColetaTemporalLaboratorySession openPinnedSharedSampleSa(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SqlConnections connections)
            throws SQLException {
        return openSampleSa(target, secrets, connections, true, true);
    }

    private static ColetaTemporalLaboratorySession openSampleSa(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SqlConnections connections,
            final boolean shared)
            throws SQLException {
        return openSampleSa(target, secrets, connections, shared, false);
    }

    private static ColetaTemporalLaboratorySession openSampleSa(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SqlConnections connections,
            final boolean shared,
            final boolean pinned)
            throws SQLException {
        if (pinned) {
            br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleTlsScope.validate(target);
        } else {
            validateSampleSaTarget(target);
        }
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")
                || !Boolean.getBoolean("coletas6908.sample.enabled")
                || !Boolean.getBoolean("coletas6908.sample.sa.enabled")) {
            throw new IllegalStateException("COL_SAMPLE_SA_OPT_IN_REQUIRED");
        }
        Objects.requireNonNull(connections);
        final long admissionStarted = System.nanoTime();
        final String password;
        try {
            password = Objects.requireNonNull(secrets).require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
            if (password == null || password.isBlank() || password.length() > 4096) {
                throw new IllegalStateException("COL_SAMPLE_SA_SECRET_INVALID");
            }
        } catch (final RuntimeException | Error failure) {
            throw new IllegalStateException("COL_SAMPLE_SA_SECRET_UNAVAILABLE");
        }
        final Properties credentials = new Properties();
        credentials.setProperty("user", "sa");
        credentials.setProperty("password", password);
        credentials.setProperty("authentication", "SqlPassword");
        credentials.setProperty("connectRetryCount", "0");
        Connection connection = null;
        try {
            connection =
                    Objects.requireNonNull(
                            connections.open(
                                    shared
                                            ? sharedSampleJdbcUrl(target.jdbcUrl())
                                            : boundedJdbcUrl(target.jdbcUrl()),
                                    credentials));
            // Identity SELECT runs before any session setting or explicit transaction.
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (var row =
                        statement.executeQuery(
                                "SELECT CASE WHEN DB_NAME()=N'ETL_SISTEMA_V2_SHADOW'"
                                        + " AND ORIGINAL_LOGIN()=N'sa' AND SUSER_SNAME()=N'sa'"
                                        + " AND CONNECTIONPROPERTY('auth_scheme')=N'SQL'"
                                        + " AND CONNECTIONPROPERTY('net_transport')=N'TCP'"
                                        + " AND @@TRANCOUNT=0 THEN 1 ELSE 0 END,"
                                        + " CONVERT(nvarchar(128),CONNECTIONPROPERTY('local_net_address')),"
                                        + " CONVERT(nvarchar(128),CONNECTIONPROPERTY('client_net_address'))"
                                        + (shared
                                                ? ", CASE WHEN EXISTS (SELECT 1 FROM sys.dm_exec_connections"
                                                        + " WHERE session_id=@@SPID AND encrypt_option=N'TRUE')"
                                                        + " THEN 1 ELSE 0 END"
                                                : ""))) {
                    if (!row.next()
                            || row.getInt(1) != 1
                            || !literalLoopback(row.getString(2))
                            || !literalLoopback(row.getString(3))
                            || (shared && row.getInt(4) != 1)
                            || row.next()) {
                        throw new SQLException("COL_SAMPLE_SA_IDENTITY_REFUSED");
                    }
                }
                connection.setAutoCommit(false);
                statement.execute(
                        shared
                                ? "SET LOCK_TIMEOUT 0; SET XACT_ABORT ON;"
                                : "SET LOCK_TIMEOUT 1800; SET XACT_ABORT ON;");
            }
            return new ColetaTemporalLaboratorySession(connection, true, shared, admissionStarted);
        } catch (final SQLException | RuntimeException | Error failure) {
            // Driver/provider failures may include credential material. Keep only typed metadata.
            if (pinned
                    && connection == null
                    && failure instanceof PinnedSampleSaJdbcConnector.ConnectionFailure sanitized) {
                throw sanitized;
            }
            final SQLException safe;
            if (failure instanceof SQLException sql) {
                final String state = sql.getSQLState();
                safe =
                        new SQLException(
                                "COL_SAMPLE_SA_CONNECTION_OR_IDENTITY_FAILED",
                                state != null && state.matches("[A-Z0-9]{5}") ? state : null,
                                sql.getErrorCode());
            } else {
                safe = new SQLException("COL_SAMPLE_SA_CONNECTION_OR_IDENTITY_FAILED");
            }
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    safe.addSuppressed(new SQLException("COL_SAMPLE_SA_CLEANUP_ROLLBACK_FAILED"));
                }
                try {
                    connection.close();
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    safe.addSuppressed(new SQLException("COL_SAMPLE_SA_CLEANUP_CLOSE_FAILED"));
                }
            }
            throw safe;
        } finally {
            credentials.clear();
        }
    }

    private static boolean literalLoopback(final String value) {
        if (value == null || !value.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        // Literal allowlist only: no DNS/resolver calls, mapped IPv4 loopback is explicit.
        return Set.of(
                        "127.0.0.1",
                        "::1",
                        "0:0:0:0:0:0:0:1",
                        "0000:0000:0000:0000:0000:0000:0000:0001",
                        "::ffff:127.0.0.1",
                        "::ffff:7f00:1",
                        "0:0:0:0:0:ffff:7f00:1",
                        "0:0:0:0:0:ffff:127.0.0.1")
                .contains(value.toLowerCase(Locale.ROOT));
    }

    boolean sampleSaOnly() {
        return sampleSaOnly;
    }

    boolean sharedSampleOnly() {
        return sharedSampleOnly;
    }

    long sharedAdmissionStartedNanos() {
        return sharedAdmissionStartedNanos;
    }

    public static ColetaTemporalLaboratorySession openFromEnvironment() throws SQLException {
        return open(System.getenv("V2_SHADOW_JDBC_URL"));
    }

    static ColetaTemporalLaboratorySession openPilotFromEnvironment() throws SQLException {
        return openPilot(System.getenv("V2_SHADOW_JDBC_URL"));
    }

    static ColetaTemporalLaboratorySession openPilot(final String jdbcUrl) throws SQLException {
        return openPilot(jdbcUrl, DriverManager::getConnection);
    }

    static ColetaTemporalLaboratorySession openPilot(
            final String jdbcUrl, final Connections connections) throws SQLException {
        return openValidated(jdbcUrl, true, connections);
    }

    /** The typed package configuration uses this same target and opt-in boundary. */
    public static ColetaTemporalLaboratorySession open(final String jdbcUrl) throws SQLException {
        return open(jdbcUrl, DriverManager::getConnection);
    }

    static ColetaTemporalLaboratorySession open(final String jdbcUrl, final Connections connections)
            throws SQLException {
        return openValidated(jdbcUrl, false, connections);
    }

    private static ColetaTemporalLaboratorySession openValidated(
            final String jdbcUrl, final boolean pilot, final Connections connections)
            throws SQLException {
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
                pilot ? boundedJdbcUrl(properties.jdbcUrl()) : legacyJdbcUrl(properties.jdbcUrl());
        final var connection = java.util.Objects.requireNonNull(connections).open(boundedUrl);
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

    static String legacyJdbcUrl(final String validatedJdbcUrl) {
        return validatedJdbcUrl.replaceAll("(?i);loginTimeout=[^;]*", "") + ";loginTimeout=5";
    }

    static String boundedJdbcUrl(final String validatedJdbcUrl) {
        return validatedJdbcUrl.replaceAll("(?i);\\s*(?:loginTimeout|socketTimeout)\\s*=[^;]*", "")
                + ";loginTimeout=5;queryTimeout=20;cancelQueryTimeout=5;socketTimeout=30000";
    }

    private static String sharedSampleJdbcUrl(final String validatedJdbcUrl) {
        return boundedJdbcUrl(validatedJdbcUrl)
                .replace(";queryTimeout=20;", ";queryTimeout=10;")
                .replace(";socketTimeout=30000", ";socketTimeout=15000");
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
