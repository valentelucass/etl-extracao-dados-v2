package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Porta de saída para obter uma página Data Export. */
public interface DataExportGateway {

    DataExportPageResponse fetch(DataExportPageRequest request);
}
