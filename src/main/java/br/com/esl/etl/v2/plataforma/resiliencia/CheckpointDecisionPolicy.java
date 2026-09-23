package br.com.esl.etl.v2.plataforma.resiliencia;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Objects;
import java.util.Optional;

/**
 * Checkpoint pertence à entidade publicada; sucesso parcial ou reconciliação ausente falha fechado.
 */
public final class CheckpointDecisionPolicy {

    public boolean canAdvanceEntityCheckpoint(
            final ExecutionState entityState,
            final boolean extractionComplete,
            final boolean reconciliationPassed,
            final Optional<FailureDecision> unresolvedFailure) {
        Objects.requireNonNull(entityState, "O estado da entidade é obrigatório.");
        Objects.requireNonNull(unresolvedFailure, "A falha pendente é obrigatória.");
        return entityState == ExecutionState.PUBLISHED
                && extractionComplete
                && reconciliationPassed
                && unresolvedFailure.map(decision -> !decision.blocksCheckpoint()).orElse(true);
    }

    /**
     * O watermark operacional é exclusivo do modo incremental e exige fronteira publicada contígua.
     * Checkpoints próprios de bootstrap, backfill, replay e sweep não o movimentam.
     */
    public boolean canAdvanceOperationalWatermark(
            final ExecutionMode executionMode,
            final boolean entityCheckpointAdvanced,
            final boolean contiguousPublishedFrontier) {
        Objects.requireNonNull(executionMode, "O modo de execução é obrigatório.");
        return executionMode == ExecutionMode.INCREMENTAL
                && entityCheckpointAdvanced
                && contiguousPublishedFrontier;
    }
}
