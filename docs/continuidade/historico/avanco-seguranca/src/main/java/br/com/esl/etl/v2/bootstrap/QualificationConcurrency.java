package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.LaboratoryJdbcBudget;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Two owned SPIDs contend in the actual claim procedure, then rebuild and consume after rollback.
 */
public final class QualificationConcurrency {
    public record Result(
            int ownerSpid,
            int contenderSpid,
            int timeoutCode,
            int cancelledStatements,
            long cancellationMillis,
            int consumed,
            long jdbcCalls,
            boolean rollback) {}

    private QualificationConcurrency() {}

    public static Result execute(
            final QualificationConfiguration configuration, final CancellationToken token)
            throws Exception {
        return execute(
                configuration,
                token,
                new LaboratoryJdbcBudget(configuration.maximumJdbcCalls()),
                AnalyticScenarioObserver.NONE);
    }

    public static Result execute(
            final QualificationConfiguration configuration,
            final CancellationToken token,
            final LaboratoryJdbcBudget budget,
            final AnalyticScenarioObserver observer)
            throws Exception {
        final String before = QualificationSqlEvidence.snapshot(configuration).sha256();
        final UUID run = UUID.randomUUID();
        final var policy =
                new ExpansionPolicy(
                        AnalyticScenarioRuntime.START,
                        AnalyticScenarioRuntime.END,
                        AnalyticScenarioRuntime.START,
                        2,
                        100,
                        1000,
                        FiscalPolicy.SYNTHETIC_CTE);
        final Result result;
        try (var owner = ColetaTemporalLaboratorySession.open(configuration.jdbcUrl());
                var contender = ColetaTemporalLaboratorySession.open(configuration.jdbcUrl())) {
            owner.controlStatements(configuration.querySeconds(), budget);
            contender.controlStatements(configuration.querySeconds(), budget);
            final int firstSpid = QualificationSqlEvidence.spid(owner);
            final int secondSpid = QualificationSqlEvidence.spid(contender);
            if (firstSpid == secondSpid) {
                throw new SQLException("QUAL_CONCURRENCY_SAME_SESSION");
            }
            prepare(owner, run, policy, token, observer);
            final var claimed =
                    new JdbcExpansionRelations(owner, AnalyticScenarioRuntime.LOGICAL_CLOCK)
                            .claimBatch(run, UUID.randomUUID(), 1, 60);
            if (claimed.size() != 1) {
                throw new SQLException("QUAL_CONCURRENCY_OWNER_CLAIM");
            }
            token.throwIfCancellationRequested();
            final var waiting =
                    new JdbcExpansionRelations(contender, AnalyticScenarioRuntime.LOGICAL_CLOCK);
            begin(contender);
            final int refused = claimRefusal(waiting, run);
            if (refused != 53401) {
                throw new SQLException("QUAL_CONCURRENCY_LOCK_REFUSAL_" + refused);
            }
            contender.rollback();
            begin(contender);
            final var pool = Executors.newSingleThreadExecutor();
            final long started = System.nanoTime();
            final int cancelled;
            try {
                final var pending = pool.submit(() -> claimRefusal(waiting, run));
                final long readyLimit = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (contender.openControlledStatements() == 0
                        && !pending.isDone()
                        && System.nanoTime() < readyLimit) {
                    Thread.sleep(10);
                }
                Thread.sleep(100);
                cancelled = contender.cancelActiveStatements();
                if (cancelled != 1 || pending.get(5, TimeUnit.SECONDS) != 0) {
                    throw new SQLException("QUAL_CONCURRENCY_CANCEL_NOT_OBSERVED");
                }
            } finally {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("QUAL_CONCURRENCY_THREAD_UNRELEASED");
                }
            }
            final long cancellationMillis =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            contender.rollback();
            owner.rollback();
            token.throwIfCancellationRequested();
            // A new transaction must reconstruct the reverted fixture; the journal cannot recover
            // it.
            prepare(contender, run, policy, token, observer);
            final var hydrated =
                    new ExpansionLaboratoryHydrator(
                                    waiting,
                                    new LocalExpansionDependencyRuntime(
                                            contender,
                                            run,
                                            policy,
                                            AnalyticScenarioRuntime.LOGICAL_CLOCK,
                                            Clock.systemUTC()),
                                    observer.forInput(AnalyticScenarioObserver.Input.FRE))
                            .hydrate(run, 1, token);
            if (hydrated.claimed() != 1
                    || hydrated.completed() != 1
                    || hydrated.capturedObservations() != 1
                    || hydrated.resolution().missing() != 0
                    || hydrated.resolution().conflicts() != 0) {
                throw new SQLException("QUAL_CONCURRENCY_CONSUMER_EQUATION");
            }
            contender.rollback();
            if (owner.openControlledStatements() != 0
                    || contender.openControlledStatements() != 0) {
                throw new SQLException("QUAL_CONCURRENCY_RESOURCE_LEAK");
            }
            final long calls =
                    owner.preparedStatements()
                            + owner.createdStatements()
                            + contender.preparedStatements()
                            + contender.createdStatements();
            if (calls > configuration.maximumJdbcCalls()) {
                throw new SQLException("QUAL_CONCURRENCY_JDBC_CAP");
            }
            result =
                    new Result(
                            firstSpid,
                            secondSpid,
                            refused,
                            cancelled,
                            cancellationMillis,
                            hydrated.completed(),
                            calls,
                            true);
        }
        if (!before.equals(QualificationSqlEvidence.snapshot(configuration).sha256())) {
            throw new SQLException("QUAL_CONCURRENCY_ROLLBACK_UNCONFIRMED");
        }
        return result;
    }

    private static int claimRefusal(final JdbcExpansionRelations relations, final UUID run)
            throws SQLException {
        try {
            relations.claimBatch(run, UUID.randomUUID(), 1, 60);
        } catch (final SQLException refusal) {
            return refusal.getErrorCode();
        }
        throw new SQLException("QUAL_CONCURRENCY_CLAIM_UNEXPECTEDLY_ACCEPTED");
    }

    private static void prepare(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final ExpansionPolicy policy,
            final CancellationToken token,
            final AnalyticScenarioObserver observer)
            throws SQLException {
        new JdbcExpansionLaboratory(session, AnalyticScenarioRuntime.LOGICAL_CLOCK)
                .start(run, policy);
        new AnalyticExpansionCapture(
                        session,
                        run,
                        AnalyticScenarioRuntime.LOGICAL_CLOCK,
                        Clock.systemUTC(),
                        observer)
                .capture(ExecutionMode.BOOTSTRAP, 1, 2, false, token);
    }

    private static void begin(final ColetaTemporalLaboratorySession session) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "IF @@TRANCOUNT=0 BEGIN TRANSACTION; SELECT @@TRANCOUNT")) {
            sql.setQueryTimeout(5);
            try (var row = sql.executeQuery()) {
                if (!row.next() || row.getInt(1) < 1 || row.next()) {
                    throw new SQLException("QUAL_CONCURRENCY_TRANSACTION_REQUIRED");
                }
            }
        }
    }
}
