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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** Executor HTTP interno que aplica limites de corpo e retry aos recursos do Data Export. */
final class DataExportHttpExecutor {

    private static final BoundedStructuredLogger STRUCTURED_LOG =
            new BoundedStructuredLogger(
                    new Slf4jStructuredLogSink(DataExportHttpExecutor.class),
                    1_000_000,
                    268_435_456);
    private static final long COMPLETION_POLL_NANOS = Duration.ofMillis(50).toNanos();

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
                    attemptObserver.beforeAttempt(httpAttempt);
                    final HttpResponse<byte[]> httpResponse =
                            sendBounded(request, templateId, permit);
                    response = readResponse(httpResponse, templateId);
                    permit.checkpoint();
                    if (isRetryableStatus(response.statusCode())
                            && (attempt < maxAttempts
                                    || attemptGovernor.appliesTerminalEmbargo())) {
                        final DataExportRetryDelay retryDelay =
                                retrySchedule.resolve(attempt, response.retryAfter());
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
        permit.checkpoint();
        final CompletableFuture<HttpResponse<byte[]>> future =
                httpClient.sendAsync(
                        request,
                        new DataExportHttpBodyHandler(templateId, properties.maxResponseBytes()));
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
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
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
