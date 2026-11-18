package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizadorIntervalCaptureTest {
    @TempDir Path directory;

    @Test
    void completeCaptureWithCurrentTableFieldInfoRetainsExplicitBindingPolicyAndLiteralRaw()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.infoBytes =
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.currentQualifiedInfo());
            final var row = LocalizadorCaptureFixture.row(1);
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
            assertEquals(3, result.required("responses").size());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.path("historicalInfoNamesVerified").booleanValue());
            assertEquals(
                    "STRICT_HISTORICAL_OR_OBSERVED_QUALIFIED_BIJECTION_V1",
                    result.path("infoNameBindingPolicy").textValue());
            assertEquals(
                    "OBSERVED_QUALIFIED_BIJECTION8_V1",
                    result.path("infoNameBindingVariant").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertEquals(1, retained(result).size());
            assertEquals(
                    row.required("invoices_mapping"),
                    ExportValidationSupport.JSON
                            .readTree(retained(result).get(0))
                            .required("record")
                            .required("invoices_mapping"));
            assertEquals(
                    1,
                    LocalizadorRawMapperQualifier.verifyOriginalResponses(
                            result.required("responses"),
                            ExportValidationSupport.Pin.from(result.required("dataPin")),
                            new java.util.ArrayList<>(),
                            new ExportValidationSupport.Budget(java.time.Duration.ofSeconds(80))));
        }
    }

    @Test
    void repeatsAcrossAndWithinPagesRemainLiteralOccurrencesAndOnlyRealEmptyArrayEndsTheCapture()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = LocalizadorCaptureFixture.row(1);
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(row, row, row, row, row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(3, 3, 20);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(6, result.path("rows").intValue());
            assertEquals(3, fixture.port.pageCalls);
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            final var rows = retained(result);
            assertEquals(6, rows.size());
            assertEquals(1, rows.stream().distinct().count());
            assertFalse(result.path("pageOverlapStopsCapture").booleanValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
        }
    }

    @Test
    void originalNumberTokensEscapesUnicodeAndOrderedInvoiceRepetitionsSurvive() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = LocalizadorCaptureFixture.row(1);
            final String page =
                    LocalizadorCaptureFixture.array(row)
                            .toString()
                            .replace("\"invoices_volumes\":0", "\"invoices_volumes\":-0")
                            .replace("\"type\":\"authored\"", "\"type\":\"\\u0041\"");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 20);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            final String line = retained(result).get(0);
            assertTrue(line.contains("\"invoices_volumes\":-0"));
            assertTrue(line.contains("\"type\":\"\\u0041\""));
            assertTrue(line.contains("2026-10-13T12:34:56.123456789-03:00"));
            final JsonNode actual = ExportValidationSupport.JSON.readTree(line).required("record");
            assertEquals(row.path("invoices_mapping"), actual.path("invoices_mapping"));
            assertEquals(row.path("fit_pyr_nickname"), actual.path("fit_pyr_nickname"));
        }
    }

    @Test
    void mapperChunksAreOrdinalLocalAndNeverCapAResponseAtOneHundredOrDeduplicateIdentifiers()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var page = LocalizadorCaptureFixture.array();
            for (int index = 0; index < 101; index++) {
                page.add(LocalizadorCaptureFixture.row(1));
            }
            fixture.port.pages.add(LocalizadorCaptureFixture.json(page));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 12000);
            assertEquals(101, result.path("rows").intValue());
            assertEquals(101, retained(result).size());
            assertTrue(result.path("mapperQualified").booleanValue());
        }
    }

    @Test
    void wireRefusalsAndMapperQuarantinesDoNotLoseNewNullsExtraPathsOrAnyRawOccurrence()
            throws Exception {
        for (final String mutation :
                List.of("null", "extra", "missing", "numericSourceKey", "array")) {
            try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
                final var row = LocalizadorCaptureFixture.row(1);
                switch (mutation) {
                    case "null" -> row.putNull("fit_pyr_nickname");
                    case "extra" -> row.put("unknown_path", "authored");
                    case "missing" -> row.remove("type");
                    case "numericSourceKey" ->
                            row.put("corporation_sequence_number", new java.math.BigDecimal("1.5"));
                    default -> row.putArray("invoices_mapping").add(1);
                }
                fixture.port.pages.add(
                        LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
                fixture.port.pages.add(
                        LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
                final var result = fixture.capture(2, 100, 10);
                assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
                assertEquals(1, retained(result).size());
                if ("numericSourceKey".equals(mutation)) {
                    assertTrue(result.path("rowTypesQualified").booleanValue());
                    assertFalse(result.path("mapperQualified").booleanValue());
                } else {
                    assertFalse(result.path("rowTypesQualified").booleanValue());
                }
            }
        }
    }

    @Test
    void serviceAtOffsetCivilDayIsCheckedWithoutLosingTheFractionOrUsingOtherDates()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row =
                    LocalizadorCaptureFixture.row(1)
                            .put("service_at", "2026-10-18T23:59:59.999999999-03:00")
                            .put("finished_at", "2027-01-01T00:00:00Z")
                            .put("fit_d_t_created_at", "2025-01-01T00:00:00Z");
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertTrue(result.path("serviceAtVerifiedForRetainedRows").booleanValue());
            assertFalse(result.path("updatedAtScopeEnforced").booleanValue());
            assertTrue(retained(result).get(0).contains("2026-10-18T23:59:59.999999999-03:00"));
        }
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(
                                    LocalizadorCaptureFixture.row(1)
                                            .put("service_at", "2026-10-19T00:00:00-03:00"))));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(2, fixture.journal.latest("LOCALIZADOR_PARTIAL").path("responses").size());
            assertFalse(
                    fixture.journal
                            .latest("LOCALIZADOR_PARTIAL")
                            .path("terminalEmptyPageObserved")
                            .booleanValue());
        }
    }

    @Test
    void emptyAndShortPagesNeverBecomeTheSameCompletionFact() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(1, 100, 10);
            assertTrue(result.path("emptyInterval").booleanValue());
            assertFalse(result.has("dataPin"));
            assertEquals(0, result.path("rows").intValue());
        }
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1))));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", result.path("status").textValue());
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.has("partialDataPin"));
        }
    }

    @Test
    void infoDriftHttp429AndNonArrayOrTrailingBodiesStopWithoutRetry() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var info = LocalizadorCaptureFixture.info();
            ((com.fasterxml.jackson.databind.node.ObjectNode) info.required("fields"))
                    .remove("type");
            fixture.port.infoBytes = LocalizadorCaptureFixture.json(info);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(0, fixture.port.pageCalls);
        }
        for (final String body : List.of("{\"data\":[]}", "[] {}", "[]")) {
            try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
                fixture.port.pages.add(body.getBytes(StandardCharsets.UTF_8));
                if ("[]".equals(body)) {
                    fixture.port.status = 429;
                }
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
                assertEquals(1, fixture.port.pageCalls);
            }
        }
    }

    @Test
    void capsDurableReopenAndOriginalResponsePinsCannotBeRelaxedOrRefreshSource() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, true)) {
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(17, 100, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 101, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 100, 12001));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(
                            LocalizadorCaptureFixture.array(LocalizadorCaptureFixture.row(1))));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var first = fixture.capture(2, 100, 10);
            final var pin = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] original = Files.readAllBytes(pin.path());
            fixture.reopen();
            fixture.clock.value = LocalizadorCaptureFixture.NOW.plusSeconds(500);
            assertEquals(first, fixture.capture(2, 100, 10));
            assertArrayEquals(original, Files.readAllBytes(pin.path()));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void
            validObservedInvoiceArrayAboveCapLineSizeIsRetainedAndQualifiedWithinTheExistingLocalMapperBound()
                    throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = LocalizadorCaptureFixture.row(1);
            final var invoices = row.putArray("invoices_mapping");
            for (int index = 0; index < 1000; index++) {
                invoices.add("A".repeat(400));
            }
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            final var source = ExportValidationSupport.Pin.from(result.required("dataPin"));
            assertTrue(source.bytes() > 262144);
            assertTrue(
                    Files.readString(source.path()).length()
                            < LocalizadorRawMapperQualifier.MAXIMUM_LINE_UTF16);
            final var qualified =
                    LocalizadorRawMapperQualifier.qualify(
                            fixture.pin(fixture.request),
                            source,
                            new ExportValidationSupport.Budget(java.time.Duration.ofSeconds(80)));
            assertTrue(qualified.path("success").booleanValue());
            assertEquals(
                    1000,
                    ExportValidationSupport.JSON
                            .readTree(retained(result).get(0))
                            .required("record")
                            .path("invoices_mapping")
                            .size());
            assertEquals(
                    1,
                    LocalizadorRawMapperQualifier.verifyOriginalResponses(
                            result.required("responses"),
                            source,
                            new java.util.ArrayList<>(),
                            new ExportValidationSupport.Budget(java.time.Duration.ofSeconds(80))));
        }
    }

    @Test
    void
            exceedingTheUnchangedMapperArrayBoundKeepsTheRawAndReportsQuarantineWithoutGenericWidening()
                    throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = LocalizadorCaptureFixture.row(1);
            final var invoices = row.putArray("invoices_mapping");
            for (int index = 0; index < 1001; index++) {
                invoices.add("A");
            }
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(
                    1001,
                    ExportValidationSupport.JSON
                            .readTree(retained(result).get(0))
                            .required("record")
                            .path("invoices_mapping")
                            .size());
        }
    }

    private static List<String> retained(final JsonNode result) throws Exception {
        return Files.readAllLines(
                ExportValidationSupport.Pin.from(result.required("dataPin")).path());
    }
}
