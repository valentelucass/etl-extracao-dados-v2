package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.observabilidade.BoundedStructuredLogger;
import br.com.esl.etl.v2.plataforma.observabilidade.LogSeverity;
import br.com.esl.etl.v2.plataforma.observabilidade.Slf4jStructuredLogSink;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredCorrelationContext;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredLogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adaptador HTTP com transporte fixo e retry limitado para falhas transitórias. */
public final class HttpDataExportGateway implements DataExportGateway {

    private static final BoundedStructuredLogger STRUCTURED_LOG =
            new BoundedStructuredLogger(
                    new Slf4jStructuredLogSink(HttpDataExportGateway.class),
                    1_000_000,
                    268_435_456);

    private final DataExportProperties properties;
    private final DataExportRequestJsonSerializer jsonSerializer;
    private final DataExportQueryEncoder queryEncoder;
    private final DataExportResponseNormalizer responseNormalizer;
    private final DataExportHttpExecutor httpExecutor;
    private final DataExportTransportFallbackPolicy fallbackPolicy;
    private final Optional<DataExportContractObservationConfiguration>
            contractObservationConfiguration;

    public HttpDataExportGateway(final DataExportProperties properties) {
        this(properties, DataExportHttpAttemptObserver.noop());
    }

    /** Cria o gateway com observação sanitizada de tentativa, antes de cada conexão HTTP. */
    public HttpDataExportGateway(
            final DataExportProperties properties,
            final DataExportHttpAttemptObserver attemptObserver) {
        this(
                properties,
                attemptObserver,
                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY);
    }

    /** Habilita observação estrutural somente com chave e limites explicitamente aprovados. */
    public HttpDataExportGateway(
            final DataExportProperties properties,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportContractObservationConfiguration observationConfiguration) {
        this(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build(),
                properties,
                new ObjectMapper(),
                attemptObserver,
                DataExportHttpAttemptGovernor.ungoverned(
                        properties.requestTimeout(),
                        properties.retryPolicy().maxDelay(),
                        DataExportSleeper.threadSleeper()),
                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY,
                Optional.of(
                        Objects.requireNonNull(
                                observationConfiguration,
                                "A configuração de observação é obrigatória.")));
    }

    /** Política explícita restrita ao pacote para testes/transição controlada. */
    HttpDataExportGateway(
            final DataExportProperties properties,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportTransportFallbackPolicy fallbackPolicy) {
        this(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build(),
                properties,
                new ObjectMapper(),
                DataExportSleeper.threadSleeper(),
                attemptObserver,
                fallbackPolicy);
    }

    HttpDataExportGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportSleeper sleeper) {
        this(
                httpClient,
                properties,
                objectMapper,
                sleeper,
                DataExportHttpAttemptObserver.noop(),
                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY);
    }

    HttpDataExportGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportSleeper sleeper,
            final DataExportHttpAttemptObserver attemptObserver) {
        this(
                httpClient,
                properties,
                objectMapper,
                sleeper,
                attemptObserver,
                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY);
    }

    HttpDataExportGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportSleeper sleeper,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportTransportFallbackPolicy fallbackPolicy) {
        this(
                httpClient,
                properties,
                objectMapper,
                attemptObserver,
                DataExportHttpAttemptGovernor.ungoverned(
                        properties.requestTimeout(),
                        properties.retryPolicy().maxDelay(),
                        Objects.requireNonNull(sleeper, "Sleeper é obrigatório.")),
                fallbackPolicy);
    }

    HttpDataExportGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportHttpAttemptGovernor attemptGovernor,
            final DataExportTransportFallbackPolicy fallbackPolicy) {
        this(
                httpClient,
                properties,
                objectMapper,
                attemptObserver,
                attemptGovernor,
                fallbackPolicy,
                Optional.empty());
    }

    HttpDataExportGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportHttpAttemptGovernor attemptGovernor,
            final DataExportTransportFallbackPolicy fallbackPolicy,
            final Optional<DataExportContractObservationConfiguration>
                    contractObservationConfiguration) {
        this.properties =
                Objects.requireNonNull(properties, "As propriedades Data Export são obrigatórias.");
        final ObjectMapper requiredMapper =
                Objects.requireNonNull(objectMapper, "ObjectMapper é obrigatório.");
        this.httpExecutor =
                new DataExportHttpExecutor(
                        Objects.requireNonNull(httpClient, "HttpClient é obrigatório."),
                        properties,
                        Objects.requireNonNull(
                                attemptObserver, "O observador de tentativa é obrigatório."),
                        Objects.requireNonNull(
                                attemptGovernor, "O governor de tentativa é obrigatório."),
                        new DataExportRetrySchedule(
                                properties.retryPolicy(),
                                Clock.systemUTC(),
                                DataExportJitterSource.threadLocal(),
                                attemptGovernor.maximumRetryAfter()));
        this.fallbackPolicy =
                Objects.requireNonNull(fallbackPolicy, "A política de fallback é obrigatória.");
        this.contractObservationConfiguration =
                Objects.requireNonNull(
                        contractObservationConfiguration,
                        "A configuração opcional de observação é obrigatória.");
        this.jsonSerializer = new DataExportRequestJsonSerializer(requiredMapper);
        this.queryEncoder = new DataExportQueryEncoder();
        this.responseNormalizer = new DataExportResponseNormalizer();
    }

    @Override
    public DataExportPageResponse fetch(final DataExportPageRequest request) {
        return fetchWithDiagnostics(request).response();
    }

    /**
     * Lê uma página e expõe apenas o transporte e cabeçalho observáveis, úteis para teste de
     * contrato.
     *
     * <p>Não registra corpo, URL, token ou filtros.
     */
    public DataExportPageFetch fetchWithDiagnostics(final DataExportPageRequest request) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        if (request.template().syntheticOccurrenceCapture()) {
            throw new IllegalArgumentException("EXP_SYNTHETIC_TRANSPORT_ONLY");
        }
        if (contractObservationConfiguration.isPresent()
                && contractObservationConfiguration.orElseThrow().template()
                        != request.template()) {
            throw new IllegalStateException(
                    "A configuração de contrato não corresponde ao template Data Export.");
        }
        final URI endpoint = properties.endpointFor(request.template().templateId());
        final String jsonBody = jsonSerializer.serializeToString(request, properties.sourceZone());
        Integer latestStatus = null;

        for (final DataExportTransport transport : transportOrder()) {
            final DataExportHttpResponse response =
                    executeWithRetry(endpoint, request, jsonBody, transport);
            latestStatus = response.statusCode();
            if (response.statusCode() == 204) {
                throw new IllegalStateException(
                        "Data Export retornou HTTP 204 sem uma página JSON para o template "
                                + request.template().templateId()
                                + ".");
            }
            if (DataExportHttpExecutor.isSuccess(response.statusCode())) {
                final ParsedPageResponse parsed =
                        parseResponse(response.body(), request.template());
                DataExportPageEntityLimitValidator.validate(request, parsed.page());
                return new DataExportPageFetch(
                        parsed.page(),
                        transport,
                        response.statusCode(),
                        response.retryAfter(),
                        parsed.responseForm(),
                        response.jsonContentTypeDeclared());
            }
            if (isFallbackStatus(response.statusCode())
                    && fallbackPolicy
                            == DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK) {
                STRUCTURED_LOG.write(
                        new StructuredLogEvent(
                                LogSeverity.WARN,
                                "HTTP_TRANSPORT_FALLBACK",
                                "DATA_EXPORT_GATEWAY",
                                "FALLBACK",
                                StructuredCorrelationContext.currentOrTechnicalScope(
                                        "DATA_EXPORT_GATEWAY"),
                                1,
                                httpExecutor.observedAt()));
                continue;
            }
            if (DataExportHttpExecutor.isRetryableStatus(response.statusCode())) {
                throw new DataExportUnavailableException(
                        request.template().templateId(),
                        response.statusCode(),
                        response.retryAfter());
            }
            throw new IllegalStateException(
                    "Data Export retornou HTTP "
                            + response.statusCode()
                            + " para o template "
                            + request.template().templateId()
                            + ".");
        }
        throw new IllegalStateException(
                "Nenhum transporte Data Export foi aceito para o template "
                        + request.template().templateId()
                        + ". Último HTTP: "
                        + latestStatus
                        + ".");
    }

    private HttpRequest buildRequest(
            final URI endpoint,
            final DataExportPageRequest request,
            final String jsonBody,
            final DataExportTransport transport,
            final Duration timeout) {
        final URI target =
                transport == DataExportTransport.GET_WITH_QUERY
                        ? queryEncoder.appendQuery(endpoint, request, properties.sourceZone())
                        : endpoint;
        final HttpRequest.Builder builder =
                HttpRequest.newBuilder(target)
                        .timeout(timeout)
                        .header("Authorization", properties.authorizationHeaderValue())
                        .header("Accept", "application/json");

        if (transport != DataExportTransport.GET_WITH_QUERY) {
            builder.header("Content-Type", "application/json");
        }

        return switch (transport) {
            case GET_WITH_BODY ->
                    builder.method(
                                    "GET",
                                    HttpRequest.BodyPublishers.ofString(
                                            jsonBody, StandardCharsets.UTF_8))
                            .build();
            case GET_WITH_QUERY -> builder.GET().build();
            case POST_JSON ->
                    builder.POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            jsonBody, StandardCharsets.UTF_8))
                            .build();
        };
    }

    private ParsedPageResponse parseResponse(
            final String responseBody, final DataExportTemplate template) {
        try {
            final int maximumNodes =
                    contractObservationConfiguration
                            .map(configuration -> configuration.observationLimits().maximumNodes())
                            .orElse(ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES);
            final JsonNode response =
                    DataExportStrictJsonParser.readTree(responseBody, maximumNodes);
            if (response == null || response.isMissingNode()) {
                throw new IllegalStateException(
                        "Resposta JSON vazia no template Data Export "
                                + template.templateId()
                                + ".");
            }
            final DataExportResponseForm responseForm = DataExportResponseForm.from(response);
            if (contractObservationConfiguration.isPresent()
                    && responseForm
                            != contractObservationConfiguration
                                    .orElseThrow()
                                    .expectedResponseForm()) {
                throw new ContractDriftException(
                        ContractDriftException.Reason.BREAKING_CHANGE, 1, 0);
            }
            final ContractResponse observation;
            final DataExportContractAdapter adapter;
            if (contractObservationConfiguration.isPresent()) {
                final DataExportContractObservationConfiguration configuration =
                        contractObservationConfiguration.orElseThrow();
                if (configuration.template() != template) {
                    throw new IllegalStateException(
                            "A configuração de contrato não corresponde ao template Data Export.");
                }
                adapter =
                        new DataExportContractAdapter(
                                configuration.observationLimits(),
                                configuration.responsePathBoundary());
                observation =
                        adapter.response(response, responseForm, configuration.approvedKeyPath());
            } else {
                adapter = null;
                observation = null;
            }
            final DataExportPageResponse normalized = responseNormalizer.normalize(response);
            final DataExportPageResponse page;
            if (contractObservationConfiguration.isPresent()) {
                final DataExportContractObservationConfiguration configuration =
                        contractObservationConfiguration.orElseThrow();
                page =
                        normalized.withObservation(
                                observation,
                                adapter.limits(),
                                configuration.responsePathBoundary());
            } else {
                page = new DataExportPageResponse(normalized.records());
            }
            return new ParsedPageResponse(page, responseForm);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Resposta JSON inválida no template Data Export "
                            + template.templateId()
                            + ".");
        }
    }

    private List<DataExportTransport> transportOrder() {
        if (fallbackPolicy == DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY) {
            return List.of(properties.preferredTransport());
        }
        return switch (properties.preferredTransport()) {
            case GET_WITH_BODY ->
                    List.of(
                            DataExportTransport.GET_WITH_BODY,
                            DataExportTransport.GET_WITH_QUERY,
                            DataExportTransport.POST_JSON);
            case GET_WITH_QUERY ->
                    List.of(
                            DataExportTransport.GET_WITH_QUERY,
                            DataExportTransport.GET_WITH_BODY,
                            DataExportTransport.POST_JSON);
            case POST_JSON ->
                    List.of(
                            DataExportTransport.POST_JSON,
                            DataExportTransport.GET_WITH_BODY,
                            DataExportTransport.GET_WITH_QUERY);
        };
    }

    private DataExportHttpResponse executeWithRetry(
            final URI endpoint,
            final DataExportPageRequest request,
            final String jsonBody,
            final DataExportTransport transport) {
        return httpExecutor.executeWithRetry(
                request.template().templateId(),
                "data-" + transport.name(),
                timeout -> buildRequest(endpoint, request, jsonBody, transport, timeout));
    }

    private boolean isFallbackStatus(final int statusCode) {
        return statusCode == 405 || statusCode == 411 || statusCode == 415 || statusCode == 501;
    }

    private record ParsedPageResponse(
            DataExportPageResponse page, DataExportResponseForm responseForm) {}
}
