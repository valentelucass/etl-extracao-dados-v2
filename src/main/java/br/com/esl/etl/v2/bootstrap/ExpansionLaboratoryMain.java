package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRecomposition;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionStatus;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Packaged one-shot composition. Every invocation prepares and rolls back its own scenario. */
public final class ExpansionLaboratoryMain {
    private ExpansionLaboratoryMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        return run(arguments, output, new SqlSession()::execute);
    }

    @FunctionalInterface
    interface SqlCommand {
        Result execute(Options options) throws SQLException;
    }

    static RuntimeExitCategory run(
            final String[] arguments, final PrintStream output, final SqlCommand command) {
        final Options options;
        try {
            options = Options.parse(arguments);
        } catch (final IllegalArgumentException failure) {
            output.println("EXP_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
        try {
            final var result = command.execute(options);
            output.printf(
                    "EXP_LAB_ROLLBACK_ONLY command=%s projection=%s detail_rows=%d %s%n",
                    options.command(), options.projection(), result.detailRows(), result.status());
            return result.status().complete()
                    ? RuntimeExitCategory.SUCCESS
                    : RuntimeExitCategory.DEGRADED;
        } catch (final ResilienceCancelledException failure) {
            output.println("EXP_LAB_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (
                final br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException failure) {
            output.println("EXP_LAB_TIMEOUT");
            return RuntimeExitCategory.SOURCE_DQ;
        } catch (final IllegalArgumentException | IllegalStateException failure) {
            output.println("EXP_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        } catch (final SQLException failure) {
            final var category =
                    failure.getErrorCode() == 53401 || failure.getErrorCode() == 1222
                            ? RuntimeExitCategory.LOCK
                            : RuntimeExitCategory.SOURCE_DQ;
            output.println("EXP_LAB_SQL_" + category.name());
            return category;
        }
    }

    private static final class SqlSession {
        private Result execute(final Options options) throws SQLException {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var deadlines =
                        ExecutionDeadlines.start(
                                Duration.ofSeconds(240),
                                MonotonicTicker.systemTicker(),
                                CancellationToken.none());
                final CancellationToken cancellation =
                        () -> {
                            deadlines.checkpointCycle();
                            return false;
                        };
                return ExpansionLaboratoryMain.execute(options, session, cancellation);
            }
        }
    }

    static Result execute(
            final Options options,
            final ColetaTemporalLaboratorySession session,
            final CancellationToken cancellation)
            throws SQLException {
        final LocalDate date = LocalDate.of(2036, 4, 1);
        final Clock clock =
                Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        final UUID run = UUID.randomUUID();
        final var policy =
                new ExpansionPolicy(
                        date,
                        date.plusDays(options.days()),
                        LocalDate.of(2036, 4, 15),
                        options.pageSize(),
                        1000,
                        10000,
                        FiscalPolicy.SYNTHETIC_CTE);
        final var slots = new ArrayList<JdbcExpansionRecomposition.Slot>(options.days());
        for (int day = 0; day < options.days(); day++) {
            slots.add(
                    new JdbcExpansionRecomposition.Slot(
                            day + 1,
                            date.plusDays(day),
                            1 + day * options.roots(),
                            options.roots(),
                            true,
                            1,
                            1));
        }
        return new SqlExecution()
                .execute(options, session, cancellation, clock, run, policy, slots);
    }

    private static final class SqlExecution {
        private Result execute(
                final Options options,
                final ColetaTemporalLaboratorySession session,
                final CancellationToken cancellation,
                final Clock clock,
                final UUID run,
                final ExpansionPolicy policy,
                final List<JdbcExpansionRecomposition.Slot> slots)
                throws SQLException {
            new JdbcExpansionLaboratory(session, clock).start(run, policy);
            final var plans = new JdbcExpansionRecomposition(session, clock);
            plans.plan(run, ExecutionMode.BOOTSTRAP, 1, null, slots);
            final var executor =
                    new ExpansionLaboratoryExecutor(session, run, clock, Clock.systemUTC());
            final boolean deferred = Set.of("status", "hydrate").contains(options.command());
            for (int ordinal = 1; ordinal <= options.days(); ordinal++) {
                executor.execute(
                        ExecutionMode.BOOTSTRAP,
                        1,
                        ordinal,
                        !deferred,
                        cancellation,
                        boundary -> {});
            }
            if (options.command().equals("hydrate") || options.command().equals("replay")) {
                final var mode =
                        options.command().equals("hydrate")
                                ? ExecutionMode.BACKFILL
                                : ExecutionMode.REPLAY;
                plans.plan(run, mode, 1, mode == ExecutionMode.REPLAY ? 1 : null, slots);
                for (int ordinal = options.days(); ordinal >= 1; ordinal--) {
                    executor.execute(mode, 1, ordinal, true, cancellation, boundary -> {});
                }
            }
            int detailRows = 0;
            if (options.command().equals("query")) {
                final var queries = new JdbcExpansionQueries(session);
                detailRows =
                        switch (options.projection()) {
                            case "INVOICE" ->
                                    queries.invoiceFactsPage(run, options.after(), options.limit())
                                            .size();
                            case "REVENUE" ->
                                    queries.revenueFactsPage(run, options.after(), options.limit())
                                            .size();
                            default ->
                                    queries.detailPage(
                                                    run,
                                                    JdbcExpansionQueries.Vertical.valueOf(
                                                            options.projection()),
                                                    1,
                                                    options.after(),
                                                    options.limit())
                                            .size();
                        };
            }
            return new Result(new JdbcExpansionStatus(session).read(run), detailRows);
        }
    }

    record Result(JdbcExpansionStatus.Status status, int detailRows) {}

    record Options(
            String command,
            int roots,
            int pageSize,
            int days,
            String projection,
            int limit,
            long after) {
        static Options parse(final String[] arguments) {
            if (arguments == null
                    || arguments.length < 2
                    || arguments.length > 8
                    || !Set.of("scenario", "hydrate", "replay", "status", "query")
                            .contains(arguments[0])
                    || !"--synthetic-expansion-lab".equals(arguments[1])) {
                throw new IllegalArgumentException("EXP_LAB_FLAGS");
            }
            int roots = 2, pageSize = 3, days = 1, limit = 20;
            long after = 0;
            String projection = "NONE";
            final Set<String> seen = new HashSet<>();
            for (int index = 2; index < arguments.length; index++) {
                final String[] option = arguments[index].split("=", -1);
                if (option.length != 2 || !seen.add(option[0])) {
                    throw new IllegalArgumentException("EXP_LAB_FLAGS");
                }
                if (option[0].equals("--projection")) {
                    if (!List.of("CAP", "FAT", "INV", "SIN", "INVOICE", "REVENUE")
                            .contains(option[1])) {
                        throw new IllegalArgumentException("EXP_LAB_PROJECTION");
                    }
                    projection = option[1];
                    continue;
                }
                if (!option[1].matches("0|[1-9][0-9]{0,6}")) {
                    throw new IllegalArgumentException("EXP_LAB_NUMBER");
                }
                final int value = Integer.parseInt(option[1]);
                switch (option[0]) {
                    case "--roots" -> roots = value;
                    case "--page-size" -> pageSize = value;
                    case "--days" -> days = value;
                    case "--limit" -> limit = value;
                    case "--after" -> after = value;
                    default -> throw new IllegalArgumentException("EXP_LAB_FLAGS");
                }
            }
            if (roots < 1
                    || roots > 256
                    || pageSize < 1
                    || pageSize > 16
                    || days < 1
                    || days > 3
                    || roots * days > 256
                    || limit < 1
                    || limit > 100
                    || (arguments[0].equals("query") == projection.equals("NONE"))
                    || (!arguments[0].equals("query")
                            && (seen.contains("--limit") || seen.contains("--after")))) {
                throw new IllegalArgumentException("EXP_LAB_BUDGET_OR_QUERY_SCOPE");
            }
            return new Options(arguments[0], roots, pageSize, days, projection, limit, after);
        }
    }
}
