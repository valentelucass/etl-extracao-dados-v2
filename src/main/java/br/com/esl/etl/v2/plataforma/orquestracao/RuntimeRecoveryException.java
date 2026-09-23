package br.com.esl.etl.v2.plataforma.orquestracao;

import java.util.Objects;

/** Reason sanitizado e causa preservada, sem converter incerteza em terminal SQL. */
public final class RuntimeRecoveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final RuntimeRecoverySnapshot.Reason reason;

    public RuntimeRecoveryException(
            final RuntimeRecoverySnapshot.Reason reason, final Throwable cause) {
        super("RUNTIME_RECOVERY_" + Objects.requireNonNull(reason), Objects.requireNonNull(cause));
        this.reason = reason;
    }

    public RuntimeRecoverySnapshot.Reason reason() {
        return reason;
    }
}
