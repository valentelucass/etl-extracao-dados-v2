package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Baselines estruturais V2-025a consumíveis pelo runtime, sem depender de fixtures de teste. */
public final class DataExportFirstWaveContractCatalog {

    private static final SourceContractRelease COLETAS =
            release(
                    DataExportTemplate.COLETAS,
                    metadata(
                            field("id", "integer"),
                            field("sequence_code", "integer"),
                            field("updated_at", "datetime"),
                            filter("request_date", "date"),
                            filter("scopes.by_updated_at", "datetime")),
                    response(
                            record("/id", false, ContractResponse.JsonType.INTEGER),
                            record(
                                    "/pck_mik_mft_sequence_code",
                                    true,
                                    ContractResponse.JsonType.INTEGER),
                            record("/sequence_code", false, ContractResponse.JsonType.INTEGER),
                            record("/updated_at", false, ContractResponse.JsonType.STRING)));

    private static final SourceContractRelease FRETES =
            release(
                    DataExportTemplate.FRETES,
                    metadata(
                            field("corporation_sequence_number", "integer"),
                            field("finished_at", "string"),
                            field("fit_dpn_performance_finished_at", "string"),
                            field("fit_p_m_pck_sequence_code", "integer"),
                            field("reference_number", "string"),
                            field("updated_at", "datetime"),
                            filter("freights.service_at", "date"),
                            filter("scopes.by_updated_at", "datetime")),
                    response(
                            record(
                                    "/corporation_sequence_number",
                                    false,
                                    ContractResponse.JsonType.INTEGER),
                            record("/finished_at", false, ContractResponse.JsonType.STRING),
                            record(
                                    "/fit_dpn_performance_finished_at",
                                    true,
                                    ContractResponse.JsonType.STRING),
                            record(
                                    "/fit_p_m_pck_sequence_code",
                                    true,
                                    ContractResponse.JsonType.INTEGER),
                            record("/id", false, ContractResponse.JsonType.INTEGER),
                            record("/updated_at", false, ContractResponse.JsonType.STRING)));

    private DataExportFirstWaveContractCatalog() {}

    public static SourceContractRelease release(final DataExportTemplate template) {
        return switch (Objects.requireNonNull(template, "O template é obrigatório.")) {
            case COLETAS -> COLETAS;
            case FRETES -> FRETES;
            default -> throw new IllegalArgumentException("FIRST_WAVE_CONTRACT_ONLY");
        };
    }

    private static SourceContractRelease release(
            final DataExportTemplate template,
            final ContractMetadata metadata,
            final ContractResponse response) {
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(template),
                template.contractVersion(),
                metadata,
                response);
    }

    private static ContractMetadata metadata(final ContractMetadata.Element... elements) {
        return new ContractMetadata(List.of(elements), Optional.empty());
    }

    private static ContractMetadata.Element field(final String path, final String type) {
        return ContractMetadata.Element.fromDeclaredType(
                ContractMetadata.ElementKind.DATA_FIELD, path, Optional.of(type));
    }

    private static ContractMetadata.Element filter(final String path, final String type) {
        return ContractMetadata.Element.fromDeclaredType(
                ContractMetadata.ElementKind.DATA_FILTER, path, Optional.of(type));
    }

    private static ContractResponse response(final ContractResponse.Field... records) {
        final java.util.ArrayList<ContractResponse.Field> fields =
                new java.util.ArrayList<>(List.of(records));
        fields.add(
                new ContractResponse.Field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.ARRAY)));
        return new ContractResponse(
                "/data",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.ObservationState.POPULATED,
                "/id",
                fields);
    }

    private static ContractResponse.Field record(
            final String path, final boolean nullable, final ContractResponse.JsonType jsonType) {
        return new ContractResponse.Field(
                path,
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                nullable,
                List.of(jsonType));
    }
}
