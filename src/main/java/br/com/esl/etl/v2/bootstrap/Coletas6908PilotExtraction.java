package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportReadPage;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityCatalog;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** Captura raizes antes do mapper e usa o staging injetado na travessia contratual completa. */
final class Coletas6908PilotExtraction implements DataExportRuntimeWorkload.Extraction {
    private final Coletas6908PilotPlan plan;
    private final Coletas6908PilotDeadline deadline;
    private final ColetaStagingGateway staging;
    private final ColetaDataExportRecordMapper mapper = new ColetaDataExportRecordMapper();
    private final Map<ScopedSourceIdentity, RootCount> expected = new LinkedHashMap<>();
    private final Map<Integer, Integer> batchToPage = new LinkedHashMap<>();
    private int batchNumber;
    private DataExportExtractionResult completed;

    Coletas6908PilotExtraction(
            final Coletas6908PilotPlan plan,
            final ColetaStagingGateway staging,
            final Coletas6908PilotDeadline deadline) {
        this.plan = Objects.requireNonNull(plan);
        this.staging = Objects.requireNonNull(staging);
        this.deadline = Objects.requireNonNull(deadline);
    }

    @Override
    public DataExportExtractionResult execute(
            final DataExportPageStreamer streamer,
            final ContractRunGuard guard,
            final DataExportPageRequest request,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellation) {
        Objects.requireNonNull(streamer);
        Objects.requireNonNull(guard);
        Objects.requireNonNull(cancellation);
        if (completed != null
                || request.template() != DataExportTemplate.COLETAS
                || !request.equals(plan.pageRequest())
                || limits.maxPages() != plan.maxPages()
                || limits.maxRecords() > plan.maxPhysicalRows()
                || limits.maxPageSize() != plan.per()) {
            throw new IllegalArgumentException("COL_PILOT_EXTRACTION_SCOPE");
        }
        final CancellationToken bounded =
                () -> cancellation.isCancellationRequested() || deadline.expired();
        try {
            final DataExportExtractionResult result =
                    streamer.stream(
                            guard.executionContext(),
                            request,
                            limits,
                            bounded,
                            page -> consume(page, bounded));
            if (result.pagesFetched() < 2
                    || result.pagesFetched() > plan.maxPages()
                    || result.recordsDelivered() < 1
                    || result.recordsDelivered() > plan.maxPhysicalRows()
                    || result.terminalPage() != result.pagesFetched()
                    || guard.responsePages() != result.terminalPage()) {
                throw new IllegalStateException("COL_PILOT_TERMINAL_OR_CAP_REQUIRED");
            }
            bounded.throwIfCancellationRequested();
            completed = result;
            return result;
        } catch (final RuntimeException failure) {
            guard.invalidateEvidence();
            throw failure;
        }
    }

    private void consume(final DataExportReadPage page, final CancellationToken cancellation) {
        if (page.executionId() == null || guardPageMismatch(page)) {
            throw new IllegalStateException("COL_PILOT_PAGE_DRIFT");
        }
        final Set<ScopedSourceIdentity> pageRoots = new HashSet<>();
        final List<ColetaStageRecord> batch = new ArrayList<>(ColetaStageRecord.MAXIMUM_PAGE_SIZE);
        page.response()
                .forEachRecord(
                        row -> {
                            cancellation.throwIfCancellationRequested();
                            final ScopedSourceIdentity identity = identity(row);
                            pageRoots.add(identity);
                            if (pageRoots.size() > plan.per()) {
                                throw new IllegalStateException("COL_PILOT_CONTRACT_ENTITY_LIMIT");
                            }
                            final RootCount count = expected.get(identity);
                            if (count == null) {
                                expected.put(identity, new RootCount(page.page()));
                            } else {
                                count.increment(page.page());
                            }
                            final ColetaStageRecord mapped = mapper.map(batch.size() + 1, row);
                            if (mapped.quarantineReasonCode() != null
                                    || !identity.sourceKey().equals(mapped.sourceKey())) {
                                throw new IllegalStateException("COL_PILOT_MAPPER_QUARANTINE");
                            }
                            batch.add(mapped);
                            if (batch.size() == ColetaStageRecord.MAXIMUM_PAGE_SIZE) {
                                stage(page, batch, cancellation);
                            }
                        });
        if (!batch.isEmpty()) {
            stage(page, batch, cancellation);
        }
    }

    private boolean guardPageMismatch(final DataExportReadPage page) {
        return !page.request().equals(plan.pageRequest().withPage(page.page()))
                || page.page() < 1
                || page.page() >= plan.maxPages();
    }

    private ScopedSourceIdentity identity(final JsonNode row) {
        if (row == null || !row.isObject()) {
            throw new IllegalStateException("COL_PILOT_INVALID_ROOT");
        }
        try {
            return ScopedSourceIdentity.fromJson(
                    plan.sourceInstance(),
                    plan.tenantScope(),
                    FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS),
                    row.get("id"));
        } catch (final IdentityQuarantineException invalidId) {
            throw new IllegalStateException("COL_PILOT_INVALID_ID", invalidId);
        }
    }

    private void stage(
            final DataExportReadPage page,
            final List<ColetaStageRecord> batch,
            final CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        batchNumber = Math.incrementExact(batchNumber);
        staging.stage(
                new ColetaStageBatch(
                        page.executionId(), batchNumber, List.copyOf(batch), page.readAt()),
                cancellation);
        batchToPage.put(batchNumber, page.page());
        batch.clear();
        cancellation.throwIfCancellationRequested();
    }

    List<ExpectedRoot> expectedRoots() {
        requireCompleted();
        return expected.entrySet().stream()
                .map(
                        entry ->
                                new ExpectedRoot(
                                        entry.getKey(),
                                        OptionalInt.of(entry.getValue().rows),
                                        Map.of(),
                                        entry.getValue().pages))
                .toList();
    }

    Map<Integer, Integer> batchToPage() {
        requireCompleted();
        return Map.copyOf(batchToPage);
    }

    int overlapRoots() {
        requireCompleted();
        return (int) expected.values().stream().filter(root -> root.pages.size() > 1).count();
    }

    DataExportExtractionResult result() {
        requireCompleted();
        return completed;
    }

    private void requireCompleted() {
        if (completed == null) {
            throw new IllegalStateException("COL_PILOT_TRAVERSAL_INCOMPLETE");
        }
    }

    private static final class RootCount {
        private int rows = 1;
        private final Set<Integer> pages = new HashSet<>();

        private RootCount(final int firstPage) {
            pages.add(firstPage);
        }

        private void increment(final int page) {
            rows = Math.incrementExact(rows);
            pages.add(page);
        }
    }
}
