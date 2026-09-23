package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.util.Objects;

/** Caso de uso de leitura em sombra da página de Fretes no template 6389. */
public final class ExtrairPaginaFretesDataExport {

    private final DataExportGateway gateway;
    private final FreteDataExportPageRequestFactory requestFactory;

    public ExtrairPaginaFretesDataExport(final DataExportGateway gateway) {
        this(gateway, new FreteDataExportPageRequestFactory());
    }

    ExtrairPaginaFretesDataExport(
            final DataExportGateway gateway,
            final FreteDataExportPageRequestFactory requestFactory) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway Data Export é obrigatório.");
        this.requestFactory =
                Objects.requireNonNull(requestFactory, "A fábrica de requisição é obrigatória.");
    }

    public DataExportPageResponse execute(
            final BusinessDateRange serviceAtWindow,
            final SourceDateTimeRange updatedAtWindow,
            final int page) {
        return gateway.fetch(requestFactory.create(serviceAtWindow, updatedAtWindow, page));
    }
}
