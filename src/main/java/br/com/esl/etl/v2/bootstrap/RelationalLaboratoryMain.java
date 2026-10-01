package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Opt-in, one-shot fixture commands. Each invocation starts and rolls back its own scenario. */
public final class RelationalLaboratoryMain {
    private RelationalLaboratoryMain() {}

    public static void main(final String[] arguments) {
        System.exit(run(arguments, System.out).code());
    }

    public static RuntimeExitCategory run(final String[] arguments, final PrintStream output) {
        return run(arguments, output, new SqlSession()::execute);
    }

    @FunctionalInterface
    interface SqlCommand {
        JdbcRelationalLaboratory.Status execute(Options options) throws SQLException;
    }

    static RuntimeExitCategory run(
            final String[] arguments, final PrintStream output, final SqlCommand command) {
        final Options options;
        try {
            options = Options.parse(arguments);
        } catch (final IllegalArgumentException failure) {
            output.println("REL_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        }
        try {
            final var status = command.execute(options);
            output.printf(
                    "REL_LAB_ROLLBACK_ONLY command=%s roots=%d/%d/%d links=%d/%d pending=%d"
                            + " quarantine=%d unbound=%d captures=%d rows=%d receipts=%d%n",
                    options.command(),
                    status.manifestos(),
                    status.coletas(),
                    status.fretes(),
                    status.manifestoColetas(),
                    status.coletaFretes(),
                    status.pending(),
                    status.quarantined(),
                    status.unboundCandidates(),
                    status.captures(),
                    status.physicalRows(),
                    status.receipts());
            return status.complete() ? RuntimeExitCategory.SUCCESS : RuntimeExitCategory.DEGRADED;
        } catch (final ResilienceCancelledException failure) {
            output.println("REL_LAB_CANCELLED");
            return RuntimeExitCategory.CANCELLED;
        } catch (final IllegalArgumentException | IllegalStateException failure) {
            output.println("REL_LAB_CONFIG_REJECTED");
            return RuntimeExitCategory.CONFIG_AUTH;
        } catch (final SQLException failure) {
            final var category =
                    failure.getErrorCode() == 53201 || failure.getErrorCode() == 1222
                            ? RuntimeExitCategory.LOCK
                            : RuntimeExitCategory.SOURCE_DQ;
            output.println("REL_LAB_SQL_" + category.name());
            return category;
        } catch (final RuntimeException failure) {
            output.println("REL_LAB_SOURCE_DQ");
            return RuntimeExitCategory.SOURCE_DQ;
        }
    }

    private static final class SqlSession {
        private JdbcRelationalLaboratory.Status execute(final Options options) throws SQLException {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                return RelationalLaboratoryMain.execute(options, session, CancellationToken.none());
            }
        }
    }

    static JdbcRelationalLaboratory.Status execute(
            final Options options,
            final ColetaTemporalLaboratorySession session,
            final CancellationToken cancellation)
            throws SQLException {
        final LocalDate date = LocalDate.of(2036, 4, 1);
        final Clock clock =
                Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        final UUID run = UUID.randomUUID();
        final var policy =
                new RelationalLaboratoryPolicy(
                        date,
                        date.plusDays(options.days() - 1L),
                        10_000,
                        32,
                        3,
                        60,
                        2,
                        1,
                        options.pageSize(),
                        1024);
        return new SqlExecution().execute(options, session, cancellation, date, clock, run, policy);
    }

    private static final class SqlExecution {
        private JdbcRelationalLaboratory.Status execute(
                final Options options,
                final ColetaTemporalLaboratorySession session,
                final CancellationToken cancellation,
                final LocalDate date,
                final Clock clock,
                final UUID run,
                final RelationalLaboratoryPolicy policy)
                throws SQLException {
            final var lab = new JdbcRelationalLaboratory(session, clock);
            lab.start(run, policy, RelationalSyntheticSource.contracts());
            final var executor =
                    new RelationalLaboratoryExecutor(session, run, clock, Clock.systemUTC());
            if (options.command().equals("replay")) {
                for (final var mode :
                        new ExecutionMode[] {ExecutionMode.BOOTSTRAP, ExecutionMode.REPLAY}) {
                    executor.plan(mode, 1, options.roots());
                    for (int ordinal = 1; ordinal <= options.days(); ordinal++) {
                        executor.executePartition(mode, ordinal, cancellation, boundary -> {});
                    }
                }
            } else {
                final var runtime =
                        new LocalRelationalRuntime(session, run, policy, clock, Clock.systemUTC());
                for (int day = 0; day < options.days(); day++) {
                    final LocalDate partition = date.plusDays(day);
                    final int first = 1 + day * options.roots();
                    for (final var template :
                            new DataExportTemplate[] {
                                DataExportTemplate.MANIFESTOS, DataExportTemplate.FRETES
                            }) {
                        runtime.capture(
                                template,
                                partition,
                                ExecutionMode.BOOTSTRAP,
                                null,
                                RelationalLaboratoryFixtures.source(
                                        template,
                                        partition,
                                        first,
                                        options.roots(),
                                        options.pageSize(),
                                        true),
                                cancellation);
                    }
                    lab.bindBatch(
                            run,
                            RelationalLaboratoryFixtures.bindingBatch(
                                    partition, first, options.roots()),
                            cancellation);
                }
                lab.resolve(run, UUID.randomUUID(), cancellation);
                if (!options.command().equals("status")) {
                    for (int day = 0; day < options.days(); day++) {
                        executor.hydrate(
                                options.roots(),
                                cancellation,
                                MonotonicTicker.systemTicker(),
                                RelationalLaboratoryFixtures::hydration);
                    }
                }
            }
            return lab.status(run);
        }
    }

    record Options(String command, int roots, int pageSize, int days) {
        static Options parse(final String[] arguments) {
            if (arguments == null
                    || arguments.length < 2
                    || arguments.length > 5
                    || !Set.of("scenario", "hydrate", "replay", "status").contains(arguments[0])
                    || !"--synthetic-relational-lab".equals(arguments[1])) {
                throw new IllegalArgumentException("REL_LAB_FLAGS");
            }
            int roots = 4;
            int pageSize = 17;
            int days = 1;
            final Set<String> seen = new HashSet<>();
            for (int index = 2; index < arguments.length; index++) {
                final String[] option = arguments[index].split("=", -1);
                if (option.length != 2
                        || !option[1].matches("[1-9][0-9]{0,2}")
                        || !seen.add(option[0])) {
                    throw new IllegalArgumentException("REL_LAB_FLAGS");
                }
                final int value = Integer.parseInt(option[1]);
                switch (option[0]) {
                    case "--roots" -> roots = value;
                    case "--page-size" -> pageSize = value;
                    case "--days" -> days = value;
                    default -> throw new IllegalArgumentException("REL_LAB_FLAGS");
                }
            }
            if (roots > 32 || pageSize > 100 || days > 3) {
                throw new IllegalArgumentException("REL_LAB_BUDGET");
            }
            return new Options(arguments[0], roots, pageSize, days);
        }
    }
}
