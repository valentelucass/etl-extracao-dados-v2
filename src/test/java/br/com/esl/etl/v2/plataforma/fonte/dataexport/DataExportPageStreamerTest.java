package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.observabilidade.CorrelationReference;
import br.com.esl.etl.v2.plataforma.observabilidade.StructuredCorrelationContext;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataExportPageStreamerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T15:30:00Z"), ZoneOffset.UTC);
    private static final ContractExecutionContext EXECUTION_CONTEXT =
            ContractTestSupport.executionContext(
                    UUID.fromString("00000000-0000-0000-0000-000000000123"));

    @Test
    void streamsEachPageAndOnlyCompletesAfterAnEmptyTerminalPage() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1", "2"), page("3"), page()));
        final CapturingAudit audit = new CapturingAudit();
        final List<String> deliveredIds = new ArrayList<>();

        final DataExportExtractionResult result =
                new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                        .stream(
                                EXECUTION_CONTEXT,
                                request(DataExportTemplate.COLETAS, 2),
                                new DataExportExtractionLimits(3, 10, 2),
                                readPage ->
                                        readPage.response()
                                                .records()
                                                .forEach(
                                                        record ->
                                                                deliveredIds.add(
                                                                        record.path("id")
                                                                                .asText())));

        assertEquals(List.of(1, 2, 3), gateway.requestedPages());
        assertEquals(List.of("1", "2", "3"), deliveredIds);
        assertEquals(3, result.pagesFetched());
        assertEquals(3L, result.recordsDelivered());
        assertEquals(3, result.terminalPage());
        assertEquals(
                DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED,
                result.traversalVerification());
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        assertEquals(
                List.of("started", "page:1:2", "page:2:1", "page:3:0", "completed"),
                audit.events());
        assertEquals(0, audit.failedEvents().size());
    }

    @Test
    void failsInsteadOfDeclaringSuccessWhenThePageLimitIsReached() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1", "2"), page("3", "4")));
        final CapturingAudit audit = new CapturingAudit();
        final List<String> deliveredIds = new ArrayList<>();

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                        .stream(
                                                EXECUTION_CONTEXT,
                                                request(DataExportTemplate.FRETES, 2),
                                                new DataExportExtractionLimits(2, 10, 2),
                                                readPage ->
                                                        readPage.response()
                                                                .records()
                                                                .forEach(
                                                                        record ->
                                                                                deliveredIds.add(
                                                                                        record.path(
                                                                                                        "id")
                                                                                                .asText()))));

        assertEquals(
                "A extração Data Export excedeu o limite de 2 páginas sem página terminal vazia.",
                exception.getMessage());
        assertEquals(List.of(1, 2), gateway.requestedPages());
        assertEquals(List.of("1", "2", "3", "4"), deliveredIds);
        assertEquals(List.of("started", "page:1:2", "page:2:2", "failed"), audit.events());
        assertEquals(
                DataExportFailureCategory.RUNTIME_FAILURE,
                audit.failedEvents().get(0).failureCategory());
        assertEquals(2, audit.failedEvents().get(0).pagesFetched());
        assertEquals(4L, audit.failedEvents().get(0).recordsDelivered());
    }

    @Test
    void rejectsAPageThatWouldExceedTheRecordLimitBeforeDeliveringIt() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1", "2")));
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> deliveredPages = new ArrayList<>();

        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 2),
                                        new DataExportExtractionLimits(3, 1, 2),
                                        deliveredPages::add));

        assertEquals(List.of(), deliveredPages);
        assertEquals(List.of("started", "page:1:2", "failed"), audit.events());
        assertEquals(1, audit.failedEvents().get(0).pagesFetched());
        assertEquals(0L, audit.failedEvents().get(0).recordsDelivered());
    }

    @Test
    void recordsGatewayFailureWithoutACompletionEvent() {
        final CapturingAudit audit = new CapturingAudit();
        final DataExportGateway gateway =
                request -> {
                    throw new DataExportUnavailableException(
                            request.template().templateId(), "HTTP 503");
                };

        assertThrows(
                DataExportUnavailableException.class,
                () ->
                        new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.FRETES, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        response -> {}));

        assertEquals(List.of("started", "failed"), audit.events());
        assertEquals(0, audit.failedEvents().get(0).pagesFetched());
        assertEquals(
                DataExportFailureCategory.SOURCE_UNAVAILABLE,
                audit.failedEvents().get(0).failureCategory());
    }

    @Test
    void cancelsBeforeTheNextPageAndAuditsTheTypedCategory() {
        final CapturingAudit audit = new CapturingAudit();
        final CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        new DataExportPageStreamer(
                                        new QueueGateway(List.of(page("1"))), audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        cancellation,
                                        ignored -> {
                                            throw new AssertionError(
                                                    "Página não deveria ser entregue.");
                                        }));

        assertEquals(List.of("started", "failed"), audit.events());
        assertEquals(
                DataExportFailureCategory.CANCELLED, audit.failedEvents().get(0).failureCategory());
    }

    @Test
    void cancellationRequestedDuringTerminalFetchPreventsCompletion() {
        final CapturingAudit audit = new CapturingAudit();
        final CancellationSignal cancellation = new CancellationSignal();
        final DataExportGateway gateway =
                request -> {
                    cancellation.cancel();
                    return page();
                };

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 2),
                                        new DataExportExtractionLimits(1, 10, 2),
                                        cancellation,
                                        ignored -> {
                                            throw new AssertionError(
                                                    "Página terminal não deveria ser entregue.");
                                        }));

        assertEquals(List.of("started", "failed"), audit.events());
        assertEquals(0, audit.failedEvents().get(0).pagesFetched());
        assertEquals(
                DataExportFailureCategory.CANCELLED, audit.failedEvents().get(0).failureCategory());
    }

    @Test
    void cancellationRequestedByConsumerDominatesThePageCap() {
        final CapturingAudit audit = new CapturingAudit();
        final CancellationSignal cancellation = new CancellationSignal();

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        new DataExportPageStreamer(
                                        new QueueGateway(List.of(page("1"))), audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 1),
                                        new DataExportExtractionLimits(1, 10, 1),
                                        cancellation,
                                        ignored -> cancellation.cancel()));

        assertEquals(List.of("started", "page:1:1", "failed"), audit.events());
        assertEquals(1, audit.failedEvents().get(0).pagesFetched());
        assertEquals(1L, audit.failedEvents().get(0).recordsDelivered());
        assertEquals(
                DataExportFailureCategory.CANCELLED, audit.failedEvents().get(0).failureCategory());
    }

    @Test
    void nullGatewayResponseFailsWithASanitizedStableMessage() {
        final CapturingAudit audit = new CapturingAudit();

        final NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new DataExportPageStreamer(request -> null, audit, FIXED_CLOCK)
                                        .stream(
                                                EXECUTION_CONTEXT,
                                                request(DataExportTemplate.COLETAS, 1),
                                                new DataExportExtractionLimits(1, 10, 1),
                                                ignored -> {}));

        assertEquals("O gateway Data Export retornou resposta nula.", exception.getMessage());
        assertEquals(List.of("started", "failed"), audit.events());
        assertEquals(0, audit.failedEvents().get(0).pagesFetched());
        assertEquals(
                DataExportFailureCategory.RUNTIME_FAILURE,
                audit.failedEvents().get(0).failureCategory());
    }

    @Test
    void rejectsMoreDistinctEntitiesThanTheRequestedPageBeforeDeliveringIt() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1", "2", "3")));
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> deliveredPages = new ArrayList<>();

        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.FRETES, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        deliveredPages::add));

        assertEquals(List.of(), deliveredPages);
        assertEquals(List.of("started", "failed"), audit.events());
        assertEquals(1, audit.failedEvents().get(0).pagesFetched());
    }

    @Test
    void deliversExpandedRowsWhenTheDistinctEntitiesFitTheRequestedPageSize() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1", "1", "2"), page()));
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> deliveredPages = new ArrayList<>();

        final DataExportExtractionResult result =
                new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                        .stream(
                                EXECUTION_CONTEXT,
                                request(DataExportTemplate.COLETAS, 2),
                                new DataExportExtractionLimits(2, 10, 2),
                                deliveredPages::add);

        assertEquals(1, deliveredPages.size());
        assertEquals(3, deliveredPages.get(0).response().records().size());
        assertEquals(3L, result.recordsDelivered());
        assertEquals(List.of("started", "page:1:3", "page:2:0", "completed"), audit.events());
        assertEquals(2, audit.pageEvents().size());
        assertEquals(2, audit.pageEvents().get(0).requestedPageSize());
        assertEquals(3, audit.pageEvents().get(0).recordCount());
        assertEquals(2, audit.pageEvents().get(0).distinctEntityCount());
        assertEquals(0, audit.pageEvents().get(1).recordCount());
        assertEquals(0, audit.pageEvents().get(1).distinctEntityCount());
    }

    @Test
    void recordsFailureWhenTheConsumerRejectsAPage() {
        final QueueGateway gateway = new QueueGateway(List.of(page("1")));
        final CapturingAudit audit = new CapturingAudit();

        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(gateway, audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        response -> {
                                            throw new IllegalStateException(
                                                    "Consumidor indisponível.");
                                        }));

        assertEquals(List.of("started", "page:1:1", "failed"), audit.events());
        assertEquals(0L, audit.failedEvents().get(0).recordsDelivered());
    }

    @Test
    void doesNotCompleteWhenTheAuditOfAPageFails() {
        final AtomicInteger completedEvents = new AtomicInteger();
        final AtomicInteger failedEvents = new AtomicInteger();
        final DataExportExtractionAudit audit =
                new DataExportExtractionAudit() {
                    @Override
                    public void executionStarted(final ExecutionStarted event) {}

                    @Override
                    public void pageRead(final PageRead event) {
                        throw new IllegalStateException("Auditoria indisponível.");
                    }

                    @Override
                    public void executionCompleted(final DataExportExtractionResult result) {
                        completedEvents.incrementAndGet();
                    }

                    @Override
                    public void executionFailed(final ExecutionFailed event) {
                        failedEvents.incrementAndGet();
                    }
                };

        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(
                                        new QueueGateway(List.of(page("1"))), audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.COLETAS, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        response -> {}));

        assertEquals(0, completedEvents.get());
        assertEquals(1, failedEvents.get());
    }

    @Test
    void doesNotEmitAContradictoryFailureAfterCompletionAuditThrows() {
        final AtomicInteger completionEvents = new AtomicInteger();
        final AtomicInteger failureEvents = new AtomicInteger();
        final DataExportExtractionAudit audit =
                new DataExportExtractionAudit() {
                    @Override
                    public void executionStarted(final ExecutionStarted event) {}

                    @Override
                    public void pageRead(final PageRead event) {}

                    @Override
                    public void executionCompleted(final DataExportExtractionResult result) {
                        completionEvents.incrementAndGet();
                        throw new IllegalStateException("Persistência da conclusão indisponível.");
                    }

                    @Override
                    public void executionFailed(final ExecutionFailed event) {
                        failureEvents.incrementAndGet();
                    }
                };

        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(
                                        new QueueGateway(List.of(page())), audit, FIXED_CLOCK)
                                .stream(
                                        EXECUTION_CONTEXT,
                                        request(DataExportTemplate.FRETES, 2),
                                        new DataExportExtractionLimits(3, 10, 2),
                                        readPage -> {}));

        assertEquals(1, completionEvents.get());
        assertEquals(0, failureEvents.get());
    }

    @Test
    void closesTheContractGateBeforePublishingACompletionAudit() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        final UUID executionId = UUID.randomUUID();
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {});
        guard.validateMetadata(release.metadata());
        guard.observeDataExportResponse(1, release.response());
        final CapturingAudit audit = new CapturingAudit();

        final ContractDriftException blocked =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                new DataExportPageStreamer(
                                                new QueueGateway(List.of(page())),
                                                audit,
                                                FIXED_CLOCK)
                                        .stream(
                                                guard.executionContext(),
                                                request(DataExportTemplate.COLETAS, 2),
                                                new DataExportExtractionLimits(2, 10, 2),
                                                readPage -> {}));

        assertEquals(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                blocked.reason());
        assertEquals(List.of("started", "page:1:0", "failed"), audit.events());
        assertEquals(
                DataExportFailureCategory.CONTRACT_DRIFT,
                audit.failedEvents().get(0).failureCategory());
        assertThrows(ContractDriftException.class, guard::complete);
    }

    @Test
    void advancesPageWithoutMovingTheUpdateScopeInsideTheEntityRoot() {
        final SourceDateTimeRange updatedAtWindow =
                new SourceDateTimeRange(
                        Instant.parse("2026-08-24T00:00:00Z"),
                        Instant.parse("2026-08-24T00:01:00Z"));
        final DataExportPageRequest firstPage =
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS,
                        new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                        Optional.of(updatedAtWindow),
                        1,
                        2,
                        List.of("request_date asc"));

        final DataExportPageRequest secondPage = firstPage.withPage(2);

        assertEquals(2, secondPage.page());
        assertEquals(firstPage.template(), secondPage.template());
        assertEquals(firstPage.businessDateWindow(), secondPage.businessDateWindow());
        assertEquals(firstPage.updatedAtWindow(), secondPage.updatedAtWindow());
        assertEquals(firstPage.orderBy(), secondPage.orderBy());
        assertEquals(
                updatedAtWindow,
                secondPage.filters().get(new SearchPath("scopes", "by_updated_at")));
        assertEquals(2, secondPage.filters().size());
    }

    @Test
    void suppliesPageContextAndRestoresThePreviousLogCorrelation() {
        final CorrelationReference outer =
                CorrelationReference.fromTechnicalScope("OUTER_EXECUTION");
        final StructuredCorrelationContext.Scope outerScope =
                StructuredCorrelationContext.open(outer);
        try {
            final List<String> gatewayCorrelations = new ArrayList<>();
            final List<DataExportReadPage> deliveredPages = new ArrayList<>();
            final DataExportGateway gateway =
                    request -> {
                        gatewayCorrelations.add(
                                StructuredCorrelationContext.current().orElseThrow().sha256());
                        return request.page() == 1 ? page("1") : page();
                    };

            final DataExportExtractionResult result =
                    new DataExportPageStreamer(
                                    gateway, DataExportExtractionAudit.noop(), FIXED_CLOCK)
                            .stream(
                                    EXECUTION_CONTEXT,
                                    request(DataExportTemplate.COLETAS, 2),
                                    new DataExportExtractionLimits(3, 10, 2),
                                    deliveredPages::add);

            assertEquals(
                    List.of(
                            CorrelationReference.fromExecutionId(result.executionId()).sha256(),
                            CorrelationReference.fromExecutionId(result.executionId()).sha256()),
                    gatewayCorrelations);
            assertEquals(1, deliveredPages.size());
            assertEquals(result.executionId(), deliveredPages.get(0).executionId());
            assertEquals(1, deliveredPages.get(0).page());
            assertEquals(outer, StructuredCorrelationContext.current().orElseThrow());
        } finally {
            outerScope.close();
        }
        assertTrue(StructuredCorrelationContext.current().isEmpty());
    }

    private static DataExportPageRequest request(
            final DataExportTemplate template, final int pageSize) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                Optional.empty(),
                1,
                pageSize,
                template.defaultOrderBy());
    }

    private static DataExportPageResponse page(final String... identifiers) {
        final List<com.fasterxml.jackson.databind.JsonNode> records = new ArrayList<>();
        for (final String identifier : identifiers) {
            records.add(JsonNodeFactory.instance.objectNode().put("id", identifier));
        }
        return new DataExportPageResponse(records);
    }

    private static final class QueueGateway implements DataExportGateway {

        private final List<DataExportPageResponse> responses;
        private final List<Integer> requestedPages = new ArrayList<>();
        private int nextResponse;

        private QueueGateway(final List<DataExportPageResponse> responses) {
            this.responses = List.copyOf(responses);
        }

        @Override
        public DataExportPageResponse fetch(final DataExportPageRequest request) {
            requestedPages.add(request.page());
            if (nextResponse >= responses.size()) {
                throw new IllegalStateException("Resposta de teste ausente.");
            }
            return responses.get(nextResponse++);
        }

        private List<Integer> requestedPages() {
            return List.copyOf(requestedPages);
        }
    }

    private static final class CapturingAudit implements DataExportExtractionAudit {

        private final List<String> events = new ArrayList<>();
        private final List<ExecutionFailed> failedEvents = new ArrayList<>();
        private final List<PageRead> pageEvents = new ArrayList<>();

        @Override
        public void executionStarted(final ExecutionStarted event) {
            events.add("started");
        }

        @Override
        public void pageRead(final PageRead event) {
            events.add("page:" + event.page() + ":" + event.recordCount());
            pageEvents.add(event);
        }

        @Override
        public void executionCompleted(final DataExportExtractionResult result) {
            events.add("completed");
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {
            events.add("failed");
            failedEvents.add(event);
        }

        private List<String> events() {
            return List.copyOf(events);
        }

        private List<ExecutionFailed> failedEvents() {
            return List.copyOf(failedEvents);
        }

        private List<PageRead> pageEvents() {
            return List.copyOf(pageEvents);
        }
    }
}
