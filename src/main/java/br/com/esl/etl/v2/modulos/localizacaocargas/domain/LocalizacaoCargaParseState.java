package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

/** Estado do parse conservador de um atributo 8656. */
public enum LocalizacaoCargaParseState {
    NOT_PRESENT,
    EXPLICIT_NULL,
    VALID,
    INVALID
}
