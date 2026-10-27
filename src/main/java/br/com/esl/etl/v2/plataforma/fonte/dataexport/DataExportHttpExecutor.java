package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.observabilidade.BoundedStructuredLogger;
import br.com.esl.etl.v2.plataforma.observabilidade.LogSeverity;
import br.com.esl.etl.v2.plataforma.observabilidade.Slf4jStructuredLogSink;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredCorrelationContext;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredLogEvent;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Executor HTTP interno que aplica limites de corpo e retry aos recursos do Data Export. */
final class DataExportHttpExecutor {

    private static final BoundedStructuredLogger STRUCTURED_LOG =
            new BoundedStructuredLogger(
                    new Slf4jStructuredLogSink(DataExportHttpExecutor.class),
                    1_000_000,
                    268_435_456);
    private static final long COMPLETION_POLL_NANOS = Duration.ofMillis(50).toNanos();
    private static final Pattern JSON_MEDIA_TYPE =
            Pattern.compile("application/json", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final DataExportProperties properties;
    private final DataExportHttpAttemptObserver attemptObserver;
    private final DataExportHttpAttemptGovernor attemptGovernor;
    private final DataExportRetrySchedule retrySchedule;

    DataExportHttpExecutor(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final DataExportSleeper sleeper) {
        this(httpClient, properties, sleeper, DataExportHttpAttemptObserver.noop());
    }

    DataExportHttpExecutor(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final DataExportSleeper sleeper,
            final DataExportHttpAttemptObserver attemptObserver) {
        this(
                httpClient,
                properties,
                attemptObserver,
                DataExportHttpAttemptGovernor.ungoverned(
                        properties.requestTimeout(), properties.retryPolicy().maxDelay(), sleeper),
                new DataExportRetrySchedule(
                        properties.retryPolicy(),
                        Clock.systemUTC(),
                        DataExportJitterSource.threadLocal()));
    }

    DataExportHttpExecutor(
            final HttpClient httpClient,
            final DataExportProperties properties,
            final DataExportHttpAttemptObserver attemptObserver,
            final DataExportHttpAttemptGovernor attemptGovernor,
            final DataExportRetrySchedule retrySchedule) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient é obrigatório.");
        this.properties =
                Objects.requireNonNull(properties, "As propriedades Data Export são obrigatórias.");
        this.attemptObserver =
                Objects.requireNonNull(attemptObserver, "O observador de tentativa é obrigatório.");
        this.attemptGovernor =
                Objects.requireNonNull(attemptGovernor, "O governor de tentativa é obrigatório.");
        this.retrySchedule =
                Objects.requireNonNull(retrySchedule, "O agendamento de retry é obrigatório.");
    }

    DataExportHttpResponse executeWithRetry(
            final int templateId,
            final String operation,
            final Function<Duration, HttpRequest> requestFactory) {
        return executeWithRetryInternal(templateId, operation, requestFactory, Optional.empty());
    }

    /** P3 INFO uses the normal parser/executor but observes status before header/body errors. */
    DataExportHttpResponse executeObservedInfoOnce(
            final int templateId,
            final Function<Duration, HttpRequest> requestFactory,
            final DataExportRawBodyCallback callback) {
        try {
            ObservedExpansionHttpTransport.requireSafeBearer(properties);
            if (properties.retryPolicy().maxAttempts() != 1
                    || properties.preferredTransport() != DataExportTransport.GET_WITH_BODY
                    || properties.requestTimeout().compareTo(Duration.ofSeconds(30)) > 0
                    || httpClient.followRedirects() != HttpClient.Redirect.NEVER
                    || !"https".equalsIgnoreCase(properties.baseUri().getScheme())) {
                throw new IllegalArgumentException("OBS_CAPTURE_INFO_POLICY");
            }
            return executeWithRetryInternal(
                    templateId,
                    "info",
                    requestFactory,
                    Optional.of(Objects.requireNonNull(callback)));
        } catch (final RuntimeException failure) {
            throw ObservedExpansionHttpTransport.sanitized(failure, templateId);
        }
    }

    private DataExportHttpResponse executeWithRetryInternal(
            final int templateId,
            final String operation,
            final Function<Duration, HttpRequest> requestFactory,
            final Optional<DataExportRawBodyCallback> statusObserver) {
        Objects.requireNonNull(operation, "A operação HTTP é obrigatória.");
        Objects.requireNonNull(requestFactory, "A fábrica de requisição HTTP é obrigatória.");
        final int maxAttempts = properties.retryPolicy().maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            final DataExportHttpAttempt httpAttempt =
                    new DataExportHttpAttempt(templateId, operation);
            try {
                final DataExportHttpResponse response;
                DataExportRetryDelay plannedRetry = null;
                try (DataExportHttpAttemptGovernor.AttemptPermit permit =
                        attemptGovernor.acquire(httpAttempt)) {
                    final Duration timeout =
                            minimum(properties.requestTimeout(), permit.remainingTime());
                    final HttpRequest request =
                            Objects.requireNonNull(
                                    requestFactory.apply(timeout),
                                    "A fábrica de requisição HTTP retornou nulo.");
                    statusObserver.ifPresent(DataExportRawBodyCallback::checkpoint);
                    attemptObserver.beforeAttempt(httpAttempt);
                    final HttpResponse<byte[]> httpResponse =
                            statusObserver.isPresent()
                                    ? sendBounded(
                                            request,
                                            templateId,
                                            permit,
                                            new DataExportHttpBodyHandler(
                                                    templateId,
                                                    Math.min(
                                                            properties.maxResponseBytes(),
                                                            8L * 1024 * 1024),
                                                    statusObserver.orElseThrow()))
                                    : sendBounded(request, templateId, permit);
                    response = readResponse(httpResponse, templateId);
                    permit.checkpoint();
                    if (isRetryableStatus(response.statusCode())
                            && (attempt < maxAttempts
                                    || attemptGovernor.appliesTerminalEmbargo())) {
                        final DataExportRetryDelay retryDelay;
                        try {
                            retryDelay = retrySchedule.resolve(attempt, response.retryAfter());
                        } catch (final DataExportRetryAfterLimitExceededException exception) {
                            // Preserve the observed status without copying the rejected header.
                            exception.initCause(
                                    new DataExportUnavailableException(
                                            templateId, response.statusCode(), Optional.empty()));
                            throw exception;
                        }
                        if (response.statusCode() == 429 || retryDelay.serverDirected()) {
                            attemptGovernor.imposeRateLimitEmbargo(retryDelay.duration());
                        }
                        plannedRetry = attempt < maxAttempts ? retryDelay : null;
                    }
                }
                if (plannedRetry == null) {
                    return response;
                }
                waitForRetry(
                        attempt,
                        response.statusCode() == 429 ? "RATE_LIMIT" : "HTTP_TRANSIENT",
                        plannedRetry);
            } catch (final IOException exception) {
                if (attempt == maxAttempts) {
                    throw new DataExportUnavailableException(
                            templateId, "falha de comunicação", exception);
                }
                waitForRetry(
                        attempt, "IO_TRANSIENT", retrySchedule.resolve(attempt, Optional.empty()));
            }
        }
        throw new IllegalStateException(
                "A política de retry Data Export não produziu uma resposta.");
    }

    /** Internal primitive only: no authority/schema admission, no retry and no decoded body. */
    void executeObservedOnce(
            final int templateId,
            final Function<Duration, HttpRequest> requestFactory,
            final long maximumBytes,
            final DataExportRawBodyCallback callback) {
        Objects.requireNonNull(requestFactory);
        Objects.requireNonNull(callback);
        ObservedExpansionHttpTransport.requireSafeBearer(properties);
        if (properties.retryPolicy().maxAttempts() != 1
                || properties.preferredTransport() != DataExportTransport.GET_WITH_BODY
                || properties.requestTimeout().compareTo(Duration.ofSeconds(30)) > 0
                || httpClient.followRedirects() != HttpClient.Redirect.NEVER
                || !"https".equalsIgnoreCase(properties.baseUri().getScheme())
                || maximumBytes < 1
                || maximumBytes > 8L * 1024 * 1024
                || maximumBytes > properties.maxResponseBytes()) {
            throw new IllegalArgumentException("OBS_CAPTURE_HTTP_POLICY");
        }
        callback.checkpoint();
        final DataExportHttpAttempt attempt =
                new DataExportHttpAttempt(templateId, "observed-data-GET_WITH_BODY");
        try (DataExportHttpAttemptGovernor.AttemptPermit permit =
                attemptGovernor.acquire(attempt)) {
            final Duration timeout = minimum(properties.requestTimeout(), permit.remainingTime());
            final HttpRequest request = Objects.requireNonNull(requestFactory.apply(timeout));
            if (!properties.endpointFor(templateId).equals(request.uri())
                    || !"GET".equals(request.method())
                    || request.bodyPublisher().isEmpty()
                    || request.timeout().isEmpty()
                    || request.timeout().orElseThrow().compareTo(timeout) > 0) {
                throw new IllegalArgumentException("OBS_CAPTURE_HTTP_REQUEST");
            }
            callback.checkpoint();
            attemptObserver.beforeAttempt(attempt);
            final HttpResponse<byte[]> response =
                    sendBounded(
                            request,
                            templateId,
                            permit,
                            new DataExportHttpBodyHandler(templateId, maximumBytes, callback));
            // Defensive second observation; the BodyHandler already stops on ResponseInfo 429.
            if (response.statusCode() == 429) {
                callback.stopSource();
            }
            permit.checkpoint();
            if (response.statusCode() != 200) {
                if (isRetryableStatus(response.statusCode())) {
                    if (attemptGovernor.appliesTerminalEmbargo()) {
                        final DataExportRetryDelay delay =
                                retrySchedule.resolve(
                                        1, response.headers().firstValue("Retry-After"));
                        if (response.statusCode() == 429 || delay.serverDirected()) {
                            attemptGovernor.imposeRateLimitEmbargo(delay.duration());
                        }
                    }
                    throw new DataExportUnavailableException(
                            templateId, response.statusCode(), Optional.empty());
                }
                throw new IllegalStateException("OBS_CAPTURE_HTTP_STATUS_" + response.statusCode());
            }
            if (response.body().length < 1 || response.body().length > maximumBytes) {
                throw new IllegalStateException("OBS_CAPTURE_HTTP_BODY_BOUND");
            }
            // Callback receives the complete bounded subscriber bytes, before ANY UTF-8 decode.
            callback.checkpoint();
            callback.received(response.body(), observedAt());
            permit.checkpoint();
        } catch (final IOException failure) {
            // The exception may contain URI/provider text; do not attach it to the capture result.
            throw new DataExportUnavailableException(
                    templateId, "falha de comunicação", Optional.empty());
        } catch (final RuntimeException failure) {
            throw ObservedExpansionHttpTransport.sanitized(failure, templateId);
        }
    }

    static boolean isSuccess(final int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    static boolean isRetryableStatus(final int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode != 501);
    }

    java.time.Instant observedAt() {
        return retrySchedule.observedAt();
    }

    private HttpResponse<byte[]> sendBounded(
            final HttpRequest request,
            final int templateId,
            final DataExportHttpAttemptGovernor.AttemptPermit permit)
            throws IOException {
        return sendBounded(
                request,
                templateId,
                permit,
                new DataExportHttpBodyHandler(templateId, properties.maxResponseBytes()));
    }

    private HttpResponse<byte[]> sendBounded(
            final HttpRequest request,
            final int templateId,
            final DataExportHttpAttemptGovernor.AttemptPermit permit,
            final DataExportHttpBodyHandler bodyHandler)
            throws IOException {
        permit.checkpoint();
        final CompletableFuture<HttpResponse<byte[]>> future =
                httpClient.sendAsync(request, bodyHandler);
        try {
            while (true) {
                final long waitNanos =
                        Math.min(permit.remainingTime().toNanos(), COMPLETION_POLL_NANOS);
                try {
                    return future.get(waitNanos, TimeUnit.NANOSECONDS);
                } catch (final TimeoutException exception) {
                    permit.checkpoint();
                }
            }
        } catch (final InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException(
                    exception);
        } catch (final ExecutionException exception) {
            throw unwrapHttpFailure(exception.getCause());
        } catch (final CancellationException exception) {
            throw new IOException("A chamada HTTP foi cancelada antes da resposta.", exception);
        } catch (final RuntimeException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    private IOException unwrapHttpFailure(final Throwable cause) throws IOException {
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new IOException("A chamada HTTP falhou antes da resposta.", cause);
    }

    private DataExportHttpResponse readResponse(
            final HttpResponse<byte[]> response, final int templateId) {
        final Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        final boolean jsonContentTypeDeclared = declaresJsonContentType(response);
        if (!isSuccess(response.statusCode()) || response.statusCode() == 204) {
            return new DataExportHttpResponse(
                    response.statusCode(), "", retryAfter, jsonContentTypeDeclared);
        }
        validateBodyLength(response.body(), templateId);
        return new DataExportHttpResponse(
                response.statusCode(),
                decodeUtf8(response.body(), templateId),
                retryAfter,
                jsonContentTypeDeclared);
    }

    private String decodeUtf8(final byte[] body, final int templateId) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (final CharacterCodingException exception) {
            throw new IllegalStateException(
                    "Data Export retornou UTF-8 inválido para o template " + templateId + ".");
        }
    }

    private boolean declaresJsonContentType(final HttpResponse<byte[]> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(DataExportHttpExecutor::isJsonContentType)
                .orElse(false);
    }

    static boolean isJsonContentType(final String value) {
        final int parameterSeparator = value.indexOf(';');
        final String mediaType =
                (parameterSeparator < 0 ? value : value.substring(0, parameterSeparator)).trim();
        return JSON_MEDIA_TYPE.matcher(mediaType).matches();
    }

    private void validateBodyLength(final byte[] body, final int templateId) {
        if (body.length > properties.maxResponseBytes()) {
            throw new DataExportResponseLimitExceededException(
                    templateId, properties.maxResponseBytes(), body.length);
        }
    }

    private void waitForRetry(
            final int failedAttempt,
            final String outcomeCode,
            final DataExportRetryDelay retryDelay) {
        final Duration delay = retryDelay.duration();
        STRUCTURED_LOG.write(
                new StructuredLogEvent(
                        LogSeverity.WARN,
                        "HTTP_RETRY_SCHEDULED",
                        "DATA_EXPORT_HTTP",
                        outcomeCode,
                        StructuredCorrelationContext.currentOrTechnicalScope("DATA_EXPORT_HTTP"),
                        failedAttempt,
                        retrySchedule.observedAt()));
        attemptGovernor.awaitRetry(delay);
    }

    private Duration minimum(final Duration first, final Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
