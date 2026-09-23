package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.util.Objects;

/** Kernel O(1), provider-neutral e estritamente de preview de uma responsabilidade por chamada. */
public final class FailClosedSweepPreviewKernel {
    public SweepPreviewAssessment assess(
            final SweepScope scope, final SweepPreviewEvidence evidence) {
        final SweepScope policy = Objects.requireNonNull(scope, "O escopo é obrigatório.");
        final SweepPreviewEvidence observed =
                Objects.requireNonNull(evidence, "A evidência é obrigatória.");

        if (observed.executionMode() != ExecutionMode.SWEEP) {
            return blocked(SweepPreviewBlockReason.MODE_NOT_SWEEP);
        }
        if (policy.applicability() != SweepApplicability.ENABLED) {
            return blocked(SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED);
        }
        if (policy.responsibilityKind() != SweepScope.ResponsibilityKind.ROOT) {
            return blocked(SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE);
        }
        if (observed.completenessStatus() != SourceCompletenessStatus.PROVEN_COMPLETE) {
            return blocked(SweepPreviewBlockReason.SOURCE_COMPLETENESS_NOT_PROVEN);
        }
        if (observed.evidenceStatus() != SweepPreviewEvidence.EvidenceStatus.PROVEN) {
            return blocked(SweepPreviewBlockReason.EVIDENCE_STATUS_NOT_PROVEN);
        }
        if (observed.expectedPageCount() == 0
                || !observed.firstTraversalComplete()
                || !observed.secondTraversalComplete()
                || observed.firstVisitedPageCount() != observed.expectedPageCount()
                || observed.secondVisitedPageCount() != observed.expectedPageCount()) {
            return blocked(SweepPreviewBlockReason.TRAVERSAL_INCOMPLETE);
        }
        if (observed.missingPageCount() != 0) {
            return blocked(SweepPreviewBlockReason.MISSING_PAGE);
        }
        if (observed.capReached()) {
            return blocked(SweepPreviewBlockReason.CAP_REACHED);
        }
        if (observed.timeoutCount() != 0 || observed.cancellationCount() != 0) {
            return blocked(SweepPreviewBlockReason.TIMEOUT_OR_CANCELLATION);
        }
        if (observed.emptySource() && !policy.emptySourceAllowedByPolicy()) {
            return blocked(SweepPreviewBlockReason.ANOMALOUS_EMPTY_SOURCE);
        }
        if (observed.invalidCount() > policy.invalidLimit()) {
            return blocked(SweepPreviewBlockReason.INVALID_LIMIT_EXCEEDED);
        }
        if (observed.quarantineCount() > policy.quarantineLimit()) {
            return blocked(SweepPreviewBlockReason.QUARANTINE_LIMIT_EXCEEDED);
        }
        if (observed.volumeMismatchCount() > policy.volumeMismatchLimit()) {
            return blocked(SweepPreviewBlockReason.VOLUME_LIMIT_EXCEEDED);
        }
        if (observed.historyGapCount() > policy.historyGapLimit()) {
            return blocked(SweepPreviewBlockReason.HISTORY_LIMIT_EXCEEDED);
        }
        if (!policy.hierarchySafe()) {
            return blocked(SweepPreviewBlockReason.HIERARCHY_UNSAFE);
        }
        if (!policy.nominalOwnerPresent()) {
            return blocked(SweepPreviewBlockReason.NOMINAL_OWNER_MISSING);
        }
        final SweepPreviewBlockReason consistency = consistencyReason(policy, observed);
        if (consistency != null) {
            return blocked(consistency);
        }
        final SweepPreviewBlockReason independence = independenceReason(observed);
        if (independence != null) {
            return blocked(independence);
        }
        return new SweepPreviewAssessment(
                SweepPreviewAssessment.Disposition.PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY,
                SweepPreviewBlockReason.NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY);
    }

    private static SweepPreviewBlockReason consistencyReason(
            final SweepScope policy, final SweepPreviewEvidence observed) {
        final SweepPreviewEvidence.Proof firstTraversal = observed.firstTraversalEvidence();
        final SweepPreviewEvidence.Proof secondTraversal = observed.secondTraversalEvidence();
        final SweepPreviewEvidence.Proof firstAbsence = observed.firstAbsenceEvidence();
        final SweepPreviewEvidence.Proof secondAbsence = observed.secondAbsenceEvidence();
        if (!matchesAll(
                policy.policyFingerprint(),
                firstTraversal.policyFingerprint(),
                secondTraversal.policyFingerprint(),
                firstAbsence.policyFingerprint(),
                secondAbsence.policyFingerprint())) {
            return SweepPreviewBlockReason.EVIDENCE_POLICY_MISMATCH;
        }
        if (!matchesAll(
                policy.scopeFingerprint(),
                firstTraversal.scopeFingerprint(),
                secondTraversal.scopeFingerprint(),
                firstAbsence.scopeFingerprint(),
                secondAbsence.scopeFingerprint())) {
            return SweepPreviewBlockReason.EVIDENCE_SCOPE_MISMATCH;
        }
        if (!matchesAll(
                policy.snapshotFingerprint(),
                firstTraversal.snapshotFingerprint(),
                secondTraversal.snapshotFingerprint(),
                firstAbsence.snapshotFingerprint(),
                secondAbsence.snapshotFingerprint())) {
            return SweepPreviewBlockReason.SNAPSHOT_FINGERPRINT_MISMATCH;
        }
        if (!matchesAll(
                policy.bindingFingerprint(),
                firstTraversal.bindingFingerprint(),
                secondTraversal.bindingFingerprint(),
                firstAbsence.bindingFingerprint(),
                secondAbsence.bindingFingerprint())) {
            return SweepPreviewBlockReason.BINDING_FINGERPRINT_MISMATCH;
        }
        return null;
    }

    private static boolean matchesAll(
            final String expected,
            final String first,
            final String second,
            final String third,
            final String fourth) {
        return expected.equals(first)
                && expected.equals(second)
                && expected.equals(third)
                && expected.equals(fourth);
    }

    private static SweepPreviewBlockReason independenceReason(final SweepPreviewEvidence evidence) {
        final SweepPreviewEvidence.Proof firstTraversal = evidence.firstTraversalEvidence();
        final SweepPreviewEvidence.Proof secondTraversal = evidence.secondTraversalEvidence();
        final SweepPreviewEvidence.Proof firstAbsence = evidence.firstAbsenceEvidence();
        final SweepPreviewEvidence.Proof secondAbsence = evidence.secondAbsenceEvidence();
        if (firstTraversal.occurrenceOrdinal() != 1
                || secondTraversal.occurrenceOrdinal() != 2
                || sameOccurrenceOrRun(firstTraversal, secondTraversal)) {
            return SweepPreviewBlockReason.TRAVERSAL_EVIDENCE_NOT_INDEPENDENT;
        }
        if (firstAbsence.occurrenceOrdinal() != 3
                || secondAbsence.occurrenceOrdinal() != 4
                || sameOccurrenceOrRun(firstAbsence, secondAbsence)) {
            return SweepPreviewBlockReason.ABSENCE_EVIDENCE_NOT_INDEPENDENT;
        }
        if (sameOccurrenceOrRun(firstTraversal, firstAbsence)
                || sameOccurrenceOrRun(firstTraversal, secondAbsence)
                || sameOccurrenceOrRun(secondTraversal, firstAbsence)
                || sameOccurrenceOrRun(secondTraversal, secondAbsence)) {
            return SweepPreviewBlockReason.CROSS_EVIDENCE_NOT_INDEPENDENT;
        }
        return null;
    }

    private static boolean sameOccurrenceOrRun(
            final SweepPreviewEvidence.Proof left, final SweepPreviewEvidence.Proof right) {
        return left.occurrenceFingerprint().equals(right.occurrenceFingerprint())
                || left.runFingerprint().equals(right.runFingerprint())
                || left.occurrenceFingerprint().equals(right.runFingerprint())
                || left.runFingerprint().equals(right.occurrenceFingerprint());
    }

    private static SweepPreviewAssessment blocked(final SweepPreviewBlockReason reason) {
        return new SweepPreviewAssessment(SweepPreviewAssessment.Disposition.BLOCKED, reason);
    }
}
