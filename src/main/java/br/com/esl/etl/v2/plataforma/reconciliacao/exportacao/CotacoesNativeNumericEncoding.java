package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Exact observed native serialization model, scoped to three Source fields; never a Native-side
 * normalization.
 */
final class CotacoesNativeNumericEncoding {
    static final String DIRECT = "BINARY64_DIRECT";
    static final String G16 = "COTACOES_BINARY64_G16_NATIVE_SERIALIZATION_V1";
    private static final Map<Integer, String> FIELDS =
            Map.of(
                    14,
                    "qoe_qes_invoices_value",
                    28,
                    "qoe_qes_real_weight",
                    31,
                    "qoe_qes_fit_fdt_subtotal");
    private static final BigInteger FIVE_POW_1074 = BigInteger.valueOf(5).pow(1074);
    private static final MathContext SIGNIFICANT_16 = new MathContext(16, RoundingMode.HALF_EVEN);

    private CotacoesNativeNumericEncoding() {}

    static String policy(final JsonNode field) {
        if (!field.has("nativeNumberEncoding")) {
            return DIRECT;
        }
        final var declared = field.get("nativeNumberEncoding");
        require(
                declared.isTextual()
                        && (DIRECT.equals(declared.textValue())
                                || G16.equals(declared.textValue())),
                "COTACOES_NATIVE_NUMBER_ENCODING_LABEL");
        if (DIRECT.equals(declared.textValue())) {
            return DIRECT;
        }
        final var column = field.path("nativeColumn");
        require(
                column.isIntegralNumber()
                        && column.canConvertToInt()
                        && field.path("sourceField").isTextual()
                        && field.get("sourceField")
                                .textValue()
                                .equals(FIELDS.get(column.intValue()))
                        && "decimalString".equals(field.path("kind").asText())
                        && field.path("total").isBoolean()
                        && field.get("total").booleanValue(),
                "COTACOES_NATIVE_NUMBER_ENCODING_EXACT_FIELD_KIND_TOTAL");
        return G16;
    }

    static ExactBinary64.Encoding source(final JsonNode field, final String literal) {
        return G16.equals(policy(field)) ? g16(literal) : ExactBinary64.encode(literal);
    }

    static ExactBinary64.Encoding g16(final String literal) {
        final var original = ExactBinary64.encode(literal);
        if (original.units().signum() == 0) {
            return original; // Preserve both signed zero encodings.
        }
        final BigInteger coefficient = original.units().abs().multiply(FIVE_POW_1074);
        final BigDecimal magnitude = new BigDecimal(coefficient, 1074);
        final BigDecimal exact = original.units().signum() < 0 ? magnitude.negate() : magnitude;
        final BigDecimal rounded = exact.round(SIGNIFICANT_16).stripTrailingZeros();
        BigInteger numerator = rounded.unscaledValue();
        BigInteger denominator = BigInteger.ONE;
        if (rounded.scale() < 0) {
            numerator = numerator.multiply(BigInteger.TEN.pow(-rounded.scale()));
        } else {
            denominator = BigInteger.TEN.pow(rounded.scale());
        }
        // Reencode the exact rounded ratio: avoid introducing a second lexical exponent/length
        // domain.
        final String bits = ExactBinary64.ratioBits(numerator, denominator, false);
        return new ExactBinary64.Encoding(
                bits,
                ExactBinary64.dyadicUnits(bits),
                new ExactBinary64.Ratio(numerator, denominator));
    }
}
