package br.com.esl.etl.v2.plataforma.identidade;

import java.util.Objects;

/** Falha sanitizada de identidade; o valor rejeitado nunca integra mensagem ou {@code toString}. */
public final class IdentityQuarantineException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public IdentityQuarantineException(final Reason reason) {
        super("A observação de identidade deve ser quarentenada: " + reason.name());
        this.reason = Objects.requireNonNull(reason, "O motivo de quarentena é obrigatório.");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MISSING_SOURCE_KEY,
        INVALID_SOURCE_KEY_TYPE,
        INVALID_SOURCE_KEY_VALUE,
        SOURCE_KEY_TOO_LONG,
        SCOPE_MISMATCH,
        SOURCE_KEY_COLLISION,
        AMBIGUOUS_BUSINESS_ALIAS,
        CARDINALITY_DIVERGENCE,
        UNPROVEN_REKEY,
        UNPROVEN_ALIAS_CHANGE
    }
}
