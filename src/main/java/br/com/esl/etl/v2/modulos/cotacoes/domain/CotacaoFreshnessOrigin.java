package br.com.esl.etl.v2.modulos.cotacoes.domain;

/** Ordem COT-01, compartilhada pelo dedupe e pela promoção. */
public enum CotacaoFreshnessOrigin {
    NFSE_ISSUED_AT,
    CTE_ISSUED_AT,
    REQUESTED_AT
}
