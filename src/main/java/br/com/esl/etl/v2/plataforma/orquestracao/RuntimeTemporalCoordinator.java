package br.com.esl.etl.v2.plataforma.orquestracao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One-shot planning and reconciliation; dispatch still requires a fresh authorization per
 * occurrence.
 */
public final class RuntimeTemporalCoordinator {
    public enum NextAction {
        START_ORIGINAL,
        RECOVER_ORIGINAL,
        EXPLICIT_REPLAY_REQUIRED
    }

    public record Pending(RuntimeTemporalStore.Gap occurrence, NextAction action) {}

    public record Reconciliation(
            Instant contiguousEnd, List<Pending> pending, int degraded, boolean limitReached) {
        public Reconciliation {
            if (pending.size() > 4) {
                throw new IllegalArgumentException("TEMPORAL_PENDING_LIMIT");
            }
            pending = List.copyOf(pending);
        }
    }

    private final RuntimeTemporalStore store;

    public RuntimeTemporalCoordinator(final RuntimeTemporalStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public RuntimeTemporalPlanner.Result persistCatchUp(
            final UUID plan,
            final String namespaceHash,
            final RuntimeTemporalPolicy policy,
            final LocalDate firstUnpublished,
            final Instant observedAt) {
        final var result = new RuntimeTemporalPlanner().plan(policy, firstUnpublished, observedAt);
        if (store.persist(plan, namespaceHash, policy, result) != result.windows().size()) {
            throw new IllegalStateException("TEMPORAL_PLAN_UNCONFIRMED");
        }
        return result;
    }

    public Reconciliation reconcile(
            final String namespaceHash,
            final RuntimeTemporalPolicy policy,
            final Instant previousContiguousEnd) {
        return reconcile(namespaceHash, policy, previousContiguousEnd, List.of());
    }

    /** A declared plan can share its namespace with other bounded laboratory plans. */
    public Reconciliation reconcile(
            final String namespaceHash,
            final RuntimeTemporalPolicy policy,
            final Instant previousContiguousEnd,
            final List<UUID> plannedOccurrences) {
        final var expected = java.util.Set.copyOf(plannedOccurrences);
        if (expected.size() != plannedOccurrences.size() || expected.size() > 64) {
            throw new IllegalArgumentException("TEMPORAL_PLANNED_OCCURRENCE_LIMIT");
        }
        final var rows =
                store.readGapPage(
                        namespaceHash, policy.maximumReconciliation(), previousContiguousEnd);
        if (rows.size() > policy.maximumReconciliation()) {
            throw new IllegalStateException("TEMPORAL_RECONCILIATION_OVERFLOW");
        }
        final var selected =
                expected.isEmpty()
                        ? rows
                        : rows.stream().filter(row -> expected.contains(row.execution())).toList();
        Instant contiguous = previousContiguousEnd;
        Instant preceding = previousContiguousEnd;
        boolean gapFound = false;
        int degraded = 0;
        final var pending = new ArrayList<Pending>();
        for (final var row : selected) {
            if (!row.start().equals(preceding)) {
                throw new IllegalStateException("TEMPORAL_RECONCILIATION_GAP_OR_OVERLAP");
            }
            preceding = row.endExclusive();
            if (row.state().equals("PUBLISHED")) {
                if (!gapFound) {
                    contiguous = row.endExclusive();
                }
                continue;
            }
            gapFound = true;
            final NextAction action =
                    switch (row.state()) {
                        case "NOT_STARTED" -> NextAction.START_ORIGINAL;
                        case "FAILED",
                                "CANCELLED",
                                "BLOCKED",
                                "SKIPPED",
                                "NOT_APPLICABLE",
                                "DEGRADED" -> {
                            degraded++;
                            yield NextAction.EXPLICIT_REPLAY_REQUIRED;
                        }
                        default -> NextAction.RECOVER_ORIGINAL;
                    };
            if (pending.size() < policy.concurrency()) {
                pending.add(new Pending(row, action));
            }
        }
        if (degraded > policy.maximumDegraded()) {
            throw new IllegalStateException("TEMPORAL_DEGRADED_LIMIT");
        }
        return new Reconciliation(
                contiguous,
                pending,
                degraded,
                expected.isEmpty()
                        ? rows.size() == policy.maximumReconciliation()
                        : selected.size() < expected.size());
    }
}
