package br.com.esl.etl.v2.plataforma.contrato;

/** Efeito de dados cuja autorização depende de evidência distinta de terminalidade protocolar. */
public enum SourceDataEffect {
    SHADOW_UPSERT,
    SWEEP_OR_DEACTIVATION,
    CUTOVER
}
