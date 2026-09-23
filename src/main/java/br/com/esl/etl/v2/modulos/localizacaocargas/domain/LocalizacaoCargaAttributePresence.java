package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

/** Presença wire explícita; ABSENT e NULL nunca são equivalentes. */
public enum LocalizacaoCargaAttributePresence {
    ABSENT,
    NULL,
    VALUE
}
