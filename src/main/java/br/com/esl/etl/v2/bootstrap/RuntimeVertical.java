package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;

/** Closed operational identities. Ordering and pagination never establish a domain relationship. */
final class RuntimeVertical {
    private RuntimeVertical() {}

    static String entity(final DataExportTemplate template) {
        return switch (template) {
            case COLETAS -> "coletas";
            case FRETES -> "fretes";
            case MANIFESTOS -> "manifestos";
            case COTACOES -> "cotacoes";
            case LOCALIZACAO_CARGAS -> "localizacao_cargas";
            default -> throw new IllegalArgumentException("EXP_SEPARATE_LABORATORY_REQUIRED");
        };
    }

    static EslWorkload workload(final DataExportTemplate template) {
        return switch (template) {
            case COLETAS -> EslWorkload.COLETAS;
            case FRETES -> EslWorkload.FRETES;
            case MANIFESTOS -> EslWorkload.MANIFESTOS;
            case COTACOES -> EslWorkload.COTACOES;
            case LOCALIZACAO_CARGAS -> EslWorkload.LOCALIZACAO_DE_CARGAS;
            default -> throw new IllegalArgumentException("EXP_SEPARATE_LABORATORY_REQUIRED");
        };
    }
}
