package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/** Offline fixtures only. No real HttpClient, socket, source or filesystem use. */
final class ObservedHttpTransportTestSupport {
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final BusinessDateRange DATES =
            new BusinessDateRange(LocalDate.of(2036, 3, 19), LocalDate.of(2036, 3, 19));
    static final String PRIVATE_BODY = "SYNTHETIC_PRIVATE_BODY_MARKER";

    static ObservedExpansionCapturePlan plan(final ObservedExpansionCaptureTemplate template) {
        return new ObservedExpansionCapturePlan(
                UUID.randomUUID(),
                template,
                "source-one",
                "tenant-one",
                DATES,
                ZONE,
                DataExportTransport.GET_WITH_BODY,
                ObservedExpansionCapturePlan.OrderBy.absent(),
                ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY,
                50,
                ObservedExpansionCaptureLimits.proposedProofBounds());
    }

    static EslResiliencePolicy policy(final int workloadBudget, final Duration interval) {
        return new EslResiliencePolicy(
                interval,
                1,
                20,
                workloadBudget,
                Duration.ofSeconds(2),
                Duration.ofSeconds(180),
                Duration.ofSeconds(1080),
                Duration.ZERO,
                0,
                3,
                Duration.ofSeconds(1));
    }

    static DataExportSourceConfiguration source(
            final DataExportTransport transport,
            final int attempts,
            final Duration timeout,
            final URI uri,
            final EslResiliencePolicy policy) {
        return new DataExportSourceConfiguration(
                "source-one",
                "tenant-one",
                new DataExportClientSettings(
                        uri,
                        ZONE,
                        timeout,
                        transport,
                        new DataExportRetryPolicy(attempts, Duration.ZERO, Duration.ZERO),
                        8 * 1024 * 1024),
                policy);
    }

    static final class Fixture {
        final AtomicLong nanos = new AtomicLong();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CancellationToken cancellation = cancelled::get;
        final EslResiliencePolicy policy;
        final EslRequestGovernor governor;
        final EslRequestGovernor.Cycle cycle;
        final DataExportSourceConfiguration source;
        final FakeClient client = new FakeClient();
        final DataExportHttpGatewayFactory factory;
        final ObservedExpansionCapturePlan plan;

        Fixture(final ObservedExpansionCaptureTemplate template) {
            this(
                    template,
                    DataExportTransport.GET_WITH_BODY,
                    1,
                    Duration.ofSeconds(2),
                    URI.create("https://synthetic.invalid/v1"),
                    policy(5, Duration.ofSeconds(3)));
        }

        Fixture(
                final ObservedExpansionCaptureTemplate template,
                final DataExportTransport transport,
                final int attempts,
                final Duration timeout,
                final URI uri,
                final EslResiliencePolicy policy) {
            this.policy = policy;
            source = source(transport, attempts, timeout, uri, policy);
            governor =
                    new EslRequestGovernor(
                            policy, nanos::get, delay -> nanos.addAndGet(delay.toNanos()));
            cycle = governor.beginCycle(cancellation);
            plan = plan(template);
            factory =
                    new DataExportHttpGatewayFactory(
                            source, key -> "synthetic-token", client, Clock.systemUTC());
        }

        ObservedExpansionHttpTransport prepare() {
            return factory.prepareObservedTransport(
                    plan, cycle, governor, cancellation, DataExportHttpAttemptObserver.noop());
        }
    }

    record Wire(int status, Map<String, List<String>> headers, byte[] body, boolean streamFailure) {
        static Wire json(final byte[] body) {
            return new Wire(
                    200,
                    Map.of("Content-Type", List.of("application/json; charset=UTF-8")),
                    body,
                    false);
        }
    }

    static final class FakeClient extends HttpClient {
        final List<Wire> responses = new ArrayList<>();
        final List<HttpRequest> requests = new ArrayList<>();
        final List<String> requestBodies = new ArrayList<>();
        Redirect redirect = Redirect.NEVER;
        Throwable failure;
        Runnable beforeCompletion = () -> {};

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            final int index = requests.size();
            requests.add(request);
            requestBodies.add(requestBody(request));
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            try {
                final Wire wire = responses.get(index);
                final HttpHeaders headers = HttpHeaders.of(wire.headers(), (a, b) -> true);
                final HttpResponse.ResponseInfo info =
                        new HttpResponse.ResponseInfo() {
                            @Override
                            public int statusCode() {
                                return wire.status();
                            }

                            @Override
                            public HttpHeaders headers() {
                                return headers;
                            }

                            @Override
                            public Version version() {
                                return Version.HTTP_1_1;
                            }
                        };
                final var subscriber = handler.apply(info);
                subscriber.onSubscribe(
                        new Flow.Subscription() {
                            @Override
                            public void request(final long count) {}

                            @Override
                            public void cancel() {}
                        });
                final int split = wire.body().length / 2;
                subscriber.onNext(List.of(ByteBuffer.wrap(wire.body(), 0, split)));
                subscriber.onNext(
                        List.of(ByteBuffer.wrap(wire.body(), split, wire.body().length - split)));
                beforeCompletion.run();
                if (wire.streamFailure()) {
                    subscriber.onError(new java.io.IOException(PRIVATE_BODY));
                } else {
                    subscriber.onComplete();
                }
                return subscriber
                        .getBody()
                        .toCompletableFuture()
                        .thenApply(body -> new Response<>(request, wire.status(), headers, body));
            } catch (final RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }

        private static String requestBody(final HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) {
                return "";
            }
            final var completed = new CompletableFuture<String>();
            final var bytes = new ByteArrayOutputStream();
            request.bodyPublisher()
                    .orElseThrow()
                    .subscribe(
                            new Flow.Subscriber<ByteBuffer>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription s) {
                                    s.request(Long.MAX_VALUE);
                                }

                                @Override
                                public void onNext(final ByteBuffer b) {
                                    final byte[] part = new byte[b.remaining()];
                                    b.get(part);
                                    bytes.writeBytes(part);
                                }

                                @Override
                                public void onError(final Throwable e) {
                                    completed.completeExceptionally(e);
                                }

                                @Override
                                public void onComplete() {
                                    completed.complete(bytes.toString(StandardCharsets.UTF_8));
                                }
                            });
            return completed.join();
        }

        @Override
        public <T> HttpResponse<T> send(final HttpRequest r, final HttpResponse.BodyHandler<T> h) {
            throw new AssertionError("NO_SYNC_TRANSPORT");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest r,
                final HttpResponse.BodyHandler<T> h,
                final HttpResponse.PushPromiseHandler<T> p) {
            throw new AssertionError("NO_PUSH_TRANSPORT");
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
            return redirect;
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
    }

    private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
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
}
