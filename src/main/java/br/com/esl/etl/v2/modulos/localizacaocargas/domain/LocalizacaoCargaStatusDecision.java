package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.util.Locale;

/** Catálogo LOC-06 exato; desconhecidos permanecem não terminais. */
public record LocalizacaoCargaStatusDecision(
        String raw, String normalized, boolean terminal, boolean known) {
    public static LocalizacaoCargaStatusDecision fromRaw(final String raw) {
        final String normalized = raw == null ? "sem_status" : raw.trim().toLowerCase(Locale.ROOT);
        final String value = normalized.isBlank() ? "sem_status" : normalized;
        final boolean terminal =
                value.equals("finished")
                        || value.equals("delivered")
                        || value.equals("canceled")
                        || value.equals("cancelled");
        return new LocalizacaoCargaStatusDecision(
                raw, value, terminal, terminal || value.equals("sem_status"));
    }

    @Override
    public String toString() {
        return "LocalizacaoCargaStatusDecision[terminal="
                + terminal
                + ", known="
                + known
                + ", sensitive=<redacted>]";
    }
}
