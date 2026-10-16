package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

class C78LocalProfileBoundaryTest {
    @Test
    void targetRejectsEveryAliasPropertyCredentialAndPriorApproval() {
        assertEquals("C78_LOCAL_SHADOW_SQL_PIN_V1", C78LocalSqlTarget.PROFILE);
        for (final String url :
                new String[] {
                    C78LocalSqlTarget.URL.replace("localhost", "127.0.0.1"),
                    C78LocalSqlTarget.URL.replace("localhost", "remote"),
                    C78LocalSqlTarget.URL.replace("1433", "1434"),
                    C78LocalSqlTarget.URL.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA"),
                    C78LocalSqlTarget.URL + ";user=sa",
                    C78LocalSqlTarget.URL + ";password=synthetic",
                    C78LocalSqlTarget.URL + ";trustServerCertificate=true",
                    C78LocalSqlTarget.URL + ";encrypt=false",
                    C78LocalSqlTarget.URL + ";connectRetryCount=1"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new C78LocalSqlTarget(url, C78LocalSqlTarget.APPROVAL_REFERENCE));
        }
        for (final String approval :
                new String[] {null, "USER_20261005_E3_TEST_RETAINED", "HERMES325", ""}) {
            assertThrows(
                    IllegalArgumentException.class, () -> C78LocalSqlTarget.approved(approval));
        }
    }

    @Test
    void twoOwnFlagsDefaultFalseAndOldOptinsCannotEnableC78() throws Exception {
        final String[] names = {
            "c78.local.shadow.enabled",
            "c78.local.shadow.sql-pin.enabled",
            "test.retained.local.enabled",
            "test.retained.sql-pin.enabled",
            "shadow.sample.sql-pin.enabled"
        };
        final var old = new Properties();
        for (final String name : names) {
            if (System.getProperty(name) != null) {
                old.setProperty(name, System.getProperty(name));
            }
        }
        try {
            System.setProperty(names[2], "true");
            System.setProperty(names[3], "true");
            System.setProperty(names[4], "true");
            for (int mask = 0; mask < 3; mask++) {
                System.setProperty(names[0], Boolean.toString((mask & 1) != 0));
                System.setProperty(names[1], Boolean.toString((mask & 2) != 0));
                assertThrows(
                        IllegalStateException.class,
                        () -> C78LocalTlsScope.validate(C78LocalTlsTest.target()));
            }
            System.clearProperty(names[0]);
            System.clearProperty(names[1]);
            assertThrows(
                    IllegalStateException.class,
                    () -> C78LocalTlsScope.validate(C78LocalTlsTest.target()));
            C78LocalTlsTest.opted(() -> C78LocalTlsScope.validate(C78LocalTlsTest.target()));
        } finally {
            for (final String name : names) {
                if (old.containsKey(name)) {
                    System.setProperty(name, old.getProperty(name));
                } else {
                    System.clearProperty(name);
                }
            }
        }
    }

    @Test
    void globalTlsOverridesRefuseWithoutReadingStores() throws Exception {
        C78LocalTlsTest.opted(
                () -> {
                    for (final String name :
                            new String[] {
                                "javax.net.ssl.trustStore",
                                "javax.net.ssl.trustStoreType",
                                "javax.net.ssl.trustStorePassword",
                                "javax.net.ssl.trustStoreProvider",
                                "javax.net.ssl.keyStore",
                                "javax.net.ssl.keyStoreType",
                                "javax.net.ssl.keyStorePassword",
                                "javax.net.ssl.keyStoreProvider",
                                "javax.net.debug",
                                "java.security.properties"
                            }) {
                        final String old = System.getProperty(name);
                        try {
                            System.setProperty(name, "synthetic-invalid");
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> C78LocalTlsScope.validate(C78LocalTlsTest.target()));
                        } finally {
                            if (old == null) {
                                System.clearProperty(name);
                            } else {
                                System.setProperty(name, old);
                            }
                        }
                    }
                });
    }

    @Test
    void nestedScopesDoNotReplaceOwnerAndClosedFactoriesCannotClaim() throws Exception {
        C78LocalTlsTest.opted(
                () -> {
                    final C78LocalSocketFactory factory;
                    try (var scope = C78LocalTlsScope.enter(C78LocalTlsTest.target())) {
                        factory = new C78LocalSocketFactory();
                        assertThrows(
                                IllegalStateException.class,
                                () -> C78LocalTlsScope.enter(C78LocalTlsTest.target()));
                        assertEquals("NOT_OBSERVED", scope.failureClass());
                    }
                    assertThrows(IOException.class, factory::createSocket);
                    assertThrows(IllegalStateException.class, C78LocalTrustManager::new);
                    try (var next = C78LocalTlsScope.enter(C78LocalTlsTest.target())) {
                        assertEquals("NOT_OBSERVED", next.failureClass());
                    }
                });
    }

    @Test
    void invalidEndpointsAndUnboundedTimeoutNeverReachPhysicalConnect() throws Exception {
        C78LocalTlsTest.opted(
                () -> {
                    final C78LocalTlsTest.FakeGuard[] owner = new C78LocalTlsTest.FakeGuard[1];
                    try (var scope =
                            C78LocalTlsScope.enter(
                                    C78LocalTlsTest.target(),
                                    s -> {
                                        owner[0] = new C78LocalTlsTest.FakeGuard(s);
                                        return owner[0];
                                    })) {
                        final var factory = new C78LocalSocketFactory();
                        final var socket = factory.createSocket();
                        assertThrows(IOException.class, socket::getOutputStream);
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                InetSocketAddress.createUnresolved(
                                                        "localhost", 1433),
                                                5000));
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getByAddress(
                                                                new byte[] {(byte) 192, 0, 2, 1}),
                                                        1433),
                                                5000));
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getLoopbackAddress(), 1434),
                                                5000));
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getLoopbackAddress(), 1433),
                                                0));
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getLoopbackAddress(), 1433)));
                        assertThrows(
                                IOException.class, () -> factory.createSocket("localhost", 1433));
                        assertThrows(
                                IOException.class,
                                () -> factory.createSocket(InetAddress.getLoopbackAddress(), 1433));
                        assertThrows(
                                IOException.class,
                                () ->
                                        factory.createSocket(
                                                "localhost",
                                                1433,
                                                InetAddress.getLoopbackAddress(),
                                                0));
                        assertThrows(
                                IOException.class,
                                () ->
                                        factory.createSocket(
                                                InetAddress.getLoopbackAddress(),
                                                1433,
                                                InetAddress.getLoopbackAddress(),
                                                0));
                        assertEquals(0, owner[0].connects);
                        assertThrows(IOException.class, scope::acceptConnection);
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void physicalFailureConsumesSingleAttemptAndKnownClosure() throws Exception {
        C78LocalTlsTest.opted(
                () -> {
                    final var attempts = new AtomicInteger();
                    try (var scope =
                            C78LocalTlsScope.enter(
                                    C78LocalTlsTest.target(),
                                    s ->
                                            new C78LocalSocketFactory.GuardedSocket(s) {
                                                @Override
                                                void physicalConnect(
                                                        final java.net.SocketAddress endpoint,
                                                        final int timeout)
                                                        throws IOException {
                                                    attempts.incrementAndGet();
                                                    throw new IOException("synthetic-only");
                                                }
                                            })) {
                        final var socket = new C78LocalSocketFactory().createSocket();
                        final var address =
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 1433);
                        assertThrows(IOException.class, () -> socket.connect(address, 5000));
                        assertTrue(socket.isClosed());
                        assertThrows(IOException.class, () -> socket.connect(address, 5000));
                        assertEquals(1, attempts.get());
                        assertFalse(scope.socketCloseUncertain());
                    }
                });
    }

    @Test
    void onlyExistingPinnedDriverBytesAreAdmittedOffline() throws Exception {
        final var driver = C78LocalDriverPolicy.load();
        assertEquals(12, driver.getMajorVersion());
        assertEquals(8, driver.getMinorVersion());
        assertThrows(IllegalStateException.class, () -> C78LocalDriverPolicy.validate(null));
    }
}
