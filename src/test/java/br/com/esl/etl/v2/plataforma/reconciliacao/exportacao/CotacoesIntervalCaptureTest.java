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

class CotacoesIntervalCaptureTest {
    @TempDir Path directory;

    @Test
    void completeExpandedPagePreservesEveryOriginalAndDecimalLexemeBeforeRealTerminal()
            throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var row = CotacoesCaptureFixture.row(1);
            final String page =
                    CotacoesCaptureFixture.array(row, row)
                            .toString()
                            .replace(
                                    "\"qoe_qes_invoices_volumes\":7",
                                    "\"qoe_qes_invoices_volumes\":7.2500");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 10);
            assertEquals("COTACOES_SOURCE_COMPLETE", result.path("status").asText());
            assertEquals(2, result.path("rows").intValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertFalse(result.path("deduplicationPerformed").booleanValue());
            assertFalse(result.path("contentQualified").booleanValue());
            final var pin = ExportValidationSupport.Pin.from(result.required("dataPin"));
            final var lines = Files.readAllLines(pin.path());
            assertEquals(2, lines.size());
            assertEquals(lines.get(0), lines.get(1));
            assertTrue(lines.get(0).contains("\"qoe_qes_invoices_volumes\":7.2500"));
            final var retained =
                    ExportValidationSupport.JSON.readTree(lines.get(0)).required("record");
            assertEquals(37, retained.size());
            assertEquals(row.path("qoe_uer_name"), retained.path("qoe_uer_name"));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void expandedOccurrencesBeyondMapperChunkKeepOriginalOrderCountAndMapperQualification()
            throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final int occurrences =
                    br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageRecord.MAXIMUM_PAGE_SIZE
                            + 1;
            assertTrue(occurrences > 100);
            final var records = CotacoesCaptureFixture.array();
            final var row = CotacoesCaptureFixture.row(1);
            for (int index = 0; index < occurrences; index++) {
                records.add(row);
            }
            fixture.port.pages.add(CotacoesCaptureFixture.json(records));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 12000);
            assertEquals("COTACOES_SOURCE_COMPLETE", result.path("status").textValue());
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
        try (var fixture = new CotacoesCaptureFixture(directory, true)) {
            fixture.port.pages.add(
                    CotacoesCaptureFixture.json(
                            CotacoesCaptureFixture.array(CotacoesCaptureFixture.row(1))));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var first = fixture.capture(2, 1, 10);
            final var payload = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] bytes = Files.readAllBytes(payload.path());
            fixture.reopen();
            fixture.clock.value = CotacoesCaptureFixture.NOW.plusSeconds(500);
            assertEquals(first, fixture.capture(2, 1, 10));
            assertArrayEquals(bytes, Files.readAllBytes(payload.path()));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void emptyFirstArrayIsExplicitAndCannotInventANonemptyPayloadPin() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("COTACOES_SOURCE_COMPLETE", result.path("status").asText());
            assertTrue(result.path("emptyInterval").booleanValue());
            assertFalse(result.has("dataPin"));
            assertEquals(0, result.path("rows").intValue());
            assertEquals(2, result.path("responses").size());
        }
    }

    @Test
    void shortNonemptyPageAtTheCapIsPartialAndPreservedWithoutAssumingTerminal() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    CotacoesCaptureFixture.json(
                            CotacoesCaptureFixture.array(CotacoesCaptureFixture.row(1))));
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
            try (var fixture = new CotacoesCaptureFixture(directory, false)) {
                fixture.port.pages.add(body.getBytes(StandardCharsets.UTF_8));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                final var partial = fixture.journal.latest("COTACOES_PARTIAL");
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
            try (var fixture = new CotacoesCaptureFixture(directory, false)) {
                final var row = CotacoesCaptureFixture.row(1);
                switch (mutation) {
                    case "text" -> row.put("sequence_code", "1");
                    case "decimal" -> row.put("sequence_code", 1.0);
                    case "zero" -> row.put("sequence_code", 0);
                    case "overflow" ->
                            row.put(
                                    "sequence_code",
                                    java.math.BigInteger.valueOf(Long.MAX_VALUE)
                                            .add(java.math.BigInteger.ONE));
                    case "date" -> row.put("requested_at", "bad-date");
                    default -> row.put("requested_at", "2026-10-19T00:00:00-03:00");
                }
                fixture.port.pages.add(
                        CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                assertFalse(fixture.journal.latest("COTACOES_PARTIAL").has("dataPin"));
            }
        }
    }

    @Test
    void requestedAtScopeDoesNotBecomeTheDerivedCteFreshnessDate() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var row =
                    CotacoesCaptureFixture.row(1)
                            .put("qoe_qes_fit_fhe_cte_issued_at", "2026-11-01T01:00:00-03:00");
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            assertEquals(
                    "COTACOES_SOURCE_COMPLETE",
                    fixture.capture(2, 100, 10).path("status").textValue());
        }
    }

    @Test
    void infoDriftAndData429StopWithoutRetryOrPromotion() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var info = CotacoesCaptureFixture.info();
            ((com.fasterxml.jackson.databind.node.ObjectNode) info.path("fields"))
                    .remove("requester_name");
            fixture.port.infoBytes = CotacoesCaptureFixture.json(info);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(0, fixture.port.pageCalls);
        }
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            fixture.port.status = 429;
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(1, fixture.port.pageCalls);
            assertFalse(fixture.journal.latest("COTACOES_PARTIAL").has("dataPin"));
        }
    }

    @Test
    void wholeRootOverlapRetainsOccurrencesButCannotPromoteAnInterval() throws Exception {
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final byte[] page =
                    CotacoesCaptureFixture.json(
                            CotacoesCaptureFixture.array(CotacoesCaptureFixture.row(1)));
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
            try (var fixture = new CotacoesCaptureFixture(directory, false)) {
                final var row = CotacoesCaptureFixture.row(1);
                if ("missing".equals(mutation)) {
                    row.remove("requester_name");
                } else if ("type".equals(mutation)) {
                    row.put("qoe_qes_status", true);
                } else {
                    row.putNull("qoe_cor_document");
                }
                fixture.port.pages.add(
                        CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
                fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
                if ("missing".equals(mutation)) {
                    assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
                    assertEquals(
                            2, fixture.journal.latest("COTACOES_PARTIAL").path("responses").size());
                } else {
                    final var result = fixture.capture(2, 100, 10);
                    assertEquals("COTACOES_SOURCE_COMPLETE", result.path("status").textValue());
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
        try (var fixture = new CotacoesCaptureFixture(directory, false)) {
            final var row = CotacoesCaptureFixture.row(1).put("qoe_qes_total", "invalid-amount");
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array(row)));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("COTACOES_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("mapperRejectedOccurrences").intValue());
            assertEquals(
                    1, result.path("mapperRefusalCounts").path("INVALID_TOTAL_AMOUNT").intValue());
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
        try (var fixture = new CotacoesCaptureFixture(directory, true)) {
            fixture.port.pages.add(
                    CotacoesCaptureFixture.json(
                            CotacoesCaptureFixture.array(CotacoesCaptureFixture.row(1))));
            fixture.port.pages.add(CotacoesCaptureFixture.json(CotacoesCaptureFixture.array()));
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
}
