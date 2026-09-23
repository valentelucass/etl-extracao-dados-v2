package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalRecomposition;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executes bounded persisted intents and hydration work. Reopening reads SQL, never a JVM ledger.
 */
public final class RelationalLaboratoryExecutor {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final Clock clock;
    private final Clock technicalClock;
    private final JdbcRelationalLaboratory lab;
    private final JdbcRelationalRecomposition plans;

    public RelationalLaboratoryExecutor(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final Clock clock,
            final Clock technicalClock) {
        this.session = java.util.Objects.requireNonNull(session);
        this.run = java.util.Objects.requireNonNull(run);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.technicalClock = java.util.Objects.requireNonNull(technicalClock);
        lab = new JdbcRelationalLaboratory(session, clock);
        plans = new JdbcRelationalRecomposition(session);
    }

    public void plan(final ExecutionMode mode, final int firstRoot, final int rootsPerPartition)
            throws SQLException {
        final var policy = lab.policy(run);
        final var temporal =
                new RuntimeTemporalPolicy(
                        "synthetic-relational-plan-v1",
                        ZoneId.of("Etc/UTC"),
                        mode,
                        RuntimeWindowStrategy.INTERVAL,
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        LocalTime.MIDNIGHT,
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(5),
                        1,
                        64,
                        64,
                        0,
                        List.of());
        final var planned =
                new RuntimeTemporalPlanner()
                        .plan(
                                temporal,
                                policy.start(),
                                policy.end().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        if (planned.backlogRemaining()) {
            throw new IllegalArgumentException("REL_LAB_PLAN_CHUNK_REQUIRED");
        }
        final var slots =
                new ArrayList<JdbcRelationalRecomposition.PlanSlot>(planned.windows().size());
        int ordinal = 0;
        for (final var window : planned.windows()) {
            slots.add(
                    new JdbcRelationalRecomposition.PlanSlot(
                            ++ordinal,
                            window.partitionStart().atZone(ZoneOffset.UTC).toLocalDate(),
                            firstRoot + (ordinal - 1) * rootsPerPartition,
                            rootsPerPartition));
        }
        plans.planBatch(run, mode, slots);
    }

    public JdbcRelationalRecomposition.Progress executePartition(
            final ExecutionMode mode,
            final int ordinal,
            final CancellationToken cancellation,
            final BoundaryObserver observer)
            throws SQLException {
        final var partition = plans.partition(run, mode, ordinal);
        if (partition.completion() != null) {
            return plans.progress(run, mode);
        }
        final var policy = lab.policy(run);
        final var runtime = new LocalRelationalRuntime(session, run, policy, clock, technicalClock);
        final var slot = partition.slot();
        for (final var template :
                List.of(
                        DataExportTemplate.MANIFESTOS,
                        DataExportTemplate.COLETAS,
                        DataExportTemplate.FRETES)) {
            cancellation.throwIfCancellationRequested();
            observer.at(Boundary.BEFORE_CAPTURE);
            final String entity = template.name().toLowerCase(java.util.Locale.ROOT);
            UUID execution = plans.recoverCapture(run, mode, slot.date(), entity);
            if (execution == null) {
                final UUID original =
                        mode == ExecutionMode.REPLAY
                                ? plans.recoverCapture(
                                        run, ExecutionMode.BOOTSTRAP, slot.date(), entity)
                                : null;
                execution =
                        runtime.capture(
                                        template,
                                        slot.date(),
                                        mode,
                                        original,
                                        RelationalLaboratoryFixtures.source(
                                                template,
                                                slot.date(),
                                                slot.firstRoot(),
                                                slot.rootCount(),
                                                policy.pageSize(),
                                                true),
                                        cancellation)
                                .executionId();
            }
            observer.at(Boundary.AFTER_CAPTURE);
            plans.attach(run, mode, ordinal, execution);
            observer.at(Boundary.AFTER_ATTACH);
        }
        for (int start = slot.firstRoot();
                start < slot.firstRoot() + slot.rootCount();
                start += 50) {
            lab.bindBatch(
                    run,
                    RelationalLaboratoryFixtures.bindingBatch(
                            slot.date(),
                            start,
                            Math.min(50, slot.firstRoot() + slot.rootCount() - start)),
                    cancellation);
        }
        observer.at(Boundary.BEFORE_RESOLVE);
        final UUID receipt = UUID.randomUUID();
        lab.resolve(run, receipt, cancellation);
        observer.at(Boundary.AFTER_RESOLVE);
        if (lab.status(run).complete()) {
            plans.complete(run, mode, ordinal, receipt);
            observer.at(Boundary.AFTER_COMPLETE);
        }
        return plans.progress(run, mode);
    }

    public Hydration hydrate(
            final int maximumClaims,
            final CancellationToken cancellation,
            final MonotonicTicker ticker,
            final HydrationSource fixture)
            throws SQLException {
        final var policy = lab.policy(run);
        if (maximumClaims < 1 || maximumClaims > policy.maximumClaim()) {
            throw new IllegalArgumentException("REL_LAB_HYDRATION_BOUND");
        }
        final UUID owner = UUID.randomUUID();
        final var claims = lab.claimBatch(run, owner, maximumClaims, cancellation);
        final var deadlines =
                ExecutionDeadlines.start(
                        Duration.ofSeconds(policy.leaseSeconds()), ticker, cancellation);
        final CancellationToken bounded =
                () -> {
                    deadlines.checkpointCycle();
                    return false;
                };
        final var runtime = new LocalRelationalRuntime(session, run, policy, clock, technicalClock);
        int succeeded = 0;
        int failed = 0;
        for (final var claim : claims) {
            try {
                bounded.throwIfCancellationRequested();
                final var capture =
                        runtime.capture(
                                claim.relation() == RelationalBinding.Relation.MC
                                        ? DataExportTemplate.COLETAS
                                        : DataExportTemplate.FRETES,
                                claim.date(),
                                ExecutionMode.BACKFILL,
                                null,
                                fixture.create(claim, policy.pageSize()),
                                bounded);
                lab.resolve(run, UUID.randomUUID(), bounded);
                final var outcome = lab.claimOutcome(run, claim.backlogId(), owner);
                lab.finish(run, claim.backlogId(), owner, outcome, capture.executionId());
                if (outcome == JdbcRelationalLaboratory.AttemptResult.RESOLVED) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (final ResilienceCancelledException failure) {
                lab.finish(
                        run,
                        claim.backlogId(),
                        owner,
                        JdbcRelationalLaboratory.AttemptResult.ABANDONED,
                        null);
                throw failure;
            } catch (final RuntimeException failure) {
                lab.finish(
                        run,
                        claim.backlogId(),
                        owner,
                        failure instanceof IllegalArgumentException
                                        || failure
                                                instanceof
                                                br.com.esl.etl.v2.plataforma.contrato
                                                        .ContractDriftException
                                ? JdbcRelationalLaboratory.AttemptResult.CONTRACT_FAILURE
                                : JdbcRelationalLaboratory.AttemptResult.TEMPORARY_FAILURE,
                        null);
                failed++;
            }
        }
        return new Hydration(claims.size(), succeeded, failed, lab.status(run));
    }

    public enum Boundary {
        BEFORE_CAPTURE,
        AFTER_CAPTURE,
        AFTER_ATTACH,
        BEFORE_RESOLVE,
        AFTER_RESOLVE,
        AFTER_COMPLETE
    }

    @FunctionalInterface
    public interface BoundaryObserver {
        void at(Boundary boundary);
    }

    @FunctionalInterface
    public interface HydrationSource {
        RelationalSyntheticSource create(JdbcRelationalLaboratory.Claim claim, int pageSize);
    }

    public record Hydration(
            int claimed, int succeeded, int failed, JdbcRelationalLaboratory.Status status) {}
}
