package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Properties;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

/** Driver12.8.2 + real JSSE, fake byte streams. No socket/server/private key/SQL. */
class TestRetainedRealDriverTest {
    private static java.io.InputStream serverStream(final byte[] bytes) {
        return new java.io.ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(final byte[] target, final int offset, final int length) {
                // PRELOGIN must be received before the client sends TLS ClientHello. A real server
                // cannot coalesce a future TLS response into its earlier PRELOGIN response.
                final int boundary = ((bytes[2] & 255) << 8) | (bytes[3] & 255);
                return super.read(
                        target, offset, pos < boundary ? Math.min(length, boundary - pos) : length);
            }
        };
    }

    private static String safeCauseTypes(final Throwable failure) {
        final var types = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 6; depth++) {
            types.append(current.getClass().getSimpleName()).append('/');
            if (current.getStackTrace().length > 0) {
                types.append(current.getStackTrace()[0].getMethodName());
            }
            types.append(';');
            current = current.getCause();
        }
        return types.toString();
    }

    private static byte[] tds(final byte[] payload) {
        final var b = ByteBuffer.allocate(payload.length + 8);
        b.put((byte) 4)
                .put((byte) 1)
                .putShort((short) (payload.length + 8))
                .putShort((short) 0)
                .put((byte) 1)
                .put((byte) 0)
                .put(payload);
        return b.array();
    }

    private static void length3(final ByteArrayOutputStream out, final int length) {
        out.write(length >>> 16);
        out.write(length >>> 8);
        out.write(length);
    }

    private static byte[] prefix(final X509Certificate certificate) throws Exception {
        final var hellodata = new ByteArrayOutputStream();
        hellodata.write(new byte[] {3, 3});
        hellodata.write(new byte[32]);
        hellodata.write(0);
        hellodata.write(new byte[] {(byte) 0xC0, 0x2F, 0, 0, 5, (byte) 0xFF, 1, 0, 1, 0});
        final var handshakes = new ByteArrayOutputStream();
        handshakes.write(2);
        length3(handshakes, hellodata.size());
        handshakes.write(hellodata.toByteArray());
        final byte[] der = certificate.getEncoded();
        handshakes.write(11);
        length3(handshakes, der.length + 6);
        length3(handshakes, der.length + 3);
        length3(handshakes, der.length);
        handshakes.write(der);
        final var record =
                ByteBuffer.allocate(handshakes.size() + 5)
                        .put((byte) 22)
                        .putShort((short) 0x0303)
                        .putShort((short) handshakes.size())
                        .put(handshakes.toByteArray());
        final var response = new ByteArrayOutputStream();
        response.write(
                tds(
                        new byte[] {
                            0,
                            0,
                            11,
                            0,
                            6,
                            1,
                            0,
                            17,
                            0,
                            1,
                            (byte) 255,
                            16,
                            0,
                            3,
                            (byte) 232,
                            0,
                            0,
                            1
                        }));
        final var unfinishedFlight = new ByteArrayOutputStream();
        unfinishedFlight.write(record.array());
        // The server terminates TLS without key exchange/Finished. Keep the alert in the same
        // response flight; the real driver's assertion checks forbid an empty client flight.
        unfinishedFlight.write(new byte[] {21, 3, 3, 0, 2, 2, 40});
        response.write(tds(unfinishedFlight.toByteArray()));
        return response.toByteArray();
    }

    private static Properties driverProperties() throws Exception {
        final var props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "synthetic-only-driver-fixture");
        final var connector =
                Class.forName(
                        "br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSaJdbcConnector");
        final var config = connector.getDeclaredMethod("configureDriver", Properties.class);
        config.setAccessible(true);
        config.invoke(null, props);
        return props;
    }

    private static void onlyPreloginFrames(final byte[] written) {
        int position = 0;
        int packets = 0;
        while (position < written.length) {
            assertTrue(position + 8 <= written.length);
            assertEquals(18, Byte.toUnsignedInt(written[position]));
            final int length =
                    (Byte.toUnsignedInt(written[position + 2]) << 8)
                            | Byte.toUnsignedInt(written[position + 3]);
            assertTrue(length >= 8 && position + length <= written.length);
            position += length;
            packets++;
        }
        assertTrue(packets >= 2); // PRELOGIN and TLS; never LOGIN7(0x10)/SQL/RPC.
    }

    @Test
    void defaultStrictTrustRefusesThePublicFallbackCertificateWithoutPin() throws Exception {
        TestRetainedTlsTest.opted(
                () -> {
                    final byte[] serverPrefix = prefix(TestRetainedTlsTest.leaf());
                    final TestRetainedTlsTest.FakeGuard[] owner =
                            new TestRetainedTlsTest.FakeGuard[1];
                    try (var scope =
                            TestRetainedTlsScope.enter(
                                    TestRetainedTlsTest.target(),
                                    s -> {
                                        owner[0] = new TestRetainedTlsTest.FakeGuard(s);
                                        owner[0].incoming = serverStream(serverPrefix);
                                        return owner[0];
                                    })) {
                        final var properties = driverProperties();
                        properties.remove("trustManagerClass");
                        final var failure =
                                assertThrows(
                                        java.sql.SQLException.class,
                                        () ->
                                                new SQLServerDriver()
                                                        .connect(
                                                                TestRetainedSqlTarget.URL,
                                                                properties));
                        boolean certificateFailure = false;
                        Throwable cause = failure;
                        for (int depth = 0; cause != null && depth < 16; depth++) {
                            certificateFailure |=
                                    cause instanceof java.security.cert.CertificateException
                                            || cause
                                                    instanceof
                                                    java.security.cert.CertPathBuilderException
                                            || cause
                                                    instanceof
                                                    java.security.cert.CertPathValidatorException;
                            cause = cause.getCause();
                        }
                        assertTrue(certificateFailure);
                        assertEquals("NOT_OBSERVED", scope.failureClass());
                        onlyPreloginFrames(owner[0].written.toByteArray());
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void realDriverUsesSupportedSocketAndTrustHooksAndRefusesWrongLeafBeforeLogin()
            throws Exception {
        TestRetainedTlsTest.opted(
                () -> {
                    final var roots =
                            TrustManagerFactory.getInstance(
                                    TrustManagerFactory.getDefaultAlgorithm());
                    roots.init((KeyStore) null);
                    final var wrong =
                            ((X509TrustManager) roots.getTrustManagers()[0])
                                    .getAcceptedIssuers()[0];
                    final byte[] serverPrefix = prefix(wrong);
                    final TestRetainedTlsTest.FakeGuard[] owner =
                            new TestRetainedTlsTest.FakeGuard[1];
                    try (var scope =
                            TestRetainedTlsScope.enter(
                                    TestRetainedTlsTest.target(),
                                    s -> {
                                        owner[0] = new TestRetainedTlsTest.FakeGuard(s);
                                        owner[0].response = serverPrefix;
                                        owner[0].incoming = serverStream(serverPrefix);
                                        return owner[0];
                                    })) {
                        final var driver = new SQLServerDriver();
                        assertEquals(12, driver.getMajorVersion());
                        assertEquals(8, driver.getMinorVersion());
                        final var props = driverProperties();
                        assertEquals("0", props.getProperty("connectRetryCount"));
                        assertEquals("false", props.getProperty("transparentNetworkIPResolution"));
                        assertEquals("false", props.getProperty("multiSubnetFailover"));
                        final var failure =
                                assertThrows(
                                        java.sql.SQLException.class,
                                        () -> driver.connect(TestRetainedSqlTarget.URL, props));
                        assertEquals("LEAF_CHANGED", scope.failureClass(), safeCauseTypes(failure));
                        assertEquals(1, owner[0].connects);
                        onlyPreloginFrames(owner[0].written.toByteArray());
                    }
                    assertTrue(owner[0].closed);
                });
    }

    @Test
    void approvedPublicLeafPassesPinButUnfinishedHandshakeCannotReachLogin() throws Exception {
        TestRetainedTlsTest.opted(
                () -> {
                    final byte[] serverPrefix = prefix(TestRetainedTlsTest.leaf());
                    final TestRetainedTlsTest.FakeGuard[] owner =
                            new TestRetainedTlsTest.FakeGuard[1];
                    try (var scope =
                            TestRetainedTlsScope.enter(
                                    TestRetainedTlsTest.target(),
                                    s -> {
                                        owner[0] = new TestRetainedTlsTest.FakeGuard(s);
                                        owner[0].response = serverPrefix;
                                        owner[0].incoming = serverStream(serverPrefix);
                                        return owner[0];
                                    })) {
                        final var failure =
                                assertThrows(
                                        java.sql.SQLException.class,
                                        () ->
                                                new SQLServerDriver()
                                                        .connect(
                                                                TestRetainedSqlTarget.URL,
                                                                driverProperties()));
                        assertEquals(
                                "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED",
                                scope.failureClass(),
                                safeCauseTypes(failure));
                        onlyPreloginFrames(owner[0].written.toByteArray());
                        assertEquals(1, owner[0].connects);
                        // Certificate callback passed; server proof/Finished was never provided. No
                        // success claimed.
                        assertFalse(owner[0].written.size() == 0);
                    }
                    assertTrue(owner[0].closed);
                });
    }
}
