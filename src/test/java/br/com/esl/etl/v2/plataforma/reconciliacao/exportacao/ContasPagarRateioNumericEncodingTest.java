package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored numerical counterexamples, not supplier amounts or policy qualification. */
class ContasPagarRateioNumericEncodingTest {
    private static final String POLICY = "SOURCE_BINARY64_DECIMAL16_HALF_EVEN_BINARY64_V1";
    private static final MathContext SIGNIFICANT_16 = new MathContext(16, RoundingMode.HALF_EVEN);
    @TempDir Path directory;

    private static String bits(final double value) {
        return String.format(Locale.ROOT, "%016X", Double.doubleToRawLongBits(value));
    }

    private static double binary(final String lexeme) {
        return Double.longBitsToDouble(
                Long.parseUnsignedLong(ExactBinary64.encode(lexeme).bits(), 16));
    }

    @Test
    void absentSourcePolicyKeepsDirectBinary64ForAllSevenFinancialFields() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        for (final int column : new int[] {5, 7, 23, 24, 25, 26, 28}) {
            assertEquals(
                    ExactBinary64.encode("1.2345678901234567").bits(),
                    ContasPagarContentAdapter.sourceNumber(f.field(column), "1.2345678901234567")
                            .bits());
        }
    }

    @Test
    void explicitSourceRateioPolicyMatchesNativeDirectAndKeepsEveryOtherAxis() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.records.get(0).put("ant_ces_value", "1.2345678901234567");
        f.nativeRows.get(0).put(7, ContasPagarContentFixture.number("1.234567890123457"));
        assertFalse(f.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        f.field(7).put("sourceNumberEncoding", POLICY);
        final var compared = f.compare();
        assertTrue(compared.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                compared.path("fullValueProjection").path("api"),
                compared.path("fullValueProjection").path("ui"));
        assertNotEquals(
                compared.path("originalValueProjection").path("api"),
                compared.path("originalValueProjection").path("ui"));
        assertEquals("1.2345678901234567", f.records.get(0).path("ant_ces_value").textValue());
        assertEquals(
                "DIRECT_BINARY64_NEAREST_EVEN_V1",
                f.field(7).path("nativeNumberEncoding").textValue());
    }

    @Test
    void sourcePolicyCannotNormalizeNativeOriginalNumbersOrTheirPresentation() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.field(7).put("sourceNumberEncoding", POLICY);
        final String value = "1.2345678901234567";
        final var nativeCell =
                new ExactExportTable.Atom(
                        "NUMBER", value, f.field(7).path("nativeCarrier").textValue());
        final var nativeAtom =
                ContasPagarContentAdapter.nativeValue(f.field(7), nativeCell, f.catalog);
        assertEquals("BINARY64:" + ExactBinary64.encode(value).bits(), nativeAtom.carrier());
        assertNotEquals(
                "BINARY64:" + ContasPagarContentAdapter.sourceNumber(f.field(7), value).bits(),
                nativeAtom.carrier());
    }

    @Test
    void wrongFieldWrongKindAndUnknownSourceEncodingAreRefused() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        for (final int column : new int[] {5, 23, 24, 25, 26, 28, 18}) {
            f.field(column).put("sourceNumberEncoding", POLICY);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
            f.field(column).remove("sourceNumberEncoding");
        }
        f.field(7).put("sourceNumberEncoding", "UNKNOWN");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
        f.field(7).put("sourceNumberEncoding", POLICY).put("kind", "text");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
    }

    @Test
    void exactBinaryDecimalRoundingIsNotBigDecimalValueOfShortestDisplay() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.field(7).put("sourceNumberEncoding", POLICY);
        final String literal = "1.0000000000000715";
        final double original = binary(literal);
        final var exact = new BigDecimal(original).round(SIGNIFICANT_16);
        final var display = BigDecimal.valueOf(original).round(SIGNIFICANT_16);
        assertNotEquals(0, exact.compareTo(display));
        assertNotEquals(bits(exact.doubleValue()), bits(display.doubleValue()));
        assertEquals(
                ExactBinary64.encode("1.000000000000071").bits(),
                ContasPagarContentAdapter.sourceNumber(f.field(7), literal).bits());
        assertEquals(
                bits(exact.doubleValue()),
                ContasPagarContentAdapter.sourceNumber(f.field(7), literal).bits());
    }

    @Test
    void roundingSixteenSignificantDigitsUsesHalfEven() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.field(7).put("sourceNumberEncoding", POLICY);
        assertEquals(
                ExactBinary64.encode("100000000000000.2").bits(),
                ContasPagarContentAdapter.sourceNumber(f.field(7), "100000000000000.25").bits());
        assertEquals(
                ExactBinary64.encode("100000000000000.8").bits(),
                ContasPagarContentAdapter.sourceNumber(f.field(7), "100000000000000.75").bits());
    }

    @Test
    void signsZeroAndOriginalLexicalDomainArePreservedWithoutASecondExponentBound()
            throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.field(7).put("sourceNumberEncoding", POLICY);
        assertEquals(
                "8000000000000000",
                ContasPagarContentAdapter.sourceNumber(f.field(7), "-0.000e+2").bits());
        assertEquals(
                "0000000000000000", ContasPagarContentAdapter.sourceNumber(f.field(7), "0").bits());
        for (final String literal :
                new String[] {"-1.2345678901234567", "9".repeat(115) + "e128", "1e-128"}) {
            assertEquals(
                    bits(new BigDecimal(binary(literal)).round(SIGNIFICANT_16).doubleValue()),
                    ContasPagarContentAdapter.sourceNumber(f.field(7), literal).bits());
        }
    }

    @Test
    void nonfiniteInvalidAndOversizedSourceLexemesRemainRefused() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.field(7).put("sourceNumberEncoding", POLICY);
        for (final String literal :
                new String[] {"NaN", "Infinity", "1e129", "0".repeat(129), " 1"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarContentAdapter.sourceNumber(f.field(7), literal));
        }
    }
}
