package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;

/** Configuração vinculada de observação estrutural de uma operação GraphQL. */
public record GraphQlContractObservationConfiguration(
        GraphQlReadOperation operation,
        ContractObservationLimits observationLimits,
        ContractResponsePathBoundary responsePathBoundary,
        ImmutableFingerprint runtimeConfigurationFingerprint) {

    public GraphQlContractObservationConfiguration {
        operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        observationLimits =
                Objects.requireNonNull(
                        observationLimits, "Os limites de observação são obrigatórios.");
        responsePathBoundary =
                Objects.requireNonNull(responsePathBoundary, "A fronteira de paths é obrigatória.");
        if (!responsePathBoundary.runtimeBound()) {
            throw new IllegalArgumentException(
                    "A observação GraphQL exige uma fronteira runtime vinculada.");
        }
        runtimeConfigurationFingerprint =
                Objects.requireNonNull(
                        runtimeConfigurationFingerprint,
                        "O fingerprint runtime GraphQL é obrigatório.");
    }

    @Override
    public String toString() {
        return "GraphQlContractObservationConfiguration[operation=" + operation + "]";
    }
}
