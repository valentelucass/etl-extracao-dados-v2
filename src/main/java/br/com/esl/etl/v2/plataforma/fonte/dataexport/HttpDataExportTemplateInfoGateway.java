package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Adaptador HTTP exclusivo do recurso de metadados {@code /info}. */
public final class HttpDataExportTemplateInfoGateway implements DataExportTemplateInfoGateway {

    private final DataExportProperties properties;
    private final DataExportHttpExecutor httpExecutor;
    private final DataExportTemplateInfoParser parser;

    public HttpDataExportTemplateInfoGateway(final DataExportProperties properties) {
        this(properties, DataExportHttpAttemptObserver.noop());
    }

    /** Cria a sonda de metadados com observação sanitizada antes de cada conexão HTTP. */
    public HttpDataExportTemplateInfoGateway(
            final DataExportProperties properties,
            final DataExportHttpAttemptObserver attemptObserver) {
        this(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build(),
                properties,
                new ObjectMapper(),
                DataExportSleeper.threadSleeper(),
                attemptObserver);
    }

    HttpDataExportTemplateInfoGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportSleeper sleeper) {
        this(httpClient, properties, objectMapper, sleeper, DataExportHttpAttemptObserver.noop());
    }

    HttpDataExportTemplateInfoGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportSleeper sleeper,
            final DataExportHttpAttemptObserver attemptObserver) {
        this(
                httpClient,
                properties,
                objectMapper,
                attemptObserver,
                DataExportHttpAttemptGovernor.ungoverned(
                        properties.requestTimeout(),
                        properties.retryPolicy().maxDelay(),
                        Objects.requireNonNull(sleeper, "Sleeper é obrigatório.")));
    }

    HttpDataExportTemplateInfoGateway(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final ObjectMapper objectMapper,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportHttpAttemptGovernor attemptGovernor) {
        this.properties =
                Objects.requireNonNull(properties, "As propriedades Data Export são obrigatórias.");
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
        this.parser = new DataExportTemplateInfoParser();
    }

    @Override
    public DataExportTemplateInfo fetchInfo(final DataExportTemplate template) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        final URI endpoint = properties.infoEndpointFor(template.templateId());
        final DataExportHttpResponse response =
                httpExecutor.executeWithRetry(
                        template.templateId(), "info", timeout -> buildRequest(endpoint, timeout));
        if (response.statusCode() == 204) {
            throw new IllegalStateException(
                    "Data Export retornou HTTP 204 sem metadados para o template "
                            + template.templateId()
                            + ".");
        }
        if (DataExportHttpExecutor.isSuccess(response.statusCode())) {
            return parseResponse(template, response);
        }
        if (DataExportHttpExecutor.isRetryableStatus(response.statusCode())) {
            throw new DataExportUnavailableException(
                    template.templateId(), response.statusCode(), response.retryAfter());
        }
        throw new IllegalStateException(
                "Data Export retornou HTTP "
                        + response.statusCode()
                        + " para os metadados do template "
                        + template.templateId()
                        + ".");
    }

    private HttpRequest buildRequest(final URI endpoint, final Duration timeout) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", properties.authorizationHeaderValue())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private DataExportTemplateInfo parseResponse(
            final DataExportTemplate template, final DataExportHttpResponse response) {
        try {
            final JsonNode document = DataExportStrictJsonParser.readTree(response.body());
            if (document == null || document.isMissingNode()) {
                throw new IllegalStateException(
                        "Resposta JSON vazia nos metadados do template "
                                + template.templateId()
                                + ".");
            }
            final DataExportTemplateInfoParser.ParsedTemplateInfo parsed = parser.parse(document);
            return new DataExportTemplateInfo(
                    template,
                    response.statusCode(),
                    response.retryAfter(),
                    response.jsonContentTypeDeclared(),
                    parsed.fields(),
                    parsed.filters());
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Resposta JSON inválida nos metadados do template "
                            + template.templateId()
                            + ".");
        }
    }
}
