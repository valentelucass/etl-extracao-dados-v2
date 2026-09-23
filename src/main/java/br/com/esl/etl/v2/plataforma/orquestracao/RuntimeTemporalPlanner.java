package br.com.esl.etl.v2.plataforma.orquestracao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded one-shot catch-up from a durable contiguous frontier supplied by the control plane. */
public final class RuntimeTemporalPlanner {
    public record Window(
            Instant partitionStart,
            Instant endExclusive,
            Instant extractionStart,
            Instant dueAt,
            Instant deadlineAt) {
        public Window {
            if (!partitionStart.isBefore(endExclusive)
                    || extractionStart.isAfter(partitionStart)
                    || deadlineAt.isBefore(dueAt)) {
                throw new IllegalArgumentException("TEMPORAL_WINDOW_INVALID");
            }
        }
    }

    public record Result(
            List<Window> windows, boolean backlogRemaining, boolean blockedByBlackout) {
        public Result {
            if (windows.size() > 64) {
                throw new IllegalArgumentException("TEMPORAL_WINDOW_LIMIT");
            }
            windows = List.copyOf(windows);
        }
    }

    public Result plan(
            final RuntimeTemporalPolicy policy,
            final LocalDate firstUnpublished,
            final Instant observedAt) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(firstUnpublished);
        Objects.requireNonNull(observedAt);
        if (policy.cadence() == RuntimeTemporalPolicy.Cadence.CIVIL_MONTH
                && firstUnpublished.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("MONTH_FRONTIER_ALIGNMENT");
        }
        final var windows = new ArrayList<Window>();
        LocalDate start = firstUnpublished;
        for (int index = 0; index <= policy.maximumBacklog(); index++) {
            final LocalDate end =
                    policy.cadence() == RuntimeTemporalPolicy.Cadence.CIVIL_MONTH
                            ? start.plusMonths(1)
                            : start.plusDays(1);
            final Instant endInstant = policy.resolve(end);
            final Instant due = endInstant.plus(policy.stabilization());
            if (due.isAfter(observedAt)) {
                return new Result(windows, false, false);
            }
            final LocalDate candidateStart = start;
            if (policy.blackouts().stream()
                    .anyMatch(blackout -> blackout.intersects(candidateStart, end))) {
                return new Result(windows, true, true);
            }
            if (index == policy.maximumBacklog()) {
                return new Result(windows, true, false);
            }
            final Instant partition = policy.resolve(start);
            windows.add(
                    new Window(
                            partition,
                            endInstant,
                            partition.minus(policy.lookback()),
                            due,
                            due.plus(policy.deadline())));
            start = end;
        }
        throw new IllegalStateException("BOUNDED_PLANNER_INVARIANT");
    }

    public Window previousCivilMonth(final RuntimeTemporalPolicy policy, final Instant observedAt) {
        if (policy.cadence() != RuntimeTemporalPolicy.Cadence.CIVIL_MONTH) {
            throw new IllegalArgumentException("MONTH_POLICY_REQUIRED");
        }
        final LocalDate month =
                observedAt.atZone(policy.zone()).toLocalDate().withDayOfMonth(1).minusMonths(1);
        final Result result = plan(policy, month, observedAt);
        if (result.windows().isEmpty()) {
            throw new IllegalStateException("MONTH_NOT_DUE_OR_BLACKED_OUT");
        }
        return result.windows().get(0);
    }
}
