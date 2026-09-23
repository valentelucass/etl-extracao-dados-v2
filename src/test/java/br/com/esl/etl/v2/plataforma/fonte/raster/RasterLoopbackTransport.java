package br.com.esl.etl.v2.plataforma.fonte.raster;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Objects;

/** Testable HTTP edge restricted to numeric loopback, fictitious credentials and one fixed path. */
public final class RasterLoopbackTransport {
    public static final String PATH = "/datasnap/rest/TWebService/%22getEventoFimViagem%22";
    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    public RasterLoopbackTransport(
            final RasterTransportConfiguration configuration,
            final URI endpoint,
            final Duration timeout) {
        Objects.requireNonNull(configuration);
        if (!configuration.enabled()) {
            throw new IllegalArgumentException("RAS_DISABLED");
        }
        if (!configuration.syntheticLoopbackCredentials()) {
            throw new IllegalArgumentException("RAS_LOOPBACK_CREDENTIALS_ONLY");
        }
        if (endpoint == null
                || !"http".equals(endpoint.getScheme())
                || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getPort() < 1024
                || endpoint.getPort() > 65535
                || !PATH.equals(endpoint.getRawPath())
                || endpoint.getUserInfo() != null
                || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("RAS_LOOPBACK_TARGET_ONLY");
        }
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("RAS_HTTP_TIMEOUT_BOUND");
        }
        this.endpoint = endpoint;
        this.timeout = timeout;
        client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    public byte[] fetch(final RasterWindow window) throws IOException, InterruptedException {
        Objects.requireNonNull(window);
        final var request = new ObjectMapper().createObjectNode();
        request.put("Ambiente", "SYNTHETIC");
        request.put("Login", "synthetic-raster-user");
        request.put("Senha", "synthetic-raster-password");
        request.put("TipoRetorno", "JSON");
        request.put("DataInicial", window.start().toString());
        request.put("DataFinal", window.endExclusive().minusDays(1).toString());
        request.put("StatusViagem", "SYNTHETIC");
        final var future =
                client.sendAsync(
                        HttpRequest.newBuilder(endpoint)
                                .timeout(timeout)
                                .header("Content-Type", "application/json; charset=utf-8")
                                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                                .build(),
                        new RasterHttpBodyHandler());
        try {
            return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .body();
        } catch (final java.util.concurrent.TimeoutException failure) {
            future.cancel(true);
            throw new IOException("RAS_HTTP_DEADLINE", failure);
        } catch (final java.util.concurrent.ExecutionException failure) {
            throw new IOException("RAS_HTTP_FAILURE", failure.getCause());
        } catch (final InterruptedException failure) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw failure;
        }
    }
}
