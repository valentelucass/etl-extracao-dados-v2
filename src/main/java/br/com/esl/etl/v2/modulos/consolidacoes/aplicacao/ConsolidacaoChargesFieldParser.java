package br.com.esl.etl.v2.modulos.consolidacoes.aplicacao;

import br.com.esl.etl.v2.modulos.consolidacoes.domain.ConsolidacaoChargeDetails;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Dynamic object, exact decimal amounts, explicit nulls and source key order. */
public final class ConsolidacaoChargesFieldParser {
    private ConsolidacaoChargesFieldParser() {}

    public static ExpansionValue<ConsolidacaoChargeDetails> parse(final JsonNode row) {
        return MappedReportFields.parse(
                row,
                "charged_value_details",
                node -> {
                    if (!node.isObject()) {
                        throw new IllegalArgumentException("CON_CHARGES_OBJECT");
                    }
                    final var amounts = new LinkedHashMap<String, BigDecimal>();
                    node.fields()
                            .forEachRemaining(
                                    entry -> {
                                        final var value = entry.getValue();
                                        if (!value.isNumber() && !value.isNull()) {
                                            throw new IllegalArgumentException("CON_CHARGE_AMOUNT");
                                        }
                                        amounts.put(
                                                entry.getKey(),
                                                value.isNull() ? null : value.decimalValue());
                                    });
                    return new ConsolidacaoChargeDetails(amounts);
                });
    }
}
