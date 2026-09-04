package br.com.esl.etl.v2.plataforma.identidade;

import java.util.Objects;
import java.util.Optional;

/**
 * Política O(1) sobre contagens saturadas produzidas por preflight set-based no SQL Server.
 *
 * <p>Ela classifica uma observação; não consulta registry e não acumula chaves em memória.
 */
public final class IdentityConflictPolicy {

    private IdentityConflictPolicy() {}

    public static Decision evaluate(final Evidence evidence) {
        final Evidence required = Objects.requireNonNull(evidence, "A evidência é obrigatória.");
        if (!required.namespaceMatches()) {
            return Decision.quarantine(IdentityQuarantineException.Reason.SCOPE_MISMATCH);
        }
        if (required.sourceKeyCanonicalMatches() > 1) {
            return Decision.quarantine(IdentityQuarantineException.Reason.SOURCE_KEY_COLLISION);
        }
        if (required.businessAliasCanonicalMatches() > 1) {
            return Decision.quarantine(IdentityQuarantineException.Reason.AMBIGUOUS_BUSINESS_ALIAS);
        }
        if (required.cardinalityDiverges()) {
            return Decision.quarantine(IdentityQuarantineException.Reason.CARDINALITY_DIVERGENCE);
        }
        if (required.sourceKeyChangedForCanonical() && !required.approvedChangeEvidence()) {
            return Decision.quarantine(IdentityQuarantineException.Reason.UNPROVEN_REKEY);
        }
        if (required.businessAliasChangedForCanonical() && !required.approvedChangeEvidence()) {
            return Decision.quarantine(IdentityQuarantineException.Reason.UNPROVEN_ALIAS_CHANGE);
        }
        if (required.sourceKeyChangedForCanonical()
                || required.businessAliasChangedForCanonical()
                || (required.sourceKeyCanonicalMatches() == 0
                        && required.businessAliasCanonicalMatches() == 1)) {
            if (!required.approvedChangeEvidence()) {
                return Decision.quarantine(IdentityQuarantineException.Reason.UNPROVEN_REKEY);
            }
            return Decision.allow(Action.VERSION_ALIASES_PRESERVE_CANONICAL);
        }
        if (required.sourceKeyCanonicalMatches() == 0) {
            return Decision.allow(Action.REGISTER_NEW_CANONICAL);
        }
        if (required.previouslyInactive()) {
            return Decision.allow(Action.REACTIVATE_EXACT_BINDING);
        }
        if (required.exactReplay()) {
            return Decision.allow(Action.NO_OP_REPLAY);
        }
        return Decision.allow(Action.KEEP_CANONICAL);
    }

    /** Contagens 0, 1 ou 2; o valor 2 representa duas ou mais ocorrências. */
    public record Evidence(
            boolean namespaceMatches,
            int sourceKeyCanonicalMatches,
            int businessAliasCanonicalMatches,
            boolean cardinalityDiverges,
            boolean sourceKeyChangedForCanonical,
            boolean businessAliasChangedForCanonical,
            boolean approvedChangeEvidence,
            boolean previouslyInactive,
            boolean exactReplay) {

        public Evidence {
            saturated(sourceKeyCanonicalMatches);
            saturated(businessAliasCanonicalMatches);
            if ((previouslyInactive || exactReplay) && sourceKeyCanonicalMatches != 1) {
                throw new IllegalArgumentException(
                        "Replay ou reativação exige uma única identidade de origem.");
            }
        }

        private static void saturated(final int value) {
            if (value < 0 || value > 2) {
                throw new IllegalArgumentException("A contagem de identidade deve ser saturada.");
            }
        }
    }

    /** Resultado sanitizado; quarentena sempre bloqueia a promoção dependente. */
    public record Decision(
            Action action,
            Optional<IdentityQuarantineException.Reason> quarantineReason,
            boolean blocksDependentPromotion) {

        public Decision {
            action = Objects.requireNonNull(action, "A ação de identidade é obrigatória.");
            quarantineReason =
                    Objects.requireNonNull(
                            quarantineReason, "O motivo opcional de quarentena é obrigatório.");
            if ((action == Action.QUARANTINE) != quarantineReason.isPresent()
                    || blocksDependentPromotion != quarantineReason.isPresent()) {
                throw new IllegalArgumentException("A decisão de identidade é inconsistente.");
            }
        }

        private static Decision allow(final Action action) {
            return new Decision(action, Optional.empty(), false);
        }

        private static Decision quarantine(final IdentityQuarantineException.Reason reason) {
            return new Decision(Action.QUARANTINE, Optional.of(reason), true);
        }
    }

    public enum Action {
        REGISTER_NEW_CANONICAL,
        KEEP_CANONICAL,
        NO_OP_REPLAY,
        REACTIVATE_EXACT_BINDING,
        VERSION_ALIASES_PRESERVE_CANONICAL,
        QUARANTINE
    }
}
