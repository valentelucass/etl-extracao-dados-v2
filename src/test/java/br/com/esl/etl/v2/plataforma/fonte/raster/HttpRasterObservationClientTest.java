package br.com.esl.etl.v2.plataforma.fonte.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
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
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpRasterObservationClientTest {
    @Test
    void closedWindowRefusesLazyEndpointAndCredentials() {
        final var reads = new AtomicInteger();
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        HttpRasterObservationClient.createForSourceWindow(
                                                () -> false,
                                                () -> {
                                                    reads.incrementAndGet();
                                                    return settings(ENDPOINT, 4096, 1, 1);
                                                },
                                                () -> {
                                                    reads.incrementAndGet();
                                                    return credentials();
                                                }))
                        .getMessage());
        assertEquals(0, reads.get());
    }

    @Test
    void revokedWindowPrecedesCredentialsSendAndDelivery() {
        final var open = new java.util.concurrent.atomic.AtomicBoolean(true);
        final var reads = new AtomicInteger();
        final var transport = new FakeClient(200, "{\"Viagens\":[" + TRIP + "]}");
        final var client =
                new HttpRasterObservationClient(
                        settings(ENDPOINT, 4096, 1, 1),
                        () -> {
                            reads.incrementAndGet();
                            return credentials();
                        },
                        transport,
                        HttpRasterObservationClientTest::jdkPolicy,
                        open::get,
                        () -> 0L);
        open.set(false);
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () -> client.observe(request(), new Collector()))
                        .getMessage());
        assertEquals(0, reads.get());
        assertEquals(0, transport.calls);
        open.set(true);
        final var changedByProvider =
                new HttpRasterObservationClient(
                        settings(ENDPOINT, 4096, 1, 1),
                        () -> {
                            open.set(false);
                            return credentials();
                        },
                        transport,
                        HttpRasterObservationClientTest::jdkPolicy,
                        open::get,
                        () -> 0L);
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () -> changedByProvider.observe(request(), new Collector()))
                        .getMessage());
        assertEquals(0, transport.calls);
        open.set(true);
        transport.afterResponse = () -> open.set(false);
        final var sink = new Collector();
        final var during =
                new HttpRasterObservationClient(
                        settings(ENDPOINT, 4096, 1, 1),
                        HttpRasterObservationClientTest::credentials,
                        transport,
                        HttpRasterObservationClientTest::jdkPolicy,
                        open::get,
                        () -> 0L);
        assertThrows(IllegalStateException.class, () -> during.observe(request(), sink));
        assertEquals(1, transport.calls);
        assertEquals(0, sink.trips);
    }

    @Test
    void totalDeadlineRejectsLateResponseBeforeDelivery() {
        final var now = new java.util.concurrent.atomic.AtomicLong();
        final var transport = new FakeClient(200, "{\"Viagens\":[" + TRIP + "]}");
        transport.afterResponse = () -> now.set(Duration.ofSeconds(60).toNanos());
        final var client =
                new HttpRasterObservationClient(
                        settings(ENDPOINT, 4096, 1, 1),
                        HttpRasterObservationClientTest::credentials,
                        transport,
                        HttpRasterObservationClientTest::jdkPolicy,
                        () -> true,
                        now::get);
        final var sink = new Collector();
        assertEquals(
                "RAS_UNIT_DEADLINE",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () -> client.observe(request(), sink))
                        .code());
        assertEquals(1, transport.calls);
        assertEquals(0, sink.trips);
    }

    @Test
    void legacyPublicEntryCannotBypassClosedWindow() {
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        new HttpRasterObservationClient(
                                                null,
                                                () -> {
                                                    throw new AssertionError("NO_SECRET");
                                                }))
                        .getMessage());
    }

    private static final URI ENDPOINT =
            URI.create("https://raster.invalid" + RasterHttpSettings.PATH);
    private static final String TRIP =
            "{\"CodSolicitacao\":\"1\",\"Sequencial\":2,\"ColetasEntregas\":[{\"Tipo\":\"Entrega\"}]}";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdk.httpclient.disableRetryConnect",
                "jdk.httpclient.enableAllMethodRetry",
                "jdk.httpclient.redirects.retrylimit"
            })
    void jdkPolicyMustBeExplicitAtLaunch(final String key) {
        final var values =
                new java.util.HashMap<>(
                        Map.of(
                                "jdk.httpclient.disableRetryConnect", "true",
                                "jdk.httpclient.enableAllMethodRetry", "false",
                                "jdk.httpclient.redirects.retrylimit", "1"));
        HttpRasterObservationClient.requireJdkPolicy(values::get);
        values.remove(key);
        assertEquals(
                "RAS_JDK_RETRY_POLICY_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> HttpRasterObservationClient.requireJdkPolicy(values::get))
                        .getMessage());
        values.put(key, "wrong");
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpRasterObservationClient.requireJdkPolicy(values::get));
    }

    @Test
    void disabledDoesNotResolveCredentialsOrMakeCall() {
        final var calls = new AtomicInteger();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HttpRasterObservationClient.createForSourceWindow(
                                () -> true,
                                RasterHttpSettings::disabled,
                                () -> {
                                    calls.incrementAndGet();
                                    return credentials();
                                }));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://raster.invalid",
                "https://user@raster.invalid",
                "https://raster.invalid/other",
                "https://raster.invalid/?query=1",
                "https://raster.invalid/#fragment"
            })
    void endpointMustBeExactHttpsWithoutAccountOrQuery(final String invalid) {
        assertThrows(
                IllegalArgumentException.class, () -> settings(URI.create(invalid), 1024, 2, 3));
    }

    @Test
    void timeoutVolumeAndExplicitCivilRequestAreBounded() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterHttpSettings(
                                true,
                                ENDPOINT,
                                Duration.ofSeconds(31),
                                100,
                                1,
                                1,
                                ZoneId.of("America/Sao_Paulo")));
        assertThrows(IllegalArgumentException.class, () -> settings(ENDPOINT, 10485761, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> settings(ENDPOINT, 100, 500, 1));
        assertThrows(IllegalArgumentException.class, () -> settings(ENDPOINT, 100, 1, 1001));
        final var day = LocalDate.of(2026, 10, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterObservationRequest(day, day.plusDays(1), "TEST", "T"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterObservationRequest(day, day, "", "T"));
    }

    @Test
    void realFactoryUsesStrictTlsAndNeverRedirects() {
        assertFalse(settings(ENDPOINT, 1024, 2, 3).toString().contains("raster.invalid"));
        assertFalse(credentials().toString().contains("fixture-login"));
        if (!"true".equals(System.getProperty("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry"))
                || !"1".equals(System.getProperty("jdk.httpclient.redirects.retrylimit"))) {
            assertEquals(
                    "RAS_JDK_RETRY_POLICY_REQUIRED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            HttpRasterObservationClient.strictClient(
                                                    settings(ENDPOINT, 1024, 2, 3)))
                            .getMessage());
            return;
        }
        final var client = HttpRasterObservationClient.strictClient(settings(ENDPOINT, 1024, 2, 3));
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals("HTTPS", client.sslParameters().getEndpointIdentificationAlgorithm());
        assertTrue(client.connectTimeout().orElseThrow().compareTo(Duration.ofSeconds(10)) <= 0);
    }

    @Test
    void successfulPostPreservesCandidatesWithoutInventingIdentityOrCompleteness()
            throws Exception {
        final var transport = new FakeClient(200, "{\"result\":{\"Viagens\":[" + TRIP + "]}}");
        final var sink = new Collector();
        final var client = client(transport, 4096, 2, 3);
        final var receipt = client.observe(request(), sink);
        assertEquals(200, receipt.httpStatus());
        assertEquals(1, receipt.calls());
        assertEquals(1, receipt.trips());
        assertEquals(1, receipt.stops());
        assertFalse(receipt.sourceCompletenessProven());
        assertFalse(receipt.canonicalIdentityProven());
        assertEquals(Presence.ABSENT, sink.stop.ordem().presence());
        assertNull(sink.stop.ordem().value());
        assertEquals(1, transport.calls);
        assertEquals("POST", transport.request.method());
        assertEquals(ENDPOINT, transport.request.uri());
        final String body = requestBody(transport.request);
        assertTrue(body.contains("\"DataInicial\":\"2026-10-01\""));
        assertTrue(body.contains("\"DataFinal\":\"2026-10-01\""));
        assertTrue(body.contains("\"TipoRetorno\":\"JSON\""));
        assertTrue(body.contains("\"Login\":\"fixture-login\""));
        assertEquals(
                "RAS_ALREADY_USED",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () -> client.observe(request(), sink))
                        .code());
        assertEquals(1, transport.calls);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 401, 429, 500})
    void rejectedHttpStopsWithoutParserRetryOrBodyDisclosure(final int status) {
        final var transport = new FakeClient(status, "fixture-sensitive-body");
        final var sink = new Collector();
        final var failure =
                assertThrows(
                        HttpRasterObservationClient.Failure.class,
                        () -> client(transport, 4096, 2, 3).observe(request(), sink));
        assertEquals("RAS_HTTP_STATUS", failure.code());
        assertEquals(status, failure.httpStatus());
        assertEquals(1, transport.calls);
        assertEquals(0, sink.trips);
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("fixture-sensitive"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "{\"result\":null}",
                "{\"error\":\"fixture-value\",\"Viagens\":[]}",
                "{\"Viagens\":[],\"result\":[]}",
                "{\"result\":[],\"result\":[]}",
                "{\"result\":[]} false"
            })
    void malformedResponseIsNeverSuccessfulEmptyOrPartial(final String body) {
        final var sink = new Collector();
        final var failure =
                assertThrows(
                        HttpRasterObservationClient.Failure.class,
                        () ->
                                client(new FakeClient(200, body), 4096, 2, 3)
                                        .observe(request(), sink));
        assertEquals("RAS_CONTRACT_INVALID", failure.code());
        assertEquals(0, sink.trips);
        assertNull(failure.getCause());
    }

    @Test
    void allContractAndVolumeChecksPrecedeDeliveryOfAnyObservation() {
        final var sink = new Collector();
        final var invalidTail = "{\"result\":[" + TRIP + ",{}]}";
        assertThrows(
                HttpRasterObservationClient.Failure.class,
                () ->
                        client(new FakeClient(200, invalidTail), 4096, 2, 3)
                                .observe(request(), sink));
        assertEquals(0, sink.trips);
        final var tooMany = "{\"result\":[" + TRIP + "," + TRIP + "]}";
        assertEquals(
                "RAS_OBSERVATION_BOUND",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () ->
                                        client(new FakeClient(200, tooMany), 4096, 1, 3)
                                                .observe(request(), sink))
                        .code());
        assertEquals(0, sink.trips);
        assertEquals(
                "RAS_OBSERVATION_BOUND",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () ->
                                        client(new FakeClient(200, tooMany), 4096, 2, 1)
                                                .observe(request(), sink))
                        .code());
        assertEquals(0, sink.trips);
    }

    @Test
    void bodyHeaderAndObservedByteBoundsAreEnforced() {
        final var transport = new FakeClient(200, "{\"result\":[]}");
        transport.headers = Map.of("Content-Type", List.of("text/plain"));
        assertEquals(
                "RAS_HTTP_MEDIA_TYPE",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () ->
                                        client(transport, 4096, 2, 3)
                                                .observe(request(), new Collector()))
                        .code());
        final var over = new FakeClient(200, "{\"result\":[]}");
        assertEquals(
                "RAS_HTTP_BODY_BOUND",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () -> client(over, 8, 2, 3).observe(request(), new Collector()))
                        .code());
        assertTrue(over.cancelled);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "9,10", "abc", "9999999999999999999999999999999", ""})
    void invalidContentLengthIsSanitized(final String length) {
        final var transport = new FakeClient(200, "{\"result\":[]}");
        transport.headers =
                Map.of(
                        "Content-Type",
                        List.of("application/json"),
                        "Content-Length",
                        List.of(length));
        assertEquals(
                "RAS_HTTP_LENGTH_INVALID",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () ->
                                        client(transport, 4096, 2, 3)
                                                .observe(request(), new Collector()))
                        .code());
    }

    @Test
    void emptyObservationDoesNotDeclareCompleteAndSinkFailureDoesNotExposeValues()
            throws Exception {
        final var receipt =
                client(new FakeClient(200, "{\"result\":[]}"), 4096, 2, 3)
                        .observe(request(), new Collector());
        assertEquals(0, receipt.trips());
        assertFalse(receipt.sourceCompletenessProven());
        final var sink = new Collector();
        sink.fail = true;
        final var failure =
                assertThrows(
                        HttpRasterObservationClient.Failure.class,
                        () ->
                                client(new FakeClient(200, TRIP), 4096, 2, 3)
                                        .observe(request(), sink));
        assertEquals("RAS_SINK_FAILED", failure.code());
        assertNull(failure.getCause());
    }

    @Test
    void transportFailureAndTimeoutCancelWithoutRetryOrDisclosure() {
        final var transport = new FakeClient(200, TRIP);
        transport.failure = new IllegalStateException("fixture-url-and-secret");
        final var failure =
                assertThrows(
                        HttpRasterObservationClient.Failure.class,
                        () -> client(transport, 4096, 2, 3).observe(request(), new Collector()));
        assertEquals("RAS_HTTP_FAILURE", failure.code());
        assertNull(failure.getCause());
        final var timeout = new FakeClient(200, TRIP);
        timeout.pending = true;
        final var settings =
                new RasterHttpSettings(
                        true,
                        ENDPOINT,
                        Duration.ofMillis(20),
                        4096,
                        2,
                        3,
                        ZoneId.of("America/Sao_Paulo"));
        final var bounded =
                new HttpRasterObservationClient(
                        settings,
                        HttpRasterObservationClientTest::credentials,
                        timeout,
                        HttpRasterObservationClientTest::jdkPolicy);
        assertEquals(
                "RAS_HTTP_DEADLINE",
                assertThrows(
                                HttpRasterObservationClient.Failure.class,
                                () -> bounded.observe(request(), new Collector()))
                        .code());
        assertTrue(timeout.future.isCancelled());
        assertEquals(1, timeout.calls);
    }

    @Test
    void secretFailureDoesNotCallTransportOrLeakOriginalFailure() {
        final var transport = new FakeClient(200, TRIP);
        final var client =
                new HttpRasterObservationClient(
                        settings(ENDPOINT, 4096, 2, 3),
                        () -> {
                            throw new IllegalStateException("fixture-secret");
                        },
                        transport,
                        HttpRasterObservationClientTest::jdkPolicy);
        final var failure =
                assertThrows(
                        HttpRasterObservationClient.Failure.class,
                        () -> client.observe(request(), new Collector()));
        assertEquals("RAS_CREDENTIAL_UNAVAILABLE", failure.code());
        assertNull(failure.getCause());
        assertEquals(0, transport.calls);
    }

    private static HttpRasterObservationClient client(
            final FakeClient transport, final int bytes, final int trips, final int stops) {
        return new HttpRasterObservationClient(
                settings(ENDPOINT, bytes, trips, stops),
                HttpRasterObservationClientTest::credentials,
                transport,
                HttpRasterObservationClientTest::jdkPolicy);
    }

    private static String jdkPolicy(final String key) {
        return Map.of(
                        "jdk.httpclient.disableRetryConnect",
                        "true",
                        "jdk.httpclient.enableAllMethodRetry",
                        "false",
                        "jdk.httpclient.redirects.retrylimit",
                        "1")
                .get(key);
    }

    private static RasterHttpSettings settings(
            final URI endpoint, final int bytes, final int trips, final int stops) {
        return new RasterHttpSettings(
                true,
                endpoint,
                Duration.ofSeconds(1),
                bytes,
                trips,
                stops,
                ZoneId.of("America/Sao_Paulo"));
    }

    private static HttpRasterObservationClient.Credentials credentials() {
        return new HttpRasterObservationClient.Credentials("fixture-login", "fixture-password");
    }

    private static RasterObservationRequest request() {
        final var day = LocalDate.of(2026, 10, 1);
        return new RasterObservationRequest(day, day, "SYNTHETIC", "T");
    }

    private static String requestBody(final HttpRequest request) throws Exception {
        final var body = HttpResponse.BodySubscribers.ofByteArray();
        request.bodyPublisher()
                .orElseThrow()
                .subscribe(
                        new Flow.Subscriber<>() {
                            @Override
                            public void onSubscribe(final Flow.Subscription subscription) {
                                body.onSubscribe(subscription);
                            }

                            @Override
                            public void onNext(final ByteBuffer item) {
                                body.onNext(List.of(item));
                            }

                            @Override
                            public void onError(final Throwable failure) {
                                body.onError(failure);
                            }

                            @Override
                            public void onComplete() {
                                body.onComplete();
                            }
                        });
        return new String(body.getBody().toCompletableFuture().get(), StandardCharsets.UTF_8);
    }

    private static final class Collector implements RasterResponseParser.Sink {
        private int trips;
        private RasterStop stop;
        private boolean fail;

        @Override
        public void trip(final int position, final RasterTripObservation value)
                throws SQLException {
            if (fail) {
                throw new SQLException("fixture-business-value");
            }
            trips++;
        }

        @Override
        public void stop(final int parent, final int position, final RasterStop value) {
            stop = value;
        }
    }

    private static final class FakeClient extends HttpClient {
        private final int status;
        private final String body;
        private int calls;
        private HttpRequest request;
        private Map<String, List<String>> headers =
                Map.of("Content-Type", List.of("application/json"));
        private RuntimeException failure;
        private boolean pending;
        private boolean cancelled;
        private CompletableFuture<?> future;
        private Runnable afterResponse = () -> {};

        FakeClient(final int status, final String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest input, final HttpResponse.BodyHandler<T> handler) {
            calls++;
            request = input;
            if (pending) {
                final CompletableFuture<HttpResponse<T>> result = new CompletableFuture<>();
                future = result;
                return result;
            }
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            try {
                final var info =
                        new HttpResponse.ResponseInfo() {
                            @Override
                            public int statusCode() {
                                return status;
                            }

                            @Override
                            public HttpHeaders headers() {
                                return HttpHeaders.of(headers, (name, value) -> true);
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
                            public void cancel() {
                                cancelled = true;
                            }
                        });
                subscriber.onNext(List.of(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8))));
                subscriber.onComplete();
                afterResponse.run();
                return subscriber
                        .getBody()
                        .toCompletableFuture()
                        .thenApply(
                                value ->
                                        new HttpResponse<T>() {
                                            @Override
                                            public int statusCode() {
                                                return status;
                                            }

                                            @Override
                                            public HttpRequest request() {
                                                return input;
                                            }

                                            @Override
                                            public Optional<HttpResponse<T>> previousResponse() {
                                                return Optional.empty();
                                            }

                                            @Override
                                            public HttpHeaders headers() {
                                                return info.headers();
                                            }

                                            @Override
                                            public T body() {
                                                return value;
                                            }

                                            @Override
                                            public Optional<SSLSession> sslSession() {
                                                return Optional.empty();
                                            }

                                            @Override
                                            public URI uri() {
                                                return input.uri();
                                            }

                                            @Override
                                            public Version version() {
                                                return Version.HTTP_1_1;
                                            }
                                        });
            } catch (final RuntimeException rejected) {
                return CompletableFuture.failedFuture(rejected);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest input,
                final HttpResponse.BodyHandler<T> handler,
                final HttpResponse.PushPromiseHandler<T> push) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HttpResponse<T> send(
                final HttpRequest input, final HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
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
            try {
                return SSLContext.getDefault();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            final var p = new SSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            return p;
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
}
