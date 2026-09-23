package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationRasterCancellationIT {
    @TempDir Path folder;

    @Test
    @Timeout(90)
    void cancellationAfterLastStagedBatchCannotSealOrPublishArtifact() throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        final var artifact = new RasterArtifact(file);
        final var cancelled = new AtomicBoolean();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            new JdbcRasterLaboratory(session, Clock.systemUTC())
                    .start(
                            run,
                            artifact.window().start(),
                            artifact.window().endExclusive(),
                            artifact.zone(),
                            100000,
                            1000);
            final var runtime =
                    new LocalRasterRuntime(
                            session,
                            Clock.systemUTC(),
                            artifact.zone(),
                            new LocalRasterRuntime.Observer() {
                                @Override
                                public void batchStaged(final int count) {
                                    cancelled.set(true);
                                }
                            });
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            runtime.capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    artifact.window(),
                                    artifact.gateway(),
                                    1000,
                                    100000,
                                    cancelled::get));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_raster_capture WHERE run_id=?",
                            run));
        }
    }
}
