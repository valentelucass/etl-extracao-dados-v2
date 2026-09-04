package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Porta de leitura tipada do recurso de metadados {@code /info}. */
public interface DataExportTemplateInfoGateway {

    DataExportTemplateInfo fetchInfo(DataExportTemplate template);
}
