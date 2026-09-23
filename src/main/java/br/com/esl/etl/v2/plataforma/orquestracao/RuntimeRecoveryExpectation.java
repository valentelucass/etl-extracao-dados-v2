package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import java.util.Objects;

public record RuntimeRecoveryExpectation(
        RuntimeWorkloadId workload,
        ContractExecutionBinding contract,
        DataQualityPolicyReference policy) {
    public RuntimeRecoveryExpectation {
        Objects.requireNonNull(workload);
        Objects.requireNonNull(contract);
        Objects.requireNonNull(policy);
    }

    @Override
    public String toString() {
        return "RuntimeRecoveryExpectation[redacted]";
    }
}
