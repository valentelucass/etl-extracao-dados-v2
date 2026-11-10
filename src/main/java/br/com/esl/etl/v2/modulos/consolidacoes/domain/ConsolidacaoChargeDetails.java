package br.com.esl.etl.v2.modulos.consolidacoes.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dynamic charge names remain source keys; no fixed set of charge columns is inferred. */
public record ConsolidacaoChargeDetails(Map<String, BigDecimal> amounts) {
    public ConsolidacaoChargeDetails {
        Objects.requireNonNull(amounts);
        amounts = Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    @Override
    public String toString() {
        return "ConsolidacaoChargeDetails[redacted]";
    }
}
