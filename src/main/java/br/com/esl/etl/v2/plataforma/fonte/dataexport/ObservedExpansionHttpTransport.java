package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/** Package-only transport primitive, not a source/schema permit or an executable runtime route. */
final class ObservedExpansionHttpTransport {
    private final ObservedExpansionCapturePlan plan;
    private final DataExportTemplate template;
    private final DataExportProperties properties;
    private final DataExportHttpExecutor executor;
    private final DataExportCircuitBreakerRegistry circuits;
    private final java.util.concurrent.atomic.AtomicBoolean sourceStopped;
    private int nextPage = 1;
    private long receivedBytes;
    private boolean closed;

    ObservedExpansionHttpTransport(
            final ObservedExpansionCapturePlan plan,
            final DataExportTemplate template,
            final DataExportProperties properties,
            final DataExportHttpExecutor executor,
            final DataExportCircuitBreakerRegistry circuits,
            final java.util.concurrent.atomic.AtomicBoolean sourceStopped) {
        this.plan = Objects.requireNonNull(plan);
        this.template = Objects.requireNonNull(template);
        this.properties = Objects.requireNonNull(properties);
        this.executor = Objects.requireNonNull(executor);
        this.circuits = Objects.requireNonNull(circuits);
        this.sourceStopped = Objects.requireNonNull(sourceStopped);
        if (template.templateId() != plan.template().templateId()) {
            throw new IllegalArgumentException("OBS_CAPTURE_TEMPLATE_BINDING");
        }
    }

    /** Local P3-only bearer policy. No invalid header value is passed to the JDK builder. */
    static void requireSafeBearer(final DataExportProperties properties) {
        final String authorization = Objects.requireNonNull(properties).authorizationHeaderValue();
        if (authorization.length() <= 7
                || authorization.length() > 16_384
                || !authorization.startsWith("Bearer ")
                || !authorization.substring(7).matches("[A-Za-z0-9._~+/\\-]+=*")) {
            throw new IllegalArgumentException("OBS_CAPTURE_AUTHORIZATION_REJECTED");
        }
    }

    /** Rebuild only closed technical exception fields; never attach original/cause/suppressed. */
    static RuntimeException sanitized(final RuntimeException failure, final int templateId) {
        if (failure instanceof DataExportUnavailableException unavailable) {
            if (unavailable.httpStatus().isPresent()) {
                return new DataExportUnavailableException(
                        templateId,
                        unavailable.httpStatus().orElseThrow(),
                        java.util.Optional.empty());
            }
            return new DataExportUnavailableException(
                    templateId, "falha de comunicação", java.util.Optional.empty());
        }
        if (failure
                instanceof br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException) {
            return new br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException();
        }
        if (failure
                instanceof
                br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException timeout) {
            return new br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException(
                    timeout.scope());
        }
        if (failure
                instanceof
                br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException budget) {
            return new br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException(
                    budget.scope());
        }
        if (failure instanceof DataExportResponseLimitExceededException limit) {
            return new DataExportResponseLimitExceededException(
                    templateId, limit.maxResponseBytes(), limit.observedBytes());
        }
        if (failure instanceof DataExportCircuitOpenException) {
            return new DataExportCircuitOpenException(templateId);
        }
        if (failure instanceof IllegalArgumentException) {
            return new IllegalArgumentException("OBS_CAPTURE_REJECTED");
        }
        return new IllegalStateException("OBS_CAPTURE_EXECUTION_FAILURE");
    }

    /** Future admitted driver must validate schema and stop on projector STRUCTURE_ISSUE. */
    synchronized ObservedExpansionRawPage fetch(final ObservedExpansionCaptureRequest request) {
        Objects.requireNonNull(request);
        if (closed
                || sourceStopped.get()
                || !plan.equals(request.plan())
                || request.page() != nextPage) {
            throw new IllegalStateException("OBS_CAPTURE_TRANSPORT_SEQUENCE");
        }
        final long bound =
                Math.min(
                        properties.maxResponseBytes(),
                        Math.min(
                                plan.limits().maximumPageBytes(),
                                plan.limits().maximumRawBytes() - receivedBytes));
        if (bound < 1) {
            closed = true;
            throw new IllegalStateException("OBS_CAPTURE_TRANSPORT_TOTAL_BOUND");
        }
        requireSafeBearer(properties);
        final RawCapture capture = new RawCapture(request, sourceStopped);
        final DataExportCircuitBreakerRegistry.Permit circuit = circuits.acquire(template);
        try {
            final String requestJson = request.jsonBody().toString();
            executor.executeObservedOnce(
                    template.templateId(),
                    timeout ->
                            HttpRequest.newBuilder(properties.endpointFor(template.templateId()))
                                    .timeout(timeout)
                                    .header("Authorization", properties.authorizationHeaderValue())
                                    .header("Accept", "application/json")
                                    .header("Content-Type", "application/json")
                                    .method(
                                            "GET",
                                            HttpRequest.BodyPublishers.ofString(
                                                    requestJson, StandardCharsets.UTF_8))
                                    .build(),
                    bound,
                    capture);
            final ObservedExpansionRawPage raw = capture.page();
            receivedBytes += raw.byteLength();
            nextPage++;
            if (nextPage > plan.limits().maximumPages()) {
                closed = true;
            }
            circuits.onSuccess(template, circuit);
            return raw;
        } catch (final DataExportUnavailableException failure) {
            closed = true;
            if (failure.httpStatus().orElse(0) == 429) {
                sourceStopped.set(true);
            }
            circuits.onUnavailableFailure(template, circuit);
            throw sanitized(failure, template.templateId());
        } catch (final RuntimeException failure) {
            closed = true;
            circuits.onReachableFailure(template, circuit);
            throw sanitized(failure, template.templateId());
        }
    }

    /** Future driver calls this for INFO 429 or a source-wide revocation. Never reopens. */
    void stopSource() {
        sourceStopped.set(true);
    }

    @Override
    public String toString() {
        return "ObservedExpansionHttpTransport[template="
                + template.templateId()
                + ",nextPage="
                + nextPage
                + ",bytes="
                + receivedBytes
                + ",closed="
                + closed
                + "]";
    }

    /** Only permitted callback implementation; retains one immutable bounded page, with no I/O. */
    static final class RawCapture implements DataExportRawBodyCallback {
        private final ObservedExpansionCaptureRequest request;
        private final java.util.concurrent.atomic.AtomicBoolean sourceStopped;
        private ObservedExpansionRawPage page;

        RawCapture(
                final ObservedExpansionCaptureRequest request,
                final java.util.concurrent.atomic.AtomicBoolean sourceStopped) {
            this.request = Objects.requireNonNull(request);
            this.sourceStopped = Objects.requireNonNull(sourceStopped);
        }

        @Override
        public void checkpoint() {
            if (sourceStopped.get()) {
                throw new IllegalStateException("OBS_CAPTURE_SOURCE_STOPPED");
            }
        }

        @Override
        public void stopSource() {
            sourceStopped.set(true);
        }

        @Override
        public void received(final byte[] completeSubscriberBody, final Instant receivedAt) {
            if (page != null) {
                throw new IllegalStateException("OBS_CAPTURE_CALLBACK_REUSED");
            }
            page = new ObservedExpansionRawPage(request, receivedAt, completeSubscriberBody);
        }

        ObservedExpansionRawPage page() {
            if (page == null) {
                throw new IllegalStateException("OBS_CAPTURE_CALLBACK_MISSING");
            }
            return page;
        }

        @Override
        public String toString() {
            return "RawCapture[received=" + (page != null) + "]";
        }
    }
}
