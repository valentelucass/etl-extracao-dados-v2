package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceCompleteness;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceCounts;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceMode;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceProof;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceRuntime;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceStatus;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceTraversal;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.BINDING;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.POLICY;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SCOPE;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SNAPSHOT;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.proofWith;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scope;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scopeWithKind;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validEvidence;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validScope;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SweepPreviewDeterminismTest {
    private final FailClosedSweepPreviewKernel kernel = new FailClosedSweepPreviewKernel();

    @Test
    void canonicalFingerprintsAndAssessmentAreStableLiterals() {
        assertEquals("71d659f36f038676a71a3a7631bd06b5721dd94540ba785a3eb342e356bdee52", POLICY);
        assertEquals("8c8971a3a6ad8b60818da1d28759164086242590ec6d8c4ccfa098cf146f86b0", BINDING);
        final SweepPreviewAssessment expected = kernel.assess(validScope(), validEvidence());
        for (int repetition = 0; repetition < 1_000; repetition++) {
            assertEquals(expected, kernel.assess(validScope(), validEvidence()));
        }
    }

    @Test
    void everyAdjacentFailurePairFreezesTheEntireReasonPrecedence() {
        final SweepPreviewEvidence valid = validEvidence();
        final List<PrecedenceCase> cases =
                List.of(
                        item(
                                scope(SweepApplicability.DISABLED, true, true, false, 0, 0, 0, 0),
                                replaceMode(valid, ExecutionMode.INCREMENTAL),
                                SweepPreviewBlockReason.MODE_NOT_SWEEP),
                        item(
                                scopeWithKind(
                                        SweepApplicability.DISABLED,
                                        SweepScope.ResponsibilityKind.HISTORY,
                                        true,
                                        true,
                                        false,
                                        0,
                                        0,
                                        0,
                                        0),
                                valid,
                                SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED),
                        item(
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
                                replaceCompleteness(
                                        valid,
                                        SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF),
                                SweepPreviewBlockReason.RESPONSIBILITY_KIND_NOT_ELIGIBLE),
                        item(
                                validScope(),
                                replaceStatus(
                                        replaceCompleteness(
                                                valid,
                                                SourceCompletenessStatus
                                                        .BLOCKED_NO_COMPLETENESS_PROOF),
                                        SweepPreviewEvidence.EvidenceStatus.FAILED),
                                SweepPreviewBlockReason.SOURCE_COMPLETENESS_NOT_PROVEN),
                        item(
                                validScope(),
                                replaceTraversal(
                                        replaceStatus(
                                                valid, SweepPreviewEvidence.EvidenceStatus.FAILED),
                                        false,
                                        true,
                                        2,
                                        2,
                                        2,
                                        0),
                                SweepPreviewBlockReason.EVIDENCE_STATUS_NOT_PROVEN),
                        item(
                                validScope(),
                                replaceTraversal(valid, false, true, 2, 2, 2, 1),
                                SweepPreviewBlockReason.TRAVERSAL_INCOMPLETE),
                        item(
                                validScope(),
                                replaceRuntime(
                                        replaceTraversal(valid, true, true, 2, 2, 2, 1),
                                        true,
                                        0,
                                        0,
                                        false),
                                SweepPreviewBlockReason.MISSING_PAGE),
                        item(
                                validScope(),
                                replaceRuntime(valid, true, 1, 0, false),
                                SweepPreviewBlockReason.CAP_REACHED),
                        item(
                                validScope(),
                                replaceRuntime(valid, false, 1, 0, true),
                                SweepPreviewBlockReason.TIMEOUT_OR_CANCELLATION),
                        item(
                                validScope(),
                                replaceCounts(replaceRuntime(valid, false, 0, 0, true), 1, 0, 0, 0),
                                SweepPreviewBlockReason.ANOMALOUS_EMPTY_SOURCE),
                        item(
                                validScope(),
                                replaceCounts(valid, 1, 1, 0, 0),
                                SweepPreviewBlockReason.INVALID_LIMIT_EXCEEDED),
                        item(
                                validScope(),
                                replaceCounts(valid, 0, 1, 1, 0),
                                SweepPreviewBlockReason.QUARANTINE_LIMIT_EXCEEDED),
                        item(
                                validScope(),
                                replaceCounts(valid, 0, 0, 1, 1),
                                SweepPreviewBlockReason.VOLUME_LIMIT_EXCEEDED),
                        item(
                                scope(SweepApplicability.ENABLED, false, true, false, 0, 0, 0, 0),
                                replaceCounts(valid, 0, 0, 0, 1),
                                SweepPreviewBlockReason.HISTORY_LIMIT_EXCEEDED),
                        item(
                                scope(SweepApplicability.ENABLED, false, false, false, 0, 0, 0, 0),
                                valid,
                                SweepPreviewBlockReason.HIERARCHY_UNSAFE),
                        item(
                                scope(SweepApplicability.ENABLED, true, false, false, 0, 0, 0, 0),
                                replaceProof(
                                        valid,
                                        0,
                                        proofWith(
                                                1,
                                                '5',
                                                '9',
                                                "d".repeat(64),
                                                SCOPE,
                                                SNAPSHOT,
                                                BINDING)),
                                SweepPreviewBlockReason.NOMINAL_OWNER_MISSING),
                        item(
                                validScope(),
                                replaceProof(
                                        valid,
                                        0,
                                        proofWith(
                                                1,
                                                '5',
                                                '9',
                                                "d".repeat(64),
                                                "e".repeat(64),
                                                SNAPSHOT,
                                                BINDING)),
                                SweepPreviewBlockReason.EVIDENCE_POLICY_MISMATCH),
                        item(
                                validScope(),
                                replaceProof(
                                        valid,
                                        0,
                                        proofWith(
                                                1,
                                                '5',
                                                '9',
                                                POLICY,
                                                "d".repeat(64),
                                                "e".repeat(64),
                                                BINDING)),
                                SweepPreviewBlockReason.EVIDENCE_SCOPE_MISMATCH),
                        item(
                                validScope(),
                                replaceProof(
                                        valid,
                                        0,
                                        proofWith(
                                                1,
                                                '5',
                                                '9',
                                                POLICY,
                                                SCOPE,
                                                "d".repeat(64),
                                                "e".repeat(64))),
                                SweepPreviewBlockReason.SNAPSHOT_FINGERPRINT_MISMATCH),
                        item(
                                validScope(),
                                replaceProof(
                                        valid,
                                        1,
                                        proofWith(
                                                2,
                                                '5',
                                                'a',
                                                POLICY,
                                                SCOPE,
                                                SNAPSHOT,
                                                "d".repeat(64))),
                                SweepPreviewBlockReason.BINDING_FINGERPRINT_MISMATCH),
                        item(
                                validScope(),
                                replaceProof(
                                        replaceProof(
                                                valid,
                                                1,
                                                proofWith(
                                                        2, '5', 'a', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)),
                                        3,
                                        proofWith(4, '7', 'c', POLICY, SCOPE, SNAPSHOT, BINDING)),
                                SweepPreviewBlockReason.TRAVERSAL_EVIDENCE_NOT_INDEPENDENT),
                        item(
                                validScope(),
                                replaceProof(
                                        valid,
                                        3,
                                        proofWith(4, '7', '9', POLICY, SCOPE, SNAPSHOT, BINDING)),
                                SweepPreviewBlockReason.ABSENCE_EVIDENCE_NOT_INDEPENDENT));

        assertEquals(SweepPreviewBlockReason.values().length - 2, cases.size());
        for (final PrecedenceCase item : cases) {
            assertEquals(item.expected(), kernel.assess(item.scope(), item.evidence()).reason());
        }
    }

    private static PrecedenceCase item(
            final SweepScope scope,
            final SweepPreviewEvidence evidence,
            final SweepPreviewBlockReason expected) {
        return new PrecedenceCase(scope, evidence, expected);
    }

    private record PrecedenceCase(
            SweepScope scope, SweepPreviewEvidence evidence, SweepPreviewBlockReason expected) {}
}
