package br.com.esl.etl.v2.plataforma.contrato;

import java.util.Objects;

/** Resultado positivo: idêntico ou compatível com alerta explícito. */
public record ContractValidationResult(Status status, ContractDiff diff) {

    public ContractValidationResult {
        status = Objects.requireNonNull(status, "O status da validação é obrigatório.");
        diff = Objects.requireNonNull(diff, "O diff da validação é obrigatório.");
        if (diff.breakingCount() != 0) {
            throw new IllegalArgumentException(
                    "Um resultado positivo não pode conter drift crítico.");
        }
        if ((status == Status.ACCEPTED) != diff.changes().isEmpty()) {
            throw new IllegalArgumentException("O status positivo não corresponde ao diff.");
        }
    }

    public enum Status {
        ACCEPTED,
        ACCEPTED_WITH_ALERT
    }
}
