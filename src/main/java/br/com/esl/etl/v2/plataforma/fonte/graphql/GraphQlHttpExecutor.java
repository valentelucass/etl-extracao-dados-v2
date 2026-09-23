package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutScope;
import java.io.IOException;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** Único loop de retry GraphQL, governado pelo mesmo ciclo ESL dos demais workloads. */
final class GraphQlHttpExecutor {

    private static final long COMPLETION_POLL_NANOS = Duration.ofMillis(50).toNanos();
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final double JITTER_RATIO = 0.20d;

    private final HttpClient httpClient;
    private final GraphQlProperties properties;
    private final EslRequestGovernor.Cycle.Workload workload;
    private final Clock clock;
    private final GraphQlJitterSource jitterSource;
    private final GraphQlHttpAttemptObserver attempts;

    GraphQlHttpExecutor(
            final HttpClient httpClient,
            final GraphQlProperties properties,
            final EslRequestGovernor.Cycle.Workload workload,
            final Clock clock,
            final GraphQlJitterSource jitterSource) {
        this(
                httpClient,
                properties,
                workload,
                clock,
                jitterSource,
                GraphQlHttpAttemptObserver.noop());
    }

    GraphQlHttpExecutor(
            final HttpClient httpClient,
            final GraphQlProperties properties,
            final EslRequestGovernor.Cycle.Workload workload,
            final Clock clock,
            final GraphQlJitterSource jitterSource,
            final GraphQlHttpAttemptObserver attempts) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient é obrigatório.");
        this.properties = Objects.requireNonNull(properties, "As propriedades são obrigatórias.");
        this.workload = Objects.requireNonNull(workload, "O workload ESL é obrigatório.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
        this.jitterSource =
                Objects.requireNonNull(jitterSource, "A fonte de jitter é obrigatória.");
        this.attempts = Objects.requireNonNull(attempts, "O observador HTTP é obrigatório.");
    }

    GraphQlHttpResponse execute(
            final GraphQlReadOperation operation,
            final Function<Duration, HttpRequest> requestFactory) {
        Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        Objects.requireNonNull(requestFactory, "A fábrica de request é obrigatória.");
        final int maximumAttempts = properties.retryPolicy().maxAttempts();
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                final GraphQlHttpResponse response;
                Duration retryDelay = null;
                try (EslRequestGovernor.RequestPermit permit = workload.acquire()) {
                    final Duration timeout =
                            minimum(properties.requestTimeout(), permit.remainingTime());
                    final HttpRequest request =
                            Objects.requireNonNull(
                                    requestFactory.apply(timeout),
                                    "A fábrica de request retornou nulo.");
                    final HttpResponse<byte[]> raw = sendBounded(request, operation, permit);
                    response = readResponse(raw, operation);
                    if (isRetryableStatus(response.statusCode())) {
                        final ResolvedRetryDelay resolved =
                                resolveDelay(attempt, response.retryAfter());
                        if (response.statusCode() == 429 || resolved.serverDirected()) {
                            workload.imposeRateLimitEmbargo(resolved.delay());
                        }
                        if (attempt < maximumAttempts) {
                            retryDelay = resolved.delay();
                        }
                    }
                    permit.checkpoint();
                }
                if (retryDelay == null) {
                    return response;
                }
                workload.awaitRetry(retryDelay);
            } catch (final HttpTimeoutException exception) {
                if (attempt == maximumAttempts) {
                    throw new GraphQlUnavailableException(
                            operation,
                            GraphQlUnavailableException.Reason.REQUEST_TIMEOUT,
                            exception);
                }
                workload.awaitRetry(resolveDelay(attempt, Optional.empty()).delay());
            } catch (final IOException exception) {
                if (attempt == maximumAttempts) {
                    throw new GraphQlUnavailableException(
                            operation, GraphQlUnavailableException.Reason.IO, exception);
                }
                workload.awaitRetry(resolveDelay(attempt, Optional.empty()).delay());
            } catch (final ResilienceTimeoutException exception) {
                if (exception.scope() != ResilienceTimeoutScope.REQUEST) {
                    throw exception;
                }
                if (attempt == maximumAttempts) {
                    throw new GraphQlUnavailableException(
                            operation,
                            GraphQlUnavailableException.Reason.REQUEST_TIMEOUT,
                            exception);
                }
                workload.awaitRetry(resolveDelay(attempt, Optional.empty()).delay());
            }
        }
        throw new IllegalStateException("A política de retry GraphQL não terminou.");
    }

    static boolean isSuccess(final int statusCode) {
        return statusCode == 200;
    }

    static boolean isRetryableStatus(final int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode != 501;
    }

    private HttpResponse<byte[]> sendBounded(
            final HttpRequest request,
            final GraphQlReadOperation operation,
            final EslRequestGovernor.RequestPermit permit)
            throws IOException {
        permit.checkpoint();
        final CompletableFuture<HttpResponse<byte[]>> future;
        attempts.beforeAttempt(operation);
        try {
            future =
                    httpClient.sendAsync(
                            request,
                            new GraphQlHttpBodyHandler(operation, properties.maxResponseBytes()));
        } catch (final Error error) {
            throw error;
        } catch (final RuntimeException exception) {
            throw sanitizeRuntime(exception);
        }
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
            throw new ResilienceCancelledException(exception);
        } catch (final ExecutionException exception) {
            throw unwrap(exception.getCause());
        } catch (final CancellationException exception) {
            throw new IOException("A chamada GraphQL foi cancelada antes da resposta.", exception);
        } catch (final RuntimeException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    private IOException unwrap(final Throwable cause) throws IOException {
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw sanitizeRuntime(runtimeException);
        }
        return new IOException("A chamada GraphQL falhou antes da resposta.");
    }

    private RuntimeException sanitizeRuntime(final RuntimeException exception) {
        if (exception instanceof GraphQlResponseLimitExceededException
                || exception instanceof ResilienceCancelledException
                || exception instanceof ResilienceTimeoutException) {
            return exception;
        }
        return new GraphQlTransportException();
    }

    private GraphQlHttpResponse readResponse(
            final HttpResponse<byte[]> response, final GraphQlReadOperation operation) {
        final Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        final boolean jsonContentType =
                response.headers()
                        .firstValue("Content-Type")
                        .map(this::isJsonContentType)
                        .orElse(false);
        if (!isSuccess(response.statusCode()) || response.statusCode() == 204) {
            return new GraphQlHttpResponse(response.statusCode(), "", retryAfter, jsonContentType);
        }
        final byte[] body = response.body();
        if (body.length > properties.maxResponseBytes()) {
            throw new GraphQlResponseLimitExceededException(
                    operation, properties.maxResponseBytes(), body.length);
        }
        return new GraphQlHttpResponse(
                response.statusCode(), decodeUtf8(body), retryAfter, jsonContentType);
    }

    private String decodeUtf8(final byte[] body) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (final CharacterCodingException exception) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_UTF8);
        }
    }

    private ResolvedRetryDelay resolveDelay(
            final int failedAttempt, final Optional<String> retryAfterHeader) {
        final Duration maximum =
                minimum(properties.retryPolicy().maxDelay(), workload.maximumRetryAfter());
        final Duration backoff =
                jittered(
                        properties.retryPolicy().delayAfterFailure(failedAttempt, maximum),
                        maximum);
        final Optional<Duration> server =
                retryAfterHeader.flatMap(value -> parseRetryAfter(value, maximum));
        if (server.isEmpty()) {
            return new ResolvedRetryDelay(backoff, false);
        }
        return new ResolvedRetryDelay(
                backoff.compareTo(server.orElseThrow()) >= 0 ? backoff : server.orElseThrow(),
                true);
    }

    private Optional<Duration> parseRetryAfter(final String raw, final Duration maximum) {
        final String value = raw.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        final Duration delay;
        if (value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            if (value.length() > 32) {
                throw new GraphQlRetryAfterLimitExceededException();
            }
            final BigInteger nanos = new BigInteger(value).multiply(NANOS_PER_SECOND);
            if (nanos.compareTo(BigInteger.valueOf(maximum.toNanos())) > 0) {
                throw new GraphQlRetryAfterLimitExceededException();
            }
            delay = Duration.ofNanos(nanos.longValueExact());
        } else {
            try {
                final Instant now = clock.instant();
                final Instant requested =
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant();
                delay = requested.isAfter(now) ? Duration.between(now, requested) : Duration.ZERO;
            } catch (final DateTimeException exception) {
                return Optional.empty();
            }
            if (delay.compareTo(maximum) > 0) {
                throw new GraphQlRetryAfterLimitExceededException();
            }
        }
        return Optional.of(delay);
    }

    private Duration jittered(final Duration backoff, final Duration maximum) {
        if (backoff.isZero()) {
            return backoff;
        }
        final double sample = jitterSource.sample();
        if (!Double.isFinite(sample) || sample < 0.0d || sample >= 1.0d) {
            throw new IllegalStateException("A fonte de jitter GraphQL é inválida.");
        }
        final long available = maximum.minus(backoff).toNanos();
        final long possible =
                Math.min(available, Math.max(1L, (long) (backoff.toNanos() * JITTER_RATIO)));
        return backoff.plusNanos((long) (possible * sample));
    }

    private static Duration minimum(final Duration first, final Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private boolean isJsonContentType(final String value) {
        final int parameterSeparator = value.indexOf(';');
        final String mediaType =
                (parameterSeparator < 0 ? value : value.substring(0, parameterSeparator)).trim();
        return "application/json".equals(mediaType.toLowerCase(Locale.ROOT));
    }

    private record ResolvedRetryDelay(Duration delay, boolean serverDirected) {

        private ResolvedRetryDelay {
            Objects.requireNonNull(delay, "O atraso de retry é obrigatório.");
        }
    }
}
