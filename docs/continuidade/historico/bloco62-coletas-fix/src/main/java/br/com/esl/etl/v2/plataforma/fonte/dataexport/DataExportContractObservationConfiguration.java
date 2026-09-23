package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;

/** Configuração explícita de uma travessia paginável e vinculada ao contrato runtime. */
public record DataExportContractObservationConfiguration(
        DataExportTemplate template,
        DataExportResponseForm expectedResponseForm,
        String approvedKeyPath,
        ContractObservationLimits observationLimits,
        ContractResponsePathBoundary responsePathBoundary,
        ImmutableFingerprint runtimeConfigurationFingerprint) {

    public DataExportContractObservationConfiguration {
        template = Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        expectedResponseForm =
                Objects.requireNonNull(
                        expectedResponseForm, "A forma Data Export esperada é obrigatória.");
        if (!expectedResponseForm.isArray()) {
            throw new IllegalArgumentException(
                    "O gate Data Export exige uma forma paginável com página terminal vazia.");
        }
        approvedKeyPath = ContractResponse.requireKeyPath(approvedKeyPath);
        observationLimits =
                Objects.requireNonNull(
                        observationLimits, "Os limites de observação são obrigatórios.");
        responsePathBoundary =
                Objects.requireNonNull(responsePathBoundary, "A fronteira de paths é obrigatória.");
        if (!responsePathBoundary.runtimeBound()) {
            throw new IllegalArgumentException(
                    "A observação Data Export exige uma fronteira runtime vinculada.");
        }
        runtimeConfigurationFingerprint =
                Objects.requireNonNull(
                        runtimeConfigurationFingerprint,
                        "O fingerprint da configuração runtime é obrigatório.");
    }

    @Override
    public String toString() {
        return "DataExportContractObservationConfiguration[template="
                + template
                + ", expectedResponseForm="
                + expectedResponseForm
                + "]";
    }
}
