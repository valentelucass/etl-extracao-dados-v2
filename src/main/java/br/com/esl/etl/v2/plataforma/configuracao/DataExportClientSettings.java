package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Configuração não secreta do cliente Data Export, segura para preflight e dry-run. */
public final class DataExportClientSettings {

    public static final long MAX_RESPONSE_BYTES = 64L * 1024L * 1024L;
    public static final Duration MAX_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    public static final ZoneId REQUIRED_SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final URI baseUri;
    private final ZoneId sourceZone;
    private final Duration requestTimeout;
    private final DataExportTransport preferredTransport;
    private final DataExportRetryPolicy retryPolicy;
    private final long maxResponseBytes;

    public DataExportClientSettings(
            final URI baseUri,
            final ZoneId sourceZone,
            final Duration requestTimeout,
            final DataExportTransport preferredTransport,
            final DataExportRetryPolicy retryPolicy,
            final long maxResponseBytes) {
        this.baseUri = validateBaseUri(baseUri);
        this.sourceZone = validateSourceZone(sourceZone);
        this.requestTimeout = validatePositiveDuration(requestTimeout);
        this.preferredTransport =
                Objects.requireNonNull(preferredTransport, "O transporte é obrigatório.");
        this.retryPolicy =
                Objects.requireNonNull(retryPolicy, "A política de retry é obrigatória.");
        if (maxResponseBytes <= 0 || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("O limite de resposta está fora do permitido.");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    /** Materializa a configuração secreta somente no caminho operacional já autorizado. */
    public DataExportProperties materialize(final SecretProvider secretProvider) {
        Objects.requireNonNull(secretProvider, "O provider de segredo é obrigatório.");
        return new DataExportProperties(
                baseUri,
                secretProvider.require(SecretKey.DATA_EXPORT_TOKEN),
                sourceZone,
                requestTimeout,
                preferredTransport,
                retryPolicy,
                maxResponseBytes);
    }

    public URI baseUri() {
        return baseUri;
    }

    public ZoneId sourceZone() {
        return sourceZone;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public DataExportTransport preferredTransport() {
        return preferredTransport;
    }

    public DataExportRetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    @Override
    public String toString() {
        return "DataExportClientSettings[baseUri=<redacted>, sourceZone="
                + sourceZone
                + ", requestTimeout="
                + requestTimeout
                + ", preferredTransport="
                + preferredTransport
                + ", retryPolicy="
                + retryPolicy
                + ", maxResponseBytes="
                + maxResponseBytes
                + "]";
    }

    private static URI validateBaseUri(final URI value) {
        Objects.requireNonNull(value, "A URL base é obrigatória.");
        if (!value.isAbsolute() || value.getHost() == null || value.getHost().isBlank()) {
            throw new IllegalArgumentException("A URL base Data Export é inválida.");
        }
        if (value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null) {
            throw new IllegalArgumentException("A URL base Data Export é inválida.");
        }
        if ("https".equalsIgnoreCase(value.getScheme())
                || ("http".equalsIgnoreCase(value.getScheme())
                        && isLoopbackHost(value.getHost()))) {
            return value;
        }
        throw new IllegalArgumentException("A URL base Data Export é inválida.");
    }

    private static ZoneId validateSourceZone(final ZoneId value) {
        Objects.requireNonNull(value, "O timezone da fonte é obrigatório.");
        if (!REQUIRED_SOURCE_ZONE.equals(value)) {
            throw new IllegalArgumentException(
                    "O timezone Data Export diverge do contrato de migração.");
        }
        return value;
    }

    private static Duration validatePositiveDuration(final Duration value) {
        Objects.requireNonNull(value, "O timeout da requisição é obrigatório.");
        if (value.isZero() || value.isNegative() || value.compareTo(MAX_REQUEST_TIMEOUT) > 0) {
            throw new IllegalArgumentException("O timeout da requisição está fora do permitido.");
        }
        return value;
    }

    private static boolean isLoopbackHost(final String host) {
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }
}
