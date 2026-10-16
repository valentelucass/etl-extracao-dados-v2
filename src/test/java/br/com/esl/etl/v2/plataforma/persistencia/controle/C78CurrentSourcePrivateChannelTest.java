package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Offline private wire qualification. An observation fixture supplies no Root or Source authority.
 */
class C78CurrentSourcePrivateChannelTest {
    private static final Instant ISSUED = Instant.parse("2026-10-14T00:00:00Z");
    private static final Instant OBSERVED = ISSUED.plusSeconds(1);
    private static final Instant NOW = ISSUED.plusSeconds(2);
    private static final JsonMapper JSON =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    private static ObjectNode expected() {
        return JSON.createObjectNode()
                .put("kind", "ENGINE_SOURCE_OBSERVATION")
                .put("dispatchId", "C54SAMPLE12-" + "a".repeat(32))
                .put("dispatchSHA256", "b".repeat(64))
                .put("nonce", "c".repeat(64))
                .put("parentPID", 51764L)
                .put("childPID", 6920L)
                .put("invocationId", "00000000-0000-0000-0000-000000000001")
                .put("rootAuthoritySHA256", "d".repeat(64));
    }

    private static ObjectNode reply() throws IOException {
        final var answer = expected();
        answer.set(
                "observation",
                JSON.createObjectNode()
                        .put("requestId", "HERMES_c54d6a25880d")
                        .put("scopeBinding", "e".repeat(32))
                        .put("templateId", 6908)
                        .put("httpStatus", 200)
                        .put("responseBytes", 128L)
                        .put("metadataFields", 1)
                        .put("attempts", 1)
                        .put("observedAt", OBSERVED.toString()));
        // Real serialized wire roundtrip changes LongNode PIDs/byte count into IntNode.
        return (ObjectNode) JSON.readTree(JSON.writeValueAsBytes(answer));
    }

    @Test
    void sameSerializedSnapshotMayBeVerifiedAgainWithoutAnHttpBackend() throws Exception {
        final var one =
                C78Sample12AdmissionProducer.verifySourceReply(
                        expected(), reply(), null, ISSUED, NOW);
        final var two =
                C78Sample12AdmissionProducer.verifySourceReply(
                        expected(), reply(), one, ISSUED, NOW);
        assertEquals(one, two);
        assertEquals(6908, one.templateId());
        assertEquals(128, one.responseBytes());
    }

    @Test
    void actualRootMustVerifyBeforeConstructedTypedReaderCanReadAnyScope() throws Exception {
        final var selection = C78Sample12OwnerTest.selection();
        final var constructed = new AtomicInteger();
        final var checked = new AtomicInteger();
        try (var producer =
                C78Sample12AdmissionProducer.sample12Current(
                        selection,
                        null,
                        null,
                        evidence -> {
                            assertNotNull(evidence);
                            constructed.incrementAndGet();
                            return new RuntimeDevEngineBankBinding.Admission() {
                                public void verify() throws java.sql.SQLException {
                                    checked.incrementAndGet();
                                    evidence.verifyCurrentScope();
                                }

                                public RuntimeDevEngineBankBinding.Session open() {
                                    throw new AssertionError();
                                }

                                public void close() {}
                            };
                        })) {
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, producer::verify)
                            .gate());
            assertEquals(1, constructed.get());
            assertEquals(0, checked.get());
        }
    }

    @Test
    void bindingAndPidTypesAreStrictOnTheSerializedWire() throws Exception {
        for (final String field :
                List.of(
                        "kind",
                        "dispatchId",
                        "dispatchSHA256",
                        "nonce",
                        "invocationId",
                        "rootAuthoritySHA256")) {
            final var invalid = reply().put(field, "OTHER");
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.verifySourceReply(
                                    expected(), invalid, null, ISSUED, NOW));
        }
        for (final String value :
                List.of("\"51764\"", "51764.0", "0", "-1", "9223372036854775808")) {
            final var invalid = reply();
            invalid.set("parentPID", JSON.readTree(value));
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.verifySourceReply(
                                    expected(), invalid, null, ISSUED, NOW));
        }
        final var invalid = reply().put("childPID", 6921);
        assertThrows(
                IOException.class,
                () ->
                        C78Sample12AdmissionProducer.verifySourceReply(
                                expected(), invalid, null, ISSUED, NOW));
    }

    @Test
    void observationTypesBoundsAndClosedFieldsRefuse() throws Exception {
        for (final String field :
                List.of(
                        "templateId",
                        "httpStatus",
                        "responseBytes",
                        "metadataFields",
                        "attempts")) {
            for (final String invalidValue :
                    List.of("\"1\"", "1.0", "9223372036854775808", "null")) {
                final var invalid = reply();
                invalid.withObject("observation").set(field, JSON.readTree(invalidValue));
                assertThrows(
                        IOException.class,
                        () ->
                                C78Sample12AdmissionProducer.verifySourceReply(
                                        expected(), invalid, null, ISSUED, NOW));
            }
        }
        for (final String field : List.of("requestId", "scopeBinding", "observedAt")) {
            final var invalid = reply();
            invalid.withObject("observation").put(field, 1);
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.verifySourceReply(
                                    expected(), invalid, null, ISSUED, NOW));
        }
        for (final String field :
                List.of("templateId", "httpStatus", "responseBytes", "attempts")) {
            final var invalid = reply();
            invalid.withObject("observation").put(field, 0);
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.verifySourceReply(
                                    expected(), invalid, null, ISSUED, NOW));
        }
        final var extra = reply().put("permit", true);
        assertThrows(
                IOException.class,
                () ->
                        C78Sample12AdmissionProducer.verifySourceReply(
                                expected(), extra, null, ISSUED, NOW));
        final var held = reply();
        held.putNull("observation");
        assertThrows(
                IOException.class,
                () ->
                        C78Sample12AdmissionProducer.verifySourceReply(
                                expected(), held, null, ISSUED, NOW));
    }

    @Test
    void changedFirstSnapshotAndTimesOutsideActualUnitRefuse() throws Exception {
        final var first =
                C78Sample12AdmissionProducer.verifySourceReply(
                        expected(), reply(), null, ISSUED, NOW);
        final var changed = reply();
        changed.withObject("observation").put("scopeBinding", "f".repeat(32));
        assertThrows(
                IOException.class,
                () ->
                        C78Sample12AdmissionProducer.verifySourceReply(
                                expected(), changed, first, ISSUED, NOW));
        for (final String time :
                List.of(
                        ISSUED.minusSeconds(1).toString(),
                        NOW.plusSeconds(1).toString(),
                        "invalid")) {
            final var invalid = reply();
            invalid.withObject("observation").put("observedAt", time);
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.verifySourceReply(
                                    expected(), invalid, null, ISSUED, NOW));
        }
    }

    @Test
    void sameBoundedAsciiFrameParserRefusesDuplicateTrailingUnicodePartialAndOverflow()
            throws Exception {
        final String valid = JSON.writeValueAsString(reply()) + "\n";
        assertEquals(
                reply(),
                C78Sample12AdmissionProducer.readPrivateFrame(
                        new ByteArrayInputStream(valid.getBytes(StandardCharsets.US_ASCII)), JSON));
        for (final String invalid :
                List.of(
                        "{\"kind\":1,\"kind\":2}\n",
                        "{}{}\n",
                        "{}\r\n",
                        "{}",
                        " ".repeat(8192) + "\n",
                        "{\"value\":\"é\"}\n")) {
            assertThrows(
                    IOException.class,
                    () ->
                            C78Sample12AdmissionProducer.readPrivateFrame(
                                    new ByteArrayInputStream(
                                            invalid.getBytes(StandardCharsets.UTF_8)),
                                    JSON));
        }
    }
}
