package br.com.esl.etl.v2.plataforma.autorizacao;

/** Papéis internos mínimos; não há papel administrador nem hierarquia implícita. */
public enum RuntimeRole {
    RUNTIME_OBSERVER,
    RUNTIME_EXECUTOR,
    RUNTIME_REPLAY,
    RUNTIME_SWEEP_REVIEW,
    RUNTIME_SWEEP_APPLY,
    RUNTIME_FORCE_RUN
}
