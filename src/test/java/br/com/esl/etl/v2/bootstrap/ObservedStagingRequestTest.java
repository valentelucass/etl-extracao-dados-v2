package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCaptureTemplate;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObservedStagingRequestTest {
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void readsOnlyTheFourClosedTemplatesWithoutInventingIdentity(
            final ObservedExpansionCaptureTemplate template) {
        final var request =
                ObservedStagingRequest.parse(bytes(document(template.name()).toString()));
        assertEquals(template, request.template());
        assertEquals("2026-10-17", request.businessWindow().startInclusive().toString());
        assertEquals("2026-10-23", request.businessWindow().endInclusive().toString());
        assertFalse(request.toString().contains("2026-10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUPLICATE", "TRAILING"})
    void rejectsAmbiguousDocumentsRatherThanSelectingOne(final String variant) {
        final String valid = document("CAP").toString();
        final String changed =
                variant.equals("DUPLICATE")
                        ? valid.replace(
                                "\"template\":\"CAP\"", "\"template\":\"CAP\",\"template\":\"SIN\"")
                        : valid + " {}";
        assertClosed(bytes(changed));
    }

    @Test
    void callerAuthorityTargetAndPolicyFieldsCannotEnterTheRequest() {
        for (final String field : new String[] {"rootAuthorized", "jdbcUrl", "per"}) {
            final var changed = document("CAP");
            changed.put(field, "SENSITIVE_SENTINEL");
            assertClosed(bytes(changed.toString()));
        }
    }

    @Test
    void rejectsAnOversizedInputBeforeParsingItsContent() {
        assertClosed(new byte[16_385]);
    }

    @Test
    void rejectsWrongTypesNoncanonicalIdsAndReversedDates() {
        final var wrongType = document("INV");
        wrongType.put("executionId", 7);
        assertClosed(bytes(wrongType.toString()));
        final var shortUuid = document("INV");
        shortUuid.put("executionId", "1-1-1-1-1");
        assertClosed(bytes(shortUuid.toString()));
        final var reversed = document("INV");
        reversed.put("businessStart", "2026-10-24");
        assertClosed(bytes(reversed.toString()));
        final var zeroYear = document("INV");
        zeroYear.put("businessStart", "0000-01-01");
        assertClosed(bytes(zeroYear.toString()));
    }

    @Test
    void unavailableInputDoesNotPublishItsPathOrExceptionChain() {
        final var refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ObservedStagingRequest.read(
                                        directory.resolve("SENSITIVE_SENTINEL.json")));
        assertEquals("OBSERVED_STAGING_REQUEST_INVALID", refused.getMessage());
        assertNull(refused.getCause());
        assertEquals(0, refused.getSuppressed().length);
    }

    static ObjectNode document(final String template) {
        return JsonNodeFactory.instance
                .objectNode()
                .put("requestVersion", "observed-staging-trial-v1")
                .put("executionId", "651aa16b-8d7a-45d4-9d21-66960d84609b")
                .put("template", template)
                .put("businessStart", "2026-10-17")
                .put("businessEnd", "2026-10-23");
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertClosed(final byte[] input) {
        final var refused =
                assertThrows(
                        IllegalArgumentException.class, () -> ObservedStagingRequest.parse(input));
        assertEquals("OBSERVED_STAGING_REQUEST_INVALID", refused.getMessage());
        assertNull(refused.getCause());
        assertEquals(0, refused.getSuppressed().length);
    }
}
