package br.com.esl.etl.v2.modulos.contasapagar.aplicacao;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public final class ContaPagarDataExportMapper {
    private static final int HISTORICAL_ALLOCATION_SCALE = 14;
    private static final int MAX_ALLOCATION_PRECISION = 38;
    private static final Pattern ALLOCATION_DECIMAL =
            Pattern.compile("-?(?:0|[1-9][0-9]{0,19})(?:\\.[0-9]+)?");

    public ContaPagarObservation map(final int occurrence, final JsonNode envelope) {
        return map(occurrence, envelope, HISTORICAL_ALLOCATION_SCALE);
    }

    /** Production snapshots retain the original allocation alongside its exact decimal value. */
    public ContaPagarObservation mapSnapshot(final JsonNode envelope) {
        return map(1, envelope, MAX_ALLOCATION_PRECISION);
    }

    private ContaPagarObservation map(
            final int occurrence, final JsonNode envelope, final int maxAllocationScale) {
        if (occurrence < 1 || occurrence > 100) {
            throw new IllegalArgumentException("EXP_OCCURRENCE_BOUND");
        }
        final var dto = new ContaPagarDataExportDto(envelope.get("data"));
        return new ContaPagarObservation(
                ExpansionFieldParser.text(dto.data(), "ant_aln_name"),
                ExpansionFieldParser.text(dto.data(), "ant_ces_acr_name"),
                parseAntCesValue(dto.data(), maxAllocationScale),
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

    private static ExpansionValue<BigDecimal> parseAntCesValue(
            final JsonNode row, final int maxScale) {
        final var original = ExpansionFieldParser.decimal(row, "ant_ces_value");
        if (original.valid()) {
            return original;
        }
        final JsonNode node = row.get("ant_ces_value");
        if (!node.isTextual() && !node.isNumber()) {
            return original;
        }
        try {
            if (node.isTextual() && !ALLOCATION_DECIMAL.matcher(node.textValue()).matches()) {
                return original;
            }
            final BigDecimal exact =
                    node.isNumber() ? node.decimalValue() : new BigDecimal(node.textValue());
            if (exact.scale() <= 8
                    || exact.scale() > maxScale
                    || exact.precision() > MAX_ALLOCATION_PRECISION
                    || (long) exact.precision() - exact.scale() > 20) {
                return original;
            }
            final BigDecimal value =
                    exact.setScale(
                            Math.max(HISTORICAL_ALLOCATION_SCALE, exact.scale()),
                            RoundingMode.UNNECESSARY);
            return new ExpansionValue<>(
                    original.presence(), original.wire(), original.raw(), value, null);
        } catch (final IllegalArgumentException | ArithmeticException invalid) {
            return original;
        }
    }
}
