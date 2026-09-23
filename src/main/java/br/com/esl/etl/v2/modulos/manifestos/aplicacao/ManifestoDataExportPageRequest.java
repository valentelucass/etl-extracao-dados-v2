package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import java.util.List;
import java.util.Objects;

/** Requisição 6399 local: somente filtro contratado, página numérica e ordem de paridade. */
public record ManifestoDataExportPageRequest(BusinessDateRange serviceDateWindow, int page) {

    public static final int TEMPLATE_ID = 6399;
    public static final int PAGE_SIZE = 100;
    public static final List<String> ORDER_BY = List.of("sequence_code asc");

    public ManifestoDataExportPageRequest {
        Objects.requireNonNull(serviceDateWindow, "A janela de service_date é obrigatória.");
        if (page < 1) {
            throw new IllegalArgumentException("A página deve ser positiva.");
        }
    }

    public String filterName() {
        return "manifests.service_date";
    }

    public ManifestoDataExportPageRequest withPage(final int nextPage) {
        return new ManifestoDataExportPageRequest(serviceDateWindow, nextPage);
    }
}
