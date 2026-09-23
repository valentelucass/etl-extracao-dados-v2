package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

class GraphQlHttpExecutorTest {

    @Test
    void countsIoFailuresAndRetriesAtTransportSubmission() {
        final var count = new java.util.concurrent.atomic.AtomicInteger();
        final var fixture =
                fixture(new IOException("synthetic"), operation -> count.incrementAndGet());
        assertThrows(GraphQlUnavailableException.class, fixture::execute);
        assertEquals(2, count.get());
        assertEquals(2, fixture.cycle().sourceRequests());
    }

    @Test
    void requestConstructionFailureDoesNotClaimAnHttpAttempt() {
        final var count = new java.util.concurrent.atomic.AtomicInteger();
        final var fixture =
                fixture(new IOException("synthetic"), operation -> count.incrementAndGet());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.executor()
                                .execute(
                                        GraphQlReadOperation.USERS_SNAPSHOT,
                                        timeout -> {
                                            throw new IllegalArgumentException("synthetic");
                                        }));
        assertEquals(0, count.get());
        assertEquals(1, fixture.governor().availablePermits());
    }

    @Test
    void refusingObserverDoesNotSubmitOrRetry() {
        final var refusal = new IllegalStateException("ATTEMPT_LIMIT");
        final var count = new java.util.concurrent.atomic.AtomicInteger();
        final var fixture =
                fixture(
                        new AssertionError("Transport must not be called"),
                        operation -> {
                            count.incrementAndGet();
                            throw refusal;
                        });
        assertSame(refusal, assertThrows(IllegalStateException.class, fixture::execute));
        assertEquals(1, count.get());
        assertEquals(1, fixture.governor().availablePermits());
    }

    @Test
    void sanitizesUnknownRuntimeFailureWithoutRetryOrCause() {
        final String sensitive = "synthetic-sensitive-url";
        final ExecutorFixture fixture = fixture(new IllegalStateException(sensitive));

        final GraphQlTransportException exception =
                assertThrows(GraphQlTransportException.class, fixture::execute);

        assertFalse(exception.toString().contains(sensitive));
        assertNull(exception.getCause());
        assertEquals(1, fixture.cycle().sourceRequests());
        assertEquals(1, fixture.governor().availablePermits());
    }

    @Test
    void propagatesErrorWithoutRetryAndStillReleasesThePermit() {
        final AssertionError failure = new AssertionError("synthetic");
        final ExecutorFixture fixture = fixture(failure);

        assertSame(failure, assertThrows(AssertionError.class, fixture::execute));
        assertEquals(1, fixture.cycle().sourceRequests());
        assertEquals(1, fixture.governor().availablePermits());
    }

    private static ExecutorFixture fixture(final Throwable failure) {
        return fixture(failure, GraphQlHttpAttemptObserver.noop());
    }

    private static ExecutorFixture fixture(
            final Throwable failure, final GraphQlHttpAttemptObserver attempts) {
        final EslResiliencePolicy policy =
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
        final EslRequestGovernor governor =
                new EslRequestGovernor(
                        policy, MonotonicTicker.systemTicker(), ResilienceSleeper.threadSleeper());
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(CancellationToken.none());
        final GraphQlProperties properties =
                new GraphQlProperties(
                        URI.create("http://127.0.0.1:1/graphql"),
                        "synthetic-token",
                        Duration.ofSeconds(1),
                        new GraphQlRetryPolicy(2, Duration.ZERO, Duration.ofMillis(10)),
                        1_024);
        final GraphQlHttpExecutor executor =
                new GraphQlHttpExecutor(
                        new FailingHttpClient(failure),
                        properties,
                        cycle.beginWorkload(GraphQlReadOperation.USERS_SNAPSHOT.workload()),
                        Clock.systemUTC(),
                        () -> 0.0d,
                        attempts);
        return new ExecutorFixture(governor, cycle, executor);
    }

    private record ExecutorFixture(
            EslRequestGovernor governor,
            EslRequestGovernor.Cycle cycle,
            GraphQlHttpExecutor executor) {

        private GraphQlHttpResponse execute() {
            return executor.execute(
                    GraphQlReadOperation.USERS_SNAPSHOT,
                    timeout ->
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:1/graphql"))
                                    .timeout(timeout)
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build());
        }
    }

    private static final class FailingHttpClient extends HttpClient {

        private final Throwable failure;

        private FailingHttpClient(final Throwable failure) {
            this.failure = failure;
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
                final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException("A fixture usa somente sendAsync.");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
            final CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
            future.completeExceptionally(failure);
            return future;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> responseBodyHandler,
                final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }
}
