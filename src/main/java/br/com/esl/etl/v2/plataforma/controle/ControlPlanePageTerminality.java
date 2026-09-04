package br.com.esl.etl.v2.plataforma.controle;

/** Evidência terminal tipada; nenhum valor afirma completude do dataset. */
public enum ControlPlanePageTerminality {
    NONE,
    DATA_EXPORT_EMPTY_PAGE,
    GRAPHQL_PAGE_INFO
}
