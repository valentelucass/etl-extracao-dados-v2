package br.com.esl.etl.v2.plataforma.autorizacao;

/** Ações operacionais que obrigatoriamente atravessam a fronteira de autorização. */
public enum RuntimeAction {
    RUN,
    REPLAY,
    SWEEP_PREVIEW,
    SWEEP_APPLY,
    FORCE_RUN,
    STATUS
}
