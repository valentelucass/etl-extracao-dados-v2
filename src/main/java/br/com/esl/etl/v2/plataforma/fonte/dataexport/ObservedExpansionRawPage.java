package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.Objects;

/** Immutable bounded received-body container. It does not verify HTTP provenance or admission. */
public final class ObservedExpansionRawPage {
    private final ObservedExpansionCaptureRequest request;
    private final Instant receivedAt;
    private final byte[] body;

    public ObservedExpansionRawPage(
            final ObservedExpansionCaptureRequest request,
            final Instant receivedAt,
            final byte[] receivedBody) {
        this.request = Objects.requireNonNull(request);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        Objects.requireNonNull(receivedBody);
        if (receivedBody.length < 1
                || receivedBody.length > request.plan().limits().maximumPageBytes()) {
            throw new IllegalArgumentException("OBS_CAPTURE_RAW_PAGE_BOUND");
        }
        this.body = receivedBody.clone();
    }

    public ObservedExpansionCaptureRequest request() {
        return request;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public int byteLength() {
        return body.length;
    }

    public byte[] body() {
        return body.clone();
    }

    @Override
    public String toString() {
        return "ObservedExpansionRawPage[page=" + request.page() + ",bytes=" + body.length + "]";
    }
}
