package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CircuitBreakingDataExportGatewayTest {

    @Test
    void opensAfterUnavailableFailuresAndAllowsOneProbeAfterCooldown() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T15:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    if (calls.incrementAndGet() <= 2) {
                        throw new DataExportUnavailableException(
                                request.template().templateId(), "HTTP 503");
                    }
                    return emptyPage();
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofSeconds(30)),
                        clock);

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportCircuitOpenException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertEquals(2, calls.get());

        clock.advance(Duration.ofSeconds(30));

        assertEquals(0, gateway.fetch(request(DataExportTemplate.COLETAS)).records().size());
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotOpenForContractFailures() {
        final AtomicInteger calls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("Filtro inválido.");
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(1, Duration.ofMinutes(1)),
                        Clock.systemUTC());

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));

        assertEquals(2, calls.get());
    }

    @Test
    void keepsCircuitsIsolatedByTemplate() {
        final AtomicInteger coletasCalls = new AtomicInteger();
        final AtomicInteger fretesCalls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    if (request.template() == DataExportTemplate.COLETAS) {
                        coletasCalls.incrementAndGet();
                        throw new DataExportUnavailableException(
                                request.template().templateId(), "HTTP 429");
                    }
                    fretesCalls.incrementAndGet();
                    return emptyPage();
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofMinutes(1)),
                        Clock.systemUTC());

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportCircuitOpenException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));

        assertEquals(0, gateway.fetch(request(DataExportTemplate.FRETES)).records().size());
        assertEquals(2, coletasCalls.get());
        assertEquals(1, fretesCalls.get());
    }

    @Test
    void reopensWhenTheHalfOpenProbeIsStillUnavailable() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T15:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    calls.incrementAndGet();
                    throw new DataExportUnavailableException(
                            request.template().templateId(), "HTTP 503");
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofSeconds(30)),
                        clock);

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        clock.advance(Duration.ofSeconds(30));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        assertThrows(
                DataExportCircuitOpenException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));

        assertEquals(3, calls.get());
    }

    @Test
    void resetsUnavailableFailureCountAfterAReachableContractFailure() {
        final AtomicInteger calls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    return switch (calls.incrementAndGet()) {
                        case 1, 3 ->
                                throw new DataExportUnavailableException(
                                        request.template().templateId(), "HTTP 503");
                        case 2 -> throw new IllegalArgumentException("Filtro inválido.");
                        default -> emptyPage();
                    };
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofMinutes(1)),
                        Clock.systemUTC());

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));

        assertEquals(0, gateway.fetch(request(DataExportTemplate.COLETAS)).records().size());
        assertEquals(4, calls.get());
    }

    @Test
    void closesTheCircuitWhenTheHalfOpenProbeReachesAContractFailure() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T15:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final DataExportGateway delegate =
                request -> {
                    return switch (calls.incrementAndGet()) {
                        case 1, 2 ->
                                throw new DataExportUnavailableException(
                                        request.template().templateId(), "HTTP 503");
                        case 3 -> throw new IllegalArgumentException("Filtro inválido.");
                        default -> emptyPage();
                    };
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofSeconds(30)),
                        clock);

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));
        clock.advance(Duration.ofSeconds(30));
        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.fetch(request(DataExportTemplate.FRETES)));

        assertEquals(0, gateway.fetch(request(DataExportTemplate.FRETES)).records().size());
        assertEquals(4, calls.get());
    }

    @Test
    void permitsOnlyOneHalfOpenProbeAtATime() throws Exception {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T15:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch probeStarted = new CountDownLatch(1);
        final CountDownLatch releaseProbe = new CountDownLatch(1);
        final DataExportGateway delegate =
                request -> {
                    if (calls.incrementAndGet() <= 2) {
                        throw new DataExportUnavailableException(
                                request.template().templateId(), "HTTP 503");
                    }
                    probeStarted.countDown();
                    try {
                        if (!releaseProbe.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("A sonda de teste não foi liberada.");
                        }
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "A sonda de teste foi interrompida.", exception);
                    }
                    return emptyPage();
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofSeconds(30)),
                        clock);

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        clock.advance(Duration.ofSeconds(30));

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<DataExportPageResponse> firstProbe =
                    executor.submit(() -> gateway.fetch(request(DataExportTemplate.COLETAS)));
            assertTrue(probeStarted.await(5, TimeUnit.SECONDS));
            assertThrows(
                    DataExportCircuitOpenException.class,
                    () -> gateway.fetch(request(DataExportTemplate.COLETAS)));

            releaseProbe.countDown();

            assertEquals(0, firstProbe.get(5, TimeUnit.SECONDS).records().size());
            assertEquals(3, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsTheHalfOpenProbeProtectedFromAnOlderInFlightSuccess() throws Exception {
        final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T15:00:00Z"));
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch oldCallStarted = new CountDownLatch(1);
        final CountDownLatch releaseOldCall = new CountDownLatch(1);
        final CountDownLatch probeStarted = new CountDownLatch(1);
        final CountDownLatch releaseProbe = new CountDownLatch(1);
        final DataExportGateway delegate =
                request -> {
                    final int call = calls.incrementAndGet();
                    if (call == 1) {
                        oldCallStarted.countDown();
                        await(releaseOldCall, "A chamada antiga não foi liberada.");
                        return emptyPage();
                    }
                    if (call == 2 || call == 3) {
                        throw new DataExportUnavailableException(
                                request.template().templateId(), "HTTP 503");
                    }
                    if (call == 4) {
                        probeStarted.countDown();
                        await(releaseProbe, "A sonda half-open não foi liberada.");
                        return emptyPage();
                    }
                    return emptyPage();
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate,
                        new DataExportCircuitBreakerPolicy(2, Duration.ofSeconds(30)),
                        clock);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<DataExportPageResponse> oldCall =
                    executor.submit(() -> gateway.fetch(request(DataExportTemplate.COLETAS)));
            assertTrue(oldCallStarted.await(5, TimeUnit.SECONDS));
            assertThrows(
                    DataExportUnavailableException.class,
                    () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
            assertThrows(
                    DataExportUnavailableException.class,
                    () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
            clock.advance(Duration.ofSeconds(30));

            final Future<DataExportPageResponse> halfOpenProbe =
                    executor.submit(() -> gateway.fetch(request(DataExportTemplate.COLETAS)));
            assertTrue(probeStarted.await(5, TimeUnit.SECONDS));

            releaseOldCall.countDown();
            assertEquals(0, oldCall.get(5, TimeUnit.SECONDS).records().size());
            assertThrows(
                    DataExportCircuitOpenException.class,
                    () -> gateway.fetch(request(DataExportTemplate.COLETAS)));

            releaseProbe.countDown();
            assertEquals(0, halfOpenProbe.get(5, TimeUnit.SECONDS).records().size());
            assertEquals(4, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsCircuitLimitsOutsideTheSharedResilienceCeilings() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportCircuitBreakerPolicy(
                                EslResiliencePolicy.MAX_CIRCUIT_FAILURE_THRESHOLD + 1,
                                Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportCircuitBreakerPolicy(
                                1, ExecutionDeadlines.MAX_TIMEOUT.plusNanos(1L)));
    }

    @Test
    void anOpenCircuitNearTheInstantCeilingFailsClosedWithoutTemporalOverflow() {
        final MutableClock clock = new MutableClock(Instant.MAX.minusSeconds(1L));
        final DataExportGateway delegate =
                request -> {
                    throw new DataExportUnavailableException(
                            request.template().templateId(), "HTTP 503");
                };
        final CircuitBreakingDataExportGateway gateway =
                new CircuitBreakingDataExportGateway(
                        delegate, new DataExportCircuitBreakerPolicy(1, Duration.ofDays(1)), clock);

        assertThrows(
                DataExportUnavailableException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(
                DataExportCircuitOpenException.class,
                () -> gateway.fetch(request(DataExportTemplate.COLETAS)));
    }

    private static DataExportPageRequest request(final DataExportTemplate template) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                Optional.empty(),
                1,
                10,
                template.defaultOrderBy());
    }

    private static DataExportPageResponse emptyPage() {
        return new DataExportPageResponse(List.of());
    }

    private static void await(final CountDownLatch latch, final String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada de teste foi interrompida.", exception);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
