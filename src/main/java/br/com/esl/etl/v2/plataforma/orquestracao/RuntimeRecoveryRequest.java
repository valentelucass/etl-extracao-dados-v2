package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import java.util.Objects;

/** Expectativas de configuração, nunca uma autorização de promoção. */
public record RuntimeRecoveryRequest(
        ControlPlaneStart start,
        ImmutableFingerprint plan,
        ContractExecutionBinding contract,
        DataQualityPolicyReference policy) {
    public RuntimeRecoveryRequest {
        Objects.requireNonNull(start);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(contract).verify(start);
        Objects.requireNonNull(policy);
    }

    @Override
    public String toString() {
        return "RuntimeRecoveryRequest[redacted]";
    }
}
