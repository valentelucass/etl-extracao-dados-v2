package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.UUID;

/** Package entrypoint; the session's existing two opt-ins and rollback guard remain mandatory. */
public final class LocalArtifactSequenceMain {
    private LocalArtifactSequenceMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        try {
            if (arguments.length != 3
                    || !"run".equals(arguments[0])
                    || !"--sequence".equals(arguments[1])) {
                throw new IllegalArgumentException("SEQUENCE_COMMAND");
            }
            final var sequence =
                    new LocalArtifactSequence(Path.of(arguments[2]), CancellationToken.none());
            final boolean passed;
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(60, 100000);
                final var results =
                        sequence.execute(
                                session,
                                UUID.randomUUID(),
                                AnalyticScenarioObserver.NONE,
                                CancellationToken.none(),
                                result -> {
                                    output.printf(
                                            "SEQUENCE_STAGE id=%s operation=%s mode=%s executionRevision=%d sourceRevision=%d "
                                                    + "referenceRevision=%d supplementRevision=%d state=%s millis=%d jdbc=%d%n",
                                            result.id(),
                                            result.operation(),
                                            result.mode(),
                                            result.executionRevision(),
                                            result.sourceRevision(),
                                            result.referenceRevision(),
                                            result.supplementRevision(),
                                            result.comparison().selected().state(),
                                            result.elapsedMillis(),
                                            result.jdbcCalls());
                                    output.printf(
                                            "SEQUENCE_STATE stage=%s captureDate=%s sourceFrontier=%s scopes=%d previews=%d%n",
                                            result.id(),
                                            result.captureDate(),
                                            result.sourceFrontier(),
                                            result.comparison().scopes().size(),
                                            result.comparison().sweepPreview().size());
                                    for (final var preview : result.comparison().sweepPreview()) {
                                        output.printf(
                                                "SEQUENCE_SWEEP stage=%s responsibility=%s disposition=%s reason=%s%n",
                                                result.id(),
                                                preview.responsibility().id(),
                                                preview.assessment().disposition(),
                                                preview.assessment().reason());
                                    }
                                    for (final var comparison : result.comparison().outputs()) {
                                        output.printf(
                                                "SEQUENCE_COMPARE stage=%s contract=%s expected=%d observed=%d differences=%d%n",
                                                result.id(),
                                                comparison.contract(),
                                                comparison.expectedRows(),
                                                comparison.observedRows(),
                                                comparison.differences());
                                    }
                                });
                passed =
                        results.size() == sequence.steps().size()
                                && results.stream()
                                        .allMatch(
                                                value ->
                                                        value.comparison().selected().state()
                                                                == QualificationGate.State
                                                                        .PASS_LOCAL);
            }
            output.println("SEQUENCE_ROLLBACK_CONFIRMED");
            return passed ? RuntimeExitCategory.SUCCESS : RuntimeExitCategory.SOURCE_DQ;
        } catch (final ResilienceCancelledException failure) {
            output.println("SEQUENCE_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final Exception failure) {
            final String code = failure.getMessage();
            output.println(
                    "SEQUENCE_REJECTED type="
                            + failure.getClass().getSimpleName()
                            + (code != null && code.matches("[A-Z][A-Z0-9_]{1,80}")
                                    ? " code=" + code
                                    : ""));
            return RuntimeExitCategory.CONFIG_AUTH;
        }
    }
}
