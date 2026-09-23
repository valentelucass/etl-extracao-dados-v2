package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;

/** Offline characterization through the delivered profiles and typed mappers. */
public final class LocalProfileCharacterizationMain {
    private LocalProfileCharacterizationMain() {}

    public static RuntimeExitCategory run(final String[] args, final PrintStream output) {
        try {
            if (args.length != 3
                    || !"characterize".equals(args[0])
                    || !"--artifact".equals(args[1])) {
                throw new IllegalArgumentException("LOCAL_PROFILE_COMMAND");
            }
            final var deadline =
                    ExecutionDeadlines.start(
                            Duration.ofSeconds(240),
                            MonotonicTicker.systemTicker(),
                            CancellationToken.none());
            final var result =
                    new LocalProfileArtifact(Path.of(args[2]))
                            .inspect(
                                    () -> {
                                        deadline.checkpointCycle();
                                        return false;
                                    });
            output.printf(
                    "LOCAL_PROFILE family=%s input=%s pages=%d rows=%d invalid=%d"
                            + " freshness_unavailable=%d complete_synthetic=%s structural=%s"
                            + " provider=UNVERIFIED nominal_parity=BLOCKED%n",
                    result.family(),
                    result.inputSha256(),
                    result.pages(),
                    result.rows(),
                    result.invalid(),
                    result.freshnessUnavailable(),
                    result.completeSynthetic(),
                    result.structurallyValid());
            return result.structurallyValid()
                    ? RuntimeExitCategory.SUCCESS
                    : RuntimeExitCategory.DEGRADED;
        } catch (final ResilienceCancelledException failure) {
            output.println("LOCAL_PROFILE_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final IOException | RuntimeException failure) {
            output.println("LOCAL_PROFILE_INPUT_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
    }
}
