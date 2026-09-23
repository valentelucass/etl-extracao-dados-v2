package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Governor único por instância ESL: serializa requests, compartilha quota e propaga embargo de rate
 * limit entre workloads e templates.
 */
public final class EslRequestGovernor {

    private static final long PERMIT_POLL_NANOS = Duration.ofMillis(50).toNanos();

    private final EslResiliencePolicy policy;
    private final MonotonicTicker ticker;
    private final ResilienceSleeper sleeper;
    private final Semaphore inFlight;
    private final Object rateLock = new Object();
    private boolean requestStarted;
    private long nextStartNanos;
    private boolean embargoActive;
    private long embargoUntilNanos;

    public EslRequestGovernor(
            final EslResiliencePolicy policy,
            final MonotonicTicker ticker,
            final ResilienceSleeper sleeper) {
        this.policy = Objects.requireNonNull(policy, "A política ESL é obrigatória.");
        this.ticker = Objects.requireNonNull(ticker, "O ticker monotônico é obrigatório.");
        this.sleeper = Objects.requireNonNull(sleeper, "O sleeper é obrigatório.");
        this.inFlight = new Semaphore(policy.maxInFlight(), true);
    }

    public Cycle beginCycle(final CancellationToken cancellationToken) {
        final CancellationToken required =
                Objects.requireNonNull(cancellationToken, "O cancelamento do ciclo é obrigatório.");
        return new Cycle(
                ExecutionDeadlines.start(policy.cycleTimeout(), ticker, required), required);
    }

    /** Confirma que o governor materializado representa a policy declarada pela origem. */
    public void verifyPolicy(final EslResiliencePolicy expected) {
        if (!policy.equals(
                Objects.requireNonNull(expected, "A política ESL esperada é obrigatória."))) {
            throw new IllegalArgumentException(
                    "A política do governor não corresponde ao runtime da origem.");
        }
    }

    public void imposeRateLimitEmbargo(final Duration delay) {
        Objects.requireNonNull(delay, "O embargo de rate limit é obrigatório.");
        if (delay.isNegative() || delay.compareTo(policy.maxRetryAfter()) > 0) {
            throw new IllegalArgumentException("O embargo de rate limit está fora do permitido.");
        }
        synchronized (rateLock) {
            final long now = ticker.readNanos();
            final long currentRemaining =
                    embargoActive ? positiveRemaining(embargoUntilNanos, now) : 0L;
            if (delay.toNanos() > currentRemaining) {
                embargoUntilNanos = now + delay.toNanos();
                embargoActive = !delay.isZero();
            }
        }
    }

    public int availablePermits() {
        return inFlight.availablePermits();
    }

    private void acquireInFlight(final ExecutionDeadlines.Step step) {
        while (true) {
            step.checkpoint();
            final long waitNanos =
                    Math.min(step.remainingOperationTime().toNanos(), PERMIT_POLL_NANOS);
            try {
                if (inFlight.tryAcquire(waitNanos, TimeUnit.NANOSECONDS)) {
                    return;
                }
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResilienceCancelledException(exception);
            }
        }
    }

    private void awaitGlobalStart(final ExecutionDeadlines.Step step) {
        while (true) {
            final long waitNanos;
            synchronized (rateLock) {
                final long now = ticker.readNanos();
                final long intervalRemaining =
                        requestStarted ? positiveRemaining(nextStartNanos, now) : 0L;
                final long embargoRemaining =
                        embargoActive ? positiveRemaining(embargoUntilNanos, now) : 0L;
                waitNanos = Math.max(intervalRemaining, embargoRemaining);
                if (waitNanos <= 0) {
                    embargoActive = false;
                    final long startedAt = ticker.readNanos();
                    nextStartNanos = startedAt + policy.minimumRequestInterval().toNanos();
                    requestStarted = true;
                    return;
                }
            }
            step.awaitDelay(Duration.ofNanos(waitNanos), sleeper);
        }
    }

    private static long positiveRemaining(final long targetNanos, final long nowNanos) {
        return Math.max(0L, targetNanos - nowNanos);
    }

    /** Budget de requests compartilhado por todos os workloads de um ciclo. */
    public final class Cycle {

        private final ExecutionDeadlines deadlines;
        private final CancellationToken cancellationToken;
        private final Workload[] workloads = new Workload[EslWorkload.values().length];
        private int sourceRequests;

        private Cycle(
                final ExecutionDeadlines deadlines, final CancellationToken cancellationToken) {
            this.deadlines = deadlines;
            this.cancellationToken = cancellationToken;
        }

        /** Exige a mesma capability de cancelamento usada ao abrir o ciclo. */
        public void verifyCancellationToken(final CancellationToken expected) {
            if (cancellationToken
                    != Objects.requireNonNull(expected, "O cancelamento esperado é obrigatório.")) {
                throw new IllegalArgumentException(
                        "A capability de cancelamento não corresponde ao ciclo ESL.");
            }
        }

        /** Impede execução com budgets diferentes dos declarados no runtime da origem. */
        public void verifyPolicy(final EslResiliencePolicy expected) {
            if (!policy.equals(
                    Objects.requireNonNull(expected, "A política ESL esperada é obrigatória."))) {
                throw new IllegalArgumentException(
                        "A política ESL do ciclo não corresponde ao runtime da origem.");
            }
        }

        /** Exige o governor único que abriu este ciclo, não apenas uma policy equivalente. */
        public void verifyGovernor(final EslRequestGovernor expected) {
            if (EslRequestGovernor.this
                    != Objects.requireNonNull(expected, "O governor ESL esperado é obrigatório.")) {
                throw new IllegalArgumentException(
                        "O ciclo não pertence ao governor ESL do runtime da origem.");
            }
        }

        public synchronized Workload beginWorkload(final EslWorkload workload) {
            deadlines.checkpointCycle();
            final EslWorkload validated =
                    Objects.requireNonNull(workload, "O workload ESL é obrigatório.");
            final int index = validated.ordinal();
            if (workloads[index] == null) {
                workloads[index] =
                        new Workload(
                                deadlines.beginStep(policy.stepTimeout(), policy.requestTimeout()));
            }
            return workloads[index];
        }

        public synchronized int sourceRequests() {
            return sourceRequests;
        }

        private synchronized void reserve(final Workload workload) {
            if (sourceRequests >= policy.maxRequestsPerCycle()) {
                throw new ResilienceBudgetExceededException(ResilienceBudgetScope.SOURCE);
            }
            if (workload.requests >= policy.maxRequestsPerWorkload()) {
                throw new ResilienceBudgetExceededException(ResilienceBudgetScope.WORKLOAD);
            }
            sourceRequests++;
            workload.requests++;
        }

        /** Guard por vertical/workload; compartilha quota e rate limit com seus pares. */
        public final class Workload {

            private final ExecutionDeadlines.Step step;
            private int requests;
            private BoundedWindowRepartitioner repartitioner;
            private Duration repartitionUnit;

            private Workload(final ExecutionDeadlines.Step step) {
                this.step = step;
            }

            public RequestPermit acquire() {
                acquireInFlight(step);
                boolean succeeded = false;
                try {
                    awaitGlobalStart(step);
                    reserve(this);
                    final RequestPermit permit = new RequestPermit(step.beginRequest(), inFlight);
                    succeeded = true;
                    return permit;
                } finally {
                    if (!succeeded) {
                        inFlight.release();
                    }
                }
            }

            public void awaitRetry(final Duration delay) {
                step.awaitDelay(delay, sleeper);
            }

            public void imposeRateLimitEmbargo(final Duration delay) {
                // A diretiva já observada protege outros workloads mesmo se este ciclo for
                // cancelado imediatamente após receber a resposta remota.
                EslRequestGovernor.this.imposeRateLimitEmbargo(delay);
            }

            public Duration maximumRetryAfter() {
                return policy.maxRetryAfter();
            }

            public synchronized BoundedWindowRepartitioner repartitioner(
                    final Duration partitionUnit) {
                Objects.requireNonNull(partitionUnit, "A unidade de partição é obrigatória.");
                if (repartitioner == null) {
                    this.repartitionUnit = partitionUnit;
                    repartitioner =
                            new BoundedWindowRepartitioner(
                                    policy.maxRepartitions(), partitionUnit, cancellationToken);
                } else if (!repartitionUnit.equals(partitionUnit)) {
                    throw new IllegalStateException(
                            "A unidade de reparticionamento do workload é imutável.");
                }
                return repartitioner;
            }

            public int requests() {
                synchronized (Cycle.this) {
                    return requests;
                }
            }
        }
    }

    /** Permit de uma única request; o fechamento idempotente libera a vaga global. */
    public static final class RequestPermit implements AutoCloseable {

        private final ExecutionDeadlines.Step.Request requestDeadline;
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RequestPermit(
                final ExecutionDeadlines.Step.Request requestDeadline, final Semaphore semaphore) {
            this.requestDeadline = requestDeadline;
            this.semaphore = semaphore;
        }

        public synchronized Duration remainingTime() {
            ensureOpen();
            return requestDeadline.remainingTime();
        }

        public synchronized void checkpoint() {
            ensureOpen();
            requestDeadline.checkpoint();
        }

        @Override
        public synchronized void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new IllegalStateException("O permit de request já foi encerrado.");
            }
        }
    }
}
