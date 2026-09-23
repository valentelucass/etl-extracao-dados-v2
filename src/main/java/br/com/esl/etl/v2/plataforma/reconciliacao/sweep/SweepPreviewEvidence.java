package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Evidência agregada, limitada e sem chaves para duas travessias e duas ausências independentes.
 */
public record SweepPreviewEvidence(
        ExecutionMode executionMode,
        SourceCompletenessStatus completenessStatus,
        EvidenceStatus evidenceStatus,
        boolean firstTraversalComplete,
        boolean secondTraversalComplete,
        long expectedPageCount,
        long firstVisitedPageCount,
        long secondVisitedPageCount,
        long missingPageCount,
        boolean capReached,
        long timeoutCount,
        long cancellationCount,
        boolean emptySource,
        boolean localTerminalObserved,
        boolean hasNextFalseObserved,
        long invalidCount,
        long quarantineCount,
        long volumeMismatchCount,
        long historyGapCount,
        Proof firstTraversalEvidence,
        Proof secondTraversalEvidence,
        Proof firstAbsenceEvidence,
        Proof secondAbsenceEvidence) {
    public enum EvidenceStatus {
        PROVEN,
        FAILED,
        UNVERIFIED
    }

    /** Uma ocorrência técnica; todos os campos são bounded e não carregam cursor ou chave. */
    public record Proof(
            long occurrenceOrdinal,
            String occurrenceFingerprint,
            String runFingerprint,
            String policyFingerprint,
            String scopeFingerprint,
            String snapshotFingerprint,
            String bindingFingerprint) {
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public Proof {
            if (occurrenceOrdinal < 1 || occurrenceOrdinal > 4) {
                throw new IllegalArgumentException("Ordinal de ocorrência fora da matriz fechada.");
            }
            occurrenceFingerprint = requireFingerprint(occurrenceFingerprint);
            runFingerprint = requireFingerprint(runFingerprint);
            if (occurrenceFingerprint.equals(runFingerprint)) {
                throw new IllegalArgumentException("Ocorrência e execução não são independentes.");
            }
            policyFingerprint = requireFingerprint(policyFingerprint);
            scopeFingerprint = requireFingerprint(scopeFingerprint);
            snapshotFingerprint = requireFingerprint(snapshotFingerprint);
            bindingFingerprint = requireFingerprint(bindingFingerprint);
        }

        private static String requireFingerprint(final String value) {
            if (value == null || !SHA256.matcher(value).matches()) {
                throw new IllegalArgumentException("Fingerprint técnico inválido.");
            }
            return value;
        }

        @Override
        public String toString() {
            return "Proof[occurrenceOrdinal=" + occurrenceOrdinal + ", fingerprints=<redacted>]";
        }
    }

    public static final long MAX_OBSERVATION_COUNT = 1_000_000;

    public SweepPreviewEvidence {
        Objects.requireNonNull(executionMode, "O modo é obrigatório.");
        Objects.requireNonNull(completenessStatus, "A completude é obrigatória.");
        Objects.requireNonNull(evidenceStatus, "O estado da evidência é obrigatório.");
        requireCount(expectedPageCount);
        requireCount(firstVisitedPageCount);
        requireCount(secondVisitedPageCount);
        requireCount(missingPageCount);
        requireCount(timeoutCount);
        requireCount(cancellationCount);
        requireCount(invalidCount);
        requireCount(quarantineCount);
        requireCount(volumeMismatchCount);
        requireCount(historyGapCount);
        firstTraversalEvidence =
                Objects.requireNonNull(firstTraversalEvidence, "A prova é obrigatória.");
        secondTraversalEvidence =
                Objects.requireNonNull(secondTraversalEvidence, "A prova é obrigatória.");
        firstAbsenceEvidence =
                Objects.requireNonNull(firstAbsenceEvidence, "A prova é obrigatória.");
        secondAbsenceEvidence =
                Objects.requireNonNull(secondAbsenceEvidence, "A prova é obrigatória.");
    }

    private static void requireCount(final long value) {
        if (value < 0 || value > MAX_OBSERVATION_COUNT) {
            throw new IllegalArgumentException("Contagem fora do intervalo governado.");
        }
    }

    @Override
    public String toString() {
        return "SweepPreviewEvidence[executionMode="
                + executionMode
                + ", completenessStatus="
                + completenessStatus
                + ", evidenceStatus="
                + evidenceStatus
                + ", signals=<redacted>, proofs=<redacted>]";
    }
}
