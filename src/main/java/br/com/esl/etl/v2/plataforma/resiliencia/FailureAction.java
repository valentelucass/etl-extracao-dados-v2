package br.com.esl.etl.v2.plataforma.resiliencia;

/** Ação operacional canônica tomada pela matriz compartilhada. */
public enum FailureAction {
    ABORT,
    RETRY,
    REPARTITION,
    DEGRADE,
    BLOCK,
    SKIP,
    CONTINUE_WITH_ALERT
}
