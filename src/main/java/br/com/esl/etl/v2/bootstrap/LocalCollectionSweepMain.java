package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;

/** Package consumer for synthetic Coletas observations and the nominal responsibility preview. */
public final class LocalCollectionSweepMain {
    private LocalCollectionSweepMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        try {
            if (arguments.length != 3
                    || !"observe".equals(arguments[0])
                    || !"--input".equals(arguments[1])) {
                throw new IllegalArgumentException("LOCAL_SWEEP_COMMAND");
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
            final var program = new LocalCollectionSweepProgram(Path.of(arguments[2]), token);
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(30, 100000);
                final var observations = program.execute(session, token);
                for (int index = 0; index < observations.size(); index++) {
                    final var result = observations.get(index).result();
                    output.printf(
                            "LOCAL_SWEEP_OBSERVATION phase=%d candidates=%d confirmations=%d reactivated=%d%n",
                            index + 1,
                            result.candidates(),
                            result.confirmations(),
                            result.reactivated());
                }
                for (final var row : observations.get(3).preview()) {
                    output.printf(
                            "LOCAL_SWEEP_PREVIEW responsibility=%s applicability=%s disposition=%s reason=%s%n",
                            row.responsibility().id(),
                            row.responsibility().applicability(),
                            row.assessment().disposition(),
                            row.responsibility().reason());
                }
            }
            output.println("LOCAL_SWEEP_ROLLBACK_CONFIRMED nominal_apply=NONE");
            return RuntimeExitCategory.SUCCESS;
        } catch (final ResilienceCancelledException failure) {
            output.println("LOCAL_SWEEP_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final Exception failure) {
            output.println("LOCAL_SWEEP_INPUT_OR_EXECUTION_REJECTED");
            return RuntimeExitCategory.SOURCE_DQ;
        }
    }
}
