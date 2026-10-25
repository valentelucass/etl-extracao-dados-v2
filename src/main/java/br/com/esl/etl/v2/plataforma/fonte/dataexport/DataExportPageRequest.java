package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        List<String> orderBy,
        @JsonIgnore DataExportRequestProfile profile) {

    /** Historical constructor and JSON shape remain a legacy ordered request. */
    public DataExportPageRequest(
            final DataExportTemplate template,
            final BusinessDateRange businessDateWindow,
            final Optional<SourceDateTimeRange> updatedAtWindow,
            final int page,
            final int pageSize,
            final List<String> orderBy) {
        this(
                template,
                businessDateWindow,
                updatedAtWindow,
                page,
                pageSize,
                orderBy,
                DataExportRequestProfile.LEGACY);
    }

    public DataExportPageRequest {
        Objects.requireNonNull(template, "O template é obrigatório.");
        Objects.requireNonNull(businessDateWindow, "A janela de data de negócio é obrigatória.");
        profile = profile == null ? DataExportRequestProfile.LEGACY : profile;
        updatedAtWindow = updatedAtWindow == null ? Optional.empty() : updatedAtWindow;
        if (template.laboratoryBackfillOnly() && updatedAtWindow.isPresent()) {
            throw new IllegalArgumentException("UPDATED_AT_FILTER_NOT_CONTRACTED");
        }
        if (page < 1) {
            throw new IllegalArgumentException("A página deve ser maior que zero.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("O tamanho da página deve ser maior que zero.");
        }
        profile.requireShape(
                template, businessDateWindow, updatedAtWindow, page, pageSize, orderBy);
        final List<String> requestedOrderBy = orderBy == null ? template.defaultOrderBy() : orderBy;
        if (!profile.observed() && requestedOrderBy.isEmpty()) {
            throw new IllegalArgumentException(
                    "A ordenação Data Export é obrigatória para paginação rastreável.");
        }
        if (requestedOrderBy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("A ordenação não pode conter valores vazios.");
        }
        orderBy = List.copyOf(requestedOrderBy);
    }

    /** Only three pinned observed releases can create an explicitly unordered request. */
    public static DataExportPageRequest observed(
            final DataExportTemplate template,
            final SourceContractRelease release,
            final ImmutableFingerprint baseRuntimeFingerprint,
            final BusinessDateRange dates,
            final int page,
            final int pageSize) {
        return new DataExportPageRequest(
                template,
                dates,
                Optional.empty(),
                page,
                pageSize,
                List.of(),
                DataExportRequestProfile.observed(
                        template, release, baseRuntimeFingerprint, dates, pageSize));
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof DataExportPageRequest value
                && template == value.template
                && businessDateWindow.equals(value.businessDateWindow)
                && updatedAtWindow.equals(value.updatedAtWindow)
                && page == value.page
                && pageSize == value.pageSize
                && orderBy.equals(value.orderBy)
                && profile.equals(value.profile);
    }

    /** Preserve the six-component record's legacy hash while distinguishing observed selections. */
    @Override
    public int hashCode() {
        int result = template.hashCode();
        result = 31 * result + businessDateWindow.hashCode();
        result = 31 * result + updatedAtWindow.hashCode();
        result = 31 * result + Integer.hashCode(page);
        result = 31 * result + Integer.hashCode(pageSize);
        result = 31 * result + orderBy.hashCode();
        return profile.observed() ? 31 * result + profile.hashCode() : result;
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
                template,
                businessDateWindow,
                updatedAtWindow,
                nextPage,
                pageSize,
                orderBy,
                profile);
    }

    public Map<SearchPath, DataExportFilterValue> filters() {
        final Map<SearchPath, DataExportFilterValue> filters = new LinkedHashMap<>();
        filters.put(template.businessDateFilter(), businessDateWindow);
        if (template == DataExportTemplate.CONTAS_A_PAGAR) {
            filters.put(new SearchPath("accounting_debits", "created_at"), businessDateWindow);
        }
        updatedAtWindow.ifPresent(value -> filters.put(template.updatedAtFilter(), value));
        return Collections.unmodifiableMap(filters);
    }
}
