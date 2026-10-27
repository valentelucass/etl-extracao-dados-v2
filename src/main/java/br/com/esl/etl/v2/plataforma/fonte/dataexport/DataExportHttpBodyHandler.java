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
    private final boolean observedRaw;
    private final DataExportRawBodyCallback observedCallback;

    DataExportHttpBodyHandler(final int templateId, final long maxResponseBytes) {
        this(templateId, maxResponseBytes, false);
    }

    DataExportHttpBodyHandler(
            final int templateId, final long maxResponseBytes, final boolean observedRaw) {
        this(templateId, maxResponseBytes, observedRaw, null);
    }

    /** Closed observed route: status is recorded before ANY header/body validation can fail. */
    DataExportHttpBodyHandler(
            final int templateId,
            final long maxResponseBytes,
            final DataExportRawBodyCallback observedCallback) {
        this(templateId, maxResponseBytes, true, Objects.requireNonNull(observedCallback));
    }

    private DataExportHttpBodyHandler(
            final int templateId,
            final long maxResponseBytes,
            final boolean observedRaw,
            final DataExportRawBodyCallback observedCallback) {
        this.templateId = templateId;
        this.maxResponseBytes = maxResponseBytes;
        this.observedRaw = observedRaw;
        this.observedCallback = observedCallback;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo responseInfo) {
        Objects.requireNonNull(responseInfo, "Os metadados da resposta são obrigatórios.");
        if (observedCallback != null && responseInfo.statusCode() == 429) {
            observedCallback.stopSource();
        }
        final Long declaredLength = validateContentLength(responseInfo.headers());
        if (observedRaw && responseInfo.statusCode() == 200) {
            validateObservedHeaders(responseInfo.headers());
        }
        final boolean retainBody =
                observedRaw
                        ? responseInfo.statusCode() == 200
                        : DataExportHttpExecutor.isSuccess(responseInfo.statusCode())
                                && responseInfo.statusCode() != 204;
        return new BoundedBodySubscriber(
                templateId, maxResponseBytes, retainBody, observedRaw ? declaredLength : null);
    }

    private Long validateContentLength(final HttpHeaders headers) {
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
        return declaredLength;
    }

    private static void validateObservedHeaders(final HttpHeaders headers) {
        final List<String> types = headers.allValues("Content-Type");
        if (types.size() != 1
                || !types.get(0)
                        .strip()
                        .matches(
                                "(?i)application/json(?:\\s*;\\s*charset\\s*=\\s*(?:utf-8|\"utf-8\"))?")) {
            throw new IllegalStateException("OBS_CAPTURE_CONTENT_TYPE");
        }
        final List<String> encodings = headers.allValues("Content-Encoding");
        if (!encodings.isEmpty()
                && (encodings.size() != 1
                        || !encodings.get(0).strip().equalsIgnoreCase("identity"))) {
            throw new IllegalStateException("OBS_CAPTURE_CONTENT_ENCODING");
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
        private final Long expectedLength;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long receivedBytes;

        private BoundedBodySubscriber(
                final int templateId,
                final long maxResponseBytes,
                final boolean retainBody,
                final Long expectedLength) {
            this.templateId = templateId;
            this.maxResponseBytes = maxResponseBytes;
            this.retainBody = retainBody;
            this.expectedLength = expectedLength;
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
            if (expectedLength != null && receivedBytes != expectedLength.longValue()) {
                body.completeExceptionally(
                        new IllegalStateException("OBS_CAPTURE_INCOMPLETE_BODY"));
                return;
            }
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
