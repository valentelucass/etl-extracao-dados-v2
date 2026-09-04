package br.com.esl.etl.v2.plataforma.autorizacao;

import java.util.Objects;
import java.util.UUID;

/** Recusa sanitizada; causas potencialmente sensíveis nunca são encadeadas. */
public final class RuntimeAuthorizationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final UUID invocationId;
    private final RuntimeAction action;
    private final RuntimeAuthorizationReason reason;

    RuntimeAuthorizationException(
            final UUID invocationId,
            final RuntimeAction action,
            final RuntimeAuthorizationReason reason) {
        super(
                "A operação de runtime foi recusada. Correlação: "
                        + Objects.requireNonNull(
                                invocationId, "O identificador da invocação é obrigatório.")
                        + ".");
        this.invocationId = invocationId;
        this.action = Objects.requireNonNull(action, "A ação recusada é obrigatória.");
        this.reason = Objects.requireNonNull(reason, "O motivo da recusa é obrigatório.");
    }

    public UUID invocationId() {
        return invocationId;
    }

    public RuntimeAction action() {
        return action;
    }

    public RuntimeAuthorizationReason reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "RuntimeAuthorizationException[correlationId=" + invocationId + "]";
    }
}
