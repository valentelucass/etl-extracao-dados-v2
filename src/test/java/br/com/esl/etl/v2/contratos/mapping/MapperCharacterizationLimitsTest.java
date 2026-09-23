package br.com.esl.etl.v2.contratos.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapperCharacterizationLimitsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MapperProjection.Entity ENTITY = MapperProjection.Entity.COTACOES;
    private static final String VALID =
            "{\"data\":[{\"sequence_code\":1,\"requested_at\":\"2026-07-15\"}]}";

    @Test
    void refusesMalformedDuplicateTrailingAndWrongEnvelopeWithoutLeakingInput() throws Exception {
        for (final String envelope :
                new String[] {
                    "{",
                    VALID + "{}",
                    "{\"data\":[],\"data\":[]}",
                    "[]",
                    "{\"data\":{}}",
                    "\ufeff" + VALID
                }) {
            assertIncomplete(compare(envelope), null);
        }
        final byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        assertIncomplete(
                MapperCharacterization.compare(
                        ENTITY,
                        1,
                        page -> new ByteArrayInputStream(invalidUtf8),
                        page -> expected("NONE")),
                "INVALID_JSON_OR_STRUCTURE_LIMIT");
    }

    @Test
    void enforcesByteLimitBeforeReadingOrMaterializingTheRemainder() throws Exception {
        final AtomicInteger received = new AtomicInteger();
        final InputStream endless =
                new InputStream() {
                    @Override
                    public int read() {
                        received.incrementAndGet();
                        return ' ';
                    }
                };
        assertIncomplete(
                MapperCharacterization.compare(
                        ENTITY, 1, page -> endless, page -> expected("NONE")),
                "BYTE_LIMIT");
        assertEquals(65_537, received.get());
        assertTrue(compare(VALID + " ".repeat(65_536 - VALID.length())).matches());
        assertIncomplete(compare(VALID + " ".repeat(65_537 - VALID.length())), "BYTE_LIMIT");
    }

    @Test
    void refusesDepthPathAndNodeOverflowBeforeTheMapper() throws Exception {
        assertIncomplete(
                compare("{\"data\":[],\"extra\":" + "[".repeat(16) + "0" + "]".repeat(16) + "}"),
                "INVALID_JSON_OR_STRUCTURE_LIMIT");
        final StringBuilder fields = new StringBuilder("{\"data\":[]");
        for (int i = 0; i < 256; i++) {
            fields.append(",\"field").append(i).append("\":0");
        }
        assertIncomplete(compare(fields + "}"), "INVALID_JSON_OR_STRUCTURE_LIMIT");
        assertIncomplete(
                compare("{\"data\":[],\"extra\":[" + "0,".repeat(4_095) + "0]}"),
                "INVALID_JSON_OR_STRUCTURE_LIMIT");
    }

    @Test
    void missingPageAndReadFailureAfterAValidPageNeverBecomeSuccess() {
        final var missing =
                MapperCharacterization.compare(
                        ENTITY,
                        2,
                        page -> page == 1 ? input(VALID) : null,
                        page -> expected("NONE"));
        assertIncomplete(missing, "MISSING_PAGE");
        assertEquals(1, missing.sanitized().path("rows").intValue());
        final var failed =
                MapperCharacterization.compare(
                        ENTITY,
                        2,
                        page ->
                                page == 1
                                        ? input(VALID)
                                        : new InputStream() {
                                            @Override
                                            public int read() throws IOException {
                                                throw new IOException("SYNTH_PRIVATE_READ_FAILURE");
                                            }
                                        },
                        page -> expected("NONE"));
        assertIncomplete(failed, "READ_FAILURE");
        assertFalse(failed.toString().contains("SYNTH_PRIVATE"));
    }

    @Test
    void pageBudgetIsCheckedBeforeOpeningAndShortPagesDoNotSkipLaterPages() {
        final AtomicInteger opened = new AtomicInteger();
        final var tooMany =
                MapperCharacterization.compare(
                        ENTITY,
                        101,
                        page -> {
                            opened.incrementAndGet();
                            return input(VALID);
                        },
                        page -> expected("NONE"));
        assertIncomplete(tooMany, "PAGE_LIMIT");
        assertEquals(0, opened.get());
        final var hundred =
                MapperCharacterization.compare(
                        ENTITY,
                        100,
                        page -> {
                            opened.incrementAndGet();
                            return input(page == 1 ? "{\"data\":[]}" : VALID);
                        },
                        page -> page == 1 ? JSON.createArrayNode() : expected("NONE"));
        assertTrue(hundred.matches());
        assertEquals(100, opened.get());
        assertEquals(99, hundred.sanitized().path("rows").intValue());
    }

    @Test
    void totalRowLimitAppliesAcrossPagesAndDoesNotHideAnUnprocessedPage() {
        final String pageOfFifty = "{\"data\":[" + "{},".repeat(49) + "{}]}";
        final var rows = JSON.createArrayNode();
        for (int i = 0; i < 50; i++) {
            rows.addObject().put("/quarantine", "MISSING_SOURCE_KEY");
        }
        final var exact =
                MapperCharacterization.compare(
                        ENTITY, 20, page -> input(pageOfFifty), page -> rows);
        assertTrue(exact.matches());
        assertEquals(1_000, exact.sanitized().path("rows").intValue());
        final var overflow =
                MapperCharacterization.compare(
                        ENTITY, 21, page -> input(pageOfFifty), page -> rows);
        assertIncomplete(overflow, "ROW_LIMIT");
        assertEquals(1_000, overflow.sanitized().path("rows").intValue());
    }

    @Test
    void rejectsMissingExpectationsAndUnexpectedQuarantine() {
        final var noExpectations =
                MapperCharacterization.compare(
                        ENTITY, 1, page -> input(VALID), page -> JSON.createArrayNode());
        assertIncomplete(noExpectations, "EXPECTED_ROW_COUNT_MISMATCH");
        final var noDisposition =
                JSON.createArrayNode().addObject().put("/sequence_code/typed", "INTEGER:1");
        assertIncomplete(
                MapperCharacterization.compare(
                        ENTITY,
                        1,
                        page -> input(VALID),
                        page -> JSON.createArrayNode().add(noDisposition)),
                "INVALID_EXPECTATIONS");
        final var refusedValid =
                MapperCharacterization.compare(
                        ENTITY,
                        1,
                        page -> input("{\"data\":[{\"sequence_code\":1}]}"),
                        page -> expected("NONE"));
        assertFalse(refusedValid.matches());
        assertEquals("DIVERGED", refusedValid.sanitized().path("status").textValue());
        assertEquals(
                "/quarantine",
                refusedValid.sanitized().path("differences").get(0).path("path").textValue());
    }

    @Test
    void redactsBothSidesAndGroupsStableDifferencesByFieldAndRule() throws Exception {
        final String privateValue = "SYNTH_PRIVATE_NAME_123456789";
        final String envelope =
                "{\"data\":[{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_uer_name\":\""
                        + privateValue
                        + "\"}]}";
        final JsonNode expected = expected("NONE");
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get(0))
                .put("/qoe_uer_name/typed", "SYNTH_PRIVATE_EXPECTED");
        final var first =
                MapperCharacterization.compare(
                        ENTITY, 1, page -> input(envelope), page -> expected);
        final var second =
                MapperCharacterization.compare(
                        ENTITY, 1, page -> input(envelope), page -> expected);
        assertEquals(first.toString(), second.toString());
        assertFalse(first.toString().contains(privateValue));
        assertFalse(first.toString().contains("SYNTH_PRIVATE_EXPECTED"));
        assertFalse(first.matches());
        assertEquals(1, first.sanitized().path("differences").size());
        final var unknown =
                JSON.createArrayNode()
                        .addObject()
                        .put("/quarantine", "NONE")
                        .put("/" + privateValue, "NONE");
        final var rejected =
                MapperCharacterization.compare(
                        ENTITY,
                        1,
                        page -> input(envelope),
                        page -> JSON.createArrayNode().add(unknown));
        assertIncomplete(rejected, "UNKNOWN_EXPECTATION_PATH");
        assertFalse(rejected.toString().contains(privateValue));
    }

    private static MapperCharacterization.Report compare(final String envelope) {
        return MapperCharacterization.compare(
                ENTITY, 1, page -> input(envelope), page -> expected("NONE"));
    }

    private static JsonNode expected(final String reason) {
        final var result = JSON.createArrayNode();
        result.addObject().put("/quarantine", reason);
        return result;
    }

    private static InputStream input(final String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertIncomplete(
            final MapperCharacterization.Report report, final String reason) {
        assertFalse(report.matches());
        assertEquals("INCOMPLETE", report.sanitized().path("status").textValue());
        if (reason != null) {
            assertTrue(report.toString().contains(reason), report::toString);
        }
    }

    @Test
    void preservesDifferentNumericLexemesAcrossRowsAndIgnoresUnrelatedEnvelopeMetadata()
            throws Exception {
        final String envelope =
                """
                {"metadata":{"nested":["{}",1]},"data":[
                  {"corporation_sequence_number":1,"service_at":"2036-03-20", "invoices_volumes":0,
                   "fit_dyn_name":"SYNTH_\\\"{}🚀"},
                  {"corporation_sequence_number":2,"service_at":"2036-03-20", "invoices_volumes":-0}
                ],"after":true}
                """;
        final JsonNode expected =
                JSON.readTree(
                        """
                [{"/quarantine":"NONE","/invoices_volumes/rawWireLexeme":"0"},
                 {"/quarantine":"INVALID_INVOICES_VOLUMES","/invoices_volumes/rawWireLexeme":"-0"}]
                """);
        final var result =
                MapperCharacterization.compare(
                        MapperProjection.Entity.LOCALIZACAO_CARGAS,
                        1,
                        page -> input(envelope),
                        page -> expected);
        assertTrue(result.matches(), result::toString);
        assertEquals(1, result.sanitized().path("valid").intValue());
        assertEquals(1, result.sanitized().path("quarantined").intValue());
    }
}
