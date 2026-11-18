package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesNativeNumericEncodingTest {
    @TempDir Path directory;

    @Test
    void onlyExactThreeDecimalStringTotalsCanDeclareG16AndOtherLabelsAreRefused() throws Exception {
        final var fixture = fixture(1);
        for (final int column : new int[] {14, 28, 31}) {
            final var field = fixture.field(column);
            assertEquals(
                    CotacoesNativeNumericEncoding.DIRECT,
                    CotacoesNativeNumericEncoding.policy(field));
            field.put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
            CotacoesContentAdapter.verifyCatalog(fixture.catalog);
            assertEquals(
                    CotacoesNativeNumericEncoding.G16, CotacoesNativeNumericEncoding.policy(field));
        }
        for (final int column : new int[] {3, 12, 13, 15, 37}) {
            final var field = fixture.field(column).deepCopy();
            field.put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesNativeNumericEncoding.policy(field));
        }
        for (final String label : List.of("", "UNKNOWN", "DECIMAL16_DIRECT", "%.16G")) {
            final var field = fixture.field(14).deepCopy().put("nativeNumberEncoding", label);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesNativeNumericEncoding.policy(field));
        }
        final var wrongKind = fixture.field(14).deepCopy().put("kind", "numericDecimal");
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacoesNativeNumericEncoding.policy(wrongKind));
        final var noTotal = fixture.field(14).deepCopy().put("total", false);
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacoesNativeNumericEncoding.policy(noTotal));
        final var nullPolicy = fixture.field(14).deepCopy().putNull("nativeNumberEncoding");
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacoesNativeNumericEncoding.policy(nullPolicy));
    }

    @Test
    void sourceEncodingMatchesNativeForRequestedG16VectorsAtEveryEligibleField() throws Exception {
        final var fixture = fixture(1);
        for (final int column : new int[] {14, 28, 31}) {
            final var field =
                    fixture.field(column)
                            .put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
            assertEquals(
                    nativeAtom(field, "0.1", fixture),
                    sourceAtom(field, "0.10000000000000002", fixture));
            assertEquals(
                    nativeAtom(field, "1", fixture),
                    sourceAtom(field, "1.0000000000000002", fixture));
        }
    }

    @Test
    void genuineRemainingDifferenceAndNativeExtraPrecisionAreNeverNormalizedAway()
            throws Exception {
        final var fixture = fixture(1);
        final var field =
                fixture.field(31).put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
        assertNotEquals(
                nativeAtom(field, "1", fixture), sourceAtom(field, "1.000000000000002", fixture));
        assertNotEquals(
                nativeAtom(field, "1.0000000000000002", fixture), sourceAtom(field, "1", fixture));
        assertNotEquals(
                nativeAtom(field, "0.10000000000000002", fixture),
                sourceAtom(field, "0.10000000000000002", fixture));
        assertEquals(
                "BINARY64:" + ExactBinary64.encode("1.0000000000000002").bits(),
                nativeAtom(field, "1.0000000000000002", fixture).carrier());
    }

    @Test
    void signedZeroIsPreservedAndNegativeValuesUseSameExactPipeline() {
        assertEquals("8000000000000000", CotacoesNativeNumericEncoding.g16("-0").bits());
        assertEquals("0000000000000000", CotacoesNativeNumericEncoding.g16("0").bits());
        assertEquals("8000000000000000", CotacoesNativeNumericEncoding.g16("-0.000e+2").bits());
        assertEquals(
                ExactBinary64.encode("-0.1").bits(),
                CotacoesNativeNumericEncoding.g16("-0.10000000000000002").bits());
    }

    @Test
    void exactDyadicHalfTiesRoundToEvenAtSixteenSignificantDigits() {
        assertEquals(
                ExactBinary64.encode("1000000000000000").bits(),
                CotacoesNativeNumericEncoding.g16("1000000000000000.5").bits());
        assertEquals(
                ExactBinary64.encode("1000000000000002").bits(),
                CotacoesNativeNumericEncoding.g16("1000000000000001.5").bits());
        final var encoding = ExactBinary64.encode("1000000000000000.5");
        final var exact =
                new BigDecimal(encoding.units().multiply(BigInteger.valueOf(5).pow(1074)), 1074);
        assertEquals(0, exact.compareTo(new BigDecimal("1000000000000000.5")));
    }

    @Test
    void decimalLiteralHalfTieDiffersFromRoundingItsExactBinary64Value() {
        final String literal = "1.0000000000000705";
        final var original = ExactBinary64.encode(literal);
        final var exact =
                new BigDecimal(original.units().multiply(BigInteger.valueOf(5).pow(1074)), 1074);
        assertTrue(exact.compareTo(new BigDecimal(literal)) > 0);
        final var directDecimal =
                new BigDecimal(literal).round(new MathContext(16, RoundingMode.HALF_EVEN));
        final var converted = CotacoesNativeNumericEncoding.g16(literal);
        assertEquals(ExactBinary64.encode("1.000000000000071").bits(), converted.bits());
        assertNotEquals(ExactBinary64.encode(directDecimal.toString()).bits(), converted.bits());
    }

    @Test
    void activatingPolicyChangesPresentationOnlyAndReportsExplicitEncodingPerColumn()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("qoe_qes_invoices_value", "0.10000000000000002");
        final var direct = fixture.compare();
        assertFalse(direct.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(14).put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
        final var scoped = fixture.compare();
        assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                direct.path("originalValueProjection"), scoped.path("originalValueProjection"));
        assertEquals(
                direct.path("fullValueProjection").path("ui"),
                scoped.path("fullValueProjection").path("ui"));
        assertEquals(
                CotacoesNativeNumericEncoding.G16,
                scoped.path("columnDiagnostics").get(13).path("nativeNumberEncoding").asText());
        for (int index = 0; index < 37; index++) {
            if (index != 13) {
                assertEquals(
                        CotacoesNativeNumericEncoding.DIRECT,
                        scoped.path("columnDiagnostics")
                                .get(index)
                                .path("nativeNumberEncoding")
                                .asText());
            }
        }
    }

    @Test
    void absentPolicyOnOtherDecimalFieldRetainsDirectDifferenceAndIdCannotOptIn() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("qoe_qes_other_fees", "0.10000000000000002");
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(36).booleanValue());
        fixture.field(37).put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(37).remove("nativeNumberEncoding");
        fixture.field(3).put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void totalsSumEachEncodedCellExactlyWithoutRoundingAggregate() throws Exception {
        final var fixture = fixture(2);
        fixture.field(31).put("nativeNumberEncoding", CotacoesNativeNumericEncoding.G16);
        fixture.records.get(0).put("qoe_qes_fit_fdt_subtotal", "1.0000000000000002");
        fixture.records.get(1).put("qoe_qes_fit_fdt_subtotal", "0.0000000000000002");
        fixture.nativeRows.get(0).put(31, CotacoesContentFixture.number("1"));
        fixture.nativeRows.get(1).put(31, CotacoesContentFixture.number("0.0000000000000002"));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        final var expected =
                ExactBinary64.encode("1")
                        .units()
                        .add(ExactBinary64.encode("0.0000000000000002").units());
        final var actual =
                new BigInteger(
                        result.path("fullValueProjection")
                                .path("api")
                                .path("totals")
                                .get(5)
                                .path("sumOfEncodedCellUnits2PowMinus1074")
                                .asText());
        assertEquals(expected, actual);
        assertNotEquals(CotacoesNativeNumericEncoding.g16("1.0000000000000004").units(), actual);
    }

    @Test
    void largeCoefficientExponentDomainReencodesRatioAndSourceLimitsRemain() {
        final String literal = "123456789012345678901234567890123456789012345678901234567890e128";
        final var positive = CotacoesNativeNumericEncoding.g16(literal);
        final var negative = CotacoesNativeNumericEncoding.g16("-" + literal);
        assertEquals(positive.units().negate(), negative.units());
        assertTrue(positive.units().compareTo(ExactBinary64.encode("1e128").units()) > 0);
        assertEquals(
                CotacoesNativeNumericEncoding.g16("1e-128").units().negate(),
                CotacoesNativeNumericEncoding.g16("-1e-128").units());
        for (final String invalid : List.of("+1", "01", "1e129", "1".repeat(129), "NaN")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesNativeNumericEncoding.g16(invalid));
        }
    }

    private CotacoesContentFixture fixture(final int rows) throws Exception {
        return new CotacoesContentFixture(directory, rows);
    }

    private static ExactExportTable.Atom sourceAtom(
            final JsonNode field, final String literal, final CotacoesContentFixture fixture) {
        return CotacoesContentAdapter.sourceValue(
                field,
                new CotacoesContentAdapter.Raw(TextNode.valueOf(literal), null),
                fixture.catalog);
    }

    private static ExactExportTable.Atom nativeAtom(
            final JsonNode field, final String literal, final CotacoesContentFixture fixture) {
        return CotacoesContentAdapter.nativeValue(
                field,
                new ExactExportTable.Atom("NUMBER", literal, CotacoesContentAdapter.NUMBER_CARRIER),
                fixture.catalog);
    }
}
