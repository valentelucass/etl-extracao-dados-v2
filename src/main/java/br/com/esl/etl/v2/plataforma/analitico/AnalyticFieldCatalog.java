package br.com.esl.etl.v2.plataforma.analitico;

import java.util.List;

/** Finite wire metadata shared with TVP adapters; contains no captured keys or payloads. */
public final class AnalyticFieldCatalog {
    private AnalyticFieldCatalog() {}

    private static final List<Field> QUOTE =
            List.of(
                    new Field("requested_at", Kind.INSTANT),
                    new Field("sequence_code", Kind.INTEGER),
                    new Field("qoe_qes_fon_name", Kind.TEXT),
                    new Field("qoe_cor_document", Kind.TEXT),
                    new Field("qoe_cor_name", Kind.TEXT),
                    new Field("qoe_qes_ony_name", Kind.TEXT),
                    new Field("qoe_qes_ony_sae_code", Kind.TEXT),
                    new Field("qoe_qes_diy_name", Kind.TEXT),
                    new Field("qoe_qes_diy_sae_code", Kind.TEXT),
                    new Field("qoe_qes_cre_name", Kind.TEXT),
                    new Field("qoe_qes_invoices_volumes", Kind.INTEGER),
                    new Field("qoe_qes_taxed_weight", Kind.DECIMAL),
                    new Field("qoe_qes_invoices_value", Kind.DECIMAL),
                    new Field("qoe_qes_total", Kind.DECIMAL),
                    new Field("qoe_qes_fit_fhe_cte_issued_at", Kind.INSTANT),
                    new Field("qoe_qes_fit_nse_issued_at", Kind.INSTANT),
                    new Field("qoe_uer_name", Kind.TEXT),
                    new Field("qoe_crn_psn_nickname", Kind.TEXT),
                    new Field("qoe_qes_sdr_document", Kind.TEXT),
                    new Field("qoe_qes_sdr_nickname", Kind.TEXT),
                    new Field("qoe_qes_rpt_document", Kind.TEXT),
                    new Field("qoe_qes_rpt_nickname", Kind.TEXT),
                    new Field("qoe_qes_origin_postal_code", Kind.TEXT),
                    new Field("qoe_qes_destination_postal_code", Kind.TEXT),
                    new Field("qoe_qes_real_weight", Kind.DECIMAL),
                    new Field("qoe_qes_disapprove_comments", Kind.TEXT),
                    new Field("qoe_qes_freight_comments", Kind.TEXT),
                    new Field("qoe_qes_fit_fdt_subtotal", Kind.DECIMAL),
                    new Field("requester_name", Kind.TEXT),
                    new Field("qoe_qes_itr_subtotal", Kind.DECIMAL),
                    new Field("qoe_qes_tde_subtotal", Kind.DECIMAL),
                    new Field("qoe_qes_collect_subtotal", Kind.DECIMAL),
                    new Field("qoe_qes_delivery_subtotal", Kind.DECIMAL),
                    new Field("qoe_qes_other_fees", Kind.DECIMAL),
                    new Field("qoe_crn_psn_name", Kind.TEXT),
                    new Field("qoe_cor_nickname", Kind.TEXT));

    private static final List<String> COLLECTION =
            List.of(
                    "request_hour",
                    "vehicle_type_id",
                    "customer_name",
                    "customer_document",
                    "address_line",
                    "address_number",
                    "address_complement",
                    "branch_source_id",
                    "cancellation_user_id",
                    "destroy_reason",
                    "destroy_user_id",
                    "status_updated_at");

    public static List<Field> quotes() {
        return QUOTE;
    }

    public static List<String> collectionSupplement() {
        return COLLECTION;
    }

    public enum Kind {
        TEXT,
        INTEGER,
        DECIMAL,
        INSTANT
    }

    public record Field(String name, Kind kind) {}
}
