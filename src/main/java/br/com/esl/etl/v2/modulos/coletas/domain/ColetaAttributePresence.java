package br.com.esl.etl.v2.modulos.coletas.domain;

/** Presença tri-state de um atributo retornado pelo Data Export 6908. */
public enum ColetaAttributePresence {
    ABSENT,
    NULL,
    VALUE
}
