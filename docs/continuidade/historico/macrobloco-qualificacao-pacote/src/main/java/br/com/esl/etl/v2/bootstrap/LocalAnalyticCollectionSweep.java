package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit one-shot synthetic Sweep. Each call rolls back with its enclosing laboratory session.
 */
public final class LocalAnalyticCollectionSweep {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final RelationalLaboratoryPolicy policy;
    private final LocalAnalyticCollectionRuntime capture;
    private final JdbcAnalyticCollectionSweep sweep;

    public LocalAnalyticCollectionSweep(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID relationalRun,
            final RelationalLaboratoryPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.policy = Objects.requireNonNull(policy);
        capture =
                new LocalAnalyticCollectionRuntime(
                        session, run, relationalRun, policy, logicalClock, technicalClock);
        sweep = new JdbcAnalyticCollectionSweep(session);
    }

    public Observation observe(
            final SyntheticCollectionSnapshot snapshot,
            final UUID cycle,
            final CancellationToken token)
            throws SQLException {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(cycle);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        if (!run.equals(snapshot.run())
                || !snapshot.date()
                        .equals(
                                LocalDate.parse(
                                        AnalyticCollectionsFixtures.data()
                                                .path("request_date")
                                                .asText()))
                || policy.pageSize() > 16
                || snapshot.expectedRoots() * 3 > policy.maximumRows()) {
            throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_FIXTURE_SCOPE");
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var captures = new ArrayList<UUID>(4);
                for (int ordinal = 0; ordinal < 4; ordinal++) {
                    captures.add(
                            capture.capture(
                                            snapshot.date(),
                                            ExecutionMode.BACKFILL,
                                            null,
                                            AnalyticCollectionsFixtures.source(
                                                    snapshot.omitFirst() ? 2 : 1,
                                                    snapshot.expectedRoots(),
                                                    policy.pageSize()),
                                            token)
                                    .source()
                                    .executionId());
                }
                final var prepared = sweep.prepare(snapshot, cycle, captures, token);
                final var result = sweep.apply(prepared, token);
                token.throwIfCancellationRequested();
                return new Observation(prepared, result);
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    public record Observation(
            JdbcAnalyticCollectionSweep.Prepared proof,
            JdbcAnalyticCollectionSweep.Receipt result) {}
}
