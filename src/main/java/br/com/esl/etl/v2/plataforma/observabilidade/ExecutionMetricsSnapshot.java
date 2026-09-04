package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Snapshot final de dimensões fixas, reconciliadas e sem labels de cardinalidade aberta. */
public record ExecutionMetricsSnapshot(
        UUID executionId,
        int sequence,
        long durationMilliseconds,
        long pages,
        long responseBytes,
        long physicalRows,
        long distinctRootKeys,
        long duplicateRows,
        long validRows,
        long quarantinedRootKeys,
        long unidentifiedQuarantineRows,
        long quarantinedStageRows,
        long candidateRows,
        long insertedRows,
        long updatedRows,
        long reactivatedRows,
        long noopRows,
        long staleNoopRows,
        long retryAttempts,
        long rateLimitResponses,
        long sourceLagMilliseconds,
        Optional<Instant> watermark,
        Instant capturedAt) {

    public ExecutionMetricsSnapshot {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        watermark = Objects.requireNonNull(watermark, "A presença do watermark é obrigatória.");
        capturedAt = ObservabilityFields.sqlServerInstant(capturedAt, "O horário da métrica");
        watermark =
                watermark.map(value -> ObservabilityFields.sqlServerInstant(value, "O watermark"));
        if (sequence < 1 || sequence > 4096) {
            throw new IllegalArgumentException(
                    "A sequência da métrica deve estar entre 1 e 4.096.");
        }
        requireNonNegative(
                durationMilliseconds,
                pages,
                responseBytes,
                physicalRows,
                distinctRootKeys,
                duplicateRows,
                validRows,
                quarantinedRootKeys,
                unidentifiedQuarantineRows,
                quarantinedStageRows,
                candidateRows,
                insertedRows,
                updatedRows,
                reactivatedRows,
                noopRows,
                staleNoopRows,
                retryAttempts,
                rateLimitResponses,
                sourceLagMilliseconds);
        if (physicalRows != exactSum(distinctRootKeys, duplicateRows, unidentifiedQuarantineRows)) {
            throw new IllegalArgumentException("A equação física da métrica não reconcilia.");
        }
        if (distinctRootKeys != exactSum(validRows, quarantinedRootKeys)) {
            throw new IllegalArgumentException("A equação de raízes da métrica não reconcilia.");
        }
        if (quarantinedStageRows < exactSum(quarantinedRootKeys, unidentifiedQuarantineRows)) {
            throw new IllegalArgumentException("A métrica de quarantine está incompleta.");
        }
        if (candidateRows != validRows) {
            throw new IllegalArgumentException(
                    "O candidate set deve corresponder exatamente às raízes válidas.");
        }
        if (candidateRows != exactSum(insertedRows, updatedRows, reactivatedRows, noopRows)) {
            throw new IllegalArgumentException("A equação de aplicação da métrica não reconcilia.");
        }
        if (staleNoopRows > noopRows) {
            throw new IllegalArgumentException("Stale no-op não pode exceder o total de no-op.");
        }
        if (rateLimitResponses > retryAttempts) {
            throw new IllegalArgumentException("A métrica de rate limit excede os retries.");
        }
        if (watermark.isPresent() && watermark.orElseThrow().isAfter(capturedAt)) {
            throw new IllegalArgumentException("O watermark não pode estar no futuro da captura.");
        }
    }

    @Override
    public String toString() {
        return "ExecutionMetricsSnapshot[sequence="
                + sequence
                + ", pages="
                + pages
                + ", physicalRows="
                + physicalRows
                + "]";
    }

    private static void requireNonNegative(final long... values) {
        for (final long value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("Métricas não podem ser negativas.");
            }
        }
    }

    private static long exactSum(final long... values) {
        long sum = 0;
        for (final long value : values) {
            sum = Math.addExact(sum, value);
        }
        return sum;
    }
}
