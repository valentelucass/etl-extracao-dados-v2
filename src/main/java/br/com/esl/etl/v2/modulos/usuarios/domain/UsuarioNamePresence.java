package br.com.esl.etl.v2.modulos.usuarios.domain;

/** Presença observada do atributo {@code name}; ausência e nulo não são convertidos entre si. */
public enum UsuarioNamePresence {
    ABSENT,
    NULL,
    VALUE
}
