package br.com.esl.etl.v2.modulos.manifestos.domain;

import java.math.BigInteger;
import java.util.Objects;

/** Par físico MDF-e: a chave identifica o filho e o número permanece atributo correlacionado. */
public record ManifestoMdfeObservation(String key, BigInteger number) {

    public ManifestoMdfeObservation {
        Objects.requireNonNull(key, "A chave MDF-e é obrigatória.");
        Objects.requireNonNull(number, "O número MDF-e é obrigatório.");
        if (!key.matches("[0-9]{44}")) {
            throw new IllegalArgumentException(
                    "A chave MDF-e deve conter exatamente 44 dígitos ASCII.");
        }
        if (number.signum() <= 0) {
            throw new IllegalArgumentException("O número MDF-e deve ser inteiro positivo.");
        }
    }

    @Override
    public String toString() {
        return "ManifestoMdfeObservation[key=<redacted>, number=<redacted>]";
    }
}
