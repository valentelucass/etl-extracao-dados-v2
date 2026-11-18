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

class LocalizadorRawMapperQualifierTest {
    @TempDir Path directory;

    @Test
    void everyLiteralDuplicateIsMappedWithTheObservedStringFactoryAndPinsStayUnchanged()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = LocalizadorCaptureFixture.row(1);
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row, row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 1, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final byte[] before = Files.readAllBytes(source.path());
            final var result =
                    LocalizadorRawMapperQualifier.qualify(
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
                    LocalizadorRawMapperQualifier.verifyOriginalResponses(
                            captured.required("responses"), source, held, budget()));
            assertEquals(3, held.size());
        }
    }

    @Test
    void negativeZeroLexemeCannotBeReplacedByTheMapperTreeOrBecomeAValidVolume() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final String page =
                    LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1))
                            .toString()
                            .replace("\"invoices_volumes\":0", "\"invoices_volumes\":-0");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final var result =
                    LocalizadorRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("rejectedRows").intValue());
            assertTrue(Files.readString(source.path()).contains("\"invoices_volumes\":-0"));
        }
    }

    @Test
    void historicalWireNumberAndCurrentIntegerOnlyMapperAreIndependentQualifications()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row =
                    LocalizadorCaptureFixture.row(1)
                            .put("corporation_sequence_number", new java.math.BigDecimal("1.5"));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var capture = fixture.capture(2, 100, 10);
            final var result =
                    LocalizadorRawMapperQualifier.qualify(
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
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1))));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var changed = captured.required("responses").deepCopy();
            final var other =
                    LocalizadorCaptureFixture.row(1)
                            .put("fit_pyr_nickname", "different-authored-value");
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get(1))
                    .set(
                            "rawBodyPin",
                            ExportValidationSupport.JSON.valueToTree(
                                    fixture.pin(LocalizadorCaptureFixture.array(other))));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorRawMapperQualifier.verifyOriginalResponses(
                                    changed,
                                    ExportValidationSupport.Pin.from(captured.required("dataPin")),
                                    new ArrayList<>(),
                                    budget()));
        }
    }

    @Test
    void nonemptyLastPageMissingFinalLfBadEnvelopeAndOutOfScopeServiceAtAreRefused()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1))));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final String original = Files.readString(source.path());
            final var request = fixture.pin(fixture.request);
            for (final String modified :
                    java.util.List.of(
                            original.stripTrailing(),
                            original.replace("\"LOCALIZADOR\"", "\"OTHER\""),
                            original.replace("2026-10-13T", "2026-10-19T"))) {
                final var modifiedPin = fixture.pin(modified.getBytes(StandardCharsets.UTF_8));
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                LocalizadorRawMapperQualifier.qualify(
                                        request, modifiedPin, budget()));
            }
            final var changed = captured.required("responses").deepCopy();
            final var replacement =
                    fixture.pin(LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1)));
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get(2))
                    .set("rawBodyPin", ExportValidationSupport.JSON.valueToTree(replacement));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorRawMapperQualifier.verifyOriginalResponses(
                                    changed, source, new ArrayList<>(), budget()));
        }
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(80));
    }
}
