package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Clock;
import java.util.Objects;

/** Decorador que interrompe chamadas repetidas após indisponibilidade transitória da fonte. */
public final class CircuitBreakingDataExportGateway implements DataExportGateway {

    private final DataExportGateway delegate;
    private final DataExportCircuitBreakerRegistry registry;

    public CircuitBreakingDataExportGateway(
            final DataExportGateway delegate,
            final DataExportCircuitBreakerPolicy policy,
            final Clock clock) {
        this(delegate, new DataExportCircuitBreakerRegistry(policy, clock));
    }

    CircuitBreakingDataExportGateway(
            final DataExportGateway delegate, final DataExportCircuitBreakerRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "O gateway Data Export é obrigatório.");
        this.registry = Objects.requireNonNull(registry, "O registro de circuitos é obrigatório.");
    }

    @Override
    public DataExportPageResponse fetch(final DataExportPageRequest request) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        final DataExportCircuitBreakerRegistry.Permit permit = registry.acquire(request.template());
        try {
            final DataExportPageResponse response = delegate.fetch(request);
            registry.onSuccess(request.template(), permit);
            return response;
        } catch (final DataExportUnavailableException exception) {
            registry.onUnavailableFailure(request.template(), permit);
            throw exception;
        } catch (final RuntimeException exception) {
            registry.onReachableFailure(request.template(), permit);
            throw exception;
        }
    }
}
