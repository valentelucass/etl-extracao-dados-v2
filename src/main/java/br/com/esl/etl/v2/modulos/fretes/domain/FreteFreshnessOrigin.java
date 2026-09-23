package br.com.esl.etl.v2.modulos.fretes.domain;

/** Ordem fechada de frescor FRE-02; updated_at não pertence a este enum. */
public enum FreteFreshnessOrigin {
    CTE_CREATED_AT,
    CTE_ISSUED_AT,
    CRIADO_EM,
    SERVICO_EM
}
