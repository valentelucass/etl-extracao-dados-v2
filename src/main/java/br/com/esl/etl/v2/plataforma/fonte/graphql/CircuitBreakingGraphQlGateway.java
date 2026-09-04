package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import java.time.Clock;
import java.util.Objects;

/** Interrompe indisponibilidade repetida sem confundir drift/envelope com falha de alcance. */
public final class CircuitBreakingGraphQlGateway implements GraphQlGateway {

    private final GraphQlGateway delegate;
    private final GraphQlCircuitBreakerRegistry registry;

    public CircuitBreakingGraphQlGateway(
            final GraphQlGateway delegate,
            final GraphQlCircuitBreakerPolicy policy,
            final Clock clock) {
        this(delegate, new GraphQlCircuitBreakerRegistry(policy, clock));
    }

    CircuitBreakingGraphQlGateway(
            final GraphQlGateway delegate, final GraphQlCircuitBreakerRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "O gateway GraphQL é obrigatório.");
        this.registry = Objects.requireNonNull(registry, "O registro de circuito é obrigatório.");
    }

    @Override
    public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
        Objects.requireNonNull(request, "A requisição GraphQL é obrigatória.");
        final GraphQlCircuitBreakerRegistry.Permit permit = registry.acquire(request.operation());
        try {
            final GraphQlPageResponse response = delegate.fetch(request);
            registry.onSuccess(request.operation(), permit);
            return response;
        } catch (final GraphQlUnavailableException exception) {
            registry.onUnavailableFailure(request.operation(), permit);
            throw exception;
        } catch (final ResilienceCancelledException
                | ResilienceBudgetExceededException
                | ResilienceTimeoutException
                | GraphQlTransportException exception) {
            registry.onAbandoned(request.operation(), permit);
            throw exception;
        } catch (final Error error) {
            registry.onAbandoned(request.operation(), permit);
            throw error;
        } catch (final RuntimeException exception) {
            registry.onReachableFailure(request.operation(), permit);
            throw exception;
        }
    }
}
