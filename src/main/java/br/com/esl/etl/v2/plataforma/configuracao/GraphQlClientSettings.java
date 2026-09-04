package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Configuração GraphQL não secreta, segura para preflight e fingerprint. */
public final class GraphQlClientSettings {

    public static final long ABSOLUTE_MAXIMUM_RESPONSE_BYTES = 64L * 1024L * 1024L;
    public static final Duration ABSOLUTE_MAXIMUM_REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final URI endpoint;
    private final Duration requestTimeout;
    private final GraphQlRetryPolicy retryPolicy;
    private final long maxResponseBytes;

    public GraphQlClientSettings(
            final URI endpoint,
            final Duration requestTimeout,
            final GraphQlRetryPolicy retryPolicy,
            final long maxResponseBytes) {
        this.endpoint = validateEndpoint(endpoint);
        this.requestTimeout = validateTimeout(requestTimeout);
        this.retryPolicy =
                Objects.requireNonNull(retryPolicy, "A política de retry é obrigatória.");
        if (maxResponseBytes < 1 || maxResponseBytes > ABSOLUTE_MAXIMUM_RESPONSE_BYTES) {
            throw new IllegalArgumentException("O limite de resposta GraphQL é inválido.");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    public GraphQlProperties materialize(final SecretProvider secretProvider) {
        return new GraphQlProperties(
                endpoint,
                Objects.requireNonNull(secretProvider, "O provider de segredo é obrigatório.")
                        .require(SecretKey.GRAPHQL_TOKEN),
                requestTimeout,
                retryPolicy,
                maxResponseBytes);
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

    @Override
    public String toString() {
        return "GraphQlClientSettings[endpoint=<redacted>, requestTimeout="
                + requestTimeout
                + ", retryPolicy="
                + retryPolicy
                + ", maxResponseBytes="
                + maxResponseBytes
                + "]";
    }

    static URI validateEndpoint(final URI value) {
        Objects.requireNonNull(value, "O endpoint GraphQL é obrigatório.");
        if (!value.isAbsolute()
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null) {
            throw new IllegalArgumentException("O endpoint GraphQL é inválido.");
        }
        if ("https".equalsIgnoreCase(value.getScheme())
                || "http".equalsIgnoreCase(value.getScheme()) && isLoopback(value.getHost())) {
            return value;
        }
        throw new IllegalArgumentException("O endpoint GraphQL exige HTTPS fora de loopback.");
    }

    static Duration validateTimeout(final Duration value) {
        Objects.requireNonNull(value, "O timeout GraphQL é obrigatório.");
        if (value.isZero()
                || value.isNegative()
                || value.compareTo(ABSOLUTE_MAXIMUM_REQUEST_TIMEOUT) > 0) {
            throw new IllegalArgumentException("O timeout GraphQL é inválido.");
        }
        return value;
    }

    private static boolean isLoopback(final String host) {
        final String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }
}
