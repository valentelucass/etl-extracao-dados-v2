package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportMetadataCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedFreightRefreshTest {
    @TempDir Path directory;

    @Test
    void locationChangesAreFoundEvenWhenBothUpdateQueriesAreEmpty() throws Exception {
        final var f = new Fixture(directory);
        f.locations.put("100", "New branch");
        assertTrue(f.run(8));
        assertEquals(List.of(f.first), f.replaced);
        assertEquals(0, f.updatedRows);
        assertTrue(f.locationScans > 0);
        assertFalse(f.state.has("relatedPending"));
        assertEquals(f.cutoff.toString(), f.state.path("relatedWatermark").asText());
    }

    @Test
    void performanceChangesUseFreightDatesFromTheReferencesRatherThanTheOccurrenceFloor()
            throws Exception {
        final var f = new Fixture(directory);
        f.changedFreights.add(f.freight("100"));
        assertTrue(f.run(8));
        assertEquals(List.of(f.first), f.replaced);
        assertEquals(f.oldServiceDate, f.freightScope.startInclusive());
        assertTrue(f.freightScope.startInclusive().isBefore(f.first));
    }

    @Test
    void newOccurrencesRefreshConsolidationEvenWithoutAConsolidationUpdate() throws Exception {
        final var f = new Fixture(directory, ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA);
        f.changedOccurrences.add(
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("ice_f_e_fit_corporation_sequence_number", 100));
        assertTrue(f.run(8));
        assertEquals(List.of(f.first), f.replaced);
        assertEquals(1, f.updatedRows);
        assertEquals(LocalDate.parse("2026-11-10"), f.occurrenceScope.endInclusive());
    }

    @Test
    void referenceCollisionsRefreshEveryMatchingDateWithoutCollapsingPhysicalRows()
            throws Exception {
        final var f = new Fixture(directory);
        f.remember(f.second, "100", "Old branch", 3);
        f.changedFreights.add(f.freight("100"));
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        assertEquals(2, f.index.read(f.template, f.first).path("physicalRows").asInt());
        assertEquals(3, f.index.read(f.template, f.second).path("physicalRows").asInt());
    }

    @Test
    void missingOrAmbiguousLocationReferencesTriggerCompleteRecapture() throws Exception {
        final var f = new Fixture(directory);
        f.locations.remove("100");
        f.ambiguous = true;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
    }

    @Test
    void sqlFailureKeepsTheDateAndWatermarkAndResumeDoesNotRepeatConfirmedWork() throws Exception {
        final var f = new Fixture(directory);
        f.changedFreights.add(f.freight("100"));
        f.changedFreights.add(f.freight("200"));
        f.reject = f.second;
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertEquals(List.of(f.first), f.replaced);
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
        assertEquals(
                f.second.toString(), f.state.path("relatedPending").path("dates").get(0).asText());
        final int queries = f.queries;
        f.reject = null;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        assertEquals(queries, f.queries);
    }

    @Test
    void resourceBudgetPersistsRemainingDatesWithoutAdvancingTheMarker() throws Exception {
        final var f = new Fixture(directory);
        f.changedFreights.add(f.freight("100"));
        f.changedFreights.add(f.freight("200"));
        assertFalse(f.run(1));
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
        assertEquals(1, f.state.path("relatedPending").path("dates").size());
        assertTrue(f.run(1));
        assertEquals(List.of(f.first, f.second), f.replaced);
    }

    @Test
    void incompleteLookupDoesNotReplaceAnythingOrCommitItsDiscovery() throws Exception {
        final var f = new Fixture(directory);
        f.incomplete = true;
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertTrue(f.replaced.isEmpty());
        assertFalse(f.state.path("relatedPending").path("freightsDone").asBoolean());
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
    }

    @Test
    void changedIndexBytesAreRejectedBeforeAnySourceRequest() throws Exception {
        final var f = new Fixture(directory);
        final var pointer =
                CurlSnapshotWire.JSON.readTree(
                        Files.readAllBytes(
                                directory.resolve("index/OCORRENCIAS/" + f.first + ".json")));
        Files.writeString(Path.of(pointer.path("path").asText()), "{}");
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertEquals(0, f.queries);
        assertTrue(f.replaced.isEmpty());
    }

    @Test
    void nullReferencesAreRecapturedRatherThanAssignedToAnInventedFreight() throws Exception {
        final var f = new Fixture(directory);
        f.remember(f.first, null, "Old branch", 2);
        assertTrue(f.run(8));
        assertEquals(List.of(f.first), f.replaced);
    }

    @Test
    void nullSourceNumberIsPreservedAndCannotMatchAStoredReference() throws Exception {
        final var f = new Fixture(directory);
        f.locations.remove("100");
        f.locations.put(null, "Unknown reference");
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        try (var files = Files.list(directory)) {
            final var capture =
                    files.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .startsWith("LOCALIZACAO_CARGAS-related-"))
                            .findFirst()
                            .orElseThrow();
            assertTrue(Files.readString(capture).contains("\"corporation_sequence_number\":null"));
        }
    }

    @Test
    void legacyQuarantineIsRetainedAndMissingLookupIdentityRefreshesAllMatchingDates()
            throws Exception {
        final var f = new Fixture(directory);
        f.locations.put(null, "Unknown reference");
        f.rejectedLocationRows = 1;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        final var operation = f.operations.get(f.operations.size() - 1);
        assertEquals(1, operation.path("legacyRejectedRows").asInt());
        assertEquals(3, operation.path("referenceProjectionRows").asInt());
        assertEquals(1, operation.path("unassignedReferenceRows").asInt());
        assertFalse(operation.path("acceptedAsCompleteSnapshot").asBoolean());
    }

    @Test
    void invalidLocationProjectionCannotCommitEvenWithAnAcceptedLegacyResult() throws Exception {
        final var f = new Fixture(directory);
        f.invalidLocation = true;
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertTrue(f.replaced.isEmpty());
        assertEquals(0, f.state.path("relatedPending").path("locationNext").asInt());
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
    }

    @Test
    void alreadyQueuedCompletePeriodRequiresEveryReplacementWithoutRedundantLookups()
            throws Exception {
        final var f = new Fixture(directory);
        f.changedFreights.add(f.freight("100"));
        f.changedFreights.add(f.freight("200"));
        f.incompleteLocation = true;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        assertEquals(0, f.locationScans);
        assertEquals(
                "RELATED_COMPLETE_PERIOD_QUEUED",
                f.operations.get(f.operations.size() - 1).path("phase").asText());
        assertEquals(f.cutoff.toString(), f.state.path("relatedWatermark").asText());
    }

    @Test
    void incompleteLocationProjectionKeepsTheCursorDespiteValidReferenceFields() throws Exception {
        final var f = new Fixture(directory);
        f.incompleteLocation = true;
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertTrue(f.replaced.isEmpty());
        assertEquals(0, f.state.path("relatedPending").path("locationNext").asInt());
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
    }

    @Test
    void freightDiscoveryResumesAtTheFailedSmallRangeWithoutLosingEarlierDates() throws Exception {
        final var f = new Fixture(directory);
        final var newerService = LocalDate.parse("2026-11-01");
        f.services.put("200", newerService);
        f.remember(f.second, "200", "Old branch", 2);
        f.changedFreights.add(f.freight("100"));
        f.changedFreights.add(f.freight("200"));
        f.rejectFreightRange = newerService;
        assertThrows(IllegalStateException.class, () -> f.run(8));
        assertTrue(f.replaced.isEmpty());
        assertEquals(1, f.state.path("relatedPending").path("freightNext").asInt());
        assertEquals(1, f.state.path("relatedPending").path("dates").size());
        assertEquals(f.before.toString(), f.state.path("relatedWatermark").asText());
        ((ObjectNode) f.state.required("relatedPending")).remove("freightRanges");
        f.rejectFreightRange = null;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        assertEquals(
                1,
                f.freightScopes.stream()
                        .filter(scope -> scope.startInclusive().equals(f.oldServiceDate))
                        .count());
        assertTrue(
                f.freightScopes.stream()
                        .allMatch(
                                scope ->
                                        java.time.temporal.ChronoUnit.DAYS.between(
                                                        scope.startInclusive(),
                                                        scope.endInclusive())
                                                <= 7));
    }

    @Test
    void nearbyServiceDatesShareLocationScanButUseSeparateFreightQueries() throws Exception {
        final var f = new Fixture(directory);
        f.services.put("200", f.oldServiceDate.plusDays(1));
        f.remember(f.second, "200", "Old branch", 2);
        f.changedFreights.add(f.freight("100"));
        f.locations.put("200", "New branch");
        f.rejectWideFreightRange = true;
        assertTrue(f.run(8));
        assertEquals(List.of(f.first, f.second), f.replaced);
        assertEquals(2, f.freightScopes.size());
        assertTrue(
                f.freightScopes.stream()
                        .allMatch(scope -> scope.startInclusive().equals(scope.endInclusive())));
        assertEquals(1, f.locationScans);
    }

    private static final class Fixture {
        final LocalDate first = LocalDate.parse("2026-11-03");
        final LocalDate second = first.plusDays(1);
        final LocalDate oldServiceDate = LocalDate.parse("2026-10-13");
        final Instant before = Instant.parse("2026-11-10T15:00:00Z");
        final Instant cutoff = before.plusSeconds(3600);
        final ObjectNode state = CurlSnapshotWire.JSON.createObjectNode();
        final com.fasterxml.jackson.databind.node.ArrayNode operations =
                CurlSnapshotWire.JSON.createArrayNode();
        final FreightDependencyIndex index;
        final Path root;
        final ReportSnapshotTemplate template;
        final Map<String, String> locations = new HashMap<>();
        final Map<String, LocalDate> services = new HashMap<>();
        final List<BusinessDateRange> freightScopes = new ArrayList<>();
        final List<JsonNode> changedFreights = new ArrayList<>();
        final List<JsonNode> changedOccurrences = new ArrayList<>();
        final List<LocalDate> replaced = new ArrayList<>();
        BusinessDateRange freightScope;
        BusinessDateRange occurrenceScope;
        LocalDate reject;
        LocalDate rejectFreightRange;
        boolean rejectWideFreightRange;
        boolean ambiguous;
        boolean incomplete;
        boolean incompleteLocation;
        boolean invalidLocation;
        int rejectedLocationRows;
        int queries;
        int locationScans;
        int updatedRows;

        Fixture(final Path root) throws Exception {
            this(root, ReportSnapshotTemplate.OCORRENCIAS);
        }

        Fixture(final Path root, final ReportSnapshotTemplate template) throws Exception {
            this.root = root;
            this.template = template;
            index = new FreightDependencyIndex(root.resolve("index"), root, "scope");
            state.put("floor", first.toString()).put("bootstrapCutoff", before.toString());
            locations.put("100", "Old branch");
            locations.put("200", "Old branch");
            remember(first, "100", "Old branch", 2);
            remember(second, "200", "Old branch", 2);
        }

        ObjectNode freight(final String draft) {
            return CurlSnapshotWire.JSON
                    .createObjectNode()
                    .put("corporation_sequence_number", draft)
                    .put("service_at", services.getOrDefault(draft, oldServiceDate).toString());
        }

        void remember(
                final LocalDate day, final String draft, final String location, final int count)
                throws Exception {
            final var row = CurlSnapshotWire.JSON.createObjectNode();
            final boolean occurrences = template == ReportSnapshotTemplate.OCORRENCIAS;
            row.put(
                    occurrences
                            ? "ice_f_e_fit_corporation_sequence_number"
                            : "ctd_fgs_corporation_sequence_number",
                    draft);
            row.put(
                    occurrences ? "ice_fit_service_at" : "ctd_fgs_service_at",
                    services.getOrDefault(draft, oldServiceDate).toString());
            row.put("ice_fit_fln_cln_nickname", location);
            final var envelope = CurlSnapshotWire.JSON.createObjectNode();
            envelope.set("record", row);
            final var rows = new ArrayList<String>();
            for (int n = 0; n < count; n++) {
                rows.add(envelope.toString());
            }
            index.remember(template, day, UUID.randomUUID(), "a".repeat(64), rows, root);
        }

        boolean run(final int limit) throws Exception {
            final ProductionRefreshCycle.CapturePort capture =
                    (source, dates, updates, wire, sink, limits) -> {
                        queries++;
                        final var rows = new ArrayList<JsonNode>();
                        if (source == ReportSnapshotTemplate.FRETES) {
                            assertNotNull(updates);
                            freightScope = dates;
                            freightScopes.add(dates);
                            if (rejectWideFreightRange
                                    && !dates.startInclusive().equals(dates.endInclusive())) {
                                throw new IllegalStateException("SIMULATED_SOURCE_TIMEOUT");
                            }
                            if (dates.startInclusive().equals(rejectFreightRange)) {
                                throw new IllegalStateException("SOURCE_RANGE_REJECTED");
                            }
                            for (final var row : changedFreights) {
                                final var day = LocalDate.parse(row.path("service_at").asText());
                                if (!day.isBefore(dates.startInclusive())
                                        && !day.isAfter(dates.endInclusive())) {
                                    rows.add(row);
                                }
                            }
                        } else if (source == ReportSnapshotTemplate.OCORRENCIAS) {
                            assertNotNull(updates);
                            occurrenceScope = dates;
                            rows.addAll(changedOccurrences);
                        } else {
                            assertEquals(ReportSnapshotTemplate.LOCALIZACAO_CARGAS, source);
                            assertNull(updates);
                            locationScans++;
                            for (final var entry : locations.entrySet()) {
                                final var day =
                                        services.getOrDefault(entry.getKey(), oldServiceDate);
                                if (!day.isBefore(dates.startInclusive())
                                        && !day.isAfter(dates.endInclusive())) {
                                    rows.add(
                                            freight(entry.getKey())
                                                    .put("fit_fln_cln_nickname", entry.getValue()));
                                }
                            }
                            if (ambiguous) {
                                rows.add(
                                        freight("200")
                                                .put("fit_fln_cln_nickname", "Another branch"));
                            }
                            if (invalidLocation) {
                                ((ObjectNode) rows.get(0)).put("fit_fln_cln_nickname", 12);
                            }
                        }
                        if (updates != null) {
                            updatedRows += rows.size();
                        }
                        final var id = UUID.randomUUID();
                        long ordinal = 0;
                        for (final var row : rows) {
                            sink.accept(id, ++ordinal, row, null);
                        }
                        final int rejected =
                                source == ReportSnapshotTemplate.LOCALIZACAO_CARGAS
                                        ? rejectedLocationRows
                                        : 0;
                        return new ReportSnapshotExtractor.Result(
                                id,
                                rows.size(),
                                rows.size() - rejected,
                                rejected,
                                new ReportMetadataCatalog().fieldCount(source),
                                2,
                                !incomplete
                                        && !(incompleteLocation
                                                && source
                                                        == ReportSnapshotTemplate
                                                                .LOCALIZACAO_CARGAS),
                                false);
                    };
            final var refresh = new RelatedFreightRefresh(index, capture, null, root, operations);
            return refresh.refresh(
                    template,
                    state,
                    second,
                    cutoff,
                    limit,
                    () ->
                            RefreshCheckpoint.atomic(
                                    root.resolve("state.json"),
                                    CurlSnapshotWire.JSON.writeValueAsBytes(state)),
                    day -> {
                        if (day.equals(reject)) {
                            throw new IllegalStateException("SQL_REJECTED");
                        }
                        replaced.add(day);
                    });
        }
    }
}
