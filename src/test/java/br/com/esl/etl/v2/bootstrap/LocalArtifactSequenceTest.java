package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalArtifactSequenceTest {
    @TempDir Path folder;
    @TempDir static Path authored;

    @BeforeAll
    static void authorOnce() throws Exception {
        IntegralSequenceFixtures.write(authored, false, 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void referenceAndSupplementRevisionsPreserveTheCapturedSource(final boolean alternate)
            throws Exception {
        final var sequence =
                new LocalArtifactSequence(
                        SequenceReferenceFixtures.write(folder, alternate),
                        CancellationToken.none());
        assertEquals(
                java.util.List.of("bootstrap", "reference", "recompose"),
                sequence.steps().stream().map(LocalArtifactSequence.Step::id).toList());
        for (int index = 1; index < 3; index++) {
            final var before = sequence.steps().get(index - 1).input().read();
            final var after = sequence.steps().get(index).input().read();
            for (final String field :
                    java.util.List.of("sources", "expansions", "raster", "relations", "revision")) {
                assertEquals(before.path(field), after.path(field), field);
            }
            assertEquals(
                    before.path("supplementRevision").asInt(before.path("revision").asInt()) + 1,
                    after.path("supplementRevision").asInt());
        }
        assertEquals(
                LocalArtifactSequence.Operation.RECOMPOSE, sequence.steps().get(2).operation());
        assertEquals(3, sequence.steps().get(2).referenceRevision());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2037-08-11", "2039-02-05"})
    void frontierOracleRejectsOutOfOrderGapsAndAdvanceFromOtherModes(final String value) {
        final var date = java.time.LocalDate.parse(value);
        final var incremental = br.com.esl.etl.v2.plataforma.controle.ExecutionMode.INCREMENTAL;
        assertEquals(
                date.plusDays(1),
                LocalArtifactSequence.verifyFrontier(incremental, date, date, date.plusDays(1)));
        assertEquals(
                date,
                LocalArtifactSequence.verifyFrontier(incremental, date.plusDays(1), date, date));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalArtifactSequence.verifyFrontier(
                                incremental, date.plusDays(1), date, date.plusDays(2)));
        for (final var mode :
                java.util.List.of(
                        br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BOOTSTRAP,
                        br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL,
                        br.com.esl.etl.v2.plataforma.controle.ExecutionMode.REPLAY)) {
            assertEquals(date, LocalArtifactSequence.verifyFrontier(mode, date, date, date));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalArtifactSequence.verifyFrontier(mode, date, date, date.plusDays(1)));
        }
    }

    private Path sequence() throws Exception {
        try (var paths = Files.walk(authored)) {
            for (final var path : paths.toList()) {
                final var copy = folder.resolve(authored.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy);
                }
            }
        }
        return folder.resolve("sequence.json");
    }

    @Test
    void admitsPinnedThreeStageSequenceAndDetectsTransitiveMutation() throws Exception {
        final var file = sequence();
        final var sequence = new LocalArtifactSequence(file, CancellationToken.none());
        assertEquals(3, sequence.steps().size());
        final var input =
                (ObjectNode) IntegralArtifactFixtures.read(folder.resolve("advanced/input.json"));
        input.put("roots", 3);
        IntegralArtifactFixtures.save(folder.resolve("advanced/input.json"), input);
        assertEquals(
                "LOCAL_PIN_BYTES",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> sequence.verifyFiles(CancellationToken.none()))
                        .getMessage());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "cycle",
                "missing",
                "duplicate",
                "bootstrap",
                "replay-source",
                "replay-reference",
                "unknown",
                "command",
                "escape",
                "seconds",
                "step-seconds",
                "scope"
            })
    void rejectsInvalidSequenceBeforeSql(final String fault) throws Exception {
        final var file = sequence();
        final var root = (ObjectNode) IntegralArtifactFixtures.read(file);
        final var second = (ObjectNode) root.path("steps").get(1);
        switch (fault) {
            case "cycle" -> second.put("predecessor", "later");
            case "missing" -> second.put("predecessor", "absent");
            case "duplicate" -> second.put("id", "bootstrap");
            case "bootstrap" -> second.put("mode", "BOOTSTRAP");
            case "replay-source" -> second.put("sourceRevision", 4);
            case "replay-reference" -> second.put("referenceRevision", 3);
            case "unknown" -> root.put("version", "local-artifact-sequence-v9");
            case "command" -> second.put("sql", "arbitrary");
            case "escape" -> ((ObjectNode) second.path("input")).put("file", "../input.json");
            case "seconds" -> root.put("maximumSeconds", 1801);
            case "step-seconds" -> second.put("maximumSeconds", 241);
            case "scope" -> root.put("tenant", "SYNTHETIC_OTHER");
            default -> throw new IllegalArgumentException(fault);
        }
        IntegralArtifactFixtures.save(file, root);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalArtifactSequence(file, CancellationToken.none()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-due",
                "blackout",
                "expired",
                "catch-up",
                "lookback",
                "unknown",
                "missing"
            })
    void refusesUnconsumableScheduleBeforeSql(final String fault) throws Exception {
        final var file = sequence();
        final var root = (ObjectNode) IntegralArtifactFixtures.read(file);
        root.put("version", "local-artifact-sequence-v2");
        for (final var stage : root.path("steps")) {
            final var schedules = ((ObjectNode) stage).putObject("schedule");
            for (final String family : java.util.List.of("COL", "FRE", "MAN", "COT", "LOC")) {
                schedules
                        .putObject(family)
                        .put("tick", "2037-08-12T12:00:00Z")
                        .put("lookbackSeconds", 0)
                        .put("deadlineSeconds", 172800)
                        .put("maximumCatchUp", 1)
                        .putArray("blackouts");
            }
        }
        final var schedule = (ObjectNode) root.path("steps").get(0).path("schedule").path("COL");
        switch (fault) {
            case "not-due" -> schedule.put("tick", "2037-08-11T12:00:00Z");
            case "blackout" -> schedule.withArray("blackouts").add("2037-08-11");
            case "expired" -> schedule.put("deadlineSeconds", 1);
            case "catch-up" ->
                    schedule.put("tick", "2037-08-13T12:00:00Z").put("maximumCatchUp", 2);
            case "lookback" -> schedule.put("lookbackSeconds", 3600);
            case "unknown" -> schedule.put("arbitrary", true);
            case "missing" -> ((ObjectNode) root.path("steps").get(0)).putNull("schedule");
            default -> throw new IllegalArgumentException(fault);
        }
        IntegralArtifactFixtures.save(file, root);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalArtifactSequence(file, CancellationToken.none()));
    }

    @Test
    void equalEnvelopeHashesInDifferentDirectoriesStillVerifyBothTransitiveGraphs()
            throws Exception {
        final var file = sequence();
        final var initial = folder.resolve("initial");
        final var mirror = folder.resolve("mirror");
        try (var paths = Files.walk(initial)) {
            for (final var path : paths.toList()) {
                final var copy = mirror.resolve(initial.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy);
                }
            }
        }
        final var root = (ObjectNode) IntegralArtifactFixtures.read(file);
        final var second = root.path("steps").get(1);
        ((ObjectNode) second.path("input")).put("file", "mirror/input.json");
        ((ObjectNode) second.path("oracle")).put("file", "mirror/oracle.json");
        Files.writeString(
                mirror.resolve("sources/fre/page-1.json"),
                "\n",
                java.nio.file.StandardOpenOption.APPEND);
        IntegralArtifactFixtures.save(file, root);
        assertEquals(
                "LOCAL_PIN_BYTES",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new LocalArtifactSequence(file, CancellationToken.none()))
                        .getMessage());
    }
}
