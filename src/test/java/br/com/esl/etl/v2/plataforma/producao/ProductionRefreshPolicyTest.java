package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionRefreshPolicyTest {
    @TempDir Path temporary;

    @Test
    void ninetyDaysAreTheMinimumInitialImportAndNotAnAutomaticRetentionLimit() {
        final var today = LocalDate.parse("2026-11-10");
        final var config = CurlSnapshotWire.JSON.createObjectNode();
        assertEquals(
                today.minusDays(89), ProductionRefreshPolicy.from(config, today).requestedStart());
        config.put("historyDays", 89);
        assertThrows(
                IllegalArgumentException.class, () -> ProductionRefreshPolicy.from(config, today));
        config.put("historyDays", 180);
        assertEquals(
                today.minusDays(179), ProductionRefreshPolicy.from(config, today).requestedStart());
        config.put("historyStart", "2026-11-01");
        assertThrows(
                IllegalArgumentException.class, () -> ProductionRefreshPolicy.from(config, today));
        config.remove("historyStart");
        config.put("waitAfterCompletionMinutes", 59);
        assertThrows(
                IllegalArgumentException.class, () -> ProductionRefreshPolicy.from(config, today));
    }

    @Test
    void waitingStartsAfterCompletionRatherThanAfterThePreviousStart() {
        final var began = Instant.parse("2026-11-10T13:00:00Z");
        final var completed = began.plus(Duration.ofMinutes(20));
        assertEquals(
                Instant.parse("2026-11-10T14:20:00Z"),
                ProductionRefreshPolicy.nextInvocation(completed));
    }

    @Test
    void timestampWindowsOverlapWithoutRoundingAwaySecondsOrAdvancingPastCutoff() {
        final var committed = Instant.parse("2026-11-10T10:03:17Z");
        final var cutoff = Instant.parse("2026-11-10T10:40:19Z");
        final var window = ProductionRefreshPolicy.nextWindow(committed, cutoff, false);
        assertEquals(committed.minusSeconds(120), window.query().startInclusive());
        assertEquals(cutoff, window.query().endInclusive());
        assertEquals(cutoff, window.commitThrough());
        assertNull(ProductionRefreshPolicy.nextWindow(cutoff, cutoff, false));
    }

    @Test
    void dateOnlyWindowsRevisitTheCurrentDayAndCatchUpOlderDaysWithoutStalling() {
        final var cutoff = Instant.parse("2026-11-10T16:00:00Z");
        var marker = Instant.parse("2026-11-05T16:00:00Z");
        int windows = 0;
        while (marker.isBefore(cutoff)) {
            final var next = ProductionRefreshPolicy.nextWindow(marker, cutoff, true);
            assertTrue(next.commitThrough().isAfter(marker));
            marker = next.commitThrough();
            assertTrue(++windows < 10);
        }
        assertEquals(cutoff, marker);
        final var later =
                ProductionRefreshPolicy.nextWindow(marker, cutoff.plusSeconds(3600), true);
        assertEquals(
                LocalDate.parse("2026-11-09"),
                later.query()
                        .startInclusive()
                        .atZone(
                                br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract
                                        .SOURCE_ZONE)
                        .toLocalDate());
    }

    @Test
    void checkpointPersistsProgressAndKeepsOlderLoadedHistory() throws Exception {
        final var path = temporary.resolve("state.json");
        final var now = Instant.parse("2026-11-10T16:00:00Z");
        final var start = LocalDate.parse("2026-08-13");
        final var checkpoint = new RefreshCheckpoint(path, "source-test", now);
        final var entry =
                checkpoint.entity(
                        ReportSnapshotTemplate.SINISTROS,
                        start,
                        start.minusDays(20),
                        LocalDate.parse("2026-11-10"),
                        now);
        entry.put("initialNext", "2026-11-05");
        checkpoint.save();
        final var resumed = new RefreshCheckpoint(path, "source-test", now.plusSeconds(3600));
        final var restored =
                resumed.entity(
                        ReportSnapshotTemplate.SINISTROS,
                        start.plusDays(30),
                        null,
                        LocalDate.parse("2026-12-10"),
                        now.plusSeconds(3600));
        assertEquals(start.minusDays(20).toString(), restored.path("floor").asText());
        assertEquals("2026-11-05", restored.path("initialNext").asText());
        final var corrupt = CurlSnapshotWire.JSON.readTree(Files.readAllBytes(path));
        ((com.fasterxml.jackson.databind.node.ObjectNode) corrupt.required("payload"))
                .put("sourceScope", "changed");
        Files.write(path, CurlSnapshotWire.JSON.writeValueAsBytes(corrupt));
        assertThrows(
                IllegalStateException.class, () -> new RefreshCheckpoint(path, "source-test", now));
    }

    @Test
    void restartingTheProcessCannotSkipTheRemainingWaitAfterCompletion() throws Exception {
        final var completed = Instant.parse("2026-11-10T13:20:00.900Z");
        final var path = temporary.resolve("restart.json");
        final var state = new RefreshCheckpoint(path, "source-test", completed);
        state.completed(completed);
        final var resumed = new RefreshCheckpoint(path, "source-test", completed.plusSeconds(60));
        assertThrows(
                IllegalStateException.class,
                () -> resumed.requireEligible(completed.plusSeconds(3599)));
        resumed.requireEligible(completed.plusSeconds(3600));
    }
}
