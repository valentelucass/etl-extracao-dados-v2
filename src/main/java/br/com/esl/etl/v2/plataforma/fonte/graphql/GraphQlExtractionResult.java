package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Resumo sanitizado de uma travessia terminal local, sem alegação de completude. */
public record GraphQlExtractionResult(
        UUID executionId,
        GraphQlReadOperation operation,
        int pagesFetched,
        long nodesDelivered,
        Instant startedAt,
        Instant completedAt,
        GraphQlTraversalVerification traversalVerification) {

    public GraphQlExtractionResult {
        Objects.requireNonNull(executionId, "A execução é obrigatória.");
        operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        Objects.requireNonNull(startedAt, "O início é obrigatório.");
        Objects.requireNonNull(completedAt, "O término é obrigatório.");
        traversalVerification =
                Objects.requireNonNull(
                        traversalVerification, "A verificação da travessia é obrigatória.");
        if (pagesFetched < 1 || nodesDelivered < 1 || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("O resumo da travessia GraphQL é inválido.");
        }
    }
}
