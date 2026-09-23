package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaAttributePresence;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaFieldValue;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaParseState;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStatusDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaDataExportRecordMapperTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LocalizacaoCargaDataExportRecordMapper mapper =
            new LocalizacaoCargaDataExportRecordMapper();

    @Test
    void mapsExactlyTheSeventeenApprovedPathsWithStrictTypedEvidence() throws Exception {
        final var record =
                mapper.map(
                        1,
                        """
                                {
                                  "corporation_sequence_number":8656,
                                  "type":"synthetic",
                                  "service_at":"2036-03-20T12:00:00Z",
                                  "invoices_volumes":0,
                                  "taxed_weight":"12.340000000",
                                  "invoices_value":"99.01",
                                  "total":100.02,
                                  "service_type":"synthetic",
                                  "fit_crn_psn_nickname":"synthetic",
                                  "fit_dpn_delivery_prediction_at":"2036-03-21",
                                  "fit_dyn_name":"synthetic",
                                  "fit_dyn_drt_nickname":"synthetic",
                                  "fit_fsn_name":"synthetic",
                                  "fit_fln_status":"  DELIVERED  ",
                                  "fit_fln_cln_nickname":"synthetic",
                                  "fit_o_n_name":"synthetic",
                                  "fit_o_n_drt_nickname":"synthetic"
                                }
                                """);

        assertFalse(record.quarantined());
        assertEquals("INTEGER:8656", record.sourceKey().storageValue());
        assertEquals(Instant.parse("2036-03-20T12:00:00Z"), record.serviceAtUtc());
        assertEquals(0, record.invoicesVolumes());
        assertEquals(new BigDecimal("12.340000000"), record.taxedWeight());
        assertEquals("delivered", record.statusNormalized());
        assertTrue(record.statusTerminal());
        assertEquals("UNSOURCED_LEGACY", record.statusBranchNicknameProvenance());
        assertTrue(record.fieldPresenceJson().contains("LOCALIZACAO_8656_17_PATHS_V1"));
        assertTrue(record.fieldPresenceJson().contains("\"rawWireLexeme\":\"0\""));
        assertTrue(
                record.fieldPresenceJson()
                        .contains("FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION"));
        assertFalse(record.payloadJson().contains("status_branch_nickname"));
        assertFalse(record.toString().contains("8656"));
    }

    @Test
    void rejectsMissingWrongOrFallbackIdentityAndAnyUnapprovedPath() throws Exception {
        assertReason("{\"service_at\":\"2036-03-20T12:00:00Z\"}", "MISSING_SOURCE_KEY");
        assertReason(
                "{\"corporation_sequence_number\":\"8656\",\"service_at\":\"2036-03-20T12:00:00Z\"}",
                "INVALID_SOURCE_KEY_TYPE");
        assertReason(
                "{\"sequence_number\":8656,\"service_at\":\"2036-03-20T12:00:00Z\"}",
                "UNAPPROVED_SOURCE_PATH");
        assertReason(
                "{\"corporation_sequence_number\":8656,\"service_at\":\"2036-03-20T12:00:00Z\",\"unexpected\":1}",
                "UNAPPROVED_SOURCE_PATH");
    }

    @Test
    void preservesAbsentNullAndZeroAsThreeDifferentStates() throws Exception {
        final var absent = valid("");
        final var explicitNull = valid(",\"invoices_volumes\":null");
        final var zero = valid(",\"invoices_volumes\":0");

        assertEquals(LocalizacaoCargaAttributePresence.ABSENT, absent.invoicesVolumesPresence());
        assertEquals(LocalizacaoCargaParseState.NOT_PRESENT, absent.invoicesVolumesParseState());
        assertNull(absent.invoicesVolumes());
        assertEquals(
                LocalizacaoCargaAttributePresence.NULL, explicitNull.invoicesVolumesPresence());
        assertEquals(
                LocalizacaoCargaParseState.EXPLICIT_NULL, explicitNull.invoicesVolumesParseState());
        assertEquals(LocalizacaoCargaAttributePresence.VALUE, zero.invoicesVolumesPresence());
        assertEquals(LocalizacaoCargaParseState.VALID, zero.invoicesVolumesParseState());
        assertEquals(0, zero.invoicesVolumes());
    }

    @Test
    void quarantinesNumericLocaleOverflowScaleAndTemporalAmbiguityWithoutCoercion()
            throws Exception {
        assertReason(validJson(",\"invoices_volumes\":-1"), "INVALID_INVOICES_VOLUMES");
        assertReason(validJson(",\"invoices_volumes\":-0"), "INVALID_INVOICES_VOLUMES");
        assertReason(validJson(",\"invoices_volumes\":2147483648"), "INVALID_INVOICES_VOLUMES");
        assertReason(validJson(",\"taxed_weight\":\"1,234\""), "INVALID_TAXED_WEIGHT");
        assertReason(validJson(",\"taxed_weight\":1e2"), "INVALID_TAXED_WEIGHT");
        assertReason(validJson(",\"taxed_weight\":\"+1.0\""), "INVALID_TAXED_WEIGHT");
        assertReason(validJson(",\"invoices_value\":\"1.1234567890\""), "INVALID_INVOICES_VALUE");
        assertReason(validJson(",\"total\":\"123456789012345678901234567890.1\""), "INVALID_TOTAL");
        assertReason(
                "{\"corporation_sequence_number\":8656,\"service_at\":\"03/04/2036\"}",
                "INVALID_SERVICE_AT");
        assertReason("{\"corporation_sequence_number\":8656}", "MISSING_VALID_SERVICE_AT");

        final var normalizedTree =
                mapper.map(1, JSON.readTree(validJson(",\"invoices_volumes\":0")));
        assertEquals("UNVERIFIED_NUMERIC_WIRE_LEXEME", normalizedTree.quarantineReasonCode());
    }

    @Test
    void preservesFiveThousandDigitOverflowForQuarantineAndRejectsAboveParserCeiling()
            throws Exception {
        final String boundedOverflow = "9".repeat(5_001);
        final var quarantined = mapper.map(1, validJson(",\"taxed_weight\":" + boundedOverflow));

        assertTrue(quarantined.quarantined());
        assertEquals("INVALID_TAXED_WEIGHT", quarantined.quarantineReasonCode());
        assertEquals(boundedOverflow, quarantined.taxedWeightRaw());
        assertNull(quarantined.taxedWeight());
        final JsonNode evidence =
                LocalizacaoCargaJson.JSON
                        .readTree(quarantined.fieldPresenceJson())
                        .path("fields")
                        .path("taxed_weight");
        assertEquals(boundedOverflow, evidence.path("rawWireLexeme").textValue());
        assertEquals(boundedOverflow, evidence.path("raw").asText());

        final String aboveCeiling =
                "9".repeat(LocalizacaoCargaJson.MAXIMUM_NUMERIC_TOKEN_CHARACTERS + 1);
        final IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mapper.map(1, validJson(",\"taxed_weight\":" + aboveCeiling)));
        assertEquals("O JSON bruto 8656 é inválido.", failure.getMessage());
        assertTrue(
                failure.getCause()
                        instanceof com.fasterxml.jackson.core.exc.StreamConstraintsException);
    }

    @Test
    void keepsExactNumericWireLexemeAndScaleInCanonicalRawEvidence() throws Exception {
        final var record = mapper.map(1, validJson(",\"taxed_weight\":10.250000000"));
        final JsonNode evidence =
                LocalizacaoCargaJson.JSON
                        .readTree(record.fieldPresenceJson())
                        .path("fields")
                        .path("taxed_weight");

        assertFalse(record.quarantined());
        assertEquals("10.250000000", record.taxedWeightRaw());
        assertEquals("10.250000000", evidence.path("rawWireLexeme").textValue());
        assertEquals("10.250000000", evidence.path("raw").asText());
    }

    @Test
    void preservesDecimal38Scale9AndSmallestScale9ValueAcrossTheTriplet() throws Exception {
        assertExactDecimalTriplet("12345678901234567890.123456789");
        assertExactDecimalTriplet("0.000000001");
    }

    @Test
    void convertsUnambiguousSaoPauloCivilTimeAndRejectsDstGapOrOverlap() throws Exception {
        final var unambiguous =
                mapper.map(
                        1,
                        "{\"corporation_sequence_number\":8656,"
                                + "\"service_at\":\"2036-04-20T12:00:00\"}");

        assertFalse(unambiguous.quarantined());
        assertEquals(Instant.parse("2036-04-20T15:00:00Z"), unambiguous.serviceAtUtc());
        assertReason(
                "{\"corporation_sequence_number\":8656,"
                        + "\"service_at\":\"2018-11-04T00:30:00\"}",
                "INVALID_SERVICE_AT");
        assertReason(
                "{\"corporation_sequence_number\":8656,"
                        + "\"service_at\":\"2018-02-17T23:30:00\"}",
                "INVALID_SERVICE_AT");
    }

    @Test
    void normalizesOnlyByTrimAndLowerAndKeepsUnknownStatusNonTerminal() throws Exception {
        final var blank = valid(",\"fit_fln_status\":\"  \"");
        final var unknown = valid(",\"fit_fln_status\":\"Delivered-ish\"");

        assertEquals("sem_status", blank.statusNormalized());
        assertFalse(blank.statusTerminal());
        assertEquals("delivered-ish", unknown.statusNormalized());
        assertFalse(unknown.statusTerminal());
        assertReason(validJson(",\"fit_fln_status\":7"), "INVALID_STATUS_TYPE");
    }

    @Test
    void publicRepresentationsNeverExposeRawTypedIdsOrStatusTokens() throws Exception {
        final String sensitive = "synthetic-sensitive-value-8656";
        final var field =
                new LocalizacaoCargaFieldValue<>(
                        LocalizacaoCargaAttributePresence.VALUE,
                        "/type",
                        '"' + sensitive + '"',
                        LocalizacaoCargaParseState.VALID,
                        sensitive,
                        "DATAEXPORT_8656");
        final var status = LocalizacaoCargaStatusDecision.fromRaw(sensitive);
        final var quarantine =
                mapper.map(
                        1,
                        JSON.readTree(
                                "{\"corporation_sequence_number\":8656,\"unknown\":\""
                                        + sensitive
                                        + "\"}"));

        assertFalse(field.toString().contains(sensitive));
        assertFalse(status.toString().contains(sensitive));
        assertFalse(quarantine.toString().contains(sensitive));
        assertTrue(quarantine.payloadJson().contains(sensitive));
        assertTrue(quarantine.fieldPresenceJson().contains("LOCALIZACAO_8656_17_PATHS_V1"));
    }

    private br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord valid(
            final String fields) throws Exception {
        return mapper.map(1, validJson(fields));
    }

    private static String validJson(final String fields) {
        return "{\"corporation_sequence_number\":8656,\"service_at\":\"2036-03-20T12:00:00Z\""
                + fields
                + "}";
    }

    private void assertReason(final String json, final String reason) throws Exception {
        final var record = mapper.map(1, json);
        assertTrue(record.quarantined());
        assertEquals(reason, record.quarantineReasonCode());
    }

    private void assertExactDecimalTriplet(final String wireLexeme) throws Exception {
        final var record = mapper.map(1, validJson(",\"taxed_weight\":" + wireLexeme));
        final JsonNode evidence =
                LocalizacaoCargaJson.JSON
                        .readTree(record.fieldPresenceJson())
                        .path("fields")
                        .path("taxed_weight");

        assertFalse(record.quarantined());
        assertEquals(new BigDecimal(wireLexeme), record.taxedWeight());
        assertEquals(wireLexeme, record.taxedWeightRaw());
        assertEquals(new BigDecimal(wireLexeme), evidence.path("raw").decimalValue());
        assertEquals(wireLexeme, evidence.path("rawWireLexeme").textValue());
        assertTrue(record.payloadJson().contains("\"taxed_weight\":" + wireLexeme));
        assertTrue(record.fieldPresenceJson().contains("\"raw\":" + wireLexeme));
    }
}
