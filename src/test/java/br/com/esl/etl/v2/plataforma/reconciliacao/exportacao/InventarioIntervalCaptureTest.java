package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventarioIntervalCaptureTest {
    @TempDir Path directory;

    @Test
    void completeExpandedPagePreservesEveryOriginalAndDecimalLexemeBeforeRealTerminal()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row = InventarioCaptureFixture.row(1);
            final String page =
                    InventarioCaptureFixture.array(row, row)
                            .toString()
                            .replace(
                                    "\"cnr_c_s_fit_invoices_volumes\":7",
                                    "\"cnr_c_s_fit_invoices_volumes\":7.2500");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 10);
            assertEquals("INVENTARIO_SOURCE_COMPLETE", result.path("status").asText());
            assertEquals(2, result.path("rows").intValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertFalse(result.path("deduplicationPerformed").booleanValue());
            assertFalse(result.path("contentQualified").booleanValue());
            final var pin = ExportValidationSupport.Pin.from(result.required("dataPin"));
            final var lines = Files.readAllLines(pin.path());
            assertEquals(2, lines.size());
            assertEquals(lines.get(0), lines.get(1));
            assertTrue(lines.get(0).contains("\"cnr_c_s_fit_invoices_volumes\":7.2500"));
            final var retained =
                    ExportValidationSupport.JSON.readTree(lines.get(0)).required("record");
            assertEquals(26, retained.size());
            assertEquals(row.path("cnr_crn_psn_nickname"), retained.path("cnr_crn_psn_nickname"));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void expandedOccurrencesBeyondMapperChunkKeepOriginalOrderCountAndMapperQualification()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final int occurrences = 100 + 1;
            assertTrue(occurrences > 100);
            final var records = InventarioCaptureFixture.array();
            final var row = InventarioCaptureFixture.row(1);
            for (int index = 0; index < occurrences; index++) {
                records.add(row);
            }
            fixture.port.pages.add(InventarioCaptureFixture.json(records));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 12000);
            assertEquals("INVENTARIO_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(occurrences, result.path("rows").intValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            final var lines =
                    Files.readAllLines(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path());
            assertEquals(occurrences, lines.size());
            assertEquals(lines.get(0), lines.get(lines.size() - 1));
            assertFalse(result.path("deduplicationPerformed").booleanValue());
        }
    }

    @Test
    void completedDurableReopenRechecksBytesAndNeverRefreshesSourceBudget() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, true)) {
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(
                            InventarioCaptureFixture.array(InventarioCaptureFixture.row(1))));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var first = fixture.capture(2, 1, 10);
            final var payload = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] bytes = Files.readAllBytes(payload.path());
            fixture.reopen();
            fixture.clock.value = InventarioCaptureFixture.NOW.plusSeconds(500);
            assertEquals(first, fixture.capture(2, 1, 10));
            assertArrayEquals(bytes, Files.readAllBytes(payload.path()));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void emptyFirstArrayIsExplicitAndCannotInventANonemptyPayloadPin() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("INVENTARIO_SOURCE_COMPLETE", result.path("status").asText());
            assertTrue(result.path("emptyInterval").booleanValue());
            assertFalse(result.has("dataPin"));
            assertEquals(0, result.path("rows").intValue());
            assertEquals(2, result.path("responses").size());
        }
    }

    @Test
    void shortNonemptyPageAtTheCapIsPartialAndPreservedWithoutAssumingTerminal() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(
                            InventarioCaptureFixture.array(InventarioCaptureFixture.row(1))));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", result.path("status").asText());
            assertFalse(result.path("payloadPromoted").booleanValue());
            assertTrue(result.has("partialDataPin"));
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertEquals(1, fixture.port.pageCalls);
        }
    }

    @Test
    void objectErrorAndTrailingJsonCannotBecomeEmptyTerminal() throws Exception {
        for (final String body : List.of("{\"data\":[]}", "{\"error\":\"synthetic\"}", "[] {}")) {
            try (var fixture = new InventarioCaptureFixture(directory, false)) {
                fixture.port.pages.add(body.getBytes(StandardCharsets.UTF_8));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                final var partial = fixture.journal.latest("INVENTARIO_PARTIAL");
                assertFalse(partial.path("emptyInterval").asBoolean());
                assertFalse(partial.has("dataPin"));
                assertEquals(1, fixture.port.pageCalls);
            }
        }
    }

    @Test
    void onlyIntegralPositiveLongSequenceCodesAndRequestedDatesInsideScopeCanBeRetained()
            throws Exception {
        for (final String mutation :
                List.of("text", "decimal", "zero", "overflow", "date", "outside")) {
            try (var fixture = new InventarioCaptureFixture(directory, false)) {
                final var row = InventarioCaptureFixture.row(1);
                switch (mutation) {
                    case "text" -> row.put("sequence_code", "1");
                    case "decimal" -> row.put("sequence_code", 1.0);
                    case "zero" -> row.put("sequence_code", 0);
                    case "overflow" ->
                            row.put(
                                    "sequence_code",
                                    java.math.BigInteger.valueOf(Long.MAX_VALUE)
                                            .add(java.math.BigInteger.ONE));
                    case "date" -> row.put("started_at", "bad-date");
                    default -> row.put("started_at", "2026-10-19T00:00:00-03:00");
                }
                fixture.port.pages.add(
                        InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                assertFalse(fixture.journal.latest("INVENTARIO_PARTIAL").has("dataPin"));
            }
        }
    }

    @Test
    void startedAtScopeDoesNotBecomeTheLastOccurrenceDate() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row =
                    InventarioCaptureFixture.row(1)
                            .put("cnr_c_s_fit_fte_lce_occurrence_at", "2026-11-01T01:00:00-03:00");
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            assertEquals(
                    "INVENTARIO_SOURCE_COMPLETE",
                    fixture.capture(2, 100, 10).path("status").textValue());
        }
    }

    @Test
    void infoDriftAndData429StopWithoutRetryOrPromotion() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var info = InventarioCaptureFixture.info();
            ((com.fasterxml.jackson.databind.node.ObjectNode) info.path("fields"))
                    .remove("cnr_crn_psn_nickname");
            fixture.port.infoBytes = InventarioCaptureFixture.json(info);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(0, fixture.port.pageCalls);
        }
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            fixture.port.status = 429;
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(1, fixture.port.pageCalls);
            assertFalse(fixture.journal.latest("INVENTARIO_PARTIAL").has("dataPin"));
        }
    }

    @Test
    void wholeRootOverlapRetainsOccurrencesButCannotPromoteAnInterval() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final byte[] page =
                    InventarioCaptureFixture.json(
                            InventarioCaptureFixture.array(InventarioCaptureFixture.row(1)));
            fixture.port.pages.add(page);
            fixture.port.pages.add(page);
            final var result = fixture.capture(2, 1, 10);
            assertEquals(
                    "PARTIAL_PAGE_ID_OVERLAP_RECONCILIATION_REQUIRED",
                    result.path("status").textValue());
            assertEquals(2, result.path("rows").intValue());
            assertFalse(result.has("dataPin"));
        }
    }

    @Test
    void missingFieldStopsButNewTypeOrNullRetainsRawThroughTerminalWithUnqualifiedTypes()
            throws Exception {
        for (final String mutation : List.of("missing", "type", "null")) {
            try (var fixture = new InventarioCaptureFixture(directory, false)) {
                final var row = InventarioCaptureFixture.row(1);
                if ("missing".equals(mutation)) {
                    row.remove("cnr_crn_psn_nickname");
                } else if ("type".equals(mutation)) {
                    row.put("status", true);
                } else {
                    row.putNull("cnr_crn_psn_nickname");
                }
                fixture.port.pages.add(
                        InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
                fixture.port.pages.add(
                        InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
                if ("missing".equals(mutation)) {
                    assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
                    assertEquals(
                            2,
                            fixture.journal.latest("INVENTARIO_PARTIAL").path("responses").size());
                } else {
                    final var result = fixture.capture(2, 100, 10);
                    assertEquals("INVENTARIO_SOURCE_COMPLETE", result.path("status").textValue());
                    assertFalse(result.path("rowTypesQualified").booleanValue());
                    assertEquals(1, result.path("rowTypeRejectedOccurrences").intValue());
                    assertEquals(
                            ExportValidationSupport.JSON.readTree(row.toString()),
                            ExportValidationSupport.JSON
                                    .readTree(
                                            Files.readAllLines(
                                                            ExportValidationSupport.Pin.from(
                                                                            result.required(
                                                                                    "dataPin"))
                                                                    .path())
                                                    .get(0))
                                    .required("record"));
                    assertEquals(2, fixture.port.pageCalls);
                }
            }
        }
    }

    @Test
    void mapperRejectionIsRecordedWithoutDroppingRawOrRepeatingSource() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row =
                    InventarioCaptureFixture.row(1)
                            .put("cnr_c_s_fit_invoices_value", "invalid-amount");
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("INVENTARIO_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("mapperRejectedOccurrences").intValue());
            assertEquals(
                    1,
                    result.path("mapperRefusalCounts")
                            .path("INVENTARIO_TYPED_OBSERVATION_INVALID")
                            .intValue());
            assertEquals(1, result.path("rows").intValue());
            assertEquals(2, fixture.port.pageCalls);
            assertEquals(
                    ExportValidationSupport.JSON.readTree(row.toString()),
                    ExportValidationSupport.JSON
                            .readTree(
                                    Files.readAllLines(
                                                    ExportValidationSupport.Pin.from(
                                                                    result.required("dataPin"))
                                                            .path())
                                            .get(0))
                            .required("record"));
            assertFalse(result.path("contentQualified").booleanValue());
        }
    }

    @Test
    void payloadDriftOnClosedJournalIsNotRepairedByAnotherSourceCall() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, true)) {
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(
                            InventarioCaptureFixture.array(InventarioCaptureFixture.row(1))));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 10);
            fixture.reopen();
            Files.writeString(
                    ExportValidationSupport.Pin.from(result.required("dataPin")).path(),
                    "corrupted");
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 1, 10));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void arrayOrderAndDuplicatesRemainLiteralAndUnknownItemTypesRemainUnqualified()
            throws Exception {
        for (final boolean unknownItem : List.of(false, true)) {
            try (var fixture = new InventarioCaptureFixture(directory, false)) {
                final var row = InventarioCaptureFixture.row(1);
                final var invoices =
                        row.putArray("cnr_c_s_fit_invoices_mapping").add("A").add("A").add("B");
                if (unknownItem) {
                    invoices.add(19);
                }
                fixture.port.pages.add(
                        InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
                fixture.port.pages.add(
                        InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
                final var result = fixture.capture(2, 100, 10);
                assertEquals(!unknownItem, result.path("rowTypesQualified").booleanValue());
                final var raw =
                        ExportValidationSupport.JSON
                                .readTree(
                                        Files.readAllLines(
                                                        ExportValidationSupport.Pin.from(
                                                                        result.required("dataPin"))
                                                                .path())
                                                .get(0))
                                .required("record");
                assertEquals(invoices, raw.path("cnr_c_s_fit_invoices_mapping"));
            }
        }
    }

    @Test
    void localDateScopeUsesSaoPauloOffsetInsteadOfUtcCalendarDate() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row =
                    InventarioCaptureFixture.row(1).put("started_at", "2026-10-19T02:59:59Z");
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            assertEquals(
                    "INVENTARIO_SOURCE_COMPLETE",
                    fixture.capture(2, 100, 10).path("status").textValue());
        }
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var row =
                    InventarioCaptureFixture.row(1).put("started_at", "2026-10-19T03:00:00Z");
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
        }
    }

    @Test
    void finiteCapsRejectBeforeAnySourceDispatchAndRowsCapPreservesFirstRows() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(17, 100, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 101, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 100, 12001));
            assertEquals(0, fixture.port.infoCalls);
            fixture.port.pages.add(
                    InventarioCaptureFixture.json(
                            InventarioCaptureFixture.array(
                                    InventarioCaptureFixture.row(1),
                                    InventarioCaptureFixture.row(1))));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 1));
            final var partial = fixture.journal.latest("INVENTARIO_PARTIAL");
            assertEquals(1, partial.path("rows").intValue());
            assertTrue(partial.has("partialDataPin"));
            assertFalse(partial.has("dataPin"));
        }
    }

    @Test
    void observedCompletedCaptureDoesNotAttestSourceIdentityOrAcceptNativeContent()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
            final var result = fixture.capture(1, 100, 10);
            assertTrue(result.path("sequenceCodeUsedAsPageOverlapCandidate").booleanValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
            assertFalse(result.path("contentQualified").booleanValue());
            assertFalse(result.path("entityQualified").booleanValue());
        }
    }
}
