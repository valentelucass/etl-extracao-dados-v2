package br.com.esl.etl.v2.plataforma.fonte.graphql;

/** Categorias sanitizadas e de cardinalidade fechada para auditoria. */
public enum GraphQlFailureCategory {
    SOURCE_UNAVAILABLE,
    CIRCUIT_OPEN,
    RESPONSE_LIMIT_EXCEEDED,
    SOURCE_RESPONSE_INVALID,
    CONTRACT_DRIFT,
    BUDGET_EXHAUSTED,
    TIMEOUT,
    CANCELLED,
    PAGINATION_ANOMALY,
    RUNTIME_FAILURE
}
