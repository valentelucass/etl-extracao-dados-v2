package br.com.esl.etl.v2.plataforma.observabilidade;

/** Classificação fechada para retry seguro das escritas idempotentes de observabilidade. */
public enum ObservabilityPersistenceFailureKind {
    DETERMINISTIC,
    TRANSIENT,
    UNAVAILABLE,
    CANCELLED
}
