package br.com.esl.etl.v2.modulos.contasapagar.aplicacao;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;

public final class ContaPagarDataExportMapper {
    public ContaPagarObservation map(final int occurrence, final JsonNode envelope) {
        if (occurrence < 1 || occurrence > 100) {
            throw new IllegalArgumentException("EXP_OCCURRENCE_BOUND");
        }
        final var dto = new ContaPagarDataExportDto(envelope.get("data"));
        return new ContaPagarObservation(
                ExpansionFieldParser.text(dto.data(), "ant_aln_name"),
                ExpansionFieldParser.text(dto.data(), "ant_ces_acr_name"),
                ExpansionFieldParser.decimal(dto.data(), "ant_ces_value"),
                ExpansionFieldParser.text(dto.data(), "ant_crn_psn_nickname"),
                ExpansionFieldParser.date(dto.data(), "ant_ils_atn_liquidation_date"),
                ExpansionFieldParser.bool(dto.data(), "ant_ils_atn_reconciled"),
                ExpansionFieldParser.date(dto.data(), "ant_ils_atn_transaction_date"),
                ExpansionFieldParser.text(dto.data(), "ant_ils_comments"),
                ExpansionFieldParser.text(dto.data(), "ant_ils_expense_description"),
                ExpansionFieldParser.text(dto.data(), "ant_ils_pas_ant_classification"),
                ExpansionFieldParser.text(dto.data(), "ant_ils_pas_ant_name"),
                ExpansionFieldParser.decimal(dto.data(), "ant_ils_pas_value"),
                ExpansionFieldParser.integer(dto.data(), "ant_ils_sequence_code"),
                ExpansionFieldParser.text(dto.data(), "ant_rir_name"),
                ExpansionFieldParser.text(dto.data(), "ant_uer_name"),
                ExpansionFieldParser.text(dto.data(), "comments"),
                ExpansionFieldParser.integer(dto.data(), "competence_month"),
                ExpansionFieldParser.integer(dto.data(), "competence_year"),
                ExpansionFieldParser.instant(dto.data(), "created_at"),
                ExpansionFieldParser.decimal(dto.data(), "discount_value"),
                ExpansionFieldParser.text(dto.data(), "document"),
                ExpansionFieldParser.decimal(dto.data(), "interest_value"),
                ExpansionFieldParser.date(dto.data(), "issue_date"),
                ExpansionFieldParser.bool(dto.data(), "paid"),
                ExpansionFieldParser.decimal(dto.data(), "paid_value"),
                ExpansionFieldParser.text(dto.data(), "type"),
                ExpansionFieldParser.decimal(dto.data(), "value"),
                ExpansionFieldParser.decimal(dto.data(), "value_to_pay"));
    }
}
