package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.PinnedSampleSaJdbcConnector;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class PinnedSampleConnectorFailureTest {
    interface Attempt {
        Connection open(Properties properties) throws Exception;
    }

    private static Connection call(
            final Attempt attempt, final Supplier<PinnedSampleTlsScope> scope) throws Exception {
        final var type =
                Class.forName(
                        "br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession$SqlConnections");
        final var method =
                PinnedSampleSaJdbcConnector.class.getDeclaredMethod(
                        "openCredentials",
                        ShadowStorageProperties.class,
                        Properties.class,
                        type,
                        Supplier.class);
        method.setAccessible(true);
        final Object connector =
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, args) -> attempt.open((Properties) args[1]));
        final var credentials = new Properties();
        credentials.setProperty("user", "sa");
        credentials.setProperty("password", "  synthetic-only  ");
        try {
            return (Connection)
                    method.invoke(
                            null, PinnedSampleTlsTest.target(), credentials, connector, scope);
        } catch (final InvocationTargetException wrapper) {
            if (wrapper.getCause() instanceof Exception failure) {
                throw failure;
            }
            throw new AssertionError("UNEXPECTED_FAKE_CAUSE_TYPE");
        }
    }

    @Test
    void pinnedSessionKeepsTheSanitizedConnectorFailure() throws Exception {
        PinnedSampleTlsTest.opted(
                () -> {
                    final var typed =
                            assertThrows(
                                    PinnedSampleSaJdbcConnector.ConnectionFailure.class,
                                    () ->
                                            call(
                                                    props -> {
                                                        throw new SQLException(
                                                                "synthetic-sensitive",
                                                                "28000",
                                                                18456);
                                                    },
                                                    () ->
                                                            PinnedSampleTlsScope.enter(
                                                                    PinnedSampleTlsTest.target(),
                                                                    PinnedSampleTlsTest.FakeGuard
                                                                            ::new)));
                    final var session =
                            Class.forName(
                                    "br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession");
                    final var type = Class.forName(session.getName() + "$SqlConnections");
                    final var method =
                            session.getDeclaredMethod(
                                    "openPinnedSharedSampleSa",
                                    ShadowStorageProperties.class,
                                    SecretProvider.class,
                                    type);
                    method.setAccessible(true);
                    final Object connector =
                            Proxy.newProxyInstance(
                                    type.getClassLoader(),
                                    new Class<?>[] {type},
                                    (p, m, args) -> {
                                        throw typed;
                                    });
                    final var wrapper =
                            assertThrows(
                                    InvocationTargetException.class,
                                    () ->
                                            method.invoke(
                                                    null,
                                                    PinnedSampleTlsTest.target(),
                                                    (SecretProvider) key -> "synthetic-only",
                                                    connector));
                    assertSame(typed, wrapper.getCause());
                    assertTrue(typed.failureEvidence().authObserved());
                });
    }

    @Test
    void socketAndConnectionCloseFailuresRemainTypedAndUncertain() throws Exception {
        PinnedSampleTlsTest.opted(
                () -> {
                    final var closes = new AtomicInteger();
                    final PinnedSampleTlsTest.FakeGuard[] owner =
                            new PinnedSampleTlsTest.FakeGuard[1];
                    final PinnedSampleTlsScope[] scopes = new PinnedSampleTlsScope[1];
                    final Supplier<PinnedSampleTlsScope> factory =
                            () -> {
                                scopes[0] =
                                        PinnedSampleTlsScope.enter(
                                                PinnedSampleTlsTest.target(),
                                                s -> {
                                                    owner[0] =
                                                            new PinnedSampleTlsTest.FakeGuard(s) {
                                                                @Override
                                                                public synchronized void close()
                                                                        throws IOException {
                                                                    throw new IOException(
                                                                            "synthetic-close-sensitive");
                                                                }
                                                            };
                                                    return owner[0];
                                                });
                                return scopes[0];
                            };
                    final Connection connection =
                            (Connection)
                                    Proxy.newProxyInstance(
                                            Connection.class.getClassLoader(),
                                            new Class<?>[] {Connection.class},
                                            (p, m, args) -> {
                                                if (m.getName().equals("close")) {
                                                    closes.incrementAndGet();
                                                    throw new SQLException(
                                                            "synthetic-close-sensitive",
                                                            "08006",
                                                            -1);
                                                }
                                                throw new AssertionError("ONLY_CLOSE_ALLOWED");
                                            });
                    final var failure =
                            assertThrows(
                                    PinnedSampleSaJdbcConnector.ConnectionFailure.class,
                                    () ->
                                            call(
                                                    props -> {
                                                        final var socket =
                                                                new PinnedSampleSocketFactory()
                                                                        .createSocket();
                                                        socket.connect(
                                                                new InetSocketAddress(
                                                                        InetAddress.getByAddress(
                                                                                new byte[] {
                                                                                    127, 0, 0, 1
                                                                                }),
                                                                        1433),
                                                                5000);
                                                        assertEquals(
                                                                "  synthetic-only  ",
                                                                props.getProperty("password"));
                                                        assertEquals(
                                                                "0",
                                                                props.getProperty(
                                                                        "connectRetryCount"));
                                                        return connection; // Pin was not verified:
                                                        // admission must fail.
                                                    },
                                                    factory));
                    assertTrue(failure.failureEvidence().cleanupUncertain());
                    assertTrue(scopes[0].socketCloseUncertain());
                    assertTrue(failure.failureEvidence().suppressedEdges() >= 1);
                    assertTrue(failure.failureEvidence().descriptors().contains("08006"));
                    assertEquals(1, failure.getSuppressed().length);
                    assertEquals(1, closes.get());
                    assertEquals(1, owner[0].connects);
                    assertNull(failure.getCause());
                    assertFalse(failure.toString().contains("synthetic"));
                    assertThrows(IllegalStateException.class, PinnedSampleSocketFactory::new);
                });
    }

    @Test
    void nestedTlsNextSqlAndDriverCleanupArePreservedWithoutRawObjects() throws Exception {
        PinnedSampleTlsTest.opted(
                () -> {
                    final var count = new AtomicInteger();
                    final var driver = new SQLException("synthetic-sensitive", "08001", 0);
                    driver.initCause(new SSLHandshakeException("synthetic-sensitive"));
                    driver.setNextException(
                            new SQLException("synthetic-sensitive", "28000", 18456));
                    driver.addSuppressed(new IOException("synthetic-sensitive"));
                    final var failure =
                            assertThrows(
                                    PinnedSampleSaJdbcConnector.ConnectionFailure.class,
                                    () ->
                                            call(
                                                    props -> {
                                                        count.incrementAndGet();
                                                        throw driver;
                                                    },
                                                    () ->
                                                            PinnedSampleTlsScope.enter(
                                                                    PinnedSampleTlsTest.target(),
                                                                    PinnedSampleTlsTest.FakeGuard
                                                                            ::new)));
                    assertEquals(1, count.get());
                    assertEquals(4, failure.failureEvidence().nodes());
                    assertTrue(failure.tlsCauseObserved());
                    assertTrue(failure.failureEvidence().authObserved());
                    assertEquals(1, failure.failureEvidence().nextSqlEdges());
                    assertNull(failure.getCause());
                    assertFalse(failure.failureEvidence().toString().contains("synthetic"));
                });
    }
}
