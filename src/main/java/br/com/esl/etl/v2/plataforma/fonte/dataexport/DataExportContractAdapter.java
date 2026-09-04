package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponseProfiler;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Converte evidência Data Export em modelos estruturais sanitizados. */
public final class DataExportContractAdapter {

    private final ContractObservationLimits limits;
    private final ContractResponseProfiler responseProfiler;

    public DataExportContractAdapter(
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        this(limits, ContractResponseProfiler.forRuntime(limits, pathBoundary));
    }

    private DataExportContractAdapter(
            final ContractObservationLimits limits,
            final ContractResponseProfiler responseProfiler) {
        this.limits = Objects.requireNonNull(limits, "Os limites de observação são obrigatórios.");
        this.responseProfiler =
                Objects.requireNonNull(responseProfiler, "O profiler de resposta é obrigatório.");
    }

    /** Adapter permissivo exclusivo para autoria offline com documentos sintéticos sanitizados. */
    public static DataExportContractAdapter forSyntheticFixtures(
            final ContractObservationLimits limits) {
        return new DataExportContractAdapter(
                limits, ContractResponseProfiler.forSyntheticFixtures(limits));
    }

    public ContractObservationLimits limits() {
        return limits;
    }

    public static String documentReference(final DataExportTemplate template) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        return "dataexport-" + template.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ContractMetadata metadata(final DataExportTemplateInfo templateInfo) {
        Objects.requireNonNull(templateInfo, "Os metadados Data Export são obrigatórios.");
        final List<ContractMetadata.Element> elements =
                new ArrayList<>(templateInfo.fields().size() + templateInfo.filters().size());
        for (final DataExportMetadataField field : templateInfo.fields()) {
            elements.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FIELD,
                            field.technicalName(),
                            field.declaredType()));
        }
        for (final DataExportMetadataField filter : templateInfo.filters()) {
            elements.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FILTER,
                            filter.technicalName(),
                            filter.declaredType()));
        }
        return new ContractMetadata(elements, Optional.empty());
    }

    public ContractResponse response(
            final JsonNode rawResponse,
            final DataExportResponseForm responseForm,
            final String approvedKeyPath) {
        Objects.requireNonNull(rawResponse, "A resposta Data Export é obrigatória.");
        Objects.requireNonNull(responseForm, "A forma Data Export é obrigatória.");
        Objects.requireNonNull(approvedKeyPath, "O path aprovado da chave é obrigatório.");
        if (DataExportResponseForm.from(rawResponse) != responseForm) {
            throw new IllegalArgumentException("A forma declarada não corresponde à resposta.");
        }
        final boolean envelope =
                responseForm == DataExportResponseForm.ENVELOPE_DATA_ARRAY
                        || responseForm == DataExportResponseForm.ENVELOPE_DATA_OBJECT;
        final JsonNode records = envelope ? rawResponse.get("data") : rawResponse;
        final ContractResponse.Cardinality cardinality =
                responseForm.isArray()
                        ? ContractResponse.Cardinality.ARRAY
                        : ContractResponse.Cardinality.OBJECT;
        if (envelope) {
            return responseProfiler.profileWithEnvelope(
                    records, "/data", cardinality, approvedKeyPath, rawResponse);
        }
        return responseProfiler.profile(records, "$", cardinality, approvedKeyPath);
    }
}
