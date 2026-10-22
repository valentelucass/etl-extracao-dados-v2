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
    private final DataExportPageRequest firstPage;
    private final String sourceInstance;
    private final String tenantScope;
    private final ColetaStagingGateway staging;
    private final Runnable checkpoint;
    private final Map<ScopedSourceIdentity, Integer> roots = new LinkedHashMap<>();
    private final Map<Integer, Integer> batchPages = new LinkedHashMap<>();
    private final Boundary boundary = new Boundary();
    private UUID executionId;
    private int physicalRows;
    private boolean observedBoundary;
    private StatusParityCounter statusParity;

    Coletas6908SampleExtraction(
            final Coletas6908PilotPlan plan,
            final ColetaStagingGateway staging,
            final Runnable checkpoint) {
        this(
                java.util.Objects.requireNonNull(plan).pageRequest(),
                plan.sourceInstance(),
                plan.tenantScope(),
                staging,
                checkpoint);
    }

    private Coletas6908SampleExtraction(
            final DataExportPageRequest firstPage,
            final String sourceInstance,
            final String tenantScope,
            final ColetaStagingGateway staging,
            final Runnable checkpoint) {
        this.firstPage = java.util.Objects.requireNonNull(firstPage);
        this.sourceInstance = java.util.Objects.requireNonNull(sourceInstance);
        this.tenantScope = java.util.Objects.requireNonNull(tenantScope);
        this.staging = java.util.Objects.requireNonNull(staging);
        this.checkpoint = java.util.Objects.requireNonNull(checkpoint);
    }

    static Coletas6908SampleExtraction devSample(
            final DataExportPageRequest firstPage,
            final String sourceInstance,
            final String tenantScope,
            final ColetaStagingGateway ownerTransactionStaging,
            final Runnable checkpoint) {
        if (firstPage.template()
                        != br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate.COLETAS
                || firstPage.page() != 1
                || firstPage.pageSize() != 1) {
            throw new IllegalArgumentException("DEV_SAMPLE_SINGLE_COLETA_SCOPE_REQUIRED");
        }
        final var extraction =
                new Coletas6908SampleExtraction(
                        firstPage,
                        sourceInstance,
                        tenantScope,
                        ownerTransactionStaging,
                        checkpoint);
        extraction.statusParity = new StatusParityCounter();
        return extraction;
    }

    @Override
    public DataExportExtractionResult execute(
            final DataExportPageStreamer streamer,
            final ContractRunGuard guard,
            final DataExportPageRequest request,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellation) {
        if (executionId != null || !request.equals(firstPage)) {
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
                            if (roots.size() > firstPage.pageSize() || roots.size() > 5) {
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
                            if (statusParity != null) {
                                final var status = row.get("status");
                                statusParity.observe(
                                        status != null && status.isTextual()
                                                ? status.textValue()
                                                : null,
                                        mapped.status());
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
                sourceInstance,
                tenantScope,
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

    RuntimeDevEngineBankBinding.SampleExpectation sampleExpectation() {
        requireBoundary();
        return new RuntimeDevEngineBankBinding.SampleExpectation(
                executionId(), physicalRows(), expectedRoots(), batchPages());
    }

    java.util.Optional<StatusParity> statusParity() {
        requireBoundary();
        return statusParity == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(statusParity.snapshot());
    }

    /** C54-PARITY-STATUS-01: counters from the same mapped RAM rows, no input values retained. */
    record StatusParity(int observedRows, int comparedRows, int divergentRows, int unknownRows) {
        StatusParity {
            if (observedRows < 0
                    || observedRows > 12
                    || comparedRows < 0
                    || unknownRows < 0
                    || divergentRows < 0
                    || divergentRows > comparedRows
                    || comparedRows + unknownRows != observedRows) {
                throw new IllegalArgumentException("COL_SAMPLE_STATUS_PARITY_COUNTS");
            }
        }

        String outcome() {
            if (observedRows == 0 || unknownRows != 0) {
                return "NOT_VERIFIED_INSUFFICIENT_STATUS";
            }
            return divergentRows == 0 ? "PARTIAL_STATUS_CONFORMANT" : "PARTIAL_STATUS_DIVERGENCE";
        }
    }

    static final class StatusParityCounter {
        private int observed;
        private int compared;
        private int divergent;
        private int unknown;

        void observe(
                final String raw,
                final br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus.Resolved mapped) {
            if (observed == 12) {
                throw new IllegalStateException("COL_SAMPLE_STATUS_PARITY_BOUND");
            }
            java.util.Objects.requireNonNull(mapped);
            final var reference = v1StatusProjection(raw);
            observed++;
            if (reference == null) {
                unknown++;
                return;
            }
            compared++;
            if (!reference.code().equals(mapped.code())
                    || !reference.label().equals(mapped.label())
                    || reference.terminal() != mapped.terminal()) {
                divergent++;
            }
        }

        StatusParity snapshot() {
            return new StatusParity(observed, compared, divergent, unknown);
        }
    }

    // Independent current V1 policy snapshot, qualified against the actual pure V1 source.
    // This code hash identifies the rule reference, never a source caller or admission.
    static final String V1_STATUS_REFERENCE_SHA256 =
            "e32fd5bf48227fcfac1c108eaadee4f801d5264b4b7091b913dc38d16275a5a4";

    record V1StatusProjection(String code, String label, boolean terminal) {}

    static V1StatusProjection v1StatusProjection(final String raw) {
        if (raw == null) {
            return null;
        }
        final String code =
                raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (code) {
            case "pending" -> new V1StatusProjection(code, "Pendente", false);
            case "treatment" -> new V1StatusProjection(code, "Em tratativa", false);
            case "manifested" -> new V1StatusProjection(code, "Manifestada", false);
            case "in_transit" -> new V1StatusProjection(code, "Em trânsito", false);
            case "draft" -> new V1StatusProjection(code, "Rascunho", false);
            case "finished" -> new V1StatusProjection(code, "Finalizada", true);
            case "done" -> new V1StatusProjection(code, "Coletada", true);
            case "canceled", "cancelled" -> new V1StatusProjection(code, "Cancelada", true);
            default -> null;
        };
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
