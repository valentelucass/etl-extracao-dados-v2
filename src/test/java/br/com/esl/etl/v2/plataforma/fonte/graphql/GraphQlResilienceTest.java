package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GraphQlResilienceTest {

    @Test
    void retryAndCircuitPoliciesAreExplicitlyBounded() {
        final GraphQlRetryPolicy retry =
                new GraphQlRetryPolicy(3, Duration.ofMillis(10), Duration.ofMillis(25));
        assertEquals(Duration.ofMillis(10), retry.delayAfterFailure(1, Duration.ofSeconds(1)));
        assertEquals(Duration.ofMillis(20), retry.delayAfterFailure(2, Duration.ofSeconds(1)));
        assertEquals(Duration.ofMillis(25), retry.delayAfterFailure(3, Duration.ofSeconds(1)));
        assertEquals(Duration.ofMillis(5), retry.delayAfterFailure(4, Duration.ofMillis(5)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphQlRetryPolicy(0, Duration.ZERO, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlRetryPolicy(
                                GraphQlRetryPolicy.ABSOLUTE_MAXIMUM_ATTEMPTS + 1,
                                Duration.ZERO,
                                Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphQlRetryPolicy(1, Duration.ofSeconds(2), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class, () -> retry.delayAfterFailure(0, Duration.ZERO));

        final GraphQlCircuitBreakerPolicy circuit =
                GraphQlCircuitBreakerPolicy.from(resiliencePolicy());
        assertEquals(2, circuit.failureThreshold());
        assertEquals(Duration.ofSeconds(1), circuit.cooldown());
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphQlCircuitBreakerPolicy(0, Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphQlCircuitBreakerPolicy(1, Duration.ZERO));
    }

    @Test
    void circuitOpensHalfOpensAndClosesPerClosedOperation() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-31T12:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean unavailable = new AtomicBoolean(true);
        final GraphQlGateway gateway =
                new CircuitBreakingGraphQlGateway(
                        request -> {
                            calls.incrementAndGet();
                            if (unavailable.get()) {
                                throw new GraphQlUnavailableException(
                                        request.operation(),
                                        GraphQlUnavailableException.Reason.IO,
                                        new java.io.IOException("synthetic"));
                            }
                            return GraphQlTestSupport.usersPage(
                                    false, null, "{\"id\":930001,\"name\":\"N\"}");
                        },
                        new GraphQlCircuitBreakerPolicy(2, Duration.ofSeconds(1)),
                        clock);
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);

        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        final GraphQlCircuitOpenException open =
                assertThrows(GraphQlCircuitOpenException.class, () -> gateway.fetch(request));
        assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, open.operation());
        assertEquals(2, calls.get());

        clock.advance(Duration.ofSeconds(1));
        unavailable.set(false);
        assertEquals(1, gateway.fetch(request).nodeCount());
        assertEquals(1, gateway.fetch(request).nodeCount());
        assertEquals(4, calls.get());
    }

    @Test
    void reachableContractFailureResetsUnavailableCounterAndMessagesStaySanitized() {
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlGateway gateway =
                new CircuitBreakingGraphQlGateway(
                        request -> {
                            final int call = calls.incrementAndGet();
                            if (call == 2) {
                                throw new GraphQlResponseException(
                                        GraphQlResponseException.Reason.INVALID_ENVELOPE);
                            }
                            throw new GraphQlUnavailableException(
                                    request.operation(),
                                    GraphQlUnavailableException.Reason.IO,
                                    new java.io.IOException("synthetic-sensitive"));
                        },
                        new GraphQlCircuitBreakerPolicy(2, Duration.ofSeconds(1)),
                        Clock.systemUTC());
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);

        final GraphQlUnavailableException first =
                assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlResponseException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        assertEquals(3, calls.get());
        assertFalse(first.getMessage().contains("synthetic-sensitive"));
        assertNull(first.getCause());
        assertEquals(GraphQlUnavailableException.Reason.IO, first.reason());
        assertFalse(first.httpStatus().isPresent());

        final GraphQlUnavailableException status =
                new GraphQlUnavailableException(GraphQlReadOperation.USERS_SNAPSHOT, 503);
        assertEquals(503, status.httpStatus().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphQlUnavailableException(GraphQlReadOperation.USERS_SNAPSHOT, 99));

        final GraphQlHttpResponse response =
                new GraphQlHttpResponse(
                        200, "synthetic-sensitive", Optional.of("synthetic-delay"), true);
        assertFalse(response.toString().contains("synthetic-sensitive"));
        assertFalse(response.toString().contains("synthetic-delay"));
    }

    @Test
    void localCancellationAndSanitizedTransportFailurePreserveUnavailableFailures() {
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlGateway gateway =
                new CircuitBreakingGraphQlGateway(
                        request -> {
                            final int call = calls.incrementAndGet();
                            if (call == 2) {
                                throw new ResilienceCancelledException();
                            }
                            if (call == 3) {
                                throw new GraphQlTransportException();
                            }
                            throw new GraphQlUnavailableException(
                                    request.operation(),
                                    GraphQlUnavailableException.Reason.IO,
                                    new java.io.IOException("synthetic"));
                        },
                        new GraphQlCircuitBreakerPolicy(2, Duration.ofSeconds(1)),
                        Clock.systemUTC());
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);

        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        assertThrows(ResilienceCancelledException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlTransportException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        assertThrows(GraphQlCircuitOpenException.class, () -> gateway.fetch(request));
        assertEquals(4, calls.get());
    }

    @Test
    void abandonedHalfOpenProbeKeepsCircuitOpenAndReleasesOnlyTheProbe() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-31T12:00:00Z"));
        final GraphQlCircuitBreakerRegistry registry =
                new GraphQlCircuitBreakerRegistry(
                        new GraphQlCircuitBreakerPolicy(1, Duration.ofSeconds(1)), clock);
        final GraphQlReadOperation operation = GraphQlReadOperation.USERS_SNAPSHOT;
        final GraphQlCircuitBreakerRegistry.Permit initial = registry.acquire(operation);
        registry.onUnavailableFailure(operation, initial);
        assertThrows(GraphQlCircuitOpenException.class, () -> registry.acquire(operation));

        clock.advance(Duration.ofSeconds(1));
        final GraphQlCircuitBreakerRegistry.Permit abandonedProbe = registry.acquire(operation);
        assertTrue(abandonedProbe.halfOpenProbe());
        registry.onAbandoned(operation, abandonedProbe);

        final GraphQlCircuitBreakerRegistry.Permit replacementProbe = registry.acquire(operation);
        assertTrue(replacementProbe.halfOpenProbe());
    }

    @Test
    void errorDuringHalfOpenProbeDoesNotLeakTheProbeCapability() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-31T12:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlGateway gateway =
                new CircuitBreakingGraphQlGateway(
                        request ->
                                switch (calls.incrementAndGet()) {
                                    case 1 ->
                                            throw new GraphQlUnavailableException(
                                                    request.operation(),
                                                    GraphQlUnavailableException.Reason.IO,
                                                    new java.io.IOException("synthetic"));
                                    case 2 -> throw new AssertionError("synthetic");
                                    default ->
                                            GraphQlTestSupport.usersPage(
                                                    false, null, "{\"id\":930001,\"name\":\"N\"}");
                                },
                        new GraphQlCircuitBreakerPolicy(1, Duration.ofSeconds(1)),
                        clock);
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);

        assertThrows(GraphQlUnavailableException.class, () -> gateway.fetch(request));
        clock.advance(Duration.ofSeconds(1));
        assertThrows(AssertionError.class, () -> gateway.fetch(request));
        assertEquals(1, gateway.fetch(request).nodeCount());
        assertEquals(3, calls.get());
    }

    private static EslResiliencePolicy resiliencePolicy() {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                10,
                10,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofMillis(500),
                0,
                2,
                Duration.ofSeconds(1));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
