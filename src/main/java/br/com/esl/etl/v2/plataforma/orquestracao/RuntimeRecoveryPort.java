package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** Evidência limitada a resumos; comandos revalidam a ocorrência sob exclusão SQL. */
public interface RuntimeRecoveryPort {
    void seal(
            ControlPlaneStart start,
            ImmutableFingerprint plan,
            ContractPromotionPermit permit,
            DataQualityPolicyReference policy,
            CancellationToken cancellation);

    RuntimeRecoverySnapshot read(RuntimeRecoveryRequest request, CancellationToken cancellation);

    void resume(RuntimeRecoveryRequest request, String revision, CancellationToken cancellation);
}
