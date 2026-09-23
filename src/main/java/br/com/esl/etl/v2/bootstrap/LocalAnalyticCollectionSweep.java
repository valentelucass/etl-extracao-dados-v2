package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
    private final AnalyticScenarioObserver observer;

    public LocalAnalyticCollectionSweep(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID relationalRun,
            final RelationalLaboratoryPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock) {
        this(
                session,
                run,
                relationalRun,
                policy,
                logicalClock,
                technicalClock,
                AnalyticScenarioObserver.NONE);
    }

    public LocalAnalyticCollectionSweep(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID relationalRun,
            final RelationalLaboratoryPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.policy = Objects.requireNonNull(policy);
        this.observer = Objects.requireNonNull(observer);
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
        return observe(
                snapshot,
                cycle,
                AnalyticCollectionSweepFixtures.inputs(snapshot, policy.pageSize(), observer),
                token);
    }

    public Observation observe(
            final SyntheticCollectionSnapshot snapshot,
            final UUID cycle,
            final List<CollectionSweepInput> inputs,
            final CancellationToken token)
            throws SQLException {
        final var planner = new SweepResponsibilityPlanner();
        return observe(
                snapshot,
                cycle,
                inputs,
                planner.bindings(snapshot.scope().scopeFingerprint(), snapshot.fingerprint()),
                token);
    }

    public Observation observe(
            final SyntheticCollectionSnapshot snapshot,
            final UUID cycle,
            final List<CollectionSweepInput> inputs,
            final List<SweepResponsibilityPlanner.Binding> bindings,
            final CancellationToken token)
            throws SQLException {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(cycle);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        if (!run.equals(snapshot.run())
                || snapshot.date().isBefore(policy.start())
                || snapshot.date().isAfter(policy.end())
                || policy.pageSize() > 16
                || snapshot.expectedRows() > policy.maximumRows()) {
            throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_FIXTURE_SCOPE");
        }
        if (inputs == null || inputs.size() != 4) {
            throw new IllegalArgumentException("ANA_SWEEP_FOUR_INPUTS_REQUIRED");
        }
        final var supplied = List.copyOf(inputs);
        for (final var input : supplied) {
            input.validate(snapshot);
        }
        final var planner = new SweepResponsibilityPlanner();
        planner.validate(bindings);
        for (final var binding : bindings) {
            if (!binding.scope().equals(snapshot.scope().scopeFingerprint())
                    || !binding.snapshot().equals(snapshot.fingerprint())) {
                throw new IllegalArgumentException("ANA_SWEEP_PREVIEW_SCOPE");
            }
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
                                            supplied.get(ordinal).source(),
                                            token)
                                    .source()
                                    .executionId());
                }
                final var prepared = sweep.prepare(snapshot, cycle, captures, token);
                final var preview =
                        planner.preview(
                                bindings, snapshot.verifiedEvidence(captures, prepared.pages()));
                final var result =
                        snapshot.declaredUniverse() == null ? sweep.apply(prepared, token) : null;
                token.throwIfCancellationRequested();
                return new Observation(prepared, result, preview);
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

    record Observation(
            JdbcAnalyticCollectionSweep.Prepared proof,
            JdbcAnalyticCollectionSweep.Receipt result,
            List<SweepResponsibilityPlanner.Preview> preview) {
        public Observation {
            if (preview == null || preview.size() != 33) {
                throw new IllegalArgumentException("ANA_SWEEP_PREVIEW_COUNT");
            }
            preview = List.copyOf(preview);
        }
    }
}
