package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.SSLParameters;

/**
 * RAS-HTTP-OBS-001, catalog raster-contrato-local/contrato.json: one bounded read-only POST.
 * Observations retain source candidates; synthetic bindings and terminal evidence never apply here.
 */
public final class HttpRasterObservationClient {
    private final RasterHttpSettings settings;
    private final Supplier<Credentials> secrets;
    private final HttpClient client;
    private final AtomicBoolean used = new AtomicBoolean();
    private final BooleanSupplier sourceWindow;
    private final MonotonicTicker ticker;
    private long startedNanos;

    public HttpRasterObservationClient(
            final RasterHttpSettings settings, final Supplier<Credentials> secrets) {
        this(settings, secrets, closedClient(), System::getProperty);
    }

    /** SRC-WINDOW-001: a closed window never resolves endpoint or credentials. */
    public static HttpRasterObservationClient createForSourceWindow(
            final BooleanSupplier sourceWindow,
            final Supplier<RasterHttpSettings> settingsProvider,
            final Supplier<Credentials> secrets) {
        requireWindow(sourceWindow);
        final var settings =
                Objects.requireNonNull(settingsProvider, "RAS_SETTINGS_PROVIDER_REQUIRED").get();
        requireWindow(sourceWindow);
        return new HttpRasterObservationClient(
                settings,
                secrets,
                strictClient(settings),
                System::getProperty,
                sourceWindow,
                MonotonicTicker.systemTicker());
    }

    private static HttpClient closedClient() {
        throw new IllegalStateException("SOURCE_WINDOW_CLOSED");
    }

    private static void requireWindow(final BooleanSupplier window) {
        try {
            if (window != null && window.getAsBoolean()) {
                return;
            }
        } catch (final RuntimeException ignored) {
            // Unknown admission never becomes permission to probe.
        }
        throw new IllegalStateException("SOURCE_WINDOW_CLOSED");
    }

    HttpRasterObservationClient(
            final RasterHttpSettings settings,
            final Supplier<Credentials> secrets,
            final HttpClient client,
            final Function<String, String> properties) {
        this(settings, secrets, client, properties, () -> true, MonotonicTicker.systemTicker());
    }

    HttpRasterObservationClient(
            final RasterHttpSettings settings,
            final Supplier<Credentials> secrets,
            final HttpClient client,
            final Function<String, String> properties,
            final BooleanSupplier sourceWindow,
            final MonotonicTicker ticker) {
        requireWindow(sourceWindow);
        if (settings == null || !settings.enabled()) {
            throw new IllegalArgumentException("RAS_DISABLED");
        }
        requireJdkPolicy(properties);
        if (client == null
                || client.followRedirects() != HttpClient.Redirect.NEVER
                || !"HTTPS".equals(client.sslParameters().getEndpointIdentificationAlgorithm())) {
            throw new IllegalArgumentException("RAS_HTTP_CLIENT_POLICY");
        }
        this.settings = settings;
        this.secrets = Objects.requireNonNull(secrets, "RAS_CREDENTIAL_PROVIDER_REQUIRED");
        this.client = client;
        this.sourceWindow = sourceWindow;
        this.ticker = Objects.requireNonNull(ticker, "RAS_TICKER_REQUIRED");
    }

    static HttpClient strictClient(final RasterHttpSettings settings) {
        if (settings == null || !settings.enabled()) {
            throw new IllegalArgumentException("RAS_DISABLED");
        }
        requireJdkPolicy(System::getProperty);
        final var tls = new SSLParameters();
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        return HttpClient.newBuilder()
                .sslParameters(tls)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Required at JVM launch, before the JDK HTTP implementation initializes its retry policy. */
    static void requireJdkPolicy(final Function<String, String> properties) {
        if (!"true".equals(properties.apply("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(properties.apply("jdk.httpclient.enableAllMethodRetry"))
                || !"1".equals(properties.apply("jdk.httpclient.redirects.retrylimit"))) {
            throw new IllegalArgumentException("RAS_JDK_RETRY_POLICY_REQUIRED");
        }
    }

    /**
     * Sink effects belong to its caller's transaction; failure never produces a success receipt.
     */
    public Receipt observe(
            final RasterObservationRequest request, final RasterResponseParser.Sink sink)
            throws IOException, InterruptedException {
        requireWindow(sourceWindow);
        Objects.requireNonNull(request, "RAS_REQUEST_REQUIRED");
        Objects.requireNonNull(sink, "RAS_SINK_REQUIRED");
        if (!used.compareAndSet(false, true)) {
            throw new Failure("RAS_ALREADY_USED", 0);
        }
        startedNanos = ticker.readNanos();
        final HttpRequest http = request(request);
        final HttpResponse<byte[]> response = send(http);
        final byte[] body = response.body();
        try {
            remaining();
            final var parser = new RasterResponseParser(settings.zone());
            final RasterResponseParser.Counts counts;
            try {
                counts = parser.parse(body, new ValidationSink());
            } catch (final IOException | SQLException | RuntimeException failure) {
                throw new Failure("RAS_CONTRACT_INVALID", response.statusCode());
            }
            if (counts.trips() > settings.maximumTrips()
                    || counts.stops() > settings.maximumStops()) {
                throw new Failure("RAS_OBSERVATION_BOUND", response.statusCode());
            }
            remaining();
            try {
                parser.parse(body, sink);
            } catch (final IOException | SQLException | RuntimeException failure) {
                throw new Failure("RAS_SINK_FAILED", response.statusCode());
            }
            remaining();
            return new Receipt(response.statusCode(), 1, counts.trips(), counts.stops());
        } finally {
            Arrays.fill(body, (byte) 0);
        }
    }

    private HttpRequest request(final RasterObservationRequest request) throws Failure {
        remaining();
        final Credentials credentials;
        try {
            credentials = Objects.requireNonNull(secrets.get());
        } catch (final RuntimeException failure) {
            throw new Failure("RAS_CREDENTIAL_UNAVAILABLE", 0);
        }
        remaining();
        try {
            final var body = new ObjectMapper().createObjectNode();
            body.put("Ambiente", request.environment());
            body.put("Login", credentials.login());
            body.put("Senha", credentials.password());
            body.put("TipoRetorno", "JSON");
            body.put("DataInicial", request.startInclusive().toString());
            body.put("DataFinal", request.endInclusive().toString());
            body.put("StatusViagem", request.status());
            return HttpRequest.newBuilder(settings.endpoint())
                    .timeout(settings.timeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    new ObjectMapper().writeValueAsBytes(body)))
                    .build();
        } catch (final IOException | RuntimeException failure) {
            throw new Failure("RAS_REQUEST_INVALID", 0);
        }
    }

    private HttpResponse<byte[]> send(final HttpRequest request)
            throws Failure, InterruptedException {
        final Duration remaining = remaining();
        final Duration timeout =
                settings.timeout().compareTo(remaining) <= 0 ? settings.timeout() : remaining;
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            future =
                    client.sendAsync(
                            request,
                            new RasterObservationBodyHandler(settings.maximumResponseBytes()));
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (final TimeoutException failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new Failure("RAS_HTTP_DEADLINE", 0);
        } catch (final InterruptedException failure) {
            if (future != null) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw new InterruptedException("RAS_INTERRUPTED");
        } catch (final ExecutionException | RuntimeException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                if (cause instanceof Rejected rejected) {
                    throw new Failure(rejected.code, rejected.status);
                }
            }
            throw new Failure("RAS_HTTP_FAILURE", 0);
        }
    }

    private Duration remaining() throws Failure {
        requireWindow(sourceWindow);
        final long elapsed = ticker.readNanos() - startedNanos;
        final long budget = Duration.ofSeconds(60).toNanos();
        if (elapsed < 0 || elapsed >= budget) {
            throw new Failure("RAS_UNIT_DEADLINE", 0);
        }
        return Duration.ofNanos(budget - elapsed);
    }

    public record Credentials(String login, String password) {
        public Credentials {
            if (!valid(login) || !valid(password)) {
                throw new IllegalArgumentException("RAS_CREDENTIAL_INVALID");
            }
        }

        private static boolean valid(final String value) {
            return value != null
                    && !value.isBlank()
                    && value.length() <= 4096
                    && value.chars().noneMatch(Character::isISOControl);
        }

        @Override
        public String toString() {
            return "RasterCredentials[REDACTED]";
        }
    }

    public record Receipt(int httpStatus, int calls, int trips, int stops) {
        public boolean sourceCompletenessProven() {
            return false;
        }

        public boolean canonicalIdentityProven() {
            return false;
        }
    }

    public static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final int httpStatus;

        private Failure(final String code, final int httpStatus) {
            super(code);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() {
            return code;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    static final class Rejected extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final int status;

        Rejected(final String code, final int status) {
            super(code);
            this.code = code;
            this.status = status;
        }
    }

    private static final class ValidationSink implements RasterResponseParser.Sink {
        @Override
        public void trip(final int position, final RasterTripObservation observation) {}

        @Override
        public void stop(final int trip, final int position, final RasterStop observation) {}
    }
}
