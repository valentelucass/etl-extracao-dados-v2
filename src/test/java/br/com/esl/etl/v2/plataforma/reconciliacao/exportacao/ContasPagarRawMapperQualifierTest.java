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

class ContasPagarRawMapperQualifierTest {
    @TempDir Path directory;

    @Test
    void actualMapperRequalifiesEveryRepeatedInstallmentOccurrenceWithoutReplacingRaw()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1);
            final var source = pinText(fixture, line(row) + line(row));
            final var before = Files.readAllBytes(source.path());
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
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
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1).put("value", "not-a-decimal");
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("mapperRejectedRows").intValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
        }
    }

    @Test
    void numericCandidateLexemeDoesNotBecomeAGroupingKeyOrHideCurrentMapperRefusal()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final String raw =
                    line(ContasPagarCaptureFixture.row(1))
                            .replace(
                                    "\"ant_ils_sequence_code\":1",
                                    "\"ant_ils_sequence_code\":10000000000000001.0");
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, raw), budget());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertFalse(result.path("success").booleanValue());
            assertEquals(1, result.path("mapperRejectedRows").intValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
        }
    }

    @Test
    void unqualifiedJsonTypeIsSeparatedFromCurrentMapperAcceptance() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1).put("value", 12);
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertEquals(1, result.path("rowTypeRejectedRows").intValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertEquals(0, result.path("mapperRejectedRows").intValue());
        }
    }

    @Test
    void missingFinalLfMissingFieldAndTrailingJsonCannotBeQualified() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(row).stripTrailing()),
                                    budget()));
            row.remove("document");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(row)),
                                    budget()));
            final var valid = ContasPagarCaptureFixture.row(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(valid).stripTrailing() + " {}\n"),
                                    budget()));
        }
    }

    @Test
    void pinDriftAndIssueDateOutsideTheExactRequestRefuse() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var source = pinText(fixture, line(ContasPagarCaptureFixture.row(1)));
            Files.writeString(source.path(), "{}\n");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request), source, budget()));
            final var outside = ContasPagarCaptureFixture.row(1).put("issue_date", "2026-10-19");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarRawMapperQualifier.qualify(
                                    fixture.pin(fixture.request),
                                    pinText(fixture, line(outside)),
                                    budget()));
        }
    }

    @Test
    void repeatedZeroAndLargeInstallmentCandidatesAreNeverPromotedToPositiveLongRoots()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var zero = ContasPagarCaptureFixture.row(0);
            final var large =
                    ContasPagarCaptureFixture.row(1)
                            .put(
                                    "ant_ils_sequence_code",
                                    java.math.BigInteger.valueOf(Long.MAX_VALUE)
                                            .add(java.math.BigInteger.ONE));
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request),
                            pinText(fixture, line(zero) + line(zero) + line(large)),
                            budget());
            assertTrue(result.path("success").booleanValue());
            assertEquals(3, result.path("rows").intValue());
            assertFalse(result.path("deduplicationPerformed").booleanValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
        }
    }

    @Test
    void unobservedStringMonthAndCurrentMapperQualificationRemainSeparateAtEof() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1).put("competence_month", "10");
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
            assertEquals(1, result.path("rowTypeRejectedRows").intValue());
            assertEquals(0, result.path("mapperRejectedRows").intValue());
        }
    }

    @Test
    void
            sourceAndRequalificationAcceptIssuedItemsCreatedBeforeTheWeekWithoutChangingTheirTimestamp()
                    throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row =
                    ContasPagarCaptureFixture.row(1)
                            .put("created_at", "2026-09-01T02:59:59.123456789Z");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request),
                            ContasPagarRawMapperQualifier.pin(captured.required("dataPin")),
                            budget());
            assertTrue(result.path("success").booleanValue());
            assertFalse(result.path("createdAtScopeEnforced").booleanValue());
            assertTrue(result.path("createdAtValuePreserved").booleanValue());
            assertTrue(result.path("issueDateVerifiedEveryRow").booleanValue());
            assertEquals(
                    ContasPagarIndependentRequest.FILTER_SCOPE_POLICY,
                    result.path("filterScopePolicy").textValue());
            assertFalse(result.path("dateFilterSemanticsObserved").booleanValue());
        }
    }

    @Test
    void anInvalidCreatedAtStillRequiresMapperQuarantineEvenThoughItsDateDoesNotFilterRows()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row =
                    ContasPagarCaptureFixture.row(1)
                            .put("created_at", "authored-invalid-created-at");
            final var result =
                    ContasPagarRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), pinText(fixture, line(row)), budget());
            assertFalse(result.path("success").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertFalse(result.path("createdAtScopeEnforced").booleanValue());
            assertTrue(result.path("allRawRowsReadToEof").booleanValue());
        }
    }

    private static String line(final JsonNode row) throws Exception {
        final var envelope =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("entity", "CONTAS_A_PAGAR")
                        .put("templateId", 8636);
        envelope.set("record", row);
        return ExportValidationSupport.JSON.writeValueAsString(envelope) + "\n";
    }

    private static ExportValidationSupport.Pin pinText(
            final ContasPagarCaptureFixture fixture, final String value) throws Exception {
        return fixture.pin(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}
