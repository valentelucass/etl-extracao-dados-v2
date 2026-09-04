package br.com.esl.etl.v2.plataforma.identidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityConflictPolicyTest {

    @Test
    void newReplayReactivationAndOrdinaryObservationPreserveTheRightIdentity() {
        assertAction(IdentityConflictPolicy.Action.REGISTER_NEW_CANONICAL, evidence(0, 0));
        assertAction(IdentityConflictPolicy.Action.KEEP_CANONICAL, evidence(1, 1));
        assertAction(
                IdentityConflictPolicy.Action.NO_OP_REPLAY,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 1, false, false, false, false, false, true));
        assertAction(
                IdentityConflictPolicy.Action.REACTIVATE_EXACT_BINDING,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 1, false, false, false, false, true, false));
    }

    @Test
    void collisionsCardinalityAndScopeAlwaysQuarantineDependentPromotion() {
        assertQuarantine(
                IdentityQuarantineException.Reason.SCOPE_MISMATCH,
                new IdentityConflictPolicy.Evidence(
                        false, 0, 0, false, false, false, false, false, false));
        assertQuarantine(IdentityQuarantineException.Reason.SOURCE_KEY_COLLISION, evidence(2, 0));
        assertQuarantine(
                IdentityQuarantineException.Reason.AMBIGUOUS_BUSINESS_ALIAS, evidence(1, 2));
        assertQuarantine(
                IdentityQuarantineException.Reason.CARDINALITY_DIVERGENCE,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 1, true, false, false, false, false, false));
    }

    @Test
    void rekeyAndAliasChangesRequireEvidenceAndAlwaysPreserveCanonicalId() {
        assertQuarantine(
                IdentityQuarantineException.Reason.UNPROVEN_REKEY,
                new IdentityConflictPolicy.Evidence(
                        true, 0, 1, false, true, false, false, false, false));
        assertQuarantine(
                IdentityQuarantineException.Reason.UNPROVEN_ALIAS_CHANGE,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 0, false, false, true, false, false, false));
        assertAction(
                IdentityConflictPolicy.Action.VERSION_ALIASES_PRESERVE_CANONICAL,
                new IdentityConflictPolicy.Evidence(
                        true, 0, 1, false, true, false, true, false, false));
        assertAction(
                IdentityConflictPolicy.Action.VERSION_ALIASES_PRESERVE_CANONICAL,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 0, false, false, true, true, false, false));
        assertQuarantine(
                IdentityQuarantineException.Reason.UNPROVEN_REKEY,
                new IdentityConflictPolicy.Evidence(
                        true, 0, 1, false, false, false, false, false, false));
        assertAction(
                IdentityConflictPolicy.Action.VERSION_ALIASES_PRESERVE_CANONICAL,
                new IdentityConflictPolicy.Evidence(
                        true, 0, 1, false, false, false, true, false, false));
    }

    @Test
    void repeatedExpandedRootIsAReplayButOneToManyAliasEvidenceIsNotSilentlyDeduped() {
        assertAction(
                IdentityConflictPolicy.Action.NO_OP_REPLAY,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 1, false, false, false, false, false, true));
        assertQuarantine(
                IdentityQuarantineException.Reason.CARDINALITY_DIVERGENCE,
                new IdentityConflictPolicy.Evidence(
                        true, 1, 1, true, false, false, false, false, true));
    }

    @Test
    void invalidCountsAndInconsistentDecisionsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Evidence(
                                true, -1, 0, false, false, false, false, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Evidence(
                                true, 0, 3, false, false, false, false, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Evidence(
                                true, 0, 0, false, false, false, false, true, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Evidence(
                                true, 0, 0, false, false, false, false, false, true));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Decision(
                                IdentityConflictPolicy.Action.QUARANTINE, Optional.empty(), false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityConflictPolicy.Decision(
                                IdentityConflictPolicy.Action.KEEP_CANONICAL,
                                Optional.of(IdentityQuarantineException.Reason.SCOPE_MISMATCH),
                                true));
        assertThrows(NullPointerException.class, () -> IdentityConflictPolicy.evaluate(null));
    }

    private static IdentityConflictPolicy.Evidence evidence(
            final int sourceMatches, final int businessMatches) {
        return new IdentityConflictPolicy.Evidence(
                true, sourceMatches, businessMatches, false, false, false, false, false, false);
    }

    private static void assertAction(
            final IdentityConflictPolicy.Action expected,
            final IdentityConflictPolicy.Evidence evidence) {
        final IdentityConflictPolicy.Decision decision = IdentityConflictPolicy.evaluate(evidence);
        assertEquals(expected, decision.action());
        assertEquals(Optional.empty(), decision.quarantineReason());
        assertFalse(decision.blocksDependentPromotion());
    }

    private static void assertQuarantine(
            final IdentityQuarantineException.Reason expected,
            final IdentityConflictPolicy.Evidence evidence) {
        final IdentityConflictPolicy.Decision decision = IdentityConflictPolicy.evaluate(evidence);
        assertEquals(IdentityConflictPolicy.Action.QUARANTINE, decision.action());
        assertEquals(Optional.of(expected), decision.quarantineReason());
        assertTrue(decision.blocksDependentPromotion());
    }
}
