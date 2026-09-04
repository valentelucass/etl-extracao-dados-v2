package br.com.esl.etl.v2.plataforma.qualidade;

/** Classificação fechada para impedir retry cego de rejeições determinísticas. */
public enum DataQualityFailureKind {
    DETERMINISTIC,
    TRANSIENT,
    UNAVAILABLE,
    CANCELLED
}
