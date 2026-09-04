package br.com.esl.etl.v2.contratos;

import java.util.Objects;
import java.util.Optional;

/** Interrompe a execução de contrato assim que o fornecedor responde HTTP 429. */
public final class ContractRateLimitExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String retryAfter;

    public ContractRateLimitExceededException() {
        this(Optional.empty());
    }

    public ContractRateLimitExceededException(final Optional<String> retryAfter) {
        super("A sonda de contrato recebeu HTTP 429 e foi interrompida.");
        this.retryAfter =
                Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.")
                        .orElse(null);
    }

    public Optional<String> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
