package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;

/** Traduz a janela de Fretes para o contrato 6389. */
public final class FreteDataExportPageRequestFactory {

    public DataExportPageRequest create(
            final BusinessDateRange serviceAtWindow,
            final SourceDateTimeRange updatedAtWindow,
            final int page) {
        return DataExportPageRequest.forTemplate(
                DataExportTemplate.FRETES, serviceAtWindow, updatedAtWindow, page);
    }
}
