package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/** Página transitória para validação de travessia; nunca é serializada como evidência. */
public record ContractObservedPage(int pageNumber, int requestedPageSize, List<JsonNode> records) {

    public ContractObservedPage {
        if (pageNumber < 1 || requestedPageSize < 1) {
            throw new IllegalArgumentException(
                    "O número e o tamanho da página observada devem ser positivos.");
        }
        records =
                List.copyOf(
                        Objects.requireNonNull(
                                records, "Os registros observados são obrigatórios."));
    }

    public static ContractObservedPage from(
            final DataExportPageRequest request, final DataExportPageFetch pageFetch) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        Objects.requireNonNull(pageFetch, "A página Data Export é obrigatória.");
        return new ContractObservedPage(
                request.page(), request.pageSize(), pageFetch.response().records());
    }

    @Override
    public String toString() {
        return "ContractObservedPage[redacted]";
    }
}
