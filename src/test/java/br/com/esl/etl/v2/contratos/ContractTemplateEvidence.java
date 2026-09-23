package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Metadados e flags não sensíveis de um template na evidência de contrato. */
public record ContractTemplateEvidence(
        int templateId,
        int infoHttpStatus,
        boolean retryAfterObserved,
        boolean infoJsonContentTypeDeclared,
        List<ContractMetadataEvidence> declaredFields,
        List<ContractMetadataEvidence> declaredFilters,
        List<DataExportTransport> acceptedTransports,
        List<ContractPayloadEvidence> payloads,
        Map<ContractEvidenceCheck, Boolean> checks) {

    public ContractTemplateEvidence(
            final int templateId,
            final int infoHttpStatus,
            final boolean retryAfterObserved,
            final List<ContractMetadataEvidence> declaredFields,
            final List<ContractMetadataEvidence> declaredFilters,
            final List<DataExportTransport> acceptedTransports,
            final List<ContractPayloadEvidence> payloads,
            final Map<ContractEvidenceCheck, Boolean> checks) {
        this(
                templateId,
                infoHttpStatus,
                retryAfterObserved,
                false,
                declaredFields,
                declaredFilters,
                acceptedTransports,
                payloads,
                checks);
    }

    public ContractTemplateEvidence {
        if (!isSupportedTemplate(templateId) || infoHttpStatus < 200 || infoHttpStatus >= 300) {
            throw new IllegalArgumentException("Os metadados HTTP da evidência são inválidos.");
        }
        declaredFields =
                List.copyOf(
                        Objects.requireNonNull(
                                declaredFields, "Os campos declarados são obrigatórios."));
        declaredFilters =
                List.copyOf(
                        Objects.requireNonNull(
                                declaredFilters, "Os filtros declarados são obrigatórios."));
        acceptedTransports =
                List.copyOf(
                        Objects.requireNonNull(
                                acceptedTransports, "Os transportes são obrigatórios."));
        payloads =
                List.copyOf(
                        Objects.requireNonNull(
                                payloads, "Os payloads sanitizados são obrigatórios."));
        checks = Map.copyOf(Objects.requireNonNull(checks, "As verificações são obrigatórias."));
    }

    private static boolean isSupportedTemplate(final int templateId) {
        if (templateId == Contract4924Configuration.TEMPLATE_ID) {
            return true;
        }
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            if (template.templateId() == templateId) {
                return true;
            }
        }
        return false;
    }
}
