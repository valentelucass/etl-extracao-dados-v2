package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Acumulador thread-safe O(1); summaries relacionais entram uma única vez após o SQL. */
public final class ExecutionMetricsAccumulator {

    private final UUID executionId;
    private long pages;
    private long responseBytes;
    private long physicalRows;
    private long distinctRootKeys;
    private long duplicateRows;
    private long validRows;
    private long quarantinedRootKeys;
    private long unidentifiedQuarantineRows;
    private long quarantinedStageRows;
    private long candidateRows;
    private long insertedRows;
    private long updatedRows;
    private long reactivatedRows;
    private long noopRows;
    private long staleNoopRows;
    private long retryAttempts;
    private long rateLimitResponses;
    private long sourceLagMilliseconds;
    private Optional<Instant> watermark = Optional.empty();
    private boolean stagingSummaryRecorded;
    private boolean applicationSummaryRecorded;
    private boolean progressRecorded;

    public ExecutionMetricsAccumulator(final UUID executionId) {
        this.executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
    }

    public synchronized void recordPage(final long pageResponseBytes, final long pagePhysicalRows) {
        requireNonNegative(pageResponseBytes, pagePhysicalRows);
        final long nextPages = Math.addExact(pages, 1);
        final long nextResponseBytes = Math.addExact(responseBytes, pageResponseBytes);
        final long nextPhysicalRows = Math.addExact(physicalRows, pagePhysicalRows);
        pages = nextPages;
        responseBytes = nextResponseBytes;
        physicalRows = nextPhysicalRows;
    }

    public synchronized void recordStagingSummary(
            final long summaryDistinctRootKeys,
            final long summaryDuplicateRows,
            final long summaryValidRows,
            final long summaryQuarantinedRootKeys,
            final long summaryUnidentifiedQuarantineRows,
            final long summaryQuarantinedStageRows,
            final long summaryCandidateRows) {
        if (stagingSummaryRecorded) {
            throw new IllegalStateException("O resumo de staging já foi registrado.");
        }
        requireNonNegative(
                summaryDistinctRootKeys,
                summaryDuplicateRows,
                summaryValidRows,
                summaryQuarantinedRootKeys,
                summaryUnidentifiedQuarantineRows,
                summaryQuarantinedStageRows,
                summaryCandidateRows);
        distinctRootKeys = summaryDistinctRootKeys;
        duplicateRows = summaryDuplicateRows;
        validRows = summaryValidRows;
        quarantinedRootKeys = summaryQuarantinedRootKeys;
        unidentifiedQuarantineRows = summaryUnidentifiedQuarantineRows;
        quarantinedStageRows = summaryQuarantinedStageRows;
        candidateRows = summaryCandidateRows;
        stagingSummaryRecorded = true;
    }

    public synchronized void recordApplication(
            final long inserted,
            final long updated,
            final long reactivated,
            final long noop,
            final long staleNoop) {
        if (applicationSummaryRecorded) {
            throw new IllegalStateException("O resumo de aplicação já foi registrado.");
        }
        requireNonNegative(inserted, updated, reactivated, noop, staleNoop);
        insertedRows = inserted;
        updatedRows = updated;
        reactivatedRows = reactivated;
        noopRows = noop;
        staleNoopRows = staleNoop;
        applicationSummaryRecorded = true;
    }

    public synchronized void recordProgress(
            final long lagMilliseconds, final Optional<Instant> observedWatermark) {
        if (progressRecorded) {
            throw new IllegalStateException("O resumo de lag/watermark já foi registrado.");
        }
        requireNonNegative(lagMilliseconds);
        sourceLagMilliseconds = lagMilliseconds;
        watermark = Objects.requireNonNull(observedWatermark, "O watermark é obrigatório.");
        progressRecorded = true;
    }

    public synchronized void recordRetry(final boolean rateLimited) {
        final long nextRetryAttempts = Math.addExact(retryAttempts, 1);
        final long nextRateLimitResponses =
                rateLimited ? Math.addExact(rateLimitResponses, 1) : rateLimitResponses;
        retryAttempts = nextRetryAttempts;
        if (rateLimited) {
            rateLimitResponses = nextRateLimitResponses;
        }
    }

    public synchronized ExecutionMetricsSnapshot snapshot(
            final int sequence, final long durationMilliseconds, final Instant capturedAt) {
        if (!stagingSummaryRecorded || !applicationSummaryRecorded || !progressRecorded) {
            throw new IllegalStateException(
                    "A métrica final exige staging, aplicação e progresso.");
        }
        return new ExecutionMetricsSnapshot(
                executionId,
                sequence,
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
                sourceLagMilliseconds,
                watermark,
                capturedAt);
    }

    private static void requireNonNegative(final long... values) {
        for (final long value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("A medida não pode ser negativa.");
            }
        }
    }
}
