package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlProperties;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;

/** Factory source-scoped; nunca cria governor paralelo nem caminho produtivo sem contrato. */
public final class GraphQlHttpGatewayFactory {

    private final GraphQlProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GraphQlCircuitBreakerRegistry circuitRegistry;
    private final ImmutableFingerprint runtimeConfigurationFingerprint;
    private final EslResiliencePolicy resiliencePolicy;
    private final EslRequestGovernor governor;
    private final Clock clock;
    private final GraphQlJitterSource jitterSource;

    public GraphQlHttpGatewayFactory(
            final GraphQlSourceConfiguration sourceConfiguration,
            final SecretProvider secretProvider,
            final EslRequestGovernor governor,
            final Clock clock) {
        this(
                materialize(sourceConfiguration, secretProvider, governor),
                Objects.requireNonNull(clock, "O relógio é obrigatório."));
    }

    private GraphQlHttpGatewayFactory(final MaterializedSource source, final Clock clock) {
        this(
                source.properties(),
                HttpClient.newBuilder()
                        .connectTimeout(source.properties().requestTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper(),
                new GraphQlCircuitBreakerRegistry(
                        GraphQlCircuitBreakerPolicy.from(source.configuration().resiliencePolicy()),
                        clock),
                source.fingerprint(),
                source.configuration().resiliencePolicy(),
                source.governor(),
                clock,
                GraphQlJitterSource.threadLocal());
    }

    GraphQlHttpGatewayFactory(
            final GraphQlProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final GraphQlCircuitBreakerRegistry circuitRegistry,
            final ImmutableFingerprint runtimeConfigurationFingerprint,
            final EslResiliencePolicy resiliencePolicy,
            final EslRequestGovernor governor,
            final Clock clock,
            final GraphQlJitterSource jitterSource) {
        this.properties = Objects.requireNonNull(properties, "As propriedades são obrigatórias.");
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient é obrigatório.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper é obrigatório.");
        this.circuitRegistry = Objects.requireNonNull(circuitRegistry, "O circuito é obrigatório.");
        this.runtimeConfigurationFingerprint =
                Objects.requireNonNull(
                        runtimeConfigurationFingerprint, "O fingerprint runtime é obrigatório.");
        this.resiliencePolicy =
                Objects.requireNonNull(resiliencePolicy, "A política ESL é obrigatória.");
        this.governor = Objects.requireNonNull(governor, "O governor ESL é obrigatório.");
        this.governor.verifyPolicy(this.resiliencePolicy);
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
        this.jitterSource = Objects.requireNonNull(jitterSource, "O jitter é obrigatório.");
    }

    public GraphQlGateway forOperation(
            final EslRequestGovernor.Cycle cycle,
            final GraphQlReadOperation operation,
            final GraphQlContractObservationConfiguration observationConfiguration,
            final ContractRunGuard contractRunGuard,
            final CancellationToken cancellationToken) {
        final EslRequestGovernor.Cycle requiredCycle =
                Objects.requireNonNull(cycle, "O ciclo ESL é obrigatório.");
        final CancellationToken requiredCancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento GraphQL é obrigatório.");
        requiredCycle.verifyCancellationToken(requiredCancellation);
        requiredCycle.verifyPolicy(resiliencePolicy);
        requiredCycle.verifyGovernor(governor);
        final GraphQlContractObservationConfiguration requiredObservation =
                Objects.requireNonNull(
                        observationConfiguration, "A observação GraphQL é obrigatória.");
        return GraphQlContractGate.enforce(
                bindCancellation(
                        observedForOperation(requiredCycle, operation, requiredObservation),
                        requiredCancellation),
                requiredObservation,
                Objects.requireNonNull(contractRunGuard, "O gate de contrato é obrigatório."),
                requiredCancellation);
    }

    GraphQlGateway observedForOperation(
            final EslRequestGovernor.Cycle cycle,
            final GraphQlReadOperation operation,
            final GraphQlContractObservationConfiguration observationConfiguration) {
        final EslRequestGovernor.Cycle requiredCycle =
                Objects.requireNonNull(cycle, "O ciclo ESL é obrigatório.");
        final GraphQlReadOperation requiredOperation =
                Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        final GraphQlContractObservationConfiguration requiredObservation =
                Objects.requireNonNull(
                        observationConfiguration, "A observação GraphQL é obrigatória.");
        if (requiredObservation.operation() != requiredOperation
                || !requiredObservation
                        .runtimeConfigurationFingerprint()
                        .equals(runtimeConfigurationFingerprint)) {
            throw new IllegalArgumentException(
                    "A configuração de observação não corresponde ao runtime GraphQL.");
        }
        GraphQlTransitionalFieldCatalog.validate();
        final HttpGraphQlGateway gateway =
                new HttpGraphQlGateway(
                        requiredOperation,
                        properties,
                        httpClient,
                        objectMapper,
                        requiredCycle.beginWorkload(requiredOperation.workload()),
                        clock,
                        jitterSource,
                        requiredObservation);
        return new CircuitBreakingGraphQlGateway(gateway, circuitRegistry);
    }

    public ImmutableFingerprint runtimeConfigurationFingerprint() {
        return runtimeConfigurationFingerprint;
    }

    private static GraphQlGateway bindCancellation(
            final GraphQlGateway delegate, final CancellationToken cancellationToken) {
        return new GraphQlGateway() {
            @Override
            public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
                return delegate.fetch(request);
            }

            @Override
            public void verifyCancellationToken(final CancellationToken candidate) {
                if (cancellationToken
                        != Objects.requireNonNull(
                                candidate, "O cancelamento GraphQL é obrigatório.")) {
                    throw new IllegalArgumentException(
                            "A capability de cancelamento não corresponde ao gateway GraphQL.");
                }
            }
        };
    }

    private static MaterializedSource materialize(
            final GraphQlSourceConfiguration configuration,
            final SecretProvider secretProvider,
            final EslRequestGovernor governor) {
        final GraphQlSourceConfiguration required =
                Objects.requireNonNull(configuration, "A configuração GraphQL é obrigatória.");
        final EslRequestGovernor requiredGovernor =
                Objects.requireNonNull(governor, "O governor ESL é obrigatório.");
        requiredGovernor.verifyPolicy(required.resiliencePolicy());
        return new MaterializedSource(
                required,
                required.settings()
                        .materialize(
                                Objects.requireNonNull(
                                        secretProvider, "O provider de segredo é obrigatório.")),
                GraphQlRuntimeConfigurationFingerprint.from(required),
                requiredGovernor);
    }

    private record MaterializedSource(
            GraphQlSourceConfiguration configuration,
            GraphQlProperties properties,
            ImmutableFingerprint fingerprint,
            EslRequestGovernor governor) {}
}
