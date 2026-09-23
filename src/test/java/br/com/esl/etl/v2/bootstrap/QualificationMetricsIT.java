package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationMetricsIT {
    @Test
    @Timeout(120)
    void allElevenActualTransportsAndBatchesIncludingHydrationBalance() throws Exception {
        final var config = configuration();
        final var metrics = new QualificationMetrics(config, () -> {});
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(config.querySeconds(), config.maximumJdbcCalls());
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC(), metrics);
            final var run =
                    runtime.start(UUID.randomUUID(), 2, 2, AnalyticScenarioRuntime.Fault.NONE);
            runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var expected =
                    Map.ofEntries(
                            Map.entry("CAP", 6L),
                            Map.entry("FAT", 6L),
                            Map.entry("INV", 6L),
                            Map.entry("SIN", 6L),
                            Map.entry("MAN", 6L),
                            Map.entry("COL", 6L),
                            Map.entry("FRE", 7L),
                            Map.entry("LOC", 4L),
                            Map.entry("COT", 6L),
                            Map.entry("USER", 2L),
                            Map.entry("RASTER", 12L));
            final var snapshot = metrics.snapshot();
            assertEquals(67, snapshot.records());
            assertEquals(11, snapshot.inputs().size());
            assertEquals(
                    snapshot.pages(),
                    snapshot.inputs().stream()
                            .mapToLong(QualificationMetrics.InputSnapshot::pages)
                            .sum());
            assertEquals(
                    snapshot.bytes(),
                    snapshot.inputs().stream()
                            .mapToLong(QualificationMetrics.InputSnapshot::bytes)
                            .sum());
            assertEquals(
                    snapshot.batches(),
                    snapshot.inputs().stream()
                            .mapToLong(QualificationMetrics.InputSnapshot::batches)
                            .sum());
            for (final var input : snapshot.inputs()) {
                assertEquals(
                        expected.get(input.input().name()).longValue(),
                        input.records(),
                        input.input().name());
                assertTrue(input.pages() > 0 && input.bytes() > 0 && input.batches() > 0);
                assertEquals(0, input.inFlight());
                assertEquals(0, input.retainedPageBytes());
            }
        }
    }

    @Test
    @Timeout(120)
    void entityQuotaStopsCaptureBeforeTheExcessBatchCanBeStaged() throws Exception {
        final var original = configuration();
        final var limited =
                new QualificationConfiguration(
                        original.querySeconds(),
                        original.socketMillis(),
                        original.caseSeconds(),
                        original.campaignSeconds(),
                        original.heapMiB(),
                        4,
                        original.maximumJdbcCalls(),
                        original.maximumBytes());
        final var metrics = new QualificationMetrics(limited, () -> {});
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(limited.querySeconds(), limited.maximumJdbcCalls());
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC(), metrics);
            final var run =
                    runtime.start(UUID.randomUUID(), 2, 2, AnalyticScenarioRuntime.Fault.NONE);
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    runtime.capture(
                                            run,
                                            ExecutionMode.BOOTSTRAP,
                                            1,
                                            false,
                                            null,
                                            CancellationToken.none()));
            assertEquals("QUAL_CAPTURE_ROWS", failure.getMessage());
            assertEquals(4, metrics.snapshot().records());
            assertEquals(0, metrics.snapshot().inFlight());
            assertTrue(
                    metrics.snapshot().inputs().stream()
                            .allMatch(value -> value.retainedPageBytes() == 0));
            assertEquals(0, session.openControlledStatements());
        }
    }

    private static QualificationConfiguration configuration() throws Exception {
        return QualificationConfiguration.read(
                Path.of("src/main/resources/qualification-laboratory/config.synthetic.json"));
    }
}
