package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class QualifiedColetasModelTest {
    @Test
    void signedZeroHasDistinctBitsWhileItsExactRationalTotalRemainsZero() {
        final var positive = ExactBinary64.encode("0");
        final var negative = ExactBinary64.encode("-0.000");
        assertEquals("0000000000000000", positive.bits());
        assertEquals("8000000000000000", negative.bits());
        assertEquals(BigInteger.ZERO, negative.units());
        assertEquals(positive.units(), negative.units());
    }

    @Test
    void qualifiedDecimalParserKeepsItsLexemeAndExponentDomain() {
        assertThrows(IllegalArgumentException.class, () -> ExactBinary64.encode("1e129"));
        assertThrows(IllegalArgumentException.class, () -> ExactBinary64.encode(" 1"));
        assertThrows(IllegalArgumentException.class, () -> ExactBinary64.encode("1".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> ExactBinary64.encode("+1"));
        assertThrows(IllegalArgumentException.class, () -> ExactBinary64.encode("01"));
        assertEquals(BigInteger.valueOf(12300), ExactBinary64.ratio("1.2300e2").numerator());
        assertEquals(BigInteger.valueOf(100), ExactBinary64.ratio("1.2300e2").denominator());
    }

    @Test
    void completePortReceivesOnlyInputsAndRejectsUnknownOperations() throws Exception {
        final var input = ExportValidationSupport.JSON.createObjectNode().put("lexeme", "0.1");
        final var result = new Original129JavaPort().evaluate("encoding.enc_decimal_bits", input);
        assertEquals("RETURNED", result.path("status").asText());
        assertEquals("3FB999999999999A", result.path("value").path("$tuple").get(0).asText());
        assertThrows(
                IllegalStateException.class,
                () -> new Original129JavaPort().evaluate("unknown.operation", input));
    }

    @Test
    void invalidRuntimeFloatIsRefusedWithTheOriginalDomainReason() throws Exception {
        final var input = ExportValidationSupport.JSON.createObjectNode();
        input.putObject("lexeme").put("$invalidRuntimeFloat", "1.25");
        final var result = new Original129JavaPort().evaluate("precision._ratio", input);
        assertEquals("REFUSED", result.path("status").asText());
        assertEquals("PRECISION_EXACT_NUMERIC_LEXEME128", result.path("reason").asText());
    }

    @Test
    void civilTimestampAndFractionalSerialUseTheSameProductionFunctions() {
        assertEquals("2036-03-20", QualifiedColetasModel.sourceCivil("2036-03-20"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceCivil("2036-02-30"));
        assertEquals(
                "2036-03-20T10:11:12",
                QualifiedColetasModel.sourceTimestamp("2036-03-20T10:11:12.123456789-03:00"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceTimestamp("2036-03-20T13:11:12Z"));
        assertEquals(
                "2026-10-19T00:00:14", QualifiedColetasModel.nativeTimestamp("46314.00015625"));
        assertEquals(
                "2026-10-19T00:00:13",
                QualifiedColetasModel.nativeTimestamp("46314.00015624999999999"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.nativeTimestamp("2958465.99999999999999999"));
    }

    @Test
    void exactDateAndTimestampFormatsCannotBeSubstitutedForEachOther() {
        final String date = "XLSX:1900:DD/MM/YYYY:apply=1:base=0:fmtId=165";
        final String timestamp = "XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164";
        assertTrue(QualifiedColetasModel.carrierRole("date", date));
        assertFalse(QualifiedColetasModel.carrierRole("date", timestamp));
        assertFalse(QualifiedColetasModel.carrierRole("date", date.replace("165", "164")));
        assertFalse(
                QualifiedColetasModel.carrierRole(
                        "timestamp", timestamp.replace("apply=1", "apply=0")));
        final String general = "XLSX:1900:builtin:0:apply=null:base=0:fmtId=0";
        assertTrue(QualifiedColetasModel.carrierRole("decimal", general));
        assertFalse(QualifiedColetasModel.carrierRole("date", general));
        assertFalse(QualifiedColetasModel.carrierRole("timestamp", general));
        assertFalse(
                QualifiedColetasModel.carrierRole(
                        "decimal", general.replace("fmtId=0", "fmtId=164")));
    }
}
