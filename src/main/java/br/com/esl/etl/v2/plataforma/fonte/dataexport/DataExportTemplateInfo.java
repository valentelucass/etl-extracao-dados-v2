package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Metadados sanitizados retornados pelo recurso {@code /info} de um template. */
public record DataExportTemplateInfo(
        DataExportTemplate template,
        int httpStatus,
        Optional<String> retryAfter,
        boolean jsonContentTypeDeclared,
        List<DataExportMetadataField> fields,
        List<DataExportMetadataField> filters) {

    public DataExportTemplateInfo(
            final DataExportTemplate template,
            final int httpStatus,
            final Optional<String> retryAfter,
            final List<DataExportMetadataField> fields,
            final List<DataExportMetadataField> filters) {
        this(template, httpStatus, retryAfter, false, fields, filters);
    }

    public DataExportTemplateInfo {
        template = Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("Metadados Data Export exigem HTTP de sucesso.");
        }
        retryAfter = Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.");
        fields =
                List.copyOf(
                        Objects.requireNonNull(fields, "Os campos declarados são obrigatórios."));
        filters =
                List.copyOf(
                        Objects.requireNonNull(filters, "Os filtros declarados são obrigatórios."));
        if ((long) fields.size() + filters.size() > ContractMetadata.MAXIMUM_ELEMENTS) {
            throw new IllegalArgumentException(
                    "Os metadados Data Export excedem o limite de itens.");
        }
    }

    @Override
    public String toString() {
        return "DataExportTemplateInfo[template="
                + template
                + ", httpStatus="
                + httpStatus
                + ", retryAfterObserved="
                + retryAfter.isPresent()
                + ", jsonContentTypeDeclared="
                + jsonContentTypeDeclared
                + ", fieldCount="
                + fields.size()
                + ", filterCount="
                + filters.size()
                + "]";
    }
}
