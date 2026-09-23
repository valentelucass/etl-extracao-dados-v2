package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;

/** One-shot file composition, independent comparison and rollback from the closed package. */
public final class LocalArtifactScenarioMain {
    private LocalArtifactScenarioMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        try {
            if (arguments.length != 5
                    || !"run".equals(arguments[0])
                    || !"--input".equals(arguments[1])
                    || !"--oracle".equals(arguments[3])) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_COMMAND");
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
            final var scenario =
                    new LocalArtifactScenario(Path.of(arguments[2]), Path.of(arguments[4]), token);
            final boolean passed;
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(30, 100000);
                final var result = scenario.execute(session, token);
                for (final var comparison : result.outputs()) {
                    output.printf(
                            "LOCAL_SCENARIO_COMPARE contract=%s expected=%d actual=%d differences=%d state=%s%n",
                            comparison.contract(),
                            comparison.expectedRows(),
                            comparison.observedRows(),
                            comparison.differences(),
                            comparison.gate());
                    for (final var sample : comparison.sample()) {
                        output.printf(
                                "LOCAL_SCENARIO_DIFF contract=%s row=%d column=%d kind=%s%n",
                                comparison.contract(),
                                sample.row(),
                                sample.ordinal(),
                                sample.kind());
                    }
                }
                passed = result.selected().state() == QualificationGate.State.PASS_LOCAL;
                output.printf(
                        "LOCAL_SCENARIO_FACT_GRAINS count=5 state=PASS_LOCAL scopes=%d%n",
                        result.scopes().size());
            }
            output.println("LOCAL_SCENARIO_ROLLBACK_CONFIRMED");
            return passed ? RuntimeExitCategory.SUCCESS : RuntimeExitCategory.SOURCE_DQ;
        } catch (final ResilienceCancelledException failure) {
            output.println("LOCAL_SCENARIO_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final SQLException failure) {
            output.println("LOCAL_SCENARIO_SQL_OR_FACT_DIVERGENCE code=" + failure.getErrorCode());
            return RuntimeExitCategory.SOURCE_DQ;
        } catch (final Exception failure) {
            output.println("LOCAL_SCENARIO_INPUT_OR_EXECUTION_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
    }
}
