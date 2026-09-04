package br.com.esl.etl.v2.plataforma.resiliencia;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Objects;

/** Resumo O(1) que não perde publicações independentes ao agregar falhas do ciclo. */
public record CycleOutcome(
        long publishedEntities,
        long degradedEntities,
        long failedEntities,
        long blockedEntities,
        long cancelledEntities,
        long skippedEntities,
        ExecutionState state,
        RuntimeExitCategory exitCategory) {

    public CycleOutcome {
        if (publishedEntities < 0
                || degradedEntities < 0
                || failedEntities < 0
                || blockedEntities < 0
                || cancelledEntities < 0
                || skippedEntities < 0) {
            throw new IllegalArgumentException("As contagens do ciclo não podem ser negativas.");
        }
        state = Objects.requireNonNull(state, "O estado do ciclo é obrigatório.");
        exitCategory = Objects.requireNonNull(exitCategory, "A categoria de saída é obrigatória.");
        if (!isCoherent(
                publishedEntities,
                degradedEntities,
                failedEntities,
                blockedEntities,
                cancelledEntities,
                skippedEntities,
                state,
                exitCategory)) {
            throw new IllegalArgumentException("O resumo do ciclo é incoerente.");
        }
    }

    private static boolean isCoherent(
            final long published,
            final long degraded,
            final long failed,
            final long blocked,
            final long cancelled,
            final long skipped,
            final ExecutionState state,
            final RuntimeExitCategory exitCategory) {
        return switch (state) {
            case PLANNED ->
                    published == 0
                            && degraded == 0
                            && failed == 0
                            && blocked == 0
                            && cancelled == 0
                            && skipped == 0
                            && exitCategory == RuntimeExitCategory.SUCCESS;
            case PUBLISHED ->
                    published > 0
                            && degraded == 0
                            && failed == 0
                            && blocked == 0
                            && cancelled == 0
                            && exitCategory == RuntimeExitCategory.SUCCESS;
            case DEGRADED ->
                    failed == 0
                            && blocked == 0
                            && cancelled == 0
                            && (degraded > 0 || exitCategory == RuntimeExitCategory.DEGRADED)
                            && exitCategory == RuntimeExitCategory.DEGRADED;
            case FAILED ->
                    (failed > 0 || blocked > 0)
                            && exitCategory != RuntimeExitCategory.SUCCESS
                            && exitCategory != RuntimeExitCategory.DEGRADED
                            && exitCategory != RuntimeExitCategory.CANCELLED;
            case CANCELLED ->
                    cancelled > 0
                            && failed == 0
                            && blocked == 0
                            && exitCategory == RuntimeExitCategory.CANCELLED;
            case SKIPPED ->
                    skipped > 0
                            && published == 0
                            && degraded == 0
                            && failed == 0
                            && blocked == 0
                            && cancelled == 0
                            && exitCategory == RuntimeExitCategory.SUCCESS;
            default -> false;
        };
    }
}
