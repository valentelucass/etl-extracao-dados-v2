package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * BANK-FINAL-ADMISSION-052 and BANK-COMMIT-POSSIBLE-052: a final receipt cannot hide expiry or a
 * possible commit that requires independent occurrence reconciliation.
 */
class TestRetainedSqlSessionFinalReadbackTest {
    private static final UUID OCCURRENCE = UUID.fromString("39306bb7-3794-4a87-8242-94037fac689c");
    private static final TestRetainedSqlSession.Expected EXPECTED =
            new TestRetainedSqlSession.Expected(OCCURRENCE, 1, 1, 1);

    private static Object invoke(final Object target, final Method method, final Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static final class Fixture {
        final TestRetainedSqlSessionTest.Fake jdbc = new TestRetainedSqlSessionTest.Fake();
        long finalElapsed;
        int finalResultCloses;
        int finalStatementCloses;
        boolean expireOnFinalStatementClose;
        boolean admissionValid = true;
        boolean revokeOnFinalClose;
        int admissionChecks;
        final Connection connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    final Object delegated = invoke(jdbc.connection, method, args);
                                    if (!method.getName().equals("prepareStatement")
                                            || !TestRetainedSqlContract.READBACK.equals(args[0])) {
                                        return delegated;
                                    }
                                    return Proxy.newProxyInstance(
                                            PreparedStatement.class.getClassLoader(),
                                            new Class<?>[] {PreparedStatement.class},
                                            (statement, operation, values) -> {
                                                final Object result =
                                                        invoke(delegated, operation, values);
                                                if (operation.getName().equals("close")
                                                        && jdbc.commits > 0) {
                                                    finalStatementCloses++;
                                                    if (expireOnFinalStatementClose) {
                                                        jdbc.clock = TimeUnit.SECONDS.toNanos(60);
                                                    }
                                                }
                                                if (!operation.getName().equals("executeQuery")
                                                        || jdbc.commits == 0) {
                                                    return result;
                                                }
                                                return Proxy.newProxyInstance(
                                                        ResultSet.class.getClassLoader(),
                                                        new Class<?>[] {ResultSet.class},
                                                        (rows, read, columns) -> {
                                                            final Object row =
                                                                    invoke(result, read, columns);
                                                            if (read.getName().equals("close")) {
                                                                finalResultCloses++;
                                                                jdbc.clock = finalElapsed;
                                                                if (revokeOnFinalClose) {
                                                                    admissionValid = false;
                                                                }
                                                            }
                                                            return row;
                                                        });
                                            });
                                });

        TestRetainedSqlSession open() throws Exception {
            return TestRetainedSqlSession.admitted(
                    connection,
                    new TestRetainedSqlSession.Limits(1200, 1000, 10, 60),
                    OCCURRENCE,
                    () -> jdbc.clock,
                    0);
        }

        void assertCurrentAdmission() {
            admissionChecks++;
            if (!admissionValid) {
                throw new IllegalStateException("synthetic private admission cause");
            }
        }

        void assertKnownCommitClosed(final TestRetainedSqlSession session) throws Exception {
            session.close();
            assertTrue(session.committed());
            assertFalse(session.commitUncertain());
            assertTrue(session.closeObserved());
            assertEquals(1, jdbc.commits);
            assertEquals(0, jdbc.rollbacks);
            assertEquals(1, jdbc.closes);
            assertEquals(1, finalResultCloses);
        }
    }

    @Test
    void receiptIsAllowedOnlyWhileDeadlineStillValidAfterFinalResultClose() throws Exception {
        final var fixture = new Fixture();
        fixture.finalElapsed = TimeUnit.SECONDS.toNanos(60) - 1;
        final var session = fixture.open();
        try (session) {
            assertTrue(session.finishPartial(EXPECTED).postCommitReadback());
        }
        fixture.assertKnownCommitClosed(session);
    }

    @Test
    void exactDeadlineDuringFinalResultCloseRefusesReceiptAndPreservesKnownCommit()
            throws Exception {
        assertFinalDeadlineRefusal(TimeUnit.SECONDS.toNanos(60));
    }

    @Test
    void expiredDeadlineDuringFinalResultCloseRefusesReceiptAndPreservesKnownCommit()
            throws Exception {
        assertFinalDeadlineRefusal(TimeUnit.SECONDS.toNanos(61));
    }

    @Test
    void deadlineDuringFinalStatementCloseRefusesReceiptAndPreservesKnownCommit() throws Exception {
        final var fixture = new Fixture();
        fixture.expireOnFinalStatementClose = true;
        final var session = fixture.open();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("DEADLINE", failure.code());
            assertEquals(1, fixture.finalStatementCloses);
            final long executions = session.sqlExecutions();
            assertThrows(
                    TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
            assertEquals(executions, session.sqlExecutions());
        }
        fixture.assertKnownCommitClosed(session);
    }

    private static void assertFinalDeadlineRefusal(final long finalElapsed) throws Exception {
        final var fixture = new Fixture();
        fixture.finalElapsed = finalElapsed;
        final var session = fixture.open();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED));
            assertEquals("DEADLINE", failure.code());
            assertTrue(session.committed());
            assertFalse(session.commitUncertain());
            final long executions = session.sqlExecutions();
            assertThrows(
                    TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
            assertEquals(executions, session.sqlExecutions());
        }
        fixture.assertKnownCommitClosed(session);
    }

    @Test
    void validAdmissionIsRecheckedAfterFinalResultCloseBeforeReceipt() throws Exception {
        final var fixture = new Fixture();
        final var session = fixture.open();
        try (session) {
            assertTrue(
                    session.finishPartial(EXPECTED, fixture::assertCurrentAdmission).committed());
            assertEquals(3, fixture.admissionChecks);
        }
        fixture.assertKnownCommitClosed(session);
    }

    @Test
    void admissionRevokedOnFinalCloseRefusesReceiptWithoutCompensatingKnownCommit()
            throws Exception {
        final var fixture = new Fixture();
        fixture.revokeOnFinalClose = true;
        final var session = fixture.open();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED, fixture::assertCurrentAdmission));
            assertEquals("ADMISSION_REVOKED", failure.code());
            assertFalse(failure.toString().contains("private"));
            assertEquals(3, fixture.admissionChecks);
            assertTrue(session.committed());
            assertFalse(session.commitUncertain());
        }
        fixture.assertKnownCommitClosed(session);
    }

    @Test
    void admissionCheckThatCrossesDeadlineCannotReturnReceipt() throws Exception {
        final var fixture = new Fixture();
        final var session = fixture.open();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () ->
                                    session.finishPartial(
                                            EXPECTED,
                                            () -> {
                                                fixture.assertCurrentAdmission();
                                                if (fixture.finalResultCloses == 1) {
                                                    fixture.jdbc.clock =
                                                            TimeUnit.SECONDS.toNanos(60);
                                                }
                                            }));
            assertEquals("DEADLINE", failure.code());
        }
        fixture.assertKnownCommitClosed(session);
    }

    @Test
    void precommitAdmissionRefusalRollsBackOnlyOwnedTransactionAndClosesOnce() throws Exception {
        final var fixture = new Fixture();
        fixture.admissionValid = false;
        final var session = fixture.open();
        final long before = session.sqlExecutions();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED, fixture::assertCurrentAdmission));
            assertEquals("ADMISSION_REVOKED", failure.code());
            assertEquals(before, session.sqlExecutions());
            assertFalse(session.committed());
            assertFalse(session.commitUncertain());
        }
        session.close();
        assertEquals(0, fixture.jdbc.commits);
        assertEquals(1, fixture.jdbc.rollbacks);
        assertEquals(1, fixture.jdbc.closes);
    }

    @Test
    void uncertainCommitRemainsUncertainAndCannotReplayOrReturnReceipt() throws Exception {
        final var fixture = new Fixture();
        fixture.jdbc.failCommit = true;
        final var session = fixture.open();
        try (session) {
            final var failure =
                    assertThrows(
                            TestRetainedSqlSession.Failure.class,
                            () -> session.finishPartial(EXPECTED, fixture::assertCurrentAdmission));
            assertEquals("COMMIT_UNCERTAIN", failure.code());
            assertTrue(session.commitUncertain());
            assertFalse(session.committed());
            final long executions = session.sqlExecutions();
            assertThrows(
                    TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
            assertEquals(executions, session.sqlExecutions());
            assertEquals(1, fixture.jdbc.commits);
        }
        session.close();
        assertTrue(session.commitUncertain());
        assertTrue(session.closeObserved());
        assertEquals(1, fixture.jdbc.commits);
        assertEquals(0, fixture.jdbc.rollbacks);
        assertEquals(1, fixture.jdbc.closes);
        assertEquals(0, fixture.finalResultCloses);
    }

    @Test
    void unknownCloseAfterKnownCommitDoesNotInventCommitUncertaintyOrRepeatClose()
            throws Exception {
        final var fixture = new Fixture();
        final var session = fixture.open();
        assertTrue(session.finishPartial(EXPECTED).committed());
        fixture.jdbc.failClose = true;
        final var failure = assertThrows(TestRetainedSqlSession.Failure.class, session::close);
        assertEquals("CLOSE_UNCERTAIN", failure.code());
        assertTrue(session.committed());
        assertFalse(session.commitUncertain());
        assertFalse(session.closeObserved());
        session.close();
        assertEquals(1, fixture.jdbc.commits);
        assertEquals(0, fixture.jdbc.rollbacks);
        assertEquals(1, fixture.jdbc.closes);
        assertThrows(TestRetainedSqlSession.Failure.class, () -> session.finishPartial(EXPECTED));
    }
}
