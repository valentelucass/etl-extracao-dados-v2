package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Test-only socket boundary, never a real native/TLS/SQL proof or production factory. */
public final class C78DevPeerConnectorFixture {
    public int streams;
    public int connects;
    public int closes;
    public int localPort = 50123;
    public boolean closeFails;
    public FakeSocket socket;

    public C78LocalTlsScope enter(final C78LocalSqlTarget target, final C78DevPeerProof proof) {
        return C78LocalTlsScope.enterDev(
                target,
                scope -> {
                    socket = new FakeSocket(scope);
                    return socket;
                },
                proof);
    }

    public void connectAndTouchStreams() throws IOException {
        final var owned = new C78LocalSocketFactory().createSocket();
        owned.connect(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                10);
        owned.getInputStream();
        owned.getOutputStream();
    }

    public static void markSyntheticPinOnly() {
        C78LocalTlsScope.current().verifiedPin();
    }

    public final class FakeSocket extends C78LocalSocketFactory.GuardedSocket {
        private boolean connected;
        private boolean closed;

        FakeSocket(final C78LocalTlsScope scope) {
            super(scope);
        }

        @Override
        void physicalConnect(final SocketAddress endpoint, final int timeout) {
            connects++;
            connected = true;
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
        public int getPort() {
            return 1433;
        }

        @Override
        public int getLocalPort() {
            return localPort;
        }

        @Override
        public InetAddress getInetAddress() {
            return address();
        }

        @Override
        public InetAddress getLocalAddress() {
            return address();
        }

        private InetAddress address() {
            try {
                return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            } catch (final java.net.UnknownHostException impossible) {
                throw new AssertionError(impossible);
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            requireVerified();
            streams++;
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            requireVerified();
            streams++;
            return new ByteArrayOutputStream();
        }

        @Override
        public void close() throws IOException {
            closes++;
            if (closeFails) {
                throw new IOException("UNTRUSTED_TEST_CLOSE_DETAIL");
            }
            closed = true;
        }
    }
}
