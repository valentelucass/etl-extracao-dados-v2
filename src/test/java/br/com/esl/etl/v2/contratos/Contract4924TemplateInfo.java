package br.com.esl.etl.v2.contratos;

import java.util.List;
import java.util.Objects;

/** Metadados 4924 transitórios e sanitizados; o corpo remoto nunca é retido. */
public record Contract4924TemplateInfo(
        int httpStatus,
        boolean retryAfterObserved,
        boolean jsonContentTypeDeclared,
        List<ContractMetadataEvidence> declaredFields,
        List<ContractMetadataEvidence> declaredFilters) {

    public Contract4924TemplateInfo {
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("Os metadados auxiliares exigem HTTP de sucesso.");
        }
        declaredFields =
                List.copyOf(
                        Objects.requireNonNull(
                                declaredFields, "Os campos 4924 declarados são obrigatórios."));
        declaredFilters =
                List.copyOf(
                        Objects.requireNonNull(
                                declaredFilters, "Os filtros 4924 declarados são obrigatórios."));
    }

    @Override
    public String toString() {
        return "Contract4924TemplateInfo[httpStatus="
                + httpStatus
                + ", retryAfterObserved="
                + retryAfterObserved
                + ", jsonContentTypeDeclared="
                + jsonContentTypeDeclared
                + ", fieldCount="
                + declaredFields.size()
                + ", filterCount="
                + declaredFilters.size()
                + "]";
    }
}
