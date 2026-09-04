package br.com.esl.etl.v2.plataforma.resiliencia;

/** Motivo sanitizado para recusar nova subdivisão. */
public enum RepartitionRefusalReason {
    CATEGORY_NOT_PROVEN,
    BUDGET_EXHAUSTED,
    WINDOW_NOT_ALIGNED,
    WINDOW_OUT_OF_RANGE,
    MINIMUM_PARTITION_REACHED
}
