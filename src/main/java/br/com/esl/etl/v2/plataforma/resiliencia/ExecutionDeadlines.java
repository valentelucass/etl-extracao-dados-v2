package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.util.Objects;

/** Deadlines hierárquicos de ciclo, passo e request medidos somente por ticker monotônico. */
public final class ExecutionDeadlines {

    public static final Duration MAX_TIMEOUT = Duration.ofDays(7);
    private static final Duration CANCELLATION_POLL_INTERVAL = Duration.ofMillis(50);

    private final Duration cycleTimeout;
    private final long cycleStartedAtNanos;
    private final MonotonicTicker ticker;
    private final CancellationToken cancellationToken;

    private ExecutionDeadlines(
            final Duration cycleTimeout,
            final MonotonicTicker ticker,
            final CancellationToken cancellationToken) {
        this.cycleTimeout = validateTimeout(cycleTimeout, "O timeout do ciclo é obrigatório.");
        this.ticker = Objects.requireNonNull(ticker, "O ticker monotônico é obrigatório.");
        this.cancellationToken =
                Objects.requireNonNull(cancellationToken, "O token de cancelamento é obrigatório.");
        this.cycleStartedAtNanos = ticker.readNanos();
    }

    public static ExecutionDeadlines start(
            final Duration cycleTimeout,
            final MonotonicTicker ticker,
            final CancellationToken cancellationToken) {
        return new ExecutionDeadlines(cycleTimeout, ticker, cancellationToken);
    }

    public Step beginStep(final Duration stepTimeout, final Duration requestTimeout) {
        final Duration validatedStep =
                validateTimeout(stepTimeout, "O timeout do passo é obrigatório.");
        final Duration validatedRequest =
                validateTimeout(requestTimeout, "O timeout do request é obrigatório.");
        if (validatedStep.compareTo(cycleTimeout) > 0
                || validatedRequest.compareTo(validatedStep) > 0) {
            throw new IllegalArgumentException(
                    "Os timeouts devem respeitar request <= passo <= ciclo.");
        }
        checkpointCycle();
        return new Step(validatedStep, validatedRequest, ticker.readNanos());
    }

    public void checkpointCycle() {
        cancellationToken.throwIfCancellationRequested();
        remaining(cycleStartedAtNanos, cycleTimeout, ResilienceTimeoutScope.CYCLE);
    }

    private long remaining(
            final long startedAtNanos, final Duration timeout, final ResilienceTimeoutScope scope) {
        return remainingAt(startedAtNanos, timeout, scope, ticker.readNanos());
    }

    private long remainingAt(
            final long startedAtNanos,
            final Duration timeout,
            final ResilienceTimeoutScope scope,
            final long nowNanos) {
        final long elapsed = nowNanos - startedAtNanos;
        if (elapsed < 0) {
            throw new IllegalStateException("O ticker monotônico regrediu.");
        }
        final long remaining = timeout.toNanos() - elapsed;
        if (remaining <= 0) {
            throw new ResilienceTimeoutException(scope);
        }
        return remaining;
    }

    private static Duration validateTimeout(final Duration value, final String message) {
        Objects.requireNonNull(value, message);
        if (value.isZero() || value.isNegative() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("O timeout está fora do limite permitido.");
        }
        return value;
    }

    /** Deadline de um workload; pode abrir requests sequenciais sob o mesmo passo. */
    public final class Step {

        private final Duration stepTimeout;
        private final Duration requestTimeout;
        private final long stepStartedAtNanos;

        private Step(
                final Duration stepTimeout,
                final Duration requestTimeout,
                final long stepStartedAtNanos) {
            this.stepTimeout = stepTimeout;
            this.requestTimeout = requestTimeout;
            this.stepStartedAtNanos = stepStartedAtNanos;
        }

        public void checkpoint() {
            remainingOperationNanos();
        }

        public Request beginRequest() {
            checkpoint();
            return new Request(ticker.readNanos());
        }

        public Duration remainingOperationTime() {
            return Duration.ofNanos(remainingOperationNanos());
        }

        public void awaitDelay(final Duration delay, final ResilienceSleeper resilienceSleeper) {
            Objects.requireNonNull(delay, "O atraso é obrigatório.");
            Objects.requireNonNull(resilienceSleeper, "O sleeper é obrigatório.");
            if (delay.isNegative() || delay.compareTo(MAX_TIMEOUT) > 0) {
                throw new IllegalArgumentException("O atraso está fora do limite permitido.");
            }
            final long delayStartedAtNanos = ticker.readNanos();
            final long delayNanos = delay.toNanos();
            while (true) {
                final OperationRemaining operation = remainingOperation();
                final long elapsedDelay = operation.nowNanos() - delayStartedAtNanos;
                if (elapsedDelay < 0) {
                    throw new IllegalStateException("O ticker monotônico regrediu.");
                }
                final long remainingDelay = delayNanos - elapsedDelay;
                if (remainingDelay <= 0) {
                    checkpoint();
                    return;
                }
                if (remainingDelay >= operation.minimumNanos()) {
                    throw exhaustedOperationScope(operation);
                }
                final long slice = Math.min(remainingDelay, CANCELLATION_POLL_INTERVAL.toNanos());
                sleep(resilienceSleeper, Duration.ofNanos(slice));
            }
        }

        private long remainingOperationNanos() {
            return remainingOperation().minimumNanos();
        }

        private OperationRemaining remainingOperation() {
            cancellationToken.throwIfCancellationRequested();
            return remainingOperationAt(ticker.readNanos());
        }

        private OperationRemaining remainingOperationAt(final long now) {
            final long cycleRemaining =
                    remainingAt(
                            cycleStartedAtNanos, cycleTimeout, ResilienceTimeoutScope.CYCLE, now);
            final long stepRemaining =
                    remainingAt(stepStartedAtNanos, stepTimeout, ResilienceTimeoutScope.STEP, now);
            return new OperationRemaining(now, cycleRemaining, stepRemaining);
        }

        private ResilienceTimeoutException exhaustedOperationScope(
                final OperationRemaining operation) {
            cancellationToken.throwIfCancellationRequested();
            return new ResilienceTimeoutException(
                    operation.cycleNanos() <= operation.stepNanos()
                            ? ResilienceTimeoutScope.CYCLE
                            : ResilienceTimeoutScope.STEP);
        }

        private void sleep(final ResilienceSleeper resilienceSleeper, final Duration duration) {
            try {
                resilienceSleeper.sleep(duration);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResilienceCancelledException(exception);
            }
        }

        private record OperationRemaining(long nowNanos, long cycleNanos, long stepNanos) {

            private long minimumNanos() {
                return Math.min(cycleNanos, stepNanos);
            }
        }

        /** Deadline de uma tentativa HTTP, incluindo o restante do passo e do ciclo. */
        public final class Request {

            private final long requestStartedAtNanos;

            private Request(final long requestStartedAtNanos) {
                this.requestStartedAtNanos = requestStartedAtNanos;
            }

            public Duration remainingTime() {
                cancellationToken.throwIfCancellationRequested();
                final long now = ticker.readNanos();
                final long operationRemaining = remainingOperationAt(now).minimumNanos();
                final long requestRemaining =
                        remainingAt(
                                requestStartedAtNanos,
                                requestTimeout,
                                ResilienceTimeoutScope.REQUEST,
                                now);
                return Duration.ofNanos(Math.min(operationRemaining, requestRemaining));
            }

            public void checkpoint() {
                remainingTime();
            }
        }
    }
}
