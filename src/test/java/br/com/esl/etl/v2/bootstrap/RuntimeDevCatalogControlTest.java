package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Pure public IPC controls; no controller authority, provider, socket or native probe. */
class RuntimeDevCatalogControlTest {
    @Test
    void consumesOneBoundedAsciiFrameAndRejectsPartialControlUnicodeAndOverrun() throws Exception {
        final byte[] actual = "{}".getBytes(StandardCharsets.US_ASCII);
        for (final String ending : new String[] {"\n", "\r\n"}) {
            assertArrayEquals(
                    actual,
                    RuntimeDevCatalogControl.readLine(
                            new ByteArrayInputStream(
                                    ("{}" + ending).getBytes(StandardCharsets.US_ASCII))));
        }
        for (final byte[] malformed :
                new byte[][] {
                    actual,
                    "\n".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {127, 10},
                    ("a".repeat(8193) + "\n").getBytes(StandardCharsets.US_ASCII),
                    "{}\rx\n".getBytes(StandardCharsets.US_ASCII)
                }) {
            assertThrows(
                    IOException.class,
                    () -> RuntimeDevCatalogControl.readLine(new ByteArrayInputStream(malformed)));
        }
    }

    @Test
    void rejectsNonceCohortChildAndPhaseChangesWithoutConsumingAnyEffect() throws Exception {
        final var descriptor = RuntimeDevCatalogDispatchTest.descriptor();
        final var frame = frame(descriptor, "START_PERMIT");
        validate(frame, descriptor, "START_PERMIT", null, null);
        for (final String key :
                new String[] {"nonce", "dispatchSHA256", "dispatchId", "invocationId", "kind"}) {
            final var changed = frame.deepCopy();
            changed.put(key, "changed-public-control");
            assertThrows(
                    IOException.class,
                    () -> validate(changed, descriptor, "START_PERMIT", null, null));
        }
        frame.put("childPID", ProcessHandle.current().pid() + 1);
        assertThrows(
                IOException.class, () -> validate(frame, descriptor, "START_PERMIT", null, null));
    }

    @Test
    void refusesTupleCreationDriftForeignImageAndStaleNativeProof() throws Exception {
        final var descriptor = RuntimeDevCatalogDispatchTest.descriptor();
        final var tuple = new C78DevPeerProof.Tuple("127.0.0.1", 50000, "127.0.0.1", 1433);
        final var published = Instant.now();
        final var frame = frame(descriptor, "PRELOGIN_PEER_PERMIT");
        frame.set("tuple", new ObjectMapper().valueToTree(tuple));
        final var image = descriptor.path("nativeImage");
        final var proof = frame.putObject("nativeProof");
        for (final String key : new String[] {"service", "pid", "version", "sha256"}) {
            proof.set(key, image.path(key));
        }
        proof.put("rawCreationPRE", "1")
                .put("rawCreationPOST", "1")
                .put("loadedImage", true)
                .put("microsoftCacheOnly", true)
                .put("reverseTupleMatches", 1)
                .put("observedUtc", Instant.now().toString())
                .put("evidenceSHA256", "0".repeat(64));
        validate(frame, descriptor, "PRELOGIN_PEER_PERMIT", tuple, published);
        for (final String key : new String[] {"rawCreationPOST", "sha256", "version"}) {
            final var changed = frame.deepCopy();
            ((ObjectNode) changed.path("nativeProof")).put(key, "changed-public-control");
            assertThrows(
                    IOException.class,
                    () -> validate(changed, descriptor, "PRELOGIN_PEER_PERMIT", tuple, published));
        }
        proof.put("observedUtc", published.minusSeconds(1).toString());
        assertThrows(
                IOException.class,
                () -> validate(frame, descriptor, "PRELOGIN_PEER_PERMIT", tuple, published));
        proof.put("observedUtc", Instant.now().toString());
        ((ObjectNode) frame.path("tuple")).put("localPort", 50001);
        assertThrows(
                IOException.class,
                () -> validate(frame, descriptor, "PRELOGIN_PEER_PERMIT", tuple, published));
    }

    private static ObjectNode frame(final ObjectNode descriptor, final String kind) {
        final var node = new ObjectMapper().createObjectNode();
        node.put("schema", RuntimeDevCatalogControl.SCHEMA)
                .put("kind", kind)
                .put("dispatchSHA256", "c".repeat(64))
                .put("childPID", ProcessHandle.current().pid())
                .put("permit", true);
        for (final var key : new String[] {"dispatchId", "invocationId", "nonce", "parentPID"}) {
            node.set(key, descriptor.path(key));
        }
        return node;
    }

    private static void validate(
            final ObjectNode node,
            final ObjectNode descriptor,
            final String kind,
            final C78DevPeerProof.Tuple tuple,
            final Instant published)
            throws Exception {
        RuntimeDevCatalogControl.validateFrame(
                new ObjectMapper().writeValueAsBytes(node),
                descriptor,
                "c".repeat(64),
                kind,
                tuple,
                published);
    }
}
