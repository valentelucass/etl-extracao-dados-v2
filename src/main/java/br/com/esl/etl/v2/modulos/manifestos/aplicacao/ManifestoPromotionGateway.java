package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Candidate set e aplicação base shadow; o nome legado não concede publicação externa. */
public interface ManifestoPromotionGateway
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
