package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Rational nearest-even IEEE 754 encoding. No floating arithmetic or equality tolerance. */
final class ExactBinary64 {
    private ExactBinary64() {}

    record Ratio(BigInteger numerator, BigInteger denominator) {}

    record Encoding(String bits, BigInteger units, Ratio original) {}

    /** Same unreduced integer ratio, lexeme128 and exponent128 domain as the frozen codec. */
    static Ratio ratio(final String lexeme) {
        require(
                lexeme != null && !lexeme.isEmpty() && lexeme.length() <= 128,
                "PRECISION_EXACT_NUMERIC_LEXEME128");
        final var match =
                java.util.regex.Pattern.compile(
                                "(-?)(0|[1-9][0-9]*)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?")
                        .matcher(lexeme);
        require(match.matches(), "PRECISION_EXACT_BASE10_NUMBER");
        final String fraction = match.group(3) == null ? "" : match.group(3);
        final String exponentText = match.group(4) == null ? "0" : match.group(4);
        require(
                exponentText.replaceFirst("^[+-]", "").length() <= 4,
                "PRECISION_DECIMAL_EXPONENT128");
        final int exponent = Integer.parseInt(exponentText);
        require(Math.abs(exponent) <= 128, "PRECISION_DECIMAL_EXPONENT128");
        final int shift = exponent - fraction.length();
        var numerator =
                new BigInteger(match.group(2) + fraction)
                        .multiply(BigInteger.TEN.pow(Math.max(0, shift)));
        if (!match.group(1).isEmpty()) {
            numerator = numerator.negate();
        }
        return new Ratio(numerator, BigInteger.TEN.pow(Math.max(0, -shift)));
    }

    static String positiveRatioBits(final BigInteger numerator, final BigInteger denominator) {
        require(
                numerator != null
                        && denominator != null
                        && numerator.signum() > 0
                        && denominator.signum() > 0
                        && numerator.bitLength() <= 900
                        && denominator.bitLength() <= 900,
                "ROOT_BINARY64_BOUNDED_POSITIVE_INTEGER_RATIO");
        int exponent = numerator.bitLength() - denominator.bitLength();
        if ((exponent >= 0
                        ? numerator.compareTo(denominator.shiftLeft(exponent))
                        : numerator.shiftLeft(-exponent).compareTo(denominator))
                < 0) {
            exponent--;
        }
        final int shift = 52 - exponent;
        final var a = shift >= 0 ? numerator.shiftLeft(shift) : numerator;
        final var b = shift >= 0 ? denominator : denominator.shiftLeft(-shift);
        final var divided = a.divideAndRemainder(b);
        var significand = divided[0];
        final int half = divided[1].shiftLeft(1).compareTo(b);
        if (half > 0 || half == 0 && significand.testBit(0)) {
            significand = significand.add(BigInteger.ONE);
        }
        if (significand.equals(BigInteger.ONE.shiftLeft(53))) {
            significand = significand.shiftRight(1);
            exponent++;
        }
        require(
                significand.compareTo(BigInteger.ONE.shiftLeft(52)) >= 0
                        && significand.compareTo(BigInteger.ONE.shiftLeft(53)) < 0
                        && exponent >= -1022
                        && exponent <= 1023,
                "ROOT_BINARY64_NORMAL_RANGE");
        return String.format(
                java.util.Locale.ROOT,
                "%016X",
                ((long) (exponent + 1023) << 52)
                        | significand.subtract(BigInteger.ONE.shiftLeft(52)).longValueExact());
    }

    static Encoding encode(final String lexeme) {
        final var ratio = ratio(lexeme);
        final String bits =
                ratioBits(ratio.numerator(), ratio.denominator(), lexeme.startsWith("-"));
        return new Encoding(bits, dyadicUnits(bits), ratio);
    }

    static String ratioBits(
            final BigInteger numerator, final BigInteger denominator, final boolean negativeZero) {
        require(
                numerator != null && denominator != null && denominator.signum() > 0,
                "ENCODING_INTEGER_RATIO_REQUIRED");
        if (numerator.signum() == 0) {
            return negativeZero ? "8000000000000000" : "0000000000000000";
        }
        final var positive = new BigInteger(positiveRatioBits(numerator.abs(), denominator), 16);
        return String.format(
                java.util.Locale.ROOT,
                "%016X",
                numerator.signum() < 0 ? positive.setBit(63) : positive);
    }

    static BigInteger dyadicUnits(final String bits) {
        require(bits != null && bits.matches("[0-9A-F]{16}"), "ENCODING_EXACT16_HEX_BITS");
        final var word = new BigInteger(bits, 16);
        final int exponent = word.shiftRight(52).and(BigInteger.valueOf(2047)).intValue();
        require(exponent != 2047, "ENCODING_NONFINITE_CARRIER_REFUSED");
        final var fraction = word.and(BigInteger.ONE.shiftLeft(52).subtract(BigInteger.ONE));
        final var value = exponent == 0 ? fraction : fraction.setBit(52).shiftLeft(exponent - 1);
        return word.testBit(63) ? value.negate() : value;
    }

    /** One unit is 2^-1074, matching the qualified enc_dyadic_units total model. */
    static BigInteger units(final BigDecimal value) {
        BigInteger numerator = value.unscaledValue().abs();
        BigInteger denominator = BigInteger.ONE;
        if (value.scale() >= 0) {
            denominator = BigInteger.TEN.pow(value.scale());
        } else {
            numerator = numerator.multiply(BigInteger.TEN.pow(-value.scale()));
        }
        require(
                numerator.bitLength() <= 4096 && denominator.bitLength() <= 4096,
                "EXPORT_EXACT_RATIO_BOUND");
        if (numerator.signum() == 0) {
            return BigInteger.ZERO;
        }
        int exponent = numerator.bitLength() - denominator.bitLength();
        if ((exponent >= 0
                        ? numerator.compareTo(denominator.shiftLeft(exponent))
                        : numerator.shiftLeft(-exponent).compareTo(denominator))
                < 0) {
            exponent--;
        }
        require(exponent <= 1023, "EXPORT_BINARY64_OVERFLOW");
        final int shift = exponent < -1022 ? 1074 : 52 - exponent;
        final BigInteger scaledNumerator = shift >= 0 ? numerator.shiftLeft(shift) : numerator;
        final BigInteger scaledDenominator =
                shift >= 0 ? denominator : denominator.shiftLeft(-shift);
        final var division = scaledNumerator.divideAndRemainder(scaledDenominator);
        BigInteger significand = division[0];
        final int halfway = division[1].shiftLeft(1).compareTo(scaledDenominator);
        if (halfway > 0 || halfway == 0 && significand.testBit(0)) {
            significand = significand.add(BigInteger.ONE);
        }
        final BigInteger units =
                exponent < -1022 ? significand : significand.shiftLeft(exponent + 1022);
        require(units.bitLength() <= 2098, "EXPORT_BINARY64_OVERFLOW");
        require(units.signum() != 0, "EXPORT_BINARY64_UNDERFLOW_PENDING");
        return value.signum() < 0 ? units.negate() : units;
    }
}
