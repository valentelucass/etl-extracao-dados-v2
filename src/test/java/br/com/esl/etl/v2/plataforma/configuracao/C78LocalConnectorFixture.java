package br.com.esl.etl.v2.plataforma.configuracao;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

/** Callback/peer fixture only; no Finished, network or authentication is simulated as real. */
public final class C78LocalConnectorFixture {
    private C78LocalConnectorFixture() {}

    public static C78LocalTlsScope verified(final AtomicReference<Socket> owner) {
        C78LocalTlsScope scope = null;
        try {
            final C78LocalTlsTest.FakeGuard[] guard = new C78LocalTlsTest.FakeGuard[1];
            scope =
                    C78LocalTlsScope.enter(
                            C78LocalTlsTest.target(),
                            s -> {
                                guard[0] = new C78LocalTlsTest.FakeGuard(s);
                                owner.set(guard[0]);
                                return guard[0];
                            });
            new C78LocalSocketFactory()
                    .createSocket()
                    .connect(
                            new InetSocketAddress(
                                    InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                            5000);
            new C78LocalTrustManager(C78LocalTlsTest.VALID)
                    .checkServerTrusted(
                            new X509Certificate[] {C78LocalTlsTest.leaf()},
                            "RSA",
                            new C78LocalTlsTest.FakeTls(guard[0]));
            return scope;
        } catch (final Exception failure) {
            if (scope != null) {
                try {
                    scope.close();
                } catch (final java.io.IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw new AssertionError("C78_OFFLINE_FIXTURE_FAILED", failure);
        }
    }
}
