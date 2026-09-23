package br.com.esl.etl.v2.modulos.fretes.domain;

/** Presença wire-level; NULL nunca é confundido com ausência. */
public enum FreteAttributePresence {
    ABSENT,
    NULL,
    VALUE
}
