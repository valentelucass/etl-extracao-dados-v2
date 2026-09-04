package br.com.esl.etl.v2.plataforma.autorizacao;

import java.util.Objects;

/** Falha sanitizada produzida por um verificador de identidade. */
public final class RuntimeIdentityVerificationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final RuntimeIdentityRejectionReason reason;

    public RuntimeIdentityVerificationException(final RuntimeIdentityRejectionReason reason) {
        super("A identidade de runtime não pôde ser verificada.");
        this.reason = Objects.requireNonNull(reason, "O motivo da rejeição é obrigatório.");
    }

    public RuntimeIdentityRejectionReason reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "RuntimeIdentityVerificationException[redacted]";
    }
}
