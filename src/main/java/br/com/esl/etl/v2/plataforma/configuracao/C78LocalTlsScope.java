package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;

/** One connecting thread/one socket, not a registry of identities or business keys. */
public final class C78LocalTlsScope implements AutoCloseable {
    public static final String APPROVAL_REFERENCE = C78LocalSqlTarget.APPROVAL_REFERENCE;
    public static final String LEAF_SHA256 =
            "C78BBE9A3FDB1A997971A46FB41E7860EF7153EC7813A41250A9156363F86BCC";
    private static final ThreadLocal<C78LocalTlsScope> CURRENT = new ThreadLocal<>();
    private final SocketCreator sockets;
    private final C78DevPeerProof devPeerProof;
    private C78DevPeerProof.Tuple devTuple;
    private boolean devProofAttempted;
    private boolean devProofVerified;
    private boolean devProofRefused;
    private C78LocalSocketFactory.GuardedSocket socket;
    private boolean socketClaimed;
    private boolean closed;
    private boolean peerVerified;
    private boolean pinVerified;
    private boolean connectionAccepted;
    private boolean socketCloseUncertain;
    private String failureClass = "NOT_OBSERVED";

    @FunctionalInterface
    interface SocketCreator {
        C78LocalSocketFactory.GuardedSocket create(C78LocalTlsScope scope) throws IOException;
    }

    private C78LocalTlsScope(final SocketCreator sockets) {
        this.sockets = Objects.requireNonNull(sockets);
        devPeerProof = null;
    }

    private C78LocalTlsScope(final SocketCreator sockets, final C78DevPeerProof proof) {
        this.sockets = Objects.requireNonNull(sockets);
        devPeerProof = Objects.requireNonNull(proof);
    }

    public static void validate(final C78LocalSqlTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("C78_LOCAL_SHADOW_EXACT_TARGET_REQUIRED");
        }
        new C78LocalSqlTarget(target.jdbcUrl(), target.approvalReference());
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
                throw new IllegalStateException(
                        "C78_LOCAL_SHADOW_SQL_PIN_GLOBAL_TLS_OVERRIDE_REFUSED");
            }
        }
    }

    static void requireOptins() {
        if (!Boolean.getBoolean("c78.local.shadow.enabled")
                || !Boolean.getBoolean("c78.local.shadow.sql-pin.enabled")) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_SQL_PIN_OPT_IN_REQUIRED");
        }
    }

    public static C78LocalTlsScope enter(final C78LocalSqlTarget target) {
        return enter(target, C78LocalSocketFactory.GuardedSocket::new);
    }

    static C78LocalTlsScope enter(final C78LocalSqlTarget target, final SocketCreator sockets) {
        validate(target);
        if (CURRENT.get() != null) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_SQL_PIN_NESTED_SCOPE_REFUSED");
        }
        final var scope = new C78LocalTlsScope(sockets);
        CURRENT.set(scope);
        return scope;
    }

    public static C78LocalTlsScope enterDev(
            final C78LocalSqlTarget target, final C78DevPeerProof proof) {
        return enterDev(target, C78LocalSocketFactory.GuardedSocket::new, proof);
    }

    static C78LocalTlsScope enterDev(
            final C78LocalSqlTarget target,
            final SocketCreator sockets,
            final C78DevPeerProof proof) {
        validate(target);
        Objects.requireNonNull(proof);
        if (CURRENT.get() != null) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_SQL_PIN_NESTED_SCOPE_REFUSED");
        }
        final var scope = new C78LocalTlsScope(sockets, proof);
        CURRENT.set(scope);
        return scope;
    }

    static C78LocalTlsScope current() {
        requireOptins();
        final var scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_SQL_PIN_CONNECTION_SCOPE_REQUIRED");
        }
        return scope;
    }

    synchronized Socket claimSocket() throws IOException {
        if (closed || CURRENT.get() != this || socketClaimed) {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_SECOND_SOCKET_REFUSED");
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
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_ACTUAL_PEER_REFUSED");
        }
        peerVerified = true;
        if (devPeerProof != null) {
            verifyDevPeer(candidate);
        }
    }

    private void verifyDevPeer(final Socket candidate) throws IOException {
        final C78DevPeerProof.Tuple current;
        try {
            current =
                    new C78DevPeerProof.Tuple(
                            candidate.getLocalAddress().getHostAddress(),
                            candidate.getLocalPort(),
                            candidate.getInetAddress().getHostAddress(),
                            candidate.getPort());
        } catch (final RuntimeException refused) {
            pinRefusal("DEV_PEER_BINDING_CHANGED");
            throw new IOException("C78_DEV_PEER_BINDING_CHANGED");
        }
        if (devProofVerified) {
            if (!current.equals(devTuple)) {
                pinRefusal("DEV_PEER_BINDING_CHANGED");
                throw new IOException("C78_DEV_PEER_BINDING_CHANGED");
            }
            return;
        }
        if (devProofAttempted) {
            devProofRefused = true;
            pinRefusal("DEV_PEER_PROOF_REENTRANT");
            throw new IOException("C78_DEV_PEER_PROOF_REENTRANT");
        }
        devProofAttempted = true;
        try {
            devPeerProof.verify(current);
        } catch (final IOException | RuntimeException | Error refused) {
            devProofRefused = true;
            pinRefusal(
                    refused instanceof java.net.SocketTimeoutException
                            ? "DEV_PEER_PROOF_TIMEOUT"
                            : "DEV_PEER_PROOF_REFUSED");
            throw new IOException("C78_DEV_PEER_PROOF_REFUSED");
        }
        if (devProofRefused
                || closed
                || CURRENT.get() != this
                || candidate.isClosed()
                || !candidate.isConnected()) {
            pinRefusal("DEV_PEER_BINDING_CHANGED");
            throw new IOException("C78_DEV_PEER_BINDING_CHANGED");
        }
        final C78DevPeerProof.Tuple after;
        try {
            after =
                    new C78DevPeerProof.Tuple(
                            candidate.getLocalAddress().getHostAddress(),
                            candidate.getLocalPort(),
                            candidate.getInetAddress().getHostAddress(),
                            candidate.getPort());
        } catch (final RuntimeException refused) {
            pinRefusal("DEV_PEER_BINDING_CHANGED");
            throw new IOException("C78_DEV_PEER_BINDING_CHANGED");
        }
        if (!current.equals(after)) {
            pinRefusal("DEV_PEER_BINDING_CHANGED");
            throw new IOException("C78_DEV_PEER_BINDING_CHANGED");
        }
        devTuple = current;
        devProofVerified = true;
    }

    void verifyTlsPeer(final Socket tls) throws IOException {
        if (!peerVerified || socket == null) {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_PREAUTH_PEER_PROOF_REQUIRED");
        }
        verifyConnectedPeer(socket);
        if (tls == null
                || !tls.isConnected()
                || tls.isClosed()
                || tls.getPort() != 1433
                || tls.getLocalPort() != socket.getLocalPort()
                || !socket.getInetAddress().equals(tls.getInetAddress())
                || !socket.getLocalAddress().equals(tls.getLocalAddress())) {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_TLS_SOCKET_BINDING_REFUSED");
        }
    }

    static boolean loopback(final InetAddress address) {
        return address != null && address.isLoopbackAddress();
    }

    void verifiedPin() {
        if (closed || CURRENT.get() != this) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_SQL_PIN_SCOPE_INACTIVE");
        }
        pinVerified = true;
    }

    void pinRefusal(final String code) {
        pinVerified = false;
        failureClass = code;
    }

    public String failureClass() {
        return pinVerified ? "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED" : failureClass;
    }

    public void acceptConnection() throws IOException {
        if (closed || CURRENT.get() != this) {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_SCOPE_INACTIVE");
        }
        verifyConnectedPeer(socket);
        if (!pinVerified) {
            failureClass = "PIN_HANDSHAKE_PROOF_MISSING";
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_HANDSHAKE_PROOF_REQUIRED");
        }
        connectionAccepted = true;
    }

    public boolean socketCloseUncertain() {
        return socketCloseUncertain;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        if (CURRENT.get() != this) {
            throw new IOException("C78_LOCAL_SHADOW_SQL_PIN_SCOPE_OWNER_REQUIRED");
        }
        closed = true;
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
