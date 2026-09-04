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
    private final Optional<ImmutableFingerprint> runtimeConfigurationFingerprint;

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
                Optional.of(source.runtimeConfigurationFingerprint()));
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
        final DataExportGateway dataGateway =
                new CircuitBreakingDataExportGateway(rawData, circuitRegistry);
        final DataExportTemplateInfoGateway infoGateway =
                new CircuitBreakingDataExportTemplateInfoGateway(rawInfo, circuitRegistry);
        return observationConfiguration
                .map(
                        configuration ->
                                DataExportHttpGatewayBundle.contractBound(
                                        dataGateway, infoGateway, configuration))
                .orElseGet(() -> new DataExportHttpGatewayBundle(dataGateway, infoGateway));
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
                DataExportRuntimeConfigurationFingerprint.from(required));
    }

    private record MaterializedSource(
            DataExportProperties properties,
            EslResiliencePolicy resiliencePolicy,
            ImmutableFingerprint runtimeConfigurationFingerprint) {}
}
