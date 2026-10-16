package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.TestRetainedSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.TestRetainedTlsScope;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Connector injection only: no driver, socket, SQL, real provider or server. */
class TestRetainedSaJdbcConnectorTest {
    private static final String[] FLAGS = {
        "test.retained.local.enabled", "test.retained.sql-pin.enabled"
    };
    private static final String PRIVATE = "synthetic private failure text";
    private final Properties previous = new Properties();

    @BeforeEach
    void enableOwnProfile() {
        for (final String flag : FLAGS) {
            if (System.getProperty(flag) != null) {
                previous.setProperty(flag, System.getProperty(flag));
            }
            System.setProperty(flag, "true");
        }
    }

    @AfterEach
    void restoreProfile() {
        for (final String flag : FLAGS) {
            if (previous.containsKey(flag)) {
                System.setProperty(flag, previous.getProperty(flag));
            } else {
                System.clearProperty(flag);
            }
        }
    }

    private static TestRetainedSqlTarget target() {
        return TestRetainedSqlTarget.approved(TestRetainedSqlTarget.APPROVAL_REFERENCE);
    }

    private static Properties credentials() {
        final var credentials = new Properties();
        credentials.setProperty("user", "sa");
        credentials.setProperty("password", "synthetic-only");
        return credentials;
    }

    @Test
    void scopeCreationFailureIsSanitizedBeforeAnyDriverAttempt() {
        for (final int mode : new int[] {0, 1, 2}) {
            final var attempts = new AtomicInteger();
            final var scopeAttempts = new AtomicInteger();
            final var failure =
                    assertThrows(
                            TestRetainedSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    TestRetainedSaJdbcConnector.openCredentials(
                                            target(),
                                            credentials(),
                                            (url, fixed) -> {
                                                attempts.incrementAndGet();
                                                throw new AssertionError("driver must not start");
                                            },
                                            () -> {
                                                scopeAttempts.incrementAndGet();
                                                if (mode == 1) {
                                                    throw new LinkageError(PRIVATE);
                                                }
                                                if (mode == 2) {
                                                    return null;
                                                }
                                                throw new IllegalStateException(PRIVATE);
                                            }));
            assertEquals(0, attempts.get());
            assertEquals(1, scopeAttempts.get());
            assertEquals("SCOPE_CREATION_REFUSED", failure.pinReason());
            assertEquals(
                    "TEST_RETAINED_SQL_PIN_CONNECT_FAILED_SCOPE_CREATION_REFUSED",
                    failure.getMessage());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains(PRIVATE));
            assertFalse(failure.failureEvidence().toString().contains(PRIVATE));
            assertFalse(failure.failureEvidence().cleanupUncertain());
        }
    }

    @Test
    void nestedScopeRefusalDoesNotCloseOrReplaceTheExistingScope() throws Exception {
        try (var outer = TestRetainedTlsScope.enter(target())) {
            final var failure =
                    assertThrows(
                            TestRetainedSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    TestRetainedSaJdbcConnector.openCredentials(
                                            target(),
                                            credentials(),
                                            (url, fixed) -> {
                                                throw new AssertionError("driver must not start");
                                            },
                                            () -> TestRetainedTlsScope.enter(target())));
            assertEquals("SCOPE_CREATION_REFUSED", failure.pinReason());
            assertEquals("NOT_OBSERVED", outer.failureClass());
            assertThrows(IllegalStateException.class, () -> TestRetainedTlsScope.enter(target()));
        }
        try (var next = TestRetainedTlsScope.enter(target())) {
            assertEquals("NOT_OBSERVED", next.failureClass());
        }
    }

    @Test
    void optinAndTargetRefuseBeforeProviderRead() {
        final var providerReads = new AtomicInteger();
        System.clearProperty(FLAGS[0]);
        assertThrows(
                IllegalStateException.class,
                () ->
                        TestRetainedSaJdbcConnector.open(
                                target(),
                                key -> {
                                    providerReads.incrementAndGet();
                                    throw new AssertionError(PRIVATE);
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TestRetainedSaJdbcConnector.open(
                                null,
                                key -> {
                                    providerReads.incrementAndGet();
                                    throw new AssertionError(PRIVATE);
                                }));
        assertEquals(0, providerReads.get());
    }

    @Test
    void absentBlankOversizedAndThrowingProvidersRefuseWithoutDriverOrPrivateCause() {
        for (final String value : new String[] {null, " ", "x".repeat(4097)}) {
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> TestRetainedSaJdbcConnector.open(target(), key -> value));
            assertEquals("TEST_RETAINED_SQL_PIN_SECRET_UNAVAILABLE", failure.getMessage());
            assertNull(failure.getCause());
        }
        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                TestRetainedSaJdbcConnector.open(
                                        target(),
                                        key -> {
                                            throw new AssertionError(PRIVATE);
                                        }));
        assertFalse(failure.toString().contains(PRIVATE));
        assertNull(failure.getCause());
    }

    @Test
    void wrongAccountAndBlankPasswordRefuseBeforeScopeOrDriver() {
        for (final boolean wrongAccount : new boolean[] {true, false}) {
            final var credentials = credentials();
            credentials.setProperty(wrongAccount ? "user" : "password", " ");
            final var calls = new AtomicInteger();
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            TestRetainedSaJdbcConnector.openCredentials(
                                    target(),
                                    credentials,
                                    (url, fixed) -> {
                                        calls.incrementAndGet();
                                        throw new AssertionError(PRIVATE);
                                    },
                                    () -> {
                                        calls.incrementAndGet();
                                        throw new AssertionError(PRIVATE);
                                    }));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void typedTlsFailureIsSanitizedAndPrivateDriverCopyClearedWithoutRetry() throws Exception {
        final var supplied = credentials();
        supplied.setProperty("trustServerCertificate", "true");
        supplied.setProperty("connectRetryCount", "9");
        final var driverCopy = new AtomicReference<Properties>();
        final var attempts = new AtomicInteger();
        final var failure =
                assertThrows(
                        TestRetainedSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                TestRetainedSaJdbcConnector.openCredentials(
                                        target(),
                                        supplied,
                                        (url, fixed) -> {
                                            attempts.incrementAndGet();
                                            driverCopy.set(fixed);
                                            assertNotSame(supplied, fixed);
                                            assertEquals(TestRetainedSqlTarget.URL, url);
                                            assertNull(fixed.getProperty("trustServerCertificate"));
                                            assertEquals(
                                                    "0", fixed.getProperty("connectRetryCount"));
                                            assertEquals("5", fixed.getProperty("loginTimeout"));
                                            assertEquals(
                                                    "10000", fixed.getProperty("socketTimeout"));
                                            throw new SQLException(
                                                    PRIVATE,
                                                    "08001",
                                                    18456,
                                                    new SSLHandshakeException(PRIVATE));
                                        },
                                        () -> TestRetainedTlsScope.enter(target())));
        assertEquals(1, attempts.get());
        assertTrue(driverCopy.get().isEmpty());
        assertFalse(supplied.isEmpty());
        assertEquals("08001", failure.getSQLState());
        assertEquals(18456, failure.getErrorCode());
        assertEquals("NOT_OBSERVED", failure.pinReason());
        assertTrue(failure.tlsCauseObserved());
        assertTrue(failure.failureEvidence().authObserved());
        assertFalse(failure.failureEvidence().cleanupUncertain());
        assertFalse(failure.failureEvidence().toString().contains(PRIVATE));
        assertFalse(failure.toString().contains(PRIVATE));
        assertNull(failure.getCause());
        try (var restored = TestRetainedTlsScope.enter(target())) {
            assertEquals("NOT_OBSERVED", restored.failureClass());
        }
    }

    @Test
    void invalidSqlStateAndUncheckedDriverFailuresCannotExposeDriverText() {
        for (final int mode : new int[] {0, 1, 2}) {
            final var copied = new AtomicReference<Properties>();
            final var failure =
                    assertThrows(
                            TestRetainedSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    TestRetainedSaJdbcConnector.openCredentials(
                                            target(),
                                            credentials(),
                                            (url, fixed) -> {
                                                copied.set(fixed);
                                                if (mode == 0) {
                                                    throw new SQLException(PRIVATE, PRIVATE, 912);
                                                }
                                                if (mode == 1) {
                                                    throw new IllegalStateException(PRIVATE);
                                                }
                                                throw new LinkageError(PRIVATE);
                                            },
                                            () -> TestRetainedTlsScope.enter(target())));
            assertNull(failure.getSQLState());
            assertEquals(mode == 0 ? 912 : 0, failure.getErrorCode());
            assertNull(failure.getCause());
            assertFalse(failure.tlsCauseObserved());
            assertFalse(failure.toString().contains(PRIVATE));
            assertTrue(copied.get().isEmpty());
        }
    }

    @Test
    void driverConnectionWithoutPeerProofIsClosedAndCleanupUncertaintyIsExplicit() {
        for (final boolean failedClose : new boolean[] {false, true}) {
            final var closes = new AtomicInteger();
            final var supplied = credentials();
            final Connection connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (!method.getName().equals("close")) {
                                            throw new AssertionError("only cleanup is allowed");
                                        }
                                        closes.incrementAndGet();
                                        if (failedClose) {
                                            throw new SQLException(PRIVATE);
                                        }
                                        return null;
                                    });
            final var failure =
                    assertThrows(
                            TestRetainedSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    TestRetainedSaJdbcConnector.openCredentials(
                                            target(),
                                            supplied,
                                            (url, fixed) -> connection,
                                            () -> TestRetainedTlsScope.enter(target())));
            assertEquals(1, closes.get());
            assertEquals(failedClose, failure.failureEvidence().cleanupUncertain());
            assertEquals(failedClose ? 1 : 0, failure.getSuppressed().length);
            if (failedClose) {
                assertEquals(
                        "TEST_RETAINED_SQL_PIN_CLEANUP_CLOSE_UNCERTAIN",
                        failure.getSuppressed()[0].getMessage());
            }
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains(PRIVATE));
            assertFalse(failure.failureEvidence().toString().contains(PRIVATE));
        }
    }

    @Test
    void publicSessionRejectsAliasBeforeProviderAndValidScopeStillRequiresSecret() {
        final var reads = new AtomicInteger();
        final var limits = new TestRetainedSqlSession.Limits(1200, 1000, 10, 60);
        final var occurrence = UUID.fromString("9bca53fa-2c4d-4776-ad8c-a45124e54280");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TestRetainedSqlSession.open(
                                target(),
                                key -> {
                                    reads.incrementAndGet();
                                    return null;
                                },
                                limits,
                                occurrence,
                                "OLD_SAMPLE_ALIAS",
                                TestRetainedSqlContract.TENANT));
        assertEquals(0, reads.get());
        assertThrows(
                IllegalStateException.class,
                () ->
                        TestRetainedSqlSession.open(
                                target(),
                                key -> {
                                    reads.incrementAndGet();
                                    return null;
                                },
                                limits,
                                occurrence,
                                TestRetainedSqlContract.SOURCE,
                                TestRetainedSqlContract.TENANT));
        assertEquals(1, reads.get());
    }
}
