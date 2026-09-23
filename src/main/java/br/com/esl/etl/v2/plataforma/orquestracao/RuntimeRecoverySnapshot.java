package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import java.util.Objects;
import java.util.Optional;

/** Uma única linha, sem chaves ou payloads de negócio. */
public record RuntimeRecoverySnapshot(
        Reason reason,
        Optional<ExecutionState> state,
        boolean leaseValid,
        boolean contractVerified,
        long candidateRows,
        Quality quality,
        String revision,
        Optional<StagingPublicationResult> publication) {
    public RuntimeRecoverySnapshot {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(state);
        Objects.requireNonNull(quality);
        Objects.requireNonNull(revision);
        Objects.requireNonNull(publication);
        if (candidateRows < 0
                || revision.length() > 64
                || (reason == Reason.PUBLISHED) != publication.isPresent()
                || reason == Reason.PUBLISHED && state.orElse(null) != ExecutionState.PUBLISHED
                || reason == Reason.ELIGIBLE
                        && (!leaseValid
                                || !contractVerified
                                || state.isEmpty()
                                || state.orElseThrow().isTerminal())) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_SUMMARY_INVALID");
        }
    }

    public enum Reason {
        NOT_FOUND,
        PUBLISHED,
        ELIGIBLE,
        IN_PROGRESS,
        PARTIAL_EXTRACTION,
        EVIDENCE_MISSING,
        INCONSISTENT,
        LEASE_LOST,
        DQ_FAILED,
        DQ_OBSOLETE,
        TERMINAL,
        UNAVAILABLE,
        CANCELLED,
        DEPENDENCY_NOT_PUBLISHED,
        STATE_CHANGED
    }

    public enum Quality {
        ABSENT,
        PASSED,
        FAILED,
        OBSOLETE
    }

    public static RuntimeRecoverySnapshot refused(final Reason reason) {
        return new RuntimeRecoverySnapshot(
                reason, Optional.empty(), false, false, 0, Quality.ABSENT, "", Optional.empty());
    }

    public RuntimeRecoverySnapshot withReason(final Reason next) {
        return new RuntimeRecoverySnapshot(
                next,
                state,
                leaseValid,
                contractVerified,
                candidateRows,
                quality,
                revision,
                Optional.empty());
    }

    @Override
    public String toString() {
        return "RuntimeRecoverySnapshot[reason=" + reason + ", state=" + state + "]";
    }
}
