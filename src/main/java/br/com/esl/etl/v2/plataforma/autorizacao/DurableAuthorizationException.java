package br.com.esl.etl.v2.plataforma.autorizacao;

/** Sanitized outward reason with the original failure available only to internal diagnostics. */
public final class DurableAuthorizationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        UNCONFIGURED,
        AUTHORITY_UNAVAILABLE,
        INVALID_RECEIPT,
        DENIED,
        ALREADY_CONSUMED,
        SCOPE_CHANGED,
        AUTHENTICATION_INVALID,
        CONTEXT_CHANGED,
        PRIVILEGE_EXCESSIVE,
        IDENTITY_UNMAPPED,
        IDENTITY_DISABLED_OR_UNVERIFIABLE,
        SCOPE_REJECTED
    }

    private final Reason reason;
    private final Throwable internalCause;

    DurableAuthorizationException(final Reason reason, final Throwable cause) {
        super("RUNTIME_AUTHORIZATION_" + reason);
        this.reason = reason;
        internalCause = cause;
    }

    public Reason reason() {
        return reason;
    }

    public java.util.Optional<Throwable> internalCause() {
        return java.util.Optional.ofNullable(internalCause);
    }
}
