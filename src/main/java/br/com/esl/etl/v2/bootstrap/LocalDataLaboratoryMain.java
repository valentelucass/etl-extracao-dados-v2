package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Explicit local artifact commands; physical capture is opt-in and always rolled back. */
public final class LocalDataLaboratoryMain {
    private LocalDataLaboratoryMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        try {
            if (arguments.length != 3
                    || !Set.of("characterize", "capture").contains(arguments[0])
                    || !"--artifact".equals(arguments[1])) {
                throw new IllegalArgumentException("LOCAL_DATA_COMMAND");
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
            final var input = ExpansionArtifact.read(Path.of(arguments[2]));
            final var characterization = ExpansionCharacterizer.inspect(input, token);
            output.printf(
                    "LOCAL_DATA_CHARACTERIZATION family=%s pages=%d rows=%d valid=%d invalid=%d"
                            + " unbound=%d complete_synthetic=%s provider=STRUCTURALLY_VALID_UNVERIFIED%n",
                    input.template(),
                    characterization.traversal().pages(),
                    characterization.traversal().rows(),
                    characterization.validRows(),
                    characterization.invalidRows(),
                    characterization.traversal().absentBindings(),
                    characterization.traversal().completeSyntheticTraversal());
            if (arguments[0].equals("characterize")) {
                return characterization.executable()
                        ? RuntimeExitCategory.SUCCESS
                        : RuntimeExitCategory.DEGRADED;
            }
            if (!characterization.executable()) {
                output.println("LOCAL_DATA_CAPTURE_BLOCKED_BEFORE_SQL");
                return RuntimeExitCategory.SOURCE_DQ;
            }
            final var policy =
                    new ExpansionPolicy(
                            input.date(),
                            input.date().plusDays(1),
                            input.date(),
                            input.pageSize(),
                            input.maximumPages(),
                            input.maximumRows(),
                            FiscalPolicy.SYNTHETIC_CTE);
            final var source = input.source(ExpansionCaptureSource.Observer.NONE);
            source.validate(input.template(), input.date(), policy);
            final var clock =
                    Clock.fixed(
                            input.date()
                                    .plusDays(1)
                                    .atStartOfDay(ExpansionFreshness.ZONE)
                                    .toInstant(),
                            ExpansionFreshness.ZONE);
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(30, 100000);
                final UUID run = UUID.randomUUID();
                new JdbcExpansionLaboratory(session, clock).start(run, policy);
                final var result =
                        new LocalExpansionRuntime(session, run, policy, clock, Clock.systemUTC())
                                .capture(
                                        input.template(),
                                        input.date(),
                                        ExecutionMode.BOOTSTRAP,
                                        null,
                                        source,
                                        token);
                output.printf(
                        "LOCAL_DATA_CAPTURE_ROLLBACK_ONLY family=%s pages=%d bytes=%d%n",
                        input.template(),
                        result.metrics().fetchedPages(),
                        result.metrics().bytes());
            }
            output.println("LOCAL_DATA_ROLLBACK_CONFIRMED");
            return RuntimeExitCategory.SUCCESS;
        } catch (final ResilienceCancelledException failure) {
            output.println("LOCAL_DATA_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final SQLException failure) {
            output.println("LOCAL_DATA_SQL_ERROR code=" + failure.getErrorCode());
            return RuntimeExitCategory.SOURCE_DQ;
        } catch (final IOException | RuntimeException failure) {
            output.println("LOCAL_DATA_INPUT_OR_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
    }
}
