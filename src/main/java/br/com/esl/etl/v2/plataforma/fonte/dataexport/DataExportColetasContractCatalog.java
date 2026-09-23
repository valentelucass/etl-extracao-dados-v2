package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** COL-SHAPE-01: bounded scalar capture for 6908, independent of the historical V2-025a release. */
public final class DataExportColetasContractCatalog {
    private static final List<String> FIELDS =
            List.of(
                    "id",
                    "pck_crn_psn_nickname",
                    "sequence_code",
                    "created_at",
                    "request_date",
                    "service_date",
                    "finish_date",
                    "pck_cor_nickname",
                    "invoices_volumes",
                    "invoices_weight",
                    "taxed_weight",
                    "invoices_value",
                    "status",
                    "pck_pds_cty_name",
                    "pck_pds_cty_sae_code",
                    "pck_pds_postal_code",
                    "pck_pds_neighborhood",
                    "pck_prn_name",
                    "pck_prn_drt_nickname",
                    "updated_at",
                    "pck_loe_ore_description",
                    "pck_pus_ore_description",
                    "pck_pus_ore_trigger",
                    "pck_uer_name",
                    "pck_ctr_name",
                    "cancellation_reason",
                    "pck_mik_attempt_number",
                    "pck_mik_mft_crn_psn_nickname",
                    "pck_mik_mft_sequence_code",
                    "pck_mik_mft_vie_license_plate",
                    "pck_mik_mft_vie_vee_name");
    private static final SourceContractRelease RELEASE = create();

    private DataExportColetasContractCatalog() {}

    public static SourceContractRelease release() {
        return RELEASE;
    }

    private static SourceContractRelease create() {
        final var metadata = new ArrayList<ContractMetadata.Element>();
        final var response = new ArrayList<ContractResponse.Field>();
        for (final String field : FIELDS) {
            // /info declares these names but no field types. Do not invent declared types.
            metadata.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FIELD, field, Optional.empty()));
            response.add(
                    new ContractResponse.Field(
                            "/" + field,
                            ContractResponse.Cardinality.SCALAR,
                            field.equals("id")
                                    ? ContractResponse.Presence.REQUIRED
                                    : ContractResponse.Presence.OPTIONAL,
                            !field.equals("id"),
                            types(field)));
        }
        for (final String filter : List.of("finish_date", "service_date", "request_date")) {
            metadata.add(filter(filter, "date"));
        }
        metadata.add(filter("corporation_id", "select"));
        metadata.add(filter("status", "select"));
        // Metadata name and request search[scopes][by_updated_at] are different contracts.
        metadata.add(filter("by_updated_at", "datetime"));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(DataExportTemplate.COLETAS),
                "2026-09-10.b62-coletas-scalar-capture.1",
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        response));
    }

    private static ContractMetadata.Element filter(final String name, final String type) {
        return ContractMetadata.Element.fromDeclaredType(
                ContractMetadata.ElementKind.DATA_FILTER, name, Optional.of(type));
    }

    private static List<ContractResponse.JsonType> types(final String name) {
        return switch (name) {
            case "id", "sequence_code", "pck_mik_mft_sequence_code" ->
                    List.of(ContractResponse.JsonType.INTEGER);
            case "status",
                            "cancellation_reason",
                            "created_at",
                            "updated_at",
                            "request_date",
                            "service_date",
                            "finish_date" ->
                    List.of(ContractResponse.JsonType.STRING);
            // Unconsumed, explicitly named columns are preserved as scalar JSON, never coerced.
            // This is a local storage policy, not a claim about the supplier's wire types.
            default ->
                    List.of(
                            ContractResponse.JsonType.STRING,
                            ContractResponse.JsonType.INTEGER,
                            ContractResponse.JsonType.NUMBER,
                            ContractResponse.JsonType.BOOLEAN);
        };
    }
}
