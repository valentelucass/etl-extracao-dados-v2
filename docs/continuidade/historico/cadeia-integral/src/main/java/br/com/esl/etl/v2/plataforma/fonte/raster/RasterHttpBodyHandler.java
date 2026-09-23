package br.com.esl.etl.v2.plataforma.fonte.raster;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Backpressure and early byte limit follow the existing Data Export HTTP boundary. */
final class RasterHttpBodyHandler implements HttpResponse.BodyHandler<byte[]> {
    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("RAS_HTTP_STATUS");
        }
        if (!response.headers()
                .firstValue("Content-Type")
                .orElse("")
                .toLowerCase(java.util.Locale.ROOT)
                .matches("application/json(?:\\s*;.*)?")) {
            throw new IllegalArgumentException("RAS_HTTP_MEDIA_TYPE");
        }
        if (response.headers().firstValueAsLong("Content-Length").orElse(-1)
                > RasterResponseParser.MAX_BYTES) {
            throw new IllegalArgumentException("RAS_HTTP_BODY_BOUND");
        }
        return new Subscriber();
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final Flow.Subscription next) {
            if (subscription != null) {
                next.cancel();
                return;
            }
            subscription = next;
            subscription.request(1);
        }

        @Override
        public void onNext(final List<ByteBuffer> buffers) {
            try {
                for (final ByteBuffer buffer : buffers) {
                    if ((long) output.size() + buffer.remaining()
                            > RasterResponseParser.MAX_BYTES) {
                        throw new IllegalArgumentException("RAS_HTTP_BODY_BOUND");
                    }
                    final byte[] chunk = new byte[Math.min(8192, Math.max(1, buffer.remaining()))];
                    while (buffer.hasRemaining()) {
                        final int count = Math.min(chunk.length, buffer.remaining());
                        buffer.get(chunk, 0, count);
                        output.write(chunk, 0, count);
                    }
                }
                subscription.request(1);
            } catch (final RuntimeException failure) {
                subscription.cancel();
                body.completeExceptionally(failure);
            }
        }

        @Override
        public void onError(final Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            if (output.size() == 0) {
                body.completeExceptionally(new IllegalArgumentException("RAS_HTTP_BODY_BOUND"));
            } else {
                body.complete(output.toByteArray());
            }
        }
    }
}
