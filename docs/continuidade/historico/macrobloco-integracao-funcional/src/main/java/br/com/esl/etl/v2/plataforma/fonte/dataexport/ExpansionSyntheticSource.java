package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Versioned fixture transport. Every generated page traverses the real parser and contract gate.
 */
public final class ExpansionSyntheticSource {

    private final IntFunction<String> pages;
    private final Observer observer;
    private long bytes;
    private int fetched;
    private int maximumPageBytes;

    public ExpansionSyntheticSource(final IntFunction<String> pages) {
        this(pages, Observer.NONE);
    }

    private ExpansionSyntheticSource(final IntFunction<String> pages, final Observer observer) {
        this.pages = java.util.Objects.requireNonNull(pages);
        this.observer = java.util.Objects.requireNonNull(observer);
    }

    public ExpansionSyntheticSource observed(final Observer measurement) {
        return new ExpansionSyntheticSource(pages, measurement);
    }

    public static SourceContractRelease release(final DataExportTemplate template) {
        if (!template.syntheticOccurrenceCapture()) {
            throw new IllegalArgumentException("EXP_TEMPLATE_REQUIRED");
        }
        final var fields = new ArrayList<ContractResponse.Field>();
        fields.add(field("/capture_occurrence", true, "INTEGER"));
        fields.add(field("/provenance", true, "STRING"));
        fields.add(field("/data", true, "OBJECT"));
        fields.add(field("/binding", false, "OBJECT"));
        for (final String name : List.of("root", "part", "component")) {
            fields.add(field("/binding/" + name, false, "IDENTIFIER"));
        }
        for (final String name : List.of("evidence", "currency", "unit")) {
            fields.add(field("/binding/" + name, false, "STRING"));
        }
        fields.add(field("/binding/revision", false, "INTEGER"));
        for (final String name : List.of("active", "reactivation", "additive_allocation")) {
            fields.add(field("/binding/" + name, false, "BOOLEAN"));
        }
        switch (template) {
            case CONTAS_A_PAGAR -> {
                fields.add(field("/data/ant_aln_name", false, "STRING"));
                fields.add(field("/data/ant_ces_acr_name", false, "STRING"));
                fields.add(field("/data/ant_ces_value", false, "DECIMAL"));
                fields.add(field("/data/ant_crn_psn_nickname", false, "STRING"));
                fields.add(field("/data/ant_ils_atn_liquidation_date", false, "STRING"));
                fields.add(field("/data/ant_ils_atn_reconciled", false, "BOOLEAN"));
                fields.add(field("/data/ant_ils_atn_transaction_date", false, "STRING"));
                fields.add(field("/data/ant_ils_comments", false, "STRING"));
                fields.add(field("/data/ant_ils_expense_description", false, "STRING"));
                fields.add(field("/data/ant_ils_pas_ant_classification", false, "STRING"));
                fields.add(field("/data/ant_ils_pas_ant_name", false, "STRING"));
                fields.add(field("/data/ant_ils_pas_value", false, "DECIMAL"));
                fields.add(field("/data/ant_ils_sequence_code", false, "IDENTIFIER"));
                fields.add(field("/data/ant_rir_name", false, "STRING"));
                fields.add(field("/data/ant_uer_name", false, "STRING"));
                fields.add(field("/data/comments", false, "STRING"));
                fields.add(field("/data/competence_month", false, "IDENTIFIER"));
                fields.add(field("/data/competence_year", false, "IDENTIFIER"));
                fields.add(field("/data/created_at", false, "STRING"));
                fields.add(field("/data/discount_value", false, "DECIMAL"));
                fields.add(field("/data/document", false, "STRING"));
                fields.add(field("/data/interest_value", false, "DECIMAL"));
                fields.add(field("/data/issue_date", false, "STRING"));
                fields.add(field("/data/paid", false, "BOOLEAN"));
                fields.add(field("/data/paid_value", false, "DECIMAL"));
                fields.add(field("/data/type", false, "STRING"));
                fields.add(field("/data/value", false, "DECIMAL"));
                fields.add(field("/data/value_to_pay", false, "DECIMAL"));
            }
            case FATURAS_POR_CLIENTE -> {
                fields.add(field("/data/comments", false, "STRING"));
                fields.add(field("/data/corporation_sequence_number", false, "IDENTIFIER"));
                fields.add(field("/data/courtesy", false, "BOOLEAN"));
                fields.add(field("/data/emission_type", false, "STRING"));
                fields.add(field("/data/finished_at", false, "STRING"));
                fields.add(field("/data/fit_ant_ant_name", false, "STRING"));
                fields.add(field("/data/fit_ant_discount_value", false, "DECIMAL"));
                fields.add(field("/data/fit_ant_document", false, "STRING"));
                fields.add(field("/data/fit_ant_ils_atn_transaction_date", false, "STRING"));
                fields.add(field("/data/fit_ant_ils_due_date", false, "STRING"));
                fields.add(field("/data/fit_ant_ils_original_due_date", false, "STRING"));
                fields.add(field("/data/fit_ant_interest_value", false, "DECIMAL"));
                fields.add(field("/data/fit_ant_issue_date", false, "STRING"));
                fields.add(field("/data/fit_ant_tat_account_number", false, "STRING"));
                fields.add(field("/data/fit_ant_tat_agency_number", false, "STRING"));
                fields.add(field("/data/fit_ant_tat_bnk_name", false, "STRING"));
                fields.add(field("/data/fit_ant_tat_bro_description", false, "STRING"));
                fields.add(field("/data/fit_ant_tat_custom_instruction", false, "STRING"));
                fields.add(field("/data/fit_ant_value", false, "DECIMAL"));
                fields.add(field("/data/fit_crn_psn_nickname", false, "STRING"));
                fields.add(field("/data/fit_d_t_created_at", false, "STRING"));
                fields.add(field("/data/fit_diy_sae_name", false, "STRING"));
                fields.add(field("/data/fit_dyn_drt_nickname", false, "STRING"));
                fields.add(field("/data/fit_fhe_cte_issued_at", false, "STRING"));
                fields.add(field("/data/fit_fhe_cte_key", false, "STRING"));
                fields.add(field("/data/fit_fhe_cte_number", false, "IDENTIFIER"));
                fields.add(field("/data/fit_fhe_cte_status", false, "STRING"));
                fields.add(field("/data/fit_fhe_cte_status_result", false, "STRING"));
                fields.add(field("/data/fit_fsn_name", false, "STRING"));
                fields.add(field("/data/fit_fte_foe_ore_description", false, "STRING"));
                fields.add(field("/data/fit_fte_has_delivery_receipt", false, "BOOLEAN"));
                fields.add(field("/data/fit_fte_invoices_order_number", false, "ARRAY"));
                fields.add(field("/data/fit_fte_invoices_order_number/*", false, "STRING"));
                fields.add(field("/data/fit_nse_number", false, "IDENTIFIER"));
                fields.add(field("/data/fit_pyr_cor_billing_cycle", false, "STRING"));
                fields.add(field("/data/fit_pyr_cor_billing_due_in_days", false, "IDENTIFIER"));
                fields.add(field("/data/fit_pyr_document", false, "STRING"));
                fields.add(field("/data/fit_pyr_name", false, "STRING"));
                fields.add(field("/data/fit_rpt_document", false, "STRING"));
                fields.add(field("/data/fit_rpt_name", false, "STRING"));
                fields.add(field("/data/fit_sdr_document", false, "STRING"));
                fields.add(field("/data/fit_sdr_name", false, "STRING"));
                fields.add(field("/data/fit_sps_slr_psn_name", false, "STRING"));
                fields.add(field("/data/id", false, "IDENTIFIER"));
                fields.add(field("/data/invoices_mapping", false, "ARRAY"));
                fields.add(field("/data/invoices_mapping/*", false, "STRING"));
                fields.add(field("/data/nfse_number", false, "IDENTIFIER"));
                fields.add(field("/data/payment_type", false, "STRING"));
                fields.add(field("/data/reference_number", false, "STRING"));
                fields.add(field("/data/service_at", false, "STRING"));
                fields.add(field("/data/service_type", false, "STRING"));
                fields.add(field("/data/status", false, "STRING"));
                fields.add(field("/data/third_party_ctes_value", false, "DECIMAL"));
                fields.add(field("/data/total", false, "DECIMAL"));
                fields.add(field("/data/type", false, "STRING"));
            }
            case INVENTARIO -> {
                fields.add(
                        field(
                                "/data/cnr_c_s_fit_corporation_sequence_number",
                                false,
                                "IDENTIFIER"));
                fields.add(field("/data/cnr_c_s_fit_dpn_delivery_prediction_at", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_dpn_performance_finished_at", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_dyn_drt_nickname", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_dyn_name", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_fte_lce_occurrence_at", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_fte_lce_ore_description", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_invoices_mapping", false, "ARRAY"));
                fields.add(field("/data/cnr_c_s_fit_invoices_mapping/*", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_invoices_value", false, "DECIMAL"));
                fields.add(field("/data/cnr_c_s_fit_invoices_volumes", false, "IDENTIFIER"));
                fields.add(field("/data/cnr_c_s_fit_pyr_nickname", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_real_weight", false, "DECIMAL"));
                fields.add(field("/data/cnr_c_s_fit_rpt_ads_cty_name", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_rpt_nickname", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_sdr_ads_cty_name", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_sdr_nickname", false, "STRING"));
                fields.add(field("/data/cnr_c_s_fit_taxed_weight", false, "DECIMAL"));
                fields.add(field("/data/cnr_c_s_fit_total_cubic_volume", false, "DECIMAL"));
                fields.add(field("/data/cnr_c_s_read_volumes", false, "IDENTIFIER"));
                fields.add(field("/data/cnr_cis_eoe_psn_name", false, "STRING"));
                fields.add(field("/data/cnr_crn_psn_nickname", false, "STRING"));
                fields.add(field("/data/finished_at", false, "STRING"));
                fields.add(field("/data/sequence_code", false, "IDENTIFIER"));
                fields.add(field("/data/started_at", false, "STRING"));
                fields.add(field("/data/status", false, "STRING"));
                fields.add(field("/data/type", false, "STRING"));
            }
            case SINISTROS -> {
                fields.add(field("/data/bo_number", false, "STRING"));
                fields.add(field("/data/brokers_case_number", false, "STRING"));
                fields.add(field("/data/customer_communication_contact_name", false, "STRING"));
                fields.add(field("/data/customer_communication_date", false, "STRING"));
                fields.add(field("/data/customer_communication_time", false, "STRING"));
                fields.add(field("/data/customer_credit_entries_subtotal", false, "DECIMAL"));
                fields.add(field("/data/customer_debits_subtotal", false, "DECIMAL"));
                fields.add(field("/data/expected_solution_date", false, "STRING"));
                fields.add(field("/data/finished_at_date", false, "STRING"));
                fields.add(field("/data/finished_at_time", false, "STRING"));
                fields.add(field("/data/finished_commentary", false, "STRING"));
                fields.add(field("/data/icm_crn_psn_nickname", false, "STRING"));
                fields.add(field("/data/icm_dvr_iil_name", false, "STRING"));
                fields.add(field("/data/icm_fer_name", false, "STRING"));
                fields.add(
                        field(
                                "/data/icm_fis_fit_corporation_sequence_number",
                                false,
                                "IDENTIFIER"));
                fields.add(field("/data/icm_fis_fit_pyr_nickname", false, "STRING"));
                fields.add(field("/data/icm_fis_ioe_number", false, "IDENTIFIER"));
                fields.add(field("/data/icm_ttt_dealing_type", false, "STRING"));
                fields.add(field("/data/icm_ttt_ore_code", false, "IDENTIFIER"));
                fields.add(field("/data/icm_ttt_ore_description", false, "STRING"));
                fields.add(field("/data/icm_ttt_solution_type", false, "STRING"));
                fields.add(field("/data/icm_ttt_treatment_at", false, "STRING"));
                fields.add(field("/data/icm_vie_license_plate", false, "STRING"));
                fields.add(field("/data/informed_by", false, "STRING"));
                fields.add(field("/data/insurance_claim_commentary", false, "STRING"));
                fields.add(field("/data/insurance_claim_location", false, "STRING"));
                fields.add(field("/data/insurance_claim_total", false, "DECIMAL"));
                fields.add(field("/data/insurer_credits_subtotal", false, "DECIMAL"));
                fields.add(field("/data/internal_description", false, "STRING"));
                fields.add(field("/data/invoices_count", false, "IDENTIFIER"));
                fields.add(field("/data/invoices_value", false, "DECIMAL"));
                fields.add(field("/data/invoices_volumes", false, "IDENTIFIER"));
                fields.add(field("/data/invoices_weight", false, "DECIMAL"));
                fields.add(field("/data/list_of_claimed_products", false, "STRING"));
                fields.add(field("/data/occurrence_at_date", false, "STRING"));
                fields.add(field("/data/occurrence_at_time", false, "STRING"));
                fields.add(field("/data/opening_at_date", false, "STRING"));
                fields.add(field("/data/policy_number", false, "STRING"));
                fields.add(field("/data/rcfdc", false, "ARRAY"));
                fields.add(field("/data/rcfdc/*", false, "STRING"));
                fields.add(field("/data/rctac", false, "ARRAY"));
                fields.add(field("/data/rctac/*", false, "STRING"));
                fields.add(field("/data/rctrc", false, "ARRAY"));
                fields.add(field("/data/rctrc/*", false, "STRING"));
                fields.add(field("/data/responsible_credits_subtotal", false, "DECIMAL"));
                fields.add(field("/data/responsible_debit_entries_subtotal", false, "DECIMAL"));
                fields.add(field("/data/sequence_code", false, "IDENTIFIER"));
            }
            default -> throw new IllegalArgumentException("EXP_TEMPLATE_REQUIRED");
        }
        final var metadata = new ArrayList<ContractMetadata.Element>();
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        template.metadataBusinessDateFilterName(),
                        Optional.of("date")));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(template),
                "expansion-synthetic-v1",
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/capture_occurrence",
                        fields));
    }

    private static ContractResponse.Field field(
            final String path, final boolean required, final String kind) {
        final var types =
                switch (kind) {
                    case "IDENTIFIER" ->
                            List.of(
                                    ContractResponse.JsonType.INTEGER,
                                    ContractResponse.JsonType.STRING);
                    case "DECIMAL" ->
                            List.of(
                                    ContractResponse.JsonType.INTEGER,
                                    ContractResponse.JsonType.NUMBER,
                                    ContractResponse.JsonType.STRING);
                    default -> List.of(ContractResponse.JsonType.valueOf(kind));
                };
        return new ContractResponse.Field(
                path,
                kind.equals("ARRAY")
                        ? ContractResponse.Cardinality.ARRAY
                        : kind.equals("OBJECT")
                                ? ContractResponse.Cardinality.OBJECT
                                : ContractResponse.Cardinality.SCALAR,
                required ? ContractResponse.Presence.REQUIRED : ContractResponse.Presence.OPTIONAL,
                !required,
                types);
    }

    public DataExportGateway gateway(
            final DataExportTemplate template,
            final ContractRunGuard guard,
            final SourceContractRelease release,
            final ImmutableFingerprint configuration) {
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var adapter = new DataExportContractAdapter(limits, guard.responsePathBoundary());
        final var observation =
                DataExportContractObservationConfiguration.forRelease(
                        template, release, limits, guard.responsePathBoundary(), configuration);
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        request -> {
                            final String page = pages.apply(request.page());
                            if (page == null || page.length() > 65_536) {
                                throw new IllegalArgumentException("EXP_LAB_FIXTURE_PAGE_BOUND");
                            }
                            final int size = page.getBytes(StandardCharsets.UTF_8).length;
                            if (size > 65_536) {
                                throw new IllegalArgumentException("EXP_LAB_FIXTURE_PAGE_BOUND");
                            }
                            try {
                                final var tree = DataExportStrictJsonParser.readTree(page);
                                if (!tree.isArray()) {
                                    throw new IllegalArgumentException(
                                            "EXP_LAB_FIXTURE_ARRAY_REQUIRED");
                                }
                                for (final var row : tree) {
                                    if (!"FIXTURE_SINTETICA_EXPLICITA"
                                            .equals(row.path("provenance").asText())) {
                                        throw new IllegalArgumentException(
                                                "EXP_LAB_SYNTHETIC_MARKER_REQUIRED");
                                    }
                                }
                                final var normalized =
                                        new DataExportResponseNormalizer().normalize(tree);
                                DataExportPageEntityLimitValidator.validate(request, normalized);
                                fetched = Math.incrementExact(fetched);
                                bytes = Math.addExact(bytes, size);
                                maximumPageBytes = Math.max(maximumPageBytes, size);
                                return normalized.withObservation(
                                        adapter.response(
                                                tree,
                                                DataExportResponseForm.ROOT_ARRAY,
                                                "/" + template.paginationEntityField()),
                                        limits,
                                        guard.responsePathBoundary());
                            } catch (final JsonProcessingException failure) {
                                throw new IllegalArgumentException(
                                        "EXP_LAB_FIXTURE_JSON_INVALID", failure);
                            }
                        },
                        ignored -> {
                            throw new IllegalStateException("EXP_LAB_NO_METADATA_IO");
                        },
                        observation);
        final var secured = DataExportContractGate.enforce(bundle, template, guard);
        guard.validateMetadata(release.metadata());
        return request -> {
            observer.beforeFetch();
            final long previousBytes = bytes;
            final var response = secured.dataGateway().fetch(request);
            observer.pageFetched(response, bytes - previousBytes);
            return response;
        };
    }

    public void batchStaged(final int records) {
        observer.batchStaged(records);
    }

    public void batchStarted(final int records) {
        observer.batchStarted(records);
    }

    public void captureClosed() {
        observer.captureClosed();
    }

    public interface Observer {
        Observer NONE = new Observer() {};

        default void beforeFetch() {}

        default void pageFetched(DataExportPageResponse page, long bytes) {}

        default void batchStarted(int records) {}

        default void batchStaged(int records) {}

        default void captureClosed() {}
    }

    public Metrics metrics() {
        return new Metrics(fetched, bytes, maximumPageBytes);
    }

    public record Metrics(int fetchedPages, long bytes, int maximumPageBytes) {}
}
