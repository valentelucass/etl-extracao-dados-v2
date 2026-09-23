package br.com.esl.etl.v2.modulos.coletas.domain;

/** Campo que forneceu o instante de frescor já validado para uma observação de Coleta. */
public enum ColetaFreshnessOrigin {
    STATUS_UPDATED_AT,
    FINISH_DATE,
    SERVICE_DATE,
    REQUEST_DATE,
    UNAVAILABLE
}
