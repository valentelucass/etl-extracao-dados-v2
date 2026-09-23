package br.com.esl.etl.v2.plataforma.persistencia.staging;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** Protocolo compartilhado pelas duas verticais, com permits e cancelamento explícitos. */
public interface ShadowPromotionGateway {
    void prepareCandidateSet(ContractPromotionPermit permit, CancellationToken cancellation);

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit permit,
            DataQualityPromotionPermit quality,
            CancellationToken cancellation);
}
