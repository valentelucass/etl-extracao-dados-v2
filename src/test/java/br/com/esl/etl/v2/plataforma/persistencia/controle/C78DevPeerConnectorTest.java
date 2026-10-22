package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerConnectorFixture;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class C78DevPeerConnectorTest {
    private String enabled;
    private String pin;

    @BeforeEach
    void enable() {
        enabled = System.getProperty("c78.local.shadow.enabled");
        pin = System.getProperty("c78.local.shadow.sql-pin.enabled");
        System.setProperty("c78.local.shadow.enabled", "true");
        System.setProperty("c78.local.shadow.sql-pin.enabled", "true");
    }

    @AfterEach
    void restore() {
        if (enabled == null) {
            System.clearProperty("c78.local.shadow.enabled");
        } else {
            System.setProperty("c78.local.shadow.enabled", enabled);
        }
        if (pin == null) {
            System.clearProperty("c78.local.shadow.sql-pin.enabled");
        } else {
            System.setProperty("c78.local.shadow.sql-pin.enabled", pin);
        }
    }

    private static C78LocalSqlTarget target() {
        return C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
    }

    @Test
    void devOverloadAndCatalogCannotOmitNativeProof() {
        assertThrows(
                NullPointerException.class,
                () ->
                        C78LocalSaJdbcConnector.openDev(
                                target(),
                                key -> {
                                    throw new AssertionError("PROVIDER_MUST_NOT_RUN");
                                },
                                null));
        assertThrows(NullPointerException.class, () -> new C78DevCatalogInspector(null));
    }

    @Test
    void nativeRefusalClosesKnownSocketAndClearsSyntheticCredentialReferences() {
        final var fixture = new C78DevPeerConnectorFixture();
        final var fixed = new AtomicReference<Properties>();
        final var failure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target(),
                                        key -> UUID.randomUUID().toString(),
                                        (url, properties) -> {
                                            fixed.set(properties);
                                            try {
                                                fixture.connectAndTouchStreams();
                                            } catch (final IOException rejected) {
                                                throw new SQLException(
                                                        "UNTRUSTED_DRIVER_DETAIL", rejected);
                                            }
                                            throw new AssertionError("DRIVER_AFTER_NATIVE_REFUSAL");
                                        },
                                        () ->
                                                fixture.enter(
                                                        target(),
                                                        tuple -> {
                                                            throw new IOException(
                                                                    "UNTRUSTED_NATIVE_DETAIL");
                                                        })));
        assertEquals("DEV_PEER_PROOF_REFUSED", failure.pinReason());
        assertFalse(failure.cleanupUncertain());
        assertNull(failure.getCause());
        assertTrue(fixed.get().isEmpty());
        assertEquals(0, fixture.streams);
        assertTrue(fixture.socket.isClosed());
    }

    @Test
    void nativeRefusalWithUncertainSocketCloseRemainsUncertain() {
        final var fixture = new C78DevPeerConnectorFixture();
        fixture.closeFails = true;
        final var failure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target(),
                                        key -> UUID.randomUUID().toString(),
                                        (url, properties) -> {
                                            try {
                                                fixture.connectAndTouchStreams();
                                            } catch (final IOException rejected) {
                                                throw new SQLException(
                                                        "UNTRUSTED_DRIVER_DETAIL", rejected);
                                            }
                                            throw new AssertionError("DRIVER_AFTER_NATIVE_REFUSAL");
                                        },
                                        () ->
                                                fixture.enter(
                                                        target(),
                                                        tuple -> {
                                                            throw new IOException(
                                                                    "UNTRUSTED_NATIVE_DETAIL");
                                                        })));
        assertTrue(failure.cleanupUncertain());
        assertEquals(0, fixture.streams);
    }

    @Test
    void syntheticNativePermitStillRequiresPinAndTransfersOnlyOneConnection() throws Exception {
        final var fixture = new C78DevPeerConnectorFixture();
        final var fixed = new AtomicReference<Properties>();
        final var closed = new java.util.concurrent.atomic.AtomicBoolean();
        final var connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) {
                                        closed.set(true);
                                        return null;
                                    }
                                    throw new AssertionError("NO_SQL_METHOD");
                                });
        final var actual =
                C78LocalSaJdbcConnector.open(
                        target(),
                        key -> UUID.randomUUID().toString(),
                        (url, properties) -> {
                            fixed.set(properties);
                            try {
                                fixture.connectAndTouchStreams();
                            } catch (final IOException impossible) {
                                throw new SQLException("SYNTHETIC_CONNECT_REFUSED");
                            }
                            C78DevPeerConnectorFixture.markSyntheticPinOnly();
                            return connection;
                        },
                        () -> fixture.enter(target(), tuple -> assertEquals(0, fixture.streams)));
        assertSame(connection, actual);
        assertTrue(fixed.get().isEmpty());
        assertFalse(closed.get());
        actual.close();
        fixture.socket.close();
        assertTrue(closed.get());
        assertTrue(fixture.socket.isClosed());
    }
}
