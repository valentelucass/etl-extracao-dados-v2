package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.Objects;

/** Categoria estável consumível pelo runtime futuro; os códigos não dependem de exceções. */
public enum RuntimeExitCategory {
    SUCCESS(0, 0),
    DEGRADED(10, 1),
    CONFIG_AUTH(20, 5),
    LOCK(30, 3),
    SOURCE_DQ(40, 4),
    CANCELLED(50, 2);

    private final int code;
    private final int severity;

    RuntimeExitCategory(final int code, final int severity) {
        this.code = code;
        this.severity = severity;
    }

    public int code() {
        return code;
    }

    public RuntimeExitCategory combine(final RuntimeExitCategory other) {
        Objects.requireNonNull(other, "A categoria de saída é obrigatória.");
        return severity >= other.severity ? this : other;
    }
}
