package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class SequenceScaleIT {
    @TempDir Path folder;
    private boolean scaleBlocked;
    private int nextScale;
    private final java.util.Set<UUID> observedRuns = new java.util.HashSet<>();

    @RegisterExtension
    final TestWatcher failureWatcher =
            new TestWatcher() {
                @Override
                public void testFailed(final ExtensionContext context, final Throwable cause) {
                    // Also fence failures reported by JUnit after the method body, such as timeout.
                    scaleBlocked = true;
                }
            };

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16})
    @Timeout(1800)
    void boundedTraceMeasuresCompleteSequenceAtFourMasses(final int roots) throws Exception {
        beginScale(roots);
        final boolean alternate = roots == 4 || roots == 16;
        final var file =
                IntegralCampaignFixtures.write(folder.resolve("scale-" + roots), alternate, roots);
        final var counts =
                new QualificationMetrics(
                        new QualificationConfiguration(
                                60, 65000, 300, 3600, 512, 4096, 100000, 67108864),
                        () -> {},
                        1800);
        final var measurements = new SequenceMeasurements(counts);
        final long preflightStart = System.nanoTime();
        final var sequence = new LocalArtifactSequence(file, CancellationToken.none());
        final long preflightMillis = (System.nanoTime() - preflightStart) / 1_000_000;
        final var output = Path.of("target", "sequence-scale", "roots-" + roots);
        Files.createDirectories(output);
        final var run = UUID.randomUUID();
        assertTrue(observedRuns.add(run), "SEQUENCE_SCALE_RUN_REUSED");
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            final var before = QualificationSqlEvidence.snapshot(session);
            final var results =
                    sequence.execute(
                            session,
                            run,
                            measurements,
                            CancellationToken.none(),
                            stage -> measurements.sample("STAGE"));
            assertEquals(7, results.size());
            for (final var stage : results) {
                IntegralArtifactReplayIT.exact(stage.comparison());
                assertEquals(33, stage.comparison().sweepPreview().size());
            }
            final var observed = counts.snapshot();
            assertEquals(0, observed.inFlight());
            assertTrue(
                    observed.inputs().stream().allMatch(input -> input.retainedPageBytes() == 0));
            assertTrue(measurements.snapshot().samples().size() > 2);
            final var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            final var report =
                    IntegralArtifactFixtures.object()
                            .put("roots", roots)
                            .put("pageSize", sequence.pageSize())
                            .put("sourceChildren", roots * 8)
                            .put("supplementRowsVaryWithRoots", true)
                            .put("preflightMillis", preflightMillis)
                            .put("heapPlateauProven", false)
                            .put("productionSloProven", false)
                            .put("maximumStageSeconds", 240)
                            .put("maximumSequenceSeconds", 1800)
                            .put(
                                    "jdbcCalls",
                                    session.preparedStatements() + session.createdStatements());
            report.set("capture", mapper.valueToTree(observed));
            report.set("timeline", mapper.valueToTree(measurements.snapshot()));
            final var stages = report.putArray("stages");
            for (final var stage : results) {
                stages.addObject()
                        .put("id", stage.id())
                        .put("millis", stage.elapsedMillis())
                        .put("jdbcCalls", stage.jdbcCalls())
                        .put("state", stage.comparison().selected().state().name())
                        .put("outputs", stage.comparison().outputs().size())
                        .put("previews", stage.comparison().sweepPreview().size());
            }
            AnalyticLaboratoryScaleIT.actualPlans(
                    session,
                    run,
                    output,
                    "sequence",
                    List.of(
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_02 WHERE run_id=?",
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=?",
                            "SELECT next_date FROM ctl.analytic_scenario_frontier WHERE run_id=?"));
            session.rollback();
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?"));
            report.put("runRowsAfterRollback", 0);
            final var after = QualificationSqlEvidence.snapshot(session);
            assertEquals(before.tables(), after.tables(), "SEQUENCE_SCALE_AGGREGATE_DRIFT");
            report.set("countsBefore", mapper.valueToTree(before.tables()));
            report.set("countsAfter", mapper.valueToTree(after.tables()));
            report.put("aggregateTablesCompared", before.tables().size())
                    .put("aggregatesPreserved", true)
                    .put("isolatedRun", true)
                    .put(
                            "terminalState",
                            results.get(results.size() - 1).comparison().selected().state().name());
            IntegralArtifactFixtures.save(output.resolve("measurement.json"), report);
        }
        completeScale();
    }

    void beginScale(final int roots) {
        assertTrue(!scaleBlocked, "SEQUENCE_SCALE_PREVIOUS_FAILURE_NO_MORE_IO");
        scaleBlocked = true;
        assertTrue(nextScale < 4, "SEQUENCE_SCALE_CAMPAIGN_ALREADY_COMPLETE");
        assertEquals(List.of(2, 4, 8, 16).get(nextScale).intValue(), roots);
    }

    void completeScale() {
        nextScale++;
        scaleBlocked = false;
    }
}
