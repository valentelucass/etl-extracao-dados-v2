package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import java.util.Objects;

/** Caso de uso local da página 6399; não cria cliente de rede nem publica dados. */
public final class ExtrairPaginaManifestosDataExport {

    private final ManifestoDataExportGateway gateway;

    public ExtrairPaginaManifestosDataExport(final ManifestoDataExportGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway 6399 é obrigatório.");
    }

    public DataExportPageResponse execute(
            final BusinessDateRange serviceDateWindow, final int page) {
        return gateway.fetch(new ManifestoDataExportPageRequest(serviceDateWindow, page));
    }
}
