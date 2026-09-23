package br.com.esl.etl.v2.plataforma.orquestracao;

/** Estratégia explícita de seleção de janela, independente do modo semântico da execução. */
public enum RuntimeWindowStrategy {
    FULL,
    INTERVAL,
    MICROBATCH
}
