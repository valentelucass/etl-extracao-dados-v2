package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;

/** Espera injetável que preserva precisão submilissegundo em Java 17. */
@FunctionalInterface
public interface ResilienceSleeper {

    void sleep(Duration duration) throws InterruptedException;

    static ResilienceSleeper threadSleeper() {
        return duration -> {
            final long millis = duration.toMillis();
            final int nanos = Math.toIntExact(duration.minusMillis(millis).toNanos());
            Thread.sleep(millis, nanos);
        };
    }
}
