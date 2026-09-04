package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Origem sanitizada da espera calculada para uma nova tentativa. */
enum DataExportRetryDelaySource {
    BACKOFF,
    RETRY_AFTER_DELTA,
    RETRY_AFTER_DATE
}
