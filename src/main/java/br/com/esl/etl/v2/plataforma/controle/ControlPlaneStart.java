package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pedido idempotente para iniciar uma única ocorrência e adquirir sua lease de partição. {@code
 * startedAt} é a observação do caller validada por skew; o SQL Server persiste seu próprio relógio
 * após adquirir os locks da lease.
 */
public record ControlPlaneStart(
        UUID executionId,
        UUID cycleId,
        ExecutionPartitionKey partition,
        String windowStrategy,
        ImmutableFingerprint contract,
        ImmutableFingerprint configuration,
        String idempotencyKey,
        Optional<UUID> replayOfExecutionId,
        Duration leaseDuration,
        Instant startedAt) {

    public ControlPlaneStart {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        cycleId = Objects.requireNonNull(cycleId, "O ciclo é obrigatório.");
        partition = Objects.requireNonNull(partition, "A partição é obrigatória.");
        windowStrategy = required(windowStrategy, 64, "A estratégia de janela é obrigatória.");
        contract = Objects.requireNonNull(contract, "O fingerprint de contrato é obrigatório.");
        configuration =
                Objects.requireNonNull(
                        configuration, "O fingerprint de configuração é obrigatório.");
        idempotencyKey = required(idempotencyKey, 128, "A chave de idempotência é obrigatória.");
        replayOfExecutionId = replayOfExecutionId == null ? Optional.empty() : replayOfExecutionId;
        leaseDuration = Objects.requireNonNull(leaseDuration, "A duração da lease é obrigatória.");
        startedAt = Objects.requireNonNull(startedAt, "O horário de início é obrigatório.");
        if (leaseDuration.isNegative()
                || leaseDuration.isZero()
                || leaseDuration.compareTo(Duration.ofHours(24)) > 0
                || leaseDuration.toMillis() % 1_000 != 0) {
            throw new IllegalArgumentException(
                    "A duração da lease deve ser maior que zero e no máximo 24 horas.");
        }
        if (partition.mode() == ExecutionMode.REPLAY != replayOfExecutionId.isPresent()) {
            throw new IllegalArgumentException(
                    "Somente uma execução REPLAY pode informar a ocorrência original.");
        }
        if (replayOfExecutionId.filter(executionId::equals).isPresent()) {
            throw new IllegalArgumentException(
                    "Uma execução REPLAY não pode referenciar o próprio execution_id.");
        }
    }

    private static String required(
            final String value, final int maximumLength, final String message) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
