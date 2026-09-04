package br.com.esl.etl.v2.plataforma.resiliencia;

/** Relógio monotônico para deadlines; nunca deve ser substituído por horário civil. */
@FunctionalInterface
public interface MonotonicTicker {

    long readNanos();

    static MonotonicTicker systemTicker() {
        return System::nanoTime;
    }
}
