package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.medicao.ExecutionWidePageRetentionMutant;
import br.com.esl.etl.v2.contratos.medicao.ManagedPageGauge;
import br.com.esl.etl.v2.contratos.medicao.MeasurementAssessment;
import br.com.esl.etl.v2.contratos.medicao.MeasurementDiagnostics;
import br.com.esl.etl.v2.contratos.medicao.MeasurementEvaluator;
import br.com.esl.etl.v2.contratos.medicao.MeasurementEvidence;
import br.com.esl.etl.v2.contratos.medicao.MeasurementPlan;
import br.com.esl.etl.v2.contratos.medicao.MeasurementRun;
import br.com.esl.etl.v2.contratos.medicao.MeasurementStreamer;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GraphQlPageStreamerMultiscaleMeasurementTest {

    private static final int RECORDS_PER_PAGE = 8;
    private static final int BLOOM_WORD_COUNT = 131_072;
    private static final int ONE_MEBIBYTE = 1 << 20;
    private static final String FIXTURE_KIND = "SYNTHETIC_FIXTURE";

    public static MeasurementRun runHealthyScenarioForReceipt(final int dataPages) {
        final MeasurementPlan plan = graphQlPlan(dataPages);
        return runHealthyScenario(newScenario(plan)).run();
    }

    @ParameterizedTest(name = "GraphQlPageStreamer: {0} data pages")
    @ValueSource(ints = {16, 256, 4096})
    void measuresTheRealStreamerWithOneManagedPageAtEveryScale(final int dataPages) {
        final HealthyExecution execution = runHealthyScenario(newScenario(graphQlPlan(dataPages)));
        final Scenario scenario = execution.scenario();
        final MeasurementRun run = execution.run();
        final MeasurementEvidence evidence = run.evidence();
        final GraphQlExtractionResult extraction = scenario.extractionResult().get();

        assertNotNull(extraction);
        assertEquals("GraphQlPageStreamer", scenario.plan().streamerName());
        assertEquals(dataPages, scenario.lazyGateway().fetches());
        assertEquals(dataPages, execution.consumedCallbacks());
        assertEquals(
                Math.multiplyExact((long) dataPages, RECORDS_PER_PAGE), execution.countedNodes());
        assertEquals(1, execution.terminalPages());
        assertEquals(dataPages, scenario.lazyGateway().terminalPageNumber());
        assertEquals(RECORDS_PER_PAGE, scenario.lazyGateway().terminalPageNodeCount());
        assertFalse(scenario.lazyGateway().terminalPageHasNext());

        assertEquals(dataPages, extraction.pagesFetched());
        assertEquals(
                Math.multiplyExact((long) dataPages, RECORDS_PER_PAGE),
                extraction.nodesDelivered());
        assertEquals(
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED,
                extraction.traversalVerification());
        assertFalse(extraction.traversalVerification().provesCoverageOrSnapshot());

        assertEquals(scenario.plan(), evidence.plan());
        assertEquals(scenario.plan().expectedFetchedPages(), evidence.fetchedPages());
        assertEquals(scenario.plan().expectedConsumedPages(), evidence.consumedPages());
        assertEquals(scenario.plan().expectedRecords(), evidence.records());
        assertEquals(scenario.measuredBytes().get(), evidence.bytes());
        assertTrue(evidence.bytes() > 0L);
        assertEquals(1L, evidence.maxInFlightPages());
        assertEquals(0L, evidence.finalInFlightPages());
        assertEquals(dataPages, evidence.acquisitions());
        assertEquals(dataPages, evidence.releases());
        assertEquals(0L, evidence.maxRetainedPages());
        assertEquals(0L, evidence.finalRetainedPages());

        assertTrue(run.assessment().proven());
        assertEquals(
                MeasurementAssessment.Disposition.PROVEN_SYNTHETICALLY,
                run.assessment().disposition());
        assertEquals(
                MeasurementAssessment.Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY,
                run.assessment().reason());
        assertEquals(run.assessment(), new MeasurementEvaluator().evaluate(evidence));
        assertDiagnosticsAreInformational(run.diagnostics());
    }

    private static HealthyExecution runHealthyScenario(final Scenario scenario) {
        final AtomicInteger consumedCallbacks = new AtomicInteger();
        final AtomicLong countedNodes = new AtomicLong();
        final AtomicInteger terminalPages = new AtomicInteger();

        final Consumer<GraphQlPageResponse> consumer =
                page -> {
                    try {
                        assertEquals(RECORDS_PER_PAGE, page.nodeCount());
                        final AtomicInteger pageNodes = new AtomicInteger();
                        page.forEachNode(
                                node -> {
                                    assertTrue(node.path("id").isIntegralNumber());
                                    assertTrue(node.path("name").asText().startsWith(FIXTURE_KIND));
                                    pageNodes.incrementAndGet();
                                });
                        scenario.gauge().pageConsumed(page, pageNodes.get());
                        countedNodes.addAndGet(pageNodes.get());
                        consumedCallbacks.incrementAndGet();
                        if (page.hasNextPage()) {
                            assertTrue(page.endCursor().isPresent());
                        } else {
                            terminalPages.incrementAndGet();
                            assertEquals(RECORDS_PER_PAGE, page.nodeCount());
                            assertTrue(page.endCursor().isEmpty());
                        }
                    } finally {
                        scenario.gauge().pageReleased(page);
                    }
                };

        final MeasurementRun run =
                MeasurementRun.execute(
                        scenario.plan(), scenario.gauge(), scenario.streamer(), consumer);
        return new HealthyExecution(
                scenario, run, consumedCallbacks.get(), countedNodes.get(), terminalPages.get());
    }

    @Test
    void rejectsARealExecutionWidePageRetentionMutantIndependentlyOfInFlightPages() {
        final int dataPages = 16;
        final Scenario scenario = newScenario(graphQlPlan(dataPages));
        final ExecutionWidePageRetentionMutant mutant =
                new ExecutionWidePageRetentionMutant(scenario.gauge());
        final Consumer<GraphQlPageResponse> retainingConsumer =
                page -> {
                    try {
                        scenario.gauge().pageConsumed(page, page.nodeCount());
                        mutant.accept(page);
                    } finally {
                        scenario.gauge().pageReleased(page);
                    }
                };

        final MeasurementRun run =
                MeasurementRun.execute(
                        scenario.plan(), scenario.gauge(), scenario.streamer(), retainingConsumer);
        try {
            assertEquals(dataPages, mutant.retainedPageCount());
            assertEquals(1L, run.evidence().maxInFlightPages());
            assertEquals(0L, run.evidence().finalInFlightPages());
            assertEquals(dataPages, run.evidence().maxRetainedPages());
            assertEquals(dataPages, run.evidence().finalRetainedPages());
            assertFalse(run.assessment().proven());
            assertEquals(
                    MeasurementAssessment.Disposition.REJECTED, run.assessment().disposition());
            assertEquals(
                    MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
                    run.assessment().reason());
            assertEquals(run.assessment(), new MeasurementEvaluator().evaluate(run.evidence()));
        } finally {
            mutant.clear();
        }

        assertEquals(0L, mutant.retainedPageCount());
        assertEquals(0L, scenario.gauge().snapshot(scenario.plan()).finalRetainedPages());
    }

    @Test
    void refusesTheNextLazyFetchBeforeGenerationUntilTheRealPageIsReleased() {
        final Scenario scenario = newScenario(graphQlPlan(16));
        final AtomicReference<GraphQlPageResponse> unreleasedPage = new AtomicReference<>();
        final Consumer<GraphQlPageResponse> consumer =
                page -> {
                    assertTrue(unreleasedPage.compareAndSet(null, page));
                    scenario.gauge().pageConsumed(page, page.nodeCount());
                };

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class, () -> scenario.streamer().stream(consumer));

        assertNotNull(failure.getMessage());
        assertEquals(1, scenario.lazyGateway().fetches());
        final MeasurementEvidence blocked = scenario.gauge().snapshot(scenario.plan());
        assertEquals(1L, blocked.acquisitions());
        assertEquals(1L, blocked.consumedPages());
        assertEquals(0L, blocked.releases());
        assertEquals(1L, blocked.finalInFlightPages());
        assertEquals(0L, blocked.finalRetainedPages());

        scenario.gauge().pageReleased(unreleasedPage.get());
        assertEquals(0L, scenario.gauge().snapshot(scenario.plan()).finalInFlightPages());
    }

    @Test
    void cursorCycleDetectorUsesOneFixedMebibyteWithoutGrowingWithPageCount() throws Exception {
        final Field hashFunctions =
                GraphQlCursorCycleDetector.class.getDeclaredField("HASH_FUNCTIONS");
        assertTrue(Modifier.isStatic(hashFunctions.getModifiers()));
        assertTrue(Modifier.isFinal(hashFunctions.getModifiers()));
        assertTrue(hashFunctions.trySetAccessible());
        assertEquals(6, hashFunctions.getInt(null));

        final Field[] instanceFields =
                Arrays.stream(GraphQlCursorCycleDetector.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .toArray(Field[]::new);
        assertEquals(2, instanceFields.length);
        assertEquals(
                1L,
                Arrays.stream(instanceFields)
                        .filter(field -> field.getType() == long[].class)
                        .count());
        assertEquals(
                1L,
                Arrays.stream(instanceFields)
                        .filter(field -> field.getType() == MessageDigest.class)
                        .count());
        for (final Field field : instanceFields) {
            assertTrue(field.getType() == long[].class || field.getType() == MessageDigest.class);
            assertFalse(Collection.class.isAssignableFrom(field.getType()));
            assertFalse(Map.class.isAssignableFrom(field.getType()));
            assertFalse(Set.class.isAssignableFrom(field.getType()));
        }

        final Field bloomWords =
                Arrays.stream(instanceFields)
                        .filter(field -> field.getType() == long[].class)
                        .findFirst()
                        .orElseThrow();
        assertTrue(bloomWords.trySetAccessible());
        final GraphQlCursorCycleDetector detector = new GraphQlCursorCycleDetector();
        final long[] initialWords = (long[]) bloomWords.get(detector);

        assertEquals(BLOOM_WORD_COUNT, initialWords.length);
        assertEquals(ONE_MEBIBYTE, Math.multiplyExact(initialWords.length, Long.BYTES));
        for (int page = 1; page <= 4096; page++) {
            detector.observe(GraphQlCursor.observed("cursor-" + page));
        }

        final long[] finalWords = (long[]) bloomWords.get(detector);
        assertSame(initialWords, finalWords);
        assertEquals(BLOOM_WORD_COUNT, finalWords.length);
        assertEquals(ONE_MEBIBYTE, Math.multiplyExact(finalWords.length, Long.BYTES));
    }

    private static MeasurementPlan graphQlPlan(final int dataPages) {
        return new MeasurementPlan(
                MeasurementPlan.StreamerKind.GRAPHQL, dataPages, RECORDS_PER_PAGE);
    }

    private static Scenario newScenario(final MeasurementPlan plan) {
        final int dataPages = plan.dataPages();
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final LazyGraphQlGateway lazyGateway = new LazyGraphQlGateway(dataPages);
        final CancellationToken cancellation = CancellationToken.none();
        final GraphQlGateway contractGateway =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                cancellation,
                                request ->
                                        GraphQlTestSupport.observed(
                                                fixture, lazyGateway.fetch(request))),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        final AtomicLong measuredBytes = new AtomicLong();
        final GraphQlGateway measuredGateway =
                new MeasuringGraphQlGateway(contractGateway, cancellation, gauge, measuredBytes);
        final GraphQlPageStreamer productiveStreamer =
                new GraphQlPageStreamer(
                        measuredGateway,
                        GraphQlExtractionAudit.noop(),
                        Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC));
        final GraphQlPageRequest initialRequest =
                GraphQlPageRequest.initial(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlQueryParameters.enabledUsers(),
                        RECORDS_PER_PAGE);
        final GraphQlExtractionLimits limits =
                new GraphQlExtractionLimits(
                        dataPages, Math.multiplyExact((long) dataPages, RECORDS_PER_PAGE));
        final AtomicReference<GraphQlExtractionResult> extractionResult = new AtomicReference<>();
        final MeasurementStreamer<GraphQlPageResponse> streamer =
                consumer ->
                        extractionResult.set(
                                productiveStreamer.stream(
                                        fixture.guard().executionContext(),
                                        initialRequest,
                                        limits,
                                        cancellation,
                                        page -> consumer.accept(page)));
        return new Scenario(plan, gauge, lazyGateway, measuredBytes, extractionResult, streamer);
    }

    private static void assertDiagnosticsAreInformational(
            final MeasurementDiagnostics diagnostics) {
        assertTrue(diagnostics.heapBeforeBytes() >= 0L);
        assertTrue(diagnostics.heapAfterBytes() >= 0L);
        assertTrue(diagnostics.heapPeakBytes() >= diagnostics.heapBeforeBytes());
        assertTrue(diagnostics.heapPeakBytes() >= diagnostics.heapAfterBytes());
        assertTrue(diagnostics.durationNanos() >= 0L);
    }

    private record Scenario(
            MeasurementPlan plan,
            ManagedPageGauge gauge,
            LazyGraphQlGateway lazyGateway,
            AtomicLong measuredBytes,
            AtomicReference<GraphQlExtractionResult> extractionResult,
            MeasurementStreamer<GraphQlPageResponse> streamer) {}

    private record HealthyExecution(
            Scenario scenario,
            MeasurementRun run,
            int consumedCallbacks,
            long countedNodes,
            int terminalPages) {}

    private static final class MeasuringGraphQlGateway implements GraphQlGateway {

        private final GraphQlGateway delegate;
        private final CancellationToken cancellation;
        private final ManagedPageGauge gauge;
        private final AtomicLong measuredBytes;

        private MeasuringGraphQlGateway(
                final GraphQlGateway delegate,
                final CancellationToken cancellation,
                final ManagedPageGauge gauge,
                final AtomicLong measuredBytes) {
            this.delegate = delegate;
            this.cancellation = cancellation;
            this.gauge = gauge;
            this.measuredBytes = measuredBytes;
        }

        @Override
        public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
            gauge.beforeFetch();
            final GraphQlPageResponse realPage = delegate.fetch(request);
            gauge.pageFetched(realPage, realPage.responseBytes());
            measuredBytes.addAndGet(realPage.responseBytes());
            return realPage;
        }

        @Override
        public void verifyCancellationToken(final CancellationToken candidate) {
            assertSame(cancellation, candidate);
            delegate.verifyCancellationToken(candidate);
        }
    }

    private static final class LazyGraphQlGateway implements GraphQlGateway {

        private final int dataPages;
        private final AtomicInteger fetches = new AtomicInteger();
        private int terminalPageNumber;
        private int terminalPageNodeCount;
        private boolean terminalPageHasNext = true;

        private LazyGraphQlGateway(final int dataPages) {
            this.dataPages = dataPages;
        }

        @Override
        public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
            final int pageNumber = fetches.incrementAndGet();
            assertTrue(pageNumber <= dataPages, "GraphQL generated a page beyond the plan");
            assertEquals(RECORDS_PER_PAGE, request.pageSize());
            if (pageNumber == 1) {
                assertTrue(request.after().isEmpty());
            } else {
                assertEquals("cursor-" + (pageNumber - 1), request.after().orElseThrow().value());
            }

            final ArrayNode nodes = GraphQlTestSupport.MAPPER.createArrayNode();
            for (int offset = 0; offset < RECORDS_PER_PAGE; offset++) {
                final long id =
                        Math.addExact(
                                Math.multiplyExact((long) pageNumber - 1L, RECORDS_PER_PAGE),
                                offset + 1L);
                nodes.addObject().put("id", id).put("name", FIXTURE_KIND + "_USER_" + id);
            }
            final boolean hasNextPage = pageNumber < dataPages;
            final GraphQlPageResponse generated =
                    new GraphQlPageResponse(
                            nodes,
                            hasNextPage,
                            hasNextPage
                                    ? Optional.of(GraphQlCursor.observed("cursor-" + pageNumber))
                                    : Optional.empty());
            final GraphQlPageResponse page = generated.withResponseBytes(serializedBytes(nodes));
            if (!hasNextPage) {
                terminalPageNumber = pageNumber;
                terminalPageNodeCount = page.nodeCount();
                terminalPageHasNext = page.hasNextPage();
            }
            return page;
        }

        private int fetches() {
            return fetches.get();
        }

        private int terminalPageNumber() {
            return terminalPageNumber;
        }

        private int terminalPageNodeCount() {
            return terminalPageNodeCount;
        }

        private boolean terminalPageHasNext() {
            return terminalPageHasNext;
        }

        private static long serializedBytes(final ArrayNode nodes) {
            try {
                return GraphQlTestSupport.MAPPER.writeValueAsBytes(nodes).length;
            } catch (final JsonProcessingException exception) {
                throw new AssertionError(
                        "Synthetic GraphQL fixture could not be serialized", exception);
            }
        }
    }
}
