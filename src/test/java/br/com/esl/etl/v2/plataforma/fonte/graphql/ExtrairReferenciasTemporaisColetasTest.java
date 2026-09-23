package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaGraphQlTemporalMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairReferenciasTemporaisColetas;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.LigarReferenciaTemporalColeta;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtrairReferenciasTemporaisColetasTest {
    private static final GraphQlReadOperation OPERATION =
            GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);
    private static final String NODE =
            "{\"id\":\"17\",\"status\":\"pending\","
                    + "\"statusUpdatedAt\":\"2026-09-09T17:00:00.123456789-03:00\",\"requestDate\":\"2026-09-09\"}";

    @Test
    void parsesPagesAndLinksTheExplicitPairWithBoundedSynchronousConsumption() throws Exception {
        final var fixture = fixture();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var requests = new ArrayList<GraphQlPageRequest>();
        final var references = new ArrayList<ColetaTemporalObservation>();
        final var results = new ArrayList<LigarReferenciaTemporalColeta.Result>();
        final var data =
                new ColetaDataExportRecordMapper()
                        .map(
                                1,
                                GraphQlTestSupport.MAPPER.readTree(
                                        "{\"id\":17,\"status\":\"pending\",\"request_date\":\"2026-09-09\"}"));
        final var dataRun = UUID.randomUUID();
        final var batch = new ColetaStageBatch(dataRun, 1, List.of(data), CLOCK.instant());
        final var cancellation = CancellationToken.none();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request -> {
                            if (!requests.isEmpty()) {
                                assertEquals(
                                        1,
                                        references.size()); // Consumidor terminou antes da próxima
                                // página.
                            }
                            requests.add(request);
                            return parser.parse(
                                    envelope(
                                            NODE,
                                            requests.size() == 1,
                                            requests.size() == 1 ? "\"synthetic-cursor\"" : "null"),
                                    request);
                        },
                        ref -> {
                            references.add(ref);
                            final var dataIdentity =
                                    new ScopedSourceIdentity(
                                            "SYNTHETIC_SOURCE",
                                            "SYNTHETIC_TENANT",
                                            FirstWaveIdentityContract.Entity.COLETAS,
                                            data.sourceKey());
                            final var binding =
                                    new ColetaTemporalIdentityBinding(
                                            dataRun,
                                            ref.executionId(),
                                            dataIdentity,
                                            ref.identity(),
                                            DATE,
                                            new ImmutableFingerprint(
                                                    "synthetic-pair-v1", "b".repeat(64)));
                            results.add(
                                    new LigarReferenciaTemporalColeta()
                                            .execute(batch, 0, ref, binding));
                        },
                        CLOCK);
        final var result =
                execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(2, 40));
        assertEquals(2, result.pagesFetched());
        assertEquals(2, result.nodesDelivered());
        assertTrue(requests.get(0).after().isEmpty());
        assertTrue(requests.get(1).after().isPresent());
        assertEquals(20, requests.get(0).pageSize());
        assertEquals(1, references.get(0).pageNumber());
        assertEquals(2, references.get(1).pageNumber());
        assertEquals(1, references.get(1).inputOrdinal());
        for (final var linked : results) {
            assertEquals(LigarReferenciaTemporalColeta.Outcome.COMPLEMENTED, linked.outcome());
            assertEquals(Instant.parse("2026-09-09T20:00:00.123456789Z"), linked.candidateAtUtc());
        }
        final var rejection =
                assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
        assertEquals(ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE, rejection.reason());
        assertEquals(
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED,
                result.traversalVerification());
    }

    @Test
    void nullableAndMissingTimestampReachTheConsumerWithoutInventingAnInstant() {
        final var fixture = fixture();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var delivered = new ArrayList<ColetaTemporalObservation>();
        final var cancellation = CancellationToken.none();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request ->
                                parser.parse(
                                        envelope(
                                                "{\"id\":17,\"statusUpdatedAt\":null}",
                                                false,
                                                "null"),
                                        request),
                        delivered::add,
                        CLOCK);
        execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(1, 20));
        assertEquals(1, delivered.size());
        assertNull(delivered.get(0).statusAtUtc());
    }

    @Test
    void terminalEmptyPageProducesNoObservationOrCompletenessClaim() {
        final var fixture = fixture();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var count = new AtomicInteger();
        final var cancellation = CancellationToken.none();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request ->
                                parser.parse(
                                        "{\"data\":{\"pick\":{\"edges\":[],\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}",
                                        request),
                        ref -> count.incrementAndGet(),
                        CLOCK);
        assertThrows(
                GraphQlPaginationException.class,
                () -> execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(1, 20)));
        assertEquals(0, count.get());
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":17,\"status\":7}",
                "{\"id\":17,\"statusUpdatedAt\":7}",
                "{\"id\":17,\"requestDate\":{}}",
                "{\"id\":17,\"updatedAt\":\"2026-09-09T10:00:00Z\"}"
            })
    void contractDriftNeverReachesTheConsumer(final String node) {
        final var fixture = fixture();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var count = new AtomicInteger();
        final var cancellation = CancellationToken.none();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request -> parser.parse(envelope(node, false, "null"), request),
                        ref -> count.incrementAndGet(),
                        CLOCK);
        assertThrows(
                ContractDriftException.class,
                () -> execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(1, 20)));
        assertEquals(0, count.get());
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    @Test
    void reachingPageCapWithNextCursorFailsWithoutFalseCompletion() {
        final var fixture = fixture();
        final var cancellation = CancellationToken.none();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var delivered = new AtomicInteger();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request ->
                                parser.parse(envelope(NODE, true, "\"synthetic-next\""), request),
                        ref -> delivered.incrementAndGet(),
                        CLOCK);
        assertThrows(
                GraphQlPaginationException.class,
                () -> execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(1, 20)));
        assertEquals(0, delivered.get());
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    @Test
    void consumerFailureInvalidatesPartialTraversalAndDoesNotFetchAgain() {
        final var fixture = fixture();
        final var cancellation = CancellationToken.none();
        final var calls = new AtomicInteger();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request -> {
                            calls.incrementAndGet();
                            return parser.parse(
                                    envelope(NODE, true, "\"synthetic-next\""), request);
                        },
                        ref -> {
                            throw new IllegalStateException("SYNTHETIC_CONSUMER_FAILURE");
                        },
                        CLOCK);
        assertThrows(
                IllegalStateException.class,
                () -> execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(2, 40)));
        assertEquals(1, calls.get());
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    @Test
    void cancellationWhileStampingPagePreventsConsumption() {
        final var fixture = fixture();
        final var cancellation = new CancellationSignal();
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var delivered = new AtomicInteger();
        final var cancelClock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(final java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        cancellation.cancel();
                        return CLOCK.instant();
                    }
                };
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request -> parser.parse(envelope(NODE, false, "null"), request),
                        ref -> delivered.incrementAndGet(),
                        cancelClock);
        assertThrows(
                ResilienceCancelledException.class,
                () -> execute(useCase, fixture, cancellation, new GraphQlExtractionLimits(1, 20)));
        assertEquals(0, delivered.get());
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    @Test
    void invalidScopeAndOversizedRequestsFailBeforeIo() {
        final var fixture = fixture();
        final var cancellation = CancellationToken.none();
        final var calls = new AtomicInteger();
        final var useCase =
                useCase(
                        fixture,
                        cancellation,
                        request -> {
                            calls.incrementAndGet();
                            throw new AssertionError();
                        },
                        ref -> {},
                        CLOCK);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        useCase.execute(
                                fixture.guard().executionContext(),
                                "SYNTHETIC_SOURCE",
                                "DEFAULT",
                                DATE,
                                new GraphQlExtractionLimits(1, 20),
                                cancellation));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlPageRequest.initial(
                                OPERATION, GraphQlQueryParameters.picksTemporalForDate(DATE), 21));
        assertEquals(0, calls.get());
    }

    private static GraphQlTestSupport.Fixture fixture() {
        return GraphQlTestSupport.fixture(
                OPERATION, GraphQlTestSupport.page(OPERATION, false, null, NODE));
    }

    private static ExtrairReferenciasTemporaisColetas useCase(
            final GraphQlTestSupport.Fixture fixture,
            final CancellationToken cancellation,
            final GraphQlGateway gateway,
            final Consumer<ColetaTemporalObservation> consumer,
            final Clock clock) {
        final var secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(cancellation, gateway),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        return new ExtrairReferenciasTemporaisColetas(
                new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                new ColetaGraphQlTemporalMapper(),
                clock,
                consumer);
    }

    private static GraphQlExtractionResult execute(
            final ExtrairReferenciasTemporaisColetas useCase,
            final GraphQlTestSupport.Fixture fixture,
            final CancellationToken cancellation,
            final GraphQlExtractionLimits limits) {
        return useCase.execute(
                fixture.guard().executionContext(),
                "SYNTHETIC_SOURCE",
                "SYNTHETIC_TENANT",
                DATE,
                limits,
                cancellation);
    }

    private static String envelope(final String node, final boolean next, final String cursor) {
        return "{\"data\":{\"pick\":{\"edges\":[{\"node\":"
                + node
                + "}],\"pageInfo\":{\"hasNextPage\":"
                + next
                + ",\"endCursor\":"
                + cursor
                + "}}}}";
    }
}
