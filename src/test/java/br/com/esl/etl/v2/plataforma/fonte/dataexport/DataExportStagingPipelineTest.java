package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.ExtrairFretesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DataExportStagingPipelineTest {

    private static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final DataExportExtractionLimits LIMITS =
            new DataExportExtractionLimits(4, 1000, 100);

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void integratesRealMappersAndStagingWithExpandedPages(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 251, 3, 0);
        final DataExportExtractionResult result = fixture.runVertical(CancellationToken.none());

        assertEquals(List.of(100, 100, 51, 3), fixture.batchSizes);
        assertEquals(List.of(1, 2, 3, 4), fixture.batchNumbers);
        assertEquals(List.of(1, 2, 3), fixture.requestedPages);
        assertEquals(254, fixture.stagedRows);
        assertEquals(254, result.recordsDelivered());
        assertEquals(1, fixture.audit.completed);
        assertEquals(0, fixture.audit.failed);
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        assertEquals(fixture.executionId, fixture.guard.complete().executionId());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void failedSecondBatchStopsThePageAndInvalidatesPromotion(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 251, 3, 0);
        final IllegalStateException failure =
                new IllegalStateException("synthetic-staging-failure");
        fixture.afterBatch =
                number -> {
                    if (number == 2) {
                        throw failure;
                    }
                };

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> fixture.runVertical(CancellationToken.none())));
        assertEquals(List.of(1), fixture.requestedPages);
        assertEquals(List.of(100, 100), fixture.batchSizes);
        assertEquals(0, fixture.audit.completed);
        assertEquals(1, fixture.audit.failed);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void cancellationAfterBatchStopsRemainingRowsAndPages(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 251, 3, 0);
        final CancellationSignal signal = new CancellationSignal();
        fixture.afterBatch = number -> signal.cancel();

        assertThrows(ResilienceCancelledException.class, () -> fixture.runVertical(signal));
        assertEquals(List.of(100), fixture.batchSizes);
        assertEquals(List.of(1), fixture.requestedPages);
        assertEquals(0, fixture.audit.completed);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void precancelledExecutionDoesNotFetchOrStage(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 3, 0);
        final CancellationSignal signal = new CancellationSignal();
        signal.cancel();

        assertThrows(ResilienceCancelledException.class, () -> fixture.runVertical(signal));
        assertTrue(fixture.requestedPages.isEmpty());
        assertEquals(0, fixture.stagedRows);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void terminalAuditFailureInvalidatesAllPreviouslyStagedData(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 3, 0);
        fixture.audit.failCompletion = true;

        assertThrows(
                IllegalStateException.class, () -> fixture.runVertical(CancellationToken.none()));
        assertEquals(3, fixture.stagedRows);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void emptyTraversalDoesNotProduceAnEmptyBatchOrPromotion(final DataExportTemplate template) {
        final Fixture fixture = new Fixture(template, 0);
        final var result = fixture.runVertical(CancellationToken.none());

        assertEquals(0, result.recordsDelivered());
        assertTrue(fixture.batchSizes.isEmpty());
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void newAttemptStartsAtPageOneAndBatchOne(final DataExportTemplate template) {
        final Fixture failed = new Fixture(template, 251, 0);
        failed.afterBatch =
                number -> {
                    throw new IllegalStateException("synthetic-failure");
                };
        assertThrows(
                IllegalStateException.class, () -> failed.runVertical(CancellationToken.none()));

        final Fixture retry = new Fixture(template, 251, 0);
        retry.runVertical(CancellationToken.none());
        assertNotEquals(failed.executionId, retry.executionId);
        assertEquals(List.of(1, 2), retry.requestedPages);
        assertEquals(List.of(1, 2, 3), retry.batchNumbers);
        assertEquals(251, retry.stagedRows);
        assertEquals(retry.executionId, retry.guard.complete().executionId());
    }

    @Test
    void cancellationInsideMapperCannotStageTheJustMappedRecord() {
        final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        final CancellationSignal signal = new CancellationSignal();
        final AtomicInteger mapped = new AtomicInteger();
        final var pipeline =
                fixture.pipeline(
                        (ordinal, record) -> {
                            mapped.incrementAndGet();
                            signal.cancel();
                            return record;
                        });

        assertThrows(
                ResilienceCancelledException.class,
                () -> pipeline.execute(fixture.guard, fixture.request(), LIMITS, signal));
        assertEquals(1, mapped.get());
        assertEquals(0, fixture.stagedRows);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @Test
    void mapperFailureOrNullCannotProduceSuccess() {
        for (final boolean returnsNull : List.of(false, true)) {
            final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
            final var pipeline =
                    fixture.pipeline(
                            (ordinal, record) -> {
                                if (returnsNull) {
                                    return null;
                                }
                                throw new IllegalStateException("synthetic-mapping-failure");
                            });
            assertThrows(
                    RuntimeException.class,
                    () ->
                            pipeline.execute(
                                    fixture.guard,
                                    fixture.request(),
                                    LIMITS,
                                    CancellationToken.none()));
            assertEquals(0, fixture.stagedRows);
            assertEquals(1, fixture.audit.failed);
            assertThrows(ContractDriftException.class, fixture.guard::complete);
        }
    }

    @Test
    void pageOrRecordBudgetFailureCannotAuthorizePromotion() {
        for (final var limits :
                List.of(
                        new DataExportExtractionLimits(1, 1000, 100),
                        new DataExportExtractionLimits(4, 2, 100))) {
            final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            fixture.pipeline((ordinal, record) -> record)
                                    .execute(
                                            fixture.guard,
                                            fixture.request(),
                                            limits,
                                            CancellationToken.none()));
            assertEquals(List.of(1), fixture.requestedPages);
            assertEquals(0, fixture.audit.completed);
            assertThrows(ContractDriftException.class, fixture.guard::complete);
        }
    }

    @Test
    void wrongTemplateAndSkippedInitialPageAreRejectedBeforeIo() {
        for (final int invalidCase : List.of(0, 1, 2)) {
            final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
            final var request =
                    invalidCase == 0
                            ? request(DataExportTemplate.FRETES)
                            : invalidCase == 1 ? fixture.request().withPage(2) : fixture.request();
            final var guard =
                    invalidCase == 2
                            ? new Fixture(DataExportTemplate.FRETES, 3, 0).guard
                            : fixture.guard;
            assertThrows(
                    RuntimeException.class,
                    () ->
                            fixture.pipeline((ordinal, record) -> record)
                                    .execute(guard, request, LIMITS, CancellationToken.none()));
            assertTrue(fixture.requestedPages.isEmpty());
            assertEquals(0, fixture.stagedRows);
            assertThrows(ContractDriftException.class, guard::complete);
        }
    }

    @Test
    void anotherGuardOfTheSameTemplateCannotReceiveTheStagedRows() {
        final Fixture source = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        final Fixture otherOccurrence = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        assertThrows(
                ContractDriftException.class,
                () ->
                        source.pipeline((ordinal, record) -> record)
                                .execute(
                                        otherOccurrence.guard,
                                        source.request(),
                                        LIMITS,
                                        CancellationToken.none()));
        assertEquals(0, source.stagedRows);
        assertEquals(0, source.audit.completed);
        assertThrows(ContractDriftException.class, otherOccurrence.guard::complete);
    }

    @Test
    void missingTerminalContractObservationCannotReturnSuccess() {
        final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        fixture.observeTerminal = false;
        assertThrows(
                ContractDriftException.class, () -> fixture.runVertical(CancellationToken.none()));
        assertEquals(3, fixture.stagedRows);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @Test
    void cancellationDuringTerminalAuditInvalidatesTheCompletedTraversal() {
        final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        final CancellationSignal signal = new CancellationSignal();
        fixture.audit.afterCompletion = signal::cancel;
        assertThrows(ResilienceCancelledException.class, () -> fixture.runVertical(signal));
        assertEquals(3, fixture.stagedRows);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
    }

    @Test
    void unapprovedOrderingDoesNotReachTheGateway() {
        final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        final var original = fixture.request();
        final var changed =
                new DataExportPageRequest(
                        original.template(),
                        original.businessDateWindow(),
                        original.updatedAtWindow(),
                        1,
                        100,
                        List.of("synthetic_unapproved asc"));
        assertThrows(
                ContractDriftException.class,
                () ->
                        fixture.pipeline((ordinal, record) -> record)
                                .execute(fixture.guard, changed, LIMITS, CancellationToken.none()));
        assertTrue(fixture.requestedPages.isEmpty());
        assertEquals(0, fixture.stagedRows);
    }

    @Test
    void exactBatchBoundariesNeverSendAnEmptyBatch() {
        for (final int physicalRows : List.of(100, 200, 101)) {
            final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, physicalRows, 0);
            fixture.runVertical(CancellationToken.none());
            assertEquals(physicalRows, fixture.stagedRows);
            assertEquals((physicalRows + 99) / 100, fixture.batchSizes.size());
            assertTrue(fixture.batchSizes.stream().allMatch(size -> size > 0 && size <= 100));
        }
    }

    @Test
    void repeatedExecutionWithTheSameGuardDoesNotFetchAgain() {
        final Fixture fixture = new Fixture(DataExportTemplate.COLETAS, 3, 0);
        fixture.runVertical(CancellationToken.none());
        assertThrows(
                ContractDriftException.class, () -> fixture.runVertical(CancellationToken.none()));
        assertEquals(List.of(1, 2), fixture.requestedPages);
        assertEquals(3, fixture.stagedRows);
    }

    @Test
    void defensiveRecordVisitorDoesNotExposeStoredNodesAndStopsOnFailure() {
        final DataExportPageResponse response = new DataExportPageResponse(rows(3));
        response.forEachRecord(record -> ((ObjectNode) record).put("id", 99));
        assertEquals(1, response.records().get(0).path("id").asInt());
        final AtomicInteger visited = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        response.forEachRecord(
                                record -> {
                                    visited.incrementAndGet();
                                    throw new IllegalStateException("synthetic-stop");
                                }));
        assertEquals(1, visited.get());
        assertThrows(NullPointerException.class, () -> response.forEachRecord(null));
    }

    private static DataExportPageRequest request(final DataExportTemplate template) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2036, 3, 19), LocalDate.of(2036, 3, 19)),
                Optional.empty(),
                1,
                100,
                template.defaultOrderBy());
    }

    /** Valores exclusivamente artificiais; campos de decisão não são contrato do fornecedor. */
    private static List<JsonNode> rows(final int count) {
        final List<JsonNode> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("id", index % 3 + 1)
                            .put("sequence_code", index % 3 + 1)
                            .put("status", "finished")
                            .put("status_updated_at", NOW.toString())
                            .put("servico_em", NOW.toString()));
        }
        return result;
    }

    private static final class Fixture {
        private final UUID executionId = UUID.randomUUID();
        private final DataExportTemplate template;
        private final ContractRunGuard guard;
        private final DataExportPageStreamer streamer;
        private final Audit audit = new Audit();
        private final List<Integer> requestedPages = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<Integer> batchNumbers = new ArrayList<>();
        private IntConsumer afterBatch = number -> {};
        private int stagedRows;
        private boolean observeTerminal = true;

        private Fixture(final DataExportTemplate template, final int... pageSizes) {
            this.template = template;
            final DataExportContractAdapter adapter =
                    DataExportContractAdapter.forSyntheticFixtures(
                            ContractObservationLimits.runtimeDefaults());
            final var populated = JsonNodeFactory.instance.arrayNode().addAll(rows(3));
            final var release =
                    SourceContractRelease.create(
                            ContractSourceKind.DATA_EXPORT,
                            DataExportContractAdapter.documentReference(template),
                            "synthetic-ingestion-v1",
                            ContractTestSupport.metadata(),
                            adapter.response(populated, DataExportResponseForm.ROOT_ARRAY, "/id"));
            final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
            final var binding =
                    ContractExecutionBinding.create(
                            executionId,
                            release,
                            policy,
                            new ImmutableFingerprint("synthetic-runtime-v1", "d".repeat(64)));
            guard =
                    new ContractRunGuard(
                            binding,
                            release,
                            policy,
                            ContractTestSupport.controlPlaneStart(binding),
                            alert -> {});
            guard.bindCompletenessStatus(template.completenessStatus());
            guard.validateMetadata(release.metadata());
            streamer =
                    new DataExportPageStreamer(
                            request -> {
                                requestedPages.add(request.page());
                                final List<JsonNode> page = rows(pageSizes[request.page() - 1]);
                                if (!page.isEmpty() || observeTerminal) {
                                    guard.observeDataExportResponse(
                                            request.page(),
                                            adapter.response(
                                                    JsonNodeFactory.instance
                                                            .arrayNode()
                                                            .addAll(page),
                                                    DataExportResponseForm.ROOT_ARRAY,
                                                    "/id"));
                                }
                                return new DataExportPageResponse(page);
                            },
                            audit,
                            CLOCK);
        }

        private DataExportPageRequest request() {
            return DataExportStagingPipelineTest.request(template);
        }

        private DataExportExtractionResult runVertical(final CancellationToken cancellation) {
            if (template == DataExportTemplate.COLETAS) {
                return new ExtrairColetasDataExport(
                                streamer,
                                new ColetaDataExportRecordMapper(),
                                batch -> {
                                    for (int index = 0; index < batch.size(); index++) {
                                        assertEquals(
                                                index + 1, batch.recordAt(index).inputOrdinal());
                                        assertEquals(
                                                "VALID",
                                                batch.recordAt(index).disposition().name());
                                    }
                                    staged(
                                            batch.executionId(),
                                            batch.batchNumber(),
                                            batch.size(),
                                            batch.observedAt());
                                })
                        .execute(guard, request(), LIMITS, cancellation);
            }
            return new ExtrairFretesDataExport(
                            streamer,
                            new FreteDataExportRecordMapper(),
                            batch -> {
                                for (int index = 0; index < batch.size(); index++) {
                                    assertEquals(index + 1, batch.recordAt(index).inputOrdinal());
                                    assertFalse(batch.recordAt(index).quarantined());
                                }
                                staged(
                                        batch.executionId(),
                                        batch.batchNumber(),
                                        batch.size(),
                                        batch.observedAt());
                            })
                    .execute(guard, request(), LIMITS, cancellation);
        }

        private DataExportStagingPipeline<JsonNode> pipeline(
                final BiFunction<Integer, JsonNode, JsonNode> mapper) {
            return new DataExportStagingPipeline<>(
                    template,
                    streamer,
                    mapper,
                    (id, number, records, observedAt, cancellation) -> {
                        int size = 0;
                        final Iterator<JsonNode> iterator = records.iterator();
                        while (iterator.hasNext()) {
                            iterator.next();
                            size++;
                        }
                        staged(id, number, size, observedAt);
                    });
        }

        private void staged(
                final UUID id, final int number, final int size, final Instant observedAt) {
            assertEquals(executionId, id);
            assertEquals(NOW, observedAt);
            batchSizes.add(size);
            batchNumbers.add(number);
            afterBatch.accept(number);
            stagedRows += size;
        }
    }

    private static final class Audit implements DataExportExtractionAudit {
        private int completed;
        private int failed;
        private boolean failCompletion;
        private Runnable afterCompletion = () -> {};

        @Override
        public void executionStarted(final ExecutionStarted event) {}

        @Override
        public void pageRead(final PageRead event) {}

        @Override
        public void executionCompleted(final DataExportExtractionResult result) {
            if (failCompletion) {
                throw new IllegalStateException("synthetic-audit-failure");
            }
            completed++;
            afterCompletion.run();
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {
            failed++;
        }
    }
}
