package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationTopology;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewBlockReason;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepScope;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test
    void sequencePreviewRequiresAllCatalogueResponsibilitiesBlockedForMissingApplicability() {
        final var rows =
                new SweepResponsibilityPlanner()
                        .bindings("0".repeat(64), "0".repeat(64)).stream()
                                .map(
                                        binding ->
                                                new SweepResponsibilityPlanner.Preview(
                                                        new SweepResponsibilityPlanner
                                                                .Responsibility(
                                                                binding.id(),
                                                                "SYNTHETIC",
                                                                binding.parent(),
                                                                SweepScope.ResponsibilityKind.ROOT,
                                                                SweepApplicability.BLOCKED,
                                                                "",
                                                                "",
                                                                "",
                                                                ""),
                                                        new SweepPreviewAssessment(
                                                                SweepPreviewAssessment.Disposition
                                                                        .BLOCKED,
                                                                SweepPreviewBlockReason
                                                                        .APPLICABILITY_NOT_ENABLED)))
                                .toList();
        assertEquals(rows, LocalArtifactSequence.verifyPreviews(rows));
        assertEquals(
                "SEQUENCE_SWEEP_ORACLE_COUNT",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LocalArtifactSequence.verifyPreviews(rows.subList(0, 32)))
                        .getMessage());
        final var duplicate = new ArrayList<>(rows);
        duplicate.set(1, duplicate.get(0));
        assertEquals(
                "SEQUENCE_SWEEP_ORACLE_DIVERGENCE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LocalArtifactSequence.verifyPreviews(duplicate))
                        .getMessage());
        final var wrongReason = new ArrayList<>(rows);
        wrongReason.set(
                0,
                new SweepResponsibilityPlanner.Preview(
                        rows.get(0).responsibility(),
                        new SweepPreviewAssessment(
                                SweepPreviewAssessment.Disposition.BLOCKED,
                                SweepPreviewBlockReason.MODE_NOT_SWEEP)));
        assertEquals(
                "SEQUENCE_SWEEP_ORACLE_DIVERGENCE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LocalArtifactSequence.verifyPreviews(wrongReason))
                        .getMessage());
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

    @Test
    void reportOracleBindsEveryStageFrontierCoverageAndSweepWithoutSql() throws Exception {
        final var sequence = new LocalArtifactSequence(sequence(), CancellationToken.none());
        final var report = IntegralArtifactFixtures.object();
        final var stages = report.putArray("stages");
        for (final var step : sequence.steps()) {
            final var input = step.input().read();
            final var stage =
                    stages.addObject()
                            .put("id", step.id())
                            .put("operation", step.operation().name())
                            .put("mode", step.mode().name())
                            .put("executionRevision", step.executionRevision())
                            .put("sourceRevision", step.sourceRevision())
                            .put("referenceRevision", step.referenceRevision())
                            .put("inputSha256", step.input().sha256())
                            .put("oracleSha256", step.oracle().sha256())
                            .put("selectedState", "PASS_LOCAL")
                            .put("elapsedMillis", 1)
                            .put("physicalColumns", 971)
                            .put("captureDate", input.path("windowStart").asText())
                            .put("logicalClock", input.path("logicalClock").asText())
                            .put("supplementRevision", input.path("revision").asInt())
                            .put("sourceFrontier", sequence.start().toString());
            final var outputs = stage.putArray("outputs");
            for (final var contract : AnalyticSqlContract.values()) {
                outputs.addObject()
                        .put("contract", contract.id())
                        .put("expectedRows", 0)
                        .put("observedRows", 0)
                        .put("differences", 0)
                        .put("gate", "PASS_LOCAL")
                        .putArray("sample");
            }
            final var scopes = stage.putArray("scopes");
            for (final var node : QualificationTopology.nodes()) {
                scopes.addObject()
                        .put("scope", node.id())
                        .put("state", "PASS_LOCAL")
                        .put("reason", "PROVEN")
                        .put("layer", "ORACLE");
            }
            final var sweep = stage.putArray("sweepPreview");
            for (final var binding :
                    new SweepResponsibilityPlanner().bindings("0".repeat(64), "0".repeat(64))) {
                final var row = sweep.addObject();
                row.putObject("responsibility").put("id", binding.id());
                row.putObject("assessment")
                        .put("disposition", "BLOCKED")
                        .put("reason", "APPLICABILITY_NOT_ENABLED");
            }
            stage.putArray("agenda");
        }
        sequence.verifyReport(report);

        final var frontier = report.deepCopy();
        ((ObjectNode) frontier.path("stages").get(0))
                .put("sourceFrontier", sequence.start().plusDays(1).toString());
        assertEquals(
                "QUAL_SEQUENCE_SOURCE_FRONTIER",
                assertThrows(IllegalArgumentException.class, () -> sequence.verifyReport(frontier))
                        .getMessage());

        final var duplicate = report.deepCopy();
        final var firstSweep = duplicate.path("stages").get(0).path("sweepPreview");
        ((ObjectNode) firstSweep.get(1).path("responsibility"))
                .put("id", firstSweep.get(0).path("responsibility").path("id").asText());
        assertEquals(
                "QUAL_SEQUENCE_SWEEP_ORACLE",
                assertThrows(IllegalArgumentException.class, () -> sequence.verifyReport(duplicate))
                        .getMessage());

        final var rejectedState = report.deepCopy();
        ((ObjectNode) rejectedState.path("stages").get(0)).put("selectedState", "FAILED");
        assertEquals(
                "QUAL_SEQUENCE_STAGE_BINDING",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> sequence.verifyReport(rejectedState))
                        .getMessage());
        final var wrongSupplement = report.deepCopy();
        ((ObjectNode) wrongSupplement.path("stages").get(0)).put("supplementRevision", -1);
        assertEquals(
                "QUAL_SEQUENCE_STAGE_INPUT_METADATA",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> sequence.verifyReport(wrongSupplement))
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
