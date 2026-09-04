package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GraphQlPageStreamerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void streamsSerialPagesAndIssuesPermitOnlyAfterTerminalAudit() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final Queue<GraphQlPageResponse> responses = new ArrayDeque<>();
        responses.add(
                GraphQlTestSupport.observed(
                        fixture,
                        GraphQlTestSupport.usersPage(
                                true, "cursor-a", "{\"id\":930001,\"name\":\"A\"}")));
        responses.add(
                GraphQlTestSupport.observed(
                        fixture,
                        GraphQlTestSupport.usersPage(
                                false, null, "{\"id\":930002,\"name\":\"B\"}")));
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none(),
                                request -> {
                                    calls.incrementAndGet();
                                    return responses.remove();
                                }),
                        fixture.configuration(),
                        fixture.guard(),
                        br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none());
        final AtomicInteger delivered = new AtomicInteger();

        final GraphQlExtractionResult result =
                new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK)
                        .stream(
                                fixture.guard().executionContext(),
                                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT),
                                new GraphQlExtractionLimits(2, 2),
                                page -> {
                                    assertEquals(delivered.get(), calls.get() - 1);
                                    delivered.incrementAndGet();
                                });

        assertEquals(2, calls.get());
        assertEquals(2, delivered.get());
        assertEquals(2, result.pagesFetched());
        assertEquals(2, result.nodesDelivered());
        assertEquals(
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED,
                result.traversalVerification());
        assertEquals(SourceDataEffect.SHADOW_UPSERT, fixture.guard().complete().dataEffect());
    }

    @Test
    void rejectsEmptyMissingCursorAndImmediateRepeatBeforeConsumer() {
        assertPaginationFailure(
                GraphQlTestSupport.usersPage(false, null),
                GraphQlPaginationException.Reason.EMPTY_PAGE);
        assertPaginationFailure(
                GraphQlTestSupport.usersPage(true, null, "{\"id\":930001,\"name\":\"N\"}"),
                GraphQlPaginationException.Reason.MISSING_CURSOR);

        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final Queue<GraphQlPageResponse> responses = new ArrayDeque<>();
        responses.add(
                GraphQlTestSupport.usersPage(true, "same", "{\"id\":930001,\"name\":\"N1\"}"));
        responses.add(
                GraphQlTestSupport.usersPage(true, "same", "{\"id\":930002,\"name\":\"N2\"}"));
        final AtomicInteger consumed = new AtomicInteger();
        final GraphQlPaginationException repeated =
                assertThrows(
                        GraphQlPaginationException.class,
                        () ->
                                new GraphQlPageStreamer(
                                                secure(fixture, request -> responses.remove()),
                                                GraphQlExtractionAudit.noop(),
                                                CLOCK)
                                        .stream(
                                                fixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(3, 10),
                                                page -> consumed.incrementAndGet()));
        assertEquals(GraphQlPaginationException.Reason.REPEATED_CURSOR, repeated.reason());
        assertEquals(1, consumed.get());
    }

    @Test
    void detectsANonPeriodicRepeatedCursorWithFixedMemory() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final String[] cursors = {"a", "b", "a", "c"};
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();

        final GraphQlPaginationException cycle =
                assertThrows(
                        GraphQlPaginationException.class,
                        () ->
                                new GraphQlPageStreamer(
                                                secure(
                                                        fixture,
                                                        request -> {
                                                            final int index =
                                                                    calls.getAndIncrement();
                                                            return GraphQlTestSupport.usersPage(
                                                                    true,
                                                                    cursors[index],
                                                                    "{\"id\":930001,\"name\":\"N\"}");
                                                        }),
                                                GraphQlExtractionAudit.noop(),
                                                CLOCK)
                                        .stream(
                                                fixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(10, 10),
                                                page -> consumed.incrementAndGet()));

        assertEquals(GraphQlPaginationException.Reason.REPEATED_CURSOR, cycle.reason());
        assertEquals(3, calls.get());
        assertEquals(2, consumed.get());
    }

    @Test
    void capsPagesAndNodesBeforeConsumerOrAnotherIo() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();
        final GraphQlPaginationException pageCap =
                assertThrows(
                        GraphQlPaginationException.class,
                        () ->
                                new GraphQlPageStreamer(
                                                secure(
                                                        fixture,
                                                        request -> {
                                                            calls.incrementAndGet();
                                                            return GraphQlTestSupport.usersPage(
                                                                    true,
                                                                    "cursor",
                                                                    "{\"id\":930001,\"name\":\"N\"}");
                                                        }),
                                                GraphQlExtractionAudit.noop(),
                                                CLOCK)
                                        .stream(
                                                fixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(1, 10),
                                                page -> consumed.incrementAndGet()));
        assertEquals(GraphQlPaginationException.Reason.PAGE_LIMIT, pageCap.reason());
        assertEquals(1, calls.get());
        assertEquals(0, consumed.get());

        final GraphQlTestSupport.Fixture nodeFixture = GraphQlTestSupport.usersFixture();
        final GraphQlPaginationException nodeCap =
                assertThrows(
                        GraphQlPaginationException.class,
                        () ->
                                new GraphQlPageStreamer(
                                                secure(
                                                        nodeFixture,
                                                        request ->
                                                                GraphQlTestSupport.usersPage(
                                                                        false,
                                                                        null,
                                                                        "{\"id\":930001,\"name\":\"N\"}",
                                                                        "{\"id\":930002,\"name\":\"N\"}")),
                                                GraphQlExtractionAudit.noop(),
                                                CLOCK)
                                        .stream(
                                                nodeFixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(2, 1),
                                                page -> consumed.incrementAndGet()));
        assertEquals(GraphQlPaginationException.Reason.NODE_LIMIT, nodeCap.reason());
        assertEquals(0, consumed.get());
    }

    @Test
    void cancellationTimeoutConsumerAndCompletionAuditFailuresInvalidateEvidence() {
        final GraphQlTestSupport.Fixture cancelledFixture = GraphQlTestSupport.usersFixture();
        final CancellationSignal signal = new CancellationSignal();
        signal.cancel();
        final AtomicInteger calls = new AtomicInteger();
        final CapturingAudit cancelledAudit = new CapturingAudit();
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        new GraphQlPageStreamer(
                                        secure(
                                                cancelledFixture,
                                                signal,
                                                request -> {
                                                    calls.incrementAndGet();
                                                    throw new AssertionError();
                                                }),
                                        cancelledAudit,
                                        CLOCK)
                                .stream(
                                        cancelledFixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        signal,
                                        page -> {}));
        assertEquals(0, calls.get());
        assertEquals(GraphQlFailureCategory.CANCELLED, cancelledAudit.failedCategory);
        assertThrows(ContractDriftException.class, cancelledFixture.guard()::complete);

        final CapturingAudit timeoutAudit = new CapturingAudit();
        final GraphQlTestSupport.Fixture timeoutFixture = GraphQlTestSupport.usersFixture();
        assertThrows(
                ResilienceTimeoutException.class,
                () ->
                        new GraphQlPageStreamer(
                                        secure(
                                                timeoutFixture,
                                                request -> {
                                                    throw new ResilienceTimeoutException(
                                                            ResilienceTimeoutScope.REQUEST);
                                                }),
                                        timeoutAudit,
                                        CLOCK)
                                .stream(
                                        timeoutFixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {}));
        assertEquals(GraphQlFailureCategory.TIMEOUT, timeoutAudit.failedCategory);
        assertEquals(1, timeoutAudit.started.maximumPages());
        assertEquals(1, timeoutAudit.started.maximumNodes());

        final GraphQlTestSupport.Fixture consumerFixture = GraphQlTestSupport.usersFixture();
        final GraphQlGateway securedConsumer =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none(),
                                request ->
                                        GraphQlTestSupport.observed(
                                                consumerFixture,
                                                GraphQlTestSupport.usersPage(
                                                        false,
                                                        null,
                                                        "{\"id\":930001,\"name\":\"N\"}"))),
                        consumerFixture.configuration(),
                        consumerFixture.guard(),
                        br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () ->
                        new GraphQlPageStreamer(
                                        securedConsumer, GraphQlExtractionAudit.noop(), CLOCK)
                                .stream(
                                        consumerFixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {
                                            throw new IllegalStateException("synthetic consumer");
                                        }));
        assertThrows(ContractDriftException.class, consumerFixture.guard()::complete);

        final GraphQlTestSupport.Fixture auditFixture = GraphQlTestSupport.usersFixture();
        final GraphQlGateway securedAudit =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none(),
                                request ->
                                        GraphQlTestSupport.observed(
                                                auditFixture,
                                                GraphQlTestSupport.usersPage(
                                                        false,
                                                        null,
                                                        "{\"id\":930001,\"name\":\"N\"}"))),
                        auditFixture.configuration(),
                        auditFixture.guard(),
                        br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () ->
                        new GraphQlPageStreamer(securedAudit, new FailingCompletionAudit(), CLOCK)
                                .stream(
                                        auditFixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {}));
        assertThrows(ContractDriftException.class, auditFixture.guard()::complete);

        final GraphQlTestSupport.Fixture postConsumerFixture = GraphQlTestSupport.usersFixture();
        final CancellationSignal postConsumerSignal = new CancellationSignal();
        final GraphQlGateway postConsumerGateway =
                secure(
                        postConsumerFixture,
                        postConsumerSignal,
                        request ->
                                GraphQlTestSupport.usersPage(
                                        false, null, "{\"id\":930001,\"name\":\"N\"}"));
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        new GraphQlPageStreamer(
                                        postConsumerGateway, GraphQlExtractionAudit.noop(), CLOCK)
                                .stream(
                                        postConsumerFixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        postConsumerSignal,
                                        page -> postConsumerSignal.cancel()));
        assertThrows(ContractDriftException.class, postConsumerFixture.guard()::complete);
    }

    @Test
    void classifiesRemoteEnvelopeAndPageInfoFailuresWithoutPayloadDetails() {
        assertResponseFailureCategory(
                GraphQlResponseException.Reason.INVALID_PAGE_INFO,
                GraphQlFailureCategory.PAGINATION_ANOMALY);
        assertResponseFailureCategory(
                GraphQlResponseException.Reason.INVALID_JSON,
                GraphQlFailureCategory.SOURCE_RESPONSE_INVALID);
        assertRuntimeFailureCategory(
                new GraphQlRetryAfterLimitExceededException(),
                GraphQlFailureCategory.SOURCE_RESPONSE_INVALID);

        final CapturingAudit timeoutAudit = new CapturingAudit();
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        assertThrows(
                GraphQlUnavailableException.class,
                () ->
                        new GraphQlPageStreamer(
                                        secure(
                                                fixture,
                                                request -> {
                                                    throw new GraphQlUnavailableException(
                                                            GraphQlReadOperation.USERS_SNAPSHOT,
                                                            GraphQlUnavailableException.Reason
                                                                    .REQUEST_TIMEOUT,
                                                            new IllegalStateException("synthetic"));
                                                }),
                                        timeoutAudit,
                                        CLOCK)
                                .stream(
                                        fixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {}));
        assertEquals(GraphQlFailureCategory.TIMEOUT, timeoutAudit.failedCategory);
    }

    @Test
    void refusesAStreamerCancellationCapabilityDifferentFromTheBoundGateway() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final CancellationSignal boundCancellation = new CancellationSignal();
        final CancellationSignal foreignCancellation = new CancellationSignal();
        foreignCancellation.cancel();
        final AtomicInteger fetches = new AtomicInteger();
        final CapturingAudit audit = new CapturingAudit();
        final GraphQlGateway gateway =
                secure(
                        fixture,
                        boundCancellation,
                        request -> {
                            fetches.incrementAndGet();
                            return GraphQlTestSupport.usersPage(
                                    false, null, "{\"id\":930001,\"name\":\"N\"}");
                        });

        final ContractDriftException mismatch =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                new GraphQlPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                fixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(1, 1),
                                                foreignCancellation,
                                                page -> {}));

        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, mismatch.reason());
        assertEquals(0, fetches.get());
        assertEquals(null, audit.started);
        assertThrows(ContractDriftException.class, fixture.guard()::complete);
    }

    @Test
    void traversesThousandsOfSyntheticPagesWithoutAnExecutionCollection() {
        final int totalPages = 5_000;
        final AtomicInteger fetched = new AtomicInteger();
        final AtomicInteger delivered = new AtomicInteger();
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();

        final GraphQlExtractionResult result =
                new GraphQlPageStreamer(
                                secure(
                                        fixture,
                                        request -> {
                                            final int page = fetched.incrementAndGet();
                                            return GraphQlTestSupport.usersPage(
                                                    page < totalPages,
                                                    page < totalPages ? "cursor-" + page : null,
                                                    "{\"id\":930001,\"name\":\"N\"}");
                                        }),
                                GraphQlExtractionAudit.noop(),
                                CLOCK)
                        .stream(
                                fixture.guard().executionContext(),
                                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT),
                                new GraphQlExtractionLimits(totalPages, totalPages),
                                page -> delivered.incrementAndGet());

        assertEquals(totalPages, result.pagesFetched());
        assertEquals(totalPages, fetched.get());
        assertEquals(totalPages, delivered.get());
        assertFalse(
                java.util.Arrays.stream(GraphQlPageStreamer.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("fetchAll")));
    }

    private static void assertPaginationFailure(
            final GraphQlPageResponse response,
            final GraphQlPaginationException.Reason expectedReason) {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final AtomicInteger consumed = new AtomicInteger();
        final GraphQlPaginationException exception =
                assertThrows(
                        GraphQlPaginationException.class,
                        () ->
                                new GraphQlPageStreamer(
                                                secure(fixture, request -> response),
                                                GraphQlExtractionAudit.noop(),
                                                CLOCK)
                                        .stream(
                                                fixture.guard().executionContext(),
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT),
                                                new GraphQlExtractionLimits(2, 10),
                                                page -> consumed.incrementAndGet()));
        assertEquals(expectedReason, exception.reason());
        assertEquals(0, consumed.get());
        assertThrows(ContractDriftException.class, fixture.guard()::complete);
    }

    private static void assertResponseFailureCategory(
            final GraphQlResponseException.Reason reason,
            final GraphQlFailureCategory expectedCategory) {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final CapturingAudit audit = new CapturingAudit();
        assertThrows(
                GraphQlResponseException.class,
                () ->
                        new GraphQlPageStreamer(
                                        secure(
                                                fixture,
                                                request -> {
                                                    throw new GraphQlResponseException(reason);
                                                }),
                                        audit,
                                        CLOCK)
                                .stream(
                                        fixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {}));
        assertEquals(expectedCategory, audit.failedCategory);
    }

    private static void assertRuntimeFailureCategory(
            final RuntimeException failure, final GraphQlFailureCategory expectedCategory) {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final CapturingAudit audit = new CapturingAudit();
        assertThrows(
                failure.getClass(),
                () ->
                        new GraphQlPageStreamer(
                                        secure(
                                                fixture,
                                                request -> {
                                                    throw failure;
                                                }),
                                        audit,
                                        CLOCK)
                                .stream(
                                        fixture.guard().executionContext(),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT),
                                        new GraphQlExtractionLimits(1, 1),
                                        page -> {}));
        assertEquals(expectedCategory, audit.failedCategory);
    }

    private static GraphQlGateway secure(
            final GraphQlTestSupport.Fixture fixture, final GraphQlGateway gateway) {
        return secure(
                fixture,
                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none(),
                gateway);
    }

    private static GraphQlGateway secure(
            final GraphQlTestSupport.Fixture fixture,
            final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken cancellationToken,
            final GraphQlGateway gateway) {
        return GraphQlContractGate.enforce(
                GraphQlTestSupport.bound(
                        cancellationToken,
                        request -> GraphQlTestSupport.observed(fixture, gateway.fetch(request))),
                fixture.configuration(),
                fixture.guard(),
                cancellationToken);
    }

    private static final class CapturingAudit implements GraphQlExtractionAudit {

        private GraphQlFailureCategory failedCategory;
        private ExecutionStarted started;

        @Override
        public void executionStarted(final ExecutionStarted event) {
            started = event;
        }

        @Override
        public void pageRead(final PageRead event) {}

        @Override
        public void executionCompleted(final GraphQlExtractionResult result) {}

        @Override
        public void executionFailed(final ExecutionFailed event) {
            failedCategory = event.category();
        }
    }

    private static final class FailingCompletionAudit implements GraphQlExtractionAudit {

        @Override
        public void executionStarted(final ExecutionStarted event) {}

        @Override
        public void pageRead(final PageRead event) {}

        @Override
        public void executionCompleted(final GraphQlExtractionResult result) {
            throw new IllegalStateException("synthetic audit");
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {}
    }
}
