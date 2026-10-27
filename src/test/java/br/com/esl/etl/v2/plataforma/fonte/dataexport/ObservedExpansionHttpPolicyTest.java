package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ObservedExpansionHttpPolicyTest {
    @Test
    void materializedRetryTwoIsRejectedBeforeInfoOrData() {
        final var f =
                fixture(
                        DataExportTransport.GET_WITH_BODY,
                        2,
                        Duration.ofSeconds(2),
                        URI.create("https://synthetic.invalid/v1"),
                        Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
        assertEquals(0, f.cycle.sourceRequests());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTransport.class,
            names = {"GET_WITH_QUERY", "POST_JSON"})
    void incompatibleTransportRejectedBeforeInfoOrData(final DataExportTransport transport) {
        final var f =
                fixture(
                        transport,
                        1,
                        Duration.ofSeconds(2),
                        URI.create("https://synthetic.invalid/v1"),
                        Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void redirectsRejectedFromActualClient() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        f.client.redirect = HttpClient.Redirect.ALWAYS;
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void loopbackHttpIsNotAcceptedForObservedProofEvenThoughLegacyAllowsIt() {
        final var f =
                fixture(
                        DataExportTransport.GET_WITH_BODY,
                        1,
                        Duration.ofSeconds(2),
                        URI.create("http://localhost/v1"),
                        Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void tooSlowRequestTimeoutRejectedFromMaterializedSource() {
        final var policy =
                new br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy(
                        Duration.ofSeconds(3),
                        1,
                        20,
                        5,
                        Duration.ofSeconds(31),
                        Duration.ofSeconds(180),
                        Duration.ofSeconds(1080),
                        Duration.ZERO,
                        0,
                        3,
                        Duration.ofSeconds(1));
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(
                        ObservedExpansionCaptureTemplate.INV,
                        DataExportTransport.GET_WITH_BODY,
                        1,
                        Duration.ofSeconds(31),
                        URI.create("https://synthetic.invalid/v1"),
                        policy);
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void lessThanThreeSecondsCannotBeDeclaredAsTheProofPolicy() {
        final var f =
                fixture(
                        DataExportTransport.GET_WITH_BODY,
                        1,
                        Duration.ofSeconds(2),
                        URI.create("https://synthetic.invalid/v1"),
                        Duration.ofSeconds(2));
        assertThrows(IllegalArgumentException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void callerFingerprintCannotReplaceActualSourceMaterialization() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var factory =
                new DataExportHttpGatewayFactory(
                        f.source.settings().materialize(key -> "synthetic-token"),
                        f.client,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        new DataExportCircuitBreakerRegistry(
                                new DataExportCircuitBreakerPolicy(3, Duration.ofSeconds(1)),
                                Clock.systemUTC()),
                        Optional.of(DataExportRuntimeConfigurationFingerprint.from(f.source)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        factory.prepareObservedTransport(
                                f.plan,
                                f.cycle,
                                f.governor,
                                f.cancellation,
                                DataExportHttpAttemptObserver.noop()));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void samePolicyFromDifferentGovernorIsRefused() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var other = new EslRequestGovernor(f.policy, f.nanos::get, delay -> {});
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        f.factory.prepareObservedTransport(
                                f.plan,
                                f.cycle,
                                other,
                                f.cancellation,
                                DataExportHttpAttemptObserver.noop()));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void changedCyclePolicyAndCancellationCannotBeBorrowed() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var different =
                new EslRequestGovernor(
                        ObservedHttpTransportTestSupport.policy(4, Duration.ofSeconds(3)),
                        f.nanos::get,
                        delay -> {});
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        f.factory.prepareObservedTransport(
                                f.plan,
                                different.beginCycle(f.cancellation),
                                different,
                                f.cancellation,
                                DataExportHttpAttemptObserver.noop()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        f.factory.prepareObservedTransport(
                                f.plan,
                                f.cycle,
                                f.governor,
                                () -> false,
                                DataExportHttpAttemptObserver.noop()));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void otherSourceOrTenantCannotBeDeclaredByThePlan() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        for (final boolean source : new boolean[] {true, false}) {
            final var p = f.plan;
            final var other =
                    new ObservedExpansionCapturePlan(
                            p.captureExecutionId(),
                            p.template(),
                            source ? "other-source" : p.sourceInstance(),
                            source ? p.tenantScope() : "other-tenant",
                            p.businessWindow(),
                            p.sourceZone(),
                            p.transport(),
                            p.orderBy(),
                            p.envelope(),
                            p.per(),
                            p.limits());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            f.factory.prepareObservedTransport(
                                    other,
                                    f.cycle,
                                    f.governor,
                                    f.cancellation,
                                    DataExportHttpAttemptObserver.noop()));
        }
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void invalidBearerCanaryCannotEscapeSourcePreparationOrHeaderConstruction() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final String canary = "SYNTHETIC_SECRET_CANARY_2036";
        final String invalid = canary + "\r\nInjected: " + canary;
        final var prepared =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new DataExportHttpGatewayFactory(
                                        f.source, key -> invalid, f.client, Clock.systemUTC()));
        assertChainContainsNo(prepared, canary);
        final var provider =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                DataExportHttpGatewayFactory.prepareObservedSource(
                                        f.source,
                                        key -> {
                                            final var error =
                                                    new IllegalArgumentException(
                                                            canary,
                                                            new IllegalStateException(canary));
                                            error.addSuppressed(new IllegalStateException(canary));
                                            throw error;
                                        },
                                        Clock.systemUTC()));
        assertChainContainsNo(provider, canary);
        assertEquals(0, f.client.requests.size());
        assertEquals(0, f.cycle.sourceRequests());
    }

    @Test
    void directRequestBuilderRuntimeCannotLeakAnyCauseOrSuppressedCanary() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final String canary = "SYNTHETIC_BUILDER_CANARY_2036";
        final var executor =
                new DataExportHttpExecutor(
                        f.client,
                        f.source.settings().materialize(key -> "synthetic-token"),
                        ignored -> {});
        final var capture =
                new ObservedExpansionHttpTransport.RawCapture(
                        f.plan.request(1), new java.util.concurrent.atomic.AtomicBoolean());
        final var failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                executor.executeObservedOnce(
                                        10633,
                                        timeout -> {
                                            // Exercises the actual JDK builder's invalid header
                                            // rejection, before HttpClient.sendAsync.
                                            return java.net.http.HttpRequest.newBuilder(
                                                            java.net.URI.create(
                                                                    "https://synthetic.invalid/v1/api/analytics/reports/10633/data"))
                                                    .timeout(timeout)
                                                    .header(
                                                            "Authorization",
                                                            "Bearer " + canary + "\r\n" + canary)
                                                    .method(
                                                            "GET",
                                                            java.net.http.HttpRequest.BodyPublishers
                                                                    .ofString("{}"))
                                                    .build();
                                        },
                                        1024,
                                        capture));
        assertChainContainsNo(failure, canary);
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void runtimeProviderFailureDropsOriginalCauseAndSuppressedPayload() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final String canary = "SYNTHETIC_PROVIDER_CANARY_2036";
        final var provider =
                new IllegalStateException(canary, new IllegalArgumentException(canary));
        provider.addSuppressed(new IllegalStateException(canary));
        f.client.failure = provider;
        final var failure =
                assertThrows(RuntimeException.class, () -> f.prepare().fetch(f.plan.request(1)));
        assertChainContainsNo(failure, canary);
        assertEquals(1, f.client.requests.size());
    }

    private static void assertChainContainsNo(final Throwable failure, final String canary) {
        final var queue = new java.util.ArrayDeque<Throwable>();
        final java.util.Set<Throwable> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        queue.add(failure);
        while (!queue.isEmpty()) {
            final Throwable value = queue.remove();
            if (!seen.add(value)) {
                continue;
            }
            assertFalse(value.toString().contains(canary));
            if (value.getCause() != null) {
                queue.add(value.getCause());
            }
            for (final Throwable suppressed : value.getSuppressed()) {
                queue.add(suppressed);
            }
        }
        assertEquals(1, seen.size());
    }

    private static ObservedHttpTransportTestSupport.Fixture fixture(
            final DataExportTransport transport,
            final int attempts,
            final Duration timeout,
            final URI uri,
            final Duration interval) {
        return new ObservedHttpTransportTestSupport.Fixture(
                ObservedExpansionCaptureTemplate.INV,
                transport,
                attempts,
                timeout,
                uri,
                ObservedHttpTransportTestSupport.policy(5, interval));
    }
}
