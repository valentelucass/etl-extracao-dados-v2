package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Auditoria sem query, URI, filtro, cursor, payload ou identificador de negócio. */
public interface GraphQlExtractionAudit {

    void executionStarted(ExecutionStarted event);

    void pageRead(PageRead event);

    void executionCompleted(GraphQlExtractionResult result);

    void executionFailed(ExecutionFailed event);

    static GraphQlExtractionAudit noop() {
        return NoopGraphQlExtractionAudit.INSTANCE;
    }

    record ExecutionStarted(
            UUID executionId,
            GraphQlReadOperation operation,
            int requestedPageSize,
            int maximumPages,
            long maximumNodes,
            Instant at) {
        public ExecutionStarted {
            Objects.requireNonNull(executionId, "A execução é obrigatória.");
            operation = Objects.requireNonNull(operation, "A operação é obrigatória.");
            Objects.requireNonNull(at, "O instante é obrigatório.");
            if (requestedPageSize < 1 || requestedPageSize > operation.maximumPageSize()) {
                throw new IllegalArgumentException("O tamanho de página auditado é inválido.");
            }
            if (maximumPages < 1
                    || maximumPages > GraphQlExtractionLimits.ABSOLUTE_MAXIMUM_PAGES
                    || maximumNodes < 1
                    || maximumNodes > GraphQlExtractionLimits.ABSOLUTE_MAXIMUM_NODES) {
                throw new IllegalArgumentException("Os limites auditados são inválidos.");
            }
        }
    }

    record PageRead(
            UUID executionId,
            GraphQlReadOperation operation,
            int pageNumber,
            int requestedPageSize,
            int nodeCount,
            int distinctRootKeys,
            long responseBytes,
            boolean hasNextPage,
            Instant at) {
        public PageRead {
            Objects.requireNonNull(executionId, "A execução é obrigatória.");
            operation = Objects.requireNonNull(operation, "A operação é obrigatória.");
            Objects.requireNonNull(at, "O instante é obrigatório.");
            if (pageNumber < 1
                    || requestedPageSize < 1
                    || requestedPageSize > operation.maximumPageSize()
                    || nodeCount < 0
                    || distinctRootKeys < 0
                    || distinctRootKeys > nodeCount
                    || responseBytes < 0) {
                throw new IllegalArgumentException("A página auditada é inválida.");
            }
        }
    }

    record ExecutionFailed(
            UUID executionId,
            GraphQlReadOperation operation,
            int pagesFetched,
            long nodesDelivered,
            Instant at,
            GraphQlFailureCategory category) {
        public ExecutionFailed {
            Objects.requireNonNull(executionId, "A execução é obrigatória.");
            operation = Objects.requireNonNull(operation, "A operação é obrigatória.");
            Objects.requireNonNull(at, "O instante é obrigatório.");
            category = Objects.requireNonNull(category, "A categoria é obrigatória.");
            if (pagesFetched < 0 || nodesDelivered < 0) {
                throw new IllegalArgumentException("Os contadores de falha são inválidos.");
            }
        }
    }

    enum NoopGraphQlExtractionAudit implements GraphQlExtractionAudit {
        INSTANCE;

        @Override
        public void executionStarted(final ExecutionStarted event) {
            Objects.requireNonNull(event, "O evento é obrigatório.");
        }

        @Override
        public void pageRead(final PageRead event) {
            Objects.requireNonNull(event, "O evento é obrigatório.");
        }

        @Override
        public void executionCompleted(final GraphQlExtractionResult result) {
            Objects.requireNonNull(result, "O resultado é obrigatório.");
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {
            Objects.requireNonNull(event, "O evento é obrigatório.");
        }
    }
}
