package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndependentDashboardBridgeTest {
    private ObjectNode facts() {
        final var facts =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_DASHBOARD_GOVERNANCE_V1")
                        .put("sourceKey", "101")
                        .put("excludedAtSource", false)
                        .put("publicationSnapshotUtc", "2036-03-27T15:00:00Z")
                        .put("exclusionEvidencePin", "SYNTHETIC-MAPPING-ONLY")
                        .put("snapshotEvidencePin", "SYNTHETIC-MAPPING-ONLY");
        final var region = facts.putObject("logisticsRegion");
        region.put("sourceKey", "101")
                .put("postalCode", "SYNTHETIC-16")
                .put("city", "SYNTHETIC-14")
                .put("state", "SYNTHETIC-15")
                .put("validFrom", "2036-01-01")
                .put("validThrough", "2036-12-31")
                .put("name", "SYNTHETIC GOVERNED LOGISTICS")
                .put("evidencePin", "SYNTHETIC-MAPPING-ONLY");
        return facts;
    }

    @Test
    void coletaProducesThe21ConsumerColumnsWithIndependentRegionAndPublicationSnapshot() {
        final var row =
                IndependentDashboardBridge.coleta(
                        ColetasCommon31SyntheticFixture.sourceRow(), facts());
        assertEquals("dbo.vw_coletas_powerbi", row.target());
        assertEquals(21, row.columns().size());
        assertEquals("Pendente", row.columns().get("Status"));
        assertEquals(2, row.columns().get("Volumes"));
        assertEquals("SYNTHETIC GOVERNED LOGISTICS", row.columns().get("Região Logística"));
        assertEquals(LocalDateTime.of(2036, 3, 27, 12, 0), row.columns().get("Data de extracao"));
        assertNotEquals(
                ColetasCommon31SyntheticFixture.sourceRow().path("pck_prn_drt_nickname").asText(),
                row.columns().get("Região Logística"));
    }

    @Test
    void volumeOverflowAndUnknownRegionFactsCannotBecomeConsumerValues() {
        final var source =
                ColetasCommon31SyntheticFixture.sourceRow().put("invoices_volumes", 2147483648L);
        assertThrows(
                ArithmeticException.class,
                () -> IndependentDashboardBridge.coleta(source, facts()));
        source.put("invoices_volumes", 2);
        final var wrong = facts();
        wrong.withObject("logisticsRegion").put("city", "ANOTHER SYNTHETIC CITY");
        assertThrows(
                IllegalArgumentException.class,
                () -> IndependentDashboardBridge.coleta(source, wrong));
    }

    @Test
    void expandedOccurrencesCannotBeSilentlyDeduplicatedForPublication() {
        final var row =
                IndependentDashboardBridge.coleta(
                        ColetasCommon31SyntheticFixture.sourceRow(), facts());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        IndependentDashboardBridge.requireUniquePublicationGrain(
                                List.of(row, row), "ID"));
        assertThrows(UnsupportedOperationException.class, () -> row.columns().put("ID", "202"));
    }

    @Test
    void freightBillingDateRemainsIndependentOfItsSourceServicePeriod() {
        final var source =
                ColetasCommon31SyntheticFixture.sourceRow()
                        .put("service_at", "2036-03-20T10:00:00-03:00")
                        .put("corporation_sequence_number", 9001);
        for (final String field :
                List.of(
                        "fit_pyr_name",
                        "fit_sdr_nickname",
                        "fit_rpt_nickname",
                        "fit_ony_sae_code",
                        "fit_diy_sae_code",
                        "fit_dos_cty_name",
                        "fit_crn_psn_nickname",
                        "fit_fsn_name")) {
            source.put(field, "SYNTHETIC");
        }
        final var facts =
                facts().put("sourceServiceAt", "2036-03-20T10:00:00-03:00").put("courtesy", false);
        for (final String field :
                List.of("responsavel_regiao_destino", "status_frete", "tipo_frete", "modal")) {
            facts.put(field, "SYNTHETIC GOVERNED");
        }
        facts.putObject("billing")
                .put("referenceAt", "2036-04-01T00:00:00-03:00")
                .put("grossRevenue", "123.456789")
                .put("freightValue", "100.000001")
                .put("eligible", true)
                .put("evidencePin", "SYNTHETIC-MAPPING-ONLY")
                .put("governedRevision", 1)
                .putNull("cteId")
                .putNull("cteNumber")
                .putNull("nfseNumber")
                .putNull("cteIssuedAt");
        final var row = IndependentDashboardBridge.frete(source, facts);
        assertEquals(9001L, row.columns().get("numero_minuta"));
        assertEquals(
                LocalDate.of(2036, 4, 1), row.columns().get("data_referencia_faturamento_date"));
        assertNotEquals(
                LocalDate.of(2036, 3, 20), row.columns().get("data_referencia_faturamento_date"));
        assertFalse(row.columns().containsKey("valor_icms"));
        assertFalse(
                IndependentDashboardBridge.contract()
                        .path("consumerRoundtripVerified")
                        .asBoolean());
    }
}
