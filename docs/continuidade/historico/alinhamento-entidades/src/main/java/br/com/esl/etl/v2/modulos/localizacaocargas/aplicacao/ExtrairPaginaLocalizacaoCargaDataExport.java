package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import java.time.LocalDate;
import java.util.Objects;

/** Caso de uso local; não abre rede, não publica e não resolve relações. */
public final class ExtrairPaginaLocalizacaoCargaDataExport {
    private final LocalizacaoCargaDataExportGateway gateway;

    public ExtrairPaginaLocalizacaoCargaDataExport(
            final LocalizacaoCargaDataExportGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway local 8656 é obrigatório.");
    }

    public DataExportPageResponse execute(
            final LocalDate startInclusive, final LocalDate endExclusive, final int page) {
        return gateway.fetch(
                new LocalizacaoCargaDataExportPageRequest(startInclusive, endExclusive, page));
    }
}
