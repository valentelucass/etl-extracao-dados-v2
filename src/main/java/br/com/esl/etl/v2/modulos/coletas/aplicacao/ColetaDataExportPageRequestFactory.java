package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;

/** Traduz a janela de Coletas para o contrato 6908. */
public final class ColetaDataExportPageRequestFactory {

    public DataExportPageRequest create(
            final BusinessDateRange requestDateWindow,
            final SourceDateTimeRange updatedAtWindow,
            final int page) {
        return DataExportPageRequest.forTemplate(
                DataExportTemplate.COLETAS, requestDateWindow, updatedAtWindow, page);
    }
}
