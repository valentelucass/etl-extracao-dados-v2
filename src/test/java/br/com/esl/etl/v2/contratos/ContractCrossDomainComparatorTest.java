package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContractCrossDomainComparatorTest {

    private static final String CTE_A = "35123456789012345678901234567890123456789012";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void observesTheFreteColetaCandidateWithoutPromotingItToAConfirmedRelation() throws Exception {
        final ContractFreteColetaRelationResult result =
                ContractCrossDomainComparator.compareFreteColeta(
                        List.of(row("{\"fit_p_m_pck_sequence_code\":\"700001\"}")),
                        List.of(row("{\"sequence_code\":\"700001\"}")),
                        List.of(graphQlFrete("pick-item-1", CTE_A)),
                        List.of(graphQlColeta("pick-item-1")));

        assertTrue(result.candidateKeysComplete());
        assertTrue(result.candidateKeysResolveToColetas());
        assertTrue(result.graphQlPickItemBaselineResolves());
        assertFalse(result.cardinalityComparable());
        assertFalse(result.revenueComparable());
        assertFalse(result.relationConfirmed());
        assertFalse(result.toString().contains("700001"));
        assertFalse(result.toString().contains("pick-item-1"));
    }

    @Test
    void comparesOnlyNormalizedCteSetsAndDoesNotRetainTheirValues() throws Exception {
        final ContractCteInvoiceRelationResult result =
                ContractCrossDomainComparator.compareCteInvoice(
                        List.of(
                                row(
                                        "{\"cte_key\":\"35.123456789012345678901234567890123456789012\"}")),
                        "cte_key",
                        List.of(row("{\"cte_key\":\"" + CTE_A + "\"}")),
                        "cte_key",
                        List.of(graphQlFrete("pick-item-1", CTE_A)));

        assertTrue(result.dataExportFreteCtesComplete());
        assertTrue(result.invoiceCtesComplete());
        assertTrue(result.graphQlFreteCtesComplete());
        assertTrue(result.dataExportFreteCtesMatchGraphQl());
        assertTrue(result.invoiceCtesCoverDataExportFretes());
        assertTrue(result.relationConfirmed());
        assertFalse(result.toString().contains(CTE_A));
    }

    @Test
    void rejectsMalformedCteValuesAsIncompleteInsteadOfGuessingAJoin() throws Exception {
        final ContractCteInvoiceRelationResult result =
                ContractCrossDomainComparator.compareCteInvoice(
                        List.of(row("{\"cte_key\":\"not-a-cte\"}")),
                        "cte_key",
                        List.of(row("{\"cte_key\":\"" + CTE_A + "\"}")),
                        "cte_key",
                        List.of(graphQlFrete("pick-item-1", CTE_A)));

        assertFalse(result.dataExportFreteCtesComplete());
        assertFalse(result.relationConfirmed());
    }

    @Test
    void keepsEveryFinancialFieldAbsentUntilAFormalClassificationExists() {
        final ContractFinancialMatrixEvidence matrix =
                ContractFinancialMatrixEvidence.notObserved();

        assertFalse(matrix.allClassificationsAcceptedAsEquivalent());
        assertTrue(
                matrix.classifications().values().stream()
                        .allMatch(value -> value == ContractFinancialClassification.ABSENT));
    }

    private ContractGraphQlFreteIdentity graphQlFrete(
            final String pickItemId, final String cteKey) {
        return new ContractGraphQlFreteIdentity(
                Optional.of("900001"),
                Optional.of("800001"),
                Optional.of(pickItemId),
                Optional.of(cteKey),
                Optional.of(BigDecimal.ONE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ContractGraphQlColetaIdentity graphQlColeta(final String pickItemId) {
        return new ContractGraphQlColetaIdentity(
                Optional.of("700001"), Optional.of("700001"), List.of(Optional.of(pickItemId)));
    }

    private JsonNode row(final String content) throws Exception {
        return objectMapper.readTree(content);
    }
}
