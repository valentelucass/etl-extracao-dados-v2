package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationValueObjectsTest {

    private static final Instant NOW = Instant.parse("2026-08-30T15:00:00Z");
    private static final String PRINCIPAL_HASH = "a".repeat(64);
    private static final String AUTHORITY_HASH = "b".repeat(64);
    private static final String RUNTIME_HASH = "c".repeat(64);

    @Test
    void acceptsOnlyTheFixedOpaqueReferenceFormatAndSanitizesItsRendering() {
        final PrincipalAuditReference reference =
                new PrincipalAuditReference("  " + PRINCIPAL_HASH.toUpperCase() + "  ");
        final AuthorizationPolicyFingerprint fingerprint =
                new AuthorizationPolicyFingerprint("  " + AUTHORITY_HASH.toUpperCase() + "  ");

        assertEquals(PRINCIPAL_HASH, reference.opaqueValue());
        assertEquals(AUTHORITY_HASH, fingerprint.sha256());
        assertFalse(reference.toString().contains(PRINCIPAL_HASH));
        assertFalse(fingerprint.toString().contains(AUTHORITY_HASH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrincipalAuditReference("synthetic-operator"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizationPolicyFingerprint("not-a-fingerprint"));
        assertThrows(NullPointerException.class, () -> new PrincipalAuditReference(null));
        assertThrows(NullPointerException.class, () -> new AuthorizationPolicyFingerprint(null));
    }

    @Test
    void verifiedIdentityRequiresAValidHalfOpenLifetimeAndRedactsItself() {
        final VerifiedRuntimeIdentity identity = identity(RuntimeRole.RUNTIME_OBSERVER);

        assertEquals(RuntimePrincipalKind.OPERATOR, identity.kind());
        assertEquals(NOW.minusSeconds(1), identity.validFrom());
        assertEquals(NOW.plusSeconds(1), identity.expiresAt());
        assertEquals(PRINCIPAL_HASH, identity.auditReference().opaqueValue());
        assertTrue(identity.roles().has(RuntimeRole.RUNTIME_OBSERVER));
        assertEquals(AUTHORITY_HASH, identity.authorityPolicyFingerprint().sha256());
        assertEquals("VerifiedRuntimeIdentity[redacted]", identity.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new VerifiedRuntimeIdentity(
                                reference(),
                                RuntimePrincipalKind.SERVICE,
                                RuntimeRoleSet.none(),
                                NOW,
                                NOW,
                                authorityFingerprint()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new VerifiedRuntimeIdentity(
                                reference(),
                                RuntimePrincipalKind.SERVICE,
                                RuntimeRoleSet.none(),
                                NOW,
                                NOW.minusSeconds(1),
                                authorityFingerprint()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new VerifiedRuntimeIdentity(
                                null,
                                RuntimePrincipalKind.SERVICE,
                                RuntimeRoleSet.none(),
                                NOW,
                                NOW.plusSeconds(1),
                                authorityFingerprint()));
    }

    @Test
    void verificationFailuresAndTheUnconfiguredVerifierExposeNoIdentityMaterial() {
        final RuntimeIdentityVerificationException exception =
                assertThrows(
                        RuntimeIdentityVerificationException.class,
                        RuntimeIdentityVerifier.denyAll()::verify);

        assertEquals(RuntimeIdentityRejectionReason.UNCONFIGURED, exception.reason());
        assertEquals("RuntimeIdentityVerificationException[redacted]", exception.toString());
        assertNull(exception.getCause());
        assertSame(
                UnconfiguredRuntimeIdentityVerifier.instance(), RuntimeIdentityVerifier.denyAll());
        assertTrue(UnconfiguredRuntimeIdentityVerifier.instance().toString().contains("deny-all"));
        assertThrows(
                NullPointerException.class, () -> new RuntimeIdentityVerificationException(null));
    }

    @Test
    void auditEventsCarryOnlyTechnicalOrOpaqueValues() {
        final UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        final UUID invocationId = UUID.fromString("00000000-0000-0000-0000-000000000902");
        final VerifiedRuntimeIdentity identity = identity(RuntimeRole.RUNTIME_OBSERVER);
        final AuthorizationPolicyFingerprint runtimeFingerprint =
                new AuthorizationPolicyFingerprint(RUNTIME_HASH);
        final RuntimeAuthorizationEvent allowed =
                RuntimeAuthorizationEvent.allowed(
                        eventId,
                        invocationId,
                        RuntimeAction.STATUS,
                        NOW,
                        identity,
                        runtimeFingerprint);
        final RuntimeAuthorizationEvent denied =
                RuntimeAuthorizationEvent.denied(
                        UUID.randomUUID(),
                        invocationId,
                        RuntimeAction.RUN,
                        RuntimeAuthorizationReason.IDENTITY_MISSING,
                        NOW,
                        null,
                        runtimeFingerprint);

        assertEquals(eventId, allowed.eventId());
        assertEquals(invocationId, allowed.invocationId());
        assertEquals(RuntimeAction.STATUS, allowed.action());
        assertEquals(RuntimeAuthorizationDecision.ALLOW, allowed.decision());
        assertEquals(RuntimeAuthorizationReason.AUTHORIZED, allowed.reason());
        assertEquals(NOW, allowed.decidedAt());
        assertEquals(RuntimePrincipalKind.OPERATOR, allowed.principalKind().orElseThrow());
        assertEquals(reference(), allowed.principalAuditReference().orElseThrow());
        assertEquals(authorityFingerprint(), allowed.authorityPolicyFingerprint().orElseThrow());
        assertEquals(runtimeFingerprint, allowed.runtimePolicyFingerprint());
        assertFalse(allowed.toString().contains(PRINCIPAL_HASH));
        assertFalse(allowed.toString().contains(AUTHORITY_HASH));
        assertFalse(allowed.toString().contains(RUNTIME_HASH));
        assertTrue(denied.principalAuditReference().isEmpty());
        assertTrue(denied.principalKind().isEmpty());
        assertTrue(denied.authorityPolicyFingerprint().isEmpty());
        assertEquals(RuntimeAuthorizationDecision.DENY, denied.decision());
        assertTrue(denied.toString().contains("identity=absent"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeAuthorizationEvent.denied(
                                UUID.randomUUID(),
                                invocationId,
                                RuntimeAction.RUN,
                                RuntimeAuthorizationReason.AUTHORIZED,
                                NOW,
                                null,
                                runtimeFingerprint));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeAuthorizationEvent.allowed(
                                UUID.randomUUID(),
                                invocationId,
                                RuntimeAction.STATUS,
                                NOW,
                                null,
                                runtimeFingerprint));
    }

    @Test
    void authorizedCapabilityIsActionBoundAndSanitized() {
        final UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000903");
        final UUID invocationId = UUID.fromString("00000000-0000-0000-0000-000000000904");
        final AuthorizedRuntimeInvocation invocation =
                new AuthorizedRuntimeInvocation(
                        eventId,
                        invocationId,
                        RuntimeAction.STATUS,
                        NOW,
                        NOW.plusSeconds(1),
                        reference(),
                        authorityFingerprint(),
                        new AuthorizationPolicyFingerprint(RUNTIME_HASH));

        assertEquals(eventId, invocation.authorizationEventId());
        assertEquals(invocationId, invocation.invocationId());
        assertEquals(RuntimeAction.STATUS, invocation.action());
        assertEquals(NOW, invocation.authorizedAt());
        assertEquals(NOW.plusSeconds(1), invocation.validUntil());
        assertTrue(invocation.isValidAt(NOW));
        assertFalse(invocation.isValidAt(NOW.plusSeconds(1)));
        assertEquals(reference(), invocation.principalAuditReference());
        assertEquals(authorityFingerprint(), invocation.authorityPolicyFingerprint());
        assertEquals(RUNTIME_HASH, invocation.runtimePolicyFingerprint().sha256());
        assertNotNull(invocation.toString());
        assertFalse(invocation.toString().contains(PRINCIPAL_HASH));
        assertFalse(invocation.toString().contains(AUTHORITY_HASH));
        assertFalse(invocation.toString().contains(RUNTIME_HASH));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AuthorizedRuntimeInvocation(
                                null,
                                invocationId,
                                RuntimeAction.STATUS,
                                NOW,
                                NOW.plusSeconds(1),
                                reference(),
                                authorityFingerprint(),
                                new AuthorizationPolicyFingerprint(RUNTIME_HASH)));
    }

    static VerifiedRuntimeIdentity identity(final RuntimeRole... roles) {
        return new VerifiedRuntimeIdentity(
                reference(),
                RuntimePrincipalKind.OPERATOR,
                RuntimeRoleSet.of(roles),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1),
                authorityFingerprint());
    }

    static PrincipalAuditReference reference() {
        return new PrincipalAuditReference(PRINCIPAL_HASH);
    }

    static AuthorizationPolicyFingerprint authorityFingerprint() {
        return new AuthorizationPolicyFingerprint(AUTHORITY_HASH);
    }
}
