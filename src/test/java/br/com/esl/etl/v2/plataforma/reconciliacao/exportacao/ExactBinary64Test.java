package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactBinary64Test {
    @Test
    void knownBinary64CarriersAreEncodedWithIntegerArithmetic() {
        assertEquals(BigInteger.ONE.shiftLeft(1074), ExactBinary64.units(BigDecimal.ONE));
        assertEquals(
                BigInteger.valueOf(3602879701896397L).shiftLeft(1019),
                ExactBinary64.units(new BigDecimal("0.1")));
        assertEquals(
                BigInteger.valueOf(3602879701896397L).shiftLeft(1019).negate(),
                ExactBinary64.units(new BigDecimal("-0.1")));
        assertEquals(BigInteger.ZERO, ExactBinary64.units(BigDecimal.ZERO));
    }

    @Test
    void exactHalfwayCasesUseEvenSignificand() {
        final var halfUlp = BigDecimal.ONE.divide(new BigDecimal(BigInteger.ONE.shiftLeft(53)));
        assertEquals(
                BigInteger.ONE.shiftLeft(1074), ExactBinary64.units(BigDecimal.ONE.add(halfUlp)));
        assertEquals(
                BigInteger.ONE.shiftLeft(1074).add(BigInteger.ONE.shiftLeft(1023)),
                ExactBinary64.units(BigDecimal.ONE.add(halfUlp.multiply(new BigDecimal("3")))));
    }

    @Test
    void smallestSubnormalAndOverflowAreExplicit() {
        final var least = BigDecimal.ONE.divide(new BigDecimal(BigInteger.ONE.shiftLeft(1074)));
        assertEquals(BigInteger.ONE, ExactBinary64.units(least));
        assertThrows(
                IllegalArgumentException.class, () -> ExactBinary64.units(new BigDecimal("1e309")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactBinary64.units(new BigDecimal("1e-1000")));
    }
}
