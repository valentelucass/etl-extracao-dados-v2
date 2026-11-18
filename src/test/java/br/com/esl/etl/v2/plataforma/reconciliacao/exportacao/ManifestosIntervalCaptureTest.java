package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosIntervalCaptureTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final String SELECTION = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    @TempDir Path directory;

    @Test
    void completeExpandedPageRetainsEveryRawOccurrenceAndOnlyPromotesAfterEmptyEof()
            throws Exception {
        try (var context = new Context(true)) {
            final var rows = ExportValidationSupport.JSON.createArrayNode();
            final var row = row(7).put("mft_aoe_comments", "raw ação: repeated");
            for (int occurrence = 0; occurrence < 105; occurrence++) {
                rows.add(row);
            }
            context.port.pages.add(json(rows));
            context.port.pages.add(json(empty()));
            context.port.beforePage =
                    page -> {
                        assertFalse(Files.exists(context.completePath()));
                        assertEquals(page, context.journal.count("MANIFESTOS_PAGE_RESERVED"));
                    };
            final var result = context.run(2, 1, 106);
            assertEquals("MANIFESTOS_SOURCE_COMPLETE", result.path("status").asText());
            assertEquals(105, result.path("rows").intValue());
            assertEquals(2, result.path("pages").intValue());
            assertEquals(3, result.path("httpRequestsReserved").intValue());
            assertEquals(3, result.path("responses").size());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertTrue(result.path("payloadPromoted").booleanValue());
            assertFalse(result.path("deduplicationPerformed").booleanValue());
            assertFalse(result.path("contentQualified").booleanValue());
            assertFalse(result.path("serviceDateVerifiedFromRows").booleanValue());
            assertFalse(Files.exists(context.partialPath()));
            assertTrue(Files.exists(context.completePath()));
            final var pin = ExportValidationSupport.Pin.from(result.required("dataPin"));
            final var lines = Files.readAllLines(pin.path(), StandardCharsets.UTF_8);
            assertEquals(105, lines.size());
            for (final String line : lines) {
                final var envelope = ExportValidationSupport.JSON.readTree(line);
                assertEquals("MANIFESTOS", envelope.path("entity").asText());
                assertEquals(6399, envelope.path("templateId").intValue());
                assertEquals(91, envelope.path("record").size());
                assertEquals(row, envelope.path("record"));
            }
            assertEquals(
                    pin.sha256(), ExportValidationSupport.sha256(Files.readAllBytes(pin.path())));
            assertEquals(1, context.journal.count("MANIFESTOS_COMPLETE"));
        }
    }

    @Test
    void completedDurableJournalReopensAfterDeadlineWithoutCallingSourceAgain() throws Exception {
        try (var context = new Context(true)) {
            context.port.pages.add(json(array(row(1))));
            context.port.pages.add(json(empty()));
            final var first = context.run(2, 1, 10);
            final var pin = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] payload = Files.readAllBytes(pin.path());
            final var resultPin = context.journal.latestPin("MANIFESTOS_COMPLETE");
            final byte[] resultBytes = Files.readAllBytes(resultPin.path());
            context.reopen();
            context.clock.value = NOW.plusSeconds(500);
            final var reopened = context.run(2, 1, 10);
            assertEquals(first, reopened);
            assertEquals(first.path("rows").getClass(), reopened.path("rows").getClass());
            assertEquals(resultPin, context.journal.latestPin("MANIFESTOS_COMPLETE"));
            assertArrayEquals(payload, Files.readAllBytes(pin.path()));
            assertArrayEquals(resultBytes, Files.readAllBytes(resultPin.path()));
            assertEquals(1, context.port.infoCalls);
            assertEquals(2, context.port.pageCalls);
        }
    }

    @Test
    void finishedAtNullRetainsLiteralRawAndReopensDurableJournalWithoutSourceCalls()
            throws Exception {
        try (var context = new Context(true)) {
            JsonNode historical = null;
            for (final var field : contract().path("response").required("fields")) {
                if ("/finished_at".equals(field.path("path").asText())) {
                    historical = field;
                }
            }
            assertTrue(historical != null);
            assertFalse(historical.required("nullable").booleanValue());
            final var unfinished =
                    row(1).putNull("finished_at")
                            .put("status", "in_transit")
                            .put("mft_cat_cot_status", "pending")
                            .put("mft_mdr_contract_type", "");
            final var finished = row(2).put("finished_at", "2026-10-13T18:25:00-03:00");
            context.port.pages.add(json(array(unfinished, finished)));
            context.port.pages.add(json(empty()));
            final var first = context.run(2, 2, 10);
            assertEquals("MANIFESTOS_SOURCE_COMPLETE", first.required("status").textValue());
            assertEquals(2, first.required("rows").intValue());
            assertTrue(first.required("terminalEmptyPageObserved").booleanValue());
            assertFalse(first.required("contentQualified").booleanValue());
            final var nullPaths = new java.util.HashSet<String>();
            first.required("additionalRawNullPaths")
                    .forEach(path -> nullPaths.add(path.textValue()));
            assertEquals(java.util.Set.of("/finished_at", "/closed_at"), nullPaths);
            assertEquals(
                    "595156699c94be522727198dbaed75b163b492b0360dc699a956bc13b749758c",
                    first.required("rawNullObservationSha256").textValue());
            final var pin = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] payload = Files.readAllBytes(pin.path());
            final var lines = Files.readAllLines(pin.path(), StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("\"finished_at\":null"));
            final var retained =
                    ExportValidationSupport.JSON.readTree(lines.get(0)).required("record");
            assertEquals(91, retained.size());
            assertTrue(retained.has("finished_at"));
            assertTrue(retained.get("finished_at").isNull());
            assertEquals(unfinished, retained);
            assertEquals(
                    finished,
                    ExportValidationSupport.JSON.readTree(lines.get(1)).required("record"));
            final var resultPin = context.journal.latestPin("MANIFESTOS_COMPLETE");
            context.reopen();
            context.clock.value = NOW.plusSeconds(500);
            assertEquals(first, context.run(2, 2, 10));
            assertEquals(resultPin, context.journal.latestPin("MANIFESTOS_COMPLETE"));
            assertArrayEquals(payload, Files.readAllBytes(pin.path()));
            assertEquals(1, context.port.infoCalls);
            assertEquals(2, context.port.pageCalls);
        }
    }

    @Test
    void newFinishedAtNullQualificationDoesNotAdmitNullCreatedAtOrSequenceCode() throws Exception {
        for (final String field : List.of("created_at", "sequence_code")) {
            try (var context = new Context(false)) {
                context.port.pages.add(json(array(row(1).putNull("finished_at").putNull(field))));
                context.port.pages.add(json(empty()));
                assertEquals(
                        "MANIFESTOS_NULL_TYPE_NOT_QUALIFIED",
                        assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                                .getMessage());
                assertEquals(
                        0, context.journal.latest("MANIFESTOS_PARTIAL").path("rows").intValue());
                assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
                assertFalse(Files.exists(context.completePath()));
                assertEquals(1, context.port.pageCalls);
            }
        }
    }

    @Test
    void closedAtNullRetainsRawAndHistoricalInfoAndReopensWithoutAnyNewSourceCall()
            throws Exception {
        try (var context = new Context(true)) {
            final var original = row(1).putNull("closed_at");
            context.port.pages.add(json(array(original)));
            context.port.pages.add(json(empty()));
            final var first = context.run(2, 1, 10);
            assertEquals(
                    "MANIFESTOS_RAW_CLOSED_AT_NULL_0747_V1",
                    first.path("closedAtRawNullQualification").textValue());
            assertEquals(
                    "6eb8efe6808633a0f59bddb7690610b901d3c1d25c7907cf18e7f242b592f510",
                    first.path("closedAtRawNullObservationSha256").textValue());
            for (final var field : contract().path("response").required("fields")) {
                if ("/closed_at".equals(field.path("path").asText())) {
                    assertFalse(field.path("nullable").booleanValue());
                }
            }
            final var payload = ExportValidationSupport.Pin.from(first.required("dataPin"));
            final byte[] before = Files.readAllBytes(payload.path());
            assertEquals(
                    original,
                    ExportValidationSupport.JSON
                            .readTree(Files.readAllLines(payload.path()).get(0))
                            .required("record"));
            context.reopen();
            context.clock.value = NOW.plusSeconds(500);
            assertEquals(first, context.run(2, 1, 10));
            assertArrayEquals(before, Files.readAllBytes(payload.path()));
            assertEquals(1, context.port.infoCalls);
            assertEquals(2, context.port.pageCalls);
        }
    }

    @Test
    void closedAtNullDoesNotMakeItOptionalOrQualifyCreatedAtNull() throws Exception {
        for (final boolean missing : List.of(true, false)) {
            try (var context = new Context(false)) {
                final var record = row(1).putNull("closed_at");
                if (missing) {
                    record.remove("closed_at");
                } else {
                    record.putNull("created_at");
                }
                context.port.pages.add(json(array(record)));
                context.port.pages.add(json(empty()));
                assertEquals(
                        missing
                                ? "MANIFESTOS_EXACT_91_ROW_FIELDS"
                                : "MANIFESTOS_NULL_TYPE_NOT_QUALIFIED",
                        assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                                .getMessage());
                assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
                assertEquals(1, context.port.pageCalls);
            }
        }
    }

    @Test
    void qualifiedNullDoesNotMakeFinishedAtAnOptionalField() throws Exception {
        try (var context = new Context(false)) {
            final var missing = row(1);
            missing.remove("finished_at");
            context.port.pages.add(json(array(missing)));
            context.port.pages.add(json(empty()));
            assertEquals(
                    "MANIFESTOS_EXACT_91_ROW_FIELDS",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            final var partial = context.journal.latest("MANIFESTOS_PARTIAL");
            final var response =
                    ExportValidationSupport.Pin.from(
                            partial.path("responses").get(1).required("rawBodyPin"));
            assertFalse(
                    ExportValidationSupport.JSON
                            .readTree(Files.readAllBytes(response.path()))
                            .get(0)
                            .has("finished_at"));
            assertFalse(partial.has("dataPin"));
            assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
            assertEquals(1, context.port.pageCalls);
        }
    }

    @Test
    void pageCapRetainsPartialAndDurableReopenDoesNotRefreshTheBudget() throws Exception {
        try (var context = new Context(true)) {
            context.port.pages.add(json(array(row(1))));
            final var first = context.run(1, 1, 10);
            assertEquals("PARTIAL_PAGE_BUDGET_EXHAUSTED", first.path("status").asText());
            assertFalse(first.has("dataPin"));
            assertTrue(first.has("partialDataPin"));
            assertFalse(first.path("payloadPromoted").booleanValue());
            assertFalse(first.path("terminalEmptyPageObserved").booleanValue());
            assertFalse(Files.exists(context.completePath()));
            context.reopen();
            assertEquals(first, context.run(1, 1, 10));
            assertEquals(1, context.port.infoCalls);
            assertEquals(1, context.port.pageCalls);
            assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10));
            assertEquals(1, context.port.pageCalls);
        }
    }

    @Test
    void overlapRetainsAllReceivedRowsAndNeverRequestsTheWaitingEmptyPage() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(json(array(row(1))));
            context.port.pages.add(json(array(row(1), row(2))));
            context.port.pages.add(json(empty()));
            final var result = context.run(3, 2, 10);
            assertEquals(
                    "PARTIAL_PAGE_ID_OVERLAP_RECONCILIATION_REQUIRED",
                    result.path("status").asText());
            assertEquals(3, result.path("rows").intValue());
            assertEquals(2, context.port.pageCalls);
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertEquals(
                    3,
                    Files.readAllLines(
                                    ExportValidationSupport.Pin.from(
                                                    result.required("partialDataPin"))
                                            .path())
                            .size());
            assertEquals(1, context.journal.count("MANIFESTOS_PAGE_OVERLAP"));
            assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
        }
    }

    @Test
    void perCountsIntegralSequenceCodesRatherThanPhysicalRowsOrTextAliases() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(
                    json(array(row(1), row(1).put("sequence_code", new BigDecimal("1.00")))));
            context.port.pages.add(json(empty()));
            final var result = context.run(2, 1, 10);
            assertEquals(2, result.path("rows").intValue());
            assertTrue(result.path("payloadPromoted").booleanValue());
        }
        try (var context = new Context(false)) {
            context.port.pages.add(json(array(row(1), row(2))));
            assertEquals(
                    "MANIFESTOS_PAGE_DISTINCT_SEQUENCE_LIMIT",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals(1, context.port.pageCalls);
            assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
        }
    }

    @Test
    void fractionalNullStringBooleanAndCompositeKeysAreRefused() throws Exception {
        for (final String value : List.of("null", "\"1\"", "true", "{}", "[]", "1.5")) {
            try (var context = new Context(false)) {
                final var row = row(1);
                row.set("sequence_code", ExportValidationSupport.JSON.readTree(value));
                context.port.pages.add(json(array(row)));
                assertThrows(Exception.class, () -> context.run(2, 1, 10));
                assertEquals(1, context.port.pageCalls);
                assertEquals(1, context.journal.count("MANIFESTOS_PAGE_OBSERVED"));
                assertFalse(Files.exists(context.completePath()));
            }
        }
    }

    @Test
    void physicalRowCapKeepsTheFirstOccurrenceAndTheCompleteRawResponse() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(json(array(row(1), row(1))));
            assertEquals(
                    "MANIFESTOS_ROWS_LIMIT_PARTIAL",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 1))
                            .getMessage());
            final var result = context.journal.latest("MANIFESTOS_PARTIAL");
            assertEquals(1, result.path("rows").intValue());
            assertEquals(
                    1,
                    Files.readAllLines(
                                    ExportValidationSupport.Pin.from(
                                                    result.required("partialDataPin"))
                                            .path())
                            .size());
            final var raw =
                    ExportValidationSupport.Pin.from(
                            result.path("responses").get(1).required("rawBodyPin"));
            assertEquals(
                    2,
                    ExportValidationSupport.JSON.readTree(Files.readAllBytes(raw.path())).size());
            assertEquals(1, context.port.pageCalls);
        }
    }

    @Test
    void emptyFirstPageIsExplicitAndCannotInventAConsumableNonemptyPin() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(json(empty()));
            final var result = context.run(1, 1, 10);
            assertEquals("MANIFESTOS_SOURCE_COMPLETE", result.path("status").asText());
            assertTrue(result.path("emptyInterval").booleanValue());
            assertTrue(result.path("terminalEmptyPageObserved").booleanValue());
            assertEquals(0, Files.size(context.completePath()));
            assertFalse(result.has("dataPin"));
            assertFalse(result.path("contentQualified").booleanValue());
        }
    }

    @Test
    void everyInfoFieldAndEveryQualifiedFilterIsRequiredWithoutUnknownAdditions() throws Exception {
        for (final String change :
                List.of(
                        "missingField",
                        "extraField",
                        "missingFilter",
                        "unqualifiedFilter",
                        "duplicate")) {
            try (var context = new Context(false)) {
                final var info = info();
                switch (change) {
                    case "missingField" -> info.withArray("fields").remove(90);
                    case "extraField" -> info.withArray("fields").add("unknown");
                    case "missingFilter" -> info.withArray("filters").remove(17);
                    case "unqualifiedFilter" ->
                            info.withArray("filters")
                                    .set(
                                            0,
                                            ExportValidationSupport.JSON
                                                    .getNodeFactory()
                                                    .textNode("service_date"));
                    default -> info.withArray("fields").add(info.path("fields").get(0));
                }
                context.port.infoBody = json(info);
                assertThrows(Exception.class, () -> context.run(2, 1, 10));
                assertEquals(1, context.port.infoCalls);
                assertEquals(0, context.port.pageCalls);
                assertEquals(1, context.journal.count("MANIFESTOS_INFO_OBSERVED"));
                assertEquals(1, context.journal.count("MANIFESTOS_PARTIAL"));
            }
        }
    }

    @Test
    void all91FieldsAreRequiredAndNoUnknownFieldMayReplaceOne() throws Exception {
        for (final String change : List.of("missing", "extra", "replacement")) {
            try (var context = new Context(false)) {
                final var row = row(1);
                if (!"extra".equals(change)) {
                    row.remove("mft_fis_fit_mey_mobile_receipt");
                }
                if (!"missing".equals(change)) {
                    row.put("unknown_field", "preserved only in the raw response");
                }
                context.port.pages.add(json(array(row)));
                assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10));
                assertEquals(1, context.port.pageCalls);
                assertFalse(Files.exists(context.completePath()));
            }
        }
    }

    @Test
    void booleanNumericStringNullOnlyAndArrayChildTypesFollowThePinned91Shape() throws Exception {
        for (final String[] change :
                new String[][] {
                    {"manual_km", "\"false\""},
                    {"km", "0.1"},
                    {"sequence_code", "null"},
                    {"mft_iks_id", "1"},
                    {"mft_crn_psn_nickname", "{}"},
                    {"mft_mte_delivery_region_names", "[\"flat\"]"},
                    {"mft_mte_delivery_region_names", "[[1]]"},
                    {"mft_mte_delivery_region_names", "[[[\"deep\"]]]"}
                }) {
            try (var context = new Context(false)) {
                final var row = row(1);
                row.set(change[0], ExportValidationSupport.JSON.readTree(change[1]));
                context.port.pages.add(json(array(row)));
                assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10));
                assertEquals(
                        0, context.journal.latest("MANIFESTOS_PARTIAL").path("rows").intValue());
            }
        }
    }

    @Test
    void prettyRecordsBecomeSingleJsonlLinesWithoutChangingLexemesEscapesOrUtf8() throws Exception {
        try (var context = new Context(false)) {
            final var row = row(1).put("mft_aoe_comments", "raw ação \"quoted\" \\ : \n");
            final String pretty =
                    ExportValidationSupport.JSON
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(array(row))
                            .replaceAll(
                                    "\"sequence_code\"\\s*:\\s*1(?=\\s*[,}])",
                                    "\"sequence_code\" : 1.00");
            assertTrue(pretty.contains("1.00"));
            context.port.pages.add(pretty.getBytes(StandardCharsets.UTF_8));
            context.port.pages.add(json(empty()));
            final var result = context.run(2, 1, 10);
            final String line =
                    Files.readString(
                            ExportValidationSupport.Pin.from(result.required("dataPin")).path());
            assertEquals(1, line.lines().count());
            assertTrue(line.contains("\"sequence_code\":1.00"));
            assertTrue(line.contains("ação"));
            assertEquals(
                    row.path("mft_aoe_comments"),
                    ExportValidationSupport.JSON
                            .readTree(line)
                            .path("record")
                            .path("mft_aoe_comments"));
        }
    }

    @Test
    void malformedOrTrailingJsonCannotTurnAnEmptyPrefixIntoTerminalEof() throws Exception {
        for (final String bytes : List.of("[] {}", "[", "{}", "null", "[null]", "[]\nfalse")) {
            try (var context = new Context(false)) {
                context.port.pages.add(bytes.getBytes(StandardCharsets.UTF_8));
                assertThrows(Exception.class, () -> context.run(2, 1, 10));
                assertFalse(Files.exists(context.completePath()));
                assertFalse(
                        context.journal
                                .latest("MANIFESTOS_PARTIAL")
                                .path("terminalEmptyPageObserved")
                                .booleanValue());
                assertEquals(1, context.port.pageCalls);
            }
        }
    }

    @Test
    void duplicateJsonMemberAndMalformedUtf8KeepTheRawResponseAndNeverPromote() throws Exception {
        for (final boolean duplicate : new boolean[] {true, false}) {
            try (var context = new Context(false)) {
                final String normal = new String(json(array(row(1))), StandardCharsets.UTF_8);
                final byte[] bytes =
                        duplicate
                                ? normal.replace(
                                                "\"sequence_code\":1",
                                                "\"sequence_code\":1,\"sequence_code\":1")
                                        .getBytes(StandardCharsets.UTF_8)
                                : json(array(row(1)));
                if (!duplicate) {
                    final int index = new String(bytes, StandardCharsets.UTF_8).indexOf("raw-");
                    assertTrue(index >= 0);
                    bytes[index] = (byte) 0xff;
                }
                context.port.pages.add(bytes);
                assertThrows(IOException.class, () -> context.run(2, 1, 10));
                assertEquals(
                        2, context.journal.latest("MANIFESTOS_PARTIAL").path("responses").size());
                assertFalse(Files.exists(context.completePath()));
            }
        }
    }

    @Test
    void non200InfoAndDataStopWithoutRetryAndCanBeInspectedWithoutAnotherCall() throws Exception {
        for (final boolean info : new boolean[] {true, false}) {
            try (var context = new Context(false)) {
                context.port.pages.add(json(empty()));
                context.port.infoStatus = info ? 429 : 200;
                context.port.pageStatus = info ? 200 : 500;
                assertEquals(
                        "MANIFESTOS_STOP_NON_200_NO_RETRY",
                        assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                                .getMessage());
                final var partial = context.journal.latest("MANIFESTOS_PARTIAL");
                context.reopen();
                assertEquals(partial, context.run(2, 1, 10));
                assertEquals(1, context.port.infoCalls);
                assertEquals(info ? 0 : 1, context.port.pageCalls);
            }
        }
    }

    @Test
    void uncertainTransportReservationIsDurableAndNeverAutomaticallyRepeated() throws Exception {
        try (var context = new Context(false)) {
            context.port.infoFailure =
                    new IOException("private response detail must not enter status");
            assertThrows(IOException.class, () -> context.run(2, 1, 10));
            final var result = context.journal.latest("MANIFESTOS_PARTIAL");
            assertEquals(
                    "PARTIAL_CAPTURE_FAILED_RECONCILIATION_REQUIRED",
                    result.path("status").asText());
            assertEquals(1, context.journal.count("MANIFESTOS_INFO_RESERVED"));
            assertEquals(0, context.journal.count("MANIFESTOS_INFO_OBSERVED"));
            context.reopen();
            assertEquals(result, context.run(2, 1, 10));
            assertEquals(1, context.port.infoCalls);
        }
    }

    @Test
    void infoAndDataResponsePinDriftPreventPublicationAndNoNewSourceIsUsedToRepairIt()
            throws Exception {
        for (final int pinIndex : new int[] {0, 1}) {
            try (var context = new Context(false)) {
                context.port.pages.add(json(array(row(1))));
                context.port.pages.add(json(empty()));
                context.port.beforePage =
                        page -> {
                            if (page == 2) {
                                Files.write(context.port.pins.get(pinIndex).path(), new byte[] {0});
                            }
                        };
                assertEquals(
                        "EXPORT_PIN_CHANGED",
                        assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                                .getMessage());
                assertFalse(Files.exists(context.completePath()));
                assertEquals(0, context.journal.count("MANIFESTOS_COMPLETE"));
                context.reopen();
                assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10));
                assertEquals(1, context.port.infoCalls);
                assertEquals(2, context.port.pageCalls);
            }
        }
    }

    @Test
    void payloadTamperingAfterCompletionIsDetectedOnJournalReopenWithoutAnyCall() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(json(array(row(1))));
            context.port.pages.add(json(empty()));
            context.run(2, 1, 10);
            Files.write(context.completePath(), new byte[] {0});
            context.reopen();
            assertEquals(
                    "MANIFESTOS_RETAINED_PAYLOAD_CHANGED",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals(1, context.port.infoCalls);
            assertEquals(2, context.port.pageCalls);
        }
    }

    @Test
    void aDifferentRequestCannotReuseOrOverwriteACompletedJournal() throws Exception {
        try (var context = new Context(false)) {
            context.port.pages.add(json(empty()));
            context.run(1, 1, 10);
            context.request.put("periodStart", "2026-10-19").put("periodEnd", "2026-10-25");
            ((ObjectNode) context.request.required("filters"))
                    .put(
                            ManifestosIndependentRequest.SERVICE_DATE_FILTER,
                            "2026-10-19 - 2026-10-25");
            assertEquals(
                    "MANIFESTOS_CAPTURE_REOPEN_BINDING",
                    assertThrows(IllegalArgumentException.class, () -> context.run(1, 1, 10))
                            .getMessage());
            assertEquals(1, context.port.infoCalls);
            assertEquals(1, context.port.pageCalls);
        }
    }

    @Test
    void unclosedStartedJournalRequiresReconciliationWithoutInfoOrData() throws Exception {
        try (var context = new Context(false)) {
            context.journal.record(
                    "MANIFESTOS_STARTED",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("attemptId", context.request.path("attemptId").asText())
                            .put(
                                    "requestSha256",
                                    ExportValidationSupport.sha256(
                                            ExportValidationSupport.canonicalBytes(
                                                    context.request)))
                            .put("output", context.root.toString())
                            .put("maximumPages", 2)
                            .put("per", 1)
                            .put("maximumRows", 10));
            assertEquals(
                    "MANIFESTOS_UNCLOSED_CAPTURE_RECONCILIATION_REQUIRED",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals(0, context.port.infoCalls);
            assertEquals(0, context.port.pageCalls);
        }
    }

    @Test
    void finiteCapsRejectBeforeSourceAndSixteenDataCallsExhaustAtExactlySeventeenRequests()
            throws Exception {
        for (final int[] bounds :
                new int[][] {
                    {0, 1, 10}, {17, 1, 10}, {2, 0, 10}, {2, 101, 10}, {2, 1, 0}, {2, 1, 12001}
                }) {
            try (var context = new Context(false)) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.run(bounds[0], bounds[1], bounds[2]));
                assertEquals(0, context.port.infoCalls);
                assertEquals(0, context.journal.count("MANIFESTOS_STARTED"));
            }
        }
        try (var context = new Context(false)) {
            for (int page = 1; page <= 16; page++) {
                context.port.pages.add(json(array(row(page))));
            }
            context.port.pages.add(json(empty()));
            final var result = context.run(16, 1, 100);
            assertEquals(17, result.path("httpRequestsReserved").intValue());
            assertEquals(16, context.port.pageCalls);
            assertFalse(result.path("terminalEmptyPageObserved").booleanValue());
            assertFalse(Files.exists(context.completePath()));
        }
    }

    @Test
    void sourceWindowAbove210SecondsAndExpiredResponsesCannotCreateAnotherRequest()
            throws Exception {
        try (var context = new Context(false)) {
            context.deadline = NOW.plusSeconds(211);
            assertEquals(
                    "MANIFESTOS_CAPTURE_SOURCE_210_SECONDS",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals(0, context.port.infoCalls);
        }
        try (var context = new Context(false)) {
            context.port.pages.add(json(array(row(1))));
            context.port.pages.add(json(empty()));
            context.port.beforePage = page -> context.clock.value = NOW.plusSeconds(121);
            assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10));
            assertEquals(1, context.port.pageCalls);
            assertFalse(Files.exists(context.completePath()));
        }
    }

    @Test
    void staleResponseCompletionCannotBePresentedAsANewCapture() throws Exception {
        try (var context = new Context(false)) {
            context.port.completedAt = NOW.minusSeconds(1);
            assertEquals(
                    "MANIFESTOS_WIRE_COMPLETION_WINDOW",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals(0, context.port.pageCalls);
            assertEquals(1, context.journal.count("MANIFESTOS_INFO_OBSERVED"));
        }
    }

    @Test
    void retainedJsonlCannotGrowPastEightMiBEvenWhenEveryResponseFitsItsOwnLimit()
            throws Exception {
        try (var context = new Context(false)) {
            for (int page = 1; page <= 4; page++) {
                final var rows = empty();
                final var row = row(page).put("mft_aoe_comments", "x".repeat(32768));
                for (int index = 0; index < 80; index++) {
                    rows.add(row);
                }
                final byte[] bytes = json(rows);
                assertTrue(bytes.length < 8388608);
                context.port.pages.add(bytes);
            }
            context.port.pages.add(json(empty()));
            assertEquals(
                    "MANIFESTOS_RETAINED_PAYLOAD_8MIB_BOUND",
                    assertThrows(IllegalArgumentException.class, () -> context.run(5, 1, 1000))
                            .getMessage());
            assertTrue(Files.size(context.partialPath()) <= 8388608);
            assertTrue(context.port.pageCalls >= 3 && context.port.pageCalls <= 4);
            assertFalse(Files.exists(context.completePath()));
        }
    }

    @Test
    void preexistingPayloadIsNeverOverwrittenOrUsedToRestartSource() throws Exception {
        try (var context = new Context(false)) {
            Files.writeString(context.partialPath(), "retained-marker");
            assertEquals(
                    "MANIFESTOS_FRESH_RETAINED_PAYLOAD",
                    assertThrows(IllegalArgumentException.class, () -> context.run(2, 1, 10))
                            .getMessage());
            assertEquals("retained-marker", Files.readString(context.partialPath()));
            assertEquals(0, context.port.infoCalls);
        }
    }

    @Test
    void capturedEnvelopeFeedsThe91ColumnAdapterWithoutAny31ColumnProjection() throws Exception {
        try (var context = new Context(false)) {
            final var fixture = new ManifestosContentFixture(context.root, 1);
            fixture.records.get(0).putNull("mft_iks_id");
            fixture.nativeRows.get(0).remove(27);
            context.port.pages.add(json(array(fixture.records.get(0))));
            context.port.pages.add(json(empty()));
            final var captured = context.run(2, 1, 10);
            final var workbook = fixture.pin("native.xlsx", fixture.workbookBytes());
            final var compared =
                    ManifestosContentAdapter.compare(
                            context.request,
                            ExportValidationSupport.Pin.from(captured.required("dataPin")),
                            workbook,
                            ManifestosContentFixture.budget());
            assertEquals(91, compared.path("columns").intValue());
            assertEquals(50, compared.path("totalColumns").intValue());
            assertTrue(compared.path("contentEqualUnderCandidatePresentation").booleanValue());
        }
    }

    private static ObjectNode request() {
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("entity", "MANIFESTOS")
                        .put("reportId", 6399)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", "2026-10-12")
                        .put("periodEnd", "2026-10-18")
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        ManifestosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "2026-10-12 - 2026-10-18");
        return request;
    }

    private static JsonNode contract() throws Exception {
        try (var input =
                ManifestosIntervalCaptureTest.class.getResourceAsStream(
                        "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json")) {
            return ExportValidationSupport.JSON.readTree(input);
        }
    }

    private static ObjectNode info() throws Exception {
        final var info = ExportValidationSupport.JSON.createObjectNode();
        final var fields = info.putArray("fields");
        final var filters = info.putArray("filters");
        for (final var metadata : contract().required("metadata")) {
            if ("DATA_FIELD".equals(metadata.path("kind").asText())) {
                fields.add(metadata.path("path").textValue());
            } else {
                final String[] path = metadata.path("path").asText().split("\\.", -1);
                filters.addObject().put("table", path[0]).put("field", path[1]);
            }
        }
        return info;
    }

    private static ObjectNode row(final int id) throws Exception {
        final var row = ExportValidationSupport.JSON.createObjectNode();
        for (final var field : contract().path("response").required("fields")) {
            final String path = field.path("path").asText();
            if (path.indexOf('/', 1) >= 0) {
                continue;
            }
            final String name = path.substring(1);
            final var types = field.required("jsonTypes");
            if (types.isEmpty()) {
                row.putNull(name);
            } else {
                switch (types.get(0).asText()) {
                    case "STRING" -> row.put(name, "raw-" + name);
                    case "BOOLEAN" -> row.put(name, false);
                    case "INTEGER", "NUMBER" -> row.put(name, 7);
                    case "ARRAY" -> row.putArray(name).addArray().add("Alpha").add("Alpha");
                    default -> throw new IllegalStateException("FIXTURE_UNKNOWN_TYPE");
                }
            }
        }
        row.put("sequence_code", id);
        return row;
    }

    private static ArrayNode empty() {
        return ExportValidationSupport.JSON.createArrayNode();
    }

    private static ArrayNode array(final JsonNode... rows) {
        final var array = empty();
        for (final var row : rows) {
            array.add(row);
        }
        return array;
    }

    private static byte[] json(final JsonNode value) throws Exception {
        return ExportValidationSupport.JSON.writeValueAsBytes(value);
    }

    private static final class MutableClock extends Clock {
        private Instant value = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return Clock.fixed(value, zone);
        }

        @Override
        public Instant instant() {
            return value;
        }
    }

    /** Durability cases use public factories; other fault cases use this synthetic event chain. */
    private static final class Events implements PersistentExportJournal.EventChain {
        private final List<CampaignJournal.Event> events = new ArrayList<>();

        @Override
        public CampaignJournal.Event append(
                final String caseId,
                final UUID nonce,
                final CampaignJournal.Kind kind,
                final String detail) {
            final String previous =
                    events.isEmpty() ? "0".repeat(64) : events.get(events.size() - 1).sha256();
            final int sequence = events.size() + 1;
            final String sha =
                    ExportValidationSupport.sha256(
                            (sequence + "|" + caseId + "|" + nonce + "|" + kind + "|" + detail + "|"
                                            + previous)
                                    .getBytes(StandardCharsets.UTF_8));
            final var event =
                    new CampaignJournal.Event(sequence, caseId, nonce, kind, detail, previous, sha);
            events.add(event);
            return event;
        }

        @Override
        public List<CampaignJournal.Event> read() {
            return List.copyOf(events);
        }
    }

    @FunctionalInterface
    private interface Hook {
        void run(int page) throws Exception;
    }

    private final class Context implements AutoCloseable {
        private final Path root =
                Files.createDirectory(directory.resolve(UUID.randomUUID().toString()))
                        .toAbsolutePath();
        private final ObjectNode request = request();
        private final MutableClock clock = new MutableClock();
        private Instant deadline = NOW.plusSeconds(120);
        private final Events events = new Events();
        private final boolean durable;
        private PersistentExportJournal journal;
        private final Port port = new Port();

        Context(final boolean durable) throws Exception {
            this.durable = durable;
            journal =
                    durable
                            ? PersistentExportJournal.create(
                                    root.resolve("journal"), SELECTION, CODE, attempt())
                            : PersistentExportJournal.syntheticFixture(
                                    root.resolve("journal"),
                                    SELECTION,
                                    CODE,
                                    attempt(),
                                    true,
                                    events);
        }

        UUID attempt() {
            return UUID.fromString(request.path("attemptId").textValue());
        }

        Path partialPath() {
            return root.resolve("manifestos-source.partial.jsonl");
        }

        Path completePath() {
            return root.resolve("manifestos-source.complete.jsonl");
        }

        ObjectNode run(final int cap, final int per, final long rows) throws Exception {
            return ManifestosIntervalCapture.capture(
                    request, port, journal, root, cap, per, rows, deadline, clock);
        }

        void reopen() throws Exception {
            journal.close();
            journal =
                    durable
                            ? PersistentExportJournal.open(
                                    root.resolve("journal"), SELECTION, CODE, attempt())
                            : PersistentExportJournal.syntheticFixture(
                                    root.resolve("journal"),
                                    SELECTION,
                                    CODE,
                                    attempt(),
                                    false,
                                    events);
        }

        @Override
        public void close() throws IOException {
            journal.close();
        }

        private final class Port implements ManifestosIntervalCapture.Port {
            private byte[] infoBody = json(ManifestosIntervalCaptureTest.info());
            private int infoStatus = 200;
            private int pageStatus = 200;
            private int infoCalls;
            private int pageCalls;
            private int sequence;
            private IOException infoFailure;
            private Instant completedAt;
            private Hook beforePage = page -> {};
            private final List<byte[]> pages = new ArrayList<>();
            private final List<ExportValidationSupport.Pin> pins = new ArrayList<>();

            Port() throws Exception {}

            @Override
            public CurlExportTransport.Wire info() throws Exception {
                assertTrue(journal.latest("MANIFESTOS_INFO_RESERVED") != null);
                infoCalls++;
                if (infoFailure != null) {
                    throw infoFailure;
                }
                return wire(infoStatus, infoBody);
            }

            @Override
            public CurlExportTransport.Wire page(
                    final JsonNode selection, final int page, final int per) throws Exception {
                assertEquals(pageCalls + 1, page);
                final var reservation = journal.latest("MANIFESTOS_PAGE_RESERVED");
                assertEquals("GET_WITH_QUERY", reservation.path("transport").asText());
                assertEquals("GET", reservation.path("method").asText());
                final String query =
                        ManifestosIndependentRequest.query(
                                        java.net.URI.create(
                                                "https://serialization.invalid/api/analytics/reports/6399/data"),
                                        selection,
                                        page,
                                        per)
                                .getRawQuery();
                assertEquals(
                        ExportValidationSupport.sha256(query.getBytes(StandardCharsets.US_ASCII)),
                        reservation.path("querySha256").asText());
                pageCalls++;
                beforePage.run(page);
                return wire(pageStatus, pages.get(page - 1));
            }

            private CurlExportTransport.Wire wire(final int status, final byte[] bytes)
                    throws Exception {
                final Path path = root.resolve("wire-" + ++sequence + ".json");
                Files.write(path, bytes);
                final var pin =
                        new ExportValidationSupport.Pin(
                                path, ExportValidationSupport.sha256(bytes), bytes.length);
                pins.add(pin);
                return new CurlExportTransport.Wire(
                        status, pin, completedAt == null ? clock.instant() : completedAt);
            }
        }
    }
}
