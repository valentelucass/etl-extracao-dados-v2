package br.com.esl.etl.v2.plataforma.resiliencia;

/** Orçamento contável esgotado antes de abrir uma nova operação externa. */
public enum ResilienceBudgetScope {
    SOURCE,
    WORKLOAD,
    REPARTITION
}
