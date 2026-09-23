package br.com.esl.etl.v2.modulos.sinistros.aplicacao;

import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;

public final class SinistroDataExportMapper {
    public SinistroObservation map(final int occurrence, final JsonNode envelope) {
        if (occurrence < 1 || occurrence > 100) {
            throw new IllegalArgumentException("EXP_OCCURRENCE_BOUND");
        }
        final var dto = new SinistroDataExportDto(envelope.get("data"));
        return new SinistroObservation(
                ExpansionFieldParser.text(dto.data(), "bo_number"),
                ExpansionFieldParser.text(dto.data(), "brokers_case_number"),
                ExpansionFieldParser.text(dto.data(), "customer_communication_contact_name"),
                ExpansionFieldParser.date(dto.data(), "customer_communication_date"),
                ExpansionFieldParser.text(dto.data(), "customer_communication_time"),
                ExpansionFieldParser.decimal(dto.data(), "customer_credit_entries_subtotal"),
                ExpansionFieldParser.decimal(dto.data(), "customer_debits_subtotal"),
                ExpansionFieldParser.date(dto.data(), "expected_solution_date"),
                ExpansionFieldParser.date(dto.data(), "finished_at_date"),
                ExpansionFieldParser.time(dto.data(), "finished_at_time"),
                ExpansionFieldParser.text(dto.data(), "finished_commentary"),
                ExpansionFieldParser.text(dto.data(), "icm_crn_psn_nickname"),
                ExpansionFieldParser.text(dto.data(), "icm_dvr_iil_name"),
                ExpansionFieldParser.text(dto.data(), "icm_fer_name"),
                ExpansionFieldParser.integer(dto.data(), "icm_fis_fit_corporation_sequence_number"),
                ExpansionFieldParser.text(dto.data(), "icm_fis_fit_pyr_nickname"),
                ExpansionFieldParser.identifier(dto.data(), "icm_fis_ioe_number"),
                ExpansionFieldParser.text(dto.data(), "icm_ttt_dealing_type"),
                ExpansionFieldParser.integer(dto.data(), "icm_ttt_ore_code"),
                ExpansionFieldParser.text(dto.data(), "icm_ttt_ore_description"),
                ExpansionFieldParser.text(dto.data(), "icm_ttt_solution_type"),
                ExpansionFieldParser.instant(dto.data(), "icm_ttt_treatment_at"),
                ExpansionFieldParser.text(dto.data(), "icm_vie_license_plate"),
                ExpansionFieldParser.text(dto.data(), "informed_by"),
                ExpansionFieldParser.text(dto.data(), "insurance_claim_commentary"),
                ExpansionFieldParser.text(dto.data(), "insurance_claim_location"),
                ExpansionFieldParser.decimal(dto.data(), "insurance_claim_total"),
                ExpansionFieldParser.decimal(dto.data(), "insurer_credits_subtotal"),
                ExpansionFieldParser.text(dto.data(), "internal_description"),
                ExpansionFieldParser.integer(dto.data(), "invoices_count"),
                ExpansionFieldParser.decimal(dto.data(), "invoices_value"),
                ExpansionFieldParser.integer(dto.data(), "invoices_volumes"),
                ExpansionFieldParser.decimal(dto.data(), "invoices_weight"),
                ExpansionFieldParser.text(dto.data(), "list_of_claimed_products"),
                ExpansionFieldParser.date(dto.data(), "occurrence_at_date"),
                ExpansionFieldParser.time(dto.data(), "occurrence_at_time"),
                ExpansionFieldParser.date(dto.data(), "opening_at_date"),
                ExpansionFieldParser.text(dto.data(), "policy_number"),
                ExpansionFieldParser.strings(dto.data(), "rcfdc"),
                ExpansionFieldParser.strings(dto.data(), "rctac"),
                ExpansionFieldParser.strings(dto.data(), "rctrc"),
                ExpansionFieldParser.decimal(dto.data(), "responsible_credits_subtotal"),
                ExpansionFieldParser.decimal(dto.data(), "responsible_debit_entries_subtotal"),
                ExpansionFieldParser.integer(dto.data(), "sequence_code"));
    }
}
