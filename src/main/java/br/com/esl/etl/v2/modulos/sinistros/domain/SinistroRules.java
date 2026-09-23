package br.com.esl.etl.v2.modulos.sinistros.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;

/** SIN-02: treatment first, otherwise opening civil date, without truncating the timestamp. */
public final class SinistroRules {
    private SinistroRules() {}

    public static ExpansionFreshness freshness(final SinistroObservation row) {
        final var treatment = row.icmTttTreatmentAt().value();
        return treatment != null
                ? ExpansionFreshness.instant(treatment)
                : ExpansionFreshness.civilStart(row.openingAtDate().value());
    }
}
