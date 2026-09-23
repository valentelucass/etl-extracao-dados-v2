package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Synthetic B55 transport schema; it does not ratify the pending provider temporal contract. */
final class RuntimeFiveVerticalContract {
    private RuntimeFiveVerticalContract() {}

    static SourceContractRelease release(final DataExportTemplate template) {
        final List<String> integers;
        final List<String> strings;
        switch (template) {
            case MANIFESTOS -> {
                integers = List.of("sequence_code", "mft_pfs_pck_sequence_code", "mft_mfs_number");
                strings =
                        List.of(
                                "created_at",
                                "departured_at",
                                "closed_at",
                                "finished_at",
                                "status",
                                "mdfe_status",
                                "mft_mfs_key",
                                "km",
                                "total_cost",
                                "manifest_freights_total",
                                "total_taxed_weight",
                                "mft_vie_weight_capacity",
                                "manifest_items_count",
                                "finalized_manifest_items_count",
                                "mft_ape_name",
                                "mft_man_name",
                                "mft_vie_license_plate",
                                "mft_vie_vee_name",
                                "mft_vie_onr_name",
                                "mft_mdr_iil_name",
                                "mft_crn_psn_nickname",
                                "mft_cat_cot_number",
                                "contract_type",
                                "mft_mdr_contract_type",
                                "calculation_type",
                                "cargo_type",
                                "mft_uer_name",
                                "mft_aoe_rer_name",
                                "mft_aoe_comments",
                                "mft_cat_cot_status",
                                "mft_iks_id",
                                "mft_s_n_sequence_code",
                                "mft_tl1_license_plate",
                                "mft_tl2_license_plate",
                                "operational_comments",
                                "closing_comments",
                                "mft_s_n_svs_sge_pyr_nickname",
                                "mft_s_n_svs_sge_sse_name");
            }
            case COTACOES -> {
                integers = List.of("sequence_code");
                strings =
                        List.of(
                                "requested_at",
                                "qoe_qes_fit_nse_issued_at",
                                "qoe_qes_fit_fhe_cte_issued_at",
                                "qoe_qes_total",
                                "qoe_crn_psn_nickname",
                                "qoe_uer_name",
                                "qoe_qes_ony_sae_code",
                                "qoe_qes_diy_sae_code");
            }
            case LOCALIZACAO_CARGAS -> {
                integers = List.of("corporation_sequence_number");
                strings =
                        List.of(
                                "type",
                                "service_at",
                                "invoices_volumes",
                                "taxed_weight",
                                "invoices_value",
                                "total",
                                "service_type",
                                "fit_crn_psn_nickname",
                                "fit_dpn_delivery_prediction_at",
                                "fit_dyn_name",
                                "fit_dyn_drt_nickname",
                                "fit_fsn_name",
                                "fit_fln_status",
                                "fit_fln_cln_nickname",
                                "fit_o_n_name",
                                "fit_o_n_drt_nickname");
            }
            default -> throw new IllegalArgumentException("B55_EXTENSION_TEMPLATE_REQUIRED");
        }
        final var metadata = new ArrayList<ContractMetadata.Element>();
        final var fields = new ArrayList<ContractResponse.Field>();
        for (final String name : integers) {
            add(
                    metadata,
                    fields,
                    name,
                    "integer",
                    ContractResponse.JsonType.INTEGER,
                    name.equals(template.paginationEntityField()));
        }
        for (final String name : strings) {
            add(metadata, fields, name, "string", ContractResponse.JsonType.STRING, false);
        }
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        template.metadataBusinessDateFilterName(),
                        Optional.of("date")));
        fields.add(
                new ContractResponse.Field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.ARRAY)));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(template),
                "bloco55-synthetic-v1",
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/" + template.paginationEntityField(),
                        fields));
    }

    private static void add(
            final List<ContractMetadata.Element> metadata,
            final List<ContractResponse.Field> fields,
            final String name,
            final String type,
            final ContractResponse.JsonType jsonType,
            final boolean key) {
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD, name, Optional.of(type)));
        fields.add(
                new ContractResponse.Field(
                        "/" + name,
                        ContractResponse.Cardinality.SCALAR,
                        key
                                ? ContractResponse.Presence.REQUIRED
                                : ContractResponse.Presence.OPTIONAL,
                        !key,
                        List.of(jsonType)));
    }
}
