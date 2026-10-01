package br.com.esl.etl.v2.bootstrap;

import java.time.Duration;
import java.util.Objects;

/** Prazo monotonicamente medido desde antes da primeira operacao do trial. */
final class Coletas6908PilotDeadline {
    private final long started = System.nanoTime();
    private final long nanos;

    Coletas6908PilotDeadline(final Duration duration) {
        nanos = Objects.requireNonNull(duration).toNanos();
        if (nanos <= 0) {
            throw new IllegalArgumentException("COL_PILOT_DEADLINE_INVALID");
        }
    }

    boolean expired() {
        return System.nanoTime() - started >= nanos;
    }

    void check() {
        if (expired()) {
            throw new IllegalStateException("COL_PILOT_DEADLINE_EXCEEDED");
        }
    }
}
