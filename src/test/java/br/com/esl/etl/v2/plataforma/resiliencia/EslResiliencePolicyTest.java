package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EslResiliencePolicyTest {

    @Test
    void acceptsConservativeBoundedPolicy() {
        final EslResiliencePolicy policy = ResilienceTestSupport.policy();

        assertEquals(1, policy.maxInFlight());
        assertEquals(4, policy.maxRepartitions());
        assertEquals(Duration.ofSeconds(5), policy.maxRetryAfter());
    }

    @Test
    void rejectsUnsafeConcurrencyAndBudgets() {
        assertThrows(IllegalArgumentException.class, () -> policy(2, 10, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> policy(1, 0, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> policy(1, 5, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> policy(1, 10, 5, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(1, EslResiliencePolicy.MAX_REQUESTS_PER_CYCLE + 1, 5, 1));
    }

    @Test
    void rejectsInconsistentTimeoutsRetryAndCircuit() {
        final EslResiliencePolicy base = ResilienceTestSupport.policy();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(
                                base,
                                Duration.ofSeconds(11),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                2,
                                Duration.ofSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(
                                base,
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(10),
                                2,
                                Duration.ofSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(
                                base,
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                0,
                                Duration.ofSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        copy(
                                base,
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                2,
                                Duration.ofSeconds(31)));
    }

    @Test
    void rejectsDurationsOutsideGlobalCeilings() {
        final EslResiliencePolicy base = ResilienceTestSupport.policy();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EslResiliencePolicy(
                                Duration.ofMinutes(2),
                                1,
                                10,
                                5,
                                base.requestTimeout(),
                                base.stepTimeout(),
                                base.cycleTimeout(),
                                base.maxRetryAfter(),
                                base.maxRepartitions(),
                                base.circuitFailureThreshold(),
                                base.circuitCooldown()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EslResiliencePolicy(
                                Duration.ZERO,
                                1,
                                10,
                                5,
                                Duration.ZERO,
                                base.stepTimeout(),
                                base.cycleTimeout(),
                                base.maxRetryAfter(),
                                base.maxRepartitions(),
                                base.circuitFailureThreshold(),
                                base.circuitCooldown()));
    }

    private static EslResiliencePolicy policy(
            final int maxInFlight,
            final int cycleRequests,
            final int workloadRequests,
            final int repartitions) {
        final EslResiliencePolicy base = ResilienceTestSupport.policy();
        return new EslResiliencePolicy(
                base.minimumRequestInterval(),
                maxInFlight,
                cycleRequests,
                workloadRequests,
                base.requestTimeout(),
                base.stepTimeout(),
                base.cycleTimeout(),
                base.maxRetryAfter(),
                repartitions,
                base.circuitFailureThreshold(),
                base.circuitCooldown());
    }

    private static EslResiliencePolicy copy(
            final EslResiliencePolicy base,
            final Duration request,
            final Duration step,
            final Duration cycle,
            final Duration retryAfter,
            final int circuitThreshold,
            final Duration circuitCooldown) {
        return new EslResiliencePolicy(
                base.minimumRequestInterval(),
                base.maxInFlight(),
                base.maxRequestsPerCycle(),
                base.maxRequestsPerWorkload(),
                request,
                step,
                cycle,
                retryAfter,
                base.maxRepartitions(),
                circuitThreshold,
                circuitCooldown);
    }
}
