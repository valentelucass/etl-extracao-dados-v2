package br.com.esl.etl.v2.modulos.contasapagar.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;

/** CAP-02/03/05; labels are selected by a sealed reference release in SQL. */
public final class ContaPagarRules {
    private ContaPagarRules() {}

    public static ExpansionFreshness freshness(final ContaPagarObservation row) {
        return ExpansionFreshness.maximum(
                ExpansionFreshness.instant(row.createdAt().value()),
                ExpansionFreshness.civilEnd(row.antIlsAtnTransactionDate().value()),
                ExpansionFreshness.civilEnd(row.antIlsAtnLiquidationDate().value()));
    }

    public static String payment(final ContaPagarObservation row) {
        return Boolean.TRUE.equals(row.paid().value()) ? "PAGO" : "ABERTO";
    }

    public static String typeToken(final String raw) {
        return raw == null
                ? null
                : raw.substring(raw.lastIndexOf("::") + (raw.contains("::") ? 2 : 1)).trim();
    }
}
