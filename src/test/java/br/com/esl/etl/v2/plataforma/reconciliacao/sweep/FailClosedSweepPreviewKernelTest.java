package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.BINDING;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.POLICY;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SCOPE;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SNAPSHOT;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.evidence;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.proofWith;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scope;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scopeWithKind;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validEvidence;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validScope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FailClosedSweepPreviewKernelTest {
    private final FailClosedSweepPreviewKernel kernel = new FailClosedSweepPreviewKernel();

    @Test
    void producesOnlyPreviewEligibleWithoutApplyCapability() {
        final SweepPreviewAssessment result = kernel.assess(validScope(), validEvidence());

        assertTrue(result.previewEligible());
        assertEquals(
                SweepPreviewAssessment.Disposition.PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY,
                result.disposition());
        assertEquals(
                SweepPreviewBlockReason.NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY, result.reason());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blockedCases")
    void appliesClosedFailClosedPrecedence(
            final String ignored,
            final SweepScope policy,
            final SweepPreviewEvidence evidence,
            final SweepPreviewBlockReason expected) {
        final SweepPreviewAssessment result = kernel.assess(policy, evidence);

        assertFalse(result.previewEligible());
        assertEquals(SweepPreviewAssessment.Disposition.BLOCKED, result.disposition());
        assertEquals(expected, result.reason());
    }

    @Test
    void terminalAndHasNextFalseNeverReplaceCompleteness() {
        final SweepPreviewEvidence valid = validEvidence();
        final SweepPreviewEvidence terminalOnly =
                replace(
                        valid,
                        ExecutionMode.SWEEP,
                        SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
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
                        true,
                        true,
                        0,
                        0,
                        0,
                        0,
                        valid.firstTraversalEvidence(),
                        valid.secondTraversalEvidence(),
                        valid.firstAbsenceEvidence(),
                        valid.secondAbsenceEvidence());

        assertEquals(
                SweepPreviewBlockReason.SOURCE_COMPLETENESS_NOT_PROVEN,
                kernel.assess(validScope(), terminalOnly).reason());
    }

    @Test
    void invalidConstructionRejectsNegativeOverflowMalformedAndUngovernedLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        scope(
                                SweepApplicability.ENABLED,
                                true,
                                true,
                                false,
                                SweepScope.MAX_POLICY_LIMIT + 1,
                                0,
                                0,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () -> scope(SweepApplicability.ENABLED, true, true, false, -1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> proofWith(1, '5', '9', POLICY, SCOPE, SNAPSHOT, "bad"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replaceExpectedPages(
                                validEvidence(), SweepPreviewEvidence.MAX_OBSERVATION_COUNT + 1));
        assertEquals(
                SweepPreviewEvidence.MAX_OBSERVATION_COUNT,
                replaceExpectedPages(validEvidence(), SweepPreviewEvidence.MAX_OBSERVATION_COUNT)
                        .expectedPageCount());
        assertEquals(
                SweepScope.MAX_POLICY_LIMIT,
                scope(
                                SweepApplicability.ENABLED,
                                true,
                                true,
                                false,
                                SweepScope.MAX_POLICY_LIMIT,
                                0,
                                0,
                                0)
                        .invalidLimit());
    }

    private static Stream<Arguments> blockedCases() {
        final SweepPreviewEvidence valid = validEvidence();
        return Stream.of(
                blocked(
                        "incremental",
                        validScope(),
                        replaceMode(valid, ExecutionMode.INCREMENTAL),
                        SweepPreviewBlockReason.MODE_NOT_SWEEP),
                blocked(
                        "bootstrap",
                        validScope(),
                        replaceMode(valid, ExecutionMode.BOOTSTRAP),
                        SweepPreviewBlockReason.MODE_NOT_SWEEP),
                blocked(
                        "backfill",
                        validScope(),
                        replaceMode(valid, ExecutionMode.BACKFILL),
                        SweepPreviewBlockReason.MODE_NOT_SWEEP),
                blocked(
                        "replay",
                        validScope(),
                        replaceMode(valid, ExecutionMode.REPLAY),
                        SweepPreviewBlockReason.MODE_NOT_SWEEP),
                blocked(
                        "disabled",
                        replaceApplicability(SweepApplicability.DISABLED),
                        valid,
                        SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED),
                blocked(
                        "blocked",
                        replaceApplicability(SweepApplicability.BLOCKED),
                        valid,
                        SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED),
                blocked(
                        "n/a",
                        replaceApplicability(SweepApplicability.NOT_APPLICABLE),
                        valid,
                        SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED),
                blocked(
                        "history kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.HISTORY,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "one-to-one kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.ONE_TO_ONE_COMPONENT,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "conditional kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.CONDITIONAL_COMPONENT,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "candidate kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.UNRESOLVED_CANDIDATE,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "child kind remains parent-planner blocked",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.CHILD,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "observation channel kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.OBSERVATION_CHANNEL,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "reference kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.REFERENCE,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "source line kind",
                        scopeWithKind(
                                SweepApplicability.ENABLED,
                                SweepScope.ResponsibilityKind.SOURCE_LINE,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0),
                        valid,
                        SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                blocked(
                        "completeness",
                        validScope(),
                        replaceCompleteness(
                                valid, SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF),
                        SweepPreviewBlockReason.SOURCE_COMPLETENESS_NOT_PROVEN),
                blocked(
                        "failed",
                        validScope(),
                        replaceStatus(valid, SweepPreviewEvidence.EvidenceStatus.FAILED),
                        SweepPreviewBlockReason.EVIDENCE_STATUS_NOT_PROVEN),
                blocked(
                        "unverified",
                        validScope(),
                        replaceStatus(valid, SweepPreviewEvidence.EvidenceStatus.UNVERIFIED),
                        SweepPreviewBlockReason.EVIDENCE_STATUS_NOT_PROVEN),
                blocked(
                        "incomplete",
                        validScope(),
                        replaceTraversal(valid, false, true, 2, 2, 2, 0),
                        SweepPreviewBlockReason.TRAVERSAL_INCOMPLETE),
                blocked(
                        "zero pages",
                        validScope(),
                        replaceTraversal(valid, true, true, 0, 0, 0, 0),
                        SweepPreviewBlockReason.TRAVERSAL_INCOMPLETE),
                blocked(
                        "visited mismatch",
                        validScope(),
                        replaceTraversal(valid, true, true, 2, 1, 2, 0),
                        SweepPreviewBlockReason.TRAVERSAL_INCOMPLETE),
                blocked(
                        "missing page",
                        validScope(),
                        replaceTraversal(valid, true, true, 2, 2, 2, 1),
                        SweepPreviewBlockReason.MISSING_PAGE),
                blocked(
                        "cap",
                        validScope(),
                        replaceRuntime(valid, true, 0, 0, false),
                        SweepPreviewBlockReason.CAP_REACHED),
                blocked(
                        "timeout",
                        validScope(),
                        replaceRuntime(valid, false, 1, 0, false),
                        SweepPreviewBlockReason.TIMEOUT_OR_CANCELLATION),
                blocked(
                        "cancel",
                        validScope(),
                        replaceRuntime(valid, false, 0, 1, false),
                        SweepPreviewBlockReason.TIMEOUT_OR_CANCELLATION),
                blocked(
                        "empty",
                        validScope(),
                        replaceRuntime(valid, false, 0, 0, true),
                        SweepPreviewBlockReason.ANOMALOUS_EMPTY_SOURCE),
                blocked(
                        "invalid",
                        validScope(),
                        replaceCounts(valid, 1, 0, 0, 0),
                        SweepPreviewBlockReason.INVALID_LIMIT_EXCEEDED),
                blocked(
                        "quarantine",
                        validScope(),
                        replaceCounts(valid, 0, 1, 0, 0),
                        SweepPreviewBlockReason.QUARANTINE_LIMIT_EXCEEDED),
                blocked(
                        "volume",
                        validScope(),
                        replaceCounts(valid, 0, 0, 1, 0),
                        SweepPreviewBlockReason.VOLUME_LIMIT_EXCEEDED),
                blocked(
                        "history",
                        validScope(),
                        replaceCounts(valid, 0, 0, 0, 1),
                        SweepPreviewBlockReason.HISTORY_LIMIT_EXCEEDED),
                blocked(
                        "hierarchy",
                        scope(SweepApplicability.ENABLED, false, true, false, 0, 0, 0, 0),
                        valid,
                        SweepPreviewBlockReason.HIERARCHY_UNSAFE),
                blocked(
                        "owner",
                        scope(SweepApplicability.ENABLED, true, false, false, 0, 0, 0, 0),
                        valid,
                        SweepPreviewBlockReason.NOMINAL_OWNER_MISSING),
                blocked(
                        "policy",
                        validScope(),
                        replaceProof(
                                valid,
                                0,
                                proofWith(1, '5', '9', "f".repeat(64), SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.EVIDENCE_POLICY_MISMATCH),
                blocked(
                        "scope",
                        validScope(),
                        replaceProof(
                                valid,
                                1,
                                proofWith(2, '6', 'a', POLICY, "f".repeat(64), SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.EVIDENCE_SCOPE_MISMATCH),
                blocked(
                        "snapshot",
                        validScope(),
                        replaceProof(
                                valid,
                                2,
                                proofWith(3, '7', 'b', POLICY, SCOPE, "f".repeat(64), BINDING)),
                        SweepPreviewBlockReason.SNAPSHOT_FINGERPRINT_MISMATCH),
                blocked(
                        "binding",
                        validScope(),
                        replaceProof(
                                valid,
                                3,
                                proofWith(4, '8', 'c', POLICY, SCOPE, SNAPSHOT, "f".repeat(64))),
                        SweepPreviewBlockReason.BINDING_FINGERPRINT_MISMATCH),
                blocked(
                        "same traversal occurrence",
                        validScope(),
                        replaceProof(
                                valid, 1, proofWith(2, '5', 'a', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.TRAVERSAL_EVIDENCE_NOT_INDEPENDENT),
                blocked(
                        "same traversal run",
                        validScope(),
                        replaceProof(
                                valid, 1, proofWith(2, '6', '9', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.TRAVERSAL_EVIDENCE_NOT_INDEPENDENT),
                blocked(
                        "same absence occurrence",
                        validScope(),
                        replaceProof(
                                valid, 3, proofWith(4, '7', 'c', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.ABSENCE_EVIDENCE_NOT_INDEPENDENT),
                blocked(
                        "same absence run",
                        validScope(),
                        replaceProof(
                                valid, 3, proofWith(4, '8', 'b', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.ABSENCE_EVIDENCE_NOT_INDEPENDENT),
                blocked(
                        "cross occurrence",
                        validScope(),
                        replaceProof(
                                valid, 2, proofWith(3, '5', 'b', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.CROSS_EVIDENCE_NOT_INDEPENDENT),
                blocked(
                        "cross run",
                        validScope(),
                        replaceProof(
                                valid, 2, proofWith(3, '7', '9', POLICY, SCOPE, SNAPSHOT, BINDING)),
                        SweepPreviewBlockReason.CROSS_EVIDENCE_NOT_INDEPENDENT));
    }

    private static Arguments blocked(
            final String name,
            final SweepScope policy,
            final SweepPreviewEvidence evidence,
            final SweepPreviewBlockReason reason) {
        return Arguments.of(name, policy, evidence, reason);
    }

    static SweepScope replaceApplicability(final SweepApplicability applicability) {
        return scope(applicability, true, true, false, 0, 0, 0, 0);
    }

    static SweepPreviewEvidence replaceMode(
            final SweepPreviewEvidence base, final ExecutionMode mode) {
        return replace(
                base,
                mode,
                base.completenessStatus(),
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceCompleteness(
            final SweepPreviewEvidence base, final SourceCompletenessStatus status) {
        return replace(
                base,
                base.executionMode(),
                status,
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceStatus(
            final SweepPreviewEvidence base, final SweepPreviewEvidence.EvidenceStatus status) {
        return replace(
                base,
                base.executionMode(),
                base.completenessStatus(),
                status,
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceTraversal(
            final SweepPreviewEvidence base,
            final boolean first,
            final boolean second,
            final long expected,
            final long visitedFirst,
            final long visitedSecond,
            final long missing) {
        return replace(
                base,
                base.executionMode(),
                base.completenessStatus(),
                base.evidenceStatus(),
                first,
                second,
                expected,
                visitedFirst,
                visitedSecond,
                missing,
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceRuntime(
            final SweepPreviewEvidence base,
            final boolean cap,
            final long timeouts,
            final long cancellations,
            final boolean empty) {
        return replace(
                base,
                base.executionMode(),
                base.completenessStatus(),
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                cap,
                timeouts,
                cancellations,
                empty,
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceCounts(
            final SweepPreviewEvidence base,
            final long invalid,
            final long quarantine,
            final long volume,
            final long history) {
        return replace(
                base,
                base.executionMode(),
                base.completenessStatus(),
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                invalid,
                quarantine,
                volume,
                history,
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceProof(
            final SweepPreviewEvidence base,
            final int index,
            final SweepPreviewEvidence.Proof proof) {
        return replace(
                base,
                base.executionMode(),
                base.completenessStatus(),
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                index == 0 ? proof : base.firstTraversalEvidence(),
                index == 1 ? proof : base.secondTraversalEvidence(),
                index == 2 ? proof : base.firstAbsenceEvidence(),
                index == 3 ? proof : base.secondAbsenceEvidence());
    }

    static SweepPreviewEvidence replaceExpectedPages(
            final SweepPreviewEvidence base, final long expected) {
        return replaceTraversal(base, true, true, expected, expected, expected, 0);
    }

    private static SweepPreviewEvidence replace(
            final SweepPreviewEvidence base,
            final ExecutionMode mode,
            final SourceCompletenessStatus completeness,
            final SweepPreviewEvidence.EvidenceStatus status,
            final boolean firstComplete,
            final boolean secondComplete,
            final long expectedPages,
            final long firstVisited,
            final long secondVisited,
            final long missing,
            final boolean cap,
            final long timeouts,
            final long cancellations,
            final boolean empty,
            final boolean terminal,
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
                status,
                firstComplete,
                secondComplete,
                expectedPages,
                firstVisited,
                secondVisited,
                missing,
                cap,
                timeouts,
                cancellations,
                empty,
                terminal,
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
}
