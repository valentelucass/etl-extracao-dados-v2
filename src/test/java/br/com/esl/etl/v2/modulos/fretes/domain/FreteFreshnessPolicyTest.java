package br.com.esl.etl.v2.modulos.fretes.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FreteFreshnessPolicyTest {
    private static final Instant CURRENT = Instant.parse("2036-03-20T12:00:00Z");
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void selectsTheFirstValueAndBlocksTheFirstInvalidValue() {
        final var selected =
                FreteFreshnessPolicy.select(
                        FreteTemporalValue.absent("/cte_created_at"),
                        FreteTemporalValue.explicitNull("/cte_issued_at"),
                        FreteTemporalValue.valid("/criado_em", "\"raw\"", CURRENT),
                        FreteTemporalValue.valid(
                                "/servico_em", "\"raw\"", CURRENT.minusSeconds(1)));
        assertEquals(FreteFreshnessOrigin.CRIADO_EM, selected.origin());

        final var invalid =
                FreteFreshnessPolicy.select(
                        FreteTemporalValue.invalid("/cte_created_at", "\"bad\""),
                        FreteTemporalValue.valid("/cte_issued_at", "\"raw\"", CURRENT),
                        FreteTemporalValue.absent("/criado_em"),
                        FreteTemporalValue.absent("/servico_em"));
        assertEquals("INVALID_CTE_CREATED_AT", invalid.quarantineReasonCode());
    }

    @Test
    void decidesReplayStaleUpdateAndEqualConflictWithoutArrivalTieBreak() {
        assertEquals(
                FreteFreshnessPolicy.PromotionDecision.INSERT,
                FreteFreshnessPolicy.decide(null, null, CURRENT, HASH_A));
        assertEquals(
                FreteFreshnessPolicy.PromotionDecision.STALE_NO_OP,
                FreteFreshnessPolicy.decide(CURRENT, HASH_A, CURRENT.minusSeconds(1), HASH_B));
        assertEquals(
                FreteFreshnessPolicy.PromotionDecision.UPDATE,
                FreteFreshnessPolicy.decide(CURRENT, HASH_A, CURRENT.plusSeconds(1), HASH_B));
        assertEquals(
                FreteFreshnessPolicy.PromotionDecision.REPLAY_NO_OP,
                FreteFreshnessPolicy.decide(CURRENT, HASH_A, CURRENT, HASH_A));
        assertEquals(
                FreteFreshnessPolicy.PromotionDecision.EQUAL_FRESHNESS_CONFLICT,
                FreteFreshnessPolicy.decide(CURRENT, HASH_A, CURRENT, HASH_B));
        assertThrows(
                IllegalArgumentException.class,
                () -> FreteFreshnessPolicy.decide(CURRENT, "arrival", CURRENT, HASH_A));
    }
}
