package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro compartilhado e limitado pelos templates conhecidos, usado por {@code /info} e {@code
 * /data}.
 */
final class DataExportCircuitBreakerRegistry {

    private final DataExportCircuitBreakerPolicy policy;
    private final Clock clock;
    private final Map<DataExportTemplate, CircuitState> states = new ConcurrentHashMap<>();

    DataExportCircuitBreakerRegistry(
            final DataExportCircuitBreakerPolicy policy, final Clock clock) {
        this.policy = Objects.requireNonNull(policy, "A política de circuito é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio do circuito é obrigatório.");
    }

    Permit acquire(final DataExportTemplate template) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        return states.computeIfAbsent(template, ignored -> new CircuitState())
                .acquire(clock.instant(), policy.cooldown(), template.templateId());
    }

    void onSuccess(final DataExportTemplate template, final Permit permit) {
        state(template).onSuccess(permit);
    }

    void onUnavailableFailure(final DataExportTemplate template, final Permit permit) {
        state(template).onUnavailableFailure(clock.instant(), policy.failureThreshold(), permit);
    }

    void onReachableFailure(final DataExportTemplate template, final Permit permit) {
        state(template).onReachableFailure(permit);
    }

    private CircuitState state(final DataExportTemplate template) {
        final CircuitState state = states.get(template);
        if (state == null) {
            throw new IllegalStateException("O estado do circuito Data Export não foi adquirido.");
        }
        return state;
    }

    record Permit(long generation, boolean halfOpenProbe) {}

    private static final class CircuitState {

        private int consecutiveUnavailableFailures;
        private Instant openedAt;
        private boolean halfOpenProbeInFlight;
        private long generation;

        synchronized Permit acquire(
                final Instant now, final java.time.Duration cooldown, final int templateId) {
            if (openedAt == null) {
                return new Permit(generation, false);
            }
            if (now.isBefore(openedAt)
                    || Duration.between(openedAt, now).compareTo(cooldown) < 0
                    || halfOpenProbeInFlight) {
                throw new DataExportCircuitOpenException(templateId);
            }
            halfOpenProbeInFlight = true;
            return new Permit(generation, true);
        }

        synchronized void onSuccess(final Permit permit) {
            if (!isCurrent(permit)) {
                return;
            }
            consecutiveUnavailableFailures = 0;
            if (openedAt != null) {
                closeAndAdvanceGeneration();
            }
        }

        synchronized void onUnavailableFailure(
                final Instant now, final int failureThreshold, final Permit permit) {
            if (!isCurrent(permit)) {
                return;
            }
            if (permit.halfOpenProbe()) {
                openAndAdvanceGeneration(now);
                return;
            }
            consecutiveUnavailableFailures = Math.incrementExact(consecutiveUnavailableFailures);
            if (consecutiveUnavailableFailures >= failureThreshold) {
                openAndAdvanceGeneration(now);
            }
        }

        synchronized void onReachableFailure(final Permit permit) {
            if (!isCurrent(permit)) {
                return;
            }
            consecutiveUnavailableFailures = 0;
            if (openedAt != null) {
                closeAndAdvanceGeneration();
            }
        }

        private boolean isCurrent(final Permit permit) {
            return generation == permit.generation();
        }

        private void openAndAdvanceGeneration(final Instant now) {
            final long nextGeneration = Math.incrementExact(generation);
            openedAt = now;
            halfOpenProbeInFlight = false;
            generation = nextGeneration;
        }

        private void closeAndAdvanceGeneration() {
            final long nextGeneration = Math.incrementExact(generation);
            openedAt = null;
            halfOpenProbeInFlight = false;
            generation = nextGeneration;
        }
    }
}
