package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.util.Objects;

/** Reducer LOC-02 compartilhável: ABSENT preserva e stale jamais aplica. */
public final class LocalizacaoCargaFieldReducer {
    private LocalizacaoCargaFieldReducer() {}

    public static <T> LocalizacaoCargaFieldValue<T> resolve(
            final LocalizacaoCargaFieldValue<T> current,
            final LocalizacaoCargaFieldValue<T> incoming,
            final boolean acceptedNewerObservation) {
        final LocalizacaoCargaFieldValue<T> requiredIncoming =
                Objects.requireNonNull(incoming, "O campo candidato é obrigatório.");
        if (current != null && !current.path().equals(requiredIncoming.path())) {
            throw new IllegalArgumentException("Reducer não aceita paths distintos.");
        }
        if (!acceptedNewerObservation
                || requiredIncoming.presence() == LocalizacaoCargaAttributePresence.ABSENT) {
            return current;
        }
        return requiredIncoming;
    }
}
