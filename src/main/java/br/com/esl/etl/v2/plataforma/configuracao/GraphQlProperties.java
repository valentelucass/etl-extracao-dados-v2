package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/** Propriedades GraphQL materializadas somente no caminho operacional autorizado. */
public final class GraphQlProperties {

    private static final int MAXIMUM_TOKEN_CHARACTERS = 16_384;
    private static final Pattern BEARER_TOKEN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

    private final URI endpoint;
    private final String token;
    private final Duration requestTimeout;
    private final GraphQlRetryPolicy retryPolicy;
    private final long maxResponseBytes;

    public GraphQlProperties(
            final URI endpoint,
            final String token,
            final Duration requestTimeout,
            final GraphQlRetryPolicy retryPolicy,
            final long maxResponseBytes) {
        this.endpoint = GraphQlClientSettings.validateEndpoint(endpoint);
        if (token == null
                || token.length() > MAXIMUM_TOKEN_CHARACTERS
                || !BEARER_TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("O token GraphQL é obrigatório.");
        }
        this.token = token;
        this.requestTimeout = GraphQlClientSettings.validateTimeout(requestTimeout);
        this.retryPolicy =
                Objects.requireNonNull(retryPolicy, "A política de retry é obrigatória.");
        if (maxResponseBytes < 1
                || maxResponseBytes > GraphQlClientSettings.ABSOLUTE_MAXIMUM_RESPONSE_BYTES) {
            throw new IllegalArgumentException("O limite de resposta GraphQL é inválido.");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    public URI endpoint() {
        return endpoint;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public GraphQlRetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    public String authorizationHeaderValue() {
        return "Bearer " + token;
    }

    @Override
    public String toString() {
        return "GraphQlProperties[endpoint=<redacted>, token=<redacted>, requestTimeout="
                + requestTimeout
                + ", retryPolicy="
                + retryPolicy
                + ", maxResponseBytes="
                + maxResponseBytes
                + "]";
    }
}
