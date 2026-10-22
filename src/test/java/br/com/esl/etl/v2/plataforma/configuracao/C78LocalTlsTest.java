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
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;

class C78LocalTlsTest {
    static final String[] FLAGS = {"c78.local.shadow.enabled", "c78.local.shadow.sql-pin.enabled"};
    static final Clock VALID = Clock.fixed(Instant.parse("2026-10-12T18:00:00Z"), ZoneOffset.UTC);

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

    static C78LocalSqlTarget target() {
        return C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
    }

    static X509Certificate leaf() throws Exception {
        return certificate("/tls/C78LocalCurrent.der");
    }

    static X509Certificate certificate(final String resource) throws Exception {
        try (var stream = C78LocalTlsTest.class.getResourceAsStream(resource)) {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(stream);
        }
    }

    static class FakeGuard extends C78LocalSocketFactory.GuardedSocket {
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        byte[] response = new byte[0];
        boolean connected;
        boolean closed;
        boolean remote;
        int connects;
        int streams;
        InputStream incoming;

        FakeGuard(final C78LocalTlsScope scope) {
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
        String protocol = "TLSv1.2";
        String cipher = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
        boolean sessionMissing;
        int portDelta;

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
            return peer.getLocalPort() + portDelta;
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
            if (sessionMissing) {
                return null;
            }
            return (SSLSession)
                    Proxy.newProxyInstance(
                            SSLSession.class.getClassLoader(),
                            new Class<?>[] {SSLSession.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getProtocol" -> protocol;
                                        case "getCipherSuite" -> cipher;
                                        default -> null;
                                    });
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

    private static C78LocalTlsScope connected(final FakeGuard[] owner) throws IOException {
        final var scope =
                C78LocalTlsScope.enter(
                        target(),
                        s -> {
                            owner[0] = new FakeGuard(s);
                            return owner[0];
                        });
        new C78LocalSocketFactory()
                .createSocket()
                .connect(
                        new InetSocketAddress(
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                        5000);
        return scope;
    }

    @Test
    void currentDerPinsOnlyBoundPeerAndTransfersOneSocket() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope = connected(owner)) {
                        final var trust = new C78LocalTrustManager(VALID);
                        trust.checkServerTrusted(
                                new X509Certificate[] {leaf()}, "RSA", new FakeTls(owner[0]));
                        scope.acceptConnection();
                        assertEquals(0, trust.getAcceptedIssuers().length);
                        assertThrows(
                                IOException.class,
                                () -> new C78LocalSocketFactory().createSocket());
                    }
                    assertFalse(owner[0].closed);
                    owner[0].close();
                    assertTrue(owner[0].closed);
                    assertEquals(1, owner[0].connects);
                });
    }

    @Test
    void bothHistoricalPinsAndChangedDerRefuseAndClose() throws Exception {
        opted(
                () -> {
                    for (final String resource :
                            new String[] {
                                "/tls/sql-test-retained-e3-20261005.cer",
                                "/tls/sql-sample-manual-pin-325fac56a833.cer"
                            }) {
                        final FakeGuard[] owner = new FakeGuard[1];
                        try (var scope = connected(owner)) {
                            assertThrows(
                                    CertificateException.class,
                                    () ->
                                            new C78LocalTrustManager(VALID)
                                                    .checkServerTrusted(
                                                            new X509Certificate[] {
                                                                certificate(resource)
                                                            },
                                                            "RSA",
                                                            new FakeTls(owner[0])));
                            assertEquals("LEAF_CHANGED", scope.failureClass());
                            assertThrows(IOException.class, scope::acceptConnection);
                        }
                        assertTrue(owner[0].closed);
                    }
                    final byte[] changed = leaf().getEncoded();
                    changed[changed.length - 1] ^= 1;
                    final var wrong =
                            (X509Certificate)
                                    CertificateFactory.getInstance("X.509")
                                            .generateCertificate(new ByteArrayInputStream(changed));
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope = connected(owner)) {
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        new C78LocalTrustManager(VALID)
                                                .checkServerTrusted(
                                                        new X509Certificate[] {wrong},
                                                        "RSA",
                                                        new FakeTls(owner[0])));
                        assertEquals("LEAF_CHANGED", scope.failureClass());
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void metadataOverrideCannotChangeCurrentDerFactsOrValidity() throws Exception {
        opted(
                () -> {
                    final var shadow = new ShadowCertificate(leaf());
                    for (final Instant instant :
                            new Instant[] {
                                VALID.instant(),
                                Instant.parse("2026-10-09T18:31:42Z"),
                                Instant.parse("2056-10-09T18:31:44Z")
                            }) {
                        final FakeGuard[] owner = new FakeGuard[1];
                        try (var scope = connected(owner)) {
                            final var trust =
                                    new C78LocalTrustManager(Clock.fixed(instant, ZoneOffset.UTC));
                            if (instant.equals(VALID.instant())) {
                                trust.checkServerTrusted(
                                        new X509Certificate[] {shadow},
                                        "RSA",
                                        new FakeTls(owner[0]));
                                assertEquals(
                                        "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED", scope.failureClass());
                            } else {
                                assertThrows(
                                        CertificateException.class,
                                        () ->
                                                trust.checkServerTrusted(
                                                        new X509Certificate[] {shadow},
                                                        "RSA",
                                                        new FakeTls(owner[0])));
                                assertTrue(
                                        Set.of("LEAF_NOT_YET_VALID", "LEAF_EXPIRED")
                                                .contains(scope.failureClass()));
                                assertThrows(IOException.class, scope::acceptConnection);
                            }
                        }
                        assertTrue(owner[0].closed);
                    }
                });
    }

    @Test
    void chainAndAllMissingSocketAndClientContextsRefuse() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope = connected(owner)) {
                        final var trust = new C78LocalTrustManager(VALID);
                        final X509Certificate[] c = {leaf()};
                        for (final X509Certificate[] chain :
                                new X509Certificate[][] {null, {}, {null}, {leaf(), leaf()}}) {
                            assertThrows(
                                    CertificateException.class,
                                    () ->
                                            trust.checkServerTrusted(
                                                    chain, "RSA", new FakeTls(owner[0])));
                        }
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
                        assertThrows(IOException.class, scope::acceptConnection);
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void tlsVersionCipherSessionAndTupleMismatchRefuse() throws Exception {
        opted(
                () -> {
                    for (int mode = 0; mode < 4; mode++) {
                        final FakeGuard[] owner = new FakeGuard[1];
                        try (var scope = connected(owner)) {
                            final var tls = new FakeTls(owner[0]);
                            if (mode == 0) {
                                tls.protocol = "TLSv1.3";
                            } else if (mode == 1) {
                                tls.cipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
                            } else if (mode == 2) {
                                tls.sessionMissing = true;
                            } else {
                                tls.portDelta = 1;
                            }
                            assertThrows(
                                    CertificateException.class,
                                    () ->
                                            new C78LocalTrustManager(VALID)
                                                    .checkServerTrusted(
                                                            new X509Certificate[] {leaf()},
                                                            "RSA",
                                                            tls));
                            assertThrows(IOException.class, scope::acceptConnection);
                        }
                        assertTrue(owner[0].closed);
                    }
                });
    }

    @Test
    void failedSecondCertificateCheckRevokesPriorPinProof() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope = connected(owner)) {
                        final var trust = new C78LocalTrustManager(VALID);
                        final var tls = new FakeTls(owner[0]);
                        trust.checkServerTrusted(new X509Certificate[] {leaf()}, "RSA", tls);
                        assertThrows(
                                CertificateException.class,
                                () -> trust.checkServerTrusted(null, "RSA", tls));
                        assertThrows(IOException.class, scope::acceptConnection);
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void remotePeerAndClosedRawSocketRefuseBeforeAcceptance() throws Exception {
        opted(
                () -> {
                    final FakeGuard[] owner = new FakeGuard[1];
                    try (var scope = connected(owner)) {
                        owner[0].remote = true;
                        assertThrows(IOException.class, () -> scope.verifyConnectedPeer(owner[0]));
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        new C78LocalTrustManager(VALID)
                                                .checkServerTrusted(
                                                        new X509Certificate[] {leaf()},
                                                        "RSA",
                                                        new FakeTls(owner[0])));
                        owner[0].remote = false;
                        owner[0].closed = true;
                        assertThrows(IOException.class, scope::acceptConnection);
                    }
                });
    }

    /** Only getEncoded is trustworthy input; every supplied metadata field deliberately lies. */
    @SuppressWarnings("deprecation")
    static final class ShadowCertificate extends X509Certificate {
        private static final long serialVersionUID = 1L;
        private final X509Certificate der;

        ShadowCertificate(final X509Certificate der) {
            this.der = der;
        }

        @Override
        public byte[] getEncoded() throws java.security.cert.CertificateEncodingException {
            return der.getEncoded();
        }

        @Override
        public void checkValidity() {}

        @Override
        public void checkValidity(final Date date) {}

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ZERO;
        }

        @Override
        public Principal getIssuerDN() {
            return null;
        }

        @Override
        public Principal getSubjectDN() {
            return null;
        }

        @Override
        public Date getNotBefore() {
            return new Date(0);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "FAKE";
        }

        @Override
        public String getSigAlgOID() {
            return "FAKE";
        }

        @Override
        public byte[] getSigAlgParams() {
            return null;
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return 0;
        }

        @Override
        public void verify(final PublicKey key) {}

        @Override
        public void verify(final PublicKey key, final String provider) {}

        @Override
        public String toString() {
            return "synthetic-shadow-metadata";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(final String oid) {
            return null;
        }
    }
}
