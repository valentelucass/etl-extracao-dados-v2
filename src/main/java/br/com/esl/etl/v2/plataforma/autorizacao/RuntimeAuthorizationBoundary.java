package br.com.esl.etl.v2.plataforma.autorizacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fronteira única de autorização. Toda falha de identidade, tempo, papel ou auditoria resulta em
 * negação.
 */
public final class RuntimeAuthorizationBoundary {

    private final RuntimeIdentityVerifier identityVerifier;
    private final RuntimeAuthorizationPolicy policy;
    private final RuntimeAuthorizationAudit audit;
    private final Clock clock;

    /** Cria a fronteira segura padrão, sem identidade configurada e portanto deny-all. */
    public RuntimeAuthorizationBoundary(final RuntimeAuthorizationAudit audit, final Clock clock) {
        this(
                RuntimeIdentityVerifier.denyAll(),
                RuntimeAuthorizationPolicy.standard(),
                audit,
                clock);
    }

    /** Ponto de composição para um futuro adaptador confiável ou um verificador sintético. */
    RuntimeAuthorizationBoundary(
            final RuntimeIdentityVerifier identityVerifier,
            final RuntimeAuthorizationPolicy policy,
            final RuntimeAuthorizationAudit audit,
            final Clock clock) {
        this.identityVerifier =
                Objects.requireNonNull(
                        identityVerifier, "O verificador de identidade é obrigatório.");
        this.policy = Objects.requireNonNull(policy, "A política de autorização é obrigatória.");
        this.audit = Objects.requireNonNull(audit, "A auditoria de autorização é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio de autorização é obrigatório.");
    }

    public AuthorizedRuntimeInvocation authorize(
            final UUID invocationId, final RuntimeAction action) {
        Objects.requireNonNull(invocationId, "O identificador da invocação é obrigatório.");
        Objects.requireNonNull(action, "A ação de runtime é obrigatória.");

        final Instant requestTime;
        try {
            requestTime =
                    Objects.requireNonNull(clock.instant(), "O relógio retornou instante nulo.");
        } catch (final RuntimeException exception) {
            throw new RuntimeAuthorizationException(
                    invocationId,
                    action,
                    RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE);
        }

        final VerifiedRuntimeIdentity identity;
        try {
            identity = identityVerifier.verify();
        } catch (final RuntimeIdentityVerificationException exception) {
            throw deny(
                    invocationId, action, requestTime, null, mapIdentityReason(exception.reason()));
        } catch (final RuntimeException exception) {
            throw deny(
                    invocationId,
                    action,
                    requestTime,
                    null,
                    RuntimeAuthorizationReason.AUTHORITY_UNAVAILABLE);
        }

        if (identity == null) {
            throw deny(
                    invocationId,
                    action,
                    requestTime,
                    null,
                    RuntimeAuthorizationReason.IDENTITY_INVALID);
        }

        final Instant now;
        try {
            now = Objects.requireNonNull(clock.instant(), "O relógio retornou instante nulo.");
        } catch (final RuntimeException exception) {
            throw deny(
                    invocationId,
                    action,
                    requestTime,
                    identity,
                    RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE);
        }
        if (now.isBefore(requestTime)) {
            throw deny(
                    invocationId,
                    action,
                    requestTime,
                    identity,
                    RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE);
        }
        if (now.isBefore(identity.validFrom())) {
            throw deny(
                    invocationId,
                    action,
                    now,
                    identity,
                    RuntimeAuthorizationReason.IDENTITY_NOT_YET_VALID);
        }
        if (!now.isBefore(identity.expiresAt())) {
            throw deny(
                    invocationId,
                    action,
                    now,
                    identity,
                    RuntimeAuthorizationReason.IDENTITY_EXPIRED);
        }
        if (!policy.authorizes(action, identity.roles())) {
            throw deny(
                    invocationId,
                    action,
                    now,
                    identity,
                    RuntimeAuthorizationReason.ROLE_NOT_GRANTED);
        }

        final RuntimeAuthorizationEvent event =
                RuntimeAuthorizationEvent.allowed(
                        UUID.randomUUID(),
                        invocationId,
                        action,
                        now,
                        identity,
                        policy.fingerprint());
        recordOrDeny(event, invocationId, action);
        return new AuthorizedRuntimeInvocation(
                event.eventId(),
                invocationId,
                action,
                now,
                identity.expiresAt(),
                identity.auditReference(),
                identity.authorityPolicyFingerprint(),
                policy.fingerprint());
    }

    private RuntimeAuthorizationException deny(
            final UUID invocationId,
            final RuntimeAction action,
            final Instant now,
            final VerifiedRuntimeIdentity identity,
            final RuntimeAuthorizationReason reason) {
        final RuntimeAuthorizationEvent event =
                RuntimeAuthorizationEvent.denied(
                        UUID.randomUUID(),
                        invocationId,
                        action,
                        reason,
                        now,
                        identity,
                        policy.fingerprint());
        recordOrDeny(event, invocationId, action);
        return new RuntimeAuthorizationException(invocationId, action, reason);
    }

    private void recordOrDeny(
            final RuntimeAuthorizationEvent event,
            final UUID invocationId,
            final RuntimeAction action) {
        try {
            audit.record(event);
        } catch (final RuntimeException exception) {
            throw new RuntimeAuthorizationException(
                    invocationId, action, RuntimeAuthorizationReason.AUDIT_UNAVAILABLE);
        }
    }

    private static RuntimeAuthorizationReason mapIdentityReason(
            final RuntimeIdentityRejectionReason reason) {
        return switch (Objects.requireNonNull(reason, "O motivo da identidade é obrigatório.")) {
            case UNCONFIGURED -> RuntimeAuthorizationReason.IDENTITY_UNCONFIGURED;
            case MISSING -> RuntimeAuthorizationReason.IDENTITY_MISSING;
            case INVALID -> RuntimeAuthorizationReason.IDENTITY_INVALID;
            case BLOCKED -> RuntimeAuthorizationReason.IDENTITY_BLOCKED;
            case REVOKED -> RuntimeAuthorizationReason.IDENTITY_REVOKED;
            case AUDIENCE_MISMATCH -> RuntimeAuthorizationReason.IDENTITY_AUDIENCE_MISMATCH;
            case AUTHORITY_UNAVAILABLE -> RuntimeAuthorizationReason.AUTHORITY_UNAVAILABLE;
            case ROLE_MAPPING_INVALID -> RuntimeAuthorizationReason.ROLE_MAPPING_INVALID;
        };
    }

    @Override
    public String toString() {
        return "RuntimeAuthorizationBoundary[policy=" + policy.version() + ", identity=redacted]";
    }
}
