package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Fake JDBC only: transaction ownership, writer locks, partial readback and uncertain commit. */
class TestRetainedSqlSessionTest {
    private static final UUID EXECUTION = UUID.fromString("9bca53fa-2c4d-4776-ad8c-a45124e54280");
    private static final TestRetainedSqlSession.Limits LIMITS =
            new TestRetainedSqlSession.Limits(1200, 1000, 10, 60);
    private static final TestRetainedSqlSession.Expected EXPECTED =
            new TestRetainedSqlSession.Expected(EXECUTION, 1, 1, 1);

    static final class Fake {
        boolean autoCommit = true;
        int commits;
        int rollbacks;
        int closes;
        int calls;
        int grantedLocks = 12;
        long transaction = 17;
        long fresh;
        long clock;
        long typedRows = 1;
        long genericRows = 1;
        boolean failCommit;
        boolean failPost;
        boolean failClose;
        boolean excessiveReadbackColumns;
        final List<Integer> timeouts = new ArrayList<>();
        final List<String> queries = new ArrayList<>();
        final Connection connection =
                proxy(
                        Connection.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getAutoCommit" -> autoCommit;
                                    case "setAutoCommit" -> {
                                        autoCommit = (boolean) a[0];
                                        yield null;
                                    }
                                    case "createStatement" -> statement(null, Statement.class);
                                    case "prepareStatement" ->
                                            statement(
                                                    (String) a[0],
                                                    java.sql.PreparedStatement.class);
                                    case "prepareCall" ->
                                            statement((String) a[0], CallableStatement.class);
                                    case "commit" -> {
                                        commits++;
                                        if (failCommit) {
                                            throw new SQLException(
                                                    "synthetic private error", "08006", 123);
                                        }
                                        yield null;
                                    }
                                    case "rollback" -> {
                                        rollbacks++;
                                        yield null;
                                    }
                                    case "close" -> {
                                        closes++;
                                        if (failClose) {
                                            throw new SQLException("synthetic private cleanup");
                                        }
                                        yield null;
                                    }
                                    default -> zero(m.getReturnType());
                                });

        <T> T statement(final String sql, final Class<T> type) {
            return proxy(
                    type,
                    (p, m, a) -> {
                        if (m.getName().equals("setQueryTimeout")) {
                            timeouts.add((int) a[0]);
                            return null;
                        }
                        if (m.getName().equals("executeQuery")) {
                            final String query = sql == null ? (String) a[0] : sql;
                            queries.add(query);
                            if (query.startsWith("SELECT TOP")) {
                                return rows();
                            }
                            if (query.equals(TestRetainedSqlContract.IDENTITY)) {
                                return rows(1);
                            }
                            if (query.equals(TestRetainedSqlContract.CLEAN)) {
                                return rows(0, 0, 0);
                            }
                            if (query.equals(TestRetainedSqlContract.FRESH)) {
                                return rows(fresh, 0, 0);
                            }
                            if (query.equals(TestRetainedSqlContract.checkpoint())) {
                                return rows(1, 1, 45, transaction, grantedLocks, 0);
                            }
                            if (query.equals(TestRetainedSqlContract.READBACK)) {
                                if (excessiveReadbackColumns) {
                                    return rows(1, 1, 1, 1, 1, 1, 0, 1, 1, 0, 99);
                                }
                                return rows(
                                        1,
                                        genericRows,
                                        failPost && commits > 0 ? 0 : typedRows,
                                        1,
                                        1,
                                        1,
                                        0,
                                        1,
                                        1,
                                        0);
                            }
                            throw new AssertionError("unclassified SQL fixture");
                        }
                        if (m.getName().startsWith("execute")) {
                            calls++;
                        }
                        if (m.getName().equals("executeBatch")) {
                            return new int[] {1};
                        }
                        return zero(m.getReturnType());
                    });
        }

        TestRetainedSqlSession open() throws SQLException {
            return TestRetainedSqlSession.admitted(connection, LIMITS, EXECUTION, () -> clock, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object zero(final Class<?> type) {
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

    private static ResultSet rows(final long... values) {
        final boolean[] visited = {false};
        return proxy(
                ResultSet.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "next" -> {
                                final boolean next = !visited[0] && values.length > 0;
                                visited[0] = true;
                                yield next;
                            }
                            case "getLong" -> values[(int) a[0] - 1];
                            case "getMetaData" ->
                                    proxy(
                                            ResultSetMetaData.class,
                                            (x, mm, aa) ->
                                                    mm.getName().equals("getColumnCount")
                                                            ? values.length
                                                            : zero(mm.getReturnType()));
                            default -> zero(m.getReturnType());
                        });
    }

    @Test
    @SuppressWarnings("try")
    void freshAdmissionProtectsTwelveTablesBeforeBorrowingAndOwnerAloneCommits() throws Exception {
        final var fake = new Fake();
        final var session = fake.open();
        try (session;
                var borrowed = session.getConnection()) {
            assertEquals(
                    12, fake.queries.stream().filter(q -> q.contains("TABLOCKX,HOLDLOCK")).count());
            borrowed.commit();
            borrowed.close();
            assertEquals(0, fake.commits);
            assertEquals(0, fake.closes);
            final var receipt = session.finishPartial(EXPECTED);
            assertEquals("CAPTURE_RETAINED_PARTIAL", receipt.outcome());
            assertTrue(receipt.committed());
            assertTrue(receipt.postCommitReadback());
            assertEquals(1, receipt.suppressedCommits());
            assertThrows(SQLException.class, () -> session.finishPartial(EXPECTED));
        }
        assertEquals(1, fake.commits);
        assertEquals(0, fake.rollbacks);
        assertEquals(1, fake.closes);
        assertTrue(session.closeObserved());
        assertTrue(fake.timeouts.stream().allMatch(n -> n > 0 && n <= 10));
    }

    @Test
    void excessReadbackColumnsRefuseBeforeCommitAndRollbackTheOccurrence() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open()) {
            fake.excessiveReadbackColumns = true;
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("READBACK_COLUMN_BOUND", failure.code());
        }
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
        assertEquals(1, fake.closes);
    }

    @Test
    void existingOccurrenceRefusesAndRollsBackWithoutCommit() {
        final var fake = new Fake();
        fake.fresh = 1;
        assertThrows(TestRetainedSqlSession.Failure.class, fake::open);
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
        assertEquals(1, fake.closes);
    }

    @Test
    void writerLockLossAndTransactionReplacementBothRefuseCommit() throws Exception {
        for (final boolean lockLoss : new boolean[] {true, false}) {
            final var fake = new Fake();
            try (var session = fake.open()) {
                if (lockLoss) {
                    fake.grantedLocks = 11;
                } else {
                    fake.transaction++;
                }
                assertThrows(
                        TestRetainedSqlSession.Failure.class,
                        () -> session.finishPartial(EXPECTED));
            }
            assertEquals(0, fake.commits);
            assertEquals(1, fake.rollbacks);
        }
    }

    @Test
    void mismatchedTypedReadbackRollsBackAndNeverCallsCommit() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open()) {
            fake.typedRows = 0;
            assertThrows(
                    TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
        }
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
    }

    @Test
    void uncertainCommitIsTypedAndCannotBeRepeated() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open()) {
            fake.failCommit = true;
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("COMMIT_UNCERTAIN", failure.code());
            assertEquals("08006", failure.getSQLState());
            assertEquals(123, failure.getErrorCode());
            assertFalse(failure.toString().contains("private"));
            assertNull(failure.getCause());
            assertTrue(session.commitUncertain());
            assertThrows(SQLException.class, () -> session.finishPartial(EXPECTED));
        }
        assertEquals(1, fake.commits);
        assertEquals(0, fake.rollbacks);
        assertEquals(1, fake.closes);
    }

    @Test
    void postCommitFailurePreservesKnownCommitAndDoesNotCompensate() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open()) {
            fake.failPost = true;
            assertThrows(
                    TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
            assertTrue(session.committed());
            assertFalse(session.commitUncertain());
        }
        assertEquals(1, fake.commits);
        assertEquals(0, fake.rollbacks);
    }

    @Test
    void borrowedRollbackCannotCompensateKnownCommit() throws Exception {
        assertBorrowedRollbackRefusedAfterCommit(false);
    }

    @Test
    void borrowedRollbackCannotCompensateUnknownCommit() throws Exception {
        assertBorrowedRollbackRefusedAfterCommit(true);
    }

    private static void assertBorrowedRollbackRefusedAfterCommit(final boolean unknown)
            throws Exception {
        final var fake = new Fake();
        final var session = fake.open();
        final var borrowed = session.getConnection();
        try (session;
                borrowed) {
            fake.failCommit = unknown;
            if (unknown) {
                assertThrows(
                        TestRetainedSqlSession.Failure.class,
                        () -> session.finishPartial(EXPECTED));
            } else {
                session.finishPartial(EXPECTED);
            }
            final long executions = session.sqlExecutions();
            final var failure =
                    assertThrows(TestRetainedSqlSession.Failure.class, borrowed::rollback);
            assertEquals("SESSION_NOT_ACTIVE", failure.code());
            assertEquals(executions, session.sqlExecutions());
            assertEquals(0, fake.rollbacks);
            assertEquals(unknown, session.commitUncertain());
            assertEquals(!unknown, session.committed());
        }
        session.close();
        final var closedFailure =
                assertThrows(TestRetainedSqlSession.Failure.class, borrowed::rollback);
        assertEquals("SESSION_NOT_ACTIVE", closedFailure.code());
        assertEquals(1, fake.commits);
        assertEquals(0, fake.rollbacks);
        assertEquals(1, fake.closes);
        assertTrue(session.closeObserved());
        assertEquals(unknown, session.commitUncertain());
        assertEquals(!unknown, session.committed());
    }

    @Test
    void deadlinePreventsFinalizationAndClosingRollsBack() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open()) {
            fake.clock = TimeUnit.SECONDS.toNanos(60);
            assertThrows(SQLException.class, () -> session.finishPartial(EXPECTED));
        }
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
    }

    @Test
    void adapterCannotChangeCatalogExposePhysicalConnectionOrRunArbitrarySql() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open();
                var borrowed = session.getConnection()) {
            assertThrows(SQLException.class, () -> borrowed.setAutoCommit(true));
            assertThrows(SQLException.class, () -> borrowed.setCatalog("ETL_SISTEMA"));
            assertThrows(SQLException.class, borrowed::createStatement);
            assertThrows(SQLException.class, () -> borrowed.unwrap(Connection.class));
            assertThrows(
                    SQLException.class,
                    () ->
                            borrowed.prepareCall(
                                    "{call"
                                            + " core.usp_apply_reconcile_publish_execution(?,?,?,?,?)}"));
            assertThrows(SQLException.class, () -> session.getConnection("sa", "synthetic-only"));
        }
        assertEquals(0, fake.commits);
    }

    @Test
    void batchesReserveEveryRowAndClampGatewayTimeout() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open();
                var borrowed = session.getConnection();
                var call =
                        borrowed.prepareCall(
                                "{call"
                                        + " stg.usp_stage_coleta_record(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
            call.setQueryTimeout(20);
            call.setString(1, EXECUTION.toString());
            call.setString(21, "VALID");
            call.setNull(22, java.sql.Types.NVARCHAR);
            for (int i = 0; i < 100; i++) {
                call.addBatch();
            }
            final long before = session.sqlExecutions();
            call.executeBatch();
            assertEquals(101, session.sqlExecutions() - before);
            assertSame(borrowed, call.getConnection());
            assertThrows(SQLException.class, () -> call.execute("DELETE FROM stg.coleta_record"));
            assertThrows(SQLException.class, () -> call.unwrap(CallableStatement.class));
            for (int i = 0; i < 100; i++) {
                call.addBatch();
            }
            assertThrows(SQLException.class, call::addBatch);
        }
        assertTrue(fake.timeouts.stream().allMatch(n -> n <= 10));
        assertEquals(0, fake.commits);
    }

    @Test
    void quarantineAndCrossOccurrenceBindingsRefuseBeforeExecution() throws Exception {
        final var fake = new Fake();
        try (var session = fake.open();
                var borrowed = session.getConnection();
                var call =
                        borrowed.prepareCall(
                                "{call"
                                        + " stg.usp_stage_coleta_record(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
            call.setString(1, EXECUTION.toString());
            call.setString(21, "QUARANTINE");
            assertThrows(SQLException.class, call::addBatch);
            call.setString(21, "VALID");
            call.setString(1, UUID.randomUUID().toString());
            assertThrows(SQLException.class, call::addBatch);
        }
        assertEquals(0, fake.commits);
    }

    @Test
    void invalidPoliciesAndExpectedCountsAreRejectedOffline() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Limits(1201, 1000, 10, 60));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Limits(1200, 1001, 10, 60));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Limits(1200, 1000, 11, 60));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Limits(1200, 1000, 10, 61));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Expected(EXECUTION, 1, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRetainedSqlSession.Expected(EXECUTION, 0, 0, 1));
    }

    @Test
    void fullThousandRowsIncludeEveryBatchEntryAndExplicitCheckpointWithinCap() throws Exception {
        final var fake = new Fake();
        fake.genericRows = 1000;
        fake.typedRows = 1000;
        try (var session = fake.open();
                var borrowed = session.getConnection()) {
            for (int i = 0; i < 7; i++) {
                try (var call =
                        borrowed.prepareCall(
                                "{call ctl.usp_control_plane_heartbeat_lease(?,?,?,?)}")) {
                    call.setString(1, EXECUTION.toString());
                    call.execute();
                }
            }
            for (int i = 0; i < 10; i++) {
                try (var call =
                        borrowed.prepareCall(
                                "{call"
                                        + " stg.usp_stage_coleta_record(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
                    call.setString(1, EXECUTION.toString());
                    call.setString(21, "VALID");
                    call.setNull(22, java.sql.Types.NVARCHAR);
                    for (int row = 0; row < 100; row++) {
                        call.addBatch();
                    }
                    call.executeBatch();
                }
            }
            for (int i = 0; i < 19; i++) {
                session.checkpoint();
            }
            final var receipt =
                    session.finishPartial(
                            new TestRetainedSqlSession.Expected(EXECUTION, 1000, 1000, 1));
            assertEquals(1066, receipt.sqlExecutions());
        }
        assertEquals(1, fake.commits);
    }

    @Test
    void statementHandleRegistryRefusesSeventeenthAndReusesClosedSlot() throws Exception {
        final var fake = new Fake();
        final var handles = new ArrayList<CallableStatement>();
        try (var session = fake.open();
                var borrowed = session.getConnection()) {
            assertEquals(12, TestRetainedSqlContract.WRITE_TABLES.length);
            assertEquals(12, java.util.Set.of(TestRetainedSqlContract.WRITE_TABLES).size());
            assertEquals(8, TestRetainedSqlContract.CALLS.size());
            for (int i = 0; i < 16; i++) {
                handles.add(
                        borrowed.prepareCall(
                                "{call ctl.usp_control_plane_heartbeat_lease(?,?,?,?)}"));
            }
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () ->
                                    borrowed.prepareCall(
                                            "{call ctl.usp_control_plane_heartbeat_lease(?,?,?,?)}"));
            assertEquals("OPEN_STATEMENT_LIMIT", failure.code());
            handles.remove(0).close();
            handles.add(
                    borrowed.prepareCall("{call ctl.usp_control_plane_heartbeat_lease(?,?,?,?)}"));
            assertEquals(16, handles.size());
            for (final var handle : handles) {
                handle.close();
            }
        }
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
        assertEquals(1, fake.closes);
    }

    @Test
    void insufficientPostBudgetAndOpenStatementsRefuseBeforeCommit() throws Exception {
        final var fake = new Fake();
        try (var session =
                TestRetainedSqlSession.admitted(
                        fake.connection,
                        new TestRetainedSqlSession.Limits(24, 1000, 10, 60),
                        EXECUTION,
                        () -> 0L,
                        0)) {
            session.checkpoint();
            session.checkpoint();
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("COMMIT_POST_BUDGET", failure.code());
        }
        assertEquals(0, fake.commits);
        final var another = new Fake();
        try (var session = another.open();
                var borrowed = session.getConnection();
                var ignored =
                        borrowed.prepareCall(
                                "{call ctl.usp_control_plane_heartbeat_lease(?,?,?,?)}")) {
            assertFalse(ignored.isClosed());
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("OPEN_STATEMENTS_AT_COMMIT", failure.code());
        }
        assertEquals(0, another.commits);
    }
}
