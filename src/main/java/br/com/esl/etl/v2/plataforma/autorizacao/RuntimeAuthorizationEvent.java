package br.com.esl.etl.v2.plataforma.autorizacao;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Evento imutável e sanitizado produzido antes de uma operação autorizada. */
public final class RuntimeAuthorizationEvent {

    private final UUID eventId;
    private final UUID invocationId;
    private final RuntimeAction action;
    private final RuntimeAuthorizationDecision decision;
    private final RuntimeAuthorizationReason reason;
    private final Instant decidedAt;
    private final PrincipalAuditReference principalAuditReference;
    private final RuntimePrincipalKind principalKind;
    private final AuthorizationPolicyFingerprint authorityPolicyFingerprint;
    private final AuthorizationPolicyFingerprint runtimePolicyFingerprint;

    private RuntimeAuthorizationEvent(
            final UUID eventId,
            final UUID invocationId,
            final RuntimeAction action,
            final RuntimeAuthorizationDecision decision,
            final RuntimeAuthorizationReason reason,
            final Instant decidedAt,
            final VerifiedRuntimeIdentity identity,
            final AuthorizationPolicyFingerprint runtimePolicyFingerprint) {
        this.eventId = Objects.requireNonNull(eventId, "O identificador do evento é obrigatório.");
        this.invocationId =
                Objects.requireNonNull(invocationId, "O identificador da invocação é obrigatório.");
        this.action = Objects.requireNonNull(action, "A ação de runtime é obrigatória.");
        this.decision = Objects.requireNonNull(decision, "A decisão é obrigatória.");
        this.reason = Objects.requireNonNull(reason, "O motivo da decisão é obrigatório.");
        this.decidedAt = Objects.requireNonNull(decidedAt, "O instante da decisão é obrigatório.");
        this.principalAuditReference = identity == null ? null : identity.auditReference();
        this.principalKind = identity == null ? null : identity.kind();
        this.authorityPolicyFingerprint =
                identity == null ? null : identity.authorityPolicyFingerprint();
        this.runtimePolicyFingerprint =
                Objects.requireNonNull(
                        runtimePolicyFingerprint,
                        "A impressão da política de runtime é obrigatória.");
        if ((decision == RuntimeAuthorizationDecision.ALLOW)
                != (reason == RuntimeAuthorizationReason.AUTHORIZED)) {
            throw new IllegalArgumentException(
                    "ALLOW exige AUTHORIZED e uma recusa exige motivo de negação.");
        }
        if (decision == RuntimeAuthorizationDecision.ALLOW && identity == null) {
            throw new IllegalArgumentException("Uma autorização exige identidade verificada.");
        }
    }

    static RuntimeAuthorizationEvent allowed(
            final UUID eventId,
            final UUID invocationId,
            final RuntimeAction action,
            final Instant decidedAt,
            final VerifiedRuntimeIdentity identity,
            final AuthorizationPolicyFingerprint runtimePolicyFingerprint) {
        return new RuntimeAuthorizationEvent(
                eventId,
                invocationId,
                action,
                RuntimeAuthorizationDecision.ALLOW,
                RuntimeAuthorizationReason.AUTHORIZED,
                decidedAt,
                identity,
                runtimePolicyFingerprint);
    }

    static RuntimeAuthorizationEvent denied(
            final UUID eventId,
            final UUID invocationId,
            final RuntimeAction action,
            final RuntimeAuthorizationReason reason,
            final Instant decidedAt,
            final VerifiedRuntimeIdentity identity,
            final AuthorizationPolicyFingerprint runtimePolicyFingerprint) {
        return new RuntimeAuthorizationEvent(
                eventId,
                invocationId,
                action,
                RuntimeAuthorizationDecision.DENY,
                reason,
                decidedAt,
                identity,
                runtimePolicyFingerprint);
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID invocationId() {
        return invocationId;
    }

    public RuntimeAction action() {
        return action;
    }

    public RuntimeAuthorizationDecision decision() {
        return decision;
    }

    public RuntimeAuthorizationReason reason() {
        return reason;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public Optional<PrincipalAuditReference> principalAuditReference() {
        return Optional.ofNullable(principalAuditReference);
    }

    public Optional<RuntimePrincipalKind> principalKind() {
        return Optional.ofNullable(principalKind);
    }

    public Optional<AuthorizationPolicyFingerprint> authorityPolicyFingerprint() {
        return Optional.ofNullable(authorityPolicyFingerprint);
    }

    public AuthorizationPolicyFingerprint runtimePolicyFingerprint() {
        return runtimePolicyFingerprint;
    }

    @Override
    public String toString() {
        return "RuntimeAuthorizationEvent[eventId="
                + eventId
                + ", invocationId="
                + invocationId
                + ", action="
                + action
                + ", decision="
                + decision
                + ", reason="
                + reason
                + ", decidedAt="
                + decidedAt
                + ", identity="
                + (principalAuditReference == null ? "absent" : "redacted")
                + ", principalKind="
                + (principalKind == null ? "absent" : principalKind)
                + "]";
    }
}
