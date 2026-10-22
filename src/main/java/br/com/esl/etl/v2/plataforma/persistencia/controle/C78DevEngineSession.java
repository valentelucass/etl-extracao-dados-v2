package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** One physical C78 session; borrowed handles cannot commit or escape the owner transaction. */
final class C78DevEngineSession implements RuntimeDevEngineBankBinding.Session {
    private static final int MAX_STATEMENT_HANDLES = 64;
    static final Set<String> TABLES =
            Set.of(
                    "core.coleta",
                    "ctl.coleta_promotion_result",
                    "ctl.data_quality_check_policy",
                    "ctl.data_quality_policy",
                    "ctl.execution_attempt",
                    "ctl.execution_audit",
                    "ctl.execution_count",
                    "ctl.execution_cycle",
                    "ctl.execution_lease",
                    "ctl.execution_page_audit",
                    "ctl.execution_partition",
                    "ctl.execution_state_event",
                    "ctl.incremental_publication_watermark",
                    "ctl.page_audit",
                    "ctl.runtime_coleta_publication_receipt",
                    "ctl.runtime_contract_evidence",
                    "ctl.source_catalog",
                    "ctl.source_watermark_observation",
                    "recon.execution_data_quality_check_result",
                    "recon.execution_data_quality_evaluation",
                    "recon.execution_metric_snapshot",
                    "recon.observability_alert",
                    "stg.coleta_exact_time",
                    "stg.coleta_record",
                    "stg.execution_record",
                    "ctl.source_protocol_binding",
                    "ctl.execution_source_protocol",
                    "ctl.execution_publication_event",
                    "ctl.execution_promotion_result");
    private final Connection physical;
    private final C78DevEngineDispatch controls;
    private final RuntimeDevEngineBankBinding.Admission independentAdmission;
    private final C78Sample12Selection sampleSelection;
    private C78Sample12Owner.Baseline sampleBaseline;
    private long originalTransactionId;
    private int unprovedCalls;
    private final AtomicBoolean borrowed = new AtomicBoolean();
    private final Set<Statement> statements =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private volatile boolean protectedTransaction;
    private volatile boolean closed;

    C78DevEngineSession(
            final Connection physical,
            final C78DevEngineDispatch controls,
            final RuntimeDevEngineBankBinding.Admission independentAdmission) {
        this(physical, controls, independentAdmission, null);
    }

    C78DevEngineSession(
            final Connection physical,
            final C78DevEngineDispatch controls,
            final RuntimeDevEngineBankBinding.Admission independentAdmission,
            final C78Sample12Selection sampleSelection) {
        this.physical = Objects.requireNonNull(physical);
        this.controls = Objects.requireNonNull(controls);
        this.independentAdmission = Objects.requireNonNull(independentAdmission);
        this.sampleSelection = sampleSelection;
    }

    synchronized void establishProtection() throws SQLException {
        if (sampleSelection != null) {
            controls.reserveSampleCleanup();
        }
        controls.consumeStatement();
        try (var query = physical.createStatement()) {
            query.setQueryTimeout(10);
            try (var row = query.executeQuery(C78DevCatalogPlan.Query.CONTEXT_PRE.sql())) {
                if (!row.next()
                        || row.getInt(1) != 1
                        || !"16.0.1000.6".equals(row.getString(2))
                        || row.getInt(3) != 1
                        || !"SQL".equals(row.getString(4))
                        || row.getLong(5) != controls.provedSqlPID()
                        || row.getLong(6) < 1
                        || row.getInt(7) != 0
                        || row.next()) {
                    throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
                }
            }
        }
        physical.setAutoCommit(false);
        // Names are a closed code-owned set; the descriptor cannot supply SQL identifiers.
        for (final String table : TABLES.stream().sorted().toList()) {
            controls.consumeStatement();
            try (var query = physical.createStatement()) {
                query.setQueryTimeout(10);
                try (var rows =
                        query.executeQuery(
                                "SELECT TOP (1) 1 FROM " + table + " WITH (TABLOCKX,HOLDLOCK)")) {
                    if (rows.next() && rows.next()) {
                        throw new Refusal(MissingGate.WRITER_PROTECTION);
                    }
                }
            }
        }
        protectedTransaction = true;
        assertProtection();
        if (sampleSelection != null) {
            sampleBaseline =
                    C78Sample12Owner.baseline(
                            physical,
                            sampleSelection,
                            () -> {
                                assertProtection();
                                controls.consumeStatement();
                            });
        }
    }

    private synchronized void assertProtection() throws SQLException {
        if (closed || !protectedTransaction) {
            throw new Refusal(MissingGate.WRITER_PROTECTION);
        }
        controls.consumeStatement();
        final String objectIds =
                TABLES.stream()
                        .sorted()
                        .map(table -> "OBJECT_ID(N'" + table + "')")
                        .collect(java.util.stream.Collectors.joining(","));
        try (var query = physical.createStatement()) {
            query.setQueryTimeout(10);
            try (var row =
                    query.executeQuery(
                            "SELECT @@TRANCOUNT,XACT_STATE(),COUNT(DISTINCT"
                                    + " resource_associated_entity_id)"
                                    + (sampleSelection == null
                                            ? ""
                                            : ",(SELECT transaction_id FROM"
                                                    + " sys.dm_tran_current_transaction)")
                                    + """
 FROM sys.dm_tran_locks WHERE request_session_id=@@SPID
AND resource_database_id=DB_ID() AND resource_type=N'OBJECT'
AND request_mode=N'X' AND request_status=N'GRANT'
AND request_owner_type=N'TRANSACTION'
AND request_owner_id=(SELECT transaction_id FROM sys.dm_tran_current_transaction)
AND resource_associated_entity_id IN (
"""
                                    + objectIds
                                    + ")")) {
                if (!row.next()
                        || row.getInt(1) != 1
                        || row.getInt(2) != 1
                        || row.getInt(3) != TABLES.size()) {
                    protectedTransaction = false;
                    throw new Refusal(MissingGate.WRITER_PROTECTION);
                }
                if (sampleSelection != null) {
                    final long current = row.getLong(4);
                    if (row.wasNull()
                            || current < 1
                            || originalTransactionId != 0 && originalTransactionId != current) {
                        protectedTransaction = false;
                        throw new Refusal(MissingGate.WRITER_PROTECTION);
                    }
                    originalTransactionId = current;
                }
                if (row.next()) {
                    throw new Refusal(MissingGate.WRITER_PROTECTION);
                }
            }
        }
    }

    boolean active() {
        return !closed && protectedTransaction;
    }

    @Override
    public synchronized void checkpoint() throws SQLException {
        independentAdmission.verify();
        controls.verify();
        if (sampleSelection == null) {
            assertProtection();
        } else if (closed || !protectedTransaction || originalTransactionId < 1) {
            throw new Refusal(MissingGate.WRITER_PROTECTION);
        }
    }

    @Override
    public DataSource dataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                checkpoint();
                if (!borrowed.compareAndSet(false, true)) {
                    throw new Refusal(MissingGate.BUDGET);
                }
                final AtomicBoolean returned = new AtomicBoolean();
                return (Connection)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    final String name = method.getName();
                                    if (name.equals("close")) {
                                        if (returned.compareAndSet(false, true)) {
                                            borrowed.set(false);
                                        }
                                        return null;
                                    }
                                    if (name.equals("isClosed")) {
                                        return returned.get() || closed;
                                    }
                                    if (name.equals("toString")) {
                                        return "C78DevEngineBorrowedConnection[redacted]";
                                    }
                                    if (returned.get() || closed) {
                                        throw new SQLException("DEV_ENGINE_BORROW_CLOSED");
                                    }
                                    if (name.equals("commit")
                                            || name.equals("unwrap")
                                            || name.equals("getMetaData")
                                            || name.equals("createStatement")
                                            || name.equals("prepareStatement")
                                            || name.equals("setSavepoint")
                                            || name.equals("releaseSavepoint")
                                            || name.equals("abort")
                                            || name.equals("setCatalog")
                                            || name.equals("setSchema")) {
                                        throw new SQLException("DEV_ENGINE_OPERATION_NOT_ADMITTED");
                                    }
                                    if (name.equals("setAutoCommit")) {
                                        if (Boolean.TRUE.equals(args[0])) {
                                            throw new SQLException(
                                                    "DEV_ENGINE_COMMIT_NOT_ADMITTED");
                                        }
                                        return null;
                                    }
                                    if (name.equals("rollback")) {
                                        throw new SQLException(
                                                "DEV_ENGINE_OWNER_ROLLBACK_REQUIRED");
                                    }
                                    if (name.equals("prepareCall")) {
                                        final String sql = (String) args[0];
                                        if (!controls.allowedCall(sql)) {
                                            throw new SQLException("DEV_ENGINE_CALL_NOT_ADMITTED");
                                        }
                                        return prepareCall(
                                                method, args, (Connection) proxy, returned);
                                    }
                                    if (name.equals("setNetworkTimeout")) {
                                        if ((Integer) args[1] < 1) {
                                            throw new Refusal(MissingGate.BUDGET);
                                        }
                                        args[1] = Math.min((Integer) args[1], 10_000);
                                    }
                                    try {
                                        return method.invoke(physical, args);
                                    } catch (final InvocationTargetException failure) {
                                        throw failure.getCause();
                                    }
                                });
            }

            @Override
            public Connection getConnection(final String user, final String password)
                    throws SQLException {
                throw new SQLException("DEV_ENGINE_CREDENTIAL_OVERRIDE_REFUSED");
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
            public int getLoginTimeout() {
                return 5;
            }

            @Override
            public void setLoginTimeout(final int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getLogger("br.com.esl.etl.v2.c78.engine");
            }

            @Override
            public <T> T unwrap(final Class<T> type) throws SQLException {
                throw new SQLException("DEV_ENGINE_UNWRAP_REFUSED");
            }

            @Override
            public boolean isWrapperFor(final Class<?> type) {
                return false;
            }
        };
    }

    @Override
    public br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway sampleStagingGateway()
            throws SQLException {
        checkpoint();
        if (sampleSelection != null) {
            return br.com.esl.etl.v2.plataforma.persistencia.coletas
                    .JdbcSqlServerColetaStagingGateway.borrowedSample12(dataSource());
        }
        return br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway
                .borrowedTransaction(dataSource(), controls.maximumRows());
    }

    @Override
    public synchronized RuntimeDevEngineBankBinding.SampleReadback finishSample(
            final RuntimeDevEngineBankBinding.SampleFinish finish) throws SQLException {
        if (sampleSelection == null || sampleBaseline == null || closed) {
            throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
        }
        Objects.requireNonNull(finish);
        Throwable failure = null;
        boolean compared = false;
        RuntimeDevEngineBankBinding.SampleReadback result = null;
        try {
            closeStatements();
            compared =
                    C78Sample12Owner.compare(
                            physical,
                            sampleSelection,
                            finish,
                            () -> {
                                checkpoint();
                                assertProtection();
                                controls.consumeStatement();
                            });
        } catch (SQLException refused) {
            failure = refused;
        } catch (RuntimeException refused) {
            failure = new SQLException("DEV_SAMPLE_OWNER_READBACK_FAILED", refused);
        } catch (Error refused) {
            failure = refused;
        }
        try {
            result =
                    C78Sample12Owner.rollbackAndClose(
                            physical,
                            sampleSelection,
                            sampleBaseline,
                            finish,
                            compared,
                            controls::consumeSampleCleanup,
                            controls::statementsUsed);
        } catch (SQLException | RuntimeException | Error cleanup) {
            failure =
                    C78OwnedCleanup.combine(
                            failure,
                            C78OwnedCleanup.failure(cleanup, "DEV_SAMPLE_OWNER_CLEANUP_FAILED"));
        } finally {
            protectedTransaction = false;
            borrowed.set(false);
        }
        failure = observePhysicalClose(failure);
        C78OwnedCleanup.rethrow(failure);
        return result;
    }

    private void closeStatements() throws SQLException {
        Throwable failure = null;
        for (Statement handle : statements.toArray(Statement[]::new)) {
            try {
                handle.close();
                statements.remove(handle);
            } catch (SQLException | RuntimeException | Error cleanup) {
                failure =
                        C78OwnedCleanup.combine(
                                failure,
                                C78OwnedCleanup.failure(
                                        cleanup, "DEV_ENGINE_STATEMENT_CLOSE_FAILED"));
            }
        }
        C78OwnedCleanup.rethrow(failure);
    }

    private synchronized Object prepareCall(
            final Method method,
            final Object[] args,
            final Connection borrower,
            final AtomicBoolean returned)
            throws Throwable {
        if (returned.get() || closed || !protectedTransaction) {
            throw new SQLException("DEV_ENGINE_BORROW_CLOSED");
        }
        if (statements.size() >= MAX_STATEMENT_HANDLES) {
            throw new Refusal(MissingGate.BUDGET);
        }
        // Pure rechecks before allocating a raw handle; execution has its separate SQL budget.
        independentAdmission.verify();
        controls.verify();
        final Statement raw;
        try {
            raw = (Statement) method.invoke(physical, args);
        } catch (final InvocationTargetException failure) {
            throw failure.getCause();
        }
        statements.add(raw);
        try {
            return statement(raw, borrower);
        } catch (final SQLException | RuntimeException | Error failure) {
            try {
                raw.close();
                statements.remove(raw);
            } catch (final SQLException | RuntimeException | Error cleanup) {
                C78OwnedCleanup.combine(failure, cleanup);
            }
            throw failure;
        }
    }

    private Object statement(final Statement statement, final Connection borrower)
            throws SQLException {
        statement.setQueryTimeout(10);
        final int[] batch = {0};
        final AtomicBoolean pendingCall = new AtomicBoolean();
        return Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {java.sql.CallableStatement.class},
                (proxy, method, args) -> {
                    synchronized (C78DevEngineSession.this) {
                        final String name = method.getName();
                        if (name.equals("close")) {
                            if (statements.contains(statement)) {
                                statement.close();
                                statements.remove(statement);
                                if (sampleSelection != null && pendingCall.getAndSet(false)) {
                                    assertProtection();
                                    unprovedCalls--;
                                }
                            }
                            return null;
                        }
                        if (name.equals("isClosed")) {
                            return closed || !statements.contains(statement);
                        }
                        if (closed || !statements.contains(statement)) {
                            throw new SQLException("DEV_ENGINE_STATEMENT_CLOSED");
                        }
                        if (name.equals("unwrap")
                                || name.equals("setMaxFieldSize")
                                || name.equals("setMaxRows")
                                || name.equals("setLargeMaxRows")) {
                            throw new SQLException("DEV_ENGINE_STATEMENT_ESCAPE_REFUSED");
                        }
                        if (name.equals("getConnection")) {
                            return borrower;
                        }
                        if (name.equals("setQueryTimeout")) {
                            if ((Integer) args[0] < 1) {
                                throw new Refusal(MissingGate.BUDGET);
                            }
                            args[0] = Math.max(1, Math.min((Integer) args[0], 10));
                        }
                        if (name.equals("addBatch")) {
                            if (sampleSelection != null) {
                                throw new SQLException("DEV_SAMPLE_PER_CALL_OWNER_PROOF_REQUIRED");
                            }
                            if (args != null && args.length != 0
                                    || ++batch[0] > Math.min(100, controls.maximumRows())) {
                                throw new Refusal(MissingGate.BUDGET);
                            }
                        }
                        if (name.equals("clearBatch")) {
                            batch[0] = 0;
                        }
                        if (name.startsWith("execute")) {
                            if (sampleSelection != null && unprovedCalls != 0) {
                                throw new SQLException("DEV_SAMPLE_PREVIOUS_CALL_PROOF_PENDING");
                            }
                            if (args != null && args.length != 0) {
                                throw new SQLException("DEV_ENGINE_SQL_OVERRIDE_REFUSED");
                            }
                            checkpoint();
                            if (sampleSelection != null) {
                                // The CALL and its original-transaction readback must both fit;
                                // three separate owner cleanup SELECTs remain reserved.
                                controls.requireSampleCallProofBudget();
                            }
                            for (int count = 0;
                                    count < Math.max(1, name.contains("Batch") ? batch[0] : 1);
                                    count++) {
                                controls.consumeStatement();
                            }
                        }
                        try {
                            final Object result = method.invoke(statement, args);
                            if (sampleSelection != null && name.startsWith("execute")) {
                                pendingCall.set(true);
                                if (!(result instanceof ResultSet)
                                        && !Boolean.TRUE.equals(result)) {
                                    assertProtection();
                                    pendingCall.set(false);
                                } else {
                                    unprovedCalls++;
                                }
                            }
                            if (name.contains("Batch") && name.startsWith("execute")) {
                                batch[0] = 0;
                            }
                            return result instanceof ResultSet rows
                                    ? rows(rows, pendingCall)
                                    : result;
                        } catch (final InvocationTargetException failure) {
                            if (sampleSelection != null && name.startsWith("execute")) {
                                protectedTransaction = false;
                            }
                            throw failure.getCause();
                        }
                    }
                });
    }

    private ResultSet rows(final ResultSet rows, final AtomicBoolean pendingCall) {
        final int[] count = {0};
        return (ResultSet)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("unwrap")
                                    || method.getName().equals("getStatement")
                                    || method.getName().startsWith("update")
                                    || Set.of(
                                                    "insertRow",
                                                    "deleteRow",
                                                    "absolute",
                                                    "relative",
                                                    "previous",
                                                    "first",
                                                    "last",
                                                    "beforeFirst",
                                                    "afterLast")
                                            .contains(method.getName())) {
                                throw new SQLException("DEV_ENGINE_RESULT_ESCAPE_REFUSED");
                            }
                            try {
                                final Object result = method.invoke(rows, args);
                                if (method.getName().equals("close")
                                        && sampleSelection != null
                                        && pendingCall.getAndSet(false)) {
                                    synchronized (C78DevEngineSession.this) {
                                        assertProtection();
                                        unprovedCalls--;
                                    }
                                }
                                if (method.getName().equals("next")
                                        && Boolean.TRUE.equals(result)
                                        && ++count[0] > controls.maximumRows()) {
                                    throw new Refusal(MissingGate.BUDGET);
                                }
                                return result;
                            } catch (final InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    @Override
    public synchronized void close() throws SQLException {
        if (!closed) {
            protectedTransaction = false;
            borrowed.set(false);
            Throwable failure = null;
            try {
                closeStatements();
            } catch (SQLException | RuntimeException | Error cleanup) {
                failure = C78OwnedCleanup.failure(cleanup, "DEV_ENGINE_STATEMENT_CLOSE_FAILED");
            }
            try {
                physical.rollback();
            } catch (final SQLException | RuntimeException | Error cleanup) {
                failure =
                        C78OwnedCleanup.combine(
                                failure,
                                C78OwnedCleanup.failure(cleanup, "DEV_ENGINE_ROLLBACK_FAILED"));
            }
            try {
                physical.close();
            } catch (final SQLException | RuntimeException | Error cleanup) {
                failure =
                        C78OwnedCleanup.combine(
                                failure,
                                C78OwnedCleanup.failure(
                                        cleanup, "DEV_ENGINE_PHYSICAL_CLOSE_FAILED"));
            }
            failure = observePhysicalClose(failure);
            C78OwnedCleanup.rethrow(failure);
        }
    }

    private Throwable observePhysicalClose(Throwable failure) {
        try {
            closed = physical.isClosed();
            if (closed) {
                statements.clear();
            } else {
                failure =
                        C78OwnedCleanup.combine(
                                failure, new SQLException("DEV_SAMPLE_PHYSICAL_CLOSE_NOT_PROVED"));
            }
        } catch (SQLException | RuntimeException | Error cleanup) {
            failure =
                    C78OwnedCleanup.combine(
                            failure,
                            C78OwnedCleanup.failure(
                                    cleanup, "DEV_ENGINE_CLOSE_OBSERVATION_FAILED"));
        }
        return failure;
    }

    synchronized boolean physicalClosed() {
        return closed;
    }
}
