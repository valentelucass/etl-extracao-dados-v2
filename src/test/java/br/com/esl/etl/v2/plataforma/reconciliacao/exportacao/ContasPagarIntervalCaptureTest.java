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

class ContasPagarIntervalCaptureTest {
    @TempDir Path directory;

    @Test
    void fivePhysicalRowsWithPerThreeAreRetainedBeforeTheActualEmptyArray() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var page = ContasPagarCaptureFixture.array();
            for (int index = 1; index <= 5; index++) {
                page.add(ContasPagarCaptureFixture.row(index));
            }
            fixture.port.pages.add(ContasPagarCaptureFixture.json(page));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 3, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(5, result.path("rows").intValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("pageDistinctRootCountEnforced").booleanValue());
            assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
            assertEquals(5, retained(fixture, result).size());
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void repeatedInstallmentsAcrossContiguousPagesNeverCauseOverlapStopOrDeduplication()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(7);
            final byte[] page =
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row, row));
            fixture.port.pages.add(page);
            fixture.port.pages.add(page);
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(3, 1, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(4, result.path("rows").intValue());
            final var lines = retained(fixture, result);
            assertEquals(List.of(lines.get(0), lines.get(0), lines.get(0), lines.get(0)), lines);
            assertFalse(result.path("pageOverlapStopsCapture").booleanValue());
            assertFalse(result.path("deduplicationPerformed").booleanValue());
            assertFalse(result.path("installmentSequenceUsedAsRootIdentity").booleanValue());
        }
    }

    @Test
    void nullInstallmentIsANonemptyOccurrenceAndNeverAnEmptyPageSentinel() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1);
            row.putNull("ant_ils_sequence_code");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 3, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertEquals(1, result.path("rows").intValue());
            assertEquals(2, fixture.port.pageCalls);
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertEquals(1, retained(fixture, result).size());
        }
    }

    @Test
    void aCandidateInstallmentIsNotRequiredToBeAPositiveLongRootIdentifier() throws Exception {
        for (final String mutation : List.of("zero", "fraction", "text", "overflow")) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                final var row = ContasPagarCaptureFixture.row(1);
                switch (mutation) {
                    case "zero" -> row.put("ant_ils_sequence_code", 0);
                    case "fraction" ->
                            row.put("ant_ils_sequence_code", new java.math.BigDecimal("1.25"));
                    case "text" -> row.put("ant_ils_sequence_code", "synthetic-installment");
                    default ->
                            row.put(
                                    "ant_ils_sequence_code",
                                    java.math.BigInteger.valueOf(Long.MAX_VALUE)
                                            .add(java.math.BigInteger.ONE));
                }
                fixture.port.pages.add(
                        ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
                fixture.port.pages.add(
                        ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
                final var result = fixture.capture(2, 1, 10);
                assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
                assertEquals(1, retained(fixture, result).size());
                assertFalse(result.path("sourceRootIdentityQualified").booleanValue());
                if ("text".equals(mutation)) {
                    assertFalse(result.path("rowTypesQualified").booleanValue());
                }
                if ("text".equals(mutation) || "fraction".equals(mutation)) {
                    assertFalse(result.path("mapperQualified").booleanValue());
                } else {
                    assertTrue(result.path("mapperQualified").booleanValue());
                }
            }
        }
    }

    @Test
    void numericLexemesStringsUnicodeWhitespaceAndTimestampFractionsSurviveLiteralRetention()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1);
            final String page =
                    ContasPagarCaptureFixture.array(row, row)
                            .toString()
                            .replace(
                                    "\"ant_ils_sequence_code\":1",
                                    "\"ant_ils_sequence_code\":1.2500");
            fixture.port.pages.add(page.getBytes(StandardCharsets.UTF_8));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            final var lines = retained(fixture, result);
            assertEquals(2, lines.size());
            assertEquals(lines.get(0), lines.get(1));
            assertTrue(lines.get(0).contains("\"ant_ils_sequence_code\":1.2500"));
            assertTrue(lines.get(0).contains("\"value\":\"12.3400\""));
            assertTrue(lines.get(0).contains("2026-10-13T12:34:56.123456789-03:00"));
            final var retainedRow =
                    ExportValidationSupport.JSON.readTree(lines.get(0)).required("record");
            assertEquals(28, retainedRow.size());
            assertEquals(
                    row.path("ant_crn_psn_nickname"), retainedRow.path("ant_crn_psn_nickname"));
            assertFalse(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
        }
    }

    @Test
    void mapperChunksDoNotCapPhysicalOccurrencesAtOneHundredOrRewriteTheirOrder() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var page = ContasPagarCaptureFixture.array();
            for (int index = 0; index < 101; index++) {
                page.add(ContasPagarCaptureFixture.row(1));
            }
            fixture.port.pages.add(ContasPagarCaptureFixture.json(page));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 1, 12000);
            assertEquals(101, result.path("rows").intValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertEquals(101, retained(fixture, result).size());
        }
    }

    @Test
    void issuedInTheWeekButCreatedBeforeItIsRetainedWithTheFullOriginalOffsetAndFraction()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row =
                    ContasPagarCaptureFixture.row(1)
                            .put("created_at", "2026-09-01T02:59:59.123456789Z")
                            .put("issue_date", "2026-10-12");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertFalse(result.path("createdAtScopeEnforced").booleanValue());
            assertTrue(result.path("createdAtValuePreserved").booleanValue());
            assertTrue(result.path("issueDateVerifiedForRetainedRows").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertEquals(
                    ContasPagarIndependentRequest.FILTER_SCOPE_POLICY,
                    result.path("filterScopePolicy").textValue());
            assertFalse(result.path("dateFilterSemanticsObserved").booleanValue());
            assertTrue(retained(fixture, result).get(0).contains("2026-09-01T02:59:59.123456789Z"));
        }
    }

    @Test
    void onlyIssueDateEnforcesTheWindowAndStrictCivilFormat() throws Exception {
        for (final String mutation : List.of("issueOutside", "issueInvalid", "issueWhitespace")) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                final var row = ContasPagarCaptureFixture.row(1);
                switch (mutation) {
                    case "issueOutside" -> row.put("issue_date", "2026-10-19");
                    case "issueInvalid" -> row.put("issue_date", "2026-02-30");
                    default -> row.put("issue_date", " 2026-10-13");
                }
                fixture.port.pages.add(
                        ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                assertFalse(fixture.journal.latest("CONTAS_A_PAGAR_PARTIAL").has("dataPin"));
                assertEquals(1, fixture.port.pageCalls);
            }
        }
    }

    @Test
    void transactionLiquidationCreatedDateAndCompetenceDoNotReplaceTheIssueDateScope()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row =
                    ContasPagarCaptureFixture.row(1)
                            .put("ant_ils_atn_transaction_date", "2026-11-01")
                            .put("ant_ils_atn_liquidation_date", "2026-11-02")
                            .put("competence_month", 11)
                            .put("created_at", "2027-01-01T00:00:00-03:00");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            assertEquals(
                    "CONTAS_A_PAGAR_SOURCE_COMPLETE",
                    fixture.capture(2, 100, 10).path("status").textValue());
        }
    }

    @Test
    void invalidCreatedAtIsRetainedAndQuarantinedByTheRealMapperWithoutInventingAScopeFailure()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row =
                    ContasPagarCaptureFixture.row(1)
                            .put("created_at", "authored-not-an-offset-timestamp");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertFalse(result.path("createdAtScopeEnforced").booleanValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertEquals(
                    "authored-not-an-offset-timestamp",
                    ExportValidationSupport.JSON
                            .readTree(retained(fixture, result).get(0))
                            .path("record")
                            .path("created_at")
                            .textValue());
        }
    }

    @Test
    void unobservedStringMonthIsRetainedWithWireRefusalAndSeparateCurrentMapperAcceptance()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var row = ContasPagarCaptureFixture.row(1).put("competence_month", "10");
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertFalse(result.path("rowTypesQualified").booleanValue());
            assertTrue(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("rowTypeRejectedOccurrences").intValue());
            assertEquals(
                    1,
                    result.path("rowTypeRefusalCounts").path("/competence_month:TYPE").intValue());
            assertEquals(
                    "10",
                    ExportValidationSupport.JSON
                            .readTree(retained(fixture, result).get(0))
                            .path("record")
                            .path("competence_month")
                            .textValue());
        }
    }

    @Test
    void mapperTextLimitRefusalNeverTruncatesRawOrStopsBeforeTheRealTerminal() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final String longText = "x".repeat(1025);
            final var row = ContasPagarCaptureFixture.row(1).put("comments", longText);
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(2, 100, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("rowTypesQualified").booleanValue());
            assertFalse(result.path("mapperQualified").booleanValue());
            assertEquals(1, result.path("mapperRejectedOccurrences").intValue());
            assertEquals(
                    longText,
                    ExportValidationSupport.JSON
                            .readTree(retained(fixture, result).get(0))
                            .path("record")
                            .path("comments")
                            .textValue());
        }
    }

    @Test
    void missingOrExtraFieldStopsButNewWireTypeOrNullIsRetainedAsUnqualified() throws Exception {
        for (final String mutation : List.of("missing", "extra", "type", "null")) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                final var row = ContasPagarCaptureFixture.row(1);
                switch (mutation) {
                    case "missing" -> row.remove("document");
                    case "extra" -> row.put("unqualified_extra", "synthetic");
                    case "type" -> row.put("document", true);
                    default -> row.putNull("document");
                }
                fixture.port.pages.add(
                        ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
                fixture.port.pages.add(
                        ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
                if ("missing".equals(mutation) || "extra".equals(mutation)) {
                    assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
                    assertEquals(
                            2,
                            fixture.journal
                                    .latest("CONTAS_A_PAGAR_PARTIAL")
                                    .path("responses")
                                    .size());
                } else {
                    final var result = fixture.capture(2, 100, 10);
                    assertEquals(
                            "CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
                    assertFalse(result.path("rowTypesQualified").booleanValue());
                    assertEquals(1, retained(fixture, result).size());
                }
            }
        }
    }

    @Test
    void durableReopenChecksRetainedBytesAndDoesNotRefreshAnyHttpBudget() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, true)) {
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(
                            ContasPagarCaptureFixture.array(ContasPagarCaptureFixture.row(1))));
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var first = fixture.capture(2, 1, 10);
            final var payload = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] bytes = Files.readAllBytes(payload.path());
            fixture.reopen();
            fixture.clock.value = ContasPagarCaptureFixture.NOW.plusSeconds(500);
            assertEquals(first, fixture.capture(2, 1, 10));
            assertArrayEquals(bytes, Files.readAllBytes(payload.path()));
            assertEquals(1, fixture.port.infoCalls);
            assertEquals(2, fixture.port.pageCalls);
        }
    }

    @Test
    void emptyFirstArrayIsExplicitAndDoesNotInventANonemptyPayloadPin() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("CONTAS_A_PAGAR_SOURCE_COMPLETE", result.path("status").textValue());
            assertTrue(result.path("emptyInterval").booleanValue());
            assertFalse(result.has("dataPin"));
            assertEquals(0, result.path("rows").intValue());
            assertEquals(2, result.path("responses").size());
        }
    }

    @Test
    void aShortNonemptyPageIsPartialAtTheCapAndNeverASyntheticTerminal() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(
                            ContasPagarCaptureFixture.array(ContasPagarCaptureFixture.row(1))));
            final var result = fixture.capture(1, 100, 10);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", result.path("status").textValue());
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.has("partialDataPin"));
            assertFalse(result.path("payloadPromoted").booleanValue());
        }
    }

    @Test
    void objectErrorsAndTrailingJsonCannotBecomeAnEmptyTerminal() throws Exception {
        for (final String body : List.of("{\"data\":[]}", "{\"error\":\"synthetic\"}", "[] {}")) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                fixture.port.pages.add(body.getBytes(StandardCharsets.UTF_8));
                assertThrows(IllegalArgumentException.class, () -> fixture.capture(1, 100, 10));
                assertFalse(
                        fixture.journal
                                .latest("CONTAS_A_PAGAR_PARTIAL")
                                .path("emptyInterval")
                                .asBoolean());
                assertEquals(1, fixture.port.pageCalls);
            }
        }
    }

    @Test
    void infoDriftOr429StopsWithoutRetryOrPayloadPromotion() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var info = ContasPagarCaptureFixture.info();
            ((com.fasterxml.jackson.databind.node.ObjectNode) info.path("fields"))
                    .remove("document");
            fixture.port.infoBytes = ContasPagarCaptureFixture.json(info);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(0, fixture.port.pageCalls);
        }
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            fixture.port.status = 429;
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 10));
            assertEquals(1, fixture.port.pageCalls);
            assertFalse(fixture.journal.latest("CONTAS_A_PAGAR_PARTIAL").has("dataPin"));
        }
    }

    @Test
    void finitePagePerRowAndSourceTimeCapsCannotBeExpanded() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(17, 100, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 101, 12000));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 100, 12001));
            fixture.deadline = fixture.clock.instant().plusSeconds(211);
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(16, 100, 12000));
            assertEquals(0, fixture.port.infoCalls);
            assertEquals(0, fixture.port.pageCalls);
        }
    }

    @Test
    void crossingThePhysicalRowCapRetainsThePrefixAndTheCompleteObservedBodyAsPartial()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            fixture.port.pages.add(
                    ContasPagarCaptureFixture.json(
                            ContasPagarCaptureFixture.array(
                                    ContasPagarCaptureFixture.row(1),
                                    ContasPagarCaptureFixture.row(2))));
            assertThrows(IllegalArgumentException.class, () -> fixture.capture(2, 100, 1));
            final var partial = fixture.journal.latest("CONTAS_A_PAGAR_PARTIAL");
            assertEquals(1, partial.path("rows").intValue());
            assertTrue(partial.has("partialDataPin"));
            assertEquals(2, partial.path("responses").size());
            assertFalse(partial.path("terminalEmptyPageObserved").booleanValue());
        }
    }

    private static List<String> retained(
            final ContasPagarCaptureFixture fixture,
            final com.fasterxml.jackson.databind.JsonNode result)
            throws Exception {
        return Files.readAllLines(
                ExportValidationSupport.Pin.from(result.required("dataPin")).path());
    }
}
