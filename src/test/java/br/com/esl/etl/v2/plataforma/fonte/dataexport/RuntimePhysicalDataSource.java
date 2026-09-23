package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Test-only instrumentation. No SQL response or connection is simulated. */
final class RuntimePhysicalDataSource implements DataSource {
    static final String URL =
            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                    + "integratedSecurity=true;encrypt=true;trustServerCertificate=true;"
                    + "loginTimeout=10;socketTimeout=20000;applicationName=V2_BLOCK53_SYNTHETIC";
    private final Semaphore permits = new Semaphore(2);
    private final String fault;
    private boolean injected;
    Runnable cancelAfterCommit = () -> {};

    RuntimePhysicalDataSource(final String fault) {
        this.fault = fault;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!permits.tryAcquire()) {
            throw new SQLException("PHYSICAL_CONNECTION_BUDGET");
        }
        Connection physical = null;
        try {
            physical = DriverManager.getConnection(URL);
            try (Statement check = physical.createStatement()) {
                check.setQueryTimeout(10);
                try (var result =
                        check.executeQuery(
                                "SELECT CASE WHEN DB_NAME()=N'ETL_SISTEMA_V2_SHADOW'"
                                        + " AND CONNECTIONPROPERTY('auth_scheme') IN(N'NTLM',N'KERBEROS')"
                                        + " AND SERVERPROPERTY('IsClustered')=0 AND SERVERPROPERTY('IsHadrEnabled')=0 THEN 1 ELSE 0 END")) {
                    if (!result.next() || result.getInt(1) != 1 || result.next()) {
                        throw new SQLException("PHYSICAL_TARGET_REFUSED");
                    }
                }
            }
            final Connection delegate = physical;
            final boolean[] closed = {false};
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("close")) {
                                    if (!closed[0]) {
                                        try {
                                            delegate.close();
                                        } finally {
                                            closed[0] = true;
                                            permits.release();
                                        }
                                    }
                                    return null;
                                }
                                if (fault.equals("legacy-coleta")
                                        && method.getName().equals("prepareCall")
                                        && args[0].toString()
                                                .contains("usp_apply_reconcile_publish_coletas")) {
                                    args[0] =
                                            args[0].toString()
                                                    .replace(
                                                            "usp_apply_reconcile_publish_coletas",
                                                            "usp_apply_reconcile_publish_execution");
                                }
                                final Object value = invoke(delegate, method, args);
                                if (fault.equals("partial") && method.getName().equals("commit")) {
                                    System.out.println(
                                            "PARTIAL_COMMIT_PASS deliberate_owned_JVM_exit");
                                    System.out.flush();
                                    Runtime.getRuntime().halt(0);
                                }
                                if (value instanceof Statement statement) {
                                    statement.setQueryTimeout(10);
                                    final String sql =
                                            args != null
                                                            && args.length > 0
                                                            && args[0] instanceof String
                                                    ? (String) args[0]
                                                    : "";
                                    final Class<?> type =
                                            value instanceof java.sql.CallableStatement
                                                    ? java.sql.CallableStatement.class
                                                    : value instanceof java.sql.PreparedStatement
                                                            ? java.sql.PreparedStatement.class
                                                            : Statement.class;
                                    final java.util.Map<Integer, Object> parameters =
                                            new java.util.HashMap<>();
                                    return Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class<?>[] {type},
                                            (p, m, a) -> {
                                                if (m.getName().startsWith("set")
                                                        && a != null
                                                        && a.length >= 2
                                                        && a[0] instanceof Integer index) {
                                                    parameters.put(index, a[1]);
                                                }
                                                final boolean apply =
                                                        sql.contains("usp_apply_reconcile_publish_")
                                                                && m.getName()
                                                                        .equals("executeQuery");
                                                if (apply
                                                        && fault.equals("before-apply")
                                                        && !injected) {
                                                    injected = true;
                                                    throw new SQLException(
                                                            "SYNTHETIC_PRE_APPLY_FAILURE");
                                                }
                                                if (apply
                                                        && fault.equals("apply-rollback")
                                                        && !injected) {
                                                    injected = true;
                                                    delegate.setAutoCommit(false);
                                                    try {
                                                        final Object applied =
                                                                invoke(statement, m, a);
                                                        if (applied
                                                                instanceof
                                                                java.sql.ResultSet rows) {
                                                            if (!rows.next()
                                                                    || rows.getLong(
                                                                                    "candidate_rows")
                                                                            != 3) {
                                                                throw new SQLException(
                                                                        "ROLLBACK_APPLY_RECEIPT_REQUIRED");
                                                            }
                                                            rows.close();
                                                        }
                                                        drain(statement);
                                                    } finally {
                                                        delegate.rollback();
                                                        delegate.setAutoCommit(true);
                                                    }
                                                    throw new SQLException(
                                                            "SYNTHETIC_REAL_APPLY_ROLLED_BACK_BEFORE_OUTER_COMMIT");
                                                }
                                                if (apply
                                                        && fault.equals("apply-race")
                                                        && !injected) {
                                                    injected = true;
                                                    RuntimePhysicalRecoveryBarrier.awaitPair(
                                                            parameters.get(1).toString());
                                                }
                                                final Object answer = invoke(statement, m, a);
                                                if (fault.equals("sealed")
                                                        && !injected
                                                        && sql.contains("usp_runtime_recovery")
                                                        && "SEAL".equals(parameters.get(2))
                                                        && m.getName().equals("execute")) {
                                                    injected = true;
                                                    drain(statement);
                                                    throw new SQLException(
                                                            "SYNTHETIC_ACK_LOST_AFTER_SEAL");
                                                }
                                                if (apply
                                                        && (fault.equals("lost-ack")
                                                                || fault.equals("cancel-commit"))
                                                        && !injected) {
                                                    injected = true;
                                                    if (answer instanceof java.sql.ResultSet rs) {
                                                        while (rs.next()) {
                                                            rs.getString("execution_id");
                                                        }
                                                        rs.close();
                                                    }
                                                    drain(statement);
                                                    cancelAfterCommit.run();
                                                    throw new SQLException(
                                                            "SYNTHETIC_ACK_LOST_AFTER_COMMIT");
                                                }
                                                return answer;
                                            });
                                }
                                return value;
                            });
        } catch (final SQLException | RuntimeException failure) {
            if (physical != null) {
                try {
                    physical.close();
                } catch (final SQLException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            permits.release();
            throw failure;
        }
    }

    private static void drain(final Statement statement) throws SQLException {
        int results = 0;
        while (statement.getMoreResults(Statement.CLOSE_ALL_RESULTS)
                || statement.getUpdateCount() != -1) {
            if (++results > 32) {
                throw new SQLException("RESULT_BUDGET");
            }
        }
    }

    private static Object invoke(
            final Object target, final java.lang.reflect.Method method, final Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException failure) {
            throw failure.getCause();
        }
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
        return Logger.getLogger("runtime-physical");
    }

    @Override
    public <T> T unwrap(final Class<T> type) throws SQLException {
        throw new SQLException("UNWRAP_REFUSED");
    }

    @Override
    public boolean isWrapperFor(final Class<?> type) {
        return false;
    }
}
