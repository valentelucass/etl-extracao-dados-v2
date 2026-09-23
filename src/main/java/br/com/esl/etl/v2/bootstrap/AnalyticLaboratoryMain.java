package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Packaged one-shot commands; each process builds and rolls back its own complete scenario. */
public final class AnalyticLaboratoryMain {
    private AnalyticLaboratoryMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        final AnalyticLaboratoryOptions options;
        try {
            options = AnalyticLaboratoryOptions.parse(arguments);
        } catch (final IllegalArgumentException failure) {
            output.println("ANA_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
        final long started = System.nanoTime();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
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
            final var status = execute(options, session, token, output);
            output.printf(
                    "ANA_LAB_ROLLBACK_ONLY command=%s state=%s failure=%s elapsed_ms=%d jdbc_calls=%d%n",
                    options.command(),
                    status.state(),
                    status.failure() == null ? "NONE" : status.failure(),
                    (System.nanoTime() - started) / 1_000_000,
                    session.preparedStatements() + session.createdStatements());
            return status.complete() ? RuntimeExitCategory.SUCCESS : RuntimeExitCategory.DEGRADED;
        } catch (final ResilienceCancelledException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            output.println("ANA_LAB_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final IllegalArgumentException | IllegalStateException failure) {
            output.println("ANA_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        } catch (final SQLException failure) {
            output.println("ANA_LAB_SQL_ERROR code=" + failure.getErrorCode());
            return failure.getErrorCode() == 53502 || failure.getErrorCode() == 1222
                    ? RuntimeExitCategory.LOCK
                    : RuntimeExitCategory.SOURCE_DQ;
        } catch (final Exception failure) {
            output.println("ANA_LAB_SOURCE_ERROR type=" + failure.getClass().getSimpleName());
            return RuntimeExitCategory.SOURCE_DQ;
        }
    }

    static JdbcAnalyticScenario.Status execute(
            final AnalyticLaboratoryOptions options,
            final ColetaTemporalLaboratorySession session,
            final CancellationToken token,
            final PrintStream output)
            throws Exception {
        final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
        final var run = runtime.start(options.roots(), options.pageSize(), options.fault());
        var cycle = runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, token);
        if (cycle.status().complete()) {
            if (options.command().equals("scenario") || options.command().equals("recompose")) {
                cycle = runtime.capture(run, ExecutionMode.INCREMENTAL, 2, false, cycle, token);
                cycle = runtime.capture(run, ExecutionMode.BACKFILL, 3, true, cycle, token);
                final var previousCollectors = cycle.intent().mat02();
                cycle = runtime.capture(run, ExecutionMode.REPLAY, 4, true, cycle, token);
                requireReplayNoop(cycle);
                new JdbcAnalyticScenario(session)
                        .verifyCollectorReplay(previousCollectors, cycle.intent().mat02());
            } else if (options.command().equals("replay")) {
                final var previousCollectors = cycle.intent().mat02();
                cycle = runtime.capture(run, ExecutionMode.REPLAY, 2, false, cycle, token);
                requireReplayNoop(cycle);
                new JdbcAnalyticScenario(session)
                        .verifyCollectorReplay(previousCollectors, cycle.intent().mat02());
            }
            new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), options.roots());
            if (options.command().equals("scenario")
                    || options.contract() == AnalyticSqlContract.SQL_04) {
                absence(run, session, token, output, options.command().equals("scenario"));
            }
        }
        final var queries = new JdbcAnalyticQueries(session);
        if (options.contract() != null) {
            print(
                    queries.read(
                            run.id(), options.contract(), 2, options.limit(), token, row -> {}),
                    output);
        } else {
            for (final var contract : AnalyticSqlContract.values()) {
                print(
                        queries.read(run.id(), contract, 2, options.limit(), token, row -> {}),
                        output);
            }
        }
        return new JdbcAnalyticScenario(session).status(cycle.intent().cycle());
    }

    private static void requireReplayNoop(final AnalyticScenarioRuntime.Cycle cycle)
            throws SQLException {
        if (cycle.freight().inserts()
                        + cycle.freight().updates()
                        + cycle.manifests().inserts()
                        + cycle.manifests().updates()
                        + cycle.collectors().inserts()
                != 0) {
            throw new SQLException("ANA_LAB_REPLAY_BUSINESS_CHANGED");
        }
    }

    private static void absence(
            final AnalyticScenarioRuntime.Run run,
            final ColetaTemporalLaboratorySession session,
            final CancellationToken token,
            final PrintStream output,
            final boolean reappear)
            throws SQLException {
        final var sweep =
                new LocalAnalyticCollectionSweep(
                        session,
                        run.id(),
                        run.relational(),
                        AnalyticScenarioRuntime.policy(run.pageSize()),
                        AnalyticScenarioRuntime.LOGICAL_CLOCK,
                        Clock.systemUTC());
        final var snapshot =
                new SyntheticCollectionSnapshot(
                        run.id(), AnalyticScenarioRuntime.START, run.roots(), true);
        sweep.observe(snapshot, UUID.randomUUID(), token);
        sweep.observe(snapshot, UUID.randomUUID(), token);
        final var query = new JdbcAnalyticQueries(session);
        final var confirmed =
                query.read(run.id(), AnalyticSqlContract.SQL_04, 2, 4096, token, row -> {});
        if (confirmed.rows() != 1) {
            throw new SQLException("ANA_LAB_ABSENCE_ORACLE");
        }
        print(confirmed, output);
        if (reappear) {
            sweep.observe(
                    new SyntheticCollectionSnapshot(
                            run.id(), AnalyticScenarioRuntime.START, run.roots(), false),
                    UUID.randomUUID(),
                    token);
            if (query.read(run.id(), AnalyticSqlContract.SQL_04, 2, 4096, token, row -> {}).rows()
                    != 0) {
                throw new SQLException("ANA_LAB_REAPPEARANCE_ORACLE");
            }
        }
    }

    private static void print(
            final JdbcAnalyticQueries.ReadReceipt receipt, final PrintStream output) {
        output.printf(
                "ANA_LAB_QUERY contract=%s rows=%d columns=%d characters=%d%n",
                receipt.query(), receipt.rows(), receipt.columns(), receipt.textCharacters());
    }
}
