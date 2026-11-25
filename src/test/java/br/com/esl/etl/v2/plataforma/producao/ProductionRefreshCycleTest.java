package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportMetadataCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionRefreshCycleTest {
    @TempDir Path directory;

    @Test
    void replaysOnlyTheRequestedOccurrenceDayWithoutAdvancingTheRoutine() throws Exception {
        final var fixture = new Fixture(directory);
        final var day = fixture.today().minusDays(2);
        fixture.store.earliest = day.minusDays(7);
        fixture.config.put("reprocessOccurrenceDate", day.toString());
        assertTrue(fixture.run());
        assertEquals(List.of("OCORRENCIAS:" + day), fixture.store.commits);
        assertTrue(fixture.lastReport.required("passed").asBoolean());
        assertEquals("OCORRENCIA_RECAPTURA_FILIAL", fixture.lastReport.path("schema").asText());
        assertTrue(
                fixture.lastReport.path("operations").get(0).path("sqlReadbackExact").asBoolean());
        assertFalse(Files.exists(Path.of(fixture.config.required("checkpointPath").asText())));
    }

    @Test
    void routineUsesTheSqlCalendarEvenForRetiredDaysWithNoIndex() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.store.excluded.addAll(
                List.of(fixture.floor, fixture.floor.plusDays(1), fixture.floor.plusDays(3)));
        try (var paths = Files.walk(directory.resolve("dependency-index"))) {
            for (final var p :
                    paths.filter(
                                    path ->
                                            path.getParent()
                                                            .getFileName()
                                                            .toString()
                                                            .equals("OCORRENCIAS")
                                                    && fixture.store.excluded.stream()
                                                            .anyMatch(
                                                                    day ->
                                                                            path.getFileName()
                                                                                    .toString()
                                                                                    .equals(
                                                                                            day
                                                                                                    + ".json")))
                            .toList()) {
                Files.delete(p);
            }
        }
        assertTrue(fixture.run());
        final var entity = fixture.entity(ReportSnapshotTemplate.OCORRENCIAS);
        assertEquals(fixture.floor.toString(), entity.required("floor").asText());
        assertEquals(fixture.floor.plusDays(4).toString(), entity.required("sweepNext").asText());
        assertFalse(entity.required("relatedIndexPending").asBoolean());
        assertTrue(fixture.store.commits.contains("OCORRENCIAS:" + fixture.floor.plusDays(2)));
        assertTrue(fixture.store.reviews.isEmpty());
        assertEquals(0, fixture.occurrenceFloorCaptures);
        assertEquals(13, fixture.lastReport.required("entities").size());
        assertEquals(3, fixture.lastReport.findValue("retentionExcludedDays").asInt());
        assertEquals(
                fixture.floor.plusDays(2).toString(),
                fixture.lastReport.findValue("firstPlannedDay").asText());
    }

    @Test
    void lateChangeToARetiredDateReceivesSqlReviewBeforeItsWindowCanAdvance() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var day = fixture.floor.plusDays(2);
        fixture.store.excluded.add(day);
        fixture.changedDates.add(day);
        assertTrue(fixture.run());
        final var reviews =
                fixture.lastReport.required("operations").findParents("retentionReviewId");
        assertFalse(reviews.isEmpty());
        for (final var op : reviews) {
            assertTrue(
                    fixture.store.reviews.contains(
                            UUID.fromString(op.required("retentionReviewId").asText())));
            assertEquals(day.toString(), op.required("day").asText());
            assertEquals("RETENTION_DATE_REQUIRES_REVIEW", op.required("reason").asText());
            assertFalse(op.has("runId"));
            assertFalse(op.required("stored").asBoolean());
        }
        assertFalse(fixture.store.latest.containsKey("OCORRENCIAS:" + day));
        assertFalse(fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("pending"));
    }

    @Test
    void retirementBetweenPlanningAndStartDefersTheEntityAndReplansNextTime() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.store.refuseAtStart = fixture.floor;
        fixture.store.refusalCode = 52304;
        assertTrue(fixture.run());
        assertEquals(13, fixture.lastReport.required("entities").size());
        assertEquals(1, fixture.store.reviews.size());
        assertEquals(
                fixture.floor.toString(),
                fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).required("sweepNext").asText());
        fixture.now = fixture.now.plus(Duration.ofHours(2));
        assertTrue(fixture.run());
        assertEquals(1, fixture.store.reviews.size());
        assertTrue(fixture.store.latest.containsKey("OCORRENCIAS:" + fixture.floor.plusDays(1)));
        assertFalse(fixture.store.latest.containsKey("OCORRENCIAS:" + fixture.floor));
    }

    @Test
    void anUnrelatedSqlStartFailureStillStopsTheRoutine() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.store.refuseAtStart = fixture.floor;
        fixture.store.refusalCode = 52302;
        assertFalse(fixture.run());
        assertTrue(fixture.store.reviews.isEmpty());
        assertFalse(fixture.lastReport.has("retentionReviewId"));
        assertEquals(
                fixture.floor.toString(),
                fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).required("sweepNext").asText());
    }

    @Test
    void anExplicitReplayOfARetiredDateStillReportsTheSqlRefusal() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.store.earliest = fixture.floor;
        final var day = fixture.floor.plusDays(4);
        fixture.store.excluded.add(day);
        fixture.config.put("reprocessOccurrenceDate", day.toString());
        assertFalse(fixture.run());
        assertEquals(1, fixture.store.reviews.size());
        assertEquals(
                "RETENTION_DATE_REQUIRES_REVIEW", fixture.lastReport.required("reason").asText());
        assertFalse(Files.exists(directory.resolve("checkpoint.json")));
    }

    @Test
    void initialImportUsesItsBatchForEligibleDatesAndKeepsOtherReportsIntact() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        fixture.store.excluded.add(fixture.today());
        assertTrue(fixture.run());
        assertTrue(fixture.store.latest.containsKey("OCORRENCIAS:" + fixture.today().minusDays(1)));
        assertTrue(fixture.store.latest.containsKey("FRETES:" + fixture.today()));
        assertTrue(fixture.store.reviews.isEmpty());
    }

    @Test
    void recapturesTheWholeFreightDayForLateReceiptsWithoutAdvancingTheRoutine() throws Exception {
        final var fixture = new Fixture(directory);
        final var day = fixture.today().minusDays(12);
        fixture.store.earliest = day.minusDays(1);
        fixture.config.put("reprocessFreightDate", day.toString());
        assertTrue(fixture.run());
        assertEquals(List.of("FRETES:" + day), fixture.store.commits);
        assertEquals("FRETE_RECAPTURA_COMPROVANTE", fixture.lastReport.required("schema").asText());
        final var operation = fixture.lastReport.path("operations").get(0);
        assertTrue(operation.path("snapshotTerminal").asBoolean());
        assertTrue(operation.path("sqlReadbackExact").asBoolean());
        assertFalse(Files.exists(Path.of(fixture.config.required("checkpointPath").asText())));
    }

    @Test
    void rejectsFutureFreightDaysAndPreservesPreviouslyConfirmedSnapshots() throws Exception {
        final var fixture = new Fixture(directory);
        final var day = fixture.today();
        fixture.store.earliest = day.minusDays(1);
        fixture.config.put("reprocessFreightDate", day.plusDays(1).toString());
        assertFalse(fixture.run());
        assertEquals("FREIGHT_REPLAY_OUTSIDE_COVERAGE", fixture.lastReport.path("reason").asText());
        assertTrue(fixture.store.commits.isEmpty());

        fixture.config.put("reprocessFreightDate", day.toString());
        fixture.store.rejectDay = day;
        assertFalse(fixture.run());
        assertFalse(fixture.lastReport.path("passed").asBoolean());
        assertFalse(
                fixture.lastReport.path("operations").path(0).path("sqlReadbackExact").asBoolean());
        assertTrue(fixture.store.commits.isEmpty());
        assertFalse(Files.exists(Path.of(fixture.config.required("checkpointPath").asText())));
    }

    @Test
    void aFreightReplayCannotChangeTheActiveRefreshCheckpoint() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var checkpoint = Path.of(fixture.config.required("checkpointPath").asText());
        final var original = Files.readString(checkpoint);
        final var committed = List.copyOf(fixture.store.commits);
        fixture.config.put("reprocessFreightDate", fixture.today().toString());
        assertFalse(fixture.run());
        assertEquals(
                "FREIGHT_REPLAY_ISOLATED_CHECKPOINT", fixture.lastReport.path("reason").asText());
        assertEquals(original, Files.readString(checkpoint));
        assertEquals(committed, fixture.store.commits);
    }

    @Test
    void automaticCycleReadsAttachmentFromFreightWhenAReceiptArrivesAfterTheFirstCycle()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var day = fixture.floor;
        assertTrue(fixture.run());
        final var key = "FRETES:" + day;
        assertFalse(
                fixture.store
                        .latest
                        .get(key)
                        .get(0)
                        .path("record")
                        .hasNonNull("fit_d_t_created_at"));

        fixture.now = fixture.now.plus(Duration.ofHours(2));
        fixture.signal(day, "114");
        fixture.signal(day, "114");
        fixture.signal(day.minusDays(1), "114");
        fixture.signal(day.plusDays(2), "103");
        fixture.freightReceipt = fixture.now.minusSeconds(180).toString();
        assertTrue(fixture.run());
        assertEquals(
                fixture.freightReceipt,
                fixture.store
                        .latest
                        .get(key)
                        .get(0)
                        .path("record")
                        .required("fit_d_t_created_at")
                        .asText());
        assertTrue(
                fixture.lastReport
                        .path("operations")
                        .findValuesAsText("phase")
                        .contains("FREIGHT_RECEIPT_UPDATED"));
        assertEquals(
                1,
                fixture.lastReport.path("operations").findValuesAsText("phase").stream()
                        .filter("FREIGHT_RECEIPT_UPDATED"::equals)
                        .count());
        assertEquals(0, fixture.lastReport.required("freightReceiptDaysPending").asInt());
        fixture.now = fixture.now.plus(Duration.ofHours(2));
        assertTrue(fixture.run());
        assertFalse(
                fixture.lastReport
                        .path("operations")
                        .findValuesAsText("phase")
                        .contains("FREIGHT_RECEIPT_UPDATED"));
    }

    @Test
    void unconfirmedLateReceiptIsRetriedAfterRestartWithoutPublishingAnEstimatedDate()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var day = fixture.floor.plusDays(1);
        fixture.signal(day, "114");
        fixture.store.rejectDay = day;
        assertFalse(fixture.run());
        final var persisted =
                CurlSnapshotWire.JSON.readTree(
                        Files.readAllBytes(directory.resolve("checkpoint.json")));
        assertTrue(
                persisted
                        .required("payload")
                        .required("freightReceipts")
                        .required("pendingDays")
                        .has(day.toString()));
        fixture.receiptEvents.removeAll();
        assertFalse(fixture.store.latest.containsKey("FRETES:" + day));
        fixture.store.rejectDay = null;
        fixture.now = fixture.now.plus(Duration.ofHours(2));
        assertTrue(fixture.run());
        assertFalse(
                fixture.store
                        .latest
                        .get("FRETES:" + day)
                        .get(0)
                        .path("record")
                        .hasNonNull("fit_d_t_created_at"));
    }

    @Test
    void bringsNewDatesToEverySourceBeforeReviewingOldDates() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.now = fixture.now.plus(Duration.ofDays(12));
        final var through = fixture.today().minusDays(4);
        assertTrue(fixture.run());
        for (var entity : fixture.lastReport.required("entities")) {
            assertEquals(through.toString(), entity.required("calendarThrough").asText());
            assertEquals("BATCH_DEFERRED", entity.required("status").asText());
        }
        for (var operation : fixture.lastReport.required("operations")) {
            assertEquals("NEW_CALENDAR_DAY", operation.required("phase").asText());
        }
        assertEquals(0, fixture.updateCalls);
    }

    @Test
    void boundedContinuousCyclesGiveEverySourceItsTurnAcrossRestarts() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.now = fixture.now.plusSeconds(86400);
        fixture.limitAfterFirstMetadata = true;
        final var templates = ReportSnapshotTemplate.receivingTemplates();
        for (final var expected : templates) {
            assertTrue(fixture.run());
            assertEquals(
                    expected.name(),
                    fixture.lastReport.required("entities").get(0).required("entity").asText());
            assertEquals(
                    fixture.today().toString(),
                    fixture.entity(expected).required("lastCalendarDay").asText());
            assertEquals(templates.size(), fixture.lastReport.required("entities").size());
            fixture.now = fixture.now.plusSeconds(3600);
        }
        assertTrue(fixture.run());
        assertEquals(
                templates.get(0).name(),
                fixture.lastReport.required("entities").get(0).required("entity").asText());
    }

    @Test
    void initialHistoryReallyProcessesNinetyDaysAcrossBoundedResumableBatches() throws Exception {
        final var fixture = new Fixture(directory);
        for (int cycle = 0; cycle < 12; cycle++) {
            assertTrue(fixture.run());
            fixture.now = fixture.now.plus(Duration.ofMinutes(65));
        }
        assertTrue(fixture.lastReport.path("initialComplete").asBoolean());
        assertEquals(1170, fixture.store.latest.size());
        assertEquals(1170, fixture.fullCalls);
        assertEquals(0, fixture.updateCalls);
        for (final var entity : fixture.lastReport.required("entities")) {
            assertEquals(0, entity.path("initialRemainingDays").asInt());
        }
    }

    @Test
    void manualHistoryKeepsExactlyTheOriginalNinetyDaysAfterMidnight() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.now = Instant.parse("2026-11-11T02:59:55Z");
        final var firstDay = fixture.today();
        fixture.config.put("historyOnly", true).put("historyDays", 90);
        for (int cycle = 0; cycle < 12; cycle++) {
            assertTrue(fixture.run());
            assertEquals(0, fixture.lastReport.path("waitAfterCompletionMinutes").asInt());
            if (cycle < 11) {
                assertEquals(
                        fixture.lastReport.path("completedAt"),
                        fixture.lastReport.path("nextEligibleAt"));
            }
            fixture.now = fixture.now.plusSeconds(1);
        }
        assertTrue(fixture.lastReport.path("initialComplete").asBoolean());
        assertEquals(
                firstDay.toString(), fixture.lastReport.path("initialHistoryThrough").asText());
        assertEquals(1170, fixture.fullCalls);
        assertEquals(0, fixture.updateCalls);
        assertEquals(1170, fixture.store.latest.size());
        for (final var template : ReportSnapshotTemplate.receivingTemplates()) {
            assertTrue(fixture.store.latest.containsKey(template + ":" + firstDay));
            assertTrue(fixture.store.latest.containsKey(template + ":" + firstDay.minusDays(89)));
            assertFalse(fixture.store.latest.containsKey(template + ":" + firstDay.plusDays(1)));
        }
        assertTrue(fixture.run());
        assertEquals(1170, fixture.fullCalls);
        assertEquals(0, fixture.updateCalls);
        fixture.config.remove("historyOnly");
        assertThrows(IllegalStateException.class, fixture::run);
    }

    @Test
    void addingDeliveryArchivesKeepsAnExistingCompletedHistoryAndUsesItsOriginalDates()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        final var through = fixture.today();
        final var floor = through.minusDays(89);
        final var tokenHash =
                RefreshCheckpoint.hash("synthetic-test".getBytes(StandardCharsets.UTF_8));
        final var scope =
                RefreshCheckpoint.hash(
                        ("https://rodogarcia.eslcloud.com.br\n" + tokenHash)
                                .getBytes(StandardCharsets.UTF_8));
        final var checkpoint =
                new RefreshCheckpoint(directory.resolve("checkpoint.json"), scope, fixture.now);
        for (final var template : ReportSnapshotTemplate.receivingTemplates()) {
            if (template != ReportSnapshotTemplate.COMPROVANTES_ENTREGA) {
                checkpoint
                        .entity(template, floor, null, through, fixture.now)
                        .put("initialComplete", true)
                        .put("initialNext", floor.minusDays(1).toString());
            }
        }
        checkpoint.save();
        fixture.now = fixture.now.plus(Duration.ofDays(4));

        assertTrue(fixture.run());
        assertEquals(1, fixture.fullCalls);
        assertEquals(
                java.util.Set.of("COMPROVANTES_ENTREGA:" + through), fixture.store.latest.keySet());
        assertEquals(
                89,
                fixture.lastReport
                        .required("entities")
                        .get(12)
                        .path("initialRemainingDays")
                        .asInt());
        assertFalse(fixture.lastReport.path("initialComplete").asBoolean());
        assertEquals(
                floor.minusDays(1).toString(),
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("initialNext")
                        .asText());
    }

    @Test
    void historyContinuesOtherSourcesAndRetriesOnlyThePendingFreightDate() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        final var day = fixture.today();
        final var key = "FRETES:" + day;
        fixture.freightSourceFailure = true;
        for (int failure = 1; failure <= 3; failure++) {
            assertEquals(failure < 3, fixture.run());
            assertEquals(
                    day.toString(),
                    fixture.entity(ReportSnapshotTemplate.FRETES).path("initialNext").asText());
            assertFalse(fixture.store.latest.containsKey(key));
            if (failure < 3) {
                assertEquals(13, fixture.lastReport.required("entities").size());
                assertEquals(12 * failure, fixture.store.latest.size());
            }
            fixture.now = fixture.now.plusSeconds(3900);
        }
        fixture.freightSourceFailure = false;
        assertTrue(fixture.run());
        assertEquals(1, fixture.store.commits.stream().filter(key::equals).count());
        assertFalse(fixture.entity(ReportSnapshotTemplate.FRETES).has("initialSourceFailures"));
    }

    @Test
    void historyIsolatesRejectedRowsAndRetriesThatDateWithNewRuntime() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        final var day = fixture.today();
        final var key = "MANIFESTOS:" + day;
        fixture.rejectedManifest = true;
        assertTrue(fixture.run());
        assertEquals(12, fixture.store.latest.size());
        assertFalse(fixture.store.latest.containsKey(key));
        assertEquals(1, fixture.rejectedManifestCaptures);
        assertEquals(
                day.toString(),
                fixture.entity(ReportSnapshotTemplate.MANIFESTOS).path("initialNext").asText());
        assertTrue(fixture.lastReport.required("entities").toString().contains("BATCH_BLOCKED"));

        assertTrue(fixture.run());
        assertEquals(24, fixture.store.latest.size());
        assertEquals(1, fixture.rejectedManifestCaptures);
        assertFalse(fixture.store.latest.containsKey(key));

        fixture.rejectedManifest = false;
        fixture.config.put("runtimeSha256", "b".repeat(64));
        assertTrue(fixture.run());
        assertEquals(1, fixture.store.commits.stream().filter(key::equals).count());
        assertFalse(
                fixture.entity(ReportSnapshotTemplate.MANIFESTOS).has("rejectedHistoryRuntime"));
    }

    @Test
    void historyStopsWhenOnlyRejectedDatesRemain() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        fixture.seedReady();
        final var day = fixture.today();
        final var scope =
                CurlSnapshotWire.JSON
                        .readTree(Files.readAllBytes(directory.resolve("checkpoint.json")))
                        .required("payload")
                        .required("sourceScope")
                        .asText();
        final var checkpoint =
                new RefreshCheckpoint(directory.resolve("checkpoint.json"), scope, fixture.now);
        final var manifest =
                checkpoint.entity(ReportSnapshotTemplate.MANIFESTOS, day, null, day, fixture.now);
        manifest.put("initialComplete", false).put("initialNext", day.toString());
        checkpoint.save();
        fixture.rejectedManifest = true;

        assertFalse(fixture.run());
        assertFalse(fixture.lastReport.path("initialComplete").asBoolean());
        assertEquals(
                "REFRESH_HISTORY_REJECTED_DATES_PENDING",
                fixture.lastReport.path("reason").asText());
        assertFalse(fixture.store.latest.containsKey("MANIFESTOS:" + day));
    }

    @Test
    void transientFreightMetadataTimeoutLeavesTheDatePendingAndContinues() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("historyOnly", true).put("historyDays", 90).put("initialBatchDays", 1);
        fixture.freightMetadataFailure = true;
        assertTrue(fixture.run());
        assertEquals(12, fixture.store.latest.size());
        assertEquals(
                1,
                fixture.entity(ReportSnapshotTemplate.FRETES)
                        .path("initialSourceFailures")
                        .asInt());
        fixture.freightMetadataFailure = false;
        fixture.now = fixture.now.plusSeconds(3900);
        assertTrue(fixture.run());
        assertEquals(25, fixture.store.latest.size());
        assertFalse(fixture.entity(ReportSnapshotTemplate.FRETES).has("initialSourceFailures"));
    }

    @Test
    void lowDiskStopsBeforeARequestAndResumesWhenSpaceIsAvailable() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.availableBytes = 0;
        assertFalse(fixture.run());
        assertEquals("REFRESH_DISK_SPACE_LOW", fixture.lastReport.path("reason").asText());
        assertEquals(0, fixture.fullCalls);
        assertTrue(fixture.store.runs.isEmpty());
        assertFalse(Files.exists(directory.resolve("checkpoint.json")));
        fixture.availableBytes = Long.MAX_VALUE;
        fixture.config.put("initialBatchDays", 1);
        assertTrue(fixture.run());
        assertEquals(13, fixture.store.latest.size());
    }

    @Test
    void interruptedSqlReadbackKeepsTheCursorAndReplacingTheDayDoesNotDoubleRows()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.config.put("initialBatchDays", 1);
        final var day = fixture.today();
        final var key = "CONSOLIDACAO_DETALHADA:" + day;
        fixture.store.disconnectAfterCommit = true;
        assertFalse(fixture.run());
        assertEquals(
                day.toString(),
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("initialNext")
                        .asText());
        assertEquals(2, fixture.store.latest.get(key).size());
        assertTrue(
                fixture.lastReport
                        .required("operations")
                        .get(0)
                        .path("failureStateUnconfirmed")
                        .asBoolean());
        fixture.store.disconnectAfterCommit = false;
        assertTrue(fixture.run());
        assertEquals(2, fixture.store.latest.get(key).size());
        assertEquals(
                day.minusDays(1).toString(),
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("initialNext")
                        .asText());
    }

    @Test
    void failureDoesNotSkipTheFailedInitialDayOrRepeatConfirmedEarlierDays() throws Exception {
        final var fixture = new Fixture(directory);
        final var today = fixture.today();
        fixture.store.rejectDay = today.minusDays(2);
        assertFalse(fixture.run());
        assertEquals(2, fixture.store.latest.size());
        assertEquals(
                today.minusDays(2).toString(),
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("initialNext")
                        .asText());
        fixture.store.rejectDay = null;
        fixture.now = fixture.now.plusSeconds(3900);
        assertTrue(fixture.run());
        assertEquals(
                1L,
                fixture.store.commits.stream()
                        .filter(key -> key.equals("CONSOLIDACAO_DETALHADA:" + today))
                        .count());
        assertFalse(
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("initialComplete")
                        .asBoolean());
    }

    @Test
    void partialUpdatesReplaceCompleteDatesAndKeepValidPhysicalDuplicates() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.changedDates.add(fixture.floor.plusDays(2));
        assertTrue(fixture.run());
        final var key = "OCORRENCIAS:" + fixture.floor.plusDays(2);
        assertEquals(2, fixture.store.latest.get(key).size());
        assertEquals(
                fixture.store.latest.get(key).get(0).path("record"),
                fixture.store.latest.get(key).get(1).path("record"));
        assertFalse(fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("pending"));
        assertEquals(8, fixture.templatesWithUpdates.size());
        assertTrue(
                fixture.updateScopes.stream()
                        .allMatch(
                                scope ->
                                        java.time.temporal.ChronoUnit.DAYS.between(
                                                        scope.startInclusive(),
                                                        scope.endInclusive())
                                                <= 7));
        assertTrue(
                fixture.freightScopes.stream()
                        .allMatch(scope -> scope.startInclusive().equals(scope.endInclusive())));
        assertFalse(fixture.templatesWithUpdates.contains(ReportSnapshotTemplate.CONTAS_A_PAGAR));
        assertFalse(fixture.templatesWithUpdates.contains(ReportSnapshotTemplate.SINISTROS));
        assertFalse(
                fixture.templatesWithUpdates.contains(ReportSnapshotTemplate.COLETAS_EXCLUIDAS));
    }

    @Test
    void watermarkStaysBehindWhenSqlRejectsAnAffectedDate() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var marker =
                fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                        .path("watermark")
                        .asText();
        fixture.changedDates.add(fixture.floor.plusDays(3));
        fixture.store.rejectDay = fixture.floor.plusDays(3);
        assertFalse(fixture.run());
        final var state = fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        assertEquals(marker, state.path("watermark").asText());
        assertEquals(
                fixture.floor.plusDays(3).toString(),
                state.path("consolidationSweep").path("next").asText());
        assertTrue(fixture.store.failed > 0);
        fixture.store.rejectDay = null;
        fixture.now = fixture.now.plusSeconds(3900);
        assertTrue(fixture.run());
        assertFalse(fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA).has("pending"));
    }

    @Test
    void changedDateBudgetDefersWorkWithoutPretendingItsWatermarkWasCommitted() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.config.put("changedDaysPerBatch", 1);
        fixture.changedDates.add(fixture.floor.plusDays(2));
        fixture.changedDates.add(fixture.floor.plusDays(3));
        assertTrue(fixture.run());
        final var state = fixture.entity(ReportSnapshotTemplate.OCORRENCIAS);
        assertTrue(state.has("pending") || state.has("relatedPending"));
        assertEquals(1, queued(state).path("dates").size());
        assertTrue(fixture.lastReport.required("entities").toString().contains("BATCH_DEFERRED"));
        for (int attempt = 0; attempt < 4; attempt++) {
            fixture.now = fixture.now.plusSeconds(3900);
            assertTrue(fixture.run());
            if (!fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("pending")
                    && !fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("relatedPending")) {
                break;
            }
        }
        assertFalse(fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("pending"));
        assertFalse(fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("relatedPending"));
    }

    @Test
    void manifestsAreLocatedByTheRequestedBusinessDateWithoutInventingAnExposedDateColumn()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        fixture.changedDates.add(fixture.floor.plusDays(5));
        assertTrue(fixture.run());
        assertEquals(90, fixture.manifestScopes.size());
        assertTrue(
                fixture.manifestScopes.stream()
                        .allMatch(dates -> dates.startInclusive().equals(dates.endInclusive())));
        assertTrue(fixture.store.latest.containsKey("MANIFESTOS:" + fixture.floor.plusDays(5)));
        assertFalse(fixture.entity(ReportSnapshotTemplate.MANIFESTOS).has("pending"));
    }

    @Test
    void
            exceptionsReconcileTheWholeHistoricalRangeInPartsAndAnEmptySnapshotRemovesOnlyTheCurrentRows()
                    throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        final var oldDay = fixture.floor;
        fixture.store.latest.put(
                "CONTAS_A_PAGAR:" + oldDay,
                List.of(CurlSnapshotWire.JSON.createObjectNode().put("old", true)));
        fixture.emptyDay = oldDay;
        assertTrue(fixture.run());
        assertTrue(fixture.store.latest.get("CONTAS_A_PAGAR:" + oldDay).isEmpty());
        assertEquals(
                oldDay.plusDays(8).toString(),
                fixture.entity(ReportSnapshotTemplate.CONTAS_A_PAGAR).path("sweepNext").asText());
        assertEquals(
                oldDay.plusDays(8).toString(),
                fixture.entity(ReportSnapshotTemplate.SINISTROS).path("sweepNext").asText());
        assertEquals(
                oldDay.plusDays(8).toString(),
                fixture.entity(ReportSnapshotTemplate.COMPROVANTES_ENTREGA)
                        .path("sweepNext")
                        .asText());
        assertEquals(
                8,
                fixture.lastReport.required("operations").findValues("entity").stream()
                        .filter(value -> value.asText().equals("CONTAS_A_PAGAR"))
                        .count());
    }

    @Test
    void schemaDriftStopsBeforeTheFirstSqlRun() throws Exception {
        final var fixture = new Fixture(directory);
        ((ObjectNode)
                        fixture.infos
                                .get(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA)
                                .required("filters")
                                .get(0))
                .put("type", "unsupported");
        assertFalse(fixture.run());
        assertEquals(0, fixture.fullCalls);
        assertEquals(0, fixture.store.commits.size());
    }

    @Test
    void occurrenceIndexBootstrapPreservesTheRecordingBranchWhenCargoLocationChanges()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        try (var files = Files.walk(directory.resolve("dependency-index"))) {
            final var pointer =
                    files.filter(
                                    path ->
                                            path.getFileName()
                                                            .toString()
                                                            .equals(fixture.floor + ".json")
                                                    && path.getParent()
                                                            .getFileName()
                                                            .toString()
                                                            .equals("OCORRENCIAS"))
                            .findFirst()
                            .orElseThrow();
            Files.delete(pointer);
        }
        fixture.locationChangeDuringLookup = true;
        assertTrue(fixture.run());
        assertEquals("0818", fixture.lastReport.required("relatedFieldRefreshRevision").asText());
        assertEquals(1, fixture.occurrenceFloorCaptures);
        assertEquals(
                "filial de registro",
                fixture.store
                        .latest
                        .get("OCORRENCIAS:" + fixture.floor)
                        .get(0)
                        .required("record")
                        .required("ice_uer_crn_psn_nickname")
                        .asText());
        assertEquals(2, fixture.store.latest.get("OCORRENCIAS:" + fixture.floor).size());
    }

    @Test
    void consolidationJoinedValuesAreReconciledWithoutReadingUnrelatedFreightRows()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.seedReady();
        assertTrue(fixture.run());
        assertEquals(
                90,
                fixture.lastReport.required("operations").findValues("phase").stream()
                        .filter(value -> value.asText().equals("CONSOLIDATION_RECONCILIATION"))
                        .count());
        assertFalse(
                fixture.templatesWithUpdates.contains(
                        ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA));
        final var state = fixture.entity(ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        assertEquals(1, state.path("sweepsCompleted").asInt());
        assertFalse(state.has("consolidationSweep"));
        assertEquals(fixture.now.minusSeconds(120).toString(), state.path("watermark").asText());
    }

    private static JsonNode queued(final JsonNode state) {
        return state.has("relatedPending")
                ? state.required("relatedPending")
                : state.required("pending");
    }

    private record Valid() implements ExpansionObservation {
        @Override
        public String vertical() {
            return "SYNTHETIC_TEST";
        }

        @Override
        public boolean valid() {
            return true;
        }
    }

    private static final class MemoryStore implements ProductionRefreshCycle.Receiving {
        final Map<String, List<JsonNode>> latest = new HashMap<>();
        final Map<UUID, String> runs = new HashMap<>();
        final List<String> commits = new ArrayList<>();
        final TreeSet<LocalDate> excluded = new TreeSet<>();
        final List<UUID> reviews = new ArrayList<>();
        LocalDate refuseAtStart;
        int refusalCode;
        LocalDate rejectDay;
        LocalDate earliest;
        boolean disconnectAfterCommit;
        int failed;

        @Override
        public LocalDate earliestCovered(
                final String scope, final LocalDate today, final String entity) {
            return earliest;
        }

        @Override
        public void plannedDates(
                final String scope,
                final LocalDate start,
                final LocalDate end,
                final String entity,
                final java.util.function.Consumer<LocalDate> accept) {
            assertEquals("OCORRENCIAS", entity);
            start.datesUntil(end.plusDays(1))
                    .filter(day -> !excluded.contains(day))
                    .forEach(accept);
        }

        @Override
        public void start(
                final UUID run,
                final String scope,
                final LocalDate date,
                final String requestSha,
                final String entity)
                throws java.sql.SQLException {
            if (entity.equals("OCORRENCIAS")
                    && (date.equals(refuseAtStart) || excluded.contains(date))) {
                final var code = excluded.contains(date) ? 52304 : refusalCode;
                if (code == 52304) {
                    excluded.add(date);
                    reviews.add(run);
                    throw new java.sql.SQLException(
                            "RETENTION_DATE_REQUIRES_REVIEW", "S0001", code);
                }
                throw new java.sql.SQLException("UNRELATED_SQL_FAILURE", "S0001", code);
            }
            runs.put(run, entity + ":" + date);
        }

        @Override
        public void storeAndVerify(final UUID run, final List<String> rows, final String sha)
                throws Exception {
            final var key = runs.get(run);
            if (rejectDay != null && key.endsWith(rejectDay.toString())) {
                throw new IllegalStateException("SYNTHETIC_STORE_REJECTION");
            }
            final var values = new ArrayList<JsonNode>();
            for (final var row : rows) {
                values.add(CurlSnapshotWire.JSON.readTree(row));
            }
            latest.put(key, values);
            commits.add(key);
            if (disconnectAfterCommit) {
                throw new java.sql.SQLTransientConnectionException(
                        "Synthetic SQL readback disconnect", "08S01");
            }
        }

        @Override
        public void fail(final UUID run, final String reason) throws Exception {
            if (disconnectAfterCommit) {
                throw new java.sql.SQLTransientConnectionException(
                        "Synthetic SQL disconnect", "08S01");
            }
            failed++;
        }

        @Override
        public void close() {}
    }

    private static final class Fixture {
        final Path root;
        final ObjectNode config = CurlSnapshotWire.JSON.createObjectNode();
        final MemoryStore store = new MemoryStore();
        final com.fasterxml.jackson.databind.node.ArrayNode receiptEvents =
                CurlSnapshotWire.JSON.createArrayNode();
        String freightReceipt;

        void signal(final LocalDate day, final String code) {
            receiptEvents
                    .addObject()
                    .put("ice_ore_code", code)
                    .put("ice_fit_service_at", day.toString())
                    .put("occurrence_at", now.minusSeconds(310).toString());
        }

        final EnumMap<ReportSnapshotTemplate, JsonNode> infos =
                new EnumMap<>(ReportSnapshotTemplate.class);
        final TreeSet<LocalDate> changedDates = new TreeSet<>();
        final java.util.Set<ReportSnapshotTemplate> templatesWithUpdates =
                java.util.EnumSet.noneOf(ReportSnapshotTemplate.class);
        final List<BusinessDateRange> manifestScopes = new ArrayList<>();
        final List<BusinessDateRange> updateScopes = new ArrayList<>();
        final List<BusinessDateRange> freightScopes = new ArrayList<>();
        final Instant changedAt = Instant.parse("2026-11-10T04:30:00Z");
        final LocalDate floor = LocalDate.parse("2026-08-13");
        Instant now = Instant.parse("2026-11-10T06:00:00Z");
        JsonNode lastReport;
        LocalDate emptyDay;
        int fullCalls;
        int updateCalls;
        boolean limitAfterFirstMetadata;
        boolean locationChangeDuringLookup;
        boolean freightSourceFailure;
        boolean freightMetadataFailure;
        boolean rejectedManifest;
        int rejectedManifestCaptures;
        long availableBytes = Long.MAX_VALUE;
        int occurrenceFloorCaptures;

        Fixture(final Path root) throws Exception {
            this.root = root;
            config.put("database", "ETL_SISTEMA_V2")
                    .put("runtimeSha256", "a".repeat(64))
                    .put("apiOrigin", "https://rodogarcia.eslcloud.com.br")
                    .put("apiToken", "synthetic-test")
                    .put("checkpointPath", root.resolve("checkpoint.json").toString())
                    .put("sourceStopPath", root.resolve("stop.json").toString());
            try (var input =
                    getClass()
                            .getClassLoader()
                            .getResourceAsStream(
                                    "contracts/dataexport/receiving-catalog-0818.json")) {
                assertTrue(input != null);
                for (final var entity :
                        CurlSnapshotWire.JSON.readTree(input).required("entities")) {
                    final var info = CurlSnapshotWire.JSON.createObjectNode();
                    final var fields = info.putObject("fields");
                    entity.required("fields").forEach(field -> fields.putObject(field.asText()));
                    info.set("filters", entity.required("filters").deepCopy());
                    info.set(
                            "required_date_filters",
                            entity.required("requiredDateFilters").deepCopy());
                    info.set("default_order", entity.required("defaultOrder").deepCopy());
                    infos.put(
                            ReportSnapshotTemplate.valueOf(entity.required("entity").asText()),
                            info);
                }
            }
        }

        LocalDate today() {
            return now.atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
        }

        void seedReady() throws Exception {
            final var hash =
                    RefreshCheckpoint.hash("synthetic-test".getBytes(StandardCharsets.UTF_8));
            final var scope =
                    RefreshCheckpoint.hash(
                            ("https://rodogarcia.eslcloud.com.br\n" + hash)
                                    .getBytes(StandardCharsets.UTF_8));
            final var saved =
                    new RefreshCheckpoint(
                            root.resolve("checkpoint.json"), scope, now.minusSeconds(10800));
            for (final var template : ReportSnapshotTemplate.receivingTemplates()) {
                saved.entity(template, floor, null, today(), now.minusSeconds(10800))
                        .put("initialComplete", true)
                        .put("watermark", now.minusSeconds(10800).toString());
            }
            final var indexes =
                    new FreightDependencyIndex(
                            root.resolve("dependency-index").resolve(scope), root, scope);
            final var seed = Files.createDirectories(root.resolve("seed"));
            for (final var template :
                    new ReportSnapshotTemplate[] {
                        ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA,
                        ReportSnapshotTemplate.OCORRENCIAS
                    }) {
                for (var day = floor; !day.isAfter(today()); day = day.plusDays(1)) {
                    final var envelope = CurlSnapshotWire.JSON.createObjectNode();
                    envelope.set("record", row(template, day));
                    indexes.remember(
                            template,
                            day,
                            UUID.randomUUID(),
                            "a".repeat(64),
                            List.of(envelope.toString(), envelope.toString()),
                            seed);
                }
            }
            saved.save();
        }

        private static ObjectNode row(final ReportSnapshotTemplate template, final LocalDate day) {
            final var row = CurlSnapshotWire.JSON.createObjectNode().put("synthetic", true);
            final var number = day.toEpochDay() + 1;
            if (template.rowDate() != null) {
                row.put(template.rowDate(), day.toString());
            }
            if (template == ReportSnapshotTemplate.OCORRENCIAS) {
                row.put("ice_f_e_fit_corporation_sequence_number", number)
                        .put("ice_fit_service_at", day.toString())
                        .put("ice_uer_crn_psn_nickname", "filial de registro");
            } else if (template == ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA) {
                row.put("ctd_fgs_corporation_sequence_number", number)
                        .put("ctd_fgs_service_at", day.toString());
            } else if (template == ReportSnapshotTemplate.FRETES
                    || template == ReportSnapshotTemplate.LOCALIZACAO_CARGAS) {
                row.put("corporation_sequence_number", number)
                        .put("fit_fln_cln_nickname", "fixture");
            }
            return row;
        }

        ObjectNode entity(final ReportSnapshotTemplate template) throws Exception {
            return (ObjectNode)
                    CurlSnapshotWire.JSON
                            .readTree(Files.readAllBytes(root.resolve("checkpoint.json")))
                            .required("payload")
                            .required("entities")
                            .required(template.name());
        }

        boolean run() throws Exception {
            final var privateRoot = root.resolve("cycle-" + UUID.randomUUID());
            final var report = root.resolve("report-" + UUID.randomUUID() + ".json");
            config.put("privateRoot", privateRoot.toString()).put("reportPath", report.toString());
            final var metadata = new ReportMetadataCatalog();
            final var calls = new java.util.concurrent.atomic.AtomicInteger();
            final var wire =
                    new ReportSnapshotExtractor.Wire() {
                        @Override
                        public JsonNode info(final ReportSnapshotTemplate template) {
                            if (limitAfterFirstMetadata) {
                                calls.set(1750);
                            }
                            if (freightMetadataFailure
                                    && template == ReportSnapshotTemplate.FRETES) {
                                throw new CurlSnapshotWire.RequestFailure(503, 0, false);
                            }
                            return infos.get(template);
                        }

                        @Override
                        public JsonNode page(
                                final ReportSnapshotTemplate template,
                                final BusinessDateRange dates,
                                final int page,
                                final int per) {
                            throw new AssertionError("Injected capture owns synthetic pages");
                        }

                        @Override
                        public JsonNode receiptPage(
                                final BusinessDateRange dates,
                                final br.com.esl.etl.v2.plataforma.fonte.dataexport
                                                .SourceDateTimeRange
                                        updates,
                                final int page,
                                final int per) {
                            final var matching = CurlSnapshotWire.JSON.createArrayNode();
                            for (var event : receiptEvents) {
                                final var stamp =
                                        Instant.parse(event.required("occurrence_at").asText());
                                if (!stamp.isBefore(updates.startInclusive())
                                        && !stamp.isAfter(updates.endInclusive())) {
                                    matching.add(event);
                                }
                            }
                            final var result = CurlSnapshotWire.JSON.createArrayNode();
                            for (int index = (page - 1) * per;
                                    index < Math.min(page * per, matching.size());
                                    index++) {
                                result.add(matching.get(index));
                            }
                            return result;
                        }
                    };
            final ProductionRefreshCycle.CapturePort capture =
                    (template, dates, updates, selected, sink, limits) -> {
                        final var id = UUID.randomUUID();
                        long rows = 0;
                        if (updates != null) {
                            updateCalls++;
                            updateScopes.add(dates);
                            if (template == ReportSnapshotTemplate.FRETES) {
                                freightScopes.add(dates);
                            }
                            templatesWithUpdates.add(template);
                            if (template == ReportSnapshotTemplate.MANIFESTOS) {
                                manifestScopes.add(dates);
                            }
                            for (final var changed : changedDates) {
                                if (changedAt.isBefore(updates.startInclusive())
                                        || changedAt.isAfter(updates.endInclusive())) {
                                    continue;
                                }
                                if (!changed.isBefore(dates.startInclusive())
                                        && !changed.isAfter(dates.endInclusive())) {
                                    final var row = row(template, changed).put("sequence_code", 1);
                                    sink.accept(id, ++rows, row, new Valid());
                                }
                            }
                        } else {
                            fullCalls++;
                            if (rejectedManifest && template == ReportSnapshotTemplate.MANIFESTOS) {
                                rejectedManifestCaptures++;
                                sink.accept(id, 1, row(template, dates.startInclusive()), null);
                                return new ReportSnapshotExtractor.Result(
                                        id, 1, 0, 1, metadata.fieldCount(template), 2, true, false);
                            }
                            if (freightSourceFailure && template == ReportSnapshotTemplate.FRETES) {
                                sink.accept(
                                        id, 1, row(template, dates.startInclusive()), new Valid());
                                throw new CurlSnapshotWire.RequestFailure(422, 0, true);
                            }
                            if (!dates.startInclusive().equals(emptyDay)) {
                                for (var day = dates.startInclusive();
                                        !day.isAfter(dates.endInclusive());
                                        day = day.plusDays(1)) {
                                    final var row = row(template, day);
                                    if (template == ReportSnapshotTemplate.FRETES
                                            && freightReceipt != null) {
                                        row.put("fit_d_t_created_at", freightReceipt);
                                    }
                                    if (template == ReportSnapshotTemplate.OCORRENCIAS
                                            && day.equals(floor)) {
                                        occurrenceFloorCaptures++;
                                    }
                                    if (locationChangeDuringLookup
                                            && template == ReportSnapshotTemplate.LOCALIZACAO_CARGAS
                                            && day.equals(floor)) {
                                        row.put("fit_fln_cln_nickname", "new location");
                                    }
                                    sink.accept(id, ++rows, row, new Valid());
                                    sink.accept(id, ++rows, row.deepCopy(), new Valid());
                                }
                            }
                        }
                        return new ReportSnapshotExtractor.Result(
                                id, rows, rows, 0, metadata.fieldCount(template), 2, true, false);
                    };
            final var cycle =
                    new ProductionRefreshCycle(
                            capture,
                            ignored -> store,
                            (origin, token, output, stop, deadline) ->
                                    new ProductionRefreshCycle.Session(wire, calls::get),
                            Clock.fixed(now, ZoneOffset.UTC),
                            root,
                            path -> availableBytes);
            final var passed = cycle.run(config);
            lastReport = CurlSnapshotWire.JSON.readTree(Files.readAllBytes(report));
            return passed;
        }
    }
}
