package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Clock;
import java.util.Objects;

/** Aplica ao recurso {@code /info} o mesmo circuito por template usado por {@code /data}. */
public final class CircuitBreakingDataExportTemplateInfoGateway
        implements DataExportTemplateInfoGateway {

    private final DataExportTemplateInfoGateway delegate;
    private final DataExportCircuitBreakerRegistry registry;

    public CircuitBreakingDataExportTemplateInfoGateway(
            final DataExportTemplateInfoGateway delegate,
            final DataExportCircuitBreakerPolicy policy,
            final Clock clock) {
        this(delegate, new DataExportCircuitBreakerRegistry(policy, clock));
    }

    CircuitBreakingDataExportTemplateInfoGateway(
            final DataExportTemplateInfoGateway delegate,
            final DataExportCircuitBreakerRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "O gateway de metadados é obrigatório.");
        this.registry = Objects.requireNonNull(registry, "O registro de circuitos é obrigatório.");
    }

    @Override
    public DataExportTemplateInfo fetchInfo(final DataExportTemplate template) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        final DataExportCircuitBreakerRegistry.Permit permit = registry.acquire(template);
        try {
            final DataExportTemplateInfo response = delegate.fetchInfo(template);
            registry.onSuccess(template, permit);
            return response;
        } catch (final DataExportUnavailableException exception) {
            registry.onUnavailableFailure(template, permit);
            throw exception;
        } catch (final RuntimeException exception) {
            registry.onReachableFailure(template, permit);
            throw exception;
        }
    }
}
