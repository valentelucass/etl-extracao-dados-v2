package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteFreshnessOrigin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FreteDataExportRecordMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FreteDataExportRecordMapper mapper = new FreteDataExportRecordMapper();

    @Test
    void mapsTheOnlyContractedIdentityAndKeepsAliasOutOfIdentity() throws Exception {
        final var record = mapper.map(1, json.readTree(validPayload()));

        assertFalse(record.quarantined());
        assertEquals("INTEGER:6389001", record.sourceKey().storageValue());
        assertTrue(
                record.businessAliasJson()
                        .contains("VERSIONED_NON_TECHNICAL_ALIAS_NEVER_IDENTITY"));
        assertTrue(record.businessAliasJson().contains("6389009"));
        assertFalse(record.sourceKey().storageValue().contains("6389009"));
        assertTrue(record.fieldPresenceJson().contains("reference_number\":\"VALUE"));
        assertTrue(record.fieldPresenceJson().contains("updated_at\":\"VALUE"));
        assertFalse(record.fieldPresenceJson().contains("referenceNumber"));
        assertFalse(record.fieldPresenceJson().contains("pickItemId"));
    }

    @Test
    void rejectsWrongKeyTypeZeroNegativeAndOverflowWithoutCoercion() throws Exception {
        assertEquals(
                "INVALID_SOURCE_KEY_TYPE",
                mapper.map(1, json.readTree(payloadWith("\"id\":\"6389\"")))
                        .quarantineReasonCode());
        assertEquals(
                "INVALID_SOURCE_KEY_VALUE",
                mapper.map(1, json.readTree(payloadWith("\"id\":0"))).quarantineReasonCode());
        assertEquals(
                "INVALID_SOURCE_KEY_VALUE",
                mapper.map(1, json.readTree(payloadWith("\"id\":-1"))).quarantineReasonCode());
        assertEquals(
                "INVALID_SOURCE_KEY_VALUE",
                mapper.map(1, json.readTree(payloadWith("\"id\":9223372036854775808")))
                        .quarantineReasonCode());
    }

    @Test
    void usesOneFreshnessPolicyAndNeverUsesUpdatedAt() throws Exception {
        final var record = mapper.map(1, json.readTree(validPayload()));
        assertEquals(FreteFreshnessOrigin.CTE_CREATED_AT, record.freshnessOrigin());
        assertEquals("2036-03-20T12:00:00Z", record.freshnessAtUtc().toString());
        assertTrue(record.freshnessEvidenceJson().contains("updated_at\":\"IGNORED_UNVERIFIED"));

        final var onlyUpdated =
                mapper.map(1, json.readTree("{\"id\":1,\"updated_at\":\"2099-01-01T00:00:00Z\"}"));
        assertEquals("MISSING_VALID_FRESHNESS", onlyUpdated.quarantineReasonCode());
    }

    @Test
    void aPresentInvalidHigherPriorityTimestampBlocksEveryFallback() throws Exception {
        final var record =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"id":1,"cte_created_at":"invalid",
                                 "cte_issued_at":"2036-03-19T10:00:00Z",
                                 "criado_em":"2036-03-18T10:00:00Z",
                                 "servico_em":"2036-03-17T10:00:00Z"}
                                """));

        assertEquals("INVALID_CTE_CREATED_AT", record.quarantineReasonCode());
    }

    @Test
    void distinguishesAbsentNullAndValueForProtectedGroups() throws Exception {
        final var record =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"id":1,"corporation_sequence_number":null,
                                 "cte_created_at":"2036-03-20T12:00:00Z",
                                 "fit_dpn_performance_finished_at":null,
                                 "finished_at":"03/19/2036 15:42:10",
                                 "cte":null,"total":0}
                                """));

        assertFalse(record.quarantined());
        assertTrue(record.fieldPresenceJson().contains("corporation_sequence_number\":\"NULL"));
        assertTrue(record.fieldPresenceJson().contains("finalizations\":\"ABSENT"));
        assertTrue(record.fieldPresenceJson().contains("total\":\"VALUE"));
        assertTrue(record.financialJson().contains("typedDecimal\":\"0"));
    }

    @Test
    void officialPerformanceWinsAndInvalidOfficialCannotFallBack() throws Exception {
        final var valid = mapper.map(1, json.readTree(validPayload()));
        assertEquals("OFFICIAL_6389", valid.performanceOrigin());
        assertEquals("2036-03-19T18:42:10Z", valid.performanceAtUtc().toString());

        final var invalid =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"id":1,"cte_created_at":"2036-03-20T12:00:00Z",
                                 "fit_dpn_performance_finished_at":"03/04/2036 10:00:00",
                                 "finished_at":"03/19/2036 10:00:00"}
                                """));
        assertEquals("AMBIGUOUS_OR_INVALID_PERFORMANCE_DATE", invalid.quarantineReasonCode());
    }

    @Test
    void declaredFallbackKeepsExplicitProvenance() throws Exception {
        final var record =
                mapper.map(
                        1,
                        json.readTree(
                                """
                                {"id":1,"cte_created_at":"2036-03-20T12:00:00Z",
                                 "finished_at":"19/03/2036 15:42:10"}
                                """));

        assertEquals("FINISHED_AT_FALLBACK", record.performanceOrigin());
        assertTrue(record.performanceEvidenceJson().contains("DECLARED_FALLBACK"));
    }

    @Test
    void terminalityUsesOnlyExactCatalogCodes() throws Exception {
        final var terminal = mapper.map(1, json.readTree(payloadWith("\"status\":\"finished\"")));
        final var similar = mapper.map(1, json.readTree(payloadWith("\"status\":\"Finished\"")));

        assertTrue(terminal.terminal());
        assertEquals("finalizado", terminal.statusLabel());
        assertFalse(similar.terminal());
        assertNull(similar.statusCode());
        assertEquals("Finished", similar.statusRaw());
    }

    @Test
    void invalidFinancialValuesAreNeverConvertedToZeroOrCurrency() throws Exception {
        final var invalid = mapper.map(1, json.readTree(payloadWith("\"total\":\"not-a-number\"")));
        final var valid = mapper.map(1, json.readTree(payloadWith("\"total\":\"10.2500\"")));

        assertEquals("INVALID_FINANCIAL_VALUE", invalid.quarantineReasonCode());
        assertFalse(valid.quarantined());
        assertTrue(valid.financialJson().contains("UNRESOLVED_NO_INFERENCE"));
        assertFalse(valid.financialJson().contains("BRL"));
    }

    @Test
    void marksUncontractedDecisionFieldsAsSyntheticV09Evidence() throws Exception {
        final var record = mapper.map(1, json.readTree(validPayload()));
        assertTrue(
                record.freshnessEvidenceJson()
                        .contains("SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE"));
        assertTrue(
                record.cteFinalizationsJson()
                        .contains("SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE"));
    }

    private static String validPayload() {
        return """
               {"id":6389001,"corporation_sequence_number":6389009,
                "updated_at":"2099-01-01T00:00:00Z",
                "cte_created_at":"2036-03-20T12:00:00Z",
                "cte_issued_at":"2036-03-19T12:00:00Z",
                "criado_em":"2036-03-18T12:00:00Z",
                "servico_em":"2036-03-17T12:00:00Z",
                "fit_dpn_performance_finished_at":"03/19/2036 15:42:10",
                "finished_at":"03/18/2036 15:42:10","status":"done",
                "reference_number":"REF-SYNTHETIC","total":"10.2500",
                "fit_p_m_pck_sequence_code":11}
               """;
    }

    private static String payloadWith(final String field) {
        final String identity = field.startsWith("\"id\":") ? "" : "\"id\":1,";
        return "{" + identity + field + ",\"cte_created_at\":\"2036-03-20T12:00:00Z\"}";
    }
}
