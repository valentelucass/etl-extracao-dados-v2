package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Rejects metadata and byte overflow before a response can reach the parser. */
final class DataExportObservationBodyHandler implements HttpResponse.BodyHandler<byte[]> {
    private final int maximumBytes;

    DataExportObservationBodyHandler(final int maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(final HttpResponse.ResponseInfo response) {
        final int status = response.statusCode();
        if (status < 200 || status >= 300 || status == 204) {
            throw new HttpDataExportObservationClient.Rejected("OBS_HTTP_STATUS", status);
        }
        final var types = response.headers().allValues("Content-Type");
        if (types.size() != 1
                || !types.get(0).toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;.*)?")) {
            throw new HttpDataExportObservationClient.Rejected("OBS_HTTP_MEDIA_TYPE", status);
        }
        long length = -1;
        for (final String header : response.headers().allValues("Content-Length")) {
            for (final String item : header.split(",", -1)) {
                final String value = item.trim();
                if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    throw new HttpDataExportObservationClient.Rejected(
                            "OBS_HTTP_LENGTH_INVALID", status);
                }
                final long parsed;
                try {
                    parsed = Long.parseLong(value);
                } catch (final NumberFormatException failure) {
                    throw new HttpDataExportObservationClient.Rejected(
                            "OBS_HTTP_LENGTH_INVALID", status);
                }
                if (length >= 0 && length != parsed) {
                    throw new HttpDataExportObservationClient.Rejected(
                            "OBS_HTTP_LENGTH_INVALID", status);
                }
                length = parsed;
                if (length > maximumBytes) {
                    throw new HttpDataExportObservationClient.Rejected(
                            "OBS_HTTP_BODY_BOUND", status);
                }
            }
        }
        return new Subscriber(maximumBytes, length, status);
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final long declared;
        private final int status;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        Subscriber(final int maximum, final long declared, final int status) {
            this.maximum = maximum;
            this.declared = declared;
            this.status = status;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(final Flow.Subscription next) {
            if (subscription != null || body.isDone()) {
                next.cancel();
                return;
            }
            subscription = next;
            next.request(1);
        }

        @Override
        public void onNext(final List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (final ByteBuffer buffer : buffers) {
                if ((long) output.size() + buffer.remaining() > maximum) {
                    reject("OBS_HTTP_BODY_BOUND");
                    return;
                }
                final byte[] chunk = new byte[Math.min(8192, Math.max(1, buffer.remaining()))];
                while (buffer.hasRemaining()) {
                    final int count = Math.min(chunk.length, buffer.remaining());
                    buffer.get(chunk, 0, count);
                    output.write(chunk, 0, count);
                }
            }
            subscription.request(1);
        }

        @Override
        public void onError(final Throwable failure) {
            reject("OBS_HTTP_FAILURE");
        }

        @Override
        public void onComplete() {
            if (!body.isDone()) {
                if (output.size() == 0 || (declared >= 0 && output.size() != declared)) {
                    reject("OBS_HTTP_BODY_BOUND");
                } else {
                    body.complete(output.toByteArray());
                }
            }
        }

        private void reject(final String code) {
            if (subscription != null) {
                subscription.cancel();
            }
            body.completeExceptionally(new HttpDataExportObservationClient.Rejected(code, status));
        }
    }
}
