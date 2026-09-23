package br.com.esl.etl.v2.modulos.fretes.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Seleção de frescor observado e comparação FRE-02. A promoção SQL também preserva o frescor
 * conhecido na transição terminal com CT-e omitido, conforme FRE-03 (migration V099).
 */
public final class FreteFreshnessPolicy {
    private FreteFreshnessPolicy() {}

    public static Selection select(
            final FreteTemporalValue cteCreatedAt,
            final FreteTemporalValue cteIssuedAt,
            final FreteTemporalValue criadoEm,
            final FreteTemporalValue servicoEm) {
        final Selection first = choose(cteCreatedAt, FreteFreshnessOrigin.CTE_CREATED_AT);
        if (first != null) {
            return first;
        }
        final Selection second = choose(cteIssuedAt, FreteFreshnessOrigin.CTE_ISSUED_AT);
        if (second != null) {
            return second;
        }
        final Selection third = choose(criadoEm, FreteFreshnessOrigin.CRIADO_EM);
        if (third != null) {
            return third;
        }
        final Selection fourth = choose(servicoEm, FreteFreshnessOrigin.SERVICO_EM);
        return fourth == null ? Selection.blocked("MISSING_VALID_FRESHNESS") : fourth;
    }

    public static PromotionDecision decide(
            final Instant currentAt,
            final String currentCanonicalHash,
            final Instant candidateAt,
            final String candidateCanonicalHash) {
        Objects.requireNonNull(candidateAt, "O frescor candidato é obrigatório.");
        final String candidateHash = hash(candidateCanonicalHash);
        if (currentAt == null) {
            return PromotionDecision.INSERT;
        }
        final int comparison = candidateAt.compareTo(currentAt);
        if (comparison < 0) {
            return PromotionDecision.STALE_NO_OP;
        }
        if (comparison > 0) {
            return PromotionDecision.UPDATE;
        }
        return hash(currentCanonicalHash).equals(candidateHash)
                ? PromotionDecision.REPLAY_NO_OP
                : PromotionDecision.EQUAL_FRESHNESS_CONFLICT;
    }

    private static Selection choose(
            final FreteTemporalValue value, final FreteFreshnessOrigin origin) {
        final FreteTemporalValue required =
                Objects.requireNonNull(value, "Toda posição da precedência é obrigatória.");
        if (required.presence() != FreteAttributePresence.VALUE) {
            return null;
        }
        return required.parseState() == FreteTemporalValue.ParseState.VALID
                ? Selection.selected(required.instantUtc(), origin)
                : Selection.blocked("INVALID_" + origin.name());
    }

    private static String hash(final String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("O hash canônico deve ser SHA-256 minúsculo.");
        }
        return value;
    }

    public record Selection(
            Instant freshnessAtUtc, FreteFreshnessOrigin origin, String quarantineReasonCode) {
        public Selection {
            if ((quarantineReasonCode == null) != (freshnessAtUtc != null && origin != null)) {
                throw new IllegalArgumentException("Seleção de frescor incoerente.");
            }
            if (quarantineReasonCode != null
                    && !quarantineReasonCode.matches("[A-Z][A-Z0-9_]{2,127}")) {
                throw new IllegalArgumentException("Motivo de frescor inválido.");
            }
        }

        public static Selection selected(
                final Instant freshnessAtUtc, final FreteFreshnessOrigin origin) {
            return new Selection(
                    Objects.requireNonNull(freshnessAtUtc), Objects.requireNonNull(origin), null);
        }

        public static Selection blocked(final String reason) {
            return new Selection(null, null, reason);
        }

        public boolean selected() {
            return quarantineReasonCode == null;
        }
    }

    public enum PromotionDecision {
        INSERT,
        UPDATE,
        STALE_NO_OP,
        REPLAY_NO_OP,
        EQUAL_FRESHNESS_CONFLICT
    }
}
