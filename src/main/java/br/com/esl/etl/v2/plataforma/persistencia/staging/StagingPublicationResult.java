package br.com.esl.etl.v2.plataforma.persistencia.staging;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resultado agregado do commit positivo de uma execução, sem chaves de origem ou coleções de
 * registros. As categorias de aplicação são mutuamente exclusivas; {@code staleNoopRows} é um
 * subconjunto de {@code noopRows}.
 */
public record StagingPublicationResult(
        UUID executionId,
        long candidateRows,
        long insertedRows,
        long updatedRows,
        long reactivatedRows,
        long noopRows,
        long staleNoopRows,
        Instant reconciledAt,
        Instant publishedAt,
        Optional<Instant> incrementalFrontierBefore,
        Optional<Instant> incrementalFrontierAfter) {

    public StagingPublicationResult {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        reconciledAt =
                Objects.requireNonNull(reconciledAt, "O horário de reconciliação é obrigatório.");
        publishedAt = Objects.requireNonNull(publishedAt, "O horário de publicação é obrigatório.");
        incrementalFrontierBefore =
                Objects.requireNonNull(
                        incrementalFrontierBefore,
                        "A fronteira incremental anterior é obrigatória.");
        incrementalFrontierAfter =
                Objects.requireNonNull(
                        incrementalFrontierAfter,
                        "A fronteira incremental posterior é obrigatória.");

        if (candidateRows < 0
                || insertedRows < 0
                || updatedRows < 0
                || reactivatedRows < 0
                || noopRows < 0
                || staleNoopRows < 0) {
            throw new IllegalArgumentException(
                    "As contagens da publicação não podem ser negativas.");
        }
        if (staleNoopRows > noopRows) {
            throw new IllegalArgumentException(
                    "No-ops obsoletos não podem superar o total de no-ops.");
        }
        try {
            final long changedRows = Math.addExact(insertedRows, updatedRows);
            final long appliedRows = Math.addExact(changedRows, reactivatedRows);
            if (candidateRows != Math.addExact(appliedRows, noopRows)) {
                throw new IllegalArgumentException(
                        "A equação de aplicação do candidate set não fecha.");
            }
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "A equação de aplicação excede o limite suportado.", exception);
        }
        if (publishedAt.isBefore(reconciledAt)) {
            throw new IllegalArgumentException("A publicação não pode anteceder a reconciliação.");
        }
        if (incrementalFrontierBefore.isPresent() != incrementalFrontierAfter.isPresent()) {
            throw new IllegalArgumentException(
                    "As fronteiras incrementais anterior e posterior devem formar um par.");
        }
        if (incrementalFrontierBefore.isPresent()
                && incrementalFrontierAfter
                        .orElseThrow()
                        .isBefore(incrementalFrontierBefore.orElseThrow())) {
            throw new IllegalArgumentException("A fronteira incremental não pode regredir.");
        }
    }
}
