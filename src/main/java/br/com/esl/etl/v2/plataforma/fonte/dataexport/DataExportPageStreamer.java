package br.com.esl.etl.v2.plataforma.fonte.dataexport;

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

/**
 * Percorre páginas serialmente e entrega cada resposta de imediato, sem acumular a extração na
 * memória.
 *
 * <p>Enquanto a paginação do fornecedor não estiver formalmente comprovada, somente uma página
 * vazia encerra a travessia. Uma página curta continua exigindo a próxima chamada ou o limite falha
 * a execução.
 */
public final class DataExportPageStreamer {

    private final DataExportGateway gateway;
    private final DataExportExtractionAudit audit;
    private final Clock clock;

    public DataExportPageStreamer(
            final DataExportGateway gateway,
            final DataExportExtractionAudit audit,
            final Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway Data Export é obrigatório.");
        this.audit = Objects.requireNonNull(audit, "A auditoria de extração é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
    }

    public DataExportExtractionResult stream(
            final ContractExecutionContext executionContext,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final Consumer<DataExportReadPage> pageConsumer) {
        return stream(
                executionContext, initialRequest, limits, CancellationToken.none(), pageConsumer);
    }

    public DataExportExtractionResult stream(
            final ContractExecutionContext executionContext,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellationToken,
            final Consumer<DataExportReadPage> pageConsumer) {
        Objects.requireNonNull(initialRequest, "A requisição Data Export é obrigatória.");
        final UUID executionId =
                Objects.requireNonNull(
                                executionContext,
                                "O contexto de execução Data Export é obrigatório.")
                        .executionId();
        Objects.requireNonNull(limits, "Os limites de extração são obrigatórios.");
        Objects.requireNonNull(cancellationToken, "O token de cancelamento é obrigatório.");
        Objects.requireNonNull(pageConsumer, "O consumidor de páginas é obrigatório.");
        limits.validate(initialRequest);

        final Instant startedAt = clock.instant();
        int pagesFetched = 0;
        long recordsDelivered = 0L;
        int currentPage = initialRequest.page();
        boolean completionAttempted = false;
        final StructuredCorrelationContext.Scope correlationScope =
                StructuredCorrelationContext.openExecution(executionId);
        try {
            audit.executionStarted(
                    new DataExportExtractionAudit.ExecutionStarted(
                            executionId,
                            initialRequest.template(),
                            initialRequest.businessDateWindow(),
                            initialRequest.updatedAtWindow(),
                            startedAt));

            for (int fetched = 0; fetched < limits.maxPages(); fetched++) {
                cancellationToken.throwIfCancellationRequested();
                final DataExportPageRequest request = initialRequest.withPage(currentPage);
                final DataExportPageResponse response = gateway.fetch(request);
                cancellationToken.throwIfCancellationRequested();
                Objects.requireNonNull(response, "O gateway Data Export retornou resposta nula.");
                final int recordCount = response.recordCount();
                final Instant readAt = clock.instant();
                final DataExportReadPage readPage =
                        new DataExportReadPage(executionId, request, response, readAt);

                pagesFetched++;
                final int distinctEntityCount =
                        DataExportPageEntityLimitValidator.countDistinctEntities(request, response);
                audit.pageRead(
                        new DataExportExtractionAudit.PageRead(
                                executionId,
                                currentPage,
                                request.pageSize(),
                                recordCount,
                                distinctEntityCount,
                                readAt));
                validateRecordLimit(recordsDelivered, recordCount, limits.maxRecords());

                if (recordCount == 0) {
                    cancellationToken.throwIfCancellationRequested();
                    final DataExportExtractionResult result =
                            new DataExportExtractionResult(
                                    executionId,
                                    initialRequest.template(),
                                    pagesFetched,
                                    recordsDelivered,
                                    currentPage,
                                    startedAt,
                                    clock.instant());
                    cancellationToken.throwIfCancellationRequested();
                    executionContext.dataExportTraversalCompleted(currentPage);
                    completionAttempted = true;
                    audit.executionCompleted(result);
                    executionContext.dataExportCompletionAuditSucceeded(currentPage);
                    return result;
                }

                cancellationToken.throwIfCancellationRequested();
                pageConsumer.accept(readPage);
                recordsDelivered = Math.addExact(recordsDelivered, recordCount);
                cancellationToken.throwIfCancellationRequested();
                currentPage = nextPage(currentPage);
            }
            throw new IllegalStateException(
                    "A extração Data Export excedeu o limite de "
                            + limits.maxPages()
                            + " páginas sem página terminal vazia.");
        } catch (final RuntimeException exception) {
            executionContext.traversalFailed();
            if (!completionAttempted) {
                notifyFailure(
                        executionId,
                        initialRequest.template(),
                        pagesFetched,
                        recordsDelivered,
                        exception);
            }
            throw exception;
        } finally {
            correlationScope.close();
        }
    }

    private void validateRecordLimit(
            final long recordsDelivered, final int recordCount, final long maxRecords) {
        final long prospectiveTotal;
        try {
            prospectiveTotal = Math.addExact(recordsDelivered, recordCount);
        } catch (final ArithmeticException exception) {
            throw new IllegalStateException(
                    "A contagem de registros da extração excedeu o limite numérico.", exception);
        }
        if (prospectiveTotal > maxRecords) {
            throw new IllegalStateException(
                    "A extração Data Export excederia o limite de "
                            + maxRecords
                            + " registros antes de entregar a página.");
        }
    }

    private int nextPage(final int currentPage) {
        try {
            return Math.incrementExact(currentPage);
        } catch (final ArithmeticException exception) {
            throw new IllegalStateException(
                    "A numeração de páginas Data Export excedeu o limite numérico.", exception);
        }
    }

    private void notifyFailure(
            final UUID executionId,
            final DataExportTemplate template,
            final int pagesFetched,
            final long recordsDelivered,
            final RuntimeException exception) {
        try {
            audit.executionFailed(
                    new DataExportExtractionAudit.ExecutionFailed(
                            executionId,
                            template,
                            pagesFetched,
                            recordsDelivered,
                            clock.instant(),
                            failureCategory(exception)));
        } catch (final RuntimeException auditException) {
            exception.addSuppressed(auditException);
        }
    }

    private static DataExportFailureCategory failureCategory(final RuntimeException exception) {
        if (exception instanceof DataExportUnavailableException) {
            return DataExportFailureCategory.SOURCE_UNAVAILABLE;
        }
        if (exception instanceof DataExportCircuitOpenException) {
            return DataExportFailureCategory.CIRCUIT_OPEN;
        }
        if (exception instanceof DataExportResponseLimitExceededException) {
            return DataExportFailureCategory.RESPONSE_LIMIT_EXCEEDED;
        }
        if (exception instanceof ResilienceBudgetExceededException) {
            return DataExportFailureCategory.BUDGET_EXHAUSTED;
        }
        if (exception instanceof ResilienceTimeoutException) {
            return DataExportFailureCategory.TIMEOUT;
        }
        if (exception instanceof ResilienceCancelledException) {
            return DataExportFailureCategory.CANCELLED;
        }
        if (exception instanceof ContractDriftException) {
            return DataExportFailureCategory.CONTRACT_DRIFT;
        }
        return DataExportFailureCategory.RUNTIME_FAILURE;
    }
}
