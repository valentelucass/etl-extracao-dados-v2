package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LegacyFreightBillingRulesTest {
    private ObjectNode source() {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("id", 101)
                .put("sequence_code", 7)
                .put("corporation_sequence_number", 9001)
                .put("courtesy", false)
                .put("status", "pending")
                .put("fit_fsn_name", "SYNTHETIC CLASSIFICATION")
                .put("fit_fhe_cte_number", 42)
                .put("fit_fhe_cte_series", 1)
                .put("fit_fhe_cte_key", "SYNTHETIC_CTE")
                .put("service_at", "2026-03-01T10:00:00-03:00")
                .put("total", new BigDecimal("123.45"));
    }

    private ObjectNode references() {
        final var refs =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "LEGACY_FREIGHT_RAW_REFERENCES_V1")
                        .put("sourceKey", "101");
        refs.putObject("lifecycle").put("excludedAtSource", false);
        refs.putObject("documents")
                .put("cteId", 301)
                .put("cteIssuedAt", "2026-03-04T12:34:56-03:00");
        refs.putObject("fiscal")
                .put("rankedSnapshotComplete", true)
                .put("statusCte", "authorized")
                .putNull("statusCteResult");
        refs.putObject("calendar")
                .put("sourceDay", "2026-03-04")
                .put("referenceDay", "2026-03-03")
                .put("previousBusinessDaySnapshotVerified", true);
        refs.putObject("localizacao")
                .put("sequenceNumber", 9001)
                .put("activeJoinSnapshotComplete", true)
                .put("destinationBranchNickname", " SYNTHETIC BRANCH ");
        refs.putObject("rawFrete").put("subtotal", new BigDecimal("100.25"));
        return refs;
    }

    @Test
    void legacyMinutaAndBillingReferenceUseTheVersionedIndependentInputs() {
        final var result = LegacyFreightBillingRules.derive(source(), references());
        assertEquals(9001, result.path("numeroMinuta").asInt());
        assertEquals("2026-03-03T00:00-03:00", result.path("referenceAt").asText());
        assertTrue(result.path("referenceWasRetroacted").asBoolean());
        assertEquals(new BigDecimal("123.45"), result.path("grossRevenue").decimalValue());
        assertEquals("synthetic branch", result.path("responsavel_regiao_destino_key").asText());
        assertFalse(result.path("semanticApprovalIssued").asBoolean());
    }

    @Test
    void fiscalCancellationZeroesAmountsUnderTheExplicitLegacyRule() {
        final var refs = references();
        ((ObjectNode) refs.required("fiscal")).put("statusCte", "cancelado");
        final var result = LegacyFreightBillingRules.derive(source(), refs);
        assertTrue(result.path("cteCanceled").asBoolean());
        assertFalse(result.path("eligible").asBoolean());
        assertEquals(0, result.path("grossRevenue").decimalValue().signum());
        assertEquals(0, result.path("freightValue").decimalValue().signum());
    }

    @Test
    void wrongMinutaAndMissingFiscalCompletenessCannotBecomeReferenceFacts() {
        final var refs = references();
        ((ObjectNode) refs.required("localizacao")).put("sequenceNumber", 7);
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyFreightBillingRules.derive(source(), refs));
        ((ObjectNode) refs.required("localizacao")).put("sequenceNumber", 9001);
        ((ObjectNode) refs.required("fiscal")).put("rankedSnapshotComplete", false);
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyFreightBillingRules.derive(source(), refs));
    }

    @Test
    void unknownUnicodeCollationIsPendingInsteadOfApproximateFolding() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LegacyFreightBillingRules.derive(
                                source().put("fit_fsn_name", "Bloqueio - Anulação"), references()));
    }

    @Test
    void freightStatusFallbackIsExactWhileFiscalFieldsRetainSubstringCancellation() {
        final var refs = references();
        final var prefix =
                LegacyFreightBillingRules.derive(source().put("status", "pre-cancelado"), refs);
        assertFalse(prefix.path("cteCanceled").asBoolean());
        assertTrue(prefix.path("eligible").asBoolean());
        assertEquals(new BigDecimal("123.45"), prefix.path("grossRevenue").decimalValue());
        assertTrue(
                LegacyFreightBillingRules.derive(source().put("status", "cancelado"), refs)
                        .path("cteCanceled")
                        .asBoolean());
        ((ObjectNode) refs.required("fiscal")).put("statusCte", "pre-cancelado");
        assertTrue(
                LegacyFreightBillingRules.derive(source(), refs).path("cteCanceled").asBoolean());
        ((ObjectNode) refs.required("fiscal"))
                .put("statusCte", "authorized")
                .put("statusCteResult", "pre-cancelado");
        assertTrue(
                LegacyFreightBillingRules.derive(source(), refs).path("cteCanceled").asBoolean());
    }
}
