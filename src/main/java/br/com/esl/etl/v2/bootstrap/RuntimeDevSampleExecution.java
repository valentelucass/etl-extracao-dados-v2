package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.util.Optional;
import javax.sql.DataSource;

/** Existing real one-page workload with explicitly Bank-owned transactional staging. */
final class RuntimeDevSampleExecution {
    private RuntimeDevSampleExecution() {}

    record Result(
            RuntimeExitCategory category,
            boolean partialPageBoundaryObserved,
            Optional<RuntimeDevEngineBankBinding.SampleExpectation> expectation,
            Optional<Coletas6908SampleExtraction.StatusParity> statusParity) {
        Result(
                final RuntimeExitCategory category,
                final boolean partialPageBoundaryObserved,
                final Optional<RuntimeDevEngineBankBinding.SampleExpectation> expectation) {
            this(category, partialPageBoundaryObserved, expectation, Optional.empty());
        }

        Result {
            java.util.Objects.requireNonNull(category);
            java.util.Objects.requireNonNull(expectation);
            java.util.Objects.requireNonNull(statusParity);
            if (category == RuntimeExitCategory.SUCCESS
                    || expectation.isPresent() != partialPageBoundaryObserved) {
                throw new IllegalArgumentException("DEV_SAMPLE_RESULT_NOT_PARTIAL");
            }
            if (statusParity.isPresent()
                    && (!partialPageBoundaryObserved
                            || statusParity.orElseThrow().observedRows()
                                    != expectation.orElseThrow().physicalRows())) {
                throw new IllegalArgumentException("DEV_SAMPLE_PARITY_NOT_SAME_OBSERVATION");
            }
        }

        RuntimeDevEngineBankBinding.SampleFinish finish() {
            return new RuntimeDevEngineBankBinding.SampleFinish(
                    expectation,
                    category,
                    partialPageBoundaryObserved
                            ? RuntimeDevEngineBankBinding.SampleOutcome.PARTIAL_BOUNDARY
                            : category == RuntimeExitCategory.CANCELLED
                                    ? RuntimeDevEngineBankBinding.SampleOutcome.CANCELLED
                                    : RuntimeDevEngineBankBinding.SampleOutcome.FAILED);
        }

        @Override
        public String toString() {
            return "RuntimeDevSampleResult[redacted; category="
                    + category
                    + ", partialBoundary="
                    + partialPageBoundaryObserved
                    + "]";
        }
    }

    static void validateScope(
            final RuntimeConfiguration configuration, final RuntimeOperationalRequest request) {
        if (request.template != DataExportTemplate.COLETAS
                || request.users != null
                || request.temporal != null
                || request.dependency != null
                || request.plan.executionCount() != 1
                || request.pageSize != 1
                || request.firstPage().page() != 1
                || request.limits.maxPages() != 1
                || request.limits.maxRecords() > 1000
                || request.limits.maxPageSize() != 1
                || request.laboratory()) {
            throw new IllegalArgumentException("DEV_SAMPLE_ONE_PAGE_COLETA_REQUIRED");
        }
        final var source = configuration.dataExport().orElseThrow();
        RuntimeDevSourceProvisioning.verifyMetadata(source);
        if (source.resiliencePolicy().maxRequestsPerWorkload() != 2
                || source.resiliencePolicy().maxRequestsPerCycle() != 2
                || source.resiliencePolicy().cycleTimeout().toSeconds() > 60
                || source.settings().requestTimeout().toSeconds() > 30
                || source.settings().maxResponseBytes() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("DEV_SAMPLE_FINITE_SOURCE_LIMITS_REQUIRED");
        }
    }

    /**
     * Separate SAMPLE12 execution profile; descriptive inspection of old1000 plans is unchanged.
     */
    static void validateReadbackScope(
            final RuntimeConfiguration configuration, final RuntimeOperationalRequest request) {
        validateScope(configuration, request);
        if (request.limits.maxRecords()
                > RuntimeDevEngineBankBinding.SampleExpectation.MAXIMUM_PHYSICAL_ROWS) {
            throw new IllegalArgumentException("DEV_SAMPLE12_MAXIMUM_ROWS_REQUIRED");
        }
    }

    static Result execute(
            final boolean explicitSampleOptIn,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final DataSource bankSessionDataSource,
            final RuntimeOperationalExecution.SourceGateways source,
            final ColetaStagingGateway ownerTransactionStaging,
            final Runnable bankCheckpoint) {
        if (!explicitSampleOptIn) {
            throw new IllegalStateException("DEV_ENGINE_SAMPLE_OPT_IN_REQUIRED");
        }
        validateReadbackScope(configuration, request);
        java.util.Objects.requireNonNull(bankSessionDataSource);
        java.util.Objects.requireNonNull(ownerTransactionStaging);
        final var sourceScope = configuration.dataExport().orElseThrow();
        final var extraction =
                Coletas6908SampleExtraction.devSample(
                        request.firstPage(),
                        sourceScope.sourceInstance(),
                        sourceScope.tenantScope(),
                        ownerTransactionStaging,
                        bankCheckpoint);
        final var result =
                RuntimeOperationalExecution.executeSample(
                        configuration,
                        request,
                        bankSessionDataSource,
                        source,
                        extraction,
                        forbiddenPromotion(),
                        bankCheckpoint);
        // A component boundary is not retained capture, owner readback or a complete traversal.
        final boolean boundary = extraction.boundaryObserved();
        return new Result(
                result.combine(RuntimeExitCategory.DEGRADED),
                boundary,
                boundary ? Optional.of(extraction.sampleExpectation()) : Optional.empty(),
                boundary ? extraction.statusParity() : Optional.empty());
    }

    private static ColetaPromotionGateway forbiddenPromotion() {
        return new ColetaPromotionGateway() {
            @Override
            public void prepareCandidateSet(final ContractPromotionPermit permit) {
                throw new IllegalStateException("DEV_SAMPLE_PROMOTION_FORBIDDEN");
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit contract,
                    final DataQualityPromotionPermit quality) {
                throw new IllegalStateException("DEV_SAMPLE_PROMOTION_FORBIDDEN");
            }
        };
    }
}
