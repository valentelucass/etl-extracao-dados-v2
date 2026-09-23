package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.SqlText;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pedido explícito de uma execução: modo e estratégia de janela nunca recebem default implícito.
 */
public record RuntimeExecutionRequest(
        UUID executionId,
        RuntimeWorkloadId workloadId,
        ExecutionMode mode,
        RuntimeWindowStrategy windowStrategy,
        Instant partitionStart,
        Instant partitionEndExclusive,
        String idempotencyKey,
        Optional<UUID> replayOfExecutionId) {

    public RuntimeExecutionRequest(
            final UUID executionId,
            final RuntimeWorkloadId workloadId,
            final ExecutionMode mode,
            final RuntimeWindowStrategy windowStrategy,
            final Instant partitionStart,
            final Instant partitionEndExclusive,
            final String idempotencyKey) {
        this(
                executionId,
                workloadId,
                mode,
                windowStrategy,
                partitionStart,
                partitionEndExclusive,
                idempotencyKey,
                Optional.empty());
    }

    public RuntimeExecutionRequest {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        workloadId = Objects.requireNonNull(workloadId, "O workload é obrigatório.");
        mode = Objects.requireNonNull(mode, "O modo é obrigatório.");
        replayOfExecutionId =
                Objects.requireNonNull(replayOfExecutionId, "A origem de replay é obrigatória.");
        if ((mode == ExecutionMode.REPLAY) != replayOfExecutionId.isPresent()
                || replayOfExecutionId.filter(executionId::equals).isPresent()) {
            throw new IllegalArgumentException("Replay exige outra ocorrência original explícita.");
        }
        windowStrategy =
                Objects.requireNonNull(windowStrategy, "A estratégia de janela é obrigatória.");
        partitionStart =
                Objects.requireNonNull(partitionStart, "O início da partição é obrigatório.");
        partitionEndExclusive =
                Objects.requireNonNull(
                        partitionEndExclusive, "O fim exclusivo da partição é obrigatório.");
        if (!partitionStart.isBefore(partitionEndExclusive)) {
            throw new IllegalArgumentException(
                    "A partição deve usar o intervalo [início, fim exclusivo).");
        }
        if (idempotencyKey == null || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("A chave de idempotência é obrigatória.");
        }
        idempotencyKey = SqlText.trimAsciiSpace(idempotencyKey);
        if (idempotencyKey.isEmpty()) {
            throw new IllegalArgumentException("A chave de idempotência é obrigatória.");
        }
    }
}
