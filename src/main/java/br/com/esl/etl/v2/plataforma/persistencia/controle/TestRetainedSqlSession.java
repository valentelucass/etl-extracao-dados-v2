package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.TestRetainedSqlTarget;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** A single owned, bounded transaction; only the owner may commit a verified partial capture. */
public final class TestRetainedSqlSession implements DataSource, AutoCloseable {
    public static final class Failure extends SQLException {
        private static final long serialVersionUID = 1L;
        private final String code;

        private Failure(final String code, final String state, final int vendorCode) {
            super("TEST_RETAINED_" + code, state, vendorCode);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Limits(
            int maxSqlExecutions, int maxBatchRows, int queryTimeoutSeconds, int deadlineSeconds) {
        public Limits {
            if (maxSqlExecutions < 24
                    || maxSqlExecutions > 1200
                    || maxBatchRows < 1
                    || maxBatchRows > 1000
                    || queryTimeoutSeconds < 1
                    || queryTimeoutSeconds > 10
                    || deadlineSeconds < 1
                    || deadlineSeconds > 60) {
                throw new IllegalArgumentException("TEST_RETAINED_LIMITS");
            }
        }
    }

    public record Expected(
            UUID executionId, long physicalRows, long mappedRows, long distinctRoots) {
        public Expected {
            Objects.requireNonNull(executionId);
            if (physicalRows < 0
                    || physicalRows > 1000
                    || mappedRows != physicalRows
                    || distinctRoots < 0
                    || distinctRoots > 1
                    || (physicalRows == 0) != (distinctRoots == 0)) {
                throw new IllegalArgumentException("TEST_RETAINED_EXPECTED_PARTIAL");
            }
        }
    }

    public record Receipt(
            String outcome,
            boolean committed,
            long physicalRows,
            long mappedRows,
            long sqlExecutions,
            long suppressedCommits,
            boolean postCommitReadback) {}

    private final Connection physical;
    private final Limits limits;
    private final UUID executionId;
    private final LongSupplier clock;
    private final long started;
    private final long ownerThread = Thread.currentThread().getId();
    private final java.util.Set<Statement> openStatements =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private long executions;
    private long batchRows;
    private long suppressedCommits;
    private long transaction;
    private long sessionId;
    private boolean poisoned;
    private boolean committed;
    private boolean commitUncertain;
    private boolean closed;
    private boolean closeObserved;

    private TestRetainedSqlSession(
            final Connection physical,
            final Limits limits,
            final UUID executionId,
            final LongSupplier clock,
            final long started) {
        this.physical = Objects.requireNonNull(physical);
        this.limits = Objects.requireNonNull(limits);
        this.executionId = Objects.requireNonNull(executionId);
        this.clock = Objects.requireNonNull(clock);
        this.started = started;
    }

    public static TestRetainedSqlSession open(
            final TestRetainedSqlTarget target,
            final SecretProvider secrets,
            final Limits limits,
            final UUID executionId,
            final String sourceInstance,
            final String tenantScope)
            throws SQLException {
        Objects.requireNonNull(limits);
        Objects.requireNonNull(executionId);
        if (!TestRetainedSqlContract.SOURCE.equals(sourceInstance)
                || !TestRetainedSqlContract.TENANT.equals(tenantScope)) {
            throw new IllegalArgumentException("TEST_RETAINED_EXCLUSIVE_NAMESPACE_REQUIRED");
        }
        final long started = System.nanoTime();
        return admitted(
                TestRetainedSaJdbcConnector.open(target, secrets),
                limits,
                executionId,
                System::nanoTime,
                started);
    }

    static TestRetainedSqlSession admitted(
            final Connection connection,
            final Limits limits,
            final UUID executionId,
            final LongSupplier clock,
            final long started)
            throws SQLException {
        final var result =
                new TestRetainedSqlSession(connection, limits, executionId, clock, started);
        try {
            result.initialize();
            return result;
        } catch (final SQLException | RuntimeException | Error failure) {
            final SQLException sanitized =
                    failure instanceof Failure known
                            ? known
                            : failure instanceof SQLException sql
                                    ? safe("ADMISSION", sql)
                                    : refused("ADMISSION");
            try {
                result.close();
            } catch (final SQLException cleanup) {
                sanitized.addSuppressed(cleanup);
            }
            throw sanitized;
        }
    }

    private void initialize() throws SQLException {
        if (!physical.getAutoCommit()) {
            throw refused("AUTOCOMMIT_REQUIRED");
        }
        equal("IDENTITY", readLimitedCounters(1, TestRetainedSqlContract.IDENTITY), 1);
        equal("CLEAN_PRE", readLimitedCounters(3, TestRetainedSqlContract.CLEAN), 0, 0, 0);
        physical.setAutoCommit(false);
        reserve(1);
        try (var statement = physical.createStatement()) {
            timeout(statement);
            statement.execute("SET LOCK_TIMEOUT 0; SET XACT_ABORT ON;");
        }
        for (final String table : TestRetainedSqlContract.WRITE_TABLES) {
            reserve(1);
            try (var statement = physical.createStatement()) {
                timeout(statement);
                try (var row =
                        statement.executeQuery(
                                "SELECT TOP (1) 1 FROM " + table + " WITH (TABLOCKX,HOLDLOCK)")) {
                    if (row.next() && row.next()) {
                        throw refused("LOCK_RESULT");
                    }
                }
            }
        }
        checkpoint();
        equal(
                "FRESH_SCOPE",
                readLimitedCounters(
                        3,
                        TestRetainedSqlContract.FRESH,
                        executionId.toString(),
                        TestRetainedSqlContract.SOURCE,
                        TestRetainedSqlContract.SOURCE,
                        TestRetainedSqlContract.TENANT),
                0,
                0,
                0);
    }

    public void checkpoint() throws SQLException {
        active();
        final long[] row = readLimitedCounters(6, TestRetainedSqlContract.checkpoint());
        if (row.length != 6
                || row[0] != 1
                || row[1] != 1
                || row[2] < 1
                || row[3] < 1
                || row[4] != TestRetainedSqlContract.WRITE_TABLES.length
                || row[5] != 0
                || (transaction != 0 && transaction != row[3])
                || (sessionId != 0 && sessionId != row[2])) {
            poisoned = true;
            throw refused("TRANSACTION_OR_WRITER_PROTECTION_LOST");
        }
        transaction = row[3];
        sessionId = row[2];
    }

    public Receipt finishPartial(final Expected expected) throws SQLException {
        return finishPartial(expected, () -> {});
    }

    /**
     * The caller's current admission is checked before commit and after resource-closed readback.
     */
    public Receipt finishPartial(final Expected expected, final Runnable currentAdmission)
            throws SQLException {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(currentAdmission);
        if (!executionId.equals(expected.executionId())
                || expected.mappedRows() > limits.maxBatchRows()) {
            throw refused("EXECUTION_BINDING");
        }
        active();
        assertCurrentAdmission(currentAdmission);
        checkpoint();
        if (!openStatements.isEmpty()) {
            throw refused("OPEN_STATEMENTS_AT_COMMIT");
        }
        verifyReadback(expected);
        checkpoint();
        if (limits.maxSqlExecutions() - executions < 3) {
            throw refused("COMMIT_POST_BUDGET");
        }
        assertCurrentAdmission(currentAdmission);
        reserve(1);
        commitUncertain = true;
        try {
            physical.commit();
        } catch (final SQLException | RuntimeException | Error failure) {
            poisoned = true;
            throw cleanupFailure("COMMIT_UNCERTAIN", failure);
        }
        committed = true;
        commitUncertain = false;
        try {
            physical.setAutoCommit(true);
        } catch (final SQLException failure) {
            throw safe("POST_COMMIT_STATE", failure);
        }
        equal("CLEAN_POST", readLimitedCounters(3, TestRetainedSqlContract.CLEAN), 0, 0, 0);
        verifyReadback(expected);
        // A refusal after known commit must not turn that commit into uncertainty or compensation.
        assertCurrentAdmission(currentAdmission);
        return new Receipt(
                "CAPTURE_RETAINED_PARTIAL",
                true,
                expected.physicalRows(),
                expected.mappedRows(),
                executions,
                suppressedCommits,
                true);
    }

    private void assertCurrentAdmission(final Runnable currentAdmission) throws SQLException {
        deadline();
        try {
            currentAdmission.run();
        } catch (final RuntimeException | Error failure) {
            poisoned = true;
            throw refused("ADMISSION_REVOKED");
        }
        deadline();
    }

    private void verifyReadback(final Expected expected) throws SQLException {
        final long[] actual =
                readLimitedCounters(
                        10,
                        TestRetainedSqlContract.READBACK,
                        executionId.toString(),
                        TestRetainedSqlContract.SOURCE,
                        TestRetainedSqlContract.TENANT,
                        expected.physicalRows(),
                        expected.mappedRows(),
                        expected.distinctRoots());
        final long[] wanted = {
            1, expected.mappedRows(), expected.mappedRows(), 1, 1, 1, 0, 1, 1, 0
        };
        final String[] facets = {
            "OCCURRENCE",
            "GENERIC_ROWS",
            "TYPED_ROWS",
            "PAGE_CONTRACT",
            "PAGE_COUNT",
            "CAPTURE_COUNTS",
            "FORBIDDEN_PUBLICATION",
            "PARTIAL_REASON",
            "SOURCE_BINDING",
            "QUARANTINE"
        };
        if (actual.length != wanted.length) {
            throw refused("READBACK_COLUMNS");
        }
        for (int i = 0; i < actual.length; i++) {
            equal(facets[i], new long[] {actual[i]}, wanted[i]);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        active();
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            return switch (method.getName()) {
                                case "commit" -> {
                                    active();
                                    suppressedCommits++;
                                    yield null;
                                }
                                case "close" -> null;
                                case "getAutoCommit" -> false;
                                case "isClosed" -> closed;
                                case "isWrapperFor" -> false;
                                case "toString" -> "TestRetainedBorrowedConnection";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                case "setAutoCommit" -> {
                                    active();
                                    if (!Boolean.FALSE.equals(args[0])) {
                                        throw refused("ADAPTER_COMMIT");
                                    }
                                    yield null;
                                }
                                case "rollback" -> {
                                    active();
                                    poisoned = true;
                                    physical.rollback();
                                    yield null;
                                }
                                case "prepareCall" -> {
                                    active();
                                    if (args.length != 1) {
                                        throw refused("CALL_SIGNATURE");
                                    }
                                    yield call((String) args[0], (Connection) proxy);
                                }
                                default -> throw refused("CONNECTION_ESCAPE");
                            };
                        });
    }

    private CallableStatement call(final String sql, final Connection borrowed)
            throws SQLException {
        final var match =
                java.util.regex.Pattern.compile("\\{call ([a-z_.]+)\\(([^)]*)\\)\\}")
                        .matcher(sql.replace(" ", "").replace("{call", "{call "));
        if (!match.matches() || !TestRetainedSqlContract.CALLS.containsKey(match.group(1))) {
            throw refused("SQL_CALL_ALLOWLIST");
        }
        final String routine = match.group(1);
        final int parameters = TestRetainedSqlContract.CALLS.get(routine);
        if (!match.group(2)
                .equals(String.join(",", java.util.Collections.nCopies(parameters, "?")))) {
            throw refused("CALL_PARAMETERS");
        }
        if (openStatements.size() >= 16) {
            throw refused("OPEN_STATEMENT_LIMIT");
        }
        final var statement = physical.prepareCall(sql);
        try {
            timeout(statement);
        } catch (final SQLException failure) {
            statement.close();
            throw failure;
        }
        openStatements.add(statement);
        final Map<Integer, Object> bound = new HashMap<>();
        final int[] pending = {0};
        return (CallableStatement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {CallableStatement.class},
                        (proxy, method, args) -> {
                            final String name = method.getName();
                            if (name.equals("close")) {
                                try {
                                    statement.close();
                                } catch (final SQLException failure) {
                                    poisoned = true;
                                    throw safe("STATEMENT_CLOSE", failure);
                                } finally {
                                    openStatements.remove(statement);
                                }
                                return null;
                            }
                            if (name.equals("getConnection")) {
                                return borrowed;
                            }
                            if (name.equals("unwrap")) {
                                throw refused("STATEMENT_ESCAPE");
                            }
                            if (name.equals("isWrapperFor")) {
                                return false;
                            }
                            if (name.equals("setQueryTimeout")) {
                                timeout(statement);
                                return null;
                            }
                            if (name.startsWith("execute")) {
                                active();
                                if (args != null && args.length > 0) {
                                    throw refused("SQL_ARGUMENT_ESCAPE");
                                }
                                checkBindings(routine, bound);
                                checkpoint();
                                final boolean batch =
                                        name.equals("executeBatch")
                                                || name.equals("executeLargeBatch");
                                if (batch && (pending[0] < 1 || pending[0] > 100)) {
                                    throw refused("BATCH_BOUND");
                                }
                                reserve(batch ? pending[0] : 1);
                                try {
                                    final Object value = invoke(statement, method, args);
                                    pending[0] = 0;
                                    return value instanceof ResultSet rows
                                            ? rows(rows, (Statement) proxy)
                                            : value;
                                } catch (final SQLException failure) {
                                    poisoned = true;
                                    throw failure;
                                }
                            }
                            if (name.equals("addBatch")) {
                                active();
                                if (args != null && args.length > 0) {
                                    throw refused("BATCH_SQL_ESCAPE");
                                }
                                checkBindings(routine, bound);
                                if (!routine.equals("stg.usp_stage_coleta_record")
                                        || ++pending[0] > 100
                                        || ++batchRows > limits.maxBatchRows()) {
                                    throw refused("BATCH_BOUND");
                                }
                            } else if (name.equals("clearBatch")) {
                                pending[0] = 0;
                            } else if (name.equals("clearParameters")) {
                                bound.clear();
                            } else if (name.startsWith("set")) {
                                if (!java.util.Set.of(
                                                        "setString",
                                                        "setInt",
                                                        "setLong",
                                                        "setBoolean",
                                                        "setTimestamp",
                                                        "setNull",
                                                        "setBigDecimal",
                                                        "setBytes",
                                                        "setObject")
                                                .contains(name)
                                        || args == null
                                        || !(args[0] instanceof Integer index)
                                        || index < 1
                                        || index > parameters) {
                                    throw refused("PARAMETER_SETTER");
                                }
                                bound.put(index, name.equals("setNull") ? null : args[1]);
                            } else if (!name.startsWith("get")
                                    && !name.startsWith("is")
                                    && !name.equals("registerOutParameter")
                                    && !name.equals("wasNull")) {
                                throw refused("STATEMENT_METHOD");
                            }
                            final Object value = invoke(statement, method, args);
                            return value instanceof ResultSet result
                                    ? rows(result, (Statement) proxy)
                                    : value;
                        });
    }

    private void checkBindings(final String routine, final Map<Integer, Object> bound)
            throws SQLException {
        final String first = String.valueOf(bound.get(1));
        if (routine.endsWith("register_source")) {
            if (!TestRetainedSqlContract.SOURCE.equals(first)
                    || !"DATA_EXPORT".equals(bound.get(2))) {
                throw refused("SOURCE_BINDING");
            }
        } else if (!routine.endsWith("start_cycle") && !executionId.toString().equals(first)) {
            throw refused("EXECUTION_BINDING");
        }
        if (routine.endsWith("start_execution")
                && (!TestRetainedSqlContract.SOURCE.equals(bound.get(4))
                        || !TestRetainedSqlContract.TENANT.equals(bound.get(5))
                        || !"coletas".equals(bound.get(6)))) {
            throw refused("PARTITION_BINDING");
        }
        if (routine.endsWith("transition_execution")
                && !java.util.Set.of("EXTRACTING", "CANCELLED")
                        .contains(String.valueOf(bound.get(3)))) {
            throw refused("TRANSITION_BOUNDARY");
        }
        if (routine.endsWith("record_counts") && !"TEST_CAPTURE".equals(bound.get(2))) {
            throw refused("COUNT_PHASE");
        }
        if (routine.endsWith("record_page")
                && (!Integer.valueOf(1).equals(bound.get(2))
                        || !Integer.valueOf(1).equals(bound.get(3))
                        || !Integer.valueOf(1).equals(bound.get(4))
                        || !Boolean.FALSE.equals(bound.get(8))
                        || !"NONE".equals(bound.get(10)))) {
            throw refused("PAGE_BOUNDARY");
        }
        if (routine.endsWith("stage_coleta_record")
                && (!"VALID".equals(bound.get(21)) || bound.get(22) != null)) {
            throw refused("QUARANTINE_REQUIRES_SEPARATE_FOOTPRINT");
        }
    }

    private ResultSet rows(final ResultSet physicalRows, final Statement owner) {
        return (ResultSet)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getStatement")) {
                                return owner;
                            }
                            if (method.getName().equals("unwrap")) {
                                throw refused("RESULT_ESCAPE");
                            }
                            if (method.getName().equals("isWrapperFor")) {
                                return false;
                            }
                            final String name = method.getName();
                            if (!name.startsWith("get")
                                    && !name.startsWith("is")
                                    && !java.util.Set.of("next", "close", "wasNull", "findColumn")
                                            .contains(name)) {
                                throw refused("RESULT_MUTATION");
                            }
                            return invoke(physicalRows, method, args);
                        });
    }

    private Object invoke(final Object receiver, final Method method, final Object[] args)
            throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (final InvocationTargetException error) {
            if (error.getCause() instanceof SQLException sql) {
                throw safe("JDBC_OPERATION", sql);
            }
            throw refused("JDBC_OPERATION");
        }
    }

    private long[] readLimitedCounters(final int maximum, final String sql, final Object... values)
            throws SQLException {
        if (maximum < 1 || maximum > 10) {
            throw refused("READBACK_COLUMN_BOUND");
        }
        reserve(1);
        try (PreparedStatement statement = physical.prepareStatement(sql)) {
            timeout(statement);
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Long number) {
                    statement.setLong(i + 1, number);
                } else {
                    statement.setString(i + 1, values[i].toString());
                }
            }
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw refused("READBACK_MISSING");
                }
                final int columns = row.getMetaData().getColumnCount();
                if (columns < 1 || columns > maximum) {
                    throw refused("READBACK_COLUMN_BOUND");
                }
                final long[] result = new long[columns];
                for (int i = 0; i < result.length; i++) {
                    result[i] = row.getLong(i + 1);
                    if (row.wasNull()) {
                        throw refused("READBACK_NULL");
                    }
                }
                if (row.next()) {
                    throw refused("READBACK_MULTIPLE");
                }
                return result;
            }
        } catch (final SQLException failure) {
            throw safe("SQL_READBACK", failure);
        }
    }

    private void equal(final String facet, final long[] actual, final long... expected)
            throws SQLException {
        if (!java.util.Arrays.equals(actual, expected)) {
            poisoned = true;
            throw refused("READBACK_" + facet);
        }
    }

    private void timeout(final Statement statement) throws SQLException {
        final long remaining =
                TimeUnit.SECONDS.toNanos(limits.deadlineSeconds()) - (clock.getAsLong() - started);
        if (remaining < TimeUnit.SECONDS.toNanos(1)) {
            throw refused("DEADLINE");
        }
        statement.setQueryTimeout(
                (int)
                        Math.min(
                                limits.queryTimeoutSeconds(),
                                TimeUnit.NANOSECONDS.toSeconds(remaining)));
    }

    private void active() throws SQLException {
        if (closed || poisoned || committed || commitUncertain) {
            throw refused("SESSION_NOT_ACTIVE");
        }
        deadline();
    }

    private void deadline() throws SQLException {
        if (Thread.currentThread().getId() != ownerThread) {
            throw refused("THREAD_BOUNDARY");
        }
        if (clock.getAsLong() - started >= TimeUnit.SECONDS.toNanos(limits.deadlineSeconds())) {
            poisoned = true;
            throw refused("DEADLINE");
        }
    }

    private void reserve(final long count) throws SQLException {
        deadline();
        if (count < 1 || executions > limits.maxSqlExecutions() - count) {
            throw refused("SQL_BUDGET");
        }
        executions += count;
    }

    private static SQLException refused(final String code) {
        return new Failure(code, null, 0);
    }

    private static SQLException safe(final String code, final SQLException cause) {
        if (cause instanceof Failure known) {
            return known;
        }
        final String state = cause.getSQLState();
        return new Failure(
                code,
                state != null && state.matches("[A-Z0-9]{5}") ? state : null,
                cause.getErrorCode());
    }

    private static SQLException cleanupFailure(final String code, final Throwable cause) {
        return cause instanceof SQLException sql ? safe(code, sql) : refused(code);
    }

    public boolean committed() {
        return committed;
    }

    public boolean commitUncertain() {
        return commitUncertain;
    }

    public boolean closeObserved() {
        return closeObserved;
    }

    public long sqlExecutions() {
        return executions;
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        SQLException failure = null;
        for (final Statement statement : openStatements) {
            try {
                statement.close();
            } catch (final SQLException | RuntimeException | Error error) {
                if (failure == null) {
                    failure = cleanupFailure("STATEMENT_CLOSE", error);
                } else {
                    failure.addSuppressed(cleanupFailure("STATEMENT_CLOSE", error));
                }
            }
        }
        openStatements.clear();
        try {
            // A possible commit is reconciled independently; cleanup must not compensate it.
            if (!committed && !commitUncertain) {
                physical.rollback();
            }
        } catch (final SQLException | RuntimeException | Error error) {
            if (failure == null) {
                failure = cleanupFailure("ROLLBACK_UNCERTAIN", error);
            } else {
                failure.addSuppressed(cleanupFailure("ROLLBACK_UNCERTAIN", error));
            }
        }
        try {
            physical.close();
            closeObserved = true;
        } catch (final SQLException | RuntimeException | Error error) {
            if (failure == null) {
                failure = cleanupFailure("CLOSE_UNCERTAIN", error);
            } else {
                failure.addSuppressed(cleanupFailure("CLOSE_UNCERTAIN", error));
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public Connection getConnection(final String user, final String password) throws SQLException {
        throw refused("CALLER_CREDENTIALS");
    }

    @Override
    public <T> T unwrap(final Class<T> type) throws SQLException {
        throw refused("DATASOURCE_ESCAPE");
    }

    @Override
    public boolean isWrapperFor(final Class<?> type) {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(final PrintWriter writer) throws SQLException {
        throw refused("LOG_WRITER");
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        throw refused("LOGIN_TIMEOUT_MUTATION");
    }

    @Override
    public int getLoginTimeout() {
        return 5;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
