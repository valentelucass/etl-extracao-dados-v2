package br.com.esl.etl.v2.plataforma.resiliencia;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Objects;
import java.util.Optional;

/** Resultado completo da matriz, inclusive efeitos sobre dependências e checkpoint. */
public record FailureDecision(
        FailureAction action,
        Optional<ExecutionState> terminalState,
        RuntimeExitCategory exitCategory,
        boolean blocksCheckpoint,
        boolean blocksDependents) {

    public FailureDecision {
        action = Objects.requireNonNull(action, "A ação de falha é obrigatória.");
        terminalState = Objects.requireNonNull(terminalState, "O estado terminal é obrigatório.");
        exitCategory = Objects.requireNonNull(exitCategory, "A categoria de saída é obrigatória.");
        terminalState.ifPresent(
                state -> {
                    if (!state.isTerminal()) {
                        throw new IllegalArgumentException(
                                "A decisão de falha só aceita estado terminal.");
                    }
                });
        if (!isCoherent(action, terminalState, exitCategory, blocksCheckpoint, blocksDependents)) {
            throw new IllegalArgumentException("A decisão de falha é incoerente.");
        }
    }

    private static boolean isCoherent(
            final FailureAction action,
            final Optional<ExecutionState> terminalState,
            final RuntimeExitCategory exitCategory,
            final boolean blocksCheckpoint,
            final boolean blocksDependents) {
        return switch (action) {
            case RETRY, REPARTITION ->
                    terminalState.isEmpty()
                            && exitCategory == RuntimeExitCategory.SUCCESS
                            && blocksCheckpoint
                            && !blocksDependents;
            case CONTINUE_WITH_ALERT ->
                    terminalState.isEmpty()
                            && exitCategory == RuntimeExitCategory.DEGRADED
                            && !blocksCheckpoint
                            && !blocksDependents;
            case ABORT ->
                    terminalState
                                    .filter(
                                            state ->
                                                    (state == ExecutionState.CANCELLED
                                                                    && exitCategory
                                                                            == RuntimeExitCategory
                                                                                    .CANCELLED)
                                                            || (state == ExecutionState.FAILED
                                                                    && exitCategory
                                                                            != RuntimeExitCategory
                                                                                    .CANCELLED
                                                                    && exitCategory
                                                                            != RuntimeExitCategory
                                                                                    .SUCCESS
                                                                    && exitCategory
                                                                            != RuntimeExitCategory
                                                                                    .DEGRADED))
                                    .isPresent()
                            && blocksCheckpoint
                            && blocksDependents;
            case BLOCK ->
                    terminalState.filter(state -> state == ExecutionState.BLOCKED).isPresent()
                            && exitCategory == RuntimeExitCategory.SOURCE_DQ
                            && blocksCheckpoint
                            && blocksDependents;
            case DEGRADE ->
                    terminalState.filter(state -> state == ExecutionState.DEGRADED).isPresent()
                            && exitCategory == RuntimeExitCategory.DEGRADED
                            && blocksCheckpoint
                            && !blocksDependents;
            case SKIP ->
                    terminalState
                                    .filter(
                                            state ->
                                                    state == ExecutionState.SKIPPED
                                                            || state
                                                                    == ExecutionState
                                                                            .NOT_APPLICABLE)
                                    .isPresent()
                            && exitCategory == RuntimeExitCategory.SUCCESS
                            && blocksCheckpoint
                            && !blocksDependents;
        };
    }
}
