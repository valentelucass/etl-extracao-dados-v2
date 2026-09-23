package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Política O(1) de uma única responsabilidade; não transporta chaves de negócio. */
public record SweepScope(
        SweepApplicability applicability,
        ResponsibilityKind responsibilityKind,
        boolean hierarchySafe,
        boolean nominalOwnerPresent,
        boolean emptySourceAllowedByPolicy,
        long invalidLimit,
        long quarantineLimit,
        long volumeMismatchLimit,
        long historyGapLimit,
        String policyFingerprint,
        String bindingFingerprint,
        String scopeFingerprint,
        String snapshotFingerprint) {
    public enum ResponsibilityKind {
        ROOT,
        CHILD,
        ONE_TO_ONE_COMPONENT,
        HISTORY,
        CONDITIONAL_COMPONENT,
        OBSERVATION_CHANNEL,
        REFERENCE,
        SOURCE_LINE,
        UNRESOLVED_CANDIDATE
    }

    public static final long MAX_POLICY_LIMIT = 1_000_000;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public SweepScope {
        Objects.requireNonNull(applicability, "A aplicabilidade é obrigatória.");
        Objects.requireNonNull(responsibilityKind, "O tipo da responsabilidade é obrigatório.");
        requirePolicyLimit(invalidLimit);
        requirePolicyLimit(quarantineLimit);
        requirePolicyLimit(volumeMismatchLimit);
        requirePolicyLimit(historyGapLimit);
        policyFingerprint = requireFingerprint(policyFingerprint);
        bindingFingerprint = requireFingerprint(bindingFingerprint);
        scopeFingerprint = requireFingerprint(scopeFingerprint);
        snapshotFingerprint = requireFingerprint(snapshotFingerprint);
        final String canonicalPolicy =
                canonicalPolicyFingerprint(
                        applicability,
                        responsibilityKind,
                        hierarchySafe,
                        nominalOwnerPresent,
                        emptySourceAllowedByPolicy,
                        invalidLimit,
                        quarantineLimit,
                        volumeMismatchLimit,
                        historyGapLimit);
        if (!canonicalPolicy.equals(policyFingerprint)) {
            throw new IllegalArgumentException("Fingerprint de política divergente.");
        }
        final String canonicalBinding =
                canonicalBindingFingerprint(
                        policyFingerprint, scopeFingerprint, snapshotFingerprint);
        if (!canonicalBinding.equals(bindingFingerprint)) {
            throw new IllegalArgumentException("Fingerprint de binding divergente.");
        }
    }

    private static void requirePolicyLimit(final long value) {
        if (value < 0 || value > MAX_POLICY_LIMIT) {
            throw new IllegalArgumentException("Limite de política fora do intervalo governado.");
        }
    }

    private static String requireFingerprint(final String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException("O fingerprint técnico deve ser SHA-256 lowercase.");
        }
        return value;
    }

    public static String canonicalPolicyFingerprint(
            final SweepApplicability applicability,
            final ResponsibilityKind responsibilityKind,
            final boolean hierarchySafe,
            final boolean ownerPresent,
            final boolean emptyAllowed,
            final long invalidLimit,
            final long quarantineLimit,
            final long volumeLimit,
            final long historyLimit) {
        return sha256(
                "V2_013_SWEEP_POLICY_V1|app="
                        + applicability.name()
                        + "|kind="
                        + responsibilityKind.name()
                        + "|hierarchy="
                        + hierarchySafe
                        + "|owner="
                        + ownerPresent
                        + "|empty="
                        + emptyAllowed
                        + "|invalid="
                        + invalidLimit
                        + "|quarantine="
                        + quarantineLimit
                        + "|volume="
                        + volumeLimit
                        + "|history="
                        + historyLimit);
    }

    public static String canonicalBindingFingerprint(
            final String policyFingerprint,
            final String scopeFingerprint,
            final String snapshotFingerprint) {
        return sha256(
                "V2_013_SWEEP_BINDING_V1|policy="
                        + policyFingerprint
                        + "|scope="
                        + scopeFingerprint
                        + "|snapshot="
                        + snapshotFingerprint);
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível.", impossible);
        }
    }

    @Override
    public String toString() {
        return "SweepScope[applicability="
                + applicability
                + ", responsibilityKind="
                + responsibilityKind
                + ", hierarchySafe="
                + hierarchySafe
                + ", nominalOwnerPresent="
                + nominalOwnerPresent
                + ", emptySourceAllowedByPolicy="
                + emptySourceAllowedByPolicy
                + ", limits=<redacted>, fingerprints=<redacted>]";
    }
}
