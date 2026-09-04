package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseNormalizer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Sonda HTTP auxiliar, test-only, para 4924.
 *
 * <p>Ela usa exatamente um transporte aprovado, sem retry nem fallback, compartilha o stop-token da
 * execução e não expõe endpoint, token, corpo ou causa remota em erros.
 */
public final class Contract4924DataExportProbe {

    private final HttpClient httpClient;
    private final DataExportProperties properties;
    private final Contract4924Configuration configuration;
    private final ContractRunGuard runGuard;
    private final ContractRemoteCallBudget callBudget;
    private final ObjectMapper objectMapper;
    private final DataExportTemplateInfoParser infoParser;
    private final DataExportResponseNormalizer responseNormalizer;
    private boolean infoObserved;

    public Contract4924DataExportProbe(
            final DataExportProperties properties,
            final Contract4924Configuration configuration,
            final ContractRunGuard runGuard) {
        this(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build(),
                properties,
                configuration,
                runGuard,
                new ObjectMapper());
    }

    Contract4924DataExportProbe(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final Contract4924Configuration configuration,
            final ContractRunGuard runGuard,
            final ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "O cliente HTTP é obrigatório.");
        this.properties =
                Objects.requireNonNull(properties, "As propriedades Data Export são obrigatórias.");
        this.configuration =
                Objects.requireNonNull(
                        configuration, "A configuração auxiliar 4924 é obrigatória.");
        this.runGuard = Objects.requireNonNull(runGuard, "O guard da execução é obrigatório.");
        this.callBudget = new ContractRemoteCallBudget(configuration.maximumCalls());
        this.objectMapper = Objects.requireNonNull(objectMapper, "O ObjectMapper é obrigatório.");
        this.infoParser = new DataExportTemplateInfoParser();
        this.responseNormalizer = new DataExportResponseNormalizer();
    }

    public synchronized Contract4924TemplateInfo fetchInfo() {
        ContractTestGate.requireEnabled();
        if (infoObserved) {
            throw new ContractRemoteCallException(
                    "A sonda auxiliar permite somente um /info por execução.");
        }
        infoObserved = true;
        final RawResponse response =
                send(properties.infoEndpointFor(Contract4924Configuration.TEMPLATE_ID), null);
        final DataExportTemplateInfoParser.ParsedTemplateInfo parsed = parseInfo(response.body());
        return new Contract4924TemplateInfo(
                response.statusCode(),
                response.retryAfter().isPresent(),
                response.jsonContentTypeDeclared(),
                parsed.fields().stream().map(ContractMetadataEvidence::from).toList(),
                parsed.filters().stream().map(ContractMetadataEvidence::from).toList());
    }

    public Contract4924PageObservation fetchClosedWindowSample() {
        ContractTestGate.requireEnabled();
        final String payload = serializeClosedWindowRequest();
        final RawResponse response =
                send(properties.endpointFor(Contract4924Configuration.TEMPLATE_ID), payload);
        final JsonNode document =
                parseJson(response.body(), "A página auxiliar retornou JSON inválido.");
        final DataExportPageResponse page;
        final DataExportResponseForm responseForm;
        try {
            page = responseNormalizer.normalize(document);
            responseForm = DataExportResponseForm.from(document);
        } catch (final RuntimeException exception) {
            throw new ContractRemoteCallException("A página auxiliar retornou envelope inválido.");
        }
        return new Contract4924PageObservation(
                response.statusCode(),
                configuration.approvedTransport(),
                response.retryAfter().isPresent(),
                response.jsonContentTypeDeclared(),
                responseForm,
                page.records());
    }

    public int callsMade() {
        return callBudget.callsMade();
    }

    private RawResponse send(final URI endpoint, final String payload) {
        ContractTestGate.requireEnabled();
        runGuard.reserveCall(callBudget);
        try {
            final HttpResponse<InputStream> response =
                    httpClient.send(
                            buildRequest(endpoint, payload),
                            HttpResponse.BodyHandlers.ofInputStream());
            final Optional<String> retryAfter = response.headers().firstValue("Retry-After");
            if (response.statusCode() == 429) {
                runGuard.stopForRateLimit();
                closeQuietly(response.body());
                throw new ContractRateLimitExceededException(retryAfter);
            }
            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || response.statusCode() == 204) {
                closeQuietly(response.body());
                throw new ContractRemoteCallException(
                        "A sonda auxiliar recebeu HTTP não elegível.");
            }
            return new RawResponse(
                    response.statusCode(),
                    readBody(response),
                    retryAfter,
                    declaresJsonContentType(response));
        } catch (final ContractRunStoppedException
                | ContractRemoteCallLimitExceededException
                | ContractRateLimitExceededException
                | ContractRemoteCallException exception) {
            throw exception;
        } catch (final IOException exception) {
            throw new ContractRemoteCallException("A sonda auxiliar ficou indisponível.");
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ContractRemoteCallException("A sonda auxiliar foi interrompida.");
        }
    }

    private HttpRequest buildRequest(final URI endpoint, final String payload) {
        final DataExportTransport transport = configuration.approvedTransport();
        final URI target =
                transport == DataExportTransport.GET_WITH_QUERY ? appendQuery(endpoint) : endpoint;
        final HttpRequest.Builder builder =
                HttpRequest.newBuilder(target)
                        .timeout(properties.requestTimeout())
                        .header("Authorization", properties.authorizationHeaderValue())
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json");
        if (payload == null) {
            return builder.GET().build();
        }
        return switch (transport) {
            case GET_WITH_BODY ->
                    builder.method(
                                    "GET",
                                    HttpRequest.BodyPublishers.ofString(
                                            payload, StandardCharsets.UTF_8))
                            .build();
            case GET_WITH_QUERY -> builder.GET().build();
            case POST_JSON ->
                    builder.POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            payload, StandardCharsets.UTF_8))
                            .build();
        };
    }

    private String serializeClosedWindowRequest() {
        final ObjectNode request = objectMapper.createObjectNode();
        final ObjectNode search = request.putObject("search");
        search.putObject(configuration.root())
                .put(
                        configuration.businessFilter(),
                        configuration.closedWindow().asGraphQlInterval());
        request.put("page", "1");
        request.put("per", "1");
        request.put("order_by", configuration.orderBy());
        try {
            return objectMapper.writeValueAsString(request);
        } catch (final JsonProcessingException exception) {
            throw new ContractRemoteCallException("Não foi possível montar a sonda auxiliar.");
        }
    }

    private String readBody(final HttpResponse<InputStream> response) throws IOException {
        final long contentLength =
                response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > properties.maxResponseBytes()) {
            closeQuietly(response.body());
            throw new ContractRemoteCallException("A resposta auxiliar excedeu o teto aprovado.");
        }
        try (InputStream body = response.body();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            long received = 0L;
            int read;
            while ((read = body.read(buffer)) != -1) {
                received += read;
                if (received > properties.maxResponseBytes()) {
                    throw new ContractRemoteCallException(
                            "A resposta auxiliar excedeu o teto aprovado.");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private DataExportTemplateInfoParser.ParsedTemplateInfo parseInfo(final String body) {
        final JsonNode document =
                parseJson(body, "Os metadados auxiliares retornaram JSON inválido.");
        try {
            return infoParser.parse(document);
        } catch (final RuntimeException exception) {
            throw new ContractRemoteCallException(
                    "Os metadados auxiliares retornaram formato inválido.");
        }
    }

    private JsonNode parseJson(final String body, final String message) {
        try {
            final JsonNode document = objectMapper.readTree(body);
            if (document == null || document.isMissingNode()) {
                throw new ContractRemoteCallException(message);
            }
            return document;
        } catch (final JsonProcessingException exception) {
            throw new ContractRemoteCallException(message);
        }
    }

    private URI appendQuery(final URI endpoint) {
        final String separator = endpoint.getRawQuery() == null ? "?" : "&";
        try {
            return URI.create(
                    endpoint
                            + separator
                            + encode(
                                    "search["
                                            + configuration.root()
                                            + "]["
                                            + configuration.businessFilter()
                                            + "]")
                            + "="
                            + encode(configuration.closedWindow().asGraphQlInterval())
                            + "&page=1&per=1&order_by="
                            + encode(configuration.orderBy()));
        } catch (final IllegalArgumentException exception) {
            throw new ContractRemoteCallException(
                    "Não foi possível compor a query da sonda auxiliar.");
        }
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean declaresJsonContentType(final HttpResponse<InputStream> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
    }

    private void closeQuietly(final InputStream body) {
        try {
            body.close();
        } catch (final IOException exception) {
            // A resposta já será descartada e a falha de fechamento não deve vazar detalhes
            // remotos.
        }
    }

    private record RawResponse(
            int statusCode,
            String body,
            Optional<String> retryAfter,
            boolean jsonContentTypeDeclared) {

        private RawResponse {
            retryAfter = Objects.requireNonNull(retryAfter, "Retry-After é obrigatório.");
        }

        @Override
        public String toString() {
            return "RawResponse[statusCode="
                    + statusCode
                    + ", bodyLength="
                    + body.length()
                    + ", retryAfterObserved="
                    + retryAfter.isPresent()
                    + ", jsonContentTypeDeclared="
                    + jsonContentTypeDeclared
                    + "]";
        }
    }
}
