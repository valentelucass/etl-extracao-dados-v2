package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.time.Instant;

/** Política única LOC-05 para dedupe e promoção; nenhum metadado técnico desempata. */
public final class LocalizacaoCargaFreshnessPolicy {
    private LocalizacaoCargaFreshnessPolicy() {}

    public static PromotionDecision decide(
            final Instant currentAt,
            final String currentHash,
            final Instant candidateAt,
            final String candidateHash) {
        final String incoming = hash(candidateHash);
        if (currentAt == null && currentHash == null) {
            return candidateAt == null
                    ? PromotionDecision.MISSING_FRESHNESS_BLOCK
                    : PromotionDecision.INSERT;
        }
        final String existing = hash(currentHash);
        if (currentAt == null) {
            if (candidateAt != null) {
                return PromotionDecision.UPDATE;
            }
            return existing.equals(incoming)
                    ? PromotionDecision.REPLAY_NO_OP
                    : PromotionDecision.NULL_FRESHNESS_CONFLICT;
        }
        if (candidateAt == null) {
            return PromotionDecision.NULL_FRESHNESS_CONFLICT;
        }
        final int comparison = candidateAt.compareTo(currentAt);
        if (comparison < 0) {
            return PromotionDecision.STALE_NO_OP;
        }
        if (comparison > 0) {
            return PromotionDecision.UPDATE;
        }
        return existing.equals(incoming)
                ? PromotionDecision.REPLAY_NO_OP
                : PromotionDecision.EQUAL_FRESHNESS_CONFLICT;
    }

    private static String hash(final String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("O hash canônico deve ser SHA-256 minúsculo.");
        }
        return value;
    }

    public enum PromotionDecision {
        INSERT,
        UPDATE,
        STALE_NO_OP,
        REPLAY_NO_OP,
        EQUAL_FRESHNESS_CONFLICT,
        NULL_FRESHNESS_CONFLICT,
        MISSING_FRESHNESS_BLOCK
    }
}
