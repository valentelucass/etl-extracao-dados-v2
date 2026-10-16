package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;

/** One connecting thread/one socket, not a registry of identities or business keys. */
public final class TestRetainedTlsScope implements AutoCloseable {
    public static final String APPROVAL_REFERENCE = TestRetainedSqlTarget.APPROVAL_REFERENCE;
    public static final String LEAF_SHA256 =
            "E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276";
    private static final ThreadLocal<TestRetainedTlsScope> CURRENT = new ThreadLocal<>();
    private final SocketCreator sockets;
    private TestRetainedSocketFactory.GuardedSocket socket;
    private boolean socketClaimed;
    private boolean peerVerified;
    private boolean pinVerified;
    private boolean connectionAccepted;
    private boolean socketCloseUncertain;
    private String failureClass = "NOT_OBSERVED";

    @FunctionalInterface
    interface SocketCreator {
        TestRetainedSocketFactory.GuardedSocket create(TestRetainedTlsScope scope)
                throws IOException;
    }

    private TestRetainedTlsScope(final SocketCreator sockets) {
        this.sockets = Objects.requireNonNull(sockets);
    }

    public static void validate(final TestRetainedSqlTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("TEST_RETAINED_EXACT_TARGET_REQUIRED");
        }
        new TestRetainedSqlTarget(target.jdbcUrl(), target.approvalReference());
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
                        "TEST_RETAINED_SQL_PIN_GLOBAL_TLS_OVERRIDE_REFUSED");
            }
        }
    }

    static void requireOptins() {
        if (!Boolean.getBoolean("test.retained.local.enabled")
                || !Boolean.getBoolean("test.retained.sql-pin.enabled")) {
            throw new IllegalStateException("TEST_RETAINED_SQL_PIN_OPT_IN_REQUIRED");
        }
    }

    public static TestRetainedTlsScope enter(final TestRetainedSqlTarget target) {
        return enter(target, TestRetainedSocketFactory.GuardedSocket::new);
    }

    static TestRetainedTlsScope enter(
            final TestRetainedSqlTarget target, final SocketCreator sockets) {
        validate(target);
        if (CURRENT.get() != null) {
            throw new IllegalStateException("TEST_RETAINED_SQL_PIN_NESTED_SCOPE_REFUSED");
        }
        final var scope = new TestRetainedTlsScope(sockets);
        CURRENT.set(scope);
        return scope;
    }

    static TestRetainedTlsScope current() {
        requireOptins();
        final var scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException("TEST_RETAINED_SQL_PIN_CONNECTION_SCOPE_REQUIRED");
        }
        return scope;
    }

    synchronized Socket claimSocket() throws IOException {
        if (socketClaimed) {
            throw new IOException("TEST_RETAINED_SQL_PIN_SECOND_SOCKET_REFUSED");
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
            throw new IOException("TEST_RETAINED_SQL_PIN_ACTUAL_PEER_REFUSED");
        }
        peerVerified = true;
    }

    void verifyTlsPeer(final Socket tls) throws IOException {
        if (!peerVerified || socket == null) {
            throw new IOException("TEST_RETAINED_SQL_PIN_PREAUTH_PEER_PROOF_REQUIRED");
        }
        verifyConnectedPeer(socket);
        if (tls == null
                || !tls.isConnected()
                || tls.isClosed()
                || tls.getPort() != 1433
                || tls.getLocalPort() != socket.getLocalPort()
                || !socket.getInetAddress().equals(tls.getInetAddress())
                || !socket.getLocalAddress().equals(tls.getLocalAddress())) {
            throw new IOException("TEST_RETAINED_SQL_PIN_TLS_SOCKET_BINDING_REFUSED");
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
            throw new IOException("TEST_RETAINED_SQL_PIN_HANDSHAKE_PROOF_REQUIRED");
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
