package br.com.esl.etl.v2.plataforma.persistencia.staging;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;

/**
 * Porta do kernel: recebe lotes limitados, prepara o candidate set e delega ao SQL Server a
 * aplicação, reconciliação e publicação atômicas. Nenhuma implementação pode decompor o protocolo
 * final em chamadas ou transações independentes.
 */
public interface StagingPromotionKernel {

    void stage(StagingBatch batch);

    void prepareCandidateSet(ContractPromotionPermit contractPermit);

    StagingPublicationResult applyReconcileAndPublish(
            ContractPromotionPermit contractPermit, DataQualityPromotionPermit dataQualityPermit);
}
