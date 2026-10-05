package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;

/** One connecting thread/one socket, not a registry of identities or business keys. */
public final class PinnedSampleTlsScope implements AutoCloseable {
    public static final String APPROVAL_REFERENCE = "HERMES325fac56a833";
    public static final String LEAF_SHA256 =
            "BF723AE91FC90F3CC448122366C90650CBC2C6F5CD4A849F4B3C88C68E7A43A0";
    private static final ThreadLocal<PinnedSampleTlsScope> CURRENT = new ThreadLocal<>();
    private final SocketCreator sockets;
    private PinnedSampleSocketFactory.GuardedSocket socket;
    private boolean socketClaimed;
    private boolean peerVerified;
    private boolean pinVerified;
    private boolean connectionAccepted;
    private boolean socketCloseUncertain;
    private String failureClass = "NOT_OBSERVED";

    @FunctionalInterface
    interface SocketCreator {
        PinnedSampleSocketFactory.GuardedSocket create(PinnedSampleTlsScope scope)
                throws IOException;
    }

    private PinnedSampleTlsScope(final SocketCreator sockets) {
        this.sockets = Objects.requireNonNull(sockets);
    }

    public static void validate(final ShadowStorageProperties target) {
        if (target == null
                || !target.auditEnabled()
                || !target.usesSampleSqlPinAuthentication()
                || target.targetKind() != ShadowStorageTargetKind.LOCAL_EPHEMERAL
                || !APPROVAL_REFERENCE.equals(target.approvalReference())) {
            throw new IllegalArgumentException("COL_SAMPLE_SQL_PIN_EXPLICIT_TARGET_REQUIRED");
        }
        ShadowStorageProperties.enabledSampleSqlPin(target.jdbcUrl(), target.approvalReference());
        requireOptins();
        for (final String name :
                new String[] {
                    "javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType",
                    "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreProvider",
                    "javax.net.ssl.keyStore", "javax.net.ssl.keyStoreType",
                    "javax.net.ssl.keyStorePassword", "javax.net.ssl.keyStoreProvider",
                    "javax.net.debug", "java.security.properties"
                }) {
            if (System.getProperties().containsKey(name)) {
                throw new IllegalStateException("COL_SAMPLE_SQL_PIN_GLOBAL_TLS_OVERRIDE_REFUSED");
            }
        }
    }

    static void requireOptins() {
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")
                || !Boolean.getBoolean("coletas6908.sample.enabled")
                || !Boolean.getBoolean("coletas6908.sample.sa.enabled")
                || !Boolean.getBoolean("coletas6908.sample.shared.enabled")
                || !Boolean.getBoolean("coletas6908.sample.sql-pin.enabled")) {
            throw new IllegalStateException("COL_SAMPLE_SQL_PIN_OPT_IN_REQUIRED");
        }
    }

    public static PinnedSampleTlsScope enter(final ShadowStorageProperties target) {
        return enter(target, PinnedSampleSocketFactory.GuardedSocket::new);
    }

    static PinnedSampleTlsScope enter(
            final ShadowStorageProperties target, final SocketCreator sockets) {
        validate(target);
        if (CURRENT.get() != null) {
            throw new IllegalStateException("COL_SAMPLE_SQL_PIN_NESTED_SCOPE_REFUSED");
        }
        final var scope = new PinnedSampleTlsScope(sockets);
        CURRENT.set(scope);
        return scope;
    }

    static PinnedSampleTlsScope current() {
        requireOptins();
        final var scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException("COL_SAMPLE_SQL_PIN_CONNECTION_SCOPE_REQUIRED");
        }
        return scope;
    }

    synchronized Socket claimSocket() throws IOException {
        if (socketClaimed) {
            throw new IOException("COL_SAMPLE_SQL_PIN_SECOND_SOCKET_REFUSED");
        }
        socketClaimed = true;
        socket = sockets.create(this);
        return socket;
    }

    void verifyConnectedPeer(final Socket candidate) throws IOException {
        if (candidate == null
                || candidate != socket
                || !candidate.isConnected()
                || candidate.isClosed()
                || candidate.getPort() != 1433
                || candidate.getLocalPort() < 1
                || !loopback(candidate.getInetAddress())
                || !loopback(candidate.getLocalAddress())) {
            throw new IOException("COL_SAMPLE_SQL_PIN_ACTUAL_PEER_REFUSED");
        }
        peerVerified = true;
    }

    void verifyTlsPeer(final Socket tls) throws IOException {
        if (!peerVerified || socket == null) {
            throw new IOException("COL_SAMPLE_SQL_PIN_PREAUTH_PEER_PROOF_REQUIRED");
        }
        verifyConnectedPeer(socket);
        if (tls == null
                || !tls.isConnected()
                || tls.isClosed()
                || tls.getPort() != 1433
                || tls.getLocalPort() != socket.getLocalPort()
                || !socket.getInetAddress().equals(tls.getInetAddress())
                || !socket.getLocalAddress().equals(tls.getLocalAddress())) {
            throw new IOException("COL_SAMPLE_SQL_PIN_TLS_SOCKET_BINDING_REFUSED");
        }
    }

    static boolean loopback(final InetAddress address) {
        return address != null && address.isLoopbackAddress();
    }

    void verifiedPin() {
        pinVerified = true;
    }

    void pinRefusal(final String code) {
        failureClass = code;
    }

    public String failureClass() {
        return pinVerified ? "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED" : failureClass;
    }

    public void acceptConnection() throws IOException {
        verifyConnectedPeer(socket);
        if (!pinVerified) {
            failureClass = "PIN_HANDSHAKE_PROOF_MISSING";
            throw new IOException("COL_SAMPLE_SQL_PIN_HANDSHAKE_PROOF_REQUIRED");
        }
        connectionAccepted = true;
    }

    public boolean socketCloseUncertain() {
        return socketCloseUncertain;
    }

    @Override
    public void close() throws IOException {
        try {
            if (!connectionAccepted && socket != null) {
                try {
                    socket.close();
                } catch (final IOException | RuntimeException | Error failure) {
                    socketCloseUncertain = true;
                    throw failure;
                }
            }
        } finally {
            CURRENT.remove();
        }
    }
}
