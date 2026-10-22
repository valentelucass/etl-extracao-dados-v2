package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Wire validation only; a matching reply does not supply Root or physical authority. */
class C78DevEngineGateWireTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void serializedReplyAcceptsSameIntegralWireValuesAcrossJacksonNumericNodeTypes()
            throws Exception {
        final var expected = expected();
        final JsonNode reply = json.readTree(json.writeValueAsBytes(expected));
        assertTrue(expected.path("parentPID").isLong());
        assertTrue(expected.path("childPID").isLong());
        assertTrue(reply.path("parentPID").isInt());
        assertTrue(reply.path("childPID").isInt());
        assertFalse(expected.equals(reply));
        assertDoesNotThrow(() -> C78DevEngineAdmissionProducer.verifyPrivateReply(expected, reply));
    }

    @Test
    void fullPositiveLongValuesRemainExactWithoutIntTruncation() throws Exception {
        final var expected =
                expected().put("parentPID", 2_147_483_648L).put("childPID", Long.MAX_VALUE);
        final JsonNode reply = json.readTree(json.writeValueAsBytes(expected));
        assertDoesNotThrow(() -> C78DevEngineAdmissionProducer.verifyPrivateReply(expected, reply));
        final var wrong = (ObjectNode) reply;
        wrong.put("parentPID", 1L);
        assertBindingRefused(expected, wrong);
    }

    @Test
    void eachPidRejectsStringsFloatingValuesNullBooleanNonPositiveAndLongOverflow()
            throws Exception {
        for (final String field : List.of("parentPID", "childPID")) {
            for (final String value :
                    List.of(
                            "\"51764\"",
                            "51764.0",
                            "51764.5",
                            "true",
                            "null",
                            "0",
                            "-1",
                            "9223372036854775808",
                            "-9223372036854775809")) {
                final var expected = expected();
                final var reply = (ObjectNode) json.readTree(json.writeValueAsBytes(expected));
                reply.set(field, json.readTree(value));
                final var error =
                        assertThrows(
                                IOException.class,
                                () ->
                                        C78DevEngineAdmissionProducer.verifyPrivateReply(
                                                expected, reply));
                assertEquals("ROOT_GATE_PRIVATE_REPLY_PID_TYPE", error.getMessage());
            }
        }
    }

    @Test
    void changedPidOrAnyTextBindingIsRefused() throws Exception {
        for (final String field :
                List.of(
                        "kind",
                        "dispatchId",
                        "dispatchSHA256",
                        "nonce",
                        "invocationId",
                        "rootAuthoritySHA256",
                        "parentPID",
                        "childPID")) {
            final var expected = expected();
            final var reply = (ObjectNode) json.readTree(json.writeValueAsBytes(expected));
            if (field.endsWith("PID")) {
                reply.put(field, expected.path(field).longValue() + 1);
            } else {
                reply.put(field, expected.path(field).textValue() + "_CHANGED");
            }
            assertBindingRefused(expected, reply);
        }
    }

    @Test
    void missingAndExtraFieldsAreRefused() throws Exception {
        final var expected = expected();
        final var missing = (ObjectNode) json.readTree(json.writeValueAsBytes(expected));
        missing.remove("nonce");
        assertShapeRefused(expected, missing);
        final var extra = (ObjectNode) json.readTree(json.writeValueAsBytes(expected));
        extra.put("permit", true);
        assertShapeRefused(expected, extra);
    }

    @Test
    void unknownFieldCannotReplaceARequiredFieldEvenWithSameFieldCount() throws Exception {
        final var expected = expected();
        final var reply = (ObjectNode) json.readTree(json.writeValueAsBytes(expected));
        reply.remove("nonce");
        reply.put("unknown", "COMPONENT_ONLY");
        assertEquals(8, reply.size());
        assertBindingRefused(expected, reply);
    }

    @Test
    void scalarArrayAndNullRepliesAreRefused() throws Exception {
        final var expected = expected();
        assertShapeRefused(expected, null);
        for (final String wire : List.of("null", "true", "42", "\"permit\"", "[]")) {
            assertShapeRefused(expected, json.readTree(wire));
        }
    }

    private ObjectNode expected() {
        return json.createObjectNode()
                .put("kind", "ENGINE_GATE_PERMIT")
                .put("dispatchId", "OWN_EXCHANGE_COMPONENT_NOT_AUTHORITY")
                .put("dispatchSHA256", "a".repeat(64))
                .put("nonce", "b".repeat(64))
                .put("parentPID", 51764L)
                .put("childPID", 6920L)
                .put("invocationId", "00000000-0000-0000-0000-000000000001")
                .put("rootAuthoritySHA256", "c".repeat(64));
    }

    private static void assertBindingRefused(final JsonNode expected, final JsonNode reply) {
        assertEquals(
                "ROOT_GATE_LIVE_PRIVATE_PERMIT_BINDING",
                assertThrows(
                                IOException.class,
                                () ->
                                        C78DevEngineAdmissionProducer.verifyPrivateReply(
                                                expected, reply))
                        .getMessage());
    }

    private static void assertShapeRefused(final JsonNode expected, final JsonNode reply) {
        assertEquals(
                "ROOT_GATE_PRIVATE_REPLY_SHAPE",
                assertThrows(
                                IOException.class,
                                () ->
                                        C78DevEngineAdmissionProducer.verifyPrivateReply(
                                                expected, reply))
                        .getMessage());
    }
}
