package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;

/**
 * Porta de promoção que impede a vertical de Usuários de chamar o publicador genérico sem a
 * projeção current/history tipada.
 */
public interface UsuarioPromotionGateway {

    void prepareCandidateSet(ContractPromotionPermit contractPermit);

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit contractPermit, DataQualityPromotionPermit dataQualityPermit);
}
