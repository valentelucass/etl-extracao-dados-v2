package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaFreshnessPolicyTest {
    private static final Instant CURRENT = Instant.parse("2036-03-20T12:00:00Z");
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void acceptsOnlyServiceAtOrderingAndTreatsReplayAndConflictDeterministically() {
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.INSERT,
                decide(null, null, CURRENT, HASH_A));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.UPDATE,
                decide(CURRENT, HASH_A, CURRENT.plusSeconds(1), HASH_B));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.STALE_NO_OP,
                decide(CURRENT, HASH_A, CURRENT.minusSeconds(1), HASH_B));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.REPLAY_NO_OP,
                decide(CURRENT, HASH_A, CURRENT, HASH_A));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.EQUAL_FRESHNESS_CONFLICT,
                decide(CURRENT, HASH_A, CURRENT, HASH_B));
    }

    @Test
    void nullFreshnessNeverUsesKeepLastHashPageOrArrivalAsTieBreaker() {
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.MISSING_FRESHNESS_BLOCK,
                decide(null, null, null, HASH_A));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.REPLAY_NO_OP,
                decide(null, HASH_A, null, HASH_A));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.NULL_FRESHNESS_CONFLICT,
                decide(null, HASH_A, null, HASH_B));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.NULL_FRESHNESS_CONFLICT,
                decide(CURRENT, HASH_A, null, HASH_A));
        assertEquals(
                LocalizacaoCargaFreshnessPolicy.PromotionDecision.UPDATE,
                decide(null, HASH_A, CURRENT, HASH_B));
        assertThrows(
                IllegalArgumentException.class,
                () -> decide(CURRENT, HASH_A, CURRENT, "not-a-hash"));
    }

    private static LocalizacaoCargaFreshnessPolicy.PromotionDecision decide(
            final Instant current,
            final String currentHash,
            final Instant candidate,
            final String candidateHash) {
        return LocalizacaoCargaFreshnessPolicy.decide(
                current, currentHash, candidate, candidateHash);
    }
}
