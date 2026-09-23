package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.util.Objects;

/** Envelope unitário com bruto, tipado, parse, path, presença e proveniência. */
public record LocalizacaoCargaFieldValue<T>(
        LocalizacaoCargaAttributePresence presence,
        String path,
        String rawJson,
        LocalizacaoCargaParseState parseState,
        T typedValue,
        String provenance) {

    public LocalizacaoCargaFieldValue {
        presence = Objects.requireNonNull(presence, "A presença é obrigatória.");
        if (path == null || !path.matches("/[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("O path deve ser canônico.");
        }
        parseState = Objects.requireNonNull(parseState, "O parse state é obrigatório.");
        if (provenance == null || !provenance.matches("[A-Z][A-Z0-9_]{2,127}")) {
            throw new IllegalArgumentException("A proveniência deve ser estável.");
        }
        if (presence == LocalizacaoCargaAttributePresence.ABSENT
                && (rawJson != null
                        || typedValue != null
                        || parseState != LocalizacaoCargaParseState.NOT_PRESENT)) {
            throw new IllegalArgumentException("ABSENT não pode carregar valor.");
        }
        if (presence == LocalizacaoCargaAttributePresence.NULL
                && (rawJson != null
                        || typedValue != null
                        || parseState != LocalizacaoCargaParseState.EXPLICIT_NULL)) {
            throw new IllegalArgumentException("NULL deve permanecer explícito.");
        }
        if (presence == LocalizacaoCargaAttributePresence.VALUE
                && (rawJson == null
                        || parseState == LocalizacaoCargaParseState.NOT_PRESENT
                        || parseState == LocalizacaoCargaParseState.EXPLICIT_NULL
                        || (parseState == LocalizacaoCargaParseState.VALID)
                                != (typedValue != null))) {
            throw new IllegalArgumentException("VALUE exige bruto e parse coerentes.");
        }
    }

    @Override
    public String toString() {
        return "LocalizacaoCargaFieldValue[presence="
                + presence
                + ", path="
                + path
                + ", parseState="
                + parseState
                + ", provenance="
                + provenance
                + ", sensitive=<redacted>]";
    }
}
