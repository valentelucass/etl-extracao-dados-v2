package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void initialHistoryReallyProcessesNinetyDaysAcrossBoundedResumableBatches() throws Exception {
        final var fixture = new Fixture(directory);
        for (int cycle = 0; cycle < 12; cycle++) {
            assertTrue(fixture.run());
            fixture.now = fixture.now.plus(Duration.ofMinutes(65));
        }
        assertTrue(fixture.lastReport.path("initialComplete").asBoolean());
        assertEquals(990, fixture.store.latest.size());
        assertEquals(990, fixture.fullCalls);
        assertEquals(0, fixture.updateCalls);
        for (final var entity : fixture.lastReport.required("entities")) {
            assertEquals(0, entity.path("initialRemainingDays").asInt());
        }
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
        assertEquals(9, fixture.templatesWithUpdates.size());
        assertFalse(fixture.templatesWithUpdates.contains(ReportSnapshotTemplate.CONTAS_A_PAGAR));
        assertFalse(fixture.templatesWithUpdates.contains(ReportSnapshotTemplate.SINISTROS));
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
        assertEquals(1, state.path("pending").path("dates").size());
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
        assertTrue(state.has("pending"));
        assertEquals(1, state.path("pending").path("dates").size());
        assertTrue(fixture.lastReport.required("entities").toString().contains("BATCH_DEFERRED"));
        fixture.now = fixture.now.plusSeconds(3900);
        assertTrue(fixture.run());
        assertFalse(fixture.entity(ReportSnapshotTemplate.OCORRENCIAS).has("pending"));
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
        LocalDate rejectDay;
        int failed;

        @Override
        public LocalDate earliestCovered(
                final String scope, final LocalDate today, final String entity) {
            return null;
        }

        @Override
        public void start(
                final UUID run,
                final String scope,
                final LocalDate date,
                final String requestSha,
                final String entity) {
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
        }

        @Override
        public void fail(final UUID run, final String reason) {
            failed++;
        }

        @Override
        public void close() {}
    }

    private static final class Fixture {
        final Path root;
        final ObjectNode config = CurlSnapshotWire.JSON.createObjectNode();
        final MemoryStore store = new MemoryStore();
        final EnumMap<ReportSnapshotTemplate, JsonNode> infos =
                new EnumMap<>(ReportSnapshotTemplate.class);
        final TreeSet<LocalDate> changedDates = new TreeSet<>();
        final java.util.Set<ReportSnapshotTemplate> templatesWithUpdates =
                java.util.EnumSet.noneOf(ReportSnapshotTemplate.class);
        final List<BusinessDateRange> manifestScopes = new ArrayList<>();
        final Instant changedAt = Instant.parse("2026-11-10T04:30:00Z");
        final LocalDate floor = LocalDate.parse("2026-08-13");
        Instant now = Instant.parse("2026-11-10T06:00:00Z");
        JsonNode lastReport;
        LocalDate emptyDay;
        int fullCalls;
        int updateCalls;

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
                                    "contracts/dataexport/receiving-catalog-0766-v1.json")) {
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
            for (final var template : ReportSnapshotTemplate.values()) {
                saved.entity(template, floor, null, today(), now.minusSeconds(10800))
                        .put("initialComplete", true)
                        .put("watermark", now.minusSeconds(10800).toString());
            }
            saved.save();
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
            final var wire =
                    new ReportSnapshotExtractor.Wire() {
                        @Override
                        public JsonNode info(final ReportSnapshotTemplate template) {
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
                    };
            final ProductionRefreshCycle.CapturePort capture =
                    (template, dates, updates, selected, sink, limits) -> {
                        final var id = UUID.randomUUID();
                        long rows = 0;
                        if (updates != null) {
                            updateCalls++;
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
                                    final var row =
                                            CurlSnapshotWire.JSON
                                                    .createObjectNode()
                                                    .put("sequence_code", 1);
                                    if (template.rowDate() != null) {
                                        row.put(template.rowDate(), changed.toString());
                                    }
                                    sink.accept(id, ++rows, row, new Valid());
                                }
                            }
                        } else {
                            fullCalls++;
                            if (!dates.startInclusive().equals(emptyDay)) {
                                final var row =
                                        CurlSnapshotWire.JSON
                                                .createObjectNode()
                                                .put("synthetic", true);
                                if (template.rowDate() != null) {
                                    row.put(template.rowDate(), dates.startInclusive().toString());
                                }
                                sink.accept(id, ++rows, row, new Valid());
                                sink.accept(id, ++rows, row.deepCopy(), new Valid());
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
                                    new ProductionRefreshCycle.Session(wire, () -> 0),
                            Clock.fixed(now, ZoneOffset.UTC),
                            root);
            final var passed = cycle.run(config);
            lastReport = CurlSnapshotWire.JSON.readTree(Files.readAllBytes(report));
            return passed;
        }
    }
}
