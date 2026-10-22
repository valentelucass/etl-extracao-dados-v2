package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.SocketFactory;

/** Driver12.8.2 supported factory; no name resolution/proxy or second socket. */
public final class C78LocalSocketFactory extends SocketFactory {
    private final C78LocalTlsScope scope;

    public C78LocalSocketFactory() {
        scope = C78LocalTlsScope.current();
    }

    @Override
    public Socket createSocket() throws IOException {
        return scope.claimSocket();
    }

    @Override
    public Socket createSocket(final String host, final int port) throws IOException {
        throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_RESOLVED_ENDPOINT_REQUIRED");
    }

    @Override
    public Socket createSocket(final InetAddress host, final int port) throws IOException {
        throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_DRIVER_CONNECT_REQUIRED");
    }

    @Override
    public Socket createSocket(
            final String host, final int port, final InetAddress local, final int localPort)
            throws IOException {
        throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_DRIVER_CONNECT_REQUIRED");
    }

    @Override
    public Socket createSocket(
            final InetAddress host, final int port, final InetAddress local, final int localPort)
            throws IOException {
        throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_DRIVER_CONNECT_REQUIRED");
    }

    static class GuardedSocket extends Socket {
        private final C78LocalTlsScope scope;
        private boolean connectAttempted;
        private boolean verified;

        GuardedSocket(final C78LocalTlsScope scope) {
            super(Proxy.NO_PROXY);
            this.scope = scope;
        }

        @Override
        public synchronized void connect(final SocketAddress endpoint, final int timeout)
                throws IOException {
            if (connectAttempted
                    || !(endpoint instanceof InetSocketAddress target)
                    || target.isUnresolved()
                    || target.getPort() != 1433
                    || !C78LocalTlsScope.loopback(target.getAddress())
                    || timeout <= 0) {
                throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_CONNECT_TARGET_REFUSED");
            }
            connectAttempted = true;
            try {
                // Target already admitted as localhost. The wire route is a literal with no proxy.
                physicalConnect(
                        new InetSocketAddress(
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 1433),
                        Math.min(timeout, 10000));
                scope.verifyConnectedPeer(this);
                verified = true;
            } catch (final IOException failure) {
                try {
                    close();
                } catch (final IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        @Override
        public void connect(final SocketAddress endpoint) throws IOException {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_CONNECT_TIMEOUT_REQUIRED");
        }

        void physicalConnect(final SocketAddress endpoint, final int timeout) throws IOException {
            super.connect(endpoint, timeout);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            requireVerified();
            return super.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            requireVerified();
            return super.getOutputStream();
        }

        final void requireVerified() throws IOException {
            if (!verified) {
                throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_STREAM_BEFORE_PEER_PROOF_REFUSED");
            }
            scope.verifyConnectedPeer(this);
        }
    }
}
