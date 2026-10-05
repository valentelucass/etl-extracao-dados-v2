package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;

class PinnedSampleTlsTest {
    static final String[] FLAGS = {
        "shadow.local.integration.enabled",
        "shadow.local.integration.profile.active",
        "coletas6908.sample.enabled",
        "coletas6908.sample.sa.enabled",
        "coletas6908.sample.shared.enabled",
        "coletas6908.sample.sql-pin.enabled"
    };
    static final Clock VALID = Clock.fixed(Instant.parse("2026-10-04T22:00:00Z"), ZoneOffset.UTC);

    interface Action {
        void run() throws Exception;
    }

    static void opted(final Action action) throws Exception {
        final var old = new java.util.Properties();
        for (final String flag : FLAGS) {
            if (System.getProperty(flag) != null) {
                old.setProperty(flag, System.getProperty(flag));
            }
            System.setProperty(flag, "true");
        }
        try {
            action.run();
        } finally {
            for (final String flag : FLAGS) {
                if (old.containsKey(flag)) {
                    System.setProperty(flag, old.getProperty(flag));
                } else {
                    System.clearProperty(flag);
                }
            }
        }
    }

    static ShadowStorageProperties target() {
        return ShadowStorageProperties.enabledSampleSqlPin(
                ShadowSampleSqlPinPropertiesTest.URL, "HERMES325fac56a833");
    }

    static X509Certificate leaf() throws Exception {
        try (var stream =
                PinnedSampleTlsTest.class.getResourceAsStream(
                        "/tls/sql-sample-manual-pin-325fac56a833.cer")) {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(stream);
        }
    }

    static class FakeGuard extends PinnedSampleSocketFactory.GuardedSocket {
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        byte[] response = new byte[0];
        boolean connected;
        boolean closed;
        boolean remote;
        int connects;
        int streams;
        InputStream incoming;

        FakeGuard(final PinnedSampleTlsScope scope) {
            super(scope);
        }

        @Override
        void physicalConnect(final SocketAddress endpoint, final int timeout) {
            connects++;
            connected = true;
            assertEquals("/127.0.0.1:1433", endpoint.toString());
            assertTrue(timeout > 0 && timeout <= 10000);
        }

        @Override
        public InetAddress getInetAddress() {
            try {
                return InetAddress.getByAddress(
                        remote ? new byte[] {(byte) 192, 0, 2, 1} : new byte[] {127, 0, 0, 1});
            } catch (final IOException impossible) {
                throw new AssertionError(impossible);
            }
        }

        @Override
        public InetAddress getLocalAddress() {
            try {
                return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            } catch (final IOException impossible) {
                throw new AssertionError(impossible);
            }
        }

        @Override
        public int getPort() {
            return 1433;
        }

        @Override
        public int getLocalPort() {
            return 21456;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            requireVerified();
            streams++;
            if (incoming == null) {
                incoming = new ByteArrayInputStream(response);
            }
            return incoming;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            requireVerified();
            streams++;
            return written;
        }

        @Override
        public void setSoTimeout(final int value) {}

        @Override
        public int getSoTimeout() {
            return 10000;
        }

        @Override
        public void setTcpNoDelay(final boolean value) {}

        @Override
        public boolean getTcpNoDelay() {
            return true;
        }

        @Override
        public void setKeepAlive(final boolean value) {}

        @Override
        public boolean getKeepAlive() {
            return false;
        }

        @Override
        public void setSoLinger(final boolean value, final int seconds) {}

        @Override
        public int getSoLinger() {
            return -1;
        }

        @Override
        public int getReceiveBufferSize() {
            return 65536;
        }

        @Override
        public int getSendBufferSize() {
            return 65536;
        }

        @Override
        public boolean isBound() {
            return connected;
        }
    }

    static class FakeTls extends SSLSocket {
        final FakeGuard peer;

        FakeTls(final FakeGuard peer) {
            this.peer = peer;
        }

        @Override
        public InetAddress getInetAddress() {
            return peer.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return peer.getLocalAddress();
        }

        @Override
        public int getPort() {
            return peer.getPort();
        }

        @Override
        public int getLocalPort() {
            return peer.getLocalPort();
        }

        @Override
        public boolean isConnected() {
            return peer.connected;
        }

        @Override
        public boolean isClosed() {
            return peer.closed;
        }

        @Override
        public SSLSession getHandshakeSession() {
            return (SSLSession)
                    Proxy.newProxyInstance(
                            SSLSession.class.getClassLoader(),
                            new Class<?>[] {SSLSession.class},
                            (p, m, a) -> null);
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }

        @Override
        public void setEnabledCipherSuites(final String[] suites) {}

        @Override
        public String[] getSupportedProtocols() {
            return new String[0];
        }

        @Override
        public String[] getEnabledProtocols() {
            return new String[0];
        }

        @Override
        public void setEnabledProtocols(final String[] protocols) {}

        @Override
        public SSLSession getSession() {
            return getHandshakeSession();
        }

        @Override
        public void addHandshakeCompletedListener(final HandshakeCompletedListener listener) {}

        @Override
        public void removeHandshakeCompletedListener(final HandshakeCompletedListener listener) {}

        @Override
        public void startHandshake() {
            throw new AssertionError("NO_REAL_HANDSHAKE_IN_CALLBACK_FIXTURE");
        }

        @Override
        public void setUseClientMode(final boolean value) {}

        @Override
        public boolean getUseClientMode() {
            return true;
        }

        @Override
        public void setNeedClientAuth(final boolean value) {}

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(final boolean value) {}

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(final boolean value) {}

        @Override
        public boolean getEnableSessionCreation() {
            return true;
        }
    }

    @Test
    void absentPeerAndInvalidChainNeverAcceptAndCleanupRemainsObservable() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope =
                            PinnedSampleTlsScope.enter(
                                    target(),
                                    s -> {
                                        owner[0] = new FakeGuard(s);
                                        return owner[0];
                                    })) {
                        assertThrows(IOException.class, scope::acceptConnection);
                        final Socket socket = new PinnedSampleSocketFactory().createSocket();
                        socket.connect(
                                new InetSocketAddress(
                                        InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                                5000);
                        final var trust = new PinnedSampleTrustManager(VALID);
                        for (final X509Certificate[] chain :
                                new X509Certificate[][] {null, {}, {null}, {leaf(), leaf()}}) {
                            assertThrows(
                                    CertificateException.class,
                                    () ->
                                            trust.checkServerTrusted(
                                                    chain, "RSA", new FakeTls(owner[0])));
                            assertEquals("SINGLE_LEAF_REQUIRED", scope.failureClass());
                        }
                        owner[0].closed = true;
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        trust.checkServerTrusted(
                                                new X509Certificate[] {leaf()},
                                                "RSA",
                                                new FakeTls(owner[0])));
                        assertThrows(IOException.class, scope::acceptConnection);
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void fixedLeafAcceptsOnlyScopedVerifiedTlsSocket() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope =
                            PinnedSampleTlsScope.enter(
                                    target(),
                                    s -> {
                                        owner[0] = new FakeGuard(s);
                                        return owner[0];
                                    })) {
                        final Socket socket = new PinnedSampleSocketFactory().createSocket();
                        assertThrows(IOException.class, socket::getOutputStream);
                        socket.connect(
                                new InetSocketAddress(
                                        InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                                5000);
                        final var trust = new PinnedSampleTrustManager(VALID);
                        trust.checkServerTrusted(
                                new X509Certificate[] {leaf()}, "ECDHE_RSA", new FakeTls(owner[0]));
                        scope.acceptConnection();
                        assertEquals(0, trust.getAcceptedIssuers().length);
                        assertThrows(
                                IOException.class,
                                () -> new PinnedSampleSocketFactory().createSocket());
                    }
                    assertFalse(owner[0].closed);
                    owner[0].close();
                    assertEquals(1, owner[0].connects);
                    assertThrows(IllegalStateException.class, PinnedSampleTrustManager::new);
                });
    }

    @Test
    void validityAndMandatorySocketContextRefuseBeforeAcceptanceAndClose() throws Exception {
        opted(
                () -> {
                    for (final Instant now :
                            new Instant[] {
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2057-01-01T00:00:00Z")
                            }) {
                        final FakeGuard[] owner = new FakeGuard[1];
                        try (var scope =
                                PinnedSampleTlsScope.enter(
                                        target(),
                                        s -> {
                                            owner[0] = new FakeGuard(s);
                                            return owner[0];
                                        })) {
                            final Socket socket = new PinnedSampleSocketFactory().createSocket();
                            socket.connect(
                                    new InetSocketAddress(
                                            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}),
                                            1433),
                                    5000);
                            final var trust =
                                    new PinnedSampleTrustManager(Clock.fixed(now, ZoneOffset.UTC));
                            assertThrows(
                                    CertificateException.class,
                                    () ->
                                            trust.checkServerTrusted(
                                                    new X509Certificate[] {leaf()},
                                                    "RSA",
                                                    new FakeTls(owner[0])));
                            assertTrue(
                                    scope.failureClass().equals("LEAF_EXPIRED")
                                            || scope.failureClass().equals("LEAF_NOT_YET_VALID"));
                            assertThrows(IOException.class, scope::acceptConnection);
                        }
                        assertTrue(owner[0].closed);
                    }
                    try (var scope = PinnedSampleTlsScope.enter(target(), FakeGuard::new)) {
                        assertEquals("NOT_OBSERVED", scope.failureClass());
                        final var trust = new PinnedSampleTrustManager(VALID);
                        final X509Certificate[] c = {leaf()};
                        assertThrows(
                                CertificateException.class,
                                () -> trust.checkServerTrusted(c, "RSA"));
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        trust.checkServerTrusted(
                                                c, "RSA", (javax.net.ssl.SSLEngine) null));
                        assertThrows(
                                CertificateException.class,
                                () -> trust.checkServerTrusted(c, "RSA", (Socket) null));
                        assertThrows(
                                CertificateException.class,
                                () -> trust.checkClientTrusted(c, "RSA"));
                        assertThrows(
                                CertificateException.class,
                                () -> trust.checkClientTrusted(c, "RSA", (Socket) null));
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        trust.checkClientTrusted(
                                                c, "RSA", (javax.net.ssl.SSLEngine) null));
                        assertThrows(
                                IllegalStateException.class,
                                () -> PinnedSampleTlsScope.enter(target()));
                    }
                });
    }

    @Test
    void remoteMalformedOrSecondConnectCannotOpenStreams() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope =
                            PinnedSampleTlsScope.enter(
                                    target(),
                                    s -> {
                                        owner[0] = new FakeGuard(s);
                                        return owner[0];
                                    })) {
                        assertEquals("NOT_OBSERVED", scope.failureClass());
                        final Socket socket = new PinnedSampleSocketFactory().createSocket();
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
                                                        InetAddress.getByAddress(
                                                                new byte[] {127, 0, 0, 1}),
                                                        1444),
                                                5000));
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getByAddress(
                                                                new byte[] {127, 0, 0, 1}),
                                                        1433)));
                        assertEquals(0, owner[0].connects);
                        owner[0].remote = true;
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getByAddress(
                                                                new byte[] {127, 0, 0, 1}),
                                                        1433),
                                                5000));
                        assertTrue(owner[0].closed);
                        assertThrows(IOException.class, socket::getInputStream);
                        assertThrows(
                                IOException.class,
                                () ->
                                        socket.connect(
                                                new InetSocketAddress(
                                                        InetAddress.getByAddress(
                                                                new byte[] {127, 0, 0, 1}),
                                                        1433),
                                                5000));
                        assertEquals(0, owner[0].streams);
                        final var factory = new PinnedSampleSocketFactory();
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
                    }
                });
    }

    @Test
    void acceptedIssuerDiscoveryIsAlwaysEmptyAndDefensive() throws Exception {
        opted(
                () -> {
                    try (var scope = PinnedSampleTlsScope.enter(target(), FakeGuard::new)) {
                        final var trust = new PinnedSampleTrustManager();
                        final var first = trust.getAcceptedIssuers();
                        final var second = trust.getAcceptedIssuers();
                        assertEquals(0, first.length);
                        assertEquals(0, second.length);
                        assertFalse(first == second);
                        assertFalse(scope.socketCloseUncertain());
                    }
                });
    }
}
