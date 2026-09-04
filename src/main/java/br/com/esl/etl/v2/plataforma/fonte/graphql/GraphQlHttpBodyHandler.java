package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Subscriber com backpressure e cancelamento imediato ao ultrapassar o teto de bytes. */
final class GraphQlHttpBodyHandler implements HttpResponse.BodyHandler<byte[]> {

    private final GraphQlReadOperation operation;
    private final long maximumBytes;

    GraphQlHttpBodyHandler(final GraphQlReadOperation operation, final long maximumBytes) {
        this.operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("O limite de resposta GraphQL é inválido.");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo responseInfo) {
        Objects.requireNonNull(responseInfo, "Os metadados HTTP são obrigatórios.");
        final long contentLength =
                responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > maximumBytes) {
            throw new GraphQlResponseLimitExceededException(operation, maximumBytes, contentLength);
        }
        if (!GraphQlHttpExecutor.isSuccess(responseInfo.statusCode())
                || responseInfo.statusCode() == 204) {
            return new BoundedDiscardingSubscriber(operation, maximumBytes);
        }
        return new BoundedSubscriber(operation, maximumBytes);
    }

    private static final class BoundedDiscardingSubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

        private final GraphQlReadOperation operation;
        private final long maximumBytes;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private long receivedBytes;

        private BoundedDiscardingSubscriber(
                final GraphQlReadOperation operation, final long maximumBytes) {
            this.operation = operation;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "A assinatura HTTP é obrigatória.");
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            subscription.request(1L);
        }

        @Override
        public void onNext(final List<ByteBuffer> buffers) {
            Objects.requireNonNull(buffers, "O lote de buffers é obrigatório.");
            try {
                for (final ByteBuffer buffer : buffers) {
                    receivedBytes =
                            Math.addExact(
                                    receivedBytes,
                                    Objects.requireNonNull(buffer, "O buffer HTTP é obrigatório.")
                                            .remaining());
                    if (receivedBytes > maximumBytes) {
                        throw new GraphQlResponseLimitExceededException(
                                operation, maximumBytes, receivedBytes);
                    }
                }
                subscription.request(1L);
            } catch (final RuntimeException exception) {
                subscription.cancel();
                body.completeExceptionally(exception);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(new byte[0]);
        }
    }

    private static final class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private static final int COPY_BUFFER_BYTES = 8_192;

        private final GraphQlReadOperation operation;
        private final long maximumBytes;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long receivedBytes;

        private BoundedSubscriber(final GraphQlReadOperation operation, final long maximumBytes) {
            this.operation = operation;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "A assinatura HTTP é obrigatória.");
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            subscription.request(1L);
        }

        @Override
        public void onNext(final List<ByteBuffer> buffers) {
            Objects.requireNonNull(buffers, "O lote de buffers é obrigatório.");
            try {
                for (final ByteBuffer buffer : buffers) {
                    copy(Objects.requireNonNull(buffer, "O buffer HTTP é obrigatório."));
                }
                if (!body.isDone()) {
                    subscription.request(1L);
                }
            } catch (final RuntimeException exception) {
                subscription.cancel();
                body.completeExceptionally(exception);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }

        private void copy(final ByteBuffer buffer) {
            final long nextSize = Math.addExact(receivedBytes, buffer.remaining());
            if (nextSize > maximumBytes) {
                throw new GraphQlResponseLimitExceededException(operation, maximumBytes, nextSize);
            }
            final byte[] copyBuffer =
                    new byte[Math.min(COPY_BUFFER_BYTES, Math.max(1, buffer.remaining()))];
            while (buffer.hasRemaining()) {
                final int size = Math.min(buffer.remaining(), copyBuffer.length);
                buffer.get(copyBuffer, 0, size);
                output.write(copyBuffer, 0, size);
                receivedBytes += size;
            }
        }
    }
}
