package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.usuarios.aplicacao.ExtrairUsuariosGraphQl;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioNamePresence;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageBatch;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExtrairUsuariosGraphQlTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-01T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void restartsWithoutCursorAndStagesOneSynchronousBoundedPageAtATime() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final Queue<GraphQlPageResponse> pages = new ArrayDeque<>();
        pages.add(
                GraphQlTestSupport.observed(
                        fixture,
                        GraphQlTestSupport.usersPage(
                                true,
                                "synthetic-cursor-a",
                                "{\"id\":1,\"name\":\"Primeiro\"}",
                                "{\"id\":\"2\",\"name\":null}")));
        pages.add(
                GraphQlTestSupport.observed(
                        fixture, GraphQlTestSupport.usersPage(false, null, "{\"id\":3}")));
        final List<GraphQlPageRequest> requests = new ArrayList<>();
        final List<UsuarioStageBatch> batches = new ArrayList<>();
        final CancellationToken cancellation = CancellationToken.none();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                cancellation,
                                request -> {
                                    requests.add(request);
                                    if (requests.size() == 2) {
                                        assertEquals(1, batches.size());
                                    }
                                    return pages.remove();
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        final ExtrairUsuariosGraphQl useCase =
                new ExtrairUsuariosGraphQl(
                        new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                        batches::add,
                        new UsuarioGraphQlNodeMapper(),
                        CLOCK);

        final GraphQlExtractionResult result =
                useCase.execute(
                        fixture.guard().executionContext(),
                        new GraphQlExtractionLimits(2, 3),
                        cancellation);

        assertEquals(2, result.pagesFetched());
        assertEquals(3, result.nodesDelivered());
        assertEquals(2, requests.size());
        assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, requests.get(0).operation());
        assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, requests.get(0).parameters().operation());
        assertEquals(ExtrairUsuariosGraphQl.PAGE_SIZE, requests.get(0).pageSize());
        assertTrue(requests.get(0).after().isEmpty());
        assertTrue(requests.get(1).after().isPresent());
        assertEquals(2, batches.size());
        assertEquals(1, batches.get(0).batchNumber());
        assertEquals(2, batches.get(0).size());
        assertEquals(2, batches.get(1).batchNumber());
        assertEquals(1, batches.get(1).size());
        assertEquals(1, batches.get(0).recordAt(0).inputOrdinal());
        assertEquals(2, batches.get(0).recordAt(1).inputOrdinal());
        assertEquals(1, batches.get(1).recordAt(0).inputOrdinal());
        assertEquals(UsuarioNamePresence.VALUE, batches.get(0).recordAt(0).namePresence());
        assertEquals(UsuarioNamePresence.NULL, batches.get(0).recordAt(1).namePresence());
        assertEquals(UsuarioNamePresence.ABSENT, batches.get(1).recordAt(0).namePresence());
        assertEquals(CLOCK.instant(), batches.get(0).observedAt());
        assertEquals(CLOCK.instant(), batches.get(1).observedAt());
        assertTrue(pages.isEmpty());
        assertEquals(SourceDataEffect.SHADOW_UPSERT, fixture.guard().complete().dataEffect());
    }

    @Test
    void stagingFailureStopsBeforeTheNextPageAndInvalidatesPromotionEvidence() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final CancellationToken cancellation = CancellationToken.none();
        final AtomicInteger fetches = new AtomicInteger();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                cancellation,
                                request -> {
                                    fetches.incrementAndGet();
                                    return GraphQlTestSupport.observed(
                                            fixture,
                                            GraphQlTestSupport.usersPage(
                                                    true,
                                                    "synthetic-cursor-a",
                                                    "{\"id\":1,\"name\":\"Primeiro\"}"));
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        final ExtrairUsuariosGraphQl useCase =
                new ExtrairUsuariosGraphQl(
                        new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                        batch -> {
                            throw new IllegalStateException("synthetic-stage-failure");
                        },
                        new UsuarioGraphQlNodeMapper(),
                        CLOCK);

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                useCase.execute(
                                        fixture.guard().executionContext(),
                                        new GraphQlExtractionLimits(2, 2),
                                        cancellation));

        assertEquals("synthetic-stage-failure", failure.getMessage());
        assertEquals(1, fetches.get());
        assertEquals(
                ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL,
                assertThrows(ContractDriftException.class, fixture.guard()::complete).reason());
    }

    @Test
    void stagingFailureOnSecondPageStopsBeforeTheThirdFetch() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final CancellationToken cancellation = CancellationToken.none();
        final Queue<GraphQlPageResponse> pages = new ArrayDeque<>();
        pages.add(
                GraphQlTestSupport.observed(
                        fixture,
                        GraphQlTestSupport.usersPage(true, "synthetic-cursor-a", "{\"id\":1}")));
        pages.add(
                GraphQlTestSupport.observed(
                        fixture,
                        GraphQlTestSupport.usersPage(true, "synthetic-cursor-b", "{\"id\":2}")));
        final AtomicInteger fetches = new AtomicInteger();
        final List<Integer> stagedBatches = new ArrayList<>();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                cancellation,
                                request -> {
                                    fetches.incrementAndGet();
                                    return pages.remove();
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        final ExtrairUsuariosGraphQl useCase =
                new ExtrairUsuariosGraphQl(
                        new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                        batch -> {
                            stagedBatches.add(batch.batchNumber());
                            if (batch.batchNumber() == 2) {
                                throw new IllegalStateException(
                                        "synthetic-second-page-stage-failure");
                            }
                        },
                        new UsuarioGraphQlNodeMapper(),
                        CLOCK);

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                useCase.execute(
                                        fixture.guard().executionContext(),
                                        new GraphQlExtractionLimits(3, 3),
                                        cancellation));

        assertEquals("synthetic-second-page-stage-failure", failure.getMessage());
        assertEquals(2, fetches.get());
        assertEquals(List.of(1, 2), stagedBatches);
        assertThrows(ContractDriftException.class, fixture.guard()::complete);
    }

    @Test
    void checksCancellationAfterMappingAndBeforeStagingThePage() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final AtomicInteger cancellationChecks = new AtomicInteger();
        final CancellationToken cancellation = () -> cancellationChecks.incrementAndGet() >= 4;
        final AtomicInteger fetches = new AtomicInteger();
        final AtomicInteger staged = new AtomicInteger();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                cancellation,
                                request -> {
                                    fetches.incrementAndGet();
                                    return GraphQlTestSupport.observed(
                                            fixture,
                                            GraphQlTestSupport.usersPage(
                                                    false,
                                                    null,
                                                    "{\"id\":1,\"name\":\"Synthetic\"}"));
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        final ExtrairUsuariosGraphQl useCase =
                new ExtrairUsuariosGraphQl(
                        new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                        batch -> staged.incrementAndGet(),
                        new UsuarioGraphQlNodeMapper(),
                        CLOCK);

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        useCase.execute(
                                fixture.guard().executionContext(),
                                new GraphQlExtractionLimits(1, 1),
                                cancellation));

        assertEquals(1, fetches.get());
        assertEquals(0, staged.get());
        assertThrows(ContractDriftException.class, fixture.guard()::complete);
    }

    @Test
    void validatesDependenciesAndExecutionArgumentsBeforeTraversal() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                CancellationToken.none(),
                                request -> {
                                    throw new AssertionError("delegate should not be called");
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        CancellationToken.none());
        final GraphQlPageStreamer streamer =
                new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK);
        final UsuarioGraphQlNodeMapper mapper = new UsuarioGraphQlNodeMapper();
        final ExtrairUsuariosGraphQl useCase =
                new ExtrairUsuariosGraphQl(streamer, batch -> {}, mapper, CLOCK);

        assertThrows(
                NullPointerException.class,
                () ->
                        useCase.execute(
                                null, new GraphQlExtractionLimits(1, 1), CancellationToken.none()));
        assertThrows(
                NullPointerException.class,
                () ->
                        useCase.execute(
                                fixture.guard().executionContext(),
                                null,
                                CancellationToken.none()));
        assertThrows(
                NullPointerException.class,
                () ->
                        useCase.execute(
                                fixture.guard().executionContext(),
                                new GraphQlExtractionLimits(1, 1),
                                null));
        assertThrows(
                NullPointerException.class,
                () -> new ExtrairUsuariosGraphQl(null, batch -> {}, mapper, CLOCK));
        assertThrows(
                NullPointerException.class,
                () -> new ExtrairUsuariosGraphQl(streamer, null, mapper, CLOCK));
        assertThrows(
                NullPointerException.class,
                () -> new ExtrairUsuariosGraphQl(streamer, batch -> {}, null, CLOCK));
        assertThrows(
                NullPointerException.class,
                () -> new ExtrairUsuariosGraphQl(streamer, batch -> {}, mapper, null));
        assertFalse(fixture.guard().alertObserved());
    }
}
