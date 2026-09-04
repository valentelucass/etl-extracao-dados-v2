package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.Objects;

/** Duas subpartições contíguas, sem lacuna nem sobreposição. */
public record RepartitionSplit(RepartitionWindow first, RepartitionWindow second) {

    public RepartitionSplit {
        first = Objects.requireNonNull(first, "A primeira subpartição é obrigatória.");
        second = Objects.requireNonNull(second, "A segunda subpartição é obrigatória.");
        if (!first.endExclusive().equals(second.start())) {
            throw new IllegalArgumentException("As subpartições devem ser contíguas.");
        }
    }
}
