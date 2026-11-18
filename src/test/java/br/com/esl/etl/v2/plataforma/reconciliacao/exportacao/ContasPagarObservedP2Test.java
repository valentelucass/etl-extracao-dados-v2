package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Synthetic causal coverage for the eight observed wire deltas and one field-specific decimal
 * bound.
 */
class ContasPagarObservedP2Test {
    private static final String P1 =
            "/contracts/dataexport/expansion-8636-observed-wire-20261025-v1.json";
    private static final String P2 =
            "/contracts/dataexport/expansion-8636-observed-wire-20261107-p2.json";
    private static final String EXACT = "123.45678901234567";
    private static final Set<String> STRINGS =
            Set.of("ant_aln_name", "ant_ils_comments", "ant_ils_expense_description");
    private static final Set<String> NULLS =
            Set.of(
                    "ant_ils_pas_ant_classification",
                    "ant_uer_name",
                    "ant_ils_atn_liquidation_date",
                    "ant_ils_atn_transaction_date",
                    "ant_ils_atn_reconciled");
    @TempDir Path directory;

    @Test
    void
            supplementChangesOnlyTheEightObservedFieldTypesAndPreservesHistoricalInfoAndRequiredFields()
                    throws Exception {
        final var oldContract = contract(P1);
        final var current = contract(P2);
        assertEquals(oldContract.required("metadata"), current.required("metadata"));
        assertEquals(28, current.required("fields").size());
        final var changed = new HashSet<String>();
        for (int index = 0; index < 28; index++) {
            final var oldField = (ObjectNode) oldContract.required("fields").get(index).deepCopy();
            final var newField = (ObjectNode) current.required("fields").get(index).deepCopy();
            final String name = newField.required("path").textValue();
            if (!oldField.equals(newField)) {
                changed.add(name);
                assertTrue(STRINGS.contains(name) || NULLS.contains(name));
                assertTrue(newField.required("required").booleanValue());
                assertFalse(newField.required("unknownNonNull").booleanValue());
                final Set<String> expected =
                        STRINGS.contains(name) || !"ant_ils_atn_reconciled".equals(name)
                                ? Set.of("NULL", "STRING")
                                : Set.of("NULL", "BOOLEAN");
                final var actual = new HashSet<String>();
                newField.required("wireTypes").forEach(value -> actual.add(value.textValue()));
                assertEquals(expected, actual);
                oldField.remove(List.of("wireTypes", "unknownNonNull"));
                newField.remove(List.of("wireTypes", "unknownNonNull"));
            }
            assertEquals(oldField, newField);
        }
        final var expected = new HashSet<>(STRINGS);
        expected.addAll(NULLS);
        assertEquals(expected, changed);
        try (var stream = getClass().getResourceAsStream(P1)) {
            assertEquals(
                    "863f46f21d232c8bc8f27e9803061492ed613c020c42c211ffadf93c3aa17277",
                    ExportValidationSupport.sha256(stream.readAllBytes()));
        }
        try (var stream = getClass().getResourceAsStream(P2)) {
            assertEquals(
                    "9aff087869178bdc3729ffa79e6a7a65886b3e66440f7cc1afd3c24fff6e1ba5",
                    ExportValidationSupport.sha256(stream.readAllBytes()));
        }
    }

    @Test
    void newlyObservedStringsAndNullsQualifyWithTheCurrentMapperWithoutChangingRaw()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = observedRow();
            final var raw = source(row);
            final var pin = fixture.pin(raw);
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pin, budget());
            assertTrue(result.required("rowTypesQualified").booleanValue());
            assertTrue(result.required("mapperQualified").booleanValue());
            assertEquals(0, result.required("rejectedRows").intValue());
            assertEquals(
                    ContasPagarIntervalCapture.SHAPE_SHA,
                    result.required("sourceWireContractSha256").textValue());
            assertArrayEquals(raw, Files.readAllBytes(pin.path()));
            assertEquals("  A\u0301 \uD83D\uDE80\n  ", map(row).antIlsComments().raw());
        }
    }

    @Test
    void collectorRetainsRepeatedP2RowsThroughTheRealTerminalArrayAndReportsBothQualifications()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = observedRow();
            final String originalRecord =
                    new String(ContasPagarCaptureFixture.json(row), StandardCharsets.UTF_8);
            fixture.port.pages.add(
                    ("[" + originalRecord + "," + originalRecord + "]")
                            .getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.required("status").textValue());
            assertTrue(result.required("rowTypesQualified").booleanValue());
            assertTrue(result.required("mapperQualified").booleanValue());
            assertTrue(result.required("terminalEmptyPageObserved").booleanValue());
            assertEquals(2, result.required("rows").intValue());
            final var pin = ExportValidationSupport.Pin.from(result.required("dataPin"));
            final String retainedRecord =
                    "{\"entity\":\"CONTAS_A_PAGAR\",\"templateId\":8636,\"record\":"
                            + originalRecord
                            + "}\n";
            assertArrayEquals(
                    retainedRecord.repeat(2).getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(pin.path()));
        }
    }

    @Test
    void anUnobservedObjectStillFailsWireAndMapperQualification() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = observedRow();
            row.putObject("ant_aln_name").put("authored", true);
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), fixture.pin(source(row)), budget());
            assertFalse(result.required("success").booleanValue());
            assertEquals(1, result.required("rowTypeRejectedRows").intValue());
            assertEquals(1, result.required("mapperRejectedRows").intValue());
        }
    }

    @Test
    void fourteenNonzeroFractionDigitsAndSeventeenPrecisionDigitsSurviveExactly() throws Exception {
        final var observation = map(observedRow());
        assertTrue(observation.valid());
        assertEquals(new BigDecimal(EXACT), observation.antCesValue().value());
        assertEquals(14, observation.antCesValue().value().scale());
        assertEquals(EXACT, observation.antCesValue().raw());
        final var encoded =
                ExportValidationSupport.JSON.valueToTree(observation).required("antCesValue");
        assertEquals(new BigDecimal(EXACT), encoded.required("value").decimalValue());
        assertEquals(EXACT, encoded.required("raw").textValue());
    }

    @Test
    void supportedNumericWireUsesExactBigDecimalWhileTheWireContractRemainsSeparate()
            throws Exception {
        final var row = observedRow().put("ant_ces_value", new BigDecimal(EXACT));
        final var value = map(row).antCesValue();
        assertTrue(value.valid());
        assertEquals(ExpansionValue.Wire.NUMBER, value.wire());
        assertEquals(new BigDecimal(EXACT), value.value());
        assertEquals(EXACT, value.raw());
    }

    @Test
    void oldEightScaleValuesStillUseTheUnchangedGenericParserAndTypedEncoding() throws Exception {
        final var row = observedRow().put("ant_ces_value", "12.3400");
        assertEquals(ExpansionFieldParser.decimal(row, "ant_ces_value"), map(row).antCesValue());
        assertEquals(8, map(row).antCesValue().value().scale());
        assertEquals("12.3400", map(row).antCesValue().raw());
    }

    @Test
    void theOtherSixDecimalFieldsAndTheGenericParserKeepScaleEight() throws Exception {
        for (final String name :
                List.of(
                        "ant_ils_pas_value",
                        "discount_value",
                        "interest_value",
                        "paid_value",
                        "value",
                        "value_to_pay")) {
            final var row = observedRow().put(name, EXACT);
            assertFalse(map(row).valid());
            assertFalse(ExpansionFieldParser.decimal(row, name).valid());
            assertEquals(EXACT, ExpansionFieldParser.decimal(row, name).raw());
        }
        assertFalse(ExpansionFieldParser.decimal(observedRow(), "ant_ces_value").valid());
    }

    @Test
    void fifteenFractionDigitsOrTooManyIntegerDigitsRemainQuarantinedWithoutRounding()
            throws Exception {
        for (final String raw :
                List.of(
                        "123.456789012345678",
                        "123456789012345678901.12345678901234",
                        " 123.45678901234567",
                        "123,45678901234567",
                        "1e-14",
                        "")) {
            final var value = map(observedRow().put("ant_ces_value", raw)).antCesValue();
            assertFalse(value.valid());
            assertEquals(raw, value.raw());
            assertEquals("INVALID_ANT_CES_VALUE", value.issue());
        }
    }

    @Test
    void missingNullAndWrongTypeKeepTheirExistingPresenceAndQuarantineSemantics() throws Exception {
        final var row = observedRow();
        row.remove("ant_ces_value");
        assertEquals(ExpansionValue.Presence.ABSENT, map(row).antCesValue().presence());
        row.putNull("ant_ces_value");
        assertEquals(ExpansionValue.Presence.NULL, map(row).antCesValue().presence());
        row.put("ant_ces_value", true);
        assertFalse(map(row).antCesValue().valid());
        assertEquals(ExpansionValue.Wire.BOOLEAN, map(row).antCesValue().wire());
        assertEquals("true", map(row).antCesValue().raw());
    }

    @Test
    void raw4000AndText1024BoundsAreNotExpanded() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> map(observedRow().put("ant_ces_value", "1".repeat(4001))));
        assertFalse(
                map(observedRow().put("ant_ils_comments", "x".repeat(1025)))
                        .antIlsComments()
                        .valid());
    }

    @Test
    void timestampFractionsArePreservedByTheMapperWithoutClaimingAnyNativePresentation()
            throws Exception {
        final String raw = "2026-10-01T12:34:56.123-03:00";
        final var value = map(observedRow().put("created_at", raw)).createdAt();
        assertEquals(raw, value.raw());
        assertEquals(Instant.parse("2026-10-01T15:34:56.123Z"), value.value());
    }

    private static ObjectNode observedRow() throws Exception {
        final var row = ContasPagarCaptureFixture.row(7).put("ant_ces_value", EXACT);
        STRINGS.forEach(name -> row.put(name, "  A\u0301 \uD83D\uDE80\n  "));
        NULLS.forEach(row::putNull);
        return row;
    }

    private static ContaPagarObservation map(final ObjectNode row) {
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        return new ContaPagarDataExportMapper().map(1, envelope);
    }

    private static byte[] source(final JsonNode... rows) throws Exception {
        final var result = new StringBuilder();
        for (final var row : rows) {
            result.append("{\"entity\":\"CONTAS_A_PAGAR\",\"templateId\":8636,\"record\":")
                    .append(ExportValidationSupport.JSON.writeValueAsString(row))
                    .append("}\n");
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode contract(final String resource) throws Exception {
        try (var stream = ContasPagarObservedP2Test.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("AUTHORED_CONTRACT_RESOURCE_REQUIRED");
            }
            return ExportValidationSupport.JSON.readTree(stream);
        }
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}
