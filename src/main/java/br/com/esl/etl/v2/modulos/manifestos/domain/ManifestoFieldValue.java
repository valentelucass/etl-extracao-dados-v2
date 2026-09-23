package br.com.esl.etl.v2.modulos.manifestos.domain;

import java.util.Objects;

/** Valor canônico de uma folha, com presença separada do JSON preservado. */
public record ManifestoFieldValue(
        ManifestoAttributePresence presence, String canonicalJson, String textValue) {

    public ManifestoFieldValue(
            final ManifestoAttributePresence presence, final String canonicalJson) {
        this(presence, canonicalJson, null);
    }

    public ManifestoFieldValue {
        presence = Objects.requireNonNull(presence, "A presença é obrigatória.");
        if ((presence == ManifestoAttributePresence.VALUE) != (canonicalJson != null)) {
            throw new IllegalArgumentException("O JSON só existe quando a folha possui valor.");
        }
        if (canonicalJson != null && canonicalJson.isBlank()) {
            throw new IllegalArgumentException("O JSON canônico não pode ser vazio.");
        }
        if (textValue != null && presence != ManifestoAttributePresence.VALUE) {
            throw new IllegalArgumentException("Texto interpretado exige presença VALUE.");
        }
    }

    public static ManifestoFieldValue absent() {
        return new ManifestoFieldValue(ManifestoAttributePresence.ABSENT, null);
    }

    public static ManifestoFieldValue nullValue() {
        return new ManifestoFieldValue(ManifestoAttributePresence.NULL, null);
    }

    public static ManifestoFieldValue value(final String canonicalJson) {
        return new ManifestoFieldValue(ManifestoAttributePresence.VALUE, canonicalJson);
    }

    /** A borda fornece o texto interpretado e sua representação canônica opaca. */
    public static ManifestoFieldValue text(final String text, final String canonicalJson) {
        return new ManifestoFieldValue(
                ManifestoAttributePresence.VALUE,
                canonicalJson,
                Objects.requireNonNull(text, "O texto interpretado é obrigatório."));
    }

    @Override
    public String toString() {
        return "ManifestoFieldValue[presence=" + presence + ", canonicalJson=<redacted>]";
    }
}
