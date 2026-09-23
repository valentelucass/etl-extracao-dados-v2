package br.com.esl.etl.v2.modulos.cotacoes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoFreshnessOrigin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CotacaoDataExportRecordMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CotacaoDataExportRecordMapper mapper = new CotacaoDataExportRecordMapper();

    @Test
    void appliesCot01PrecedenceAndTriState() throws Exception {
        final var record =
                mapper.map(
                        1,
                        json.readTree(
                                """
          {"sequence_code":6906001,"requested_at":"2026-07-15",
           "qoe_qes_fit_fhe_cte_issued_at":"2026-07-16",
           "qoe_qes_fit_nse_issued_at":"2026-07-17","qoe_qes_total":"10.2500",
           "qoe_uer_name":"  José  ","qoe_qes_ony_sae_code":"sp",
           "qoe_qes_diy_sae_code":"rj"}
          """));
        assertFalse(record.quarantined());
        assertEquals(CotacaoFreshnessOrigin.NFSE_ISSUED_AT, record.freshnessOrigin());
        assertEquals("2026-07-17", record.freshnessBusinessDate().toString());
        assertEquals("José", record.userNameNormalized());
        assertEquals("SP", record.originUf());
        assertEquals("RJ", record.destinationUf());
        assertEquals(9, json.readTree(record.fieldPresenceJson()).size());
        assertEquals(
                "VALUE",
                json.readTree(record.fieldPresenceJson()).path("sequence_code").textValue());
        assertTrue(record.fieldPresenceJson().contains("\"qoe_qes_ony_sae_code\":\"VALUE\""));
        assertTrue(record.fieldPresenceJson().contains("\"qoe_qes_diy_sae_code\":\"VALUE\""));
    }

    @Test
    void quarantinesInvalidKeyAndNeverConvertsInvalidAmountToZero() throws Exception {
        assertTrue(
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":\"x\",\"requested_at\":\"2026-07-15\"}"))
                        .quarantined());
        assertEquals(
                "INVALID_TOTAL_AMOUNT",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":\"bad\"}"))
                        .quarantineReasonCode());
        assertEquals(
                "INVALID_TOTAL_AMOUNT",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":\"1.00001\"}"))
                        .quarantineReasonCode());
        assertEquals(
                "INVALID_COTACAO_ATTRIBUTE",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_ony_sae_code\":10}"))
                        .quarantineReasonCode());
    }

    @Test
    void quarantinesNonPositiveSequenceCodesRequiredToBeValidLegacyIds() throws Exception {
        assertEquals(
                "INVALID_SOURCE_KEY_VALUE",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":0,\"requested_at\":\"2026-07-15\"}"))
                        .quarantineReasonCode());
        assertEquals(
                "INVALID_SOURCE_KEY_VALUE",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":-1,\"requested_at\":\"2026-07-15\"}"))
                        .quarantineReasonCode());
    }

    @Test
    void acceptsThePositiveSignedBigintDomainAndQuarantinesOverflow() throws Exception {
        final var aboveInteger =
                mapper.map(
                        1,
                        json.readTree(
                                "{\"sequence_code\":2147483648,\"requested_at\":\"2026-07-15\"}"));
        final var maximum =
                mapper.map(
                        1,
                        json.readTree(
                                "{\"sequence_code\":9223372036854775807,\"requested_at\":\"2026-07-15\"}"));
        final var overflow =
                mapper.map(
                        1,
                        json.readTree(
                                "{\"sequence_code\":9223372036854775808,\"requested_at\":\"2026-07-15\"}"));

        assertFalse(aboveInteger.quarantined());
        assertEquals("INTEGER:2147483648", aboveInteger.sourceKey().storageValue());
        assertFalse(maximum.quarantined());
        assertEquals("INTEGER:9223372036854775807", maximum.sourceKey().storageValue());
        assertTrue(overflow.quarantined());
        assertEquals("INVALID_SOURCE_KEY_VALUE", overflow.quarantineReasonCode());
        assertEquals(null, overflow.sourceKey());
        assertEquals(null, overflow.payloadJson());
    }

    @Test
    void enforcesTheFullDecimal19Scale4RangeAndKeepsZeroAsAValue() throws Exception {
        assertEquals(
                "INVALID_TOTAL_AMOUNT",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":\"1000000000000000\"}"))
                        .quarantineReasonCode());
        assertEquals(
                "INVALID_TOTAL_AMOUNT",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":\"1E+15\"}"))
                        .quarantineReasonCode());

        final var maximum =
                mapper.map(
                        1,
                        json.readTree(
                                "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":\"999999999999999.9999\"}"));
        final var zero =
                mapper.map(
                        1,
                        json.readTree(
                                "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_total\":0}"));

        assertFalse(maximum.quarantined());
        assertEquals("999999999999999.9999", maximum.totalAmount().toPlainString());
        assertFalse(zero.quarantined());
        assertEquals(0, zero.totalAmount().signum());
    }

    @Test
    void normalizesUserOnlyAsDisplayAttribute() {
        assertEquals("José", CotacaoDataExportRecordMapper.normalizeUser("  José  "));
    }

    @Test
    void quarantinesInvalidPresentDateRatherThanChangingFreshnessPrecedence() throws Exception {
        assertEquals(
                "INVALID_COTACAO_DATE",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_fit_nse_issued_at\":\"bad\"}"))
                        .quarantineReasonCode());
    }

    @Test
    void keepsAbsentAndNullDistinctWithoutInventingRoutePaths() throws Exception {
        final var record =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"sequence_code":1,"requested_at":"2026-07-15", "qoe_uer_name":null,
                                 "origin_state":"SP","destination_state":"RJ"}
                                """));

        assertFalse(record.quarantined());
        assertEquals(null, record.originUf());
        assertEquals(null, record.destinationUf());
        assertTrue(record.fieldPresenceJson().contains("\"qoe_uer_name\":\"NULL\""));
        assertTrue(record.fieldPresenceJson().contains("\"qoe_qes_ony_sae_code\":\"ABSENT\""));
        assertFalse(record.fieldPresenceJson().contains("origin_state"));
    }

    @Test
    void doesNotCreateAnAsciiUfThroughUnicodeCaseExpansion() throws Exception {
        assertEquals(
                "INVALID_COTACAO_ATTRIBUTE",
                mapper.map(
                                1,
                                json.readTree(
                                        "{\"sequence_code\":1,\"requested_at\":\"2026-07-15\",\"qoe_qes_ony_sae_code\":\"ß\"}"))
                        .quarantineReasonCode());
    }

    @Test
    void usesExactlyTheSameFallbackOrderWhenEarlierFieldsAreAbsentOrNull() throws Exception {
        final var cte =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"sequence_code":1,"qoe_qes_fit_nse_issued_at":null,
                                 "qoe_qes_fit_fhe_cte_issued_at":"2026-07-16","requested_at":"2026-07-15"}
                                """));
        final var requested =
                mapper.map(
                        1, json.readTree("{\"sequence_code\":1,\"requested_at\":\"2026-07-15\"}"));

        assertEquals(CotacaoFreshnessOrigin.CTE_ISSUED_AT, cte.freshnessOrigin());
        assertEquals(CotacaoFreshnessOrigin.REQUESTED_AT, requested.freshnessOrigin());
        assertEquals("2026-07-16", cte.freshnessBusinessDate().toString());
        assertEquals("2026-07-15", requested.freshnessBusinessDate().toString());
    }
}
