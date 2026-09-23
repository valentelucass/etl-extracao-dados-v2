package br.com.esl.etl.v2.modulos.cotacoes.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Entrada de promoção 6906: candidate set comum e release tarifária explícita. */
public interface CotacaoPromotionGateway {
    void prepareCandidateSet(ContractPromotionPermit contractPermit);

    default void prepareCandidateSet(
            final ContractPromotionPermit contractPermit,
            final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        prepareCandidateSet(contractPermit);
    }

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit contractPermit,
            DataQualityPromotionPermit dataQualityPermit,
            long referenceReleaseId);

    default StagingPublicationResult applyReconcileAndPublish(
            final ContractPromotionPermit contractPermit,
            final DataQualityPromotionPermit dataQualityPermit,
            final long referenceReleaseId,
            final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        return applyReconcileAndPublish(contractPermit, dataQualityPermit, referenceReleaseId);
    }
}
