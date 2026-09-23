package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationArtifactScaleIT {
    @TempDir Path folder;
    private static final QualificationConfiguration CONFIGURATION =
            new QualificationConfiguration(60, 90000, 240, 900, 512, 4096, 100000, 67108864);

    @ParameterizedTest
    @ValueSource(ints = {2, 8})
    @Timeout(240)
    void twoBoundedSizesMeasureArtifactReadCaptureMaterializationAndAllComparisons(final int roots)
            throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, true, roots);
        for (final var pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                pool.resetPeakUsage();
            }
        }
        final var metrics = new QualificationMetrics(CONFIGURATION, () -> {});
        final var scenario =
                new LocalArtifactScenario(files.input(), files.oracle(), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var result =
                    scenario.execute(
                            session,
                            CancellationToken.none(),
                            metrics,
                            UUID.randomUUID(),
                            () -> {});
            final var observed = metrics.snapshot();
            final long peak =
                    ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(pool -> pool.getType() == MemoryType.HEAP)
                            .mapToLong(pool -> pool.getPeakUsage().getUsed())
                            .sum();
            assertEquals(QualificationGate.State.PASS_LOCAL, result.selected().state());
            assertEquals(19, result.outputs().size());
            assertEquals(35, result.scopes().size());
            assertEquals(0, observed.inFlight());
            assertTrue(
                    observed.inputs().stream().allMatch(input -> input.retainedPageBytes() == 0));
            assertTrue(observed.largestBatch() <= 1000);
            assertTrue(observed.elapsedMillis() < 240000);
            assertTrue(session.preparedStatements() + session.createdStatements() < 100000);
            assertTrue(Runtime.getRuntime().maxMemory() <= 512L * 1024 * 1024);
            System.out.printf(
                    "ARTIFACT_SCALE roots=%d pages=%d bytes=%d records=%d batches=%d largestBatch=%d"
                            + " heapBefore=%d heapAfter=%d heapPoolPeakSum=%d elapsedMillis=%d jdbc=%d outputs=19 scopes=35%n",
                    roots,
                    observed.pages(),
                    observed.bytes(),
                    observed.records(),
                    observed.batches(),
                    observed.largestBatch(),
                    observed.heapBefore(),
                    observed.heapAfter(),
                    peak,
                    observed.elapsedMillis(),
                    session.preparedStatements() + session.createdStatements());
        }
    }

    @Test
    @Timeout(240)
    void cancellationAfterPreparationStopsComparisonAndTheWholeSessionRollsBack() throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, true, 8);
        final var cancelled = new AtomicBoolean();
        final var metrics = new QualificationMetrics(CONFIGURATION, () -> {});
        final var scenario =
                new LocalArtifactScenario(files.input(), files.oracle(), CancellationToken.none());
        final UUID run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            scenario.execute(
                                    session,
                                    cancelled::get,
                                    metrics,
                                    run,
                                    () -> cancelled.set(true)));
            assertTrue(session.preparedStatements() > 100);
            session.rollback();
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?",
                            run));
            assertEquals(0, session.openControlledStatements());
        }
    }
}
