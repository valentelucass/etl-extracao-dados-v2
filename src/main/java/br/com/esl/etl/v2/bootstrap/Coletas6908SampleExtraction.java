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
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityCatalog;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** One populated page, explicitly partial. It cannot return a traversal completion or permit. */
final class Coletas6908SampleExtraction implements DataExportRuntimeWorkload.Extraction {
    private final Coletas6908PilotPlan plan;
    private final ColetaStagingGateway staging;
    private final Runnable checkpoint;
    private final Map<ScopedSourceIdentity, Integer> roots = new LinkedHashMap<>();
    private final Map<Integer, Integer> batchPages = new LinkedHashMap<>();
    private final Boundary boundary = new Boundary();
    private UUID executionId;
    private int physicalRows;
    private boolean observedBoundary;

    Coletas6908SampleExtraction(
            final Coletas6908PilotPlan plan,
            final ColetaStagingGateway staging,
            final Runnable checkpoint) {
        this.plan = java.util.Objects.requireNonNull(plan);
        this.staging = java.util.Objects.requireNonNull(staging);
        this.checkpoint = java.util.Objects.requireNonNull(checkpoint);
    }

    @Override
    public DataExportExtractionResult execute(
            final DataExportPageStreamer streamer,
            final ContractRunGuard guard,
            final DataExportPageRequest request,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellation) {
        if (executionId != null || !request.equals(plan.pageRequest())) {
            throw new IllegalStateException("COL_SAMPLE_EXTRACTION_SCOPE");
        }
        // The real streamer audits the populated page. Our boundary invalidates completion;
        // no empty page is fabricated and no second data request is issued.
        streamer.stream(
                guard.executionContext(),
                request,
                limits,
                cancellation,
                page -> capture(page, cancellation));
        guard.invalidateEvidence();
        throw new IllegalStateException("COL_SAMPLE_BOUNDARY_MISSING");
    }

    private void capture(final DataExportReadPage page, final CancellationToken cancellation) {
        checkpoint.run();
        if (page.page() != 1 || page.executionId() == null || page.response().recordCount() < 1) {
            throw new IllegalStateException("COL_SAMPLE_PAGE_SCOPE");
        }
        executionId = page.executionId();
        // Validate all identities before sending any mapped batch to JDBC.
        page.response()
                .forEachRecord(
                        row -> {
                            cancellation.throwIfCancellationRequested();
                            final var identity = identity(row);
                            roots.merge(identity, 1, Math::addExact);
                            if (roots.size() > plan.per() || roots.size() > 5) {
                                throw new IllegalStateException("COL_SAMPLE_ENTITY_LIMIT");
                            }
                        });
        final var mapper = new ColetaDataExportRecordMapper();
        final List<ColetaStageRecord> batch = new ArrayList<>();
        page.response()
                .forEachRecord(
                        row -> {
                            cancellation.throwIfCancellationRequested();
                            checkpoint.run();
                            final var mapped = mapper.map(batch.size() + 1, row);
                            if (mapped.quarantineReasonCode() != null
                                    || !identity(row).sourceKey().equals(mapped.sourceKey())) {
                                throw new IllegalStateException("COL_SAMPLE_MAPPER_QUARANTINE");
                            }
                            batch.add(mapped);
                            if (batch.size() == ColetaStageRecord.MAXIMUM_PAGE_SIZE) {
                                stage(page, batch, cancellation);
                            }
                        });
        if (!batch.isEmpty()) {
            stage(page, batch, cancellation);
        }
        physicalRows = page.response().recordCount();
        cancellation.throwIfCancellationRequested();
        checkpoint.run();
        observedBoundary = true;
        throw boundary;
    }

    private void stage(
            final DataExportReadPage page,
            final List<ColetaStageRecord> batch,
            final CancellationToken cancellation) {
        checkpoint.run();
        final int number = batchPages.size() + 1;
        staging.stage(
                new ColetaStageBatch(executionId, number, List.copyOf(batch), page.readAt()),
                cancellation);
        checkpoint.run();
        batchPages.put(number, 1);
        batch.clear();
    }

    private ScopedSourceIdentity identity(final com.fasterxml.jackson.databind.JsonNode row) {
        return ScopedSourceIdentity.fromJson(
                plan.sourceInstance(),
                plan.tenantScope(),
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS),
                row.get("id"));
    }

    boolean boundaryObserved() {
        return observedBoundary && boundary.getSuppressed().length == 0;
    }

    List<ExpectedRoot> expectedRoots() {
        requireBoundary();
        return roots.entrySet().stream()
                .map(
                        entry ->
                                new ExpectedRoot(
                                        entry.getKey(),
                                        OptionalInt.of(entry.getValue()),
                                        Map.of(),
                                        Set.of(1)))
                .toList();
    }

    UUID executionId() {
        requireBoundary();
        return executionId;
    }

    int physicalRows() {
        requireBoundary();
        return physicalRows;
    }

    Map<Integer, Integer> batchPages() {
        requireBoundary();
        return Map.copyOf(batchPages);
    }

    private void requireBoundary() {
        if (!boundaryObserved()) {
            throw new IllegalStateException("COL_SAMPLE_UNVERIFIED_BOUNDARY");
        }
    }

    private static final class Boundary extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private Boundary() {
            super("COL_SAMPLE_PARTIAL_PAGE_BOUNDARY");
        }
    }
}
