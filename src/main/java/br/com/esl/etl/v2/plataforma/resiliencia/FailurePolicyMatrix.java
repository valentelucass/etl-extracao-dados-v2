package br.com.esl.etl.v2.plataforma.resiliencia;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Objects;
import java.util.Optional;

/** Matriz fail-closed baseada exclusivamente em taxonomia tipada e budgets explícitos. */
public final class FailurePolicyMatrix {

    public FailureDecision decide(final FailureKind kind, final FailurePolicyContext context) {
        Objects.requireNonNull(kind, "A categoria de falha é obrigatória.");
        Objects.requireNonNull(context, "O contexto da política é obrigatório.");
        return switch (kind) {
            case CONFIGURATION, IDENTITY, AUTHORIZATION ->
                    terminal(
                            FailureAction.ABORT,
                            ExecutionState.FAILED,
                            RuntimeExitCategory.CONFIG_AUTH,
                            true);
            case SCHEMA_CONTRACT,
                            CONTRACT_DRIFT,
                            SQL_FAILURE,
                            CRITICAL_DATA_QUALITY,
                            UNCLASSIFIED_HTTP_422,
                            PERMANENT_SOURCE_REJECTION,
                            STEP_TIMEOUT,
                            CYCLE_TIMEOUT,
                            CIRCUIT_OPEN,
                            SOURCE_BUDGET_EXHAUSTED,
                            WORKLOAD_BUDGET_EXHAUSTED ->
                    terminal(
                            FailureAction.ABORT,
                            ExecutionState.FAILED,
                            RuntimeExitCategory.SOURCE_DQ,
                            true);
            case LOCK_UNAVAILABLE ->
                    terminal(
                            FailureAction.ABORT,
                            ExecutionState.FAILED,
                            RuntimeExitCategory.LOCK,
                            true);
            case RATE_LIMIT, REQUEST_TIMEOUT, HTTP_5XX, SOURCE_UNAVAILABLE ->
                    retryOrAbort(context.retryAvailable());
            case WINDOW_TOO_LARGE_HTTP_422 -> repartitionOrAbort(context.repartitionAvailable());
            case REQUIRED_DEPENDENCY_FAILED ->
                    terminal(
                            FailureAction.BLOCK,
                            ExecutionState.BLOCKED,
                            RuntimeExitCategory.SOURCE_DQ,
                            true);
            case OPTIONAL_DEPENDENCY_FAILED, OPTIONAL_SOURCE_UNAVAILABLE ->
                    terminal(
                            FailureAction.DEGRADE,
                            ExecutionState.DEGRADED,
                            RuntimeExitCategory.DEGRADED,
                            false);
            case SOURCE_DISABLED ->
                    terminal(
                            FailureAction.SKIP,
                            ExecutionState.NOT_APPLICABLE,
                            RuntimeExitCategory.SUCCESS,
                            false);
            case NON_CRITICAL_OBSERVABILITY ->
                    transientDecision(
                            FailureAction.CONTINUE_WITH_ALERT,
                            RuntimeExitCategory.DEGRADED,
                            false,
                            false);
            case CANCELLATION ->
                    terminal(
                            FailureAction.ABORT,
                            ExecutionState.CANCELLED,
                            RuntimeExitCategory.CANCELLED,
                            true);
        };
    }

    private FailureDecision retryOrAbort(final boolean retryAvailable) {
        if (retryAvailable) {
            return transientDecision(FailureAction.RETRY, RuntimeExitCategory.SUCCESS, true, false);
        }
        return terminal(
                FailureAction.ABORT, ExecutionState.FAILED, RuntimeExitCategory.SOURCE_DQ, true);
    }

    private FailureDecision repartitionOrAbort(final boolean repartitionAvailable) {
        if (repartitionAvailable) {
            return transientDecision(
                    FailureAction.REPARTITION, RuntimeExitCategory.SUCCESS, true, false);
        }
        return terminal(
                FailureAction.ABORT, ExecutionState.FAILED, RuntimeExitCategory.SOURCE_DQ, true);
    }

    private FailureDecision terminal(
            final FailureAction action,
            final ExecutionState state,
            final RuntimeExitCategory exitCategory,
            final boolean blocksDependents) {
        return new FailureDecision(
                action, Optional.of(state), exitCategory, true, blocksDependents);
    }

    private FailureDecision transientDecision(
            final FailureAction action,
            final RuntimeExitCategory exitCategory,
            final boolean blocksCheckpoint,
            final boolean blocksDependents) {
        return new FailureDecision(
                action, Optional.empty(), exitCategory, blocksCheckpoint, blocksDependents);
    }
}
