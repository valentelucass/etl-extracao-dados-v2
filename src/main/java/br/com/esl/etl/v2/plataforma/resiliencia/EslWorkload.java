package br.com.esl.etl.v2.plataforma.resiliencia;

/** Verticais ESL conhecidas; o conjunto fechado mantém o registro de budgets limitado. */
public enum EslWorkload {
    USUARIOS,
    REFERENCIAS,
    COLETAS,
    MANIFESTOS,
    FRETES,
    COTACOES,
    LOCALIZACAO_DE_CARGAS,
    CONTAS_A_PAGAR,
    FATURAS_POR_CLIENTE,
    INVENTARIO,
    SINISTROS
}
