package br.com.esl.etl.v2.modulos.manifestos.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Métrica validada sem escala implícita, arredondamento ou conversão de zero em ausência. */
public record ManifestoMetricValue(ManifestoAttributePresence presence, BigDecimal value) {

    public ManifestoMetricValue {
        presence = Objects.requireNonNull(presence, "A presença da métrica é obrigatória.");
        if ((presence == ManifestoAttributePresence.VALUE) != (value != null)) {
            throw new IllegalArgumentException("A métrica só aceita valor com presença VALUE.");
        }
    }

    public static ManifestoMetricValue absent() {
        return new ManifestoMetricValue(ManifestoAttributePresence.ABSENT, null);
    }

    public static ManifestoMetricValue nullValue() {
        return new ManifestoMetricValue(ManifestoAttributePresence.NULL, null);
    }

    public static ManifestoMetricValue value(final BigDecimal value) {
        return new ManifestoMetricValue(
                ManifestoAttributePresence.VALUE,
                Objects.requireNonNull(value, "O valor da métrica é obrigatório."));
    }

    public String canonicalNumber() {
        if (value == null) {
            return null;
        }
        final BigDecimal normalized =
                value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return normalized.toPlainString();
    }

    @Override
    public String toString() {
        return "ManifestoMetricValue[presence=" + presence + ", value=<redacted>]";
    }
}
