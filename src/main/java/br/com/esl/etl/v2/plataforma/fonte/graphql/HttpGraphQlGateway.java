package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Adaptador HTTP POST vinculado a um único documento estático e um workload ESL compartilhado. */
public final class HttpGraphQlGateway implements GraphQlGateway {

    private final GraphQlReadOperation operation;
    private final GraphQlProperties properties;
    private final GraphQlRequestJsonSerializer serializer;
    private final GraphQlResponseParser parser;
    private final GraphQlHttpExecutor executor;

    HttpGraphQlGateway(
            final GraphQlReadOperation operation,
            final GraphQlProperties properties,
            final HttpClient httpClient,
            final ObjectMapper objectMapper,
            final EslRequestGovernor.Cycle.Workload workload,
            final Clock clock,
            final GraphQlJitterSource jitterSource,
            final GraphQlContractObservationConfiguration observationConfiguration) {
        this.operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        this.properties = Objects.requireNonNull(properties, "As propriedades são obrigatórias.");
        if (Objects.requireNonNull(
                                observationConfiguration,
                                "A configuração de observação é obrigatória.")
                        .operation()
                != operation) {
            throw new IllegalArgumentException("A observação não corresponde à operação GraphQL.");
        }
        serializer = new GraphQlRequestJsonSerializer(objectMapper);
        parser = new GraphQlResponseParser(observationConfiguration);
        executor = new GraphQlHttpExecutor(httpClient, properties, workload, clock, jitterSource);
    }

    @Override
    public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
        Objects.requireNonNull(request, "A requisição GraphQL é obrigatória.");
        if (request.operation() != operation) {
            throw new IllegalArgumentException("A requisição não corresponde ao gateway GraphQL.");
        }
        final String body = serializer.serialize(request);
        final GraphQlHttpResponse response =
                executor.execute(operation, timeout -> buildRequest(body, timeout));
        if (GraphQlHttpExecutor.isRetryableStatus(response.statusCode())) {
            throw new GraphQlUnavailableException(operation, response.statusCode());
        }
        if (!GraphQlHttpExecutor.isSuccess(response.statusCode()) || response.statusCode() == 204) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.HTTP_STATUS);
        }
        if (!response.jsonContentTypeDeclared()) {
            throw new GraphQlResponseException(
                    GraphQlResponseException.Reason.INVALID_CONTENT_TYPE);
        }
        return parser.parse(response.body(), request);
    }

    private HttpRequest buildRequest(final String body, final Duration timeout) {
        return HttpRequest.newBuilder(properties.endpoint())
                .timeout(timeout)
                .header("Authorization", properties.authorizationHeaderValue())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }
}
