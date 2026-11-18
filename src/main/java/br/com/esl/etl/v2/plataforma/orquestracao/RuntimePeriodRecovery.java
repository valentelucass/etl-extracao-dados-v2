package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded source extraction of missing civil periods; SQL publication remains authoritative. */
public final class RuntimePeriodRecovery {
    @FunctionalInterface
    public interface Executor {
        void execute(
                RuntimeTemporalCoordinator.Pending pending, RuntimeTemporalPlanner.Window window);
    }

    public record Result(
            Instant contiguousEnd,
            int dispatched,
            List<RuntimeTemporalCoordinator.Pending> pending,
            boolean backlogRemaining,
            boolean blockedByBlackout) {
        public Result {
            Objects.requireNonNull(contiguousEnd);
            pending = List.copyOf(pending);
        }
    }

    private final RuntimeTemporalStore store;

    public RuntimePeriodRecovery(final RuntimeTemporalStore store) {
        this.store = Objects.requireNonNull(store);
    }

    /**
     * The caller retains contiguousEnd per namespace between bounded passes. It must never seed
     * recovery with today's normal D-7 window or with the maximum successful date across holes.
     */
    public Result recover(
            final String namespace,
            final RuntimeTemporalPolicy policy,
            final LocalDate firstRequired,
            final Instant observedAt,
            final Executor executor) {
        if (namespace == null || !namespace.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("TEMPORAL_NAMESPACE_REQUIRED");
        }
        Objects.requireNonNull(executor);
        if (policy.mode() != ExecutionMode.BACKFILL || !policy.lookback().isZero()) {
            throw new IllegalArgumentException("PERIOD_RECOVERY_FULL_BUSINESS_RANGE_REQUIRED");
        }
        final var plan = new RuntimeTemporalPlanner().plan(policy, firstRequired, observedAt);
        Instant contiguous = policy.resolve(firstRequired);
        int dispatched = 0;
        for (final var window : plan.windows()) {
            var occurrence = read(namespace, policy, window);
            if (occurrence == null) {
                final var single = new RuntimeTemporalPlanner.Result(List.of(window), false, false);
                final UUID planId =
                        uuid(key(namespace, window) + "|" + policy.fingerprint().sha256());
                if (store.persist(planId, namespace, policy, single) != 1) {
                    throw new IllegalStateException("TEMPORAL_PLAN_UNCONFIRMED");
                }
                occurrence = read(namespace, policy, window);
                if (occurrence == null) {
                    throw new IllegalStateException("PERIOD_RECOVERY_PLAN_NOT_VISIBLE");
                }
            }
            if (!"PUBLISHED".equals(occurrence.state())) {
                final var pending = pending(occurrence);
                if (pending.action()
                        == RuntimeTemporalCoordinator.NextAction.EXPLICIT_REPLAY_REQUIRED) {
                    return stopped(contiguous, dispatched, pending, plan);
                }
                executor.execute(pending, window);
                dispatched++;
                occurrence = read(namespace, policy, window);
                if (occurrence == null) {
                    throw new IllegalStateException("PERIOD_RECOVERY_PUBLICATION_UNCONFIRMED");
                }
                if (!"PUBLISHED".equals(occurrence.state())) {
                    return stopped(contiguous, dispatched, pending(occurrence), plan);
                }
            }
            contiguous = window.endExclusive();
        }
        return new Result(
                contiguous,
                dispatched,
                List.of(),
                plan.backlogRemaining(),
                plan.blockedByBlackout());
    }

    private Result stopped(
            final Instant contiguous,
            final int dispatched,
            final RuntimeTemporalCoordinator.Pending pending,
            final RuntimeTemporalPlanner.Result plan) {
        return new Result(contiguous, dispatched, List.of(pending), true, plan.blockedByBlackout());
    }

    private RuntimeTemporalStore.Gap read(
            final String namespace,
            final RuntimeTemporalPolicy policy,
            final RuntimeTemporalPlanner.Window window) {
        final var rows =
                store.readGapPage(
                        namespace, policy.maximumReconciliation(), window.partitionStart());
        if (rows.size() > policy.maximumReconciliation()) {
            throw new IllegalStateException("TEMPORAL_RECONCILIATION_OVERFLOW");
        }
        RuntimeTemporalStore.Gap match = null;
        Instant preceding = window.partitionStart();
        for (final var row : rows) {
            if (row.start().isBefore(preceding)) {
                throw new IllegalStateException("PERIOD_RECOVERY_OVERLAP_OR_ORDER");
            }
            preceding = row.endExclusive();
            if (row.start().isBefore(window.endExclusive())) {
                if (!row.start().equals(window.partitionStart())
                        || !row.endExclusive().equals(window.endExclusive())
                        || !row.execution().equals(uuid(key(namespace, window)))) {
                    throw new IllegalStateException("PERIOD_RECOVERY_OCCURRENCE_MISMATCH");
                }
                match = row;
            }
        }
        return match;
    }

    private static RuntimeTemporalCoordinator.Pending pending(final RuntimeTemporalStore.Gap gap) {
        final var action =
                switch (gap.state()) {
                    case "NOT_STARTED" -> RuntimeTemporalCoordinator.NextAction.START_ORIGINAL;
                    case "FAILED",
                                    "CANCELLED",
                                    "BLOCKED",
                                    "SKIPPED",
                                    "NOT_APPLICABLE",
                                    "DEGRADED" ->
                            RuntimeTemporalCoordinator.NextAction.EXPLICIT_REPLAY_REQUIRED;
                    default -> RuntimeTemporalCoordinator.NextAction.RECOVER_ORIGINAL;
                };
        return new RuntimeTemporalCoordinator.Pending(gap, action);
    }

    private static String key(final String namespace, final RuntimeTemporalPlanner.Window window) {
        return namespace + "|" + window.partitionStart() + "|" + window.endExclusive();
    }

    private static UUID uuid(final String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
