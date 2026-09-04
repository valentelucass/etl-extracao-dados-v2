package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Duration;
import java.util.Objects;

/** Espera limitada; não preserva o valor bruto do cabeçalho. */
record DataExportRetryDelay(
        Duration duration, DataExportRetryDelaySource source, boolean serverDirected) {

    DataExportRetryDelay {
        duration = Objects.requireNonNull(duration, "A duração de retry é obrigatória.");
        source = Objects.requireNonNull(source, "A origem do retry é obrigatória.");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("A duração de retry não pode ser negativa.");
        }
    }

    DataExportRetryDelay(final Duration duration, final DataExportRetryDelaySource source) {
        this(duration, source, source != DataExportRetryDelaySource.BACKOFF);
    }
}
