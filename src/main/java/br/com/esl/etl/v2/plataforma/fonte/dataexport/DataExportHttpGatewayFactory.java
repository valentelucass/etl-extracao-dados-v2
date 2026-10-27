package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory source-scoped: reutiliza cliente/pool HTTP e circuitos, abrindo apenas o contexto de cada
 * workload.
 */
public final class DataExportHttpGatewayFactory {

    private final DataExportProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DataExportCircuitBreakerRegistry circuitRegistry;
    private final Clock observedClock;
    private final Optional<ImmutableFingerprint> runtimeConfigurationFingerprint;
    private final Optional<DataExportSourceConfiguration> materializedConfiguration;
    private final java.util.concurrent.atomic.AtomicBoolean observedSourceStopped =
            new java.util.concurrent.atomic.AtomicBoolean();

    public DataExportHttpGatewayFactory(
            final DataExportProperties properties,
            final EslResiliencePolicy resiliencePolicy,
            final Clock clock) {
        this(
                properties,
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build(),
                new ObjectMapper(),
                new DataExportCircuitBreakerRegistry(
                        DataExportCircuitBreakerPolicy.from(resiliencePolicy), clock),
                Optional.empty());
    }

    /** Caminho operacional: materializa o segredo e sela a configuração não secreta efetiva. */
    public DataExportHttpGatewayFactory(
            final DataExportSourceConfiguration sourceConfiguration,
            final SecretProvider secretProvider,
            final Clock clock) {
        this(materialize(sourceConfiguration, secretProvider), clock);
    }

    private DataExportHttpGatewayFactory(final MaterializedSource source, final Clock clock) {
        this(
                source.properties(),
                HttpClient.newBuilder()
                        .connectTimeout(source.properties().requestTimeout())
                        .build(),
                new ObjectMapper(),
                new DataExportCircuitBreakerRegistry(
                        DataExportCircuitBreakerPolicy.from(source.resiliencePolicy()), clock),
                Optional.of(source.runtimeConfigurationFingerprint()),
                Optional.of(source.configuration()),
                clock);
    }

    DataExportHttpGatewayFactory(
            final DataExportProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final DataExportCircuitBreakerRegistry circuitRegistry) {
        this(properties, httpClient, objectMapper, circuitRegistry, Optional.empty());
    }

    DataExportHttpGatewayFactory(
            final DataExportProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final DataExportCircuitBreakerRegistry circuitRegistry,
            final Optional<ImmutableFingerprint> runtimeConfigurationFingerprint) {
        this(
                properties,
                httpClient,
                objectMapper,
                circuitRegistry,
                runtimeConfigurationFingerprint,
                Optional.empty());
    }

    private DataExportHttpGatewayFactory(
            final DataExportProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final DataExportCircuitBreakerRegistry circuitRegistry,
            final Optional<ImmutableFingerprint> runtimeConfigurationFingerprint,
            final Optional<DataExportSourceConfiguration> materializedConfiguration) {
        this(
                properties,
                httpClient,
                objectMapper,
                circuitRegistry,
                runtimeConfigurationFingerprint,
                materializedConfiguration,
                Clock.systemUTC());
    }

    private DataExportHttpGatewayFactory(
            final DataExportProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final DataExportCircuitBreakerRegistry circuitRegistry,
            final Optional<ImmutableFingerprint> runtimeConfigurationFingerprint,
            final Optional<DataExportSourceConfiguration> materializedConfiguration,
            final Clock observedClock) {
        this.properties =
                Objects.requireNonNull(properties, "As propriedades Data Export são obrigatórias.");
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient é obrigatório.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper é obrigatório.");
        this.circuitRegistry =
                Objects.requireNonNull(circuitRegistry, "O registro de circuitos é obrigatório.");
        this.runtimeConfigurationFingerprint =
                Objects.requireNonNull(
                        runtimeConfigurationFingerprint,
                        "O fingerprint opcional da configuração é obrigatório.");
        this.materializedConfiguration = Objects.requireNonNull(materializedConfiguration);
        this.observedClock = Objects.requireNonNull(observedClock);
    }

    public DataExportHttpGatewayBundle forWorkload(
            final EslRequestGovernor.Cycle cycle, final EslWorkload workload) {
        return forWorkload(cycle, workload, DataExportHttpAttemptObserver.noop());
    }

    public DataExportHttpGatewayBundle forWorkload(
            final EslRequestGovernor.Cycle cycle,
            final EslWorkload workload,
            final DataExportHttpAttemptObserver attemptObserver) {
        return forWorkload(cycle, workload, attemptObserver, Optional.empty());
    }

    public DataExportHttpGatewayBundle forWorkload(
            final EslRequestGovernor.Cycle cycle,
            final EslWorkload workload,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportContractObservationConfiguration observationConfiguration) {
        return forWorkload(
                cycle,
                workload,
                attemptObserver,
                Optional.of(
                        Objects.requireNonNull(
                                observationConfiguration,
                                "A configuração de observação é obrigatória.")));
    }

    private DataExportHttpGatewayBundle forWorkload(
            final EslRequestGovernor.Cycle cycle,
            final EslWorkload workload,
            final DataExportHttpAttemptObserver attemptObserver,
            final Optional<DataExportContractObservationConfiguration> observationConfiguration) {
        Objects.requireNonNull(cycle, "O ciclo ESL é obrigatório.");
        Objects.requireNonNull(workload, "O workload ESL é obrigatório.");
        Objects.requireNonNull(attemptObserver, "O observador de tentativa é obrigatório.");
        observationConfiguration.ifPresent(this::verifyObservationConfiguration);
        final DataExportHttpAttemptGovernor governor =
                new EslDataExportHttpAttemptGovernor(cycle.beginWorkload(workload));
        final HttpDataExportGateway rawData =
                new HttpDataExportGateway(
                        httpClient,
                        properties,
                        objectMapper,
                        attemptObserver,
                        governor,
                        DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY,
                        observationConfiguration);
        final HttpDataExportTemplateInfoGateway rawInfo =
                new HttpDataExportTemplateInfoGateway(
                        httpClient, properties, objectMapper, attemptObserver, governor);
        if (observationConfiguration.isPresent()) {
            return DataExportHttpGatewayBundle.materialized(
                    rawData, rawInfo, circuitRegistry, observationConfiguration.orElseThrow());
        }
        return new DataExportHttpGatewayBundle(
                new CircuitBreakingDataExportGateway(rawData, circuitRegistry),
                new CircuitBreakingDataExportTemplateInfoGateway(rawInfo, circuitRegistry));
    }

    /** Offline-prepared package-only transport primitive. Does not admit a P3 runtime or schema. */
    ObservedExpansionHttpTransport prepareObservedTransport(
            final ObservedExpansionCapturePlan plan,
            final EslRequestGovernor.Cycle cycle,
            final EslRequestGovernor expectedGovernor,
            final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken cancellation,
            final DataExportHttpAttemptObserver observer) {
        ObservedExpansionHttpTransport.requireSafeBearer(properties);
        final DataExportSourceConfiguration source =
                materializedConfiguration.orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "OBS_CAPTURE_MATERIALIZED_SOURCE_REQUIRED"));
        Objects.requireNonNull(plan);
        if (observedSourceStopped.get()) {
            throw new IllegalStateException("OBS_CAPTURE_SOURCE_STOPPED");
        }
        if (!source.sourceInstance().equals(plan.sourceInstance())
                || !source.tenantScope().equals(plan.tenantScope())
                || !properties.sourceZone().equals(plan.sourceZone())
                || !runtimeConfigurationFingerprint
                        .orElseThrow()
                        .equals(DataExportRuntimeConfigurationFingerprint.from(source))
                || properties.preferredTransport() != DataExportTransport.GET_WITH_BODY
                || properties.retryPolicy().maxAttempts() != 1
                || properties.requestTimeout().compareTo(java.time.Duration.ofSeconds(30)) > 0
                || !"https".equalsIgnoreCase(properties.baseUri().getScheme())
                || httpClient.followRedirects() != HttpClient.Redirect.NEVER
                || source.resiliencePolicy()
                                .minimumRequestInterval()
                                .compareTo(java.time.Duration.ofSeconds(3))
                        < 0) {
            throw new IllegalArgumentException("OBS_CAPTURE_MATERIALIZED_POLICY");
        }
        Objects.requireNonNull(cycle).verifyPolicy(source.resiliencePolicy());
        cycle.verifyGovernor(expectedGovernor);
        cycle.verifyCancellationToken(cancellation);
        final DataExportTemplate template =
                switch (plan.template()) {
                    case CAP -> DataExportTemplate.CONTAS_A_PAGAR;
                    case FAT -> DataExportTemplate.FATURAS_POR_CLIENTE;
                    case INV -> DataExportTemplate.INVENTARIO;
                    case SIN -> DataExportTemplate.SINISTROS;
                };
        final EslWorkload workload =
                switch (plan.template()) {
                    case CAP -> EslWorkload.CONTAS_A_PAGAR;
                    case FAT -> EslWorkload.FATURAS_POR_CLIENTE;
                    case INV -> EslWorkload.INVENTARIO;
                    case SIN -> EslWorkload.SINISTROS;
                };
        final var governor = new EslDataExportHttpAttemptGovernor(cycle.beginWorkload(workload));
        final var executor =
                new DataExportHttpExecutor(
                        httpClient,
                        properties,
                        Objects.requireNonNull(observer),
                        governor,
                        new DataExportRetrySchedule(
                                properties.retryPolicy(),
                                observedClock,
                                DataExportJitterSource.threadLocal(),
                                governor.maximumRetryAfter()));
        return new ObservedExpansionHttpTransport(
                plan, template, properties, executor, circuitRegistry, observedSourceStopped);
    }

    /** Same materialization as the public source constructor, with an injected offline client. */
    DataExportHttpGatewayFactory(
            final DataExportSourceConfiguration source,
            final SecretProvider secrets,
            final HttpClient client,
            final Clock clock) {
        this(materializeObserved(source, secrets), client, clock);
    }

    private DataExportHttpGatewayFactory(
            final MaterializedSource source, final HttpClient client, final Clock clock) {
        this(
                source.properties(),
                client,
                new ObjectMapper(),
                new DataExportCircuitBreakerRegistry(
                        DataExportCircuitBreakerPolicy.from(source.resiliencePolicy()), clock),
                Optional.of(source.runtimeConfigurationFingerprint()),
                Optional.of(source.configuration()),
                clock);
    }

    /**
     * Same normal INFO parser/circuit, with an early P3-only irreversible source-status observer.
     */
    DataExportTemplateInfoGateway prepareObservedInfoGateway(
            final ObservedExpansionCapturePlan plan,
            final EslRequestGovernor.Cycle cycle,
            final EslRequestGovernor governor,
            final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken cancellation,
            final DataExportHttpAttemptObserver observer) {
        prepareObservedTransport(plan, cycle, governor, cancellation, observer);
        final EslWorkload workload =
                switch (plan.template()) {
                    case CAP -> EslWorkload.CONTAS_A_PAGAR;
                    case FAT -> EslWorkload.FATURAS_POR_CLIENTE;
                    case INV -> EslWorkload.INVENTARIO;
                    case SIN -> EslWorkload.SINISTROS;
                };
        final var attemptGovernor =
                new EslDataExportHttpAttemptGovernor(cycle.beginWorkload(workload));
        final var rawInfo =
                new HttpDataExportTemplateInfoGateway(
                        httpClient,
                        properties,
                        objectMapper,
                        observer,
                        attemptGovernor,
                        new ObservedExpansionHttpTransport.RawCapture(
                                plan.request(1), observedSourceStopped));
        return new CircuitBreakingDataExportTemplateInfoGateway(rawInfo, circuitRegistry);
    }

    void verifyObservedRuntimeConfiguration(final DataExportSourceConfiguration expected) {
        if (materializedConfiguration.isEmpty()
                || runtimeConfigurationFingerprint.isEmpty()
                || !runtimeConfigurationFingerprint
                        .orElseThrow()
                        .equals(
                                DataExportRuntimeConfigurationFingerprint.from(
                                        Objects.requireNonNull(expected)))) {
            throw new IllegalArgumentException("OBS_DRIVER_MATERIALIZED_RUNTIME_MISMATCH");
        }
    }

    boolean observedSourceStopped() {
        return observedSourceStopped.get();
    }

    /** P3-only source creation. Existing public legacy materialization is unchanged. */
    static DataExportHttpGatewayFactory prepareObservedSource(
            final DataExportSourceConfiguration source,
            final SecretProvider secrets,
            final Clock clock) {
        try {
            return new DataExportHttpGatewayFactory(materializeObserved(source, secrets), clock);
        } catch (final RuntimeException failure) {
            throw new IllegalArgumentException("OBS_CAPTURE_SOURCE_PREPARATION_REJECTED");
        }
    }

    private static MaterializedSource materializeObserved(
            final DataExportSourceConfiguration source, final SecretProvider secrets) {
        try {
            final MaterializedSource actual = materialize(source, secrets);
            ObservedExpansionHttpTransport.requireSafeBearer(actual.properties());
            return actual;
        } catch (final RuntimeException failure) {
            throw new IllegalArgumentException("OBS_CAPTURE_SOURCE_PREPARATION_REJECTED");
        }
    }

    private void verifyObservationConfiguration(
            final DataExportContractObservationConfiguration observationConfiguration) {
        if (runtimeConfigurationFingerprint.isEmpty()
                || !runtimeConfigurationFingerprint
                        .orElseThrow()
                        .equals(observationConfiguration.runtimeConfigurationFingerprint())) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
    }

    private static MaterializedSource materialize(
            final DataExportSourceConfiguration sourceConfiguration,
            final SecretProvider secretProvider) {
        final DataExportSourceConfiguration required =
                Objects.requireNonNull(
                        sourceConfiguration, "A configuração Data Export é obrigatória.");
        return new MaterializedSource(
                required.settings()
                        .materialize(
                                Objects.requireNonNull(
                                        secretProvider, "O provider de segredo é obrigatório.")),
                required.resiliencePolicy(),
                DataExportRuntimeConfigurationFingerprint.from(required),
                required);
    }

    private record MaterializedSource(
            DataExportProperties properties,
            EslResiliencePolicy resiliencePolicy,
            ImmutableFingerprint runtimeConfigurationFingerprint,
            DataExportSourceConfiguration configuration) {}
}
