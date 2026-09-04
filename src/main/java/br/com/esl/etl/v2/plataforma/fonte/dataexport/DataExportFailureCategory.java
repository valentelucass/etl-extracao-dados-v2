package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Categorias fechadas e sanitizadas para a auditoria de falhas da extração. */
public enum DataExportFailureCategory {
    SOURCE_UNAVAILABLE,
    CIRCUIT_OPEN,
    RESPONSE_LIMIT_EXCEEDED,
    BUDGET_EXHAUSTED,
    TIMEOUT,
    CANCELLED,
    CONTRACT_DRIFT,
    RUNTIME_FAILURE
}
