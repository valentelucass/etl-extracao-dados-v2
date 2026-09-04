package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Registro compartilhado com cardinalidade limitada pelo enum de operações. */
final class GraphQlCircuitBreakerRegistry {

    private final GraphQlCircuitBreakerPolicy policy;
    private final Clock clock;
    private final CircuitState[] states = new CircuitState[GraphQlReadOperation.values().length];

    GraphQlCircuitBreakerRegistry(final GraphQlCircuitBreakerPolicy policy, final Clock clock) {
        this.policy = Objects.requireNonNull(policy, "A política de circuito é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio de circuito é obrigatório.");
        for (int index = 0; index < states.length; index++) {
            states[index] = new CircuitState();
        }
    }

    Permit acquire(final GraphQlReadOperation operation) {
        return state(operation).acquire(clock.instant(), policy.cooldown(), operation);
    }

    void onSuccess(final GraphQlReadOperation operation, final Permit permit) {
        state(operation).onSuccess(permit);
    }

    void onUnavailableFailure(final GraphQlReadOperation operation, final Permit permit) {
        state(operation).onUnavailableFailure(clock.instant(), policy.failureThreshold(), permit);
    }

    void onReachableFailure(final GraphQlReadOperation operation, final Permit permit) {
        state(operation).onReachableFailure(permit);
    }

    void onAbandoned(final GraphQlReadOperation operation, final Permit permit) {
        state(operation).onAbandoned(permit);
    }

    private CircuitState state(final GraphQlReadOperation operation) {
        return states[Objects.requireNonNull(operation, "A operação é obrigatória.").ordinal()];
    }

    record Permit(long generation, boolean halfOpenProbe) {}

    private static final class CircuitState {

        private int consecutiveFailures;
        private Instant openedAt;
        private boolean halfOpenProbeInFlight;
        private long generation;

        synchronized Permit acquire(
                final Instant now,
                final java.time.Duration cooldown,
                final GraphQlReadOperation operation) {
            if (openedAt == null) {
                return new Permit(generation, false);
            }
            if (now.isBefore(openedAt.plus(cooldown)) || halfOpenProbeInFlight) {
                throw new GraphQlCircuitOpenException(operation);
            }
            halfOpenProbeInFlight = true;
            return new Permit(generation, true);
        }

        synchronized void onSuccess(final Permit permit) {
            if (!current(permit)) {
                return;
            }
            consecutiveFailures = 0;
            if (openedAt != null) {
                close();
            }
        }

        synchronized void onUnavailableFailure(
                final Instant now, final int threshold, final Permit permit) {
            if (!current(permit)) {
                return;
            }
            if (permit.halfOpenProbe()) {
                open(now);
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                open(now);
            }
        }

        synchronized void onReachableFailure(final Permit permit) {
            if (!current(permit)) {
                return;
            }
            consecutiveFailures = 0;
            if (openedAt != null) {
                close();
            }
        }

        synchronized void onAbandoned(final Permit permit) {
            if (current(permit) && permit.halfOpenProbe()) {
                halfOpenProbeInFlight = false;
            }
        }

        private boolean current(final Permit permit) {
            return permit.generation() == generation;
        }

        private void open(final Instant now) {
            openedAt = now;
            halfOpenProbeInFlight = false;
            generation++;
        }

        private void close() {
            openedAt = null;
            halfOpenProbeInFlight = false;
            generation++;
        }
    }
}
