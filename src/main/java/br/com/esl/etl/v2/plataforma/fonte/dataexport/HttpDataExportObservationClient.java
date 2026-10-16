package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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

/** OBS-HTTP-001: serial INFO and first page, each at most once, with strict HTTPS. */
public final class HttpDataExportObservationClient {
    private final DataExportObservationSettings settings;
    private final HttpClient client;
    private final AtomicBoolean used = new AtomicBoolean();
    private final MonotonicTicker ticker;
    private final DataExportSleeper sleeper;
    private final BooleanSupplier sourceWindow;
    private long startedNanos;
    private long budgetNanos;
    private long infoCompletedNanos;

    public HttpDataExportObservationClient(final DataExportObservationSettings settings) {
        this(
                settings,
                closedClient(),
                System::getProperty,
                MonotonicTicker.systemTicker(),
                DataExportSleeper.threadSleeper());
    }

    /** SRC-WINDOW-001: admission precedes lazy source selection; no implicit probe window. */
    public static HttpDataExportObservationClient createForSourceWindow(
            final BooleanSupplier sourceWindow,
            final Supplier<DataExportObservationSettings> settingsProvider) {
        requireWindow(sourceWindow);
        final var settings =
                Objects.requireNonNull(settingsProvider, "OBS_SETTINGS_PROVIDER_REQUIRED").get();
        requireWindow(sourceWindow);
        return new HttpDataExportObservationClient(
                settings,
                strictClient(settings),
                System::getProperty,
                MonotonicTicker.systemTicker(),
                DataExportSleeper.threadSleeper(),
                sourceWindow);
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
            // Unknown admission also fails closed, without propagating provider details.
        }
        throw new IllegalStateException("SOURCE_WINDOW_CLOSED");
    }

    HttpDataExportObservationClient(
            final DataExportObservationSettings settings,
            final HttpClient client,
            final Function<String, String> properties,
            final MonotonicTicker ticker,
            final DataExportSleeper sleeper) {
        this(settings, client, properties, ticker, sleeper, () -> true);
    }

    HttpDataExportObservationClient(
            final DataExportObservationSettings settings,
            final HttpClient client,
            final Function<String, String> properties,
            final MonotonicTicker ticker,
            final DataExportSleeper sleeper,
            final BooleanSupplier sourceWindow) {
        requireWindow(sourceWindow);
        if (settings == null || !settings.enabled()) {
            throw new IllegalArgumentException("OBS_DISABLED");
        }
        requireJdkPolicy(properties);
        if (client == null
                || client.followRedirects() != HttpClient.Redirect.NEVER
                || !"HTTPS".equals(client.sslParameters().getEndpointIdentificationAlgorithm())) {
            throw new IllegalArgumentException("OBS_HTTP_CLIENT_POLICY");
        }
        this.settings = settings;
        this.client = client;
        this.ticker = Objects.requireNonNull(ticker, "OBS_TICKER_REQUIRED");
        this.sleeper = Objects.requireNonNull(sleeper, "OBS_SLEEPER_REQUIRED");
        this.sourceWindow = sourceWindow;
    }

    static HttpClient strictClient(final DataExportObservationSettings settings) {
        if (settings == null || !settings.enabled()) {
            throw new IllegalArgumentException("OBS_DISABLED");
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

    static void requireJdkPolicy(final Function<String, String> properties) {
        if (!"true".equals(properties.apply("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(properties.apply("jdk.httpclient.enableAllMethodRetry"))
                || !"1".equals(properties.apply("jdk.httpclient.redirects.retrylimit"))) {
            throw new IllegalArgumentException("OBS_JDK_RETRY_POLICY_REQUIRED");
        }
    }

    public DataExportObservedPage observe(final DataExportObservationRequest request)
            throws IOException, InterruptedException {
        requireWindow(sourceWindow);
        if (request == null) {
            throw new IllegalArgumentException("OBS_REQUEST_REQUIRED");
        }
        // SRC-PER-SCOPE-001: INFO-only candidate roots cannot establish remote per semantics.
        if (request.page().template() != DataExportTemplate.FRETES) {
            throw new IllegalArgumentException("ROOT_PER_UNVERIFIED");
        }
        final String root = request.paginationRoot();
        begin(Duration.ofSeconds(60));
        final var source = settings.source();
        final var info = fetchMetadata(request.page().template());
        final var metadata = info.metadata();
        if (metadata.fields().stream().noneMatch(field -> root.equals(field.technicalName()))
                || metadata.filters().stream()
                        .noneMatch(
                                field ->
                                        request.page()
                                                .template()
                                                .metadataBusinessDateFilterName()
                                                .equals(field.technicalName()))) {
            throw new Failure("OBS_METADATA_REQUIRED_FIELD", info.httpStatus());
        }
        final URI endpoint =
                new DataExportQueryEncoder()
                        .appendQuery(
                                source.endpointFor(request.page().template().templateId()),
                                request.page(),
                                source.sourceZone());
        spaceDataRequest();
        final var response = send(endpoint);
        try {
            final var page =
                    new DataExportResponseNormalizer()
                            .normalize(
                                    DataExportStrictJsonParser.readTree(decode(response.body())));
            final var observation =
                    new DataExportObservedPage(
                            request,
                            page.records(),
                            settings.maximumRows(),
                            info.httpStatus(),
                            response.statusCode());
            remaining();
            return observation;
        } catch (final Failure failure) {
            throw failure;
        } catch (final IllegalArgumentException failure) {
            final String code = failure.getMessage();
            if ("ROOT_PER_UNVERIFIED".equals(code)
                    || "OBS_ROW_BOUND".equals(code)
                    || "OBS_PER_EXCEEDED".equals(code)
                    || "OBS_RECORD_INVALID".equals(code)) {
                throw new Failure(code, response.statusCode());
            }
            throw new Failure("OBS_DATA_INVALID", response.statusCode());
        } catch (final IOException | RuntimeException failure) {
            throw new Failure("OBS_DATA_INVALID", response.statusCode());
        } finally {
            Arrays.fill(response.body(), (byte) 0);
        }
    }

    /** Explicit metadata-only operation; success never advances automatically to a data request. */
    public MetadataReceipt observeMetadata(final DataExportTemplate template)
            throws IOException, InterruptedException {
        if (template == null || template == DataExportTemplate.COLETAS) {
            throw new IllegalArgumentException("OBS_TEMPLATE_REFUSED");
        }
        begin(Duration.ofSeconds(30));
        return fetchMetadata(template);
    }

    /** Explicit finite physical observation; never a fallback for an unverified pagination root. */
    public DataExportObservedPage.PhysicalObservation observePhysical(
            final DataExportObservationRequest request) throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("PHYSICAL_REQUEST_REQUIRED");
        }
        final var template = request.page().template();
        DataExportObservedPage.PhysicalObservation.requireTemplate(template);
        begin(Duration.ofSeconds(60));
        final var info = fetchMetadata(template);
        final var metadata = info.metadata();
        if (metadata.fields().isEmpty()
                || request.page().filters().keySet().stream()
                        .anyMatch(
                                path ->
                                        metadata.filters().stream()
                                                .noneMatch(
                                                        field ->
                                                                (path.root() + "." + path.field())
                                                                        .equals(
                                                                                field
                                                                                        .technicalName())))) {
            throw new Failure("PHYSICAL_METADATA_REQUIRED_FIELD", info.httpStatus());
        }
        final var source = settings.source();
        final URI endpoint =
                new DataExportQueryEncoder()
                        .appendQuery(
                                source.endpointFor(template.templateId()),
                                request.page(),
                                source.sourceZone());
        spaceDataRequest();
        final var response = send(endpoint);
        try {
            final var result =
                    new DataExportObservedPage.PhysicalObservation(
                            request,
                            DataExportStrictJsonParser.readTree(decode(response.body())),
                            metadata,
                            info.httpStatus(),
                            response.statusCode());
            remaining();
            return result;
        } catch (final Failure failure) {
            throw failure;
        } catch (final IllegalArgumentException failure) {
            final String code = failure.getMessage();
            if (code != null && code.startsWith("PHYSICAL_")) {
                throw new Failure(code, response.statusCode());
            }
            throw new Failure("PHYSICAL_DATA_INVALID", response.statusCode());
        } catch (final IOException | RuntimeException failure) {
            throw new Failure("PHYSICAL_DATA_INVALID", response.statusCode());
        } finally {
            Arrays.fill(response.body(), (byte) 0);
        }
    }

    private MetadataReceipt fetchMetadata(final DataExportTemplate template)
            throws Failure, InterruptedException {
        final var response = send(settings.source().infoEndpointFor(template.templateId()));
        infoCompletedNanos = ticker.readNanos();
        try {
            final var metadata =
                    new DataExportTemplateInfoParser()
                            .parse(DataExportStrictJsonParser.readTree(decode(response.body())));
            remaining();
            return new MetadataReceipt(response.statusCode(), metadata);
        } catch (final Failure failure) {
            throw failure;
        } catch (final IOException | RuntimeException failure) {
            throw new Failure("OBS_METADATA_INVALID", response.statusCode());
        } finally {
            Arrays.fill(response.body(), (byte) 0);
        }
    }

    private void begin(final Duration budget) throws Failure {
        requireWindow(sourceWindow);
        if (!used.compareAndSet(false, true)) {
            throw new Failure("OBS_ALREADY_USED", 0);
        }
        startedNanos = ticker.readNanos();
        budgetNanos = budget.toNanos();
    }

    private Duration remaining() throws Failure {
        requireWindow(sourceWindow);
        final long elapsed = ticker.readNanos() - startedNanos;
        if (elapsed < 0 || elapsed >= budgetNanos) {
            throw new Failure("OBS_UNIT_DEADLINE", 0);
        }
        return Duration.ofNanos(budgetNanos - elapsed);
    }

    private void spaceDataRequest() throws Failure, InterruptedException {
        final long elapsed = ticker.readNanos() - infoCompletedNanos;
        if (elapsed < 0) {
            throw new Failure("OBS_UNIT_DEADLINE", 0);
        }
        final long wait = Math.max(0L, Duration.ofSeconds(2).toNanos() - elapsed);
        if (remaining().toNanos() <= wait) {
            throw new Failure("OBS_UNIT_DEADLINE", 0);
        }
        if (wait > 0) {
            try {
                sleeper.sleep(Duration.ofNanos(wait));
            } catch (final InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("OBS_INTERRUPTED");
            }
        }
        remaining();
        if (ticker.readNanos() - infoCompletedNanos < Duration.ofSeconds(2).toNanos()) {
            throw new Failure("OBS_INTERVAL_NOT_ELAPSED", 0);
        }
    }

    public record MetadataReceipt(
            int httpStatus, DataExportTemplateInfoParser.ParsedTemplateInfo metadata) {
        public int calls() {
            return 1;
        }

        public boolean canonicalIdentityProven() {
            return false;
        }

        public boolean paginationRootProven() {
            return false;
        }

        @Override
        public String toString() {
            return "DataExportMetadataReceipt[httpStatus="
                    + httpStatus
                    + ", fields="
                    + metadata.fields().size()
                    + ", filters="
                    + metadata.filters().size()
                    + "]";
        }
    }

    private static String decode(final byte[] body) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString();
    }

    private HttpResponse<byte[]> send(final URI endpoint) throws Failure, InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            final var source = settings.source();
            final Duration remaining = remaining();
            final Duration timeout =
                    source.requestTimeout().compareTo(remaining) <= 0
                            ? source.requestTimeout()
                            : remaining;
            final var request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("Authorization", source.authorizationHeaderValue())
                            .header("Accept", "application/json")
                            .GET()
                            .build();
            future =
                    client.sendAsync(
                            request,
                            new DataExportObservationBodyHandler(
                                    Math.toIntExact(source.maxResponseBytes())));
            final var response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            try {
                remaining();
            } catch (final Failure | RuntimeException failure) {
                Arrays.fill(response.body(), (byte) 0);
                throw failure;
            }
            return response;
        } catch (final Failure failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw failure;
        } catch (final TimeoutException failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new Failure("OBS_HTTP_DEADLINE", 0);
        } catch (final InterruptedException failure) {
            if (future != null) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw new InterruptedException("OBS_INTERRUPTED");
        } catch (final ExecutionException | RuntimeException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                if (cause instanceof Rejected rejected) {
                    throw new Failure(rejected.code, rejected.status);
                }
            }
            throw new Failure("OBS_HTTP_FAILURE", 0);
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
}
