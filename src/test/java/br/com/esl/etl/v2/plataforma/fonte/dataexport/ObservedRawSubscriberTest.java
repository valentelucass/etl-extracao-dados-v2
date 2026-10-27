package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObservedRawSubscriberTest {
    @ParameterizedTest
    @ValueSource(strings = {"gzip", "identity, identity", "br"})
    void refusesEncodingBeforeBodyRetention(final String encoding) {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportHttpBodyHandler(10633, 16, true)
                                .apply(
                                        info(
                                                Map.of(
                                                        "Content-Type",
                                                        List.of("application/json"),
                                                        "Content-Encoding",
                                                        List.of(encoding)))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"text/plain", "application/json; charset=latin1", "application/json; x=1"})
    void refusesUnsupportedContentType(final String type) {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportHttpBodyHandler(10633, 16, true)
                                .apply(info(Map.of("Content-Type", List.of(type)))));
    }

    @Test
    void refusesMissingAndAmbiguousHeaders() {
        assertThrows(
                IllegalStateException.class,
                () -> new DataExportHttpBodyHandler(10633, 16, true).apply(info(Map.of())));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportHttpBodyHandler(10633, 16, true)
                                .apply(
                                        info(
                                                Map.of(
                                                        "Content-Type",
                                                        List.of(
                                                                "application/json",
                                                                "application/json")))));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportHttpBodyHandler(10633, 16, true)
                                .apply(
                                        info(
                                                Map.of(
                                                        "Content-Type",
                                                        List.of("application/json"),
                                                        "Content-Encoding",
                                                        List.of("identity", "identity")))));
    }

    @Test
    void identityAndQuotedUtf8PreserveExactBytes() {
        final var s =
                new DataExportHttpBodyHandler(10633, 16, true)
                        .apply(
                                info(
                                        Map.of(
                                                "Content-Type",
                                                List.of("application/json; charset=\"UTF-8\""),
                                                "Content-Encoding",
                                                List.of("identity"),
                                                "Content-Length",
                                                List.of("3"))));
        subscribe(s);
        s.onNext(List.of(ByteBuffer.wrap(new byte[] {'[', ' ', ']'})));
        s.onComplete();
        assertArrayEquals(new byte[] {'[', ' ', ']'}, s.getBody().toCompletableFuture().join());
    }

    @Test
    void declaredLengthMismatchCannotProduceCompletePage() {
        final var s =
                new DataExportHttpBodyHandler(10633, 16, true)
                        .apply(
                                info(
                                        Map.of(
                                                "Content-Type",
                                                List.of("application/json"),
                                                "Content-Length",
                                                List.of("4"))));
        subscribe(s);
        s.onNext(List.of(ByteBuffer.wrap(new byte[] {'[', ']'})));
        s.onComplete();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> s.getBody().toCompletableFuture().join());
    }

    @Test
    void excessBytesCancelWithoutRetainingTruncatedSuccess() {
        final var s =
                new DataExportHttpBodyHandler(10633, 2, true)
                        .apply(info(Map.of("Content-Type", List.of("application/json"))));
        final var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        s.onSubscribe(
                new Flow.Subscription() {
                    @Override
                    public void request(final long n) {
                        assertEquals(1, n);
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                    }
                });
        s.onNext(List.of(ByteBuffer.wrap(new byte[] {'[', ' ', ']'})));
        s.onComplete();
        assertTrue(cancelled.get());
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> s.getBody().toCompletableFuture().join());
    }

    @Test
    void noContentLengthDoesNotTurnStreamErrorIntoCompleteRaw() {
        final var s =
                new DataExportHttpBodyHandler(10633, 16, true)
                        .apply(info(Map.of("Content-Type", List.of("application/json"))));
        subscribe(s);
        s.onNext(List.of(ByteBuffer.wrap(new byte[] {'['})));
        s.onError(new java.io.IOException("synthetic"));
        s.onComplete();
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> s.getBody().toCompletableFuture().join());
    }

    @Test
    void legacyHandlerKeepsEarlierContentTypeAndLengthBehavior() {
        final var s =
                new DataExportHttpBodyHandler(10633, 16)
                        .apply(
                                info(
                                        Map.of(
                                                "Content-Type",
                                                List.of("text/plain"),
                                                "Content-Encoding",
                                                List.of("gzip"),
                                                "Content-Length",
                                                List.of("4"))));
        subscribe(s);
        s.onNext(List.of(ByteBuffer.wrap(new byte[] {'[', ']'})));
        s.onComplete();
        assertArrayEquals(new byte[] {'[', ']'}, s.getBody().toCompletableFuture().join());
    }

    private static void subscribe(final HttpResponse.BodySubscriber<byte[]> s) {
        s.onSubscribe(
                new Flow.Subscription() {
                    @Override
                    public void request(final long n) {}

                    @Override
                    public void cancel() {}
                });
    }

    private static HttpResponse.ResponseInfo info(final Map<String, List<String>> headers) {
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (a, b) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
