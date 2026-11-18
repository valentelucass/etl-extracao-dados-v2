package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesP3RawQualifierTest {
    @TempDir Path directory;

    @Test
    void supplementChangesExactlyNineteenNullablesAndNfseStringWhileP2StaysHistorical()
            throws Exception {
        final var p2 = CotacoesCaptureFixture.contract();
        final var p3 = CotacoesP3RawQualifier.contract();
        final var before = new HashMap<String, JsonNode>();
        p2.path("response")
                .required("fields")
                .forEach(field -> before.put(field.path("path").asText(), field));
        int additions = 0;
        int nullable = 0;
        for (final var rule : p3.path("response").required("fields")) {
            nullable += rule.path("nullable").booleanValue() ? 1 : 0;
            final var old = before.get(rule.path("path").asText());
            if (rule.path("nullable").booleanValue() && !old.path("nullable").booleanValue()) {
                additions++;
                assertTrue(
                        CotacoesP3RawQualifier.NEW_NULLABLE.contains(
                                rule.path("path").asText().substring(1)));
            }
            if ("/qoe_qes_fit_nse_issued_at".equals(rule.path("path").asText())) {
                assertTrue(old.path("jsonTypes").isEmpty());
                assertEquals("STRING", rule.path("jsonTypes").get(0).textValue());
                assertEquals("EXACT_ISO_CIVIL_DATE", rule.path("wireFormat").asText());
            }
        }
        assertEquals(19, additions);
        assertEquals(26, nullable);
        assertEquals(37, before.size());
        assertFalse(p3.path("acceptance").path("runtimeAdopted").booleanValue());
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/contracts/dataexport/cotacoes-6906-observed-p2-info-qualified-20261025.json")) {
            assertTrue(stream != null);
            assertEquals(
                    CotacoesP3RawQualifier.P2_SHA,
                    ExportValidationSupport.sha256(stream.readAllBytes()));
        }
    }

    @Test
    void producer02ShapedRowsWithNineteenLegNullsAndRealCivilNfseAreRequalifiedWithoutRewriting()
            throws Exception {
        final var nulls = CotacoesCaptureFixture.row(1);
        CotacoesP3RawQualifier.NEW_NULLABLE.forEach(nulls::putNull);
        final var nfse =
                CotacoesCaptureFixture.row(2).put("qoe_qes_fit_nse_issued_at", "2026-10-13");
        final byte[] bytes = lines(nulls, nfse);
        final var pin = pin(bytes);
        final var result = qualify(pin);
        assertTrue(result.path("rowTypesQualified").booleanValue());
        assertEquals(2, result.path("rows").intValue());
        assertEquals(0, result.path("rejectedRows").intValue());
        assertTrue(result.path("allRawRowsReadToEof").booleanValue());
        assertTrue(result.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertArrayEquals(bytes, Files.readAllBytes(Path.of(pin.path("path").textValue())));
        for (final String flag :
                List.of(
                        "sourceBytesModified",
                        "producerReceiptsModified",
                        "trustedSourceCompleteness",
                        "contentAccepted",
                        "entityQualified",
                        "mainResumeAllowed",
                        "productionAuthorized",
                        "runtimeAdopted")) {
            assertTrue(result.path(flag).isBoolean());
            assertFalse(result.path(flag).booleanValue());
        }
    }

    @Test
    void nullOutsideObservedNineteenAndHistoricalSevenStaysUnqualified() throws Exception {
        final var row = CotacoesCaptureFixture.row(1).putNull("qoe_cor_document");
        final var result = qualify(pin(lines(row)));
        assertFalse(result.path("rowTypesQualified").booleanValue());
        assertEquals(1, result.path("rejectedRows").intValue());
        assertEquals(1, result.path("refusalCounts").path("/qoe_cor_document:NULL").intValue());
    }

    @Test
    void nfseCivilDatesIncludeLeapDayButNeverTrimOrTruncateTimestampOrRepairInvalidDay()
            throws Exception {
        for (final String value : List.of("2024-02-29", "2026-10-13", "9999-12-31")) {
            assertTrue(
                    qualify(
                                    pin(
                                            lines(
                                                    CotacoesCaptureFixture.row(1)
                                                            .put(
                                                                    "qoe_qes_fit_nse_issued_at",
                                                                    value))))
                            .path("rowTypesQualified")
                            .booleanValue());
        }
        for (final String value :
                List.of(
                        "2026-02-29",
                        "2026-02-30",
                        " 2026-10-13",
                        "2026-10-13 ",
                        "2026-10-13T00:00:00-03:00",
                        "13/10/2026",
                        "2026-1-3",
                        "")) {
            final var result =
                    qualify(
                            pin(
                                    lines(
                                            CotacoesCaptureFixture.row(1)
                                                    .put("qoe_qes_fit_nse_issued_at", value))));
            assertFalse(result.path("rowTypesQualified").booleanValue(), value);
            assertEquals(
                    1,
                    result.path("refusalCounts")
                            .path("/qoe_qes_fit_nse_issued_at:TYPE_OR_FORMAT")
                            .intValue());
        }
    }

    @Test
    void unrelatedNewNonNullTypeIsNotApprovedByNfseSupplement() throws Exception {
        final var row = CotacoesCaptureFixture.row(1).put("qoe_qes_status", true);
        final var result = qualify(pin(lines(row)));
        assertFalse(result.path("rowTypesQualified").booleanValue());
        assertEquals(
                1, result.path("refusalCounts").path("/qoe_qes_status:TYPE_OR_FORMAT").intValue());
    }

    @Test
    void lastBadRowCannotHideBehindQualifiedPrefixAndDuplicateOccurrencesRemain() throws Exception {
        final var good = CotacoesCaptureFixture.row(1);
        final var bad = CotacoesCaptureFixture.row(2).putNull("qoe_cor_document");
        final var result = qualify(pin(lines(good, good.deepCopy(), bad)));
        assertEquals(3, result.path("rows").intValue());
        assertEquals(1, result.path("rejectedRows").intValue());
        assertFalse(result.path("rowTypesQualified").booleanValue());
        assertTrue(result.path("allRawRowsReadToEof").booleanValue());
        assertFalse(result.path("deduplicationPerformed").booleanValue());
    }

    @Test
    void missingOrExtraFieldRefusesExact37InsteadOfApprovingSubset() throws Exception {
        final var missing = CotacoesCaptureFixture.row(1);
        missing.remove("requester_name");
        assertThrows(IllegalArgumentException.class, () -> qualify(pin(lines(missing))));
        final var extra = CotacoesCaptureFixture.row(1).put("unobserved", "value");
        assertThrows(IllegalArgumentException.class, () -> qualify(pin(lines(extra))));
    }

    @Test
    void wrongEnvelopeTrailingJsonAndMissingFinalLfNeverQualify() throws Exception {
        final byte[] valid = lines(CotacoesCaptureFixture.row(1));
        final String text = new String(valid, StandardCharsets.UTF_8);
        for (final String altered :
                List.of(
                        text.replace("\"COTACOES\"", "\"MANIFESTOS\""),
                        text.replace("\"templateId\":6906", "\"templateId\":6399"),
                        text.stripTrailing(),
                        text + "{}\n")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> qualify(pin(altered.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    void realZeroByteRetentionStillRequiresActualEmptyTerminalArray() throws Exception {
        final var source = pin(new byte[0]);
        final var result = qualify(source);
        assertTrue(result.path("rowTypesQualified").booleanValue());
        assertTrue(result.path("emptyPayloadWitnessVerified").booleanValue());
        assertEquals(0, result.path("rows").intValue());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CotacoesP3RawQualifier.qualify(
                                request(),
                                source,
                                pin("{\"data\":[]}".getBytes(StandardCharsets.UTF_8)),
                                budget()));
    }

    @Test
    void payloadMutationAtPostcheckCannotReturnSuccessfulQualification() throws Exception {
        final var source = pin(lines(CotacoesCaptureFixture.row(1)));
        assertEquals(
                "EXPORT_PIN_CHANGED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CotacoesP3RawQualifier.qualifyWithPostcheckProbe(
                                                request(),
                                                source,
                                                terminal(),
                                                budget(),
                                                () -> {
                                                    try {
                                                        Files.writeString(
                                                                Path.of(
                                                                        source.path("path")
                                                                                .textValue()),
                                                                "changed",
                                                                StandardCharsets.UTF_8);
                                                    } catch (final java.io.IOException failure) {
                                                        throw new UncheckedIOException(failure);
                                                    }
                                                }))
                        .getMessage());
    }

    @Test
    void requestedAtOutsideDayAndNonintegralIdentityAreNotRequalified() throws Exception {
        final var row =
                CotacoesCaptureFixture.row(1).put("requested_at", "2026-10-14T00:00:00-03:00");
        final var result = qualify(pin(lines(row)));
        assertFalse(result.path("rowTypesQualified").booleanValue());
        assertEquals(
                1,
                result.path("refusalCounts")
                        .path("/requested_at:INVALID_OR_OUTSIDE_PERIOD")
                        .intValue());
        row.put("requested_at", "2026-10-13T12:34:56-03:00")
                .put("sequence_code", new java.math.BigDecimal("1.0"));
        assertFalse(qualify(pin(lines(row))).path("rowTypesQualified").booleanValue());
    }

    @Test
    void newCollectorUsesSameP3RulesAndRetainsObservedNullAndDateBytes() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var row =
                    CotacoesCaptureFixture.row(1).put("qoe_qes_fit_nse_issued_at", "2026-10-13");
            CotacoesP3RawQualifier.NEW_NULLABLE.forEach(row::putNull);
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertEquals(0, result.path("rowTypeRejectedOccurrences").intValue());
            final var retained =
                    Files.readAllLines(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path());
            assertEquals(
                    ExportValidationSupport.JSON.readTree(row.toString()),
                    ExportValidationSupport.JSON.readTree(retained.get(0)).required("record"));
        }
    }

    @Test
    void invalidNfseCivilDateRemainsRetainedButNewCollectorDoesNotQualifyItsType()
            throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var row =
                    CotacoesCaptureFixture.row(1).put("qoe_qes_fit_nse_issued_at", "2026-02-30");
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertEquals(1, result.path("rows").intValue());
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    private ObjectNode qualify(final JsonNode pin) throws Exception {
        return CotacoesP3RawQualifier.qualify(request(), pin, terminal(), budget());
    }

    private ObjectNode pin(final byte[] bytes) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + ".jsonl").toAbsolutePath();
        Files.write(path, bytes);
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("path", path.toString())
                .put("bytes", bytes.length)
                .put("sha256", ExportValidationSupport.sha256(bytes));
    }

    private ObjectNode terminal() throws Exception {
        return pin(" [] \n".getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode request() {
        return CotacoesCaptureFixture.request("2026-10-13", "2026-10-13");
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(150));
    }

    private static byte[] lines(final JsonNode... records) throws Exception {
        final var output = new ByteArrayOutputStream();
        for (final var record : records) {
            final var envelope =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("entity", "COTACOES")
                            .put("templateId", 6906);
            envelope.set("record", record);
            output.write(ExportValidationSupport.JSON.writeValueAsBytes(envelope));
            output.write('\n');
        }
        return output.toByteArray();
    }
}
