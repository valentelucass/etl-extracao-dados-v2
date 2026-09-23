package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import br.com.esl.etl.v2.plataforma.contrato.ContractSemanticsFingerprint;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.List;
import java.util.Objects;

/** Contratos de paginação; a presença no catálogo não autoriza consulta externa ao template. */
public enum DataExportTemplate {
    COLETAS(
            "2026-08-31.v2-025a.1",
            6908,
            new SearchPath("picks", "request_date"),
            "request_date",
            new SearchPath("scopes", "by_updated_at"),
            "id",
            List.of("sequence_code asc"),
            100),
    FRETES(
            "2026-08-31.v2-025a.1",
            6389,
            new SearchPath("freights", "service_at"),
            "freights.service_at",
            new SearchPath("scopes", "by_updated_at"),
            "id",
            List.of("corporation_sequence_number asc"),
            100),
    MANIFESTOS(
            "bloco55-synthetic-v1",
            6399,
            new SearchPath("manifests", "service_date"),
            "manifests.service_date",
            null,
            "sequence_code",
            List.of("sequence_code asc"),
            100),
    COTACOES(
            "bloco55-synthetic-v1",
            6906,
            new SearchPath("quotes", "requested_at"),
            "quotes.requested_at",
            null,
            "sequence_code",
            List.of("sequence_code asc"),
            100),
    LOCALIZACAO_CARGAS(
            "bloco55-synthetic-v1",
            8656,
            new SearchPath("freights", "service_at"),
            "freights.service_at",
            null,
            "corporation_sequence_number",
            List.of("sequence_number asc"),
            100),
    CONTAS_A_PAGAR(
            "expansion-synthetic-v1",
            8636,
            new SearchPath("accounting_debits", "issue_date"),
            "accounting_debits.issue_date",
            null,
            "capture_occurrence",
            List.of("issue_date desc"),
            100),
    FATURAS_POR_CLIENTE(
            "expansion-synthetic-v1",
            4924,
            new SearchPath("freights", "service_at"),
            "freights.service_at",
            null,
            "capture_occurrence",
            List.of("unique_id asc"),
            100),
    INVENTARIO(
            "expansion-synthetic-v1",
            10633,
            new SearchPath("check_in_orders", "started_at"),
            "check_in_orders.started_at",
            null,
            "capture_occurrence",
            List.of("sequence_code asc"),
            100),
    SINISTROS(
            "expansion-synthetic-v1",
            6392,
            new SearchPath("insurance_claims", "opening_at_date"),
            "insurance_claims.opening_at_date",
            null,
            "capture_occurrence",
            List.of("sequence_code asc"),
            100);

    private final String contractVersion;
    private final int templateId;
    private final SearchPath businessDateFilter;
    private final String metadataBusinessDateFilterName;
    private final SearchPath updatedAtFilter;
    private final String paginationEntityField;
    private final List<String> defaultOrderBy;
    private final int defaultPageSize;
    private final ImmutableFingerprint contractSemanticsFingerprint;

    DataExportTemplate(
            final String contractVersion,
            final int templateId,
            final SearchPath businessDateFilter,
            final String metadataBusinessDateFilterName,
            final SearchPath updatedAtFilter,
            final String paginationEntityField,
            final List<String> defaultOrderBy,
            final int defaultPageSize) {
        this.contractVersion = contractVersion;
        this.templateId = templateId;
        this.businessDateFilter = businessDateFilter;
        if (metadataBusinessDateFilterName == null || metadataBusinessDateFilterName.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do filtro de metadata Data Export é obrigatório.");
        }
        this.metadataBusinessDateFilterName = metadataBusinessDateFilterName;
        this.updatedAtFilter = updatedAtFilter;
        if (paginationEntityField == null || paginationEntityField.isBlank()) {
            throw new IllegalArgumentException("O campo de entidade de paginação é obrigatório.");
        }
        this.paginationEntityField = paginationEntityField;
        this.defaultOrderBy = List.copyOf(defaultOrderBy);
        this.defaultPageSize = defaultPageSize;
        this.contractSemanticsFingerprint = createSemanticsFingerprint();
    }

    public String contractVersion() {
        return contractVersion;
    }

    public int templateId() {
        return templateId;
    }

    public SearchPath businessDateFilter() {
        return businessDateFilter;
    }

    /** Nome observado em metadata; pode diferir deliberadamente do path usado em {@code search}. */
    public String metadataBusinessDateFilterName() {
        return metadataBusinessDateFilterName;
    }

    public SearchPath updatedAtFilter() {
        if (updatedAtFilter == null) {
            throw new IllegalArgumentException("UPDATED_AT_FILTER_NOT_CONTRACTED");
        }
        return updatedAtFilter;
    }

    public boolean laboratoryBackfillOnly() {
        return updatedAtFilter == null;
    }

    /** Restricted envelope occurrence contract. The per budget counts physical rows only. */
    public boolean syntheticOccurrenceCapture() {
        return contractVersion.equals("expansion-synthetic-v1");
    }

    /** Campo escalar que define a entidade contada pelo parâmetro {@code per}. */
    public String paginationEntityField() {
        return paginationEntityField;
    }

    public List<String> defaultOrderBy() {
        return defaultOrderBy;
    }

    public int defaultPageSize() {
        return defaultPageSize;
    }

    /** Limita {@code per} ao teto caracterizado e exige a ordenação versionada exata. */
    public boolean approvesRequestSemantics(final int pageSize, final List<String> orderBy) {
        return pageSize > 0
                && pageSize <= defaultPageSize
                && defaultOrderBy.equals(
                        Objects.requireNonNull(orderBy, "A ordenação é obrigatória."));
    }

    public DataExportTransport approvedTransport() {
        return DataExportTransport.GET_WITH_QUERY;
    }

    public DataExportResponseForm promotableResponseForm() {
        return DataExportResponseForm.ENVELOPE_DATA_ARRAY;
    }

    public ContractClassification contractClassification() {
        return ContractClassification.TRANSITIONAL;
    }

    public SourceCompletenessStatus completenessStatus() {
        return SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF;
    }

    public ImmutableFingerprint contractSemanticsFingerprint() {
        return contractSemanticsFingerprint;
    }

    private ImmutableFingerprint createSemanticsFingerprint() {
        final String[] elements = new String[13 + defaultOrderBy.size()];
        int index = 0;
        elements[index++] = "DATA_EXPORT";
        elements[index++] = Integer.toString(templateId);
        elements[index++] = businessDateFilter.asQueryParameterName();
        elements[index++] = metadataBusinessDateFilterName;
        elements[index++] =
                updatedAtFilter == null ? "NOT_CONTRACTED" : updatedAtFilter.asQueryParameterName();
        elements[index++] = paginationEntityField;
        elements[index++] = Integer.toString(defaultPageSize);
        elements[index++] = approvedTransport().name();
        elements[index++] = promotableResponseForm().name();
        elements[index++] = "NUMERIC_PAGE_EMPTY_TERMINAL_LOCAL_UNVERIFIED";
        elements[index++] = "SOURCE_INTERVAL_INCLUSIVE_BOUNDARIES_UNVERIFIED";
        elements[index++] = contractClassification().name();
        elements[index++] = completenessStatus().name();
        for (final String orderBy : defaultOrderBy) {
            elements[index++] = orderBy;
        }
        return ContractSemanticsFingerprint.create(contractVersion, elements);
    }
}
