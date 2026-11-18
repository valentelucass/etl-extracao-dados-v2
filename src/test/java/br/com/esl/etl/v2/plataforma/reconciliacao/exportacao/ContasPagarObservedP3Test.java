package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Synthetic coverage for the sole P3 NULL observation; P2 and all raw occurrences remain literal.
 */
class ContasPagarObservedP3Test {
    private static final String FIELD = "ant_ces_acr_name";
    private static final String P2 =
            "/contracts/dataexport/expansion-8636-observed-wire-20261107-p2.json";
    private static final String P3 =
            "/contracts/dataexport/expansion-8636-observed-wire-20261107-p3.json";
    private static final String P2_SHA =
            "9aff087869178bdc3729ffa79e6a7a65886b3e66440f7cc1afd3c24fff6e1ba5";
    @TempDir Path directory;

    @Test
    void supplementChangesOnlyObservedNullForAntCesAcrNameAndKeepsRequiredFieldAndMetadata()
            throws Exception {
        final var oldContract = contract(P2);
        final var current = contract(P3);
        assertEquals(oldContract.required("metadata"), current.required("metadata"));
        assertEquals(28, current.required("fields").size());
        final var changed = new HashSet<String>();
        for (int index = 0; index < 28; index++) {
            final var oldField = (ObjectNode) oldContract.required("fields").get(index).deepCopy();
            final var newField = (ObjectNode) current.required("fields").get(index).deepCopy();
            if (!oldField.equals(newField)) {
                final String name = newField.required("path").textValue();
                changed.add(name);
                assertEquals(FIELD, name);
                assertEquals(Set.of("STRING"), types(oldField));
                assertEquals(Set.of("NULL", "STRING"), types(newField));
                assertTrue(newField.required("required").booleanValue());
                assertFalse(newField.required("unknownNonNull").booleanValue());
                oldField.remove("wireTypes");
                newField.remove("wireTypes");
            }
            assertEquals(oldField, newField);
        }
        assertEquals(Set.of(FIELD), changed);
    }

    @Test
    void archivedP2BytesStayPinnedWhileBothCurrentReadersUseP3() throws Exception {
        try (var stream = getClass().getResourceAsStream(P2)) {
            assertEquals(P2_SHA, ExportValidationSupport.sha256(stream.readAllBytes()));
        }
        try (var stream = getClass().getResourceAsStream(P3)) {
            assertEquals(
                    ContasPagarIntervalCapture.SHAPE_SHA,
                    ExportValidationSupport.sha256(stream.readAllBytes()));
        }
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(7).putNull(FIELD);
            final var qualified =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), fixture.pin(source(row)), budget());
            assertEquals(
                    ContasPagarIntervalCapture.SHAPE_SHA,
                    qualified.required("sourceWireContractSha256").textValue());
        }
    }

    @Test
    void nullQualifiesWithoutReplacingItsWirePresenceOrOriginalRawBytes() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(7).putNull(FIELD);
            final byte[] raw = source(row);
            final var pin = fixture.pin(raw);
            final var qualified =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pin, budget());
            assertTrue(qualified.required("rowTypesQualified").booleanValue());
            assertTrue(qualified.required("mapperQualified").booleanValue());
            assertEquals(0, qualified.required("rejectedRows").intValue());
            final var value = map(row);
            assertEquals(ExpansionValue.Presence.NULL, value.presence());
            assertEquals(ExpansionValue.Wire.NULL, value.wire());
            assertNull(value.raw());
            assertNull(value.value());
            assertArrayEquals(raw, Files.readAllBytes(pin.path()));
        }
    }

    @Test
    void collectorRetainsTwoNullOccurrencesLiterallyAndStillRequiresActualTerminalArray()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(7).putNull(FIELD);
            final String record =
                    new String(ContasPagarCaptureFixture.json(row), StandardCharsets.UTF_8);
            fixture.port.pages.add(
                    ("[" + record + "," + record + "]").getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var captured = fixture.capture(2, 1, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", captured.required("status").textValue());
            assertTrue(captured.required("terminalEmptyPageObserved").booleanValue());
            assertTrue(captured.required("rowTypesQualified").booleanValue());
            assertTrue(captured.required("mapperQualified").booleanValue());
            assertEquals(2, captured.required("rows").intValue());
            final var pin = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final String envelope =
                    "{\"entity\":\"CONTAS_A_PAGAR\",\"templateId\":8636,\"record\":"
                            + record
                            + "}\n";
            assertArrayEquals(
                    envelope.repeat(2).getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(pin.path()));
        }
    }

    @Test
    void unobservedBooleanIntegerAndObjectStillRefuseWithoutCoercionOrRawReplacement()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var objectRow = ContasPagarCaptureFixture.row(7);
            objectRow.putObject(FIELD).put("authored", true);
            for (final var row :
                    List.of(
                            ContasPagarCaptureFixture.row(7).put(FIELD, true),
                            ContasPagarCaptureFixture.row(7).put(FIELD, 7),
                            objectRow)) {
                final byte[] raw = source(row);
                final var pin = fixture.pin(raw);
                final var qualified =
                        ContasPagarRawMapperQualifier.qualify(
                                fixture.pin(fixture.request), pin, budget());
                assertFalse(qualified.required("success").booleanValue());
                assertEquals(1, qualified.required("rowTypeRejectedRows").intValue());
                assertEquals(1, qualified.required("mapperRejectedRows").intValue());
                assertArrayEquals(raw, Files.readAllBytes(pin.path()));
            }
        }
    }

    @Test
    void originalStringWhitespaceAndUnicodeRemainUnchangedAndGenericTextBoundRemains1024()
            throws Exception {
        final String text = "  A\u0301 \uD83D\uDE80\n  ";
        final var row = ContasPagarCaptureFixture.row(7).put(FIELD, text);
        assertEquals(text, map(row).raw());
        assertEquals(text, map(row).value());
        assertFalse(map(row.put(FIELD, "x".repeat(1025))).valid());
    }

    @Test
    void absenceDoesNotBecomeAnObservedNullOrAnOptionalWireField() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(7);
            row.remove(FIELD);
            assertEquals(ExpansionValue.Presence.ABSENT, map(row).presence());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    fixture.pin(source(row)),
                                    budget()));
        }
    }

    private static Set<String> types(final JsonNode field) {
        final var types = new HashSet<String>();
        field.required("wireTypes").forEach(value -> types.add(value.textValue()));
        return types;
    }

    private static ExpansionValue<String> map(final ObjectNode row) {
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        return new ContaPagarDataExportMapper().map(1, envelope).antCesAcrName();
    }

    private static byte[] source(final JsonNode row) throws Exception {
        return ("{\"entity\":\"CONTAS_A_PAGAR\",\"templateId\":8636,\"record\":"
                        + ExportValidationSupport.JSON.writeValueAsString(row)
                        + "}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode contract(final String name) throws Exception {
        try (var stream = ContasPagarObservedP3Test.class.getResourceAsStream(name)) {
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
