package br.com.esl.etl.v2.plataforma.qualidade;

/** Boundary que só aceita/devolve estruturas escalares; a avaliação de massa permanece no SQL. */
@FunctionalInterface
public interface DataQualityGateway {

    DataQualityRunSummary evaluatePlatformIntegrity(DataQualityEvaluationRequest request);
}
