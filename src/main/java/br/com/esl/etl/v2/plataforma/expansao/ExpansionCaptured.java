package br.com.esl.etl.v2.plataforma.expansao;

import java.util.Objects;

public record ExpansionCaptured<T extends ExpansionObservation>(
        long occurrence, T observation, ExpansionBinding binding) {
    public ExpansionCaptured {
        Objects.requireNonNull(observation);
        if (occurrence < 1 || occurrence > 100_000) {
            throw new IllegalArgumentException("EXP_CAPTURE_BOUND");
        }
    }
}
