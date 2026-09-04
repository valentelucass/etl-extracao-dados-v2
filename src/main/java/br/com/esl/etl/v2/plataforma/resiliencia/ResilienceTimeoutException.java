package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.Objects;

/** Deadline esgotado sem incluir duração, URI ou dado de negócio na mensagem. */
public final class ResilienceTimeoutException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final ResilienceTimeoutScope scope;

    public ResilienceTimeoutException(final ResilienceTimeoutScope scope) {
        super(
                "O orçamento temporal da operação foi esgotado no escopo "
                        + Objects.requireNonNull(scope, "O escopo de timeout é obrigatório.").name()
                        + ".");
        this.scope = scope;
    }

    public ResilienceTimeoutScope scope() {
        return scope;
    }
}
