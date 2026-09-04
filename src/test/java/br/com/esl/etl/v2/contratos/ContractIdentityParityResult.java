package br.com.esl.etl.v2.contratos;

/** Resultado agregado de identidade sem carregar identificadores para o relatório de evidência. */
public record ContractIdentityParityResult(
        int dataExportRecordCount,
        int graphQlRecordCount,
        boolean volumesEqual,
        boolean dataExportNaturalKeysComplete,
        boolean graphQlNaturalKeysComplete,
        boolean dataExportNaturalKeysUnique,
        boolean graphQlNaturalKeysUnique,
        boolean dataExportIdsComplete,
        boolean graphQlSourceIdsComplete,
        boolean dataExportIdsUnique,
        boolean graphQlSourceIdsUnique,
        boolean naturalKeySetsEqual,
        boolean naturalKeyToGraphQlSourceIdOneToOne,
        boolean sourceIdsComparable,
        boolean sourceIdsEqual) {

    public ContractIdentityParityResult {
        if (dataExportRecordCount < 0 || graphQlRecordCount < 0) {
            throw new IllegalArgumentException("Os volumes de paridade não podem ser negativos.");
        }
    }

    public boolean hasNoCriticalDivergence() {
        return volumesEqual
                && dataExportNaturalKeysComplete
                && graphQlNaturalKeysComplete
                && dataExportNaturalKeysUnique
                && graphQlNaturalKeysUnique
                && dataExportIdsComplete
                && graphQlSourceIdsComplete
                && dataExportIdsUnique
                && graphQlSourceIdsUnique
                && naturalKeySetsEqual
                && naturalKeyToGraphQlSourceIdOneToOne
                && sourceIdsComparable
                && sourceIdsEqual;
    }
}
