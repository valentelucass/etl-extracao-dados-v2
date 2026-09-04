package br.com.esl.etl.v2.plataforma.fonte.graphql;

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

class GraphQlHttpBodyHandlerTest {

    @Test
    void consumesOneBoundedBatchAtATime() {
        final HttpResponse.BodySubscriber<byte[]> subscriber =
                new GraphQlHttpBodyHandler(GraphQlReadOperation.USERS_SNAPSHOT, 10)
                        .apply(responseInfo(200, Map.of()));
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
                new GraphQlHttpBodyHandler(GraphQlReadOperation.USERS_SNAPSHOT, 3)
                        .apply(responseInfo(200, Map.of()));
        final TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.wrap("four".getBytes(StandardCharsets.UTF_8))));

        final CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () -> subscriber.getBody().toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof GraphQlResponseLimitExceededException);
        assertTrue(subscription.cancelled.get());
    }

    @Test
    void rejectsDeclaredLengthAndDiscardsNonSuccessBodies() {
        final GraphQlHttpBodyHandler handler =
                new GraphQlHttpBodyHandler(GraphQlReadOperation.USERS_SNAPSHOT, 3);
        final GraphQlResponseLimitExceededException oversized =
                assertThrows(
                        GraphQlResponseLimitExceededException.class,
                        () ->
                                handler.apply(
                                        responseInfo(200, Map.of("Content-Length", List.of("4")))));
        assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, oversized.operation());
        assertEquals(3, oversized.maximumBytes());
        assertEquals(4, oversized.observedBytes());

        final HttpResponse.BodySubscriber<byte[]> errorSubscriber =
                handler.apply(responseInfo(429, Map.of()));
        final TestSubscription subscription = new TestSubscription();
        errorSubscriber.onSubscribe(subscription);
        errorSubscriber.onNext(List.of(ByteBuffer.wrap("ok".getBytes(StandardCharsets.UTF_8))));
        errorSubscriber.onComplete();
        assertEquals(0, errorSubscriber.getBody().toCompletableFuture().join().length);

        final HttpResponse.BodySubscriber<byte[]> oversizedErrorSubscriber =
                handler.apply(responseInfo(503, Map.of()));
        final TestSubscription oversizedErrorSubscription = new TestSubscription();
        oversizedErrorSubscriber.onSubscribe(oversizedErrorSubscription);
        oversizedErrorSubscriber.onNext(
                List.of(ByteBuffer.wrap("four".getBytes(StandardCharsets.UTF_8))));
        final CompletionException oversizedError =
                assertThrows(
                        CompletionException.class,
                        () -> oversizedErrorSubscriber.getBody().toCompletableFuture().join());
        assertTrue(oversizedError.getCause() instanceof GraphQlResponseLimitExceededException);
        assertTrue(oversizedErrorSubscription.cancelled.get());

        assertThrows(
                GraphQlResponseLimitExceededException.class,
                () -> handler.apply(responseInfo(400, Map.of("Content-Length", List.of("4")))));
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
