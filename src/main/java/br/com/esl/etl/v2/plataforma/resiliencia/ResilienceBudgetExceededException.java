package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.Objects;

/** Falha tipada de budget; a mensagem não revela quota, tenant ou identificador de origem. */
public final class ResilienceBudgetExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final ResilienceBudgetScope scope;

    public ResilienceBudgetExceededException(final ResilienceBudgetScope scope) {
        super(
                "O orçamento de resiliência foi esgotado no escopo "
                        + Objects.requireNonNull(scope, "O escopo do orçamento é obrigatório.")
                                .name()
                        + ".");
        this.scope = scope;
    }

    public ResilienceBudgetScope scope() {
        return scope;
    }
}
