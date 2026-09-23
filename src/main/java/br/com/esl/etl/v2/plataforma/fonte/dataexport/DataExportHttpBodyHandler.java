package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Subscriber com backpressure que nunca aceita mais bytes que o limite configurado. */
final class DataExportHttpBodyHandler implements HttpResponse.BodyHandler<byte[]> {

    private final int templateId;
    private final long maxResponseBytes;

    DataExportHttpBodyHandler(final int templateId, final long maxResponseBytes) {
        this.templateId = templateId;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo responseInfo) {
        Objects.requireNonNull(responseInfo, "Os metadados da resposta são obrigatórios.");
        validateContentLength(responseInfo.headers());
        final boolean retainBody =
                DataExportHttpExecutor.isSuccess(responseInfo.statusCode())
                        && responseInfo.statusCode() != 204;
        return new BoundedBodySubscriber(templateId, maxResponseBytes, retainBody);
    }

    private void validateContentLength(final HttpHeaders headers) {
        Long declaredLength = null;
        for (final String headerValue : headers.allValues("Content-Length")) {
            for (final String candidate : headerValue.split(",", -1)) {
                final String value = candidate.trim();
                if (value.isEmpty()
                        || !value.chars()
                                .allMatch(character -> character >= '0' && character <= '9')) {
                    throw invalidContentLength();
                }
                final long parsed;
                try {
                    parsed = Long.parseLong(value);
                } catch (final NumberFormatException exception) {
                    throw invalidContentLength();
                }
                if (declaredLength != null && declaredLength.longValue() != parsed) {
                    throw invalidContentLength();
                }
                declaredLength = parsed;
                if (parsed > maxResponseBytes) {
                    throw new DataExportResponseLimitExceededException(
                            templateId, maxResponseBytes, parsed);
                }
            }
        }
    }

    private IllegalStateException invalidContentLength() {
        return new IllegalStateException("Data Export retornou Content-Length inválido.");
    }

    private static final class BoundedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

        private static final int COPY_BUFFER_BYTES = 8_192;

        private final int templateId;
        private final long maxResponseBytes;
        private final boolean retainBody;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long receivedBytes;

        private BoundedBodySubscriber(
                final int templateId, final long maxResponseBytes, final boolean retainBody) {
            this.templateId = templateId;
            this.maxResponseBytes = maxResponseBytes;
            this.retainBody = retainBody;
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
            Objects.requireNonNull(buffers, "O lote de buffers HTTP é obrigatório.");
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
            final long nextSize;
            try {
                nextSize = Math.addExact(receivedBytes, buffer.remaining());
            } catch (final ArithmeticException exception) {
                throw new DataExportResponseLimitExceededException(
                        templateId, maxResponseBytes, Long.MAX_VALUE);
            }
            if (nextSize > maxResponseBytes) {
                throw new DataExportResponseLimitExceededException(
                        templateId, maxResponseBytes, nextSize);
            }
            if (!retainBody) {
                buffer.position(buffer.limit());
                receivedBytes = nextSize;
                return;
            }
            final byte[] copyBuffer =
                    new byte[Math.min(COPY_BUFFER_BYTES, Math.max(1, buffer.remaining()))];
            while (buffer.hasRemaining()) {
                final int chunkSize = Math.min(buffer.remaining(), copyBuffer.length);
                buffer.get(copyBuffer, 0, chunkSize);
                output.write(copyBuffer, 0, chunkSize);
            }
            receivedBytes = nextSize;
        }
    }
}
