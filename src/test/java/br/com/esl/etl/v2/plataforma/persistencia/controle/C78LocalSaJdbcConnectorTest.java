package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalConnectorFixture;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class C78LocalSaJdbcConnectorTest {
    private static final String[] FLAGS = {
        "c78.local.shadow.enabled", "c78.local.shadow.sql-pin.enabled"
    };
    private static final String PRIVATE = "synthetic-only-private-marker";
    private final Properties old = new Properties();

    @BeforeEach
    void enable() {
        for (final String name : FLAGS) {
            if (System.getProperty(name) != null) {
                old.setProperty(name, System.getProperty(name));
            }
            System.setProperty(name, "true");
        }
    }

    @AfterEach
    void restore() {
        for (final String name : FLAGS) {
            if (old.containsKey(name)) {
                System.setProperty(name, old.getProperty(name));
            } else {
                System.clearProperty(name);
            }
        }
    }

    private static C78LocalSqlTarget target() {
        return C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
    }

    private static void safe(final Throwable failure) {
        assertFalse(failure.toString().contains(PRIVATE));
        assertNull(failure.getCause());
        for (final Throwable suppressed : failure.getSuppressed()) {
            assertFalse(suppressed.toString().contains(PRIVATE));
            assertNull(suppressed.getCause());
        }
    }

    @Test
    void targetOptinsAndNestedScopeRejectBeforeProviderOrDriver() throws Exception {
        final var reads = new AtomicInteger();
        for (final String flag : FLAGS) {
            System.clearProperty(flag);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            C78LocalSaJdbcConnector.open(
                                    target(),
                                    key -> {
                                        reads.incrementAndGet();
                                        return PRIVATE;
                                    }));
            System.setProperty(flag, "true");
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        C78LocalSaJdbcConnector.open(
                                null,
                                key -> {
                                    reads.incrementAndGet();
                                    return PRIVATE;
                                }));
        try (var outer = C78LocalTlsScope.enter(target())) {
            final var failure =
                    assertThrows(
                            C78LocalSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    C78LocalSaJdbcConnector.open(
                                            target(),
                                            key -> {
                                                reads.incrementAndGet();
                                                return PRIVATE;
                                            },
                                            (url, fixed) -> {
                                                throw new AssertionError("NO_DRIVER_ATTEMPT");
                                            },
                                            () -> C78LocalTlsScope.enter(target())));
            assertEquals("SCOPE_CREATION_REFUSED", failure.pinReason());
            assertEquals("NOT_OBSERVED", outer.failureClass());
            safe(failure);
        }
        assertEquals(0, reads.get());
    }

    @Test
    void unavailableInvalidAndThrowingProvidersNeverEnterDriver() {
        final var attempts = new AtomicInteger();
        for (final String value : new String[] {null, " ", "x".repeat(4097)}) {
            final var failure =
                    assertThrows(
                            C78LocalSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    C78LocalSaJdbcConnector.open(
                                            target(),
                                            key -> value,
                                            (url, fixed) -> {
                                                attempts.incrementAndGet();
                                                throw new AssertionError("NO_DRIVER_ATTEMPT");
                                            },
                                            () -> C78LocalTlsScope.enter(target())));
            safe(failure);
        }
        final var failure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target(),
                                        key -> {
                                            throw new IllegalStateException(PRIVATE);
                                        },
                                        (url, fixed) -> {
                                            attempts.incrementAndGet();
                                            return null;
                                        },
                                        () -> C78LocalTlsScope.enter(target())));
        safe(failure);
        assertEquals(0, attempts.get());
    }

    @Test
    void fixedPropertiesAndCredentialReferencesAreClearedAfterDriverRefusal() {
        final var properties = new AtomicReference<Properties>();
        final var attempts = new AtomicInteger();
        final var failure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target(),
                                        key -> {
                                            assertEquals(SecretKey.SHADOW_SAMPLE_SA_PASSWORD, key);
                                            return PRIVATE;
                                        },
                                        (url, fixed) -> {
                                            attempts.incrementAndGet();
                                            properties.set(fixed);
                                            assertEquals(C78LocalSqlTarget.URL, url);
                                            assertFalse(url.contains(PRIVATE));
                                            assertEquals("sa", fixed.getProperty("user"));
                                            assertEquals(PRIVATE, fixed.getProperty("password"));
                                            assertEquals(
                                                    "0", fixed.getProperty("connectRetryCount"));
                                            assertEquals("5", fixed.getProperty("loginTimeout"));
                                            assertEquals(
                                                    "10000", fixed.getProperty("socketTimeout"));
                                            assertEquals(
                                                    "false",
                                                    fixed.getProperty(
                                                            "transparentNetworkIPResolution"));
                                            assertEquals(
                                                    "false",
                                                    fixed.getProperty("multiSubnetFailover"));
                                            assertEquals(
                                                    "TLSv1.2", fixed.getProperty("sslProtocol"));
                                            assertTrue(
                                                    fixed.getProperty("trustManagerClass")
                                                            .endsWith("C78LocalTrustManager"));
                                            assertTrue(
                                                    fixed.getProperty("socketFactoryClass")
                                                            .endsWith("C78LocalSocketFactory"));
                                            throw new SQLException(
                                                    PRIVATE,
                                                    "SECRET",
                                                    18456,
                                                    new javax.net.ssl.SSLHandshakeException(
                                                            PRIVATE));
                                        },
                                        () -> C78LocalTlsScope.enter(target())));
        assertEquals(1, attempts.get());
        assertTrue(properties.get().isEmpty());
        safe(failure);
    }

    @Test
    void returnedConnectionWithoutPeerAndPinIsClosedAndNeverAccepted() {
        for (final boolean closeFails : new boolean[] {false, true}) {
            final var closes = new AtomicInteger();
            final var fixedOwner = new AtomicReference<Properties>();
            final var connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (p, m, a) -> {
                                        if (m.getName().equals("close")) {
                                            closes.incrementAndGet();
                                            if (closeFails) {
                                                throw new SQLException(PRIVATE);
                                            }
                                        }
                                        return null;
                                    });
            final var failure =
                    assertThrows(
                            C78LocalSaJdbcConnector.ConnectionFailure.class,
                            () ->
                                    C78LocalSaJdbcConnector.open(
                                            target(),
                                            key -> PRIVATE,
                                            (url, fixed) -> {
                                                fixedOwner.set(fixed);
                                                return connection;
                                            },
                                            () -> C78LocalTlsScope.enter(target())));
            assertEquals(1, closes.get());
            assertEquals(closeFails, failure.cleanupUncertain());
            assertEquals(closeFails ? 1 : 0, failure.getSuppressed().length);
            assertTrue(fixedOwner.get().isEmpty());
            safe(failure);
        }
    }

    @Test
    void driverErrorsCannotExposeCredentialCauseAndStillClearProperties() {
        final var fixedOwner = new AtomicReference<Properties>();
        final var failure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target(),
                                        key -> PRIVATE,
                                        (url, fixed) -> {
                                            fixedOwner.set(fixed);
                                            throw new AssertionError(PRIVATE);
                                        },
                                        () -> C78LocalTlsScope.enter(target())));
        assertTrue(fixedOwner.get().isEmpty());
        safe(failure);
    }

    @Test
    void verifiedCallbackFixtureTransfersConnectionAndClearsCredentials() throws Exception {
        final var owner = new AtomicReference<Socket>();
        final var fixedOwner = new AtomicReference<Properties>();
        final var closes = new AtomicInteger();
        final var connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("close")) {
                                        closes.incrementAndGet();
                                        owner.get().close();
                                    }
                                    return null;
                                });
        final var result =
                C78LocalSaJdbcConnector.open(
                        target(),
                        key -> PRIVATE,
                        (url, fixed) -> {
                            fixedOwner.set(fixed);
                            return connection;
                        },
                        () -> C78LocalConnectorFixture.verified(owner));
        assertSame(connection, result);
        assertTrue(fixedOwner.get().isEmpty());
        assertFalse(owner.get().isClosed());
        result.close();
        assertEquals(1, closes.get());
        assertTrue(owner.get().isClosed());
    }
}
