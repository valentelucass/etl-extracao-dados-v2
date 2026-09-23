package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.ShadowPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/**
 * Porta de promoção que impede a vertical de Usuários de chamar o publicador genérico sem a
 * projeção current/history tipada.
 */
public interface UsuarioPromotionGateway extends ShadowPromotionGateway {

    void prepareCandidateSet(ContractPromotionPermit contractPermit);

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit contractPermit, DataQualityPromotionPermit dataQualityPermit);

    @Override
    default void prepareCandidateSet(
            final ContractPromotionPermit permit, final CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        prepareCandidateSet(permit);
    }

    @Override
    default StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit permit,
            final DataQualityPromotionPermit quality,
            final CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        return applyReconcileAndPublish(permit, quality);
    }
}
