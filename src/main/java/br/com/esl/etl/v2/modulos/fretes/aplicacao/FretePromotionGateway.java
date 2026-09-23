package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Porta de candidate set/promoção base; relação e publicação externa não pertencem à fatia. */
public interface FretePromotionGateway
        extends br.com.esl.etl.v2.plataforma.persistencia.staging.ShadowPromotionGateway {
    void prepareCandidateSet(ContractPromotionPermit contractPermit);

    default void prepareCandidateSet(
            final ContractPromotionPermit contractPermit,
            final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        prepareCandidateSet(contractPermit);
    }

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit contractPermit, DataQualityPromotionPermit dataQualityPermit);

    default StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit contractPermit,
            final DataQualityPromotionPermit dataQualityPermit,
            final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        return applyReconcileAndPublish(contractPermit, dataQualityPermit);
    }
}
