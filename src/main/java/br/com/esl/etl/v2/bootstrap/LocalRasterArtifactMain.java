package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Characterization and rollback capture from the explicitly declared Raster laboratory contract.
 */
public final class LocalRasterArtifactMain {
    private LocalRasterArtifactMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        try {
            if (arguments.length != 3
                    || !Set.of("characterize", "capture").contains(arguments[0])
                    || !"--artifact".equals(arguments[1])) {
                throw new IllegalArgumentException("LOCAL_RASTER_COMMAND");
            }
            final var deadline =
                    ExecutionDeadlines.start(
                            Duration.ofSeconds(240),
                            MonotonicTicker.systemTicker(),
                            CancellationToken.none());
            final CancellationToken token =
                    () -> {
                        deadline.checkpointCycle();
                        return false;
                    };
            final var artifact = new RasterArtifact(Path.of(arguments[2]));
            final var receipt = artifact.characterize(token);
            output.printf(
                    "LOCAL_RASTER_CHARACTERIZATION calls=%d rows=%d invalid=%d complete_synthetic=%s"
                            + " provider=STRUCTURALLY_VALID_UNVERIFIED%n",
                    receipt.calls(),
                    receipt.rows(),
                    receipt.invalid(),
                    receipt.completeSynthetic());
            if (!receipt.executable()) {
                return RuntimeExitCategory.SOURCE_DQ;
            }
            if (arguments[0].equals("characterize")) {
                return RuntimeExitCategory.SUCCESS;
            }
            final var result = new SqlCapture().capture(artifact, token);
            output.printf(
                    "LOCAL_RASTER_CAPTURE applied=%d duplicates=%d quarantine=%d unbound=%d%n",
                    result.applied(), result.duplicates(), result.quarantine(), result.unbound());
            if (!result.complete() || result.quarantine() != 0 || result.unbound() != 0) {
                return RuntimeExitCategory.SOURCE_DQ;
            }
            output.println("LOCAL_RASTER_ROLLBACK_CONFIRMED");
            return RuntimeExitCategory.SUCCESS;
        } catch (final ResilienceCancelledException failure) {
            output.println("LOCAL_RASTER_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final Exception failure) {
            output.println("LOCAL_RASTER_INPUT_OR_EXECUTION_REJECTED");
            return RuntimeExitCategory.SOURCE_DQ;
        }
    }

    private static final class SqlCapture {
        private JdbcRasterLaboratory.Receipt capture(
                final RasterArtifact artifact, final CancellationToken token) throws Exception {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(30, 100000);
                final UUID run = UUID.randomUUID();
                new JdbcRasterLaboratory(session, Clock.systemUTC())
                        .start(
                                run,
                                artifact.window().start(),
                                artifact.window().endExclusive(),
                                artifact.zone(),
                                artifact.maximumRows(),
                                artifact.maximumCalls());
                return new LocalRasterRuntime(session, Clock.systemUTC(), artifact.zone())
                        .capture(
                                run,
                                ExecutionMode.BOOTSTRAP,
                                artifact.window(),
                                artifact.gateway(),
                                artifact.maximumCalls(),
                                artifact.maximumRows(),
                                token)
                        .receipt();
            }
        }
    }
}
