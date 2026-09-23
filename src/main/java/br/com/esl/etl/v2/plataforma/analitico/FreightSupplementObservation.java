package br.com.esl.etl.v2.plataforma.analitico;

import br.com.esl.etl.v2.modulos.fretes.domain.FreightAnalyticAttributes;
import java.util.Objects;

/** Explicit binding between one synthetic source snapshot and an already captured freight. */
public record FreightSupplementObservation(
        String sourceKey, int revision, FreightAnalyticAttributes attributes, String evidence) {
    public FreightSupplementObservation {
        Objects.requireNonNull(attributes);
        if (sourceKey == null
                || !sourceKey.matches("INTEGER:[1-9][0-9]{0,18}")
                || revision < 1
                || revision > 100000
                || evidence == null
                || !evidence.matches("synthetic-[A-Za-z0-9-]{1,54}")) {
            throw new IllegalArgumentException("ANA_FREIGHT_SUPPLEMENT_BINDING");
        }
    }
}
