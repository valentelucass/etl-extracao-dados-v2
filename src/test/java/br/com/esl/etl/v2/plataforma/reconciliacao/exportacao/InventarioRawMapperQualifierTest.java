package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventarioRawMapperQualifierTest {
    @TempDir Path directory;

    @Test
    void correctedActualMapperRequalifiesEveryThirtyThreeItemOccurrenceWithoutReplacingRaw()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row = InventarioCaptureFixture.row(1);
            final var items = row.putArray("cnr_c_s_fit_invoices_mapping");
            for (int index = 0; index < 33; index++) {
                items.add(index % 2 == 0 ? "A" : "B");
            }
            final var source = pinText(fixture, line(row) + line(row));
            final var before = Files.readAllBytes(source.path());
            final var result =
                    InventarioRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertTrue(result.path("success").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
            assertEquals(2, result.path("rows").intValue());
            assertEquals(2, result.path("currentMapperInvocations").intValue());
            assertEquals(0, result.path("rejectedRows").intValue());
            assertFalse(result.path("rawBytesReserializedOrReplaced").booleanValue());
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    before, Files.readAllBytes(source.path()));
        }
    }

    @Test
    void invalidTypedDecimalIsCountedAtEofAndCannotBecomeAQualifiedMapperResult() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row =
                    InventarioCaptureFixture.row(1)
                            .put("cnr_c_s_fit_invoices_value", "not-a-decimal");
            final var result =
                    InventarioRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("mapperRejectedRows").intValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
        }
    }

    @Test
    void unqualifiedJsonTypeIsSeparatedFromCurrentMapperAcceptance() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row = InventarioCaptureFixture.row(1).put("status", true);
            final var result =
                    InventarioRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertEquals(1, result.path("rowTypeRejectedRows").intValue());
        }
    }

    @Test
    void missingFinalLfMissingFieldAndTrailingJsonCannotBeQualified() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row = InventarioCaptureFixture.row(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(row).stripTrailing()),
                                    budget()));
            row.remove("status");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(row)),
                                    budget()));
            final var valid = InventarioCaptureFixture.row(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(valid).stripTrailing() + " {}\n"),
                                    budget()));
        }
    }

    @Test
    void pinDriftAndStartedAtOutsideTheExactRequestRefuse() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var source = pinText(fixture, line(InventarioCaptureFixture.row(1)));
            Files.writeString(source.path(), "{}\n");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request), source, budget()));
            final var outside =
                    InventarioCaptureFixture.row(1).put("started_at", "2026-10-19T00:00:00-03:00");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(outside)),
                                    budget()));
        }
    }

    private static String line(final JsonNode row) throws Exception {
        final var envelope =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("entity", "INVENTARIO")
                        .put("templateId", 10633);
        envelope.set("record", row);
        return ExportValidationSupport.JSON.writeValueAsString(envelope) + "\n";
    }

    private static ExportValidationSupport.Pin pinText(
            final InventarioCaptureFixture fixture, final String value) throws Exception {
        return fixture.pin(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}
