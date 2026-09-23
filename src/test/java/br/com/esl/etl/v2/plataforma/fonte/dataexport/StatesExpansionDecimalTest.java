package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatesExpansionDecimalTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.00000000", "1.00000000", "1.000000000"})
    void artifactPreflightAndConsumptionPreserveTheSameDeclaredScale(final String literal)
            throws Exception {
        final String document = "{\"amount\":" + literal + "}";
        final var preflight =
                br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.parse(
                        document.getBytes(java.nio.charset.StandardCharsets.UTF_8), 65536);
        final var consumed = DataExportStrictJsonParser.readTree(document);
        assertEquals(
                consumed.path("amount").decimalValue(), preflight.path("amount").decimalValue());
        assertEquals(
                ExpansionFieldParser.decimal(consumed, "amount"),
                ExpansionFieldParser.decimal(preflight, "amount"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "12345678.12345678",
                "99999999999999999999.99999999",
                "0.00000001",
                "-0.00000001",
                "0.00000000"
            })
    void numericAndTextualAmountsReachTheSameExactDecimal(final String literal) throws Exception {
        final var row =
                DataExportStrictJsonParser.readTree(
                        "{\"numeric\":" + literal + ",\"textual\":\"" + literal + "\"}");
        for (final String field : java.util.List.of("numeric", "textual")) {
            final var value = ExpansionFieldParser.decimal(row, field);
            assertTrue(value.valid(), literal + ":" + field);
            assertEquals(new BigDecimal(literal).setScale(8), value.value());
            assertEquals(
                    field.equals("numeric")
                            ? ExpansionValue.Wire.NUMBER
                            : ExpansionValue.Wire.STRING,
                    value.wire());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0000000000000001", "123.123456789", "999999999999999999999.0"})
    void precisionAndOverflowRemainRejected(final String literal) throws Exception {
        final var row = DataExportStrictJsonParser.readTree("{\"amount\":" + literal + "}");
        assertFalse(ExpansionFieldParser.decimal(row, "amount").valid());
    }
}
