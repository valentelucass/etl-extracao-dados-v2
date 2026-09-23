package br.com.esl.etl.v2.plataforma.analitico;

import java.util.Objects;
import java.util.UUID;

/** Explicit synthetic side binding for an NFS-e series absent from the supplier contract. */
public record AnalyticFiscalAttribute(
        long componentId, UUID sourceExecution, int revision, String nfseSeries) {
    public AnalyticFiscalAttribute {
        Objects.requireNonNull(sourceExecution);
        if (componentId < 1
                || revision < 1
                || revision > 100000
                || nfseSeries != null && (nfseSeries.isBlank() || nfseSeries.length() > 50)) {
            throw new IllegalArgumentException("ANA_FISCAL_ATTRIBUTE_BOUND");
        }
    }
}
