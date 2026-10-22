package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class C78DevPeerProofTest {
    private String enabled;
    private String pin;

    @BeforeEach
    void enable() {
        enabled = System.getProperty("c78.local.shadow.enabled");
        pin = System.getProperty("c78.local.shadow.sql-pin.enabled");
        System.setProperty("c78.local.shadow.enabled", "true");
        System.setProperty("c78.local.shadow.sql-pin.enabled", "true");
    }

    @AfterEach
    void restore() {
        if (enabled == null) {
            System.clearProperty("c78.local.shadow.enabled");
        } else {
            System.setProperty("c78.local.shadow.enabled", enabled);
        }
        if (pin == null) {
            System.clearProperty("c78.local.shadow.sql-pin.enabled");
        } else {
            System.setProperty("c78.local.shadow.sql-pin.enabled", pin);
        }
    }

    private static C78LocalSqlTarget target() {
        return C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
    }

    @Test
    void mandatoryProofRunsOnceOnConnectedSocketBeforeAnyStream() throws Exception {
        final var fixture = new C78DevPeerConnectorFixture();
        final var count = new AtomicInteger();
        try (final var scope =
                fixture.enter(
                        target(),
                        tuple -> {
                            count.incrementAndGet();
                            assertTrue(fixture.socket.isConnected());
                            assertFalse(fixture.socket.isClosed());
                            assertEquals(0, fixture.streams);
                            assertEquals(
                                    new C78DevPeerProof.Tuple(
                                            "127.0.0.1", 50123, "127.0.0.1", 1433),
                                    tuple);
                        })) {
            fixture.connectAndTouchStreams();
            assertEquals(1, count.get());
            assertEquals(2, fixture.streams);
            assertEquals(1, fixture.connects);
            assertThrows(IOException.class, () -> new C78LocalSocketFactory().createSocket());
            assertThrows(IOException.class, scope::acceptConnection);
            assertEquals("PIN_HANDSHAKE_PROOF_MISSING", scope.failureClass());
        }
        assertTrue(fixture.socket.isClosed());
    }

    @Test
    void refusedAndTimedOutProofExposeOnlyConstantsAndNoStreams() throws Exception {
        for (final boolean timeout : new boolean[] {false, true}) {
            final var fixture = new C78DevPeerConnectorFixture();
            try (final var scope =
                    fixture.enter(
                            target(),
                            tuple -> {
                                if (timeout) {
                                    throw new SocketTimeoutException("UNTRUSTED_TIMEOUT_DETAIL");
                                }
                                throw new IOException("UNTRUSTED_NATIVE_DETAIL");
                            })) {
                final var error = assertThrows(IOException.class, fixture::connectAndTouchStreams);
                assertEquals("C78_DEV_PEER_PROOF_REFUSED", error.getMessage());
                assertNull(error.getCause());
                assertEquals(
                        timeout ? "DEV_PEER_PROOF_TIMEOUT" : "DEV_PEER_PROOF_REFUSED",
                        scope.failureClass());
                assertEquals(0, fixture.streams);
            }
            assertTrue(fixture.socket.isClosed());
        }
    }

    @Test
    void sameSocketTupleChangeRevokesProofWithoutCallbackReplay() throws Exception {
        final var fixture = new C78DevPeerConnectorFixture();
        final var count = new AtomicInteger();
        try (final var scope = fixture.enter(target(), tuple -> count.incrementAndGet())) {
            fixture.connectAndTouchStreams();
            fixture.localPort++;
            assertThrows(IOException.class, () -> fixture.socket.getOutputStream());
            assertEquals("DEV_PEER_BINDING_CHANGED", scope.failureClass());
            assertEquals(1, count.get());
            assertEquals(2, fixture.streams);
        }
    }

    @Test
    void changeInsideCallbackRefusesBeforeStreams() throws Exception {
        final var fixture = new C78DevPeerConnectorFixture();
        try (final var scope = fixture.enter(target(), tuple -> fixture.localPort++)) {
            assertThrows(IOException.class, fixture::connectAndTouchStreams);
            assertEquals("DEV_PEER_BINDING_CHANGED", scope.failureClass());
            assertEquals(0, fixture.streams);
        }
    }

    @Test
    void swallowedReentrantProofFailureCannotBecomeVerified() throws Exception {
        final var fixture = new C78DevPeerConnectorFixture();
        try (final var scope =
                fixture.enter(
                        target(),
                        tuple -> {
                            assertThrows(
                                    IOException.class,
                                    () ->
                                            C78LocalTlsScope.current()
                                                    .verifyConnectedPeer(fixture.socket));
                        })) {
            assertThrows(IOException.class, fixture::connectAndTouchStreams);
            assertEquals(0, fixture.streams);
            assertEquals("DEV_PEER_BINDING_CHANGED", scope.failureClass());
        }
    }

    @Test
    void tupleAndMissingProofRefuseWithoutOpening() {
        assertThrows(NullPointerException.class, () -> C78LocalTlsScope.enterDev(target(), null));
        for (final String address : new String[] {"127.0.0.2", "127.0.0.0", "::1", "192.0.2.1"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new C78DevPeerProof.Tuple(address, 50000, "127.0.0.1", 1433));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new C78DevPeerProof.Tuple("127.0.0.1", 50000, address, 1433));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new C78DevPeerProof.Tuple("127.0.0.1", 0, "127.0.0.1", 1433));
        assertThrows(
                IllegalArgumentException.class,
                () -> new C78DevPeerProof.Tuple("127.0.0.1", 65536, "127.0.0.1", 1433));
    }
}
