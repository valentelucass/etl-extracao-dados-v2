package br.com.esl.etl.v2.plataforma.controle;

/** Modo semântico isolado de uma partição; estratégia de janela não faz parte desta enumeração. */
public enum ExecutionMode {
    INCREMENTAL,
    BOOTSTRAP,
    BACKFILL,
    REPLAY,
    SWEEP
}
