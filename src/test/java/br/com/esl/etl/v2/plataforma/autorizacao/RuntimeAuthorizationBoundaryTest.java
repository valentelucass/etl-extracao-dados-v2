package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RuntimeAuthorizationBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-08-30T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID INVOCATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000910");

    @Test
    void deniesByDefaultAndAuditsTheUnconfiguredReason() {
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeAuthorizationBoundary boundary =
                new RuntimeAuthorizationBoundary(events::add, CLOCK);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, RuntimeAction.RUN));

        assertEquals(RuntimeAuthorizationReason.IDENTITY_UNCONFIGURED, exception.reason());
        assertEquals(INVOCATION_ID, exception.invocationId());
        assertEquals(RuntimeAction.RUN, exception.action());
        assertNull(exception.getCause());
        assertEquals(1, events.size());
        assertEquals(RuntimeAuthorizationDecision.DENY, events.get(0).decision());
        assertEquals(RuntimeAuthorizationReason.IDENTITY_UNCONFIGURED, events.get(0).reason());
        assertTrue(events.get(0).principalAuditReference().isEmpty());
        assertFalse(boundary.toString().contains("Principal"));
    }

    @ParameterizedTest
    @MethodSource("authorizedActions")
    void authorizesEveryExactRoleCombinationOnlyAfterSynchronousAudit(
            final RuntimeAction action, final RuntimeRoleSet roles) {
        final AtomicBoolean auditCompleted = new AtomicBoolean();
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeAuthorizationAudit audit =
                event -> {
                    events.add(event);
                    auditCompleted.set(true);
                };
        final RuntimeAuthorizationBoundary boundary = boundary(() -> identity(roles), audit);

        final AuthorizedRuntimeInvocation invocation = boundary.authorize(INVOCATION_ID, action);

        assertTrue(auditCompleted.get());
        assertEquals(1, events.size());
        assertEquals(RuntimeAuthorizationDecision.ALLOW, events.get(0).decision());
        assertEquals(RuntimeAuthorizationReason.AUTHORIZED, events.get(0).reason());
        assertEquals(events.get(0).eventId(), invocation.authorizationEventId());
        assertEquals(INVOCATION_ID, invocation.invocationId());
        assertEquals(action, invocation.action());
        assertEquals(NOW, invocation.authorizedAt());
        assertEquals(NOW.plusSeconds(1), invocation.validUntil());
        assertEquals(
                AuthorizationValueObjectsTest.reference(), invocation.principalAuditReference());
    }

    @ParameterizedTest
    @MethodSource("insufficientRoleCombinations")
    void deniesPartialOrUnrelatedRoles(final RuntimeAction action, final RuntimeRoleSet roles) {
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeAuthorizationBoundary boundary = boundary(() -> identity(roles), events::add);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, action));

        assertEquals(RuntimeAuthorizationReason.ROLE_NOT_GRANTED, exception.reason());
        assertEquals(RuntimeAuthorizationDecision.DENY, events.get(0).decision());
        assertEquals(
                AuthorizationValueObjectsTest.reference(),
                events.get(0).principalAuditReference().orElseThrow());
    }

    @Test
    void acceptsTheExactStartAndRejectsTheExactExpiryOfTheHalfOpenLifetime() {
        final List<RuntimeAuthorizationEvent> startEvents = new ArrayList<>();
        final VerifiedRuntimeIdentity startsNow =
                new VerifiedRuntimeIdentity(
                        AuthorizationValueObjectsTest.reference(),
                        RuntimePrincipalKind.SERVICE,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER),
                        NOW,
                        NOW.plusSeconds(1),
                        AuthorizationValueObjectsTest.authorityFingerprint());
        final RuntimeAuthorizationBoundary startBoundary =
                boundary(() -> startsNow, startEvents::add);

        startBoundary.authorize(INVOCATION_ID, RuntimeAction.STATUS);

        assertEquals(RuntimeAuthorizationDecision.ALLOW, startEvents.get(0).decision());

        final List<RuntimeAuthorizationEvent> expiryEvents = new ArrayList<>();
        final VerifiedRuntimeIdentity expiresNow =
                new VerifiedRuntimeIdentity(
                        AuthorizationValueObjectsTest.reference(),
                        RuntimePrincipalKind.SERVICE,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER),
                        NOW.minusSeconds(1),
                        NOW,
                        AuthorizationValueObjectsTest.authorityFingerprint());
        final RuntimeAuthorizationBoundary expiryBoundary =
                boundary(() -> expiresNow, expiryEvents::add);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> expiryBoundary.authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(RuntimeAuthorizationReason.IDENTITY_EXPIRED, exception.reason());
        assertEquals(RuntimeAuthorizationReason.IDENTITY_EXPIRED, expiryEvents.get(0).reason());
    }

    @Test
    void rejectsAnIdentityThatIsNotYetValid() {
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final VerifiedRuntimeIdentity futureIdentity =
                new VerifiedRuntimeIdentity(
                        AuthorizationValueObjectsTest.reference(),
                        RuntimePrincipalKind.OPERATOR,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER),
                        NOW.plusSeconds(1),
                        NOW.plusSeconds(2),
                        AuthorizationValueObjectsTest.authorityFingerprint());

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(() -> futureIdentity, events::add)
                                        .authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(RuntimeAuthorizationReason.IDENTITY_NOT_YET_VALID, exception.reason());
        assertEquals(RuntimeAuthorizationReason.IDENTITY_NOT_YET_VALID, events.get(0).reason());
    }

    @Test
    void resamplesTrustedTimeAfterVerificationAndRejectsAnIdentityThatExpiredMeanwhile() {
        final AtomicInteger clockCalls = new AtomicInteger();
        final Clock advancingClock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(final java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return clockCalls.getAndIncrement() == 0 ? NOW : NOW.plusSeconds(1);
                    }
                };
        final VerifiedRuntimeIdentity expiresDuringVerification =
                new VerifiedRuntimeIdentity(
                        AuthorizationValueObjectsTest.reference(),
                        RuntimePrincipalKind.SERVICE,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER),
                        NOW.minusSeconds(1),
                        NOW.plusSeconds(1),
                        AuthorizationValueObjectsTest.authorityFingerprint());
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeAuthorizationBoundary boundary =
                new RuntimeAuthorizationBoundary(
                        () -> expiresDuringVerification,
                        RuntimeAuthorizationPolicy.standard(),
                        events::add,
                        advancingClock);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(RuntimeAuthorizationReason.IDENTITY_EXPIRED, exception.reason());
        assertEquals(2, clockCalls.get());
        assertEquals(RuntimeAuthorizationReason.IDENTITY_EXPIRED, events.get(0).reason());
    }

    @Test
    void failsClosedWhenTrustedTimeMovesBackwardsDuringVerification() {
        final AtomicInteger clockCalls = new AtomicInteger();
        final Clock regressingClock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(final java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return clockCalls.getAndIncrement() == 0 ? NOW : NOW.minusSeconds(1);
                    }
                };
        final VerifiedRuntimeIdentity identity =
                new VerifiedRuntimeIdentity(
                        AuthorizationValueObjectsTest.reference(),
                        RuntimePrincipalKind.SERVICE,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER),
                        NOW.minusSeconds(2),
                        NOW,
                        AuthorizationValueObjectsTest.authorityFingerprint());
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeAuthorizationBoundary boundary =
                new RuntimeAuthorizationBoundary(
                        () -> identity,
                        RuntimeAuthorizationPolicy.standard(),
                        events::add,
                        regressingClock);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(
                RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE, exception.reason());
        assertEquals(2, clockCalls.get());
        assertEquals(
                RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE, events.get(0).reason());
        assertEquals(NOW, events.get(0).decidedAt());
    }

    @ParameterizedTest
    @MethodSource("identityRejections")
    void translatesProviderNeutralIdentityRejectionsIntoSanitizedAuditableReasons(
            final RuntimeIdentityRejectionReason identityReason,
            final RuntimeAuthorizationReason authorizationReason) {
        final List<RuntimeAuthorizationEvent> events = new ArrayList<>();
        final RuntimeIdentityVerifier verifier =
                () -> {
                    throw new RuntimeIdentityVerificationException(identityReason);
                };

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(verifier, events::add)
                                        .authorize(INVOCATION_ID, RuntimeAction.RUN));

        assertEquals(authorizationReason, exception.reason());
        assertEquals(authorizationReason, events.get(0).reason());
        assertTrue(events.get(0).principalAuditReference().isEmpty());
    }

    @Test
    void convertsNullOrUnexpectedVerifierFailuresWithoutLeakingTheirMessage() {
        final List<RuntimeAuthorizationEvent> nullEvents = new ArrayList<>();
        final RuntimeAuthorizationException nullIdentity =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(() -> null, nullEvents::add)
                                        .authorize(INVOCATION_ID, RuntimeAction.RUN));

        assertEquals(RuntimeAuthorizationReason.IDENTITY_INVALID, nullIdentity.reason());

        final String unsafeDetail = "synthetic-sensitive-verifier-detail";
        final List<RuntimeAuthorizationEvent> failureEvents = new ArrayList<>();
        final RuntimeIdentityVerifier failedVerifier =
                () -> {
                    throw new IllegalStateException(unsafeDetail);
                };
        final RuntimeAuthorizationException authorityFailure =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(failedVerifier, failureEvents::add)
                                        .authorize(INVOCATION_ID, RuntimeAction.RUN));

        assertEquals(RuntimeAuthorizationReason.AUTHORITY_UNAVAILABLE, authorityFailure.reason());
        assertFalse(authorityFailure.getMessage().contains(unsafeDetail));
        assertFalse(authorityFailure.toString().contains(unsafeDetail));
        assertNull(authorityFailure.getCause());
        assertEquals(
                RuntimeAuthorizationReason.AUTHORITY_UNAVAILABLE, failureEvents.get(0).reason());
    }

    @Test
    void failsClosedWhenTheSynchronousAuditFailsForAllowOrDeny() {
        final String unsafeDetail = "synthetic-sensitive-audit-detail";
        final RuntimeAuthorizationAudit failedAudit =
                event -> {
                    throw new IllegalStateException(unsafeDetail);
                };
        final RuntimeAuthorizationException allowFailure =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(
                                                () ->
                                                        identity(
                                                                RuntimeRoleSet.of(
                                                                        RuntimeRole
                                                                                .RUNTIME_OBSERVER)),
                                                failedAudit)
                                        .authorize(INVOCATION_ID, RuntimeAction.STATUS));
        final RuntimeAuthorizationException denyFailure =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                boundary(
                                                () ->
                                                        identity(
                                                                RuntimeRoleSet.of(
                                                                        RuntimeRole
                                                                                .RUNTIME_OBSERVER)),
                                                failedAudit)
                                        .authorize(INVOCATION_ID, RuntimeAction.RUN));

        assertEquals(RuntimeAuthorizationReason.AUDIT_UNAVAILABLE, allowFailure.reason());
        assertEquals(RuntimeAuthorizationReason.AUDIT_UNAVAILABLE, denyFailure.reason());
        assertFalse(allowFailure.getMessage().contains(unsafeDetail));
        assertFalse(denyFailure.toString().contains(unsafeDetail));
        assertNull(allowFailure.getCause());
        assertNull(denyFailure.getCause());
    }

    @Test
    void invokesTheVerifierAndAuditExactlyOncePerDecision() {
        final AtomicInteger verificationCalls = new AtomicInteger();
        final AtomicInteger auditCalls = new AtomicInteger();
        final RuntimeIdentityVerifier verifier =
                () -> {
                    verificationCalls.incrementAndGet();
                    return identity(RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER));
                };
        final RuntimeAuthorizationBoundary boundary =
                boundary(verifier, event -> auditCalls.incrementAndGet());

        boundary.authorize(INVOCATION_ID, RuntimeAction.STATUS);

        assertEquals(1, verificationCalls.get());
        assertEquals(1, auditCalls.get());
    }

    @Test
    void failsClosedWithoutVerifyingOrAuditingWhenTimeCannotBeEstablished() {
        final AtomicInteger verificationCalls = new AtomicInteger();
        final AtomicInteger auditCalls = new AtomicInteger();
        final RuntimeIdentityVerifier verifier =
                () -> {
                    verificationCalls.incrementAndGet();
                    return identity(RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER));
                };
        final Clock failedClock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(final java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        throw new IllegalStateException("synthetic-clock-detail");
                    }
                };
        final RuntimeAuthorizationBoundary boundary =
                new RuntimeAuthorizationBoundary(
                        verifier,
                        RuntimeAuthorizationPolicy.standard(),
                        event -> auditCalls.incrementAndGet(),
                        failedClock);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(
                RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE, exception.reason());
        assertEquals(0, verificationCalls.get());
        assertEquals(0, auditCalls.get());
        assertFalse(exception.getMessage().contains("synthetic-clock-detail"));
    }

    @Test
    void secondClockFailureIsSanitizedAndAuditedWithoutAuthorizing() {
        final var clockCalls = new AtomicInteger();
        final var auditCalls = new AtomicInteger();
        final String sensitive = "synthetic-sensitive-second-clock";
        final Clock clock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(final java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        if (clockCalls.incrementAndGet() == 1) {
                            return NOW;
                        }
                        throw new IllegalStateException(sensitive);
                    }
                };
        final var boundary =
                new RuntimeAuthorizationBoundary(
                        () -> identity(RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER)),
                        RuntimeAuthorizationPolicy.standard(),
                        event -> {
                            assertEquals(
                                    RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE,
                                    event.reason());
                            auditCalls.incrementAndGet();
                        },
                        clock);

        final var failure =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () -> boundary.authorize(INVOCATION_ID, RuntimeAction.STATUS));

        assertEquals(RuntimeAuthorizationReason.TEMPORAL_VALIDATION_UNAVAILABLE, failure.reason());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.toString().contains(sensitive));
        assertEquals(2, clockCalls.get());
        assertEquals(1, auditCalls.get());
    }

    @Test
    void rejectsIncompleteCompositionAndInvocationInputs() {
        final RuntimeAuthorizationAudit audit = event -> {};
        final RuntimeIdentityVerifier verifier =
                () -> identity(RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER));

        assertThrows(
                NullPointerException.class, () -> new RuntimeAuthorizationBoundary(null, CLOCK));
        assertThrows(
                NullPointerException.class, () -> new RuntimeAuthorizationBoundary(audit, null));
        assertThrows(
                NullPointerException.class,
                () ->
                        new RuntimeAuthorizationBoundary(
                                null, RuntimeAuthorizationPolicy.standard(), audit, CLOCK));
        assertThrows(
                NullPointerException.class,
                () -> new RuntimeAuthorizationBoundary(verifier, null, audit, CLOCK));
        assertThrows(
                NullPointerException.class,
                () ->
                        new RuntimeAuthorizationBoundary(
                                verifier, RuntimeAuthorizationPolicy.standard(), null, CLOCK));
        final RuntimeAuthorizationBoundary boundary = boundary(verifier, audit);
        assertThrows(
                NullPointerException.class, () -> boundary.authorize(null, RuntimeAction.STATUS));
        assertThrows(NullPointerException.class, () -> boundary.authorize(INVOCATION_ID, null));
    }

    private static RuntimeAuthorizationBoundary boundary(
            final RuntimeIdentityVerifier verifier, final RuntimeAuthorizationAudit audit) {
        return new RuntimeAuthorizationBoundary(
                verifier, RuntimeAuthorizationPolicy.standard(), audit, CLOCK);
    }

    private static VerifiedRuntimeIdentity identity(final RuntimeRoleSet roles) {
        return new VerifiedRuntimeIdentity(
                AuthorizationValueObjectsTest.reference(),
                RuntimePrincipalKind.OPERATOR,
                roles,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1),
                AuthorizationValueObjectsTest.authorityFingerprint());
    }

    private static Stream<Arguments> authorizedActions() {
        return Stream.of(
                Arguments.of(RuntimeAction.RUN, RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR)),
                Arguments.of(
                        RuntimeAction.REPLAY,
                        RuntimeRoleSet.of(
                                RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_REPLAY)),
                Arguments.of(
                        RuntimeAction.SWEEP_PREVIEW,
                        RuntimeRoleSet.of(
                                RuntimeRole.RUNTIME_OBSERVER, RuntimeRole.RUNTIME_SWEEP_REVIEW)),
                Arguments.of(
                        RuntimeAction.SWEEP_APPLY,
                        RuntimeRoleSet.of(
                                RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_SWEEP_APPLY)),
                Arguments.of(
                        RuntimeAction.FORCE_RUN,
                        RuntimeRoleSet.of(
                                RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_FORCE_RUN)),
                Arguments.of(
                        RuntimeAction.STATUS, RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER)));
    }

    private static Stream<Arguments> insufficientRoleCombinations() {
        return Stream.of(
                Arguments.of(RuntimeAction.RUN, RuntimeRoleSet.none()),
                Arguments.of(RuntimeAction.REPLAY, RuntimeRoleSet.of(RuntimeRole.RUNTIME_REPLAY)),
                Arguments.of(
                        RuntimeAction.SWEEP_PREVIEW,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_SWEEP_REVIEW)),
                Arguments.of(
                        RuntimeAction.SWEEP_APPLY,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_SWEEP_APPLY)),
                Arguments.of(
                        RuntimeAction.FORCE_RUN, RuntimeRoleSet.of(RuntimeRole.RUNTIME_FORCE_RUN)),
                Arguments.of(
                        RuntimeAction.STATUS, RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR)));
    }

    private static Stream<Arguments> identityRejections() {
        return Stream.of(
                Arguments.of(
                        RuntimeIdentityRejectionReason.UNCONFIGURED,
                        RuntimeAuthorizationReason.IDENTITY_UNCONFIGURED),
                Arguments.of(
                        RuntimeIdentityRejectionReason.MISSING,
                        RuntimeAuthorizationReason.IDENTITY_MISSING),
                Arguments.of(
                        RuntimeIdentityRejectionReason.INVALID,
                        RuntimeAuthorizationReason.IDENTITY_INVALID),
                Arguments.of(
                        RuntimeIdentityRejectionReason.BLOCKED,
                        RuntimeAuthorizationReason.IDENTITY_BLOCKED),
                Arguments.of(
                        RuntimeIdentityRejectionReason.REVOKED,
                        RuntimeAuthorizationReason.IDENTITY_REVOKED),
                Arguments.of(
                        RuntimeIdentityRejectionReason.AUDIENCE_MISMATCH,
                        RuntimeAuthorizationReason.IDENTITY_AUDIENCE_MISMATCH),
                Arguments.of(
                        RuntimeIdentityRejectionReason.AUTHORITY_UNAVAILABLE,
                        RuntimeAuthorizationReason.AUTHORITY_UNAVAILABLE),
                Arguments.of(
                        RuntimeIdentityRejectionReason.ROLE_MAPPING_INVALID,
                        RuntimeAuthorizationReason.ROLE_MAPPING_INVALID));
    }
}
