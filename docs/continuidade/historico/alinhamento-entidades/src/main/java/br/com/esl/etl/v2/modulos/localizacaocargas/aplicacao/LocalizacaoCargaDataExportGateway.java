package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;

/** Porta isolada 8656, deliberadamente sem adapter HTTP ou dispatcher positivo. */
@FunctionalInterface
public interface LocalizacaoCargaDataExportGateway {
    DataExportPageResponse fetch(LocalizacaoCargaDataExportPageRequest request);
}
