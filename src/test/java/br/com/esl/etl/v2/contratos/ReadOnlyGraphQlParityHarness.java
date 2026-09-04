package br.com.esl.etl.v2.contratos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Harness transitório de paridade que emite apenas consultas GraphQL fixas de leitura.
 *
 * <p>Ele não executa comandos do V1, não usa banco e não grava payloads, URLs, tokens ou
 * identificadores. A paginação é cursor-based e cada requisição consome o orçamento explícito.
 */
public final class ReadOnlyGraphQlParityHarness {

    private static final long DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024L * 1024L;
    private static final int V1_FRETE_MAXIMUM_DAYS_PER_WINDOW = 30;

    private static final String COLETAS_CONNECTION = "pick";
    private static final String FRETES_CONNECTION = "freight";
    private static final String COLETAS_QUERY =
            """
        query ContractColetasReadOnly($params: PickInput!, $after: String, $first: Int!) {
          pick(params: $params, after: $after, first: $first) {
            edges {
              node {
                id
                sequenceCode
                pickItems { id }
              }
            }
            pageInfo { hasNextPage endCursor }
          }
        }""";
    private static final String FRETES_QUERY =
            """
        query ContractFretesReadOnly($params: FreightInput!, $after: String, $first: Int!) {
          freight(params: $params, after: $after, first: $first) {
            edges {
              node {
                id
                corporationSequenceNumber
                pickItemId
                total
                accountingCreditId
                accountingCreditInstallmentId
                referenceNumber
                cte { key }
              }
            }
            pageInfo { hasNextPage endCursor }
          }
        }""";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String authorizationHeaderValue;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final long maxResponseBytes;
    private final ContractRunGuard runGuard;

    public ReadOnlyGraphQlParityHarness(
            final HttpClient httpClient,
            final URI endpoint,
            final String token,
            final Duration requestTimeout,
            final ObjectMapper objectMapper) {
        this(
                httpClient,
                endpoint,
                token,
                requestTimeout,
                objectMapper,
                DEFAULT_MAX_RESPONSE_BYTES,
                new ContractRunGuard());
    }

    public ReadOnlyGraphQlParityHarness(
            final HttpClient httpClient,
            final URI endpoint,
            final String token,
            final Duration requestTimeout,
            final ObjectMapper objectMapper,
            final long maxResponseBytes,
            final ContractRunGuard runGuard) {
        this.httpClient = Objects.requireNonNull(httpClient, "O cliente HTTP é obrigatório.");
        this.endpoint = validateEndpoint(endpoint);
        this.authorizationHeaderValue =
                "Bearer " + requireText(token, "O token GraphQL é obrigatório.");
        this.requestTimeout = validateTimeout(requestTimeout);
        this.objectMapper = Objects.requireNonNull(objectMapper, "O ObjectMapper é obrigatório.");
        this.maxResponseBytes = validateMaxResponseBytes(maxResponseBytes);
        this.runGuard = Objects.requireNonNull(runGuard, "O guard da execução é obrigatório.");
    }

    /**
     * Cria o harness para uma execução remota, compartilhando gate, teto e stop-token com Data
     * Export.
     */
    public static ReadOnlyGraphQlParityHarness forContractExecution(
            final ContractTestConfiguration configuration, final ContractRunGuard runGuard) {
        Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória.");
        return new ReadOnlyGraphQlParityHarness(
                HttpClient.newBuilder().connectTimeout(configuration.requestTimeout()).build(),
                configuration.graphQlEndpoint(),
                configuration.graphQlToken(),
                configuration.requestTimeout(),
                new ObjectMapper(),
                configuration.maxResponseBytes(),
                runGuard);
    }

    public ContractGraphQlPage<ContractGraphQlColetaIdentity> fetchColetasPage(
            final ContractDateWindow window,
            final Optional<String> after,
            final int first,
            final ContractRemoteCallBudget callBudget) {
        return fetchPage(
                COLETAS_QUERY,
                COLETAS_CONNECTION,
                "requestDate",
                window,
                after,
                first,
                callBudget,
                this::parseColeta);
    }

    public ContractGraphQlPage<ContractGraphQlFreteIdentity> fetchFretesPage(
            final ContractDateWindow window,
            final Optional<String> after,
            final int first,
            final ContractRemoteCallBudget callBudget) {
        return fetchPage(
                FRETES_QUERY,
                FRETES_CONNECTION,
                "serviceAt",
                window,
                after,
                first,
                callBudget,
                this::parseFrete);
    }

    public List<ContractGraphQlColetaIdentity> fetchAllColetas(
            final ContractDateWindow window,
            final int first,
            final ContractRemoteCallBudget callBudget) {
        Objects.requireNonNull(window, "A janela de negócio é obrigatória.");
        final List<ContractGraphQlColetaIdentity> allRows = new ArrayList<>();
        for (final ContractDateWindow dailyWindow : window.asDailyWindows()) {
            allRows.addAll(
                    fetchAll(after -> fetchColetasPage(dailyWindow, after, first, callBudget)));
        }
        return List.copyOf(allRows);
    }

    public List<ContractGraphQlFreteIdentity> fetchAllFretes(
            final ContractDateWindow window,
            final int first,
            final ContractRemoteCallBudget callBudget) {
        Objects.requireNonNull(window, "A janela de negócio é obrigatória.");
        final List<ContractGraphQlFreteIdentity> allRows = new ArrayList<>();
        for (final ContractDateWindow boundedWindow :
                window.asWindowsOfAtMostDays(V1_FRETE_MAXIMUM_DAYS_PER_WINDOW)) {
            allRows.addAll(
                    fetchAll(after -> fetchFretesPage(boundedWindow, after, first, callBudget)));
        }
        return List.copyOf(allRows);
    }

    private <T> ContractGraphQlPage<T> fetchPage(
            final String query,
            final String connectionName,
            final String filterFieldName,
            final ContractDateWindow window,
            final Optional<String> after,
            final int first,
            final ContractRemoteCallBudget callBudget,
            final ContractGraphQlRowParser<T> rowParser) {
        Objects.requireNonNull(window, "A janela de negócio é obrigatória.");
        Objects.requireNonNull(after, "O cursor é obrigatório.");
        Objects.requireNonNull(callBudget, "O orçamento de chamadas é obrigatório.");
        Objects.requireNonNull(rowParser, "O parser de linha é obrigatório.");
        if (first <= 0) {
            throw new IllegalArgumentException("O tamanho da página GraphQL deve ser positivo.");
        }
        final JsonNode response =
                executeReadOnlyQuery(
                        query, connectionName, filterFieldName, window, after, first, callBudget);
        return parsePage(response, connectionName, rowParser);
    }

    private JsonNode executeReadOnlyQuery(
            final String query,
            final String connectionName,
            final String filterFieldName,
            final ContractDateWindow window,
            final Optional<String> after,
            final int first,
            final ContractRemoteCallBudget callBudget) {
        final HttpRequest request = buildRequest(query, filterFieldName, window, after, first);
        ContractTestGate.requireEnabled();
        runGuard.reserveCall(callBudget);
        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ContractRemoteCallException("A chamada GraphQL read-only foi interrompida.");
        } catch (final IOException exception) {
            throw new ContractRemoteCallException(
                    "Falha de transporte na chamada GraphQL read-only.");
        }
        try (InputStream responseBody = response.body()) {
            if (response.statusCode() == 429) {
                runGuard.stopForRateLimit();
                throw new ContractRateLimitExceededException(
                        response.headers().firstValue("Retry-After"));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContractRemoteCallException(
                        "A chamada GraphQL read-only retornou HTTP " + response.statusCode() + ".");
            }
            validateContentLength(response);
            final JsonNode body = objectMapper.readTree(readResponseBody(responseBody));
            if (body == null || body.has("errors") || !body.has("data")) {
                throw new ContractGraphQlResponseException(
                        "A resposta GraphQL read-only não contém um envelope de dados íntegro.");
            }
            if (!body.path("data").has(connectionName)) {
                throw new ContractGraphQlResponseException(
                        "A resposta GraphQL não contém a conexão esperada.");
            }
            return body.path("data").path(connectionName);
        } catch (final JsonProcessingException exception) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL read-only não é JSON válido.");
        } catch (final IOException exception) {
            throw new ContractRemoteCallException(
                    "Não foi possível ler a resposta GraphQL read-only.");
        }
    }

    private HttpRequest buildRequest(
            final String query,
            final String filterFieldName,
            final ContractDateWindow window,
            final Optional<String> after,
            final int first) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query);
        final ObjectNode variables = payload.putObject("variables");
        final ObjectNode params = variables.putObject("params");
        params.put(filterFieldName, businessWindowValue(filterFieldName, window));
        after.ifPresentOrElse(
                value -> variables.put("after", value), () -> variables.putNull("after"));
        variables.put("first", first);
        try {
            return HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", authorizationHeaderValue)
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(payload),
                                    StandardCharsets.UTF_8))
                    .build();
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Não foi possível serializar a consulta GraphQL read-only.");
        }
    }

    private static String businessWindowValue(
            final String filterFieldName, final ContractDateWindow window) {
        if ("requestDate".equals(filterFieldName)) {
            return window.asGraphQlSingleDate();
        }
        if ("serviceAt".equals(filterFieldName)) {
            return window.asGraphQlInterval();
        }
        throw new IllegalArgumentException("O filtro GraphQL read-only não é reconhecido.");
    }

    private void validateContentLength(final HttpResponse<InputStream> response) {
        final long contentLength =
                response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > maxResponseBytes) {
            throw new ContractRemoteCallException(
                    "A resposta GraphQL read-only excedeu o limite de bytes permitido.");
        }
    }

    private String readResponseBody(final InputStream body) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8_192];
        long receivedBytes = 0L;
        int bytesRead;
        while ((bytesRead = body.read(buffer, 0, bytesToRead(receivedBytes, buffer.length)))
                != -1) {
            if (bytesRead > maxResponseBytes - receivedBytes) {
                throw new ContractRemoteCallException(
                        "A resposta GraphQL read-only excedeu o limite de bytes permitido.");
            }
            output.write(buffer, 0, bytesRead);
            receivedBytes += bytesRead;
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private int bytesToRead(final long receivedBytes, final int bufferLength) {
        final long remainingBytes = maxResponseBytes - receivedBytes;
        if (remainingBytes >= bufferLength) {
            return bufferLength;
        }
        return Math.toIntExact(remainingBytes) + 1;
    }

    private <T> ContractGraphQlPage<T> parsePage(
            final JsonNode connection,
            final String connectionName,
            final ContractGraphQlRowParser<T> rowParser) {
        if (!connection.isObject()) {
            throw new ContractGraphQlResponseException(
                    "A conexão GraphQL esperada não é um objeto.");
        }
        final JsonNode edges = requiredArray(connection, "edges", connectionName);
        final List<T> rows = new ArrayList<>();
        for (final JsonNode edge : edges) {
            if (!edge.isObject() || !edge.has("node") || !edge.path("node").isObject()) {
                throw new ContractGraphQlResponseException(
                        "A resposta GraphQL contém uma edge sem node válido.");
            }
            rows.add(rowParser.parse(edge.path("node")));
        }
        final JsonNode pageInfo = requiredObject(connection, "pageInfo", connectionName);
        final JsonNode hasNextPage = pageInfo.get("hasNextPage");
        if (hasNextPage == null || !hasNextPage.isBoolean()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL não informa hasNextPage booleano.");
        }
        final Optional<String> endCursor = optionalScalar(pageInfo, "endCursor");
        return new ContractGraphQlPage<>(rows, hasNextPage.booleanValue(), endCursor);
    }

    private ContractGraphQlColetaIdentity parseColeta(final JsonNode node) {
        final JsonNode pickItems = requiredArray(node, "pickItems", "node de Coleta");
        final List<Optional<String>> pickItemIds = new ArrayList<>();
        for (final JsonNode pickItem : pickItems) {
            if (!pickItem.isObject()) {
                throw new ContractGraphQlResponseException(
                        "A resposta GraphQL contém pick item inválido.");
            }
            pickItemIds.add(optionalScalar(pickItem, "id"));
        }
        return new ContractGraphQlColetaIdentity(
                optionalScalar(node, "id"), optionalScalar(node, "sequenceCode"), pickItemIds);
    }

    private ContractGraphQlFreteIdentity parseFrete(final JsonNode node) {
        return new ContractGraphQlFreteIdentity(
                optionalScalar(node, "id"),
                optionalScalar(node, "corporationSequenceNumber"),
                optionalScalar(node, "pickItemId"),
                optionalNestedScalar(node, "cte", "key"),
                optionalDecimal(node, "total"),
                optionalScalar(node, "accountingCreditId"),
                optionalScalar(node, "accountingCreditInstallmentId"),
                optionalScalar(node, "referenceNumber"));
    }

    private static Optional<String> optionalNestedScalar(
            final JsonNode parent, final String objectField, final String scalarField) {
        final JsonNode nested = requiredField(parent, objectField);
        if (nested.isNull()) {
            return Optional.empty();
        }
        if (!nested.isObject()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL contém objeto aninhado inválido.");
        }
        return optionalScalar(nested, scalarField);
    }

    private static Optional<String> optionalScalar(final JsonNode parent, final String fieldName) {
        final JsonNode value = requiredField(parent, fieldName);
        if (value.isNull()) {
            return Optional.empty();
        }
        if (!value.isValueNode()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL contém escalar inválido.");
        }
        return Optional.of(value.asText());
    }

    private static Optional<BigDecimal> optionalDecimal(
            final JsonNode parent, final String fieldName) {
        final JsonNode value = requiredField(parent, fieldName);
        if (value.isNull()) {
            return Optional.empty();
        }
        if (value.isNumber()) {
            return Optional.of(value.decimalValue());
        }
        if (!value.isTextual()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL contém valor financeiro inválido.");
        }
        try {
            return Optional.of(new BigDecimal(value.textValue()));
        } catch (final NumberFormatException exception) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL contém valor financeiro não numérico.");
        }
    }

    private static JsonNode requiredField(final JsonNode parent, final String fieldName) {
        final JsonNode field = parent.get(fieldName);
        if (field == null) {
            throw new ContractGraphQlResponseException("A resposta GraphQL omitiu campo esperado.");
        }
        return field;
    }

    private static JsonNode requiredArray(
            final JsonNode parent, final String fieldName, final String context) {
        final JsonNode field = requiredField(parent, fieldName);
        if (!field.isArray()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL não contém a lista esperada em " + context + ".");
        }
        return field;
    }

    private static JsonNode requiredObject(
            final JsonNode parent, final String fieldName, final String context) {
        final JsonNode field = requiredField(parent, fieldName);
        if (!field.isObject()) {
            throw new ContractGraphQlResponseException(
                    "A resposta GraphQL não contém o objeto esperado em " + context + ".");
        }
        return field;
    }

    private static <T> List<T> fetchAll(final ContractGraphQlPageFetcher<T> pageFetcher) {
        final List<T> allRows = new ArrayList<>();
        final Set<String> observedCursors = new HashSet<>();
        Optional<String> after = Optional.empty();
        while (true) {
            final ContractGraphQlPage<T> page = pageFetcher.fetch(after);
            allRows.addAll(page.rows());
            if (!page.hasNextPage()) {
                return List.copyOf(allRows);
            }
            final String endCursor =
                    page.endCursor()
                            .orElseThrow(
                                    () ->
                                            new ContractGraphQlResponseException(
                                                    "A próxima página GraphQL não informa cursor final."));
            if (!observedCursors.add(endCursor)) {
                throw new ContractGraphQlResponseException(
                        "A paginação GraphQL repetiu cursor final.");
            }
            after = Optional.of(endCursor);
        }
    }

    private static URI validateEndpoint(final URI value) {
        Objects.requireNonNull(value, "O endpoint GraphQL é obrigatório.");
        if (!value.isAbsolute() || value.getHost() == null || value.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "O endpoint GraphQL deve ser absoluto e conter host.");
        }
        if (value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "O endpoint GraphQL não pode conter credenciais, query ou fragmento.");
        }
        if ("https".equalsIgnoreCase(value.getScheme())) {
            return value;
        }
        if ("http".equalsIgnoreCase(value.getScheme()) && isLoopbackHost(value.getHost())) {
            return value;
        }
        throw new IllegalArgumentException("O endpoint GraphQL deve usar HTTPS fora de loopback.");
    }

    private static Duration validateTimeout(final Duration value) {
        Objects.requireNonNull(value, "O timeout GraphQL é obrigatório.");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("O timeout GraphQL deve ser positivo.");
        }
        return value;
    }

    private static long validateMaxResponseBytes(final long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("O limite de resposta GraphQL deve ser positivo.");
        }
        return value;
    }

    private static String requireText(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean isLoopbackHost(final String host) {
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }

    @FunctionalInterface
    private interface ContractGraphQlRowParser<T> {
        T parse(JsonNode node);
    }

    @FunctionalInterface
    private interface ContractGraphQlPageFetcher<T> {
        ContractGraphQlPage<T> fetch(Optional<String> after);
    }
}
