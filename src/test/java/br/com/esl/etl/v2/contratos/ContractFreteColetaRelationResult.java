package br.com.esl.etl.v2.contratos;

/** Resultado agregado do candidato Data Export Frete → Coleta, sem reter as chaves. */
public record ContractFreteColetaRelationResult(
        int dataExportFreteCount,
        int dataExportColetaCount,
        int candidateRelationCount,
        boolean candidateKeysComplete,
        boolean candidateKeysResolveToColetas,
        boolean graphQlPickItemBaselineComplete,
        boolean graphQlPickItemBaselineResolves,
        boolean cardinalityComparable,
        boolean revenueComparable,
        boolean revenueEqual,
        boolean relationConfirmed) {

    public ContractFreteColetaRelationResult {
        if (dataExportFreteCount < 0 || dataExportColetaCount < 0 || candidateRelationCount < 0) {
            throw new IllegalArgumentException("As contagens de vínculo não podem ser negativas.");
        }
    }

    public static ContractFreteColetaRelationResult notObserved() {
        return new ContractFreteColetaRelationResult(
                0, 0, 0, false, false, false, false, false, false, false, false);
    }
}
