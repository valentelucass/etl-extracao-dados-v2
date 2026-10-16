package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioNamePresence;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class UsersGraphQlObservationClientTest {
    @Test
    void closedWindowRefusesSourceAndTokenBeforeAnyFactoryWork() {
        final var read = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        UsersGraphQlObservationClient.createForSourceWindow(
                                                () -> false,
                                                true,
                                                () -> {
                                                    read.incrementAndGet();
                                                    return source();
                                                },
                                                key -> {
                                                    read.incrementAndGet();
                                                    return "synthetic";
                                                },
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                        .getMessage());
        assertEquals(0, read.get());
    }

    @Test
    void revokedWindowDoesNotSendOrDeliverAfterResponse() {
        final var open = new AtomicBoolean(true);
        final var before = new FakeClient(envelope(null, false));
        final var beforeClient = client(before, CancellationToken.none(), open::get);
        open.set(false);
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(IllegalStateException.class, beforeClient::observe).getMessage());
        assertEquals(0, before.calls);
        open.set(true);
        final var during = new FakeClient(envelope(null, false));
        during.afterResponse = () -> open.set(false);
        final var duringClient = client(during, CancellationToken.none(), open::get);
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(IllegalStateException.class, duringClient::observe).getMessage());
        assertEquals(1, during.calls);
    }

    @Test
    void legacyPublicEntryCannotBypassClosedWindow() {
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        UsersGraphQlObservationClient.create(
                                                true, null, null, null, null, null, null, null))
                        .getMessage());
    }

    @Test
    void realGatewayParserAndMapperCaptureFirstOneWithoutFollowingCursor() {
        final var http =
                new FakeClient(envelope("{\"id\":930001,\"name\":\"Synthetic private\"}", true));
        final var client = client(http, CancellationToken.none());
        final var receipt = client.observe();
        assertEquals("POST", http.request.method());
        assertTrue(http.requestBody.contains("\"first\":1"));
        assertTrue(http.requestBody.contains("\"after\":null"));
        assertTrue(http.requestBody.contains("\"enabled\":true"));
        assertTrue(http.requestBody.contains("V2UsersSnapshot"));
        assertFalse(http.requestBody.contains("mutation"));
        assertEquals(UsersGraphQlObservationClient.Status.PARTIAL_CAPTURE, receipt.status());
        assertTrue(receipt.hasNextPage());
        assertFalse(receipt.sourceCompletenessProven());
        assertFalse(receipt.contractAccepted());
        assertEquals(1, receipt.httpCalls());
        assertEquals(1, receipt.pageNumber());
        assertEquals(1, receipt.requestedFirst());
        assertEquals(1, receipt.physicalRows());
        assertEquals(0, receipt.quarantinedRows());
        assertTrue(receipt.responseBytes() > 0);
        assertEquals("Synthetic private", receipt.records().get(0).name());
        assertThrows(UnsupportedOperationException.class, () -> receipt.records().clear());
        assertFalse(receipt.toString().contains("930001"));
        assertFalse(receipt.toString().contains("private"));
        assertFalse(receipt.toString().contains("synthetic-cursor"));
        assertEquals(
                "USERS_OBS_ALREADY_USED",
                assertThrows(IllegalStateException.class, client::observe).getMessage());
        assertEquals(1, http.calls);
    }

    @Test
    void emptyAndTerminalPagesRemainHonestObservations() {
        for (final boolean hasNext : List.of(false, true)) {
            final var empty = new FakeClient(envelope(null, hasNext));
            final var receipt = client(empty, CancellationToken.none()).observe();
            assertEquals(UsersGraphQlObservationClient.Status.NO_DATA_OBSERVED, receipt.status());
            assertEquals(0, receipt.physicalRows());
            assertEquals(hasNext, receipt.hasNextPage());
            assertFalse(receipt.sourceCompletenessProven());
            assertFalse(receipt.contractAccepted());
            assertEquals(1, empty.calls);
        }
        final var terminal =
                client(new FakeClient(envelope("{\"id\":930001}", false)), CancellationToken.none())
                        .observe();
        assertEquals(UsersGraphQlObservationClient.Status.PARTIAL_CAPTURE, terminal.status());
        assertFalse(terminal.sourceCompletenessProven());
    }

    @Test
    void mapperPreservesMissingNullAndQuarantineWithoutLeakingRawName() {
        final var missing =
                client(new FakeClient(envelope("{\"id\":930001}", false)), CancellationToken.none())
                        .observe();
        assertEquals(UsuarioNamePresence.ABSENT, missing.records().get(0).namePresence());
        final var explicitNull =
                client(
                                new FakeClient(
                                        envelope(
                                                "{\"id\":\"synthetic-key\",\"name\":null}", false)),
                                CancellationToken.none())
                        .observe();
        assertEquals(UsuarioNamePresence.NULL, explicitNull.records().get(0).namePresence());
        final var quarantine =
                client(
                                new FakeClient(envelope("{\"id\":930001,\"name\":42}", false)),
                                CancellationToken.none())
                        .observe();
        assertEquals(1, quarantine.physicalRows());
        assertEquals(1, quarantine.quarantinedRows());
        assertEquals("INVALID_NAME_TYPE", quarantine.records().get(0).quarantineReasonCode());
        assertNull(quarantine.records().get(0).name());
    }

    @Test
    void strictParserRejectsExcessRowsAndInvalidIdentityOrCursorBeforeDelivery() {
        for (final String body :
                List.of(
                        GraphQlTestSupport.usersEnvelope(
                                "[{\"node\":{\"id\":1}},{\"node\":{\"id\":2}}]", false, "null"),
                        envelope("{\"id\":null}", false),
                        envelope("{\"id\":true}", false),
                        GraphQlTestSupport.usersEnvelope("[]", true, "null"),
                        "{\"errors\":[{\"message\":\"private-error\"}],\"data\":null}")) {
            final var http = new FakeClient(body);
            final var client = client(http, CancellationToken.none());
            final var failure = assertThrows(GraphQlResponseException.class, client::observe);
            assertFalse(failure.toString().contains("private-error"));
            assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, client::observe);
            assertEquals(1, http.calls);
        }
    }

    @Test
    void stopOnNonSuccessAndRateLimitNeverRetries() {
        for (final int status : List.of(202, 204, 301, 400, 401, 429, 500, 503)) {
            final var http = new FakeClient(envelope("{\"id\":1}", false));
            http.status = status;
            final var client = client(http, CancellationToken.none());
            assertThrows(RuntimeException.class, client::observe);
            assertThrows(IllegalStateException.class, client::observe);
            assertEquals(1, http.calls);
        }
    }

    @Test
    void bodyLimitContentTypeUtf8AndTransportFailureUseExistingGuards() {
        final var oversized = new FakeClient(" ".repeat(2049));
        assertThrows(
                RuntimeException.class,
                () -> client(oversized, CancellationToken.none()).observe());
        assertTrue(oversized.cancelled);
        final var media = new FakeClient(envelope(null, false));
        media.contentType = "text/html";
        assertThrows(
                GraphQlResponseException.class,
                () -> client(media, CancellationToken.none()).observe());
        final var utf8 = new FakeClient(envelope(null, false));
        utf8.bytes = new byte[] {(byte) 0xc3, (byte) 0x28};
        assertThrows(
                GraphQlResponseException.class,
                () -> client(utf8, CancellationToken.none()).observe());
        final var transport = new FakeClient(envelope(null, false));
        transport.failure = new IllegalStateException("private-transport");
        final var failure =
                assertThrows(
                        RuntimeException.class,
                        () -> client(transport, CancellationToken.none()).observe());
        assertFalse(failure.toString().contains("private-transport"));
        assertNull(failure.getCause());
        assertEquals(1, transport.calls);
    }

    @Test
    void cancellationBeforeAndAfterTransportPreventsDelivery() {
        final var cancelled = new AtomicBoolean();
        final CancellationToken token = cancelled::get;
        final var before = new FakeClient(envelope(null, false));
        final var beforeClient = client(before, token);
        cancelled.set(true);
        assertThrows(ResilienceCancelledException.class, beforeClient::observe);
        assertEquals(0, before.calls);
        cancelled.set(false);
        final var after = new FakeClient(envelope(null, false));
        after.afterResponse = () -> cancelled.set(true);
        final var afterClient = client(after, token);
        assertThrows(ResilienceCancelledException.class, afterClient::observe);
        assertEquals(1, after.calls);
    }

    @Test
    void expiredCycleStopsBeforeHttpAndExpiredResponseIsNotDelivered() {
        final var before = new FakeClient(envelope(null, false));
        final var beforeClient = client(before, CancellationToken.none());
        before.now = Duration.ofSeconds(61).toNanos();
        assertThrows(RuntimeException.class, beforeClient::observe);
        assertEquals(0, before.calls);
        final var during = new FakeClient(envelope(null, false));
        during.afterResponse = () -> during.now = Duration.ofSeconds(61).toNanos();
        final var duringClient = client(during, CancellationToken.none());
        assertThrows(RuntimeException.class, duringClient::observe);
        assertEquals(1, during.calls);
    }

    @Test
    void optInAndInvalidSourceRefuseBeforeSecretsOrClientCreation() {
        final var read = new AtomicBoolean();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsersGraphQlObservationClient.createForSourceWindow(
                                () -> true,
                                false,
                                UsersGraphQlObservationClientTest::source,
                                key -> {
                                    read.set(true);
                                    return "synthetic";
                                },
                                null,
                                null,
                                null,
                                null,
                                null));
        final var invalid =
                new GraphQlSourceConfiguration(
                        "SYNTHETIC",
                        "SYNTHETIC",
                        new GraphQlClientSettings(
                                URI.create("http://localhost/graphql"),
                                Duration.ofSeconds(30),
                                retry(1),
                                2048),
                        policy(1, 2, 30, 60));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsersGraphQlObservationClient.createForSourceWindow(
                                () -> true,
                                true,
                                () -> invalid,
                                key -> {
                                    read.set(true);
                                    return "synthetic";
                                },
                                null,
                                null,
                                null,
                                null,
                                null));
        assertFalse(read.get());
    }

    @Test
    void launchRequiresExactRetryFlagsAndStrictFiniteBudgets() {
        UsersGraphQlObservationClient.validateLaunch(
                true, source(), UsersGraphQlObservationClientTest::property);
        for (final String key :
                List.of(
                        "jdk.httpclient.disableRetryConnect",
                        "jdk.httpclient.enableAllMethodRetry",
                        "jdk.httpclient.redirects.retrylimit")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            UsersGraphQlObservationClient.validateLaunch(
                                    true,
                                    source(),
                                    name -> key.equals(name) ? null : property(name)));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsersGraphQlObservationClient.validateLaunch(
                                true,
                                source(),
                                name ->
                                        name.equals(
                                                        "jdk.internal.httpclient.disableHostnameVerification")
                                                ? "true"
                                                : property(name)));
        for (final GraphQlSourceConfiguration invalid :
                List.of(
                        configuration(2, 2048, policy(1, 2, 30, 60)),
                        configuration(1, 10L * 1024 * 1024 + 1, policy(1, 2, 30, 60)),
                        configuration(1, 2048, policy(2, 2, 30, 60)),
                        configuration(1, 2048, policy(1, 1, 30, 60)),
                        configuration(1, 2048, policy(1, 2, 31, 60)),
                        configuration(1, 2048, policy(1, 2, 30, 61)))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            UsersGraphQlObservationClient.validateLaunch(
                                    true, invalid, UsersGraphQlObservationClientTest::property));
        }
    }

    private static UsersGraphQlObservationClient client(
            final FakeClient http, final CancellationToken cancellation) {
        return client(http, cancellation, () -> true);
    }

    private static UsersGraphQlObservationClient client(
            final FakeClient http,
            final CancellationToken cancellation,
            final java.util.function.BooleanSupplier sourceWindow) {
        final var source = source();
        final var governor =
                new EslRequestGovernor(
                        source.resiliencePolicy(),
                        () -> http.now,
                        duration -> {
                            throw new AssertionError("NO_SLEEP_EXPECTED");
                        });
        final var cycle = governor.beginCycle(cancellation);
        final var gateway =
                new HttpGraphQlGateway(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        source.settings().materialize(key -> "synthetic"),
                        http,
                        GraphQlTestSupport.MAPPER,
                        cycle.beginWorkload(GraphQlReadOperation.USERS_SNAPSHOT.workload()),
                        Clock.systemUTC(),
                        () -> 0.0,
                        GraphQlTestSupport.usersFixture().configuration());
        return new UsersGraphQlObservationClient(
                gateway,
                cancellation,
                () -> cycle.beginWorkload(GraphQlReadOperation.USERS_SNAPSHOT.workload()),
                sourceWindow);
    }

    private static GraphQlSourceConfiguration source() {
        return configuration(1, 2048, policy(1, 2, 30, 60));
    }

    private static GraphQlSourceConfiguration configuration(
            final int attempts, final long bytes, final EslResiliencePolicy policy) {
        return new GraphQlSourceConfiguration(
                "SYNTHETIC",
                "SYNTHETIC",
                new GraphQlClientSettings(
                        URI.create("https://example.invalid/graphql"),
                        policy.requestTimeout(),
                        retry(attempts),
                        bytes),
                policy);
    }

    private static GraphQlRetryPolicy retry(final int attempts) {
        return new GraphQlRetryPolicy(attempts, Duration.ZERO, Duration.ZERO);
    }

    private static EslResiliencePolicy policy(
            final int cap, final int interval, final int request, final int cycle) {
        return new EslResiliencePolicy(
                Duration.ofSeconds(interval),
                1,
                cap,
                cap,
                Duration.ofSeconds(request),
                Duration.ofSeconds(cycle),
                Duration.ofSeconds(cycle),
                Duration.ofSeconds(1),
                0,
                1,
                Duration.ofSeconds(1));
    }

    private static String property(final String name) {
        return switch (name) {
            case "jdk.httpclient.disableRetryConnect" -> "true";
            case "jdk.httpclient.enableAllMethodRetry" -> "false";
            case "jdk.httpclient.redirects.retrylimit" -> "1";
            default -> null;
        };
    }

    private static String envelope(final String node, final boolean hasNext) {
        return GraphQlTestSupport.usersEnvelope(
                node == null ? "[]" : "[{\"node\":" + node + "}]",
                hasNext,
                hasNext ? "\"synthetic-cursor\"" : "null");
    }

    private static final class FakeClient extends HttpClient {
        private final String body;
        private int status = 200;
        private String contentType = "application/json";
        private byte[] bytes;
        private int calls;
        private long now;
        private boolean cancelled;
        private HttpRequest request;
        private String requestBody;
        private RuntimeException failure;
        private Runnable afterResponse = () -> {};

        private FakeClient(final String body) {
            this.body = body;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest sent, final HttpResponse.BodyHandler<T> handler) {
            calls++;
            request = sent;
            final var captured = new StringBuilder();
            sent.bodyPublisher()
                    .orElseThrow()
                    .subscribe(
                            new Flow.Subscriber<ByteBuffer>() {
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    subscription.request(Long.MAX_VALUE);
                                }

                                public void onNext(final ByteBuffer bytes) {
                                    captured.append(StandardCharsets.UTF_8.decode(bytes));
                                }

                                public void onError(final Throwable error) {
                                    throw new AssertionError(error);
                                }

                                public void onComplete() {
                                    requestBody = captured.toString();
                                }
                            });
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            final var info =
                    new HttpResponse.ResponseInfo() {
                        public int statusCode() {
                            return status;
                        }

                        public HttpHeaders headers() {
                            return HttpHeaders.of(
                                    Map.of("Content-Type", List.of(contentType)),
                                    (name, value) -> true);
                        }

                        public Version version() {
                            return Version.HTTP_1_1;
                        }
                    };
            try {
                final var subscriber = handler.apply(info);
                subscriber.onSubscribe(
                        new Flow.Subscription() {
                            public void request(final long n) {}

                            public void cancel() {
                                cancelled = true;
                            }
                        });
                subscriber.onNext(
                        List.of(
                                ByteBuffer.wrap(
                                        bytes == null
                                                ? body.getBytes(StandardCharsets.UTF_8)
                                                : bytes)));
                subscriber.onComplete();
                afterResponse.run();
                return subscriber
                        .getBody()
                        .toCompletableFuture()
                        .thenApply(
                                payload ->
                                        new HttpResponse<T>() {
                                            public int statusCode() {
                                                return status;
                                            }

                                            public HttpRequest request() {
                                                return sent;
                                            }

                                            public Optional<HttpResponse<T>> previousResponse() {
                                                return Optional.empty();
                                            }

                                            public HttpHeaders headers() {
                                                return info.headers();
                                            }

                                            public T body() {
                                                return payload;
                                            }

                                            public Optional<SSLSession> sslSession() {
                                                return Optional.empty();
                                            }

                                            public URI uri() {
                                                return sent.uri();
                                            }

                                            public Version version() {
                                                return Version.HTTP_1_1;
                                            }
                                        });
            } catch (final RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }

        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        public SSLParameters sslParameters() {
            final var ssl = new SSLParameters();
            ssl.setEndpointIdentificationAlgorithm("HTTPS");
            return ssl;
        }

        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        public Version version() {
            return Version.HTTP_1_1;
        }

        public Optional<Executor> executor() {
            return Optional.empty();
        }

        public <T> HttpResponse<T> send(
                final HttpRequest sent, final HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest sent,
                final HttpResponse.BodyHandler<T> handler,
                final HttpResponse.PushPromiseHandler<T> push) {
            throw new UnsupportedOperationException();
        }
    }
}
