package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataExportPageStreamerProgressTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsReplayBeforeConsumerOrAnotherFetchAndInvalidatesTheContractContext() {
        final QueueGateway gateway = new QueueGateway(page("1", "2"), page("1", "2"), page());
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();
        final ContractRunGuard contractGuard = contractGuard();

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                contractGuard.executionContext(),
                                                request(2),
                                                new DataExportExtractionLimits(3, 10, 2),
                                                delivered::add));

        assertTrue(failure.getMessage().startsWith("DATAEXPORT_PAGE_PROGRESS_NOT_OBSERVED:"));
        assertEquals(List.of(1, 2), gateway.requestedPages);
        assertEquals(1, delivered.size());
        assertEquals(List.of("started", "page:1", "page:2", "failed"), audit.events);
        assertEquals(2, audit.failure.pagesFetched());
        assertEquals(2L, audit.failure.recordsDelivered());
        assertEquals(DataExportFailureCategory.RUNTIME_FAILURE, audit.failure.failureCategory());
        final ContractDriftException terminal =
                assertThrows(
                        ContractDriftException.class,
                        () -> contractGuard.validateMetadata(ContractTestSupport.metadata()));
        assertEquals(ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL, terminal.reason());
    }

    @Test
    void detectsAnABACycleWithoutDeliveringTheThirdPage() {
        final QueueGateway gateway = new QueueGateway(page("A"), page("B"), page("A"), page());
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                context(),
                                                request(1),
                                                new DataExportExtractionLimits(4, 10, 1),
                                                delivered::add));

        assertTrue(failure.getMessage().startsWith("DATAEXPORT_PAGE_PROGRESS_NOT_OBSERVED:"));
        assertEquals(List.of(1, 2, 3), gateway.requestedPages);
        assertEquals(2, delivered.size());
        assertEquals(3, audit.failure.pagesFetched());
        assertEquals(2L, audit.failure.recordsDelivered());
        assertEquals(List.of("started", "page:1", "page:2", "page:3", "failed"), audit.events);
    }

    @Test
    void aShortPageStillNeedsTheEmptyTerminalPage() {
        final QueueGateway gateway = new QueueGateway(page("A"), page());
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();

        final DataExportExtractionResult result =
                new DataExportPageStreamer(gateway, audit, CLOCK)
                        .stream(
                                context(),
                                request(2),
                                new DataExportExtractionLimits(3, 10, 2),
                                delivered::add);

        assertEquals(List.of(1, 2), gateway.requestedPages);
        assertEquals(1, delivered.size());
        assertEquals(2, result.pagesFetched());
        assertEquals(1L, result.recordsDelivered());
        assertEquals(List.of("started", "page:1", "page:2", "completed"), audit.events);
    }

    @Test
    void createsFreshProgressStateForEachStreamOnTheSameStreamer() {
        final QueueGateway gateway = new QueueGateway(page("A"), page(), page("A"), page());
        final CapturingAudit audit = new CapturingAudit();
        final DataExportPageStreamer streamer = new DataExportPageStreamer(gateway, audit, CLOCK);
        final List<DataExportReadPage> delivered = new ArrayList<>();

        streamer.stream(
                context(), request(1), new DataExportExtractionLimits(2, 10, 1), delivered::add);
        streamer.stream(
                context(), request(1), new DataExportExtractionLimits(2, 10, 1), delivered::add);

        assertEquals(List.of(1, 2, 1, 2), gateway.requestedPages);
        assertEquals(2, delivered.size());
        assertEquals(2, audit.events.stream().filter("completed"::equals).count());
    }

    @Test
    void recordCapTakesPrecedenceOverReplayOnTheSamePage() {
        final QueueGateway gateway = new QueueGateway(page("A"), page("A"), page());
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                context(),
                                                request(1),
                                                new DataExportExtractionLimits(3, 1, 1),
                                                delivered::add));

        assertEquals(
                "A extração Data Export excederia o limite de 1 registros antes de entregar a"
                        + " página.",
                failure.getMessage());
        assertEquals(List.of(1, 2), gateway.requestedPages);
        assertEquals(1, delivered.size());
        assertEquals(1L, audit.failure.recordsDelivered());
    }

    @Test
    void entityPerValidationTakesPrecedenceOverProgressHashing() {
        final JsonNode unsupported =
                JsonNodeFactory.instance.objectNode().put("id", "B").putPOJO("value", new Object());
        final QueueGateway gateway =
                new QueueGateway(
                        new DataExportPageResponse(
                                List.of(
                                        JsonNodeFactory.instance.objectNode().put("id", "A"),
                                        unsupported)),
                        page());
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                context(),
                                                request(1),
                                                new DataExportExtractionLimits(2, 10, 1),
                                                delivered::add));

        assertTrue(failure.getMessage().contains("acima do per solicitado de 1."));
        assertEquals(List.of(1), gateway.requestedPages);
        assertTrue(delivered.isEmpty());
        assertEquals(List.of("started", "failed"), audit.events);
        assertEquals(0L, audit.failure.recordsDelivered());
    }

    @Test
    void pageCapStillStopsWithoutFetchingAnUnreservedPage() {
        final QueueGateway gateway = new QueueGateway(page("A"), page("A"));
        final CapturingAudit audit = new CapturingAudit();
        final List<DataExportReadPage> delivered = new ArrayList<>();

        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new DataExportPageStreamer(gateway, audit, CLOCK)
                                        .stream(
                                                context(),
                                                request(1),
                                                new DataExportExtractionLimits(1, 10, 1),
                                                delivered::add));

        assertEquals(
                "A extração Data Export excedeu o limite de 1 páginas sem página terminal vazia.",
                failure.getMessage());
        assertEquals(List.of(1), gateway.requestedPages);
        assertEquals(1, delivered.size());
        assertEquals(1L, audit.failure.recordsDelivered());
        assertEquals(List.of("started", "page:1", "failed"), audit.events);
    }

    private static ContractRunGuard contractGuard() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        return new ContractRunGuard(
                binding,
                release,
                policy,
                ContractTestSupport.controlPlaneStart(binding),
                alert -> {});
    }

    private static ContractExecutionContext context() {
        return ContractTestSupport.executionContext(UUID.randomUUID());
    }

    private static DataExportPageRequest request(final int per) {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)),
                Optional.empty(),
                1,
                per,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    private static DataExportPageResponse page(final String... ids) {
        final List<JsonNode> records = new ArrayList<>();
        for (final String id : ids) {
            records.add(JsonNodeFactory.instance.objectNode().put("id", id));
        }
        return new DataExportPageResponse(records);
    }

    private static final class QueueGateway implements DataExportGateway {

        private final List<DataExportPageResponse> responses;
        private final List<Integer> requestedPages = new ArrayList<>();
        private int nextResponse;

        private QueueGateway(final DataExportPageResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public DataExportPageResponse fetch(final DataExportPageRequest request) {
            requestedPages.add(request.page());
            if (nextResponse >= responses.size()) {
                throw new AssertionError("Nenhum fetch adicional foi autorizado no cenário.");
            }
            return responses.get(nextResponse++);
        }
    }

    private static final class CapturingAudit implements DataExportExtractionAudit {

        private final List<String> events = new ArrayList<>();
        private ExecutionFailed failure;

        @Override
        public void executionStarted(final ExecutionStarted event) {
            events.add("started");
        }

        @Override
        public void pageRead(final PageRead event) {
            events.add("page:" + event.page());
        }

        @Override
        public void executionCompleted(final DataExportExtractionResult result) {
            events.add("completed");
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {
            events.add("failed");
            failure = event;
        }
    }
}
