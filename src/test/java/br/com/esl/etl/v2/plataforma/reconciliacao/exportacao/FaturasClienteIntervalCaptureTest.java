package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteIntervalCaptureTest {
    @TempDir Path directory;

    @Test
    void qualifiedInfoAndLiteralRowsCompleteOnlyAfterActualEmptyDataArray() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            fixture.port.infoBytes =
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.currentQualifiedInfo());
            add(fixture, FaturasClienteCaptureFixture.row(1));
            end(fixture);
            final var result = fixture.capture(2, 100, 10);
            assertEquals("FATURAS_CLIENTE_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertEquals(3, result.required("responses").size());
            assertEquals(
                    FaturasClienteObservedWire52.INFO_NAME_BINDING_POLICY,
                    result.path("infoNameBindingPolicy").textValue());
            final var source = ExportValidationSupport.Pin.from(result.required("dataPin"));
            assertEquals(
                    1,
                    FaturasClienteRawMapperQualifier.verifyOriginalResponses(
                            result.required("responses"),
                            source,
                            new ArrayList<>(),
                            new ExportValidationSupport.Budget(Duration.ofSeconds(80))));
            assertFalse(Files.readString(source.path()).contains("third_party_ctes_value"));
        }
    }

    @Test
    void duplicateIdsAndMorePhysicalRowsThanPerRemainOccurrencesAcrossPages() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row = FaturasClienteCaptureFixture.row(1);
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.array(row, row, row, row, row)));
            add(fixture, row);
            end(fixture);
            final var result = fixture.capture(3, 3, 20);
            assertEquals(6, result.path("rows").longValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertFalse(result.path("pageDistinctRootCountEnforced").booleanValue());
            assertFalse(result.path("pageOverlapStopsCapture").booleanValue());
            final var lines =
                    Files.readAllLines(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path());
            assertEquals(6, lines.size());
            assertEquals(1, lines.stream().distinct().count());
        }
    }

    @Test
    void unknownNonNullExtraMissingAndInvalidArrayItemsAreRetainedWithoutQualification()
            throws Exception {
        for (final String mutation : List.of("unknown", "extra", "missing", "array")) {
            try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
                final var row = FaturasClienteCaptureFixture.row(1);
                switch (mutation) {
                    case "unknown" -> row.put("fit_nse_number", "7");
                    case "extra" -> row.put("third_party_ctes_value", "0.00");
                    case "missing" -> row.remove("comments");
                    default -> row.putArray("invoices_mapping").add(4);
                }
                add(fixture, row);
                end(fixture);
                final var result = fixture.capture(2, 100, 10);
                assertEquals("FATURAS_CLIENTE_SOURCE_COMPLETE", result.path("status").textValue());
                assertEquals(1, result.path("rows").longValue());
                assertFalse(result.path("rowTypesQualified").booleanValue());
                assertEquals(
                        ExportValidationSupport.JSON.readTree(
                                FaturasClienteCaptureFixture.json(row)),
                        ExportValidationSupport.JSON
                                .readTree(
                                        Files.readString(
                                                ExportValidationSupport.Pin.from(
                                                                result.required("dataPin"))
                                                        .path()))
                                .required("record"));
            }
        }
    }

    @Test
    void currentMapperRefusalDoesNotDiscardArraysOrLiteralNumberTokens() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row = FaturasClienteCaptureFixture.row(9007199254740993L);
            row.put("comments", "A".repeat(1025));
            final String original =
                    FaturasClienteCaptureFixture.array(row)
                            .toString()
                            .replace(
                                    "\"emission_type\":\"authored\"",
                                    "\"emission_type\":\"\\u0041\"");
            fixture.port.pages.add(original.getBytes(StandardCharsets.UTF_8));
            end(fixture);
            final var result = fixture.capture(2, 100, 10);
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            final String literal =
                    Files.readString(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path());
            assertTrue(literal.contains("9007199254740993"));
            assertTrue(literal.contains("\\u0041"));
            assertEquals(
                    row.required("invoices_mapping"),
                    ExportValidationSupport.JSON
                            .readTree(literal)
                            .required("record")
                            .required("invoices_mapping"));
        }
    }

    @Test
    void mapperOrdinalIsLocalEvenWhenOnePhysicalPageHasMoreThanOneHundredOccurrences()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var rows = FaturasClienteCaptureFixture.array();
            for (int index = 0; index < 101; index++) {
                rows.add(FaturasClienteCaptureFixture.row(1));
            }
            fixture.port.pages.add(FaturasClienteCaptureFixture.json(rows));
            end(fixture);
            final var result = fixture.capture(2, 1, 12000);
            assertEquals(101, result.path("rows").longValue());
            assertTrue(result.path("mapperQualified").booleanValue());
        }
    }

    @Test
    void legitimateEmptyDayHasZeroBytesAndShortNonemptyPageCannotClaimCompletion()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            end(fixture);
            final var result = fixture.capture(1, 100, 10);
            assertTrue(result.path("emptyInterval").booleanValue());
            assertEquals(0, result.path("payloadBytes").longValue());
            assertFalse(result.has("dataPin"));
        }
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            add(fixture, FaturasClienteCaptureFixture.row(1));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", result.path("status").textValue());
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.has("partialDataPin"));
        }
    }

    @Test
    void onlyServiceOffsetCivilDateIsScopedAndOtherDatesRemainLiteral() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row =
                    FaturasClienteCaptureFixture.row(1)
                            .put("service_at", "2026-10-18T23:59:59.999999999-03:00")
                            .put("fit_ant_issue_date", "2025-01-01")
                            .put("finished_at", "2027-01-01T00:00:00Z");
            add(fixture, row);
            end(fixture);
            final var result = fixture.capture(2, 100, 10);
            assertTrue(result.path("serviceAtScopeEnforced").booleanValue());
            assertFalse(result.path("updatedAtScopeEnforced").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
        }
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            add(
                    fixture,
                    FaturasClienteCaptureFixture.row(1).put("service_at", "2026-10-19T00:00:00Z"));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertFalse(
                    fixture.journal
                            .latest("FATURAS_CLIENTE_PARTIAL")
                            .path("terminalEmptyPageObserved")
                            .booleanValue());
        }
    }

    @Test
    void infoDriftAndHttpErrorStopBeforeAnotherDispatchWithNoRetry() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var info = FaturasClienteCaptureFixture.info();
            ((com.fasterxml.jackson.databind.node.ObjectNode) info.required("fields")).remove("id");
            fixture.port.infoBytes = FaturasClienteCaptureFixture.json(info);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(0, fixture.port.pageCalls);
        }
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            add(fixture, FaturasClienteCaptureFixture.row(1));
            fixture.port.status = 429;
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(1, fixture.port.pageCalls);
        }
    }

    private static void add(
            final FaturasClienteCaptureFixture fixture,
            final com.fasterxml.jackson.databind.JsonNode row)
            throws Exception {
        fixture.port.pages.add(
                FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array(row)));
    }

    private static void end(final FaturasClienteCaptureFixture fixture) throws Exception {
        fixture.port.pages.add(
                FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
    }
}
