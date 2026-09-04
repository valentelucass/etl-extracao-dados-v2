package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Configuração injetável do Data Export, sem suporte a segredos em arquivos versionados. */
public final class DataExportProperties {

    private static final long DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024L * 1024L;

    private final URI baseUri;
    private final String token;
    private final ZoneId sourceZone;
    private final Duration requestTimeout;
    private final DataExportTransport preferredTransport;
    private final DataExportRetryPolicy retryPolicy;
    private final long maxResponseBytes;

    public DataExportProperties(
            final URI baseUri,
            final String token,
            final ZoneId sourceZone,
            final Duration requestTimeout,
            final DataExportTransport preferredTransport,
            final DataExportRetryPolicy retryPolicy) {
        this(
                baseUri,
                token,
                sourceZone,
                requestTimeout,
                preferredTransport,
                retryPolicy,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    public DataExportProperties(
            final URI baseUri,
            final String token,
            final ZoneId sourceZone,
            final Duration requestTimeout,
            final DataExportTransport preferredTransport,
            final DataExportRetryPolicy retryPolicy,
            final long maxResponseBytes) {
        this.baseUri = validateBaseUri(baseUri);
        this.token = requireText(token, "O token Data Export é obrigatório.");
        this.sourceZone = validateIanaZone(sourceZone);
        this.requestTimeout = validateRequestTimeout(requestTimeout);
        this.preferredTransport =
                Objects.requireNonNull(preferredTransport, "O transporte é obrigatório.");
        this.retryPolicy =
                Objects.requireNonNull(retryPolicy, "A política de retry é obrigatória.");
        this.maxResponseBytes = validateResponseLimit(maxResponseBytes);
    }

    public URI endpointFor(final int templateId) {
        return reportEndpointFor(templateId, "data");
    }

    /** Retorna o recurso de metadados do template, sem expor a URL base em logs. */
    public URI infoEndpointFor(final int templateId) {
        return reportEndpointFor(templateId, "info");
    }

    private URI reportEndpointFor(final int templateId, final String resource) {
        if (templateId <= 0) {
            throw new IllegalArgumentException("O template Data Export deve ser positivo.");
        }
        final String endpointPath =
                endpointBasePath(baseUri) + "/api/analytics/reports/" + templateId + "/" + resource;
        try {
            return new URI(
                    baseUri.getScheme(),
                    null,
                    baseUri.getHost(),
                    baseUri.getPort(),
                    endpointPath,
                    null,
                    null);
        } catch (final URISyntaxException exception) {
            throw new IllegalStateException(
                    "Não foi possível compor o endpoint Data Export.", exception);
        }
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

    public String authorizationHeaderValue() {
        return "Bearer " + token;
    }

    @Override
    public String toString() {
        return "DataExportProperties[baseUri=<redacted>"
                + ", sourceZone="
                + sourceZone
                + ", requestTimeout="
                + requestTimeout
                + ", preferredTransport="
                + preferredTransport
                + ", retryPolicy="
                + retryPolicy
                + ", maxResponseBytes="
                + maxResponseBytes
                + ", token=<redacted>]";
    }

    private static URI validateBaseUri(final URI value) {
        Objects.requireNonNull(value, "A URL base é obrigatória.");
        if (!value.isAbsolute()) {
            throw new IllegalArgumentException("A URL base Data Export deve ser absoluta.");
        }
        if (value.getHost() == null || value.getHost().isBlank()) {
            throw new IllegalArgumentException("A URL base Data Export deve informar host.");
        }
        if (value.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "A URL base Data Export não pode conter credenciais.");
        }
        if (value.getRawQuery() != null || value.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "A URL base Data Export não pode conter query ou fragmento.");
        }
        if ("https".equalsIgnoreCase(value.getScheme())) {
            return value;
        }
        if ("http".equalsIgnoreCase(value.getScheme()) && isLoopbackHost(value.getHost())) {
            return value;
        }
        throw new IllegalArgumentException(
                "A URL base Data Export deve usar HTTPS fora de loopback.");
    }

    private static ZoneId validateIanaZone(final ZoneId value) {
        Objects.requireNonNull(value, "O timezone da fonte é obrigatório.");
        if (!ZoneId.getAvailableZoneIds().contains(value.getId())) {
            throw new IllegalArgumentException(
                    "O timezone da fonte deve usar um identificador IANA, não offset fixo.");
        }
        return value;
    }

    private static long validateResponseLimit(final long value) {
        if (value <= 0 || value > DataExportClientSettings.MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("O limite de resposta está fora do permitido.");
        }
        return value;
    }

    private static Duration validateRequestTimeout(final Duration value) {
        Objects.requireNonNull(value, "O timeout da requisição é obrigatório.");
        if (value.isZero()
                || value.isNegative()
                || value.compareTo(DataExportClientSettings.MAX_REQUEST_TIMEOUT) > 0) {
            throw new IllegalArgumentException("O timeout da requisição está fora do permitido.");
        }
        return value;
    }

    private static String requireText(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String endpointBasePath(final URI value) {
        final String path = value.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static boolean isLoopbackHost(final String host) {
        if (host == null) {
            return false;
        }
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }
}
