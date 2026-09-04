package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Janela interna canônica {@code [start, endExclusive)}. */
public record RepartitionWindow(Instant start, Instant endExclusive) {

    public RepartitionWindow {
        start = Objects.requireNonNull(start, "O início da janela é obrigatório.");
        endExclusive = Objects.requireNonNull(endExclusive, "O fim da janela é obrigatório.");
        if (!start.isBefore(endExclusive)) {
            throw new IllegalArgumentException("A janela deve ser positiva e semiaberta.");
        }
    }

    public Duration duration() {
        return Duration.between(start, endExclusive);
    }
}
