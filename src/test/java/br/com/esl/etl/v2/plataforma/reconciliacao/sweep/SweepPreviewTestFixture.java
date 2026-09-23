package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;

final class SweepPreviewTestFixture {
    static final String SCOPE = "3".repeat(64);
    static final String SNAPSHOT = "4".repeat(64);
    static final String POLICY =
            SweepScope.canonicalPolicyFingerprint(
                    SweepApplicability.ENABLED,
                    SweepScope.ResponsibilityKind.ROOT,
                    true,
                    true,
                    false,
                    0,
                    0,
                    0,
                    0);
    static final String BINDING = SweepScope.canonicalBindingFingerprint(POLICY, SCOPE, SNAPSHOT);

    private SweepPreviewTestFixture() {}

    static SweepScope validScope() {
        return scope(SweepApplicability.ENABLED, true, true, false, 0, 0, 0, 0);
    }

    static SweepScope scope(
            final SweepApplicability applicability,
            final boolean hierarchySafe,
            final boolean ownerPresent,
            final boolean emptyAllowed,
            final long invalidLimit,
            final long quarantineLimit,
            final long volumeLimit,
            final long historyLimit) {
        return scopeWithKind(
                applicability,
                SweepScope.ResponsibilityKind.ROOT,
                hierarchySafe,
                ownerPresent,
                emptyAllowed,
                invalidLimit,
                quarantineLimit,
                volumeLimit,
                historyLimit);
    }

    static SweepScope scopeWithKind(
            final SweepApplicability applicability,
            final SweepScope.ResponsibilityKind kind,
            final boolean hierarchySafe,
            final boolean ownerPresent,
            final boolean emptyAllowed,
            final long invalidLimit,
            final long quarantineLimit,
            final long volumeLimit,
            final long historyLimit) {
        final String policy =
                SweepScope.canonicalPolicyFingerprint(
                        applicability,
                        kind,
                        hierarchySafe,
                        ownerPresent,
                        emptyAllowed,
                        invalidLimit,
                        quarantineLimit,
                        volumeLimit,
                        historyLimit);
        final String binding = SweepScope.canonicalBindingFingerprint(policy, SCOPE, SNAPSHOT);
        return new SweepScope(
                applicability,
                kind,
                hierarchySafe,
                ownerPresent,
                emptyAllowed,
                invalidLimit,
                quarantineLimit,
                volumeLimit,
                historyLimit,
                policy,
                binding,
                SCOPE,
                SNAPSHOT);
    }

    static SweepScope scopeWithFingerprints(
            final SweepApplicability applicability,
            final boolean hierarchySafe,
            final boolean ownerPresent,
            final boolean emptyAllowed,
            final long invalidLimit,
            final long quarantineLimit,
            final long volumeLimit,
            final long historyLimit,
            final String policy,
            final String binding,
            final String scope,
            final String snapshot) {
        return new SweepScope(
                applicability,
                SweepScope.ResponsibilityKind.ROOT,
                hierarchySafe,
                ownerPresent,
                emptyAllowed,
                invalidLimit,
                quarantineLimit,
                volumeLimit,
                historyLimit,
                policy,
                binding,
                scope,
                snapshot);
    }

    static SweepPreviewEvidence validEvidence() {
        return evidence(
                ExecutionMode.SWEEP,
                SourceCompletenessStatus.PROVEN_COMPLETE,
                SweepPreviewEvidence.EvidenceStatus.PROVEN,
                true,
                true,
                2,
                2,
                2,
                0,
                false,
                0,
                0,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                proof(1, '5', '9'),
                proof(2, '6', 'a'),
                proof(3, '7', 'b'),
                proof(4, '8', 'c'));
    }

    static SweepPreviewEvidence evidence(
            final ExecutionMode mode,
            final SourceCompletenessStatus completeness,
            final SweepPreviewEvidence.EvidenceStatus evidenceStatus,
            final boolean firstComplete,
            final boolean secondComplete,
            final long expectedPages,
            final long firstVisited,
            final long secondVisited,
            final long missingPages,
            final boolean capReached,
            final long timeouts,
            final long cancellations,
            final boolean emptySource,
            final boolean localTerminal,
            final boolean hasNextFalse,
            final long invalid,
            final long quarantine,
            final long volume,
            final long history,
            final SweepPreviewEvidence.Proof firstTraversal,
            final SweepPreviewEvidence.Proof secondTraversal,
            final SweepPreviewEvidence.Proof firstAbsence,
            final SweepPreviewEvidence.Proof secondAbsence) {
        return new SweepPreviewEvidence(
                mode,
                completeness,
                evidenceStatus,
                firstComplete,
                secondComplete,
                expectedPages,
                firstVisited,
                secondVisited,
                missingPages,
                capReached,
                timeouts,
                cancellations,
                emptySource,
                localTerminal,
                hasNextFalse,
                invalid,
                quarantine,
                volume,
                history,
                firstTraversal,
                secondTraversal,
                firstAbsence,
                secondAbsence);
    }

    static SweepPreviewEvidence copy(
            final SweepPreviewEvidence base,
            final ExecutionMode mode,
            final SourceCompletenessStatus completeness,
            final SweepPreviewEvidence.EvidenceStatus evidenceStatus,
            final boolean firstComplete,
            final boolean secondComplete,
            final long expectedPages,
            final long firstVisited,
            final long secondVisited,
            final long missingPages,
            final boolean capReached,
            final long timeouts,
            final long cancellations,
            final boolean emptySource,
            final boolean localTerminal,
            final boolean hasNextFalse,
            final long invalid,
            final long quarantine,
            final long volume,
            final long history,
            final SweepPreviewEvidence.Proof firstTraversal,
            final SweepPreviewEvidence.Proof secondTraversal,
            final SweepPreviewEvidence.Proof firstAbsence,
            final SweepPreviewEvidence.Proof secondAbsence) {
        return evidence(
                mode,
                completeness,
                evidenceStatus,
                firstComplete,
                secondComplete,
                expectedPages,
                firstVisited,
                secondVisited,
                missingPages,
                capReached,
                timeouts,
                cancellations,
                emptySource,
                localTerminal,
                hasNextFalse,
                invalid,
                quarantine,
                volume,
                history,
                firstTraversal,
                secondTraversal,
                firstAbsence,
                secondAbsence);
    }

    static SweepPreviewEvidence.Proof proof(
            final long ordinal, final char occurrence, final char run) {
        return proofWith(ordinal, occurrence, run, POLICY, SCOPE, SNAPSHOT, BINDING);
    }

    static SweepPreviewEvidence.Proof proofWith(
            final long ordinal,
            final char occurrence,
            final char run,
            final String policy,
            final String scope,
            final String snapshot,
            final String binding) {
        return new SweepPreviewEvidence.Proof(
                ordinal,
                String.valueOf(occurrence).repeat(64),
                String.valueOf(run).repeat(64),
                policy,
                scope,
                snapshot,
                binding);
    }
}
