package br.com.esl.etl.v2.contratos;

/** Resultado sanitizado de uma travessia e sua comparação com uma repetição. */
public record ContractPaginationResult(
        int firstRunRecordCount,
        int secondRunRecordCount,
        int firstRunEntityCount,
        int secondRunEntityCount,
        int firstRunPageCount,
        int secondRunPageCount,
        int firstRunNonEmptyPageCount,
        int secondRunNonEmptyPageCount,
        boolean firstRunHasTerminalEmptyPage,
        boolean secondRunHasTerminalEmptyPage,
        boolean firstRunHasNoIntermediateEmptyPage,
        boolean secondRunHasNoIntermediateEmptyPage,
        boolean firstRunPageNumbersConsecutive,
        boolean secondRunPageNumbersConsecutive,
        boolean firstRunEntityIdsVerifiable,
        boolean secondRunEntityIdsVerifiable,
        boolean firstRunEntityCountsWithinPageSize,
        boolean secondRunEntityCountsWithinPageSize,
        boolean firstRunEntityIdsUniqueAcrossPages,
        boolean secondRunEntityIdsUniqueAcrossPages,
        boolean firstRunKeysComplete,
        boolean secondRunKeysComplete,
        boolean firstRunKeysUnique,
        boolean secondRunKeysUnique,
        boolean entityCountsEqual,
        boolean orderedKeysStable,
        boolean keySetsStable) {

    public ContractPaginationResult {
        if (firstRunRecordCount < 0
                || secondRunRecordCount < 0
                || firstRunEntityCount < 0
                || secondRunEntityCount < 0
                || firstRunPageCount < 0
                || secondRunPageCount < 0
                || firstRunNonEmptyPageCount < 0
                || secondRunNonEmptyPageCount < 0) {
            throw new IllegalArgumentException(
                    "As contagens de paginação não podem ser negativas.");
        }
    }

    /**
     * Consistência interna da travessia; não prova cobertura sem ordem total, cursor ou contagem
     * oficial.
     */
    public boolean hasInternallyConsistentTraversal() {
        return firstRunHasTerminalEmptyPage
                && secondRunHasTerminalEmptyPage
                && firstRunHasNoIntermediateEmptyPage
                && secondRunHasNoIntermediateEmptyPage
                && firstRunPageNumbersConsecutive
                && secondRunPageNumbersConsecutive
                && firstRunEntityIdsVerifiable
                && secondRunEntityIdsVerifiable
                && firstRunEntityCountsWithinPageSize
                && secondRunEntityCountsWithinPageSize
                && firstRunEntityIdsUniqueAcrossPages
                && secondRunEntityIdsUniqueAcrossPages
                && firstRunKeysComplete
                && secondRunKeysComplete
                && firstRunKeysUnique
                && secondRunKeysUnique
                && entityCountsEqual
                && orderedKeysStable
                && keySetsStable;
    }

    public boolean hasAtLeastThreeNonEmptyPagesInBothRuns() {
        return firstRunNonEmptyPageCount >= 3 && secondRunNonEmptyPageCount >= 3;
    }
}
