package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteRawMapperQualifierTest {
    @TempDir Path directory;

    @Test
    void everyLiteralDuplicateIsMappedWithTheCurrentEnvelopeMapperAndPinsStayUnchanged()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row = FaturasClienteCaptureFixture.row(1);
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.array(row, row)));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var captured = fixture.capture(2, 1, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final byte[] before = Files.readAllBytes(source.path());
            final var result =
                    FaturasClienteRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertTrue(result.path("success").booleanValue());
            assertEquals(2, result.path("rows").intValue());
            assertEquals(2, result.path("currentMapperInvocations").intValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
            assertFalse(result.path("rawBytesReserializedOrReplaced").booleanValue());
            assertArrayEquals(before, Files.readAllBytes(source.path()));
            final var held = new ArrayList<ExportValidationSupport.Pin>();
            assertEquals(
                    2,
                    FaturasClienteRawMapperQualifier.verifyOriginalResponses(
                            captured.required("responses"), source, held, budget()));
            assertEquals(3, held.size());
        }
    }

    @Test
    void negativeZeroLexemeSurvivesEvenWhenTheIntegerMapperAcceptsItsCanonicalTree()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final String page =
                    FaturasClienteCaptureFixture.array(FaturasClienteCaptureFixture.row(1))
                            .toString()
                            .replace(
                                    "\"corporation_sequence_number\":1",
                                    "\"corporation_sequence_number\":-0");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final var result =
                    FaturasClienteRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertEquals(0, result.path("rejectedRows").intValue());
            assertTrue(
                    Files.readString(source.path()).contains("\"corporation_sequence_number\":-0"));
        }
    }

    @Test
    void historicalWireNumberAndCurrentIntegerOnlyMapperAreIndependentQualifications()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row =
                    FaturasClienteCaptureFixture.row(1)
                            .put("corporation_sequence_number", new java.math.BigDecimal("1.5"));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array(row)));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var capture = fixture.capture(2, 100, 10);
            final var result =
                    FaturasClienteRawMapperQualifier.qualify(
                            fixture.pin(fixture.request),
                            ExportValidationSupport.Pin.from(capture.required("dataPin")),
                            budget());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
        }
    }

    @Test
    void changingOriginalPageValuesOrMultiplicityCannotMatchTheRetainedRawByRowCountAlone()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.array(
                                    FaturasClienteCaptureFixture.row(1))));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var changed = captured.required("responses").deepCopy();
            final var other =
                    FaturasClienteCaptureFixture.row(1)
                            .put("fit_pyr_name", "different-authored-value");
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get(1))
                    .set(
                            "rawBodyPin",
                            ExportValidationSupport.JSON.valueToTree(
                                    fixture.pin(FaturasClienteCaptureFixture.array(other))));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FaturasClienteRawMapperQualifier.verifyOriginalResponses(
                                    changed,
                                    ExportValidationSupport.Pin.from(captured.required("dataPin")),
                                    new ArrayList<>(),
                                    budget()));
        }
    }

    @Test
    void nonemptyLastPageMissingFinalLfBadEnvelopeAndOutOfScopeServiceAtAreRefused()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.array(
                                    FaturasClienteCaptureFixture.row(1))));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final String original = Files.readString(source.path());
            final var request = fixture.pin(fixture.request);
            for (final String modified :
                    java.util.List.of(
                            original.stripTrailing(),
                            original.replace("\"FATURAS_POR_CLIENTE\"", "\"OTHER\""),
                            original.replace("2026-10-13T", "2026-10-19T"))) {
                final var modifiedPin = fixture.pin(modified.getBytes(StandardCharsets.UTF_8));
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                FaturasClienteRawMapperQualifier.qualify(
                                        request, modifiedPin, budget()));
            }
            final var changed = captured.required("responses").deepCopy();
            final var replacement =
                    fixture.pin(
                            FaturasClienteCaptureFixture.array(
                                    FaturasClienteCaptureFixture.row(1)));
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get(2))
                    .set("rawBodyPin", ExportValidationSupport.JSON.valueToTree(replacement));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FaturasClienteRawMapperQualifier.verifyOriginalResponses(
                                    changed, source, new ArrayList<>(), budget()));
        }
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(80));
    }
}
