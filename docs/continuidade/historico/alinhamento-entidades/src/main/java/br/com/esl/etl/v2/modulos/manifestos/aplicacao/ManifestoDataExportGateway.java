package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;

/** Porta isolada da fonte 6399; não há binding HTTP ou dispatcher positivo nesta vertical. */
@FunctionalInterface
public interface ManifestoDataExportGateway {

    DataExportPageResponse fetch(ManifestoDataExportPageRequest request);
}
