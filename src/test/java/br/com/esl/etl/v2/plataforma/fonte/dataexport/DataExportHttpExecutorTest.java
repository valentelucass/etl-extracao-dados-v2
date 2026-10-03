package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataExportHttpExecutorTest {

    private static final URI ENDPOINT = URI.create("https://source.invalid/data");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void executesExactlyTheConfiguredAttemptsForATransientStatus() {
        final StubHttpClient client = StubHttpClient.responding(503);
        final AtomicInteger waits = new AtomicInteger();

        final DataExportHttpResponse response =
                execute(executor(client, 3, ignored -> waits.incrementAndGet()));

        assertEquals(503, response.statusCode());
        assertEquals(3, client.requests());
        assertEquals(2, waits.get());
    }

    @Test
    void neverRetriesPermanentContractOrAuthenticationStatuses() {
        for (final int status : new int[] {400, 401, 403, 404, 409, 422, 501}) {
            final StubHttpClient client = StubHttpClient.responding(status);
            final AtomicInteger waits = new AtomicInteger();

            final DataExportHttpResponse response =
                    execute(executor(client, 3, ignored -> waits.incrementAndGet()));

            assertEquals(status, response.statusCode());
            assertEquals(1, client.requests(), "HTTP " + status);
            assertEquals(0, waits.get(), "HTTP " + status);
        }
    }

    @Test
    void boundsCommunicationRetriesAndDoesNotEchoTheirTechnicalCause() {
        final StubHttpClient client =
                StubHttpClient.failing(new IOException("sensitive-transport-detail"));
        final AtomicInteger waits = new AtomicInteger();

        final DataExportUnavailableException failure =
                assertThrows(
                        DataExportUnavailableException.class,
                        () -> execute(executor(client, 3, ignored -> waits.incrementAndGet())));

        assertEquals(3, client.requests());
        assertEquals(2, waits.get());
        assertFalse(failure.getMessage().contains("sensitive-transport-detail"));
        assertTrue(failure.getCause() instanceof IOException);
    }

    @Test
    void preservesTerminal429StatusWhenRetryAfterExceedsTheLimit() {
        assertTerminalStatusPreserved(429);
    }

    @Test
    void preservesTerminal503StatusWhenRetryAfterExceedsTheLimit() {
        assertTerminalStatusPreserved(503);
    }

    private static void assertTerminalStatusPreserved(final int status) {
        final StubHttpClient client =
                new StubHttpClient(status, null, Map.of("Retry-After", List.of("120")));
        final var retryPolicy = new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO);
        final var properties =
                new DataExportProperties(
                        URI.create("https://source.invalid"),
                        "synthetic-token",
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(1),
                        DataExportTransport.GET_WITH_QUERY,
                        retryPolicy,
                        1_024L);
        final var policy =
                new EslResiliencePolicy(
                        Duration.ZERO,
                        1,
                        2,
                        2,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3),
                        Duration.ZERO,
                        0,
                        2,
                        Duration.ofSeconds(1));
        final AtomicInteger waits = new AtomicInteger();
        final AtomicInteger attempts = new AtomicInteger();
        final var governor =
                new EslRequestGovernor(
                        policy, MonotonicTicker.systemTicker(), ignored -> waits.incrementAndGet());
        final var cycle = governor.beginCycle(CancellationToken.none());
        final var gateway =
                new HttpDataExportTemplateInfoGateway(
                        client,
                        properties,
                        new ObjectMapper(),
                        ignored -> attempts.incrementAndGet(),
                        new EslDataExportHttpAttemptGovernor(
                                cycle.beginWorkload(EslWorkload.COLETAS)));

        final var failure =
                assertThrows(
                        DataExportRetryAfterLimitExceededException.class,
                        () -> gateway.fetchInfo(DataExportTemplate.COLETAS));

        assertEquals(1, client.requests());
        assertEquals(1, attempts.get());
        assertEquals(1, cycle.sourceRequests());
        assertEquals(1, governor.availablePermits());
        assertEquals(0, waits.get());
        assertTrue(failure.getCause() instanceof DataExportUnavailableException);
        final var unavailable = (DataExportUnavailableException) failure.getCause();
        assertEquals(status, unavailable.httpStatus().orElseThrow());
        assertEquals(DataExportTemplate.COLETAS.templateId(), unavailable.templateId());
        assertTrue(unavailable.retryAfter().isEmpty());
        assertEquals(
                new DataExportRetryAfterLimitExceededException().getMessage(),
                failure.getMessage());
    }

    @Test
    void propagatesErrorWithoutRetryAndStillReleasesThePermit() {
        final var failure = new AssertionError("synthetic asynchronous failure");
        final var client = StubHttpClient.failing(failure);
        final var policy =
                new EslResiliencePolicy(
                        Duration.ZERO,
                        1,
                        10,
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500),
                        0,
                        2,
                        Duration.ofSeconds(1));
        final var governor =
                new EslRequestGovernor(
                        policy, MonotonicTicker.systemTicker(), ResilienceSleeper.threadSleeper());
        final var cycle = governor.beginCycle(CancellationToken.none());
        final var retryPolicy = new DataExportRetryPolicy(3, Duration.ZERO, Duration.ZERO);
        final var properties =
                new DataExportProperties(
                        URI.create("https://source.invalid"),
                        "synthetic-token",
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(1),
                        DataExportTransport.GET_WITH_QUERY,
                        retryPolicy,
                        1_024L);
        final var executor =
                new DataExportHttpExecutor(
                        client,
                        properties,
                        DataExportHttpAttemptObserver.noop(),
                        new EslDataExportHttpAttemptGovernor(
                                cycle.beginWorkload(EslWorkload.COLETAS)),
                        new DataExportRetrySchedule(retryPolicy, CLOCK, () -> 0.0d));

        assertSame(failure, assertThrows(AssertionError.class, () -> execute(executor)));
        assertEquals(1, client.requests());
        assertEquals(1, cycle.sourceRequests());
        assertEquals(1, governor.availablePermits());
    }

    @Test
    void cancellationDuringBackoffPreventsALateAttempt() {
        final StubHttpClient client = StubHttpClient.responding(503);
        final AtomicInteger waits = new AtomicInteger();

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        execute(
                                executor(
                                        client,
                                        3,
                                        ignored -> {
                                            waits.incrementAndGet();
                                            throw new InterruptedException("synthetic");
                                        })));

        assertEquals(1, client.requests());
        assertEquals(1, waits.get());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static DataExportHttpExecutor executor(
            final StubHttpClient client, final int maxAttempts, final DataExportSleeper sleeper) {
        final DataExportRetryPolicy retryPolicy =
                new DataExportRetryPolicy(maxAttempts, Duration.ZERO, Duration.ZERO);
        final DataExportProperties properties =
                new DataExportProperties(
                        URI.create("https://source.invalid"),
                        "synthetic-token",
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(1),
                        DataExportTransport.GET_WITH_QUERY,
                        retryPolicy,
                        1_024L);
        return new DataExportHttpExecutor(
                client,
                properties,
                DataExportHttpAttemptObserver.noop(),
                DataExportHttpAttemptGovernor.ungoverned(
                        properties.requestTimeout(), retryPolicy.maxDelay(), sleeper),
                new DataExportRetrySchedule(retryPolicy, CLOCK, () -> 0.0d));
    }

    private static DataExportHttpResponse execute(final DataExportHttpExecutor executor) {
        return executor.executeWithRetry(
                DataExportTemplate.COLETAS.templateId(),
                "unit-test",
                ignored -> HttpRequest.newBuilder(ENDPOINT).GET().build());
    }

    private static final class StubHttpClient extends HttpClient {

        private final int statusCode;
        private final Throwable failure;
        private final Map<String, List<String>> headers;
        private final AtomicInteger requests = new AtomicInteger();

        private StubHttpClient(final int statusCode, final Throwable failure) {
            this(statusCode, failure, Map.of());
        }

        private StubHttpClient(
                final int statusCode,
                final Throwable failure,
                final Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.failure = failure;
            this.headers = headers;
        }

        private static StubHttpClient responding(final int statusCode) {
            return new StubHttpClient(statusCode, null);
        }

        private static StubHttpClient failing(final Throwable failure) {
            return new StubHttpClient(0, failure);
        }

        private int requests() {
            return requests.get();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(
                final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("O teste usa somente sendAsync.");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.incrementAndGet();
            if (failure != null) {
                final CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<>();
                failed.completeExceptionally(failure);
                return failed;
            }
            final HttpResponse.BodySubscriber<T> subscriber =
                    responseBodyHandler.apply(new StubResponseInfo(statusCode, headers));
            subscriber.onSubscribe(new NoopSubscription());
            subscriber.onComplete();
            final T body = subscriber.getBody().toCompletableFuture().join();
            return CompletableFuture.completedFuture(
                    new StubHttpResponse<>(request, statusCode, body, headers));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> responseBodyHandler,
                final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record StubResponseInfo(int statusCode, Map<String, List<String>> headerValues)
            implements HttpResponse.ResponseInfo {

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headerValues, (ignoredName, ignoredValue) -> true);
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private record StubHttpResponse<T>(
            HttpRequest request, int statusCode, T body, Map<String, List<String>> headerValues)
            implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headerValues, (ignoredName, ignoredValue) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class NoopSubscription implements Flow.Subscription {

        @Override
        public void request(final long number) {}

        @Override
        public void cancel() {}
    }
}
