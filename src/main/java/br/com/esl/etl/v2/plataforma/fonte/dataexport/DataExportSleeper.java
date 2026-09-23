package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Duration;

/** Ponto injetável para espera de retry, mantendo testes sem pausa real. */
@FunctionalInterface
public interface DataExportSleeper {

    void sleep(Duration duration) throws InterruptedException;

    static DataExportSleeper threadSleeper() {
        return duration -> {
            final long millis = duration.toMillis();
            final int nanos = Math.toIntExact(duration.minusMillis(millis).toNanos());
            Thread.sleep(millis, nanos);
        };
    }
}
