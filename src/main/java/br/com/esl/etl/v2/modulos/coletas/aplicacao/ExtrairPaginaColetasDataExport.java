package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.util.Objects;

/** Caso de uso de leitura em sombra da página de Coletas no template 6908. */
public final class ExtrairPaginaColetasDataExport {

    private final DataExportGateway gateway;
    private final ColetaDataExportPageRequestFactory requestFactory;

    public ExtrairPaginaColetasDataExport(final DataExportGateway gateway) {
        this(gateway, new ColetaDataExportPageRequestFactory());
    }

    ExtrairPaginaColetasDataExport(
            final DataExportGateway gateway,
            final ColetaDataExportPageRequestFactory requestFactory) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway Data Export é obrigatório.");
        this.requestFactory =
                Objects.requireNonNull(requestFactory, "A fábrica de requisição é obrigatória.");
    }

    public DataExportPageResponse execute(
            final BusinessDateRange requestDateWindow,
            final SourceDateTimeRange updatedAtWindow,
            final int page) {
        return gateway.fetch(requestFactory.create(requestDateWindow, updatedAtWindow, page));
    }
}
