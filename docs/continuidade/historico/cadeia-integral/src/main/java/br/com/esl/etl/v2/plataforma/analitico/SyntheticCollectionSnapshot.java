package br.com.esl.etl.v2.plataforma.analitico;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernel;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewEvidence;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Closed synthetic key universe; its proof is checked against four real SQL capture receipts. */
public record SyntheticCollectionSnapshot(
        UUID run, LocalDate date, int universeRoots, boolean omitFirst) {
    public SyntheticCollectionSnapshot {
        Objects.requireNonNull(run);
        Objects.requireNonNull(date);
        if (universeRoots < 2 || universeRoots > 4096) {
            throw new IllegalArgumentException("ANA_COLLECTION_SNAPSHOT_BOUND");
        }
    }

    public int expectedRoots() {
        return universeRoots - (omitFirst ? 1 : 0);
    }

    public String fingerprint() {
        return hash(
                ("synthetic-analytic-collection-snapshot-v1|universe="
                                + universeRoots
                                + "|omit="
                                + (omitFirst ? 1 : 0)
                                + "|date="
                                + date)
                        .getBytes(StandardCharsets.UTF_16LE));
    }

    public SweepScope scope() {
        final String policy =
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
        final String scope =
                hash(
                        ("synthetic-analytic-collection-scope-v1|run="
                                        + run
                                        + "|date="
                                        + date
                                        + "|universe="
                                        + universeRoots)
                                .getBytes(StandardCharsets.UTF_8));
        return new SweepScope(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                0,
                0,
                0,
                policy,
                SweepScope.canonicalBindingFingerprint(policy, scope, fingerprint()),
                scope,
                fingerprint());
    }

    /**
     * Call only after SQL verifies the complete universe, contract, pages and immutable receipt.
     */
    public SweepPreviewAssessment assessVerifiedCaptures(
            final List<UUID> captures, final int pages) {
        return new FailClosedSweepPreviewKernel()
                .assess(scope(), verifiedEvidence(captures, pages));
    }

    /** Aggregate evidence for the nominal preview consumer, after the same SQL verification. */
    public SweepPreviewEvidence verifiedEvidence(final List<UUID> captures, final int pages) {
        if (Objects.requireNonNull(captures).size() != 4
                || captures.stream().distinct().count() != 4
                || pages < 2
                || pages > 10000) {
            throw new IllegalArgumentException("ANA_COLLECTION_SNAPSHOT_PROOFS");
        }
        final var actual = List.copyOf(captures);
        final var scope = scope();
        return new SweepPreviewEvidence(
                ExecutionMode.SWEEP,
                SourceCompletenessStatus.PROVEN_COMPLETE,
                SweepPreviewEvidence.EvidenceStatus.PROVEN,
                true,
                true,
                pages,
                pages,
                pages,
                0,
                false,
                0,
                0,
                false,
                true,
                false,
                0,
                0,
                0,
                0,
                proof(scope, actual.get(0), 1),
                proof(scope, actual.get(1), 2),
                proof(scope, actual.get(2), 3),
                proof(scope, actual.get(3), 4));
    }

    private static SweepPreviewEvidence.Proof proof(
            final SweepScope scope, final UUID execution, final int ordinal) {
        return new SweepPreviewEvidence.Proof(
                ordinal,
                hash(("actual-collection-capture|" + execution).getBytes(StandardCharsets.UTF_8)),
                hash(("actual-collection-execution|" + execution).getBytes(StandardCharsets.UTF_8)),
                scope.policyFingerprint(),
                scope.scopeFingerprint(),
                scope.snapshotFingerprint(),
                scope.bindingFingerprint());
    }

    private static String hash(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
