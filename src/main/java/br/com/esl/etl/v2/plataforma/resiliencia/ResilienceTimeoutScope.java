package br.com.esl.etl.v2.plataforma.resiliencia;

/** Escopo exato cujo orçamento temporal foi esgotado. */
public enum ResilienceTimeoutScope {
    REQUEST,
    STEP,
    CYCLE
}
