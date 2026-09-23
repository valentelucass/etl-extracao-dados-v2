package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.math.BigInteger;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/** Resolve backoff, jitter e Retry-After sem ultrapassar o teto local. */
final class DataExportRetrySchedule {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final double JITTER_RATIO = 0.20d;

    private final DataExportRetryPolicy policy;
    private final Duration maximumDelay;
    private final Clock clock;
    private final DataExportJitterSource jitterSource;

    DataExportRetrySchedule(
            final DataExportRetryPolicy policy,
            final Clock clock,
            final DataExportJitterSource jitterSource) {
        this(policy, clock, jitterSource, policy.maxDelay());
    }

    DataExportRetrySchedule(
            final DataExportRetryPolicy policy,
            final Clock clock,
            final DataExportJitterSource jitterSource,
            final Duration maximumRetryAfter) {
        this.policy = Objects.requireNonNull(policy, "A política de retry é obrigatória.");
        this.clock = Objects.requireNonNull(clock, "O relógio de Retry-After é obrigatório.");
        this.jitterSource =
                Objects.requireNonNull(jitterSource, "A fonte de jitter é obrigatória.");
        Objects.requireNonNull(maximumRetryAfter, "O teto de Retry-After é obrigatório.");
        if (maximumRetryAfter.isNegative()) {
            throw new IllegalArgumentException("O teto de Retry-After não pode ser negativo.");
        }
        this.maximumDelay =
                policy.maxDelay().compareTo(maximumRetryAfter) <= 0
                        ? policy.maxDelay()
                        : maximumRetryAfter;
    }

    DataExportRetryDelay resolve(final int failedAttempt, final Optional<String> retryAfterHeader) {
        Objects.requireNonNull(retryAfterHeader, "O Retry-After observado é obrigatório.");
        final Duration policyBackoff = policy.delayAfterFailure(failedAttempt);
        final Duration backoff =
                jitteredBackoff(
                        policyBackoff.compareTo(maximumDelay) <= 0 ? policyBackoff : maximumDelay);
        final Optional<DataExportRetryDelay> retryAfter =
                retryAfterHeader.flatMap(this::parseRetryAfter);
        if (retryAfter.isEmpty()) {
            return new DataExportRetryDelay(backoff, DataExportRetryDelaySource.BACKOFF);
        }
        if (backoff.compareTo(retryAfter.orElseThrow().duration()) >= 0) {
            return new DataExportRetryDelay(backoff, DataExportRetryDelaySource.BACKOFF, true);
        }
        return retryAfter.orElseThrow();
    }

    Instant observedAt() {
        return clock.instant();
    }

    private Optional<DataExportRetryDelay> parseRetryAfter(final String rawHeader) {
        final String value = rawHeader.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            return Optional.of(
                    checked(
                            durationFromDeltaSeconds(value),
                            DataExportRetryDelaySource.RETRY_AFTER_DELTA));
        }
        try {
            final Instant requestedAt =
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            final Instant now = clock.instant();
            final Duration delay =
                    requestedAt.isAfter(now) ? Duration.between(now, requestedAt) : Duration.ZERO;
            return Optional.of(checked(delay, DataExportRetryDelaySource.RETRY_AFTER_DATE));
        } catch (final DateTimeException exception) {
            return Optional.empty();
        }
    }

    private Duration durationFromDeltaSeconds(final String value) {
        final BigInteger requestedNanos = new BigInteger(value).multiply(NANOS_PER_SECOND);
        final BigInteger maximumNanos = BigInteger.valueOf(maximumDelay.toNanos());
        if (requestedNanos.compareTo(maximumNanos) > 0) {
            throw new DataExportRetryAfterLimitExceededException();
        }
        return Duration.ofNanos(requestedNanos.longValueExact());
    }

    private DataExportRetryDelay checked(
            final Duration delay, final DataExportRetryDelaySource source) {
        if (delay.compareTo(maximumDelay) > 0) {
            throw new DataExportRetryAfterLimitExceededException();
        }
        return new DataExportRetryDelay(delay, source);
    }

    private Duration jitteredBackoff(final Duration backoff) {
        if (backoff.isZero()) {
            return backoff;
        }
        final double sample = jitterSource.sample();
        if (!Double.isFinite(sample) || sample < 0.0d || sample >= 1.0d) {
            throw new IllegalStateException("A fonte de jitter retornou valor inválido.");
        }
        final long availableNanos = maximumDelay.minus(backoff).toNanos();
        final long maximumJitterNanos =
                Math.min(availableNanos, Math.max(1L, (long) (backoff.toNanos() * JITTER_RATIO)));
        final long jitterNanos = (long) (maximumJitterNanos * sample);
        return backoff.plusNanos(jitterNanos);
    }
}
