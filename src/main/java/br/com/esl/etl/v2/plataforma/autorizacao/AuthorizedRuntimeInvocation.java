package br.com.esl.etl.v2.plataforma.autorizacao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Capacidade ligada a uma única ação, emitida somente após auditoria síncrona de ALLOW. */
public final class AuthorizedRuntimeInvocation {

    private final UUID authorizationEventId;
    private final UUID invocationId;
    private final RuntimeAction action;
    private final Instant authorizedAt;
    private final Instant validUntil;
    private final PrincipalAuditReference principalAuditReference;
    private final AuthorizationPolicyFingerprint authorityPolicyFingerprint;
    private final AuthorizationPolicyFingerprint runtimePolicyFingerprint;

    AuthorizedRuntimeInvocation(
            final UUID authorizationEventId,
            final UUID invocationId,
            final RuntimeAction action,
            final Instant authorizedAt,
            final Instant validUntil,
            final PrincipalAuditReference principalAuditReference,
            final AuthorizationPolicyFingerprint authorityPolicyFingerprint,
            final AuthorizationPolicyFingerprint runtimePolicyFingerprint) {
        this.authorizationEventId =
                Objects.requireNonNull(
                        authorizationEventId, "O identificador da autorização é obrigatório.");
        this.invocationId =
                Objects.requireNonNull(invocationId, "O identificador da invocação é obrigatório.");
        this.action = Objects.requireNonNull(action, "A ação autorizada é obrigatória.");
        this.authorizedAt =
                Objects.requireNonNull(authorizedAt, "O instante da autorização é obrigatório.");
        this.validUntil =
                Objects.requireNonNull(validUntil, "O término da autorização é obrigatório.");
        if (!authorizedAt.isBefore(validUntil)) {
            throw new IllegalArgumentException(
                    "A autorização deve terminar depois de sua emissão.");
        }
        this.principalAuditReference =
                Objects.requireNonNull(
                        principalAuditReference, "A referência de auditoria é obrigatória.");
        this.authorityPolicyFingerprint =
                Objects.requireNonNull(
                        authorityPolicyFingerprint,
                        "A impressão da política da autoridade é obrigatória.");
        this.runtimePolicyFingerprint =
                Objects.requireNonNull(
                        runtimePolicyFingerprint,
                        "A impressão da política de runtime é obrigatória.");
    }

    public UUID authorizationEventId() {
        return authorizationEventId;
    }

    public UUID invocationId() {
        return invocationId;
    }

    public RuntimeAction action() {
        return action;
    }

    public Instant authorizedAt() {
        return authorizedAt;
    }

    public Instant validUntil() {
        return validUntil;
    }

    /** Permite ao dispatcher validar a capacidade novamente no instante exato do consumo. */
    public boolean isValidAt(final Instant instant) {
        Objects.requireNonNull(instant, "O instante de consumo é obrigatório.");
        return !instant.isBefore(authorizedAt) && instant.isBefore(validUntil);
    }

    public PrincipalAuditReference principalAuditReference() {
        return principalAuditReference;
    }

    public AuthorizationPolicyFingerprint authorityPolicyFingerprint() {
        return authorityPolicyFingerprint;
    }

    public AuthorizationPolicyFingerprint runtimePolicyFingerprint() {
        return runtimePolicyFingerprint;
    }

    @Override
    public String toString() {
        return "AuthorizedRuntimeInvocation[authorizationEventId="
                + authorizationEventId
                + ", invocationId="
                + invocationId
                + ", action="
                + action
                + ", authorizedAt="
                + authorizedAt
                + ", validUntil="
                + validUntil
                + ", identity=redacted]";
    }
}
