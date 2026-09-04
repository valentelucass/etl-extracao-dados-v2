package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Requisição de uma página, independente do transporte HTTP escolhido. */
public record DataExportPageRequest(
        DataExportTemplate template,
        BusinessDateRange businessDateWindow,
        Optional<SourceDateTimeRange> updatedAtWindow,
        int page,
        int pageSize,
        List<String> orderBy) {

    public DataExportPageRequest {
        Objects.requireNonNull(template, "O template é obrigatório.");
        Objects.requireNonNull(businessDateWindow, "A janela de data de negócio é obrigatória.");
        updatedAtWindow = updatedAtWindow == null ? Optional.empty() : updatedAtWindow;
        if (page < 1) {
            throw new IllegalArgumentException("A página deve ser maior que zero.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("O tamanho da página deve ser maior que zero.");
        }
        final List<String> requestedOrderBy = orderBy == null ? template.defaultOrderBy() : orderBy;
        if (requestedOrderBy.isEmpty()) {
            throw new IllegalArgumentException(
                    "A ordenação Data Export é obrigatória para paginação rastreável.");
        }
        if (requestedOrderBy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("A ordenação não pode conter valores vazios.");
        }
        orderBy = List.copyOf(requestedOrderBy);
    }

    public static DataExportPageRequest forTemplate(
            final DataExportTemplate template,
            final BusinessDateRange businessDateWindow,
            final SourceDateTimeRange updatedAtWindow,
            final int page) {
        return new DataExportPageRequest(
                template,
                businessDateWindow,
                Optional.ofNullable(updatedAtWindow),
                page,
                template.defaultPageSize(),
                template.defaultOrderBy());
    }

    /** Cria a próxima requisição preservando contrato, filtros, tamanho e ordenação. */
    public DataExportPageRequest withPage(final int nextPage) {
        return new DataExportPageRequest(
                template, businessDateWindow, updatedAtWindow, nextPage, pageSize, orderBy);
    }

    public Map<SearchPath, DataExportFilterValue> filters() {
        final Map<SearchPath, DataExportFilterValue> filters = new LinkedHashMap<>();
        filters.put(template.businessDateFilter(), businessDateWindow);
        updatedAtWindow.ifPresent(value -> filters.put(template.updatedAtFilter(), value));
        return Collections.unmodifiableMap(filters);
    }
}
