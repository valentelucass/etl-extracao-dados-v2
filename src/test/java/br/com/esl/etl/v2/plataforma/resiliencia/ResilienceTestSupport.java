package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

final class ResilienceTestSupport {

    private ResilienceTestSupport() {}

    static EslResiliencePolicy policy() {
        return policy(20, 10, 4);
    }

    static EslResiliencePolicy policy(
            final int cycleRequests, final int workloadRequests, final int repartitions) {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                cycleRequests,
                workloadRequests,
                Duration.ofSeconds(2),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                repartitions,
                2,
                Duration.ofSeconds(5));
    }

    static final class MutableTicker implements MonotonicTicker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long readNanos() {
            return nanos.get();
        }

        void advance(final Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        void set(final long value) {
            nanos.set(value);
        }
    }
}
