package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredCorrelationContext;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Cursor streaming serial, limitado a uma página e contadores O(1). */
public final class GraphQlPageStreamer {

    private final GraphQlGateway gateway;
    private final GraphQlExtractionAudit audit;
    private final Clock clock;

    public GraphQlPageStreamer(
            final GraphQlGateway gateway, final GraphQlExtractionAudit audit, final Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway GraphQL é obrigatório.");
        this.audit = Objects.requireNonNull(audit, "A auditoria GraphQL é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
    }

    public GraphQlExtractionResult stream(
            final ContractExecutionContext executionContext,
            final GraphQlPageRequest initialRequest,
            final GraphQlExtractionLimits limits,
            final Consumer<GraphQlPageResponse> pageConsumer) {
        return stream(
                executionContext, initialRequest, limits, CancellationToken.none(), pageConsumer);
    }

    public GraphQlExtractionResult stream(
            final ContractExecutionContext executionContext,
            final GraphQlPageRequest initialRequest,
            final GraphQlExtractionLimits limits,
            final CancellationToken cancellationToken,
            final Consumer<GraphQlPageResponse> pageConsumer) {
        final ContractExecutionContext requiredContext =
                Objects.requireNonNull(
                        executionContext, "O contexto contratual GraphQL é obrigatório.");
        final GraphQlPageRequest requiredRequest =
                Objects.requireNonNull(initialRequest, "A requisição GraphQL é obrigatória.");
        final GraphQlExtractionLimits requiredLimits =
                Objects.requireNonNull(limits, "Os limites GraphQL são obrigatórios.");
        final CancellationToken requiredCancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento GraphQL é obrigatório.");
        final Consumer<GraphQlPageResponse> requiredConsumer =
                Objects.requireNonNull(pageConsumer, "O consumidor GraphQL é obrigatório.");
        requiredLimits.validate(requiredRequest);
        gateway.verifyCancellationToken(requiredCancellation);

        final UUID executionId = requiredContext.executionId();
        final Instant startedAt = clock.instant();
        final GraphQlCursorCycleDetector cycleDetector = new GraphQlCursorCycleDetector();
        GraphQlPageRequest request = requiredRequest;
        int pagesFetched = 0;
        long nodesDelivered = 0L;
        boolean completionAttempted = false;

        final StructuredCorrelationContext.Scope correlationScope =
                StructuredCorrelationContext.openExecution(executionId);
        try {
            audit.executionStarted(
                    new GraphQlExtractionAudit.ExecutionStarted(
                            executionId,
                            requiredRequest.operation(),
                            requiredRequest.pageSize(),
                            requiredLimits.maxPages(),
                            requiredLimits.maxNodes(),
                            startedAt));
            requiredCancellation.throwIfCancellationRequested();
            for (int pageNumber = 1; pageNumber <= requiredLimits.maxPages(); pageNumber++) {
                requiredCancellation.throwIfCancellationRequested();
                final GraphQlPageResponse response =
                        Objects.requireNonNull(
                                gateway.fetch(request),
                                "O gateway GraphQL retornou uma página nula.");
                validatePage(request, response, nodesDelivered, requiredLimits);
                if (!response.validatedFor(requiredContext)) {
                    throw new ContractDriftException(
                            ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
                }
                pagesFetched++;
                final GraphQlCursor nextCursor;
                if (response.hasNextPage()) {
                    nextCursor =
                            response.endCursor()
                                    .orElseThrow(
                                            () ->
                                                    new GraphQlPaginationException(
                                                            GraphQlPaginationException.Reason
                                                                    .MISSING_CURSOR));
                    if (request.after().filter(nextCursor::equals).isPresent()) {
                        throw new GraphQlPaginationException(
                                GraphQlPaginationException.Reason.REPEATED_CURSOR);
                    }
                    cycleDetector.observe(nextCursor);
                    if (pageNumber == requiredLimits.maxPages()) {
                        throw new GraphQlPaginationException(
                                GraphQlPaginationException.Reason.PAGE_LIMIT);
                    }
                } else {
                    nextCursor = null;
                }
                audit.pageRead(
                        new GraphQlExtractionAudit.PageRead(
                                executionId,
                                request.operation(),
                                pageNumber,
                                request.pageSize(),
                                response.nodeCount(),
                                response.distinctRootKeys(),
                                response.responseBytes(),
                                response.hasNextPage(),
                                clock.instant()));
                requiredCancellation.throwIfCancellationRequested();
                requiredConsumer.accept(response);
                nodesDelivered = Math.addExact(nodesDelivered, response.nodeCount());
                requiredCancellation.throwIfCancellationRequested();

                if (!response.hasNextPage()) {
                    final GraphQlExtractionResult result =
                            new GraphQlExtractionResult(
                                    executionId,
                                    request.operation(),
                                    pagesFetched,
                                    nodesDelivered,
                                    startedAt,
                                    clock.instant(),
                                    GraphQlTraversalVerification
                                            .LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED);
                    requiredContext.graphQlTraversalCompleted();
                    completionAttempted = true;
                    audit.executionCompleted(result);
                    requiredContext.graphQlCompletionAuditSucceeded();
                    return result;
                }

                request = request.next(Objects.requireNonNull(nextCursor));
            }
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.PAGE_LIMIT);
        } catch (final RuntimeException exception) {
            requiredContext.traversalFailed();
            if (!completionAttempted) {
                notifyFailure(
                        executionId,
                        requiredRequest.operation(),
                        pagesFetched,
                        nodesDelivered,
                        exception);
            }
            throw exception;
        } finally {
            correlationScope.close();
        }
    }

    private static void validatePage(
            final GraphQlPageRequest request,
            final GraphQlPageResponse response,
            final long nodesDelivered,
            final GraphQlExtractionLimits limits) {
        if (response.nodeCount() == 0) {
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.EMPTY_PAGE);
        }
        if (response.nodeCount() > request.pageSize()
                || response.nodeCount() > request.operation().maximumPageSize()) {
            throw new GraphQlPaginationException(
                    GraphQlPaginationException.Reason.PAGE_SIZE_EXCEEDED);
        }
        final long prospective;
        try {
            prospective = Math.addExact(nodesDelivered, response.nodeCount());
        } catch (final ArithmeticException exception) {
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.NODE_LIMIT);
        }
        if (prospective > limits.maxNodes()) {
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.NODE_LIMIT);
        }
        if (response.hasNextPage() && response.endCursor().isEmpty()) {
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.MISSING_CURSOR);
        }
    }

    private void notifyFailure(
            final UUID executionId,
            final GraphQlReadOperation operation,
            final int pagesFetched,
            final long nodesDelivered,
            final RuntimeException exception) {
        try {
            audit.executionFailed(
                    new GraphQlExtractionAudit.ExecutionFailed(
                            executionId,
                            operation,
                            pagesFetched,
                            nodesDelivered,
                            clock.instant(),
                            failureCategory(exception)));
        } catch (final RuntimeException auditException) {
            exception.addSuppressed(auditException);
        }
    }

    private static GraphQlFailureCategory failureCategory(final RuntimeException exception) {
        if (exception instanceof GraphQlUnavailableException) {
            if (((GraphQlUnavailableException) exception).reason()
                    == GraphQlUnavailableException.Reason.REQUEST_TIMEOUT) {
                return GraphQlFailureCategory.TIMEOUT;
            }
            return GraphQlFailureCategory.SOURCE_UNAVAILABLE;
        }
        if (exception instanceof GraphQlCircuitOpenException) {
            return GraphQlFailureCategory.CIRCUIT_OPEN;
        }
        if (exception instanceof GraphQlResponseLimitExceededException) {
            return GraphQlFailureCategory.RESPONSE_LIMIT_EXCEEDED;
        }
        if (exception instanceof GraphQlRetryAfterLimitExceededException) {
            return GraphQlFailureCategory.SOURCE_RESPONSE_INVALID;
        }
        if (exception instanceof GraphQlResponseException) {
            if (((GraphQlResponseException) exception).reason()
                    == GraphQlResponseException.Reason.INVALID_PAGE_INFO) {
                return GraphQlFailureCategory.PAGINATION_ANOMALY;
            }
            return GraphQlFailureCategory.SOURCE_RESPONSE_INVALID;
        }
        if (exception instanceof ContractDriftException) {
            return GraphQlFailureCategory.CONTRACT_DRIFT;
        }
        if (exception instanceof ResilienceBudgetExceededException) {
            return GraphQlFailureCategory.BUDGET_EXHAUSTED;
        }
        if (exception instanceof ResilienceTimeoutException) {
            return GraphQlFailureCategory.TIMEOUT;
        }
        if (exception instanceof ResilienceCancelledException) {
            return GraphQlFailureCategory.CANCELLED;
        }
        if (exception instanceof GraphQlPaginationException) {
            return GraphQlFailureCategory.PAGINATION_ANOMALY;
        }
        return GraphQlFailureCategory.RUNTIME_FAILURE;
    }
}
