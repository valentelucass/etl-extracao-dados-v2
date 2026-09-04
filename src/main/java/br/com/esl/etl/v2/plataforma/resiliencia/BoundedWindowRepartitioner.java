package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Divide somente 422 comprovado, sempre em janelas menores e sob budget atômico. */
public final class BoundedWindowRepartitioner {

    private final int maxRepartitions;
    private final Duration partitionUnit;
    private final AtomicInteger repartitions = new AtomicInteger();

    BoundedWindowRepartitioner(final int maxRepartitions, final Duration partitionUnit) {
        this.maxRepartitions = maxRepartitions;
        this.partitionUnit =
                Objects.requireNonNull(partitionUnit, "A unidade de partição é obrigatória.");
        if (partitionUnit.isZero()
                || partitionUnit.isNegative()
                || partitionUnit.compareTo(ExecutionDeadlines.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("A unidade de partição está fora do permitido.");
        }
    }

    public RepartitionSplit split(final FailureKind failureKind, final RepartitionWindow window) {
        Objects.requireNonNull(failureKind, "A categoria de falha é obrigatória.");
        Objects.requireNonNull(window, "A janela é obrigatória.");
        if (failureKind != FailureKind.WINDOW_TOO_LARGE_HTTP_422) {
            throw new RepartitionRefusedException(RepartitionRefusalReason.CATEGORY_NOT_PROVEN);
        }
        final Duration duration = window.duration();
        final long units;
        try {
            units = duration.dividedBy(partitionUnit);
        } catch (final ArithmeticException exception) {
            throw new RepartitionRefusedException(RepartitionRefusalReason.WINDOW_OUT_OF_RANGE);
        }
        if (!partitionUnit.multipliedBy(units).equals(duration)) {
            throw new RepartitionRefusedException(RepartitionRefusalReason.WINDOW_NOT_ALIGNED);
        }
        if (units < 2L) {
            throw new RepartitionRefusedException(
                    RepartitionRefusalReason.MINIMUM_PARTITION_REACHED);
        }
        final Instant midpoint;
        try {
            midpoint = window.start().plus(partitionUnit.multipliedBy(units / 2L));
        } catch (final ArithmeticException exception) {
            throw new RepartitionRefusedException(RepartitionRefusalReason.WINDOW_OUT_OF_RANGE);
        }
        final RepartitionWindow first = new RepartitionWindow(window.start(), midpoint);
        final RepartitionWindow second = new RepartitionWindow(midpoint, window.endExclusive());
        reserve();
        return new RepartitionSplit(first, second);
    }

    public int usedRepartitions() {
        return repartitions.get();
    }

    private void reserve() {
        while (true) {
            final int observed = repartitions.get();
            if (observed >= maxRepartitions) {
                throw new RepartitionRefusedException(RepartitionRefusalReason.BUDGET_EXHAUSTED);
            }
            if (repartitions.compareAndSet(observed, observed + 1)) {
                return;
            }
        }
    }
}
