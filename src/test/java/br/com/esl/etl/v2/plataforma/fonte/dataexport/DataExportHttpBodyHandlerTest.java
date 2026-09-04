package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DataExportHttpBodyHandlerTest {

    @Test
    void consumesOneBoundedBatchAtATime() {
        final HttpResponse.BodySubscriber<byte[]> subscriber =
                new DataExportHttpBodyHandler(1, 10).apply(responseInfo(200, Map.of()));
        final TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(
                List.of(
                        ByteBuffer.wrap("ab".getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.wrap("cd".getBytes(StandardCharsets.UTF_8))));
        subscriber.onComplete();

        assertArrayEquals(
                "abcd".getBytes(StandardCharsets.UTF_8),
                subscriber.getBody().toCompletableFuture().join());
        assertEquals(2L, subscription.requests);
    }

    @Test
    void cancelsAsSoonAsObservedBytesCrossTheLimit() {
        final HttpResponse.BodySubscriber<byte[]> subscriber =
                new DataExportHttpBodyHandler(1, 3).apply(responseInfo(200, Map.of()));
        final TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.wrap("four".getBytes(StandardCharsets.UTF_8))));

        final CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () -> subscriber.getBody().toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof DataExportResponseLimitExceededException);
        assertTrue(subscription.cancelled.get());
    }

    @Test
    void rejectsOversizedDeclaredLengthBeforeReadingAndDiscardsErrorBodies() {
        final DataExportHttpBodyHandler handler = new DataExportHttpBodyHandler(1, 3);
        assertThrows(
                DataExportResponseLimitExceededException.class,
                () -> handler.apply(responseInfo(200, Map.of("Content-Length", List.of("4")))));

        final HttpResponse.BodySubscriber<byte[]> errorSubscriber =
                handler.apply(responseInfo(429, Map.of()));
        final TestSubscription subscription = new TestSubscription();
        errorSubscriber.onSubscribe(subscription);
        errorSubscriber.onNext(
                List.of(ByteBuffer.wrap("ignored".getBytes(StandardCharsets.UTF_8))));
        errorSubscriber.onComplete();
        assertEquals(0, errorSubscriber.getBody().toCompletableFuture().join().length);
    }

    private static HttpResponse.ResponseInfo responseInfo(
            final int status, final Map<String, List<String>> headers) {
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (ignoredName, ignoredValue) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class TestSubscription implements Flow.Subscription {

        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long requests;

        @Override
        public void request(final long number) {
            requests += number;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
