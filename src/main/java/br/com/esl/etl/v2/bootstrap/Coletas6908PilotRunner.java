package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.Coletas6908PilotSourceGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaShadowRollbackTrial;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/** Composicao opt-in; o Main operacional e a CLI do plano nao a chamam. */
final class Coletas6908PilotRunner {
    private Coletas6908PilotRunner() {}

    static Receipt runInLocalShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeOperationalExecution.SourceGateways source)
            throws Exception {
        preflight(plan, configuration, request);
        return ColetaShadowRollbackTrial.executeFromEnvironment(
                trial -> run(plan, configuration, request, source, new BankTrial(trial)));
    }

    static Receipt run(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeOperationalExecution.SourceGateways source,
            final Trial trial)
            throws Exception {
        preflight(plan, configuration, request);
        Objects.requireNonNull(source);
        final Coletas6908PilotDeadline deadline = new Coletas6908PilotDeadline(plan.deadline());
        final Observation observation;
        try (trial) {
            final Runnable checkpoint =
                    () -> {
                        deadline.check();
                        trial.checkpoint();
                        deadline.check();
                    };
            checkpoint.run();
            final Coletas6908PilotExtraction extraction =
                    new Coletas6908PilotExtraction(plan, trial.staging(), deadline);
            final ColetaPromotionGateway promotion =
                    boundedPromotion(trial.promotion(), checkpoint);
            final RuntimeExitCategory status =
                    RuntimeOperationalExecution.executePilot(
                            configuration,
                            request,
                            trial.dataSource(),
                            source,
                            extraction,
                            promotion,
                            checkpoint);
            checkpoint.run();
            if (status != RuntimeExitCategory.SUCCESS) {
                throw new IllegalStateException("COL_PILOT_RUNTIME_STOP_" + status);
            }
            final var result = extraction.result();
            final var expected = extraction.expectedRoots();
            final var binding =
                    new Binding(
                            plan.sourceInstance(),
                            plan.tenantScope(),
                            plan.businessDate(),
                            request.binding.contractFingerprint().sha256(),
                            result.executionId(),
                            0);
            final Comparison compared = trial.verify(binding, expected, extraction.batchToPage());
            checkpoint.run();
            if (!compared.matches() || compared.presenceComparedCells() != 0) {
                throw new IllegalStateException("COL_PILOT_COMPARISON_STOP");
            }
            observation =
                    new Observation(
                            result.pagesFetched(),
                            result.recordsDelivered(),
                            expected.size(),
                            extraction.overlapRoots(),
                            compared.observedPhysicalRows());
        }
        return new Receipt(
                "SIMULATED_PROMOTION_ROLLED_BACK",
                "PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA",
                observation.pagesFetched(),
                observation.physicalRows(),
                observation.expectedRoots(),
                observation.overlapRoots(),
                observation.observedPhysicalRows(),
                0,
                false,
                false);
    }

    static void preflight(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(request);
        BoundedRuntimeDataSource.validateTarget(configuration.shadowStorage());
        final var source = configuration.dataExport().orElseThrow();
        if (request.template != DataExportTemplate.COLETAS
                || request.temporal != null
                || request.dependency != null
                || request.users != null
                || request.plan.executionCount() != 1
                || !source.sourceInstance().equals(plan.sourceInstance())
                || !source.tenantScope().equals(plan.tenantScope())
                || !request.firstPage().equals(plan.pageRequest())
                || request.limits.maxPages() != plan.maxPages()
                || request.limits.maxRecords() != plan.maxPhysicalRows()
                || request.limits.maxPageSize() != plan.per()
                || source.settings().maxResponseBytes() > plan.maxResponseBytes()) {
            throw new IllegalArgumentException("COL_PILOT_SCOPE_REQUIRED");
        }
        final var selected =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem[1];
        request.plan.forEach(item -> selected[0] = item);
        final var partition = selected[0].partition();
        final var first =
                plan.businessDate().atStartOfDay(source.settings().sourceZone()).toInstant();
        final var end =
                plan.businessDate()
                        .plusDays(1)
                        .atStartOfDay(source.settings().sourceZone())
                        .toInstant();
        if (partition.mode() != ExecutionMode.BACKFILL
                || !partition.partitionStart().equals(first)
                || !partition.partitionEndExclusive().equals(end)
                || !partition.sourceInstance().equals(plan.sourceInstance())
                || !partition.tenantScope().equals(plan.tenantScope())) {
            throw new IllegalArgumentException("COL_PILOT_PARTITION_REQUIRED");
        }
        Coletas6908PilotSourceGuard.validateTraversal(
                source.settings(), request.firstPage(), request.limits, configuration.clock());
    }

    private static ColetaPromotionGateway boundedPromotion(
            final ColetaPromotionGateway delegate, final Runnable checkpoint) {
        return new ColetaPromotionGateway() {
            @Override
            public void prepareCandidateSet(final ContractPromotionPermit permit) {
                prepareCandidateSet(permit, CancellationToken.none());
            }

            @Override
            public void prepareCandidateSet(
                    final ContractPromotionPermit permit, final CancellationToken cancellation) {
                checkpoint.run();
                delegate.prepareCandidateSet(permit, cancellation);
                checkpoint.run();
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit permit,
                    final DataQualityPromotionPermit quality) {
                return applyReconcileAndPublish(permit, quality, CancellationToken.none());
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit permit,
                    final DataQualityPromotionPermit quality,
                    final CancellationToken cancellation) {
                checkpoint.run();
                final var result = delegate.applyReconcileAndPublish(permit, quality, cancellation);
                checkpoint.run();
                return result;
            }
        };
    }

    interface Trial extends AutoCloseable {
        DataSource dataSource();

        ColetaStagingGateway staging();

        ColetaPromotionGateway promotion();

        void checkpoint();

        Comparison verify(
                Binding binding, List<ExpectedRoot> expected, Map<Integer, Integer> batchPages);

        @Override
        void close() throws SQLException;
    }

    record Comparison(boolean matches, int observedPhysicalRows, int presenceComparedCells) {}

    record Receipt(
            String status,
            String parity,
            int pagesFetched,
            long physicalRows,
            int expectedRoots,
            int overlapRoots,
            int observedPhysicalRows,
            int presenceComparedCells,
            boolean windowCompleteness,
            boolean childCompleteness) {}

    private record Observation(
            int pagesFetched,
            long physicalRows,
            int expectedRoots,
            int overlapRoots,
            int observedPhysicalRows) {}

    private static final class BankTrial implements Trial {
        private final ColetaShadowRollbackTrial delegate;

        private BankTrial(final ColetaShadowRollbackTrial delegate) {
            this.delegate = delegate;
        }

        @Override
        public DataSource dataSource() {
            return sql(delegate::controlPlaneDataSource);
        }

        @Override
        public ColetaStagingGateway staging() {
            return sql(delegate::stagingGateway);
        }

        @Override
        public ColetaPromotionGateway promotion() {
            return sql(delegate::promotionGateway);
        }

        @Override
        public void checkpoint() {
            sql(
                    () -> {
                        delegate.checkpoint();
                        return null;
                    });
        }

        @Override
        public Comparison verify(
                final Binding binding,
                final List<ExpectedRoot> expected,
                final Map<Integer, Integer> batchPages) {
            final var result =
                    sql(
                            () ->
                                    delegate.verifyObservedTraversal(
                                            ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL,
                                            binding,
                                            expected,
                                            batchPages,
                                            CancellationToken.none()));
            return new Comparison(
                    result.sql().matches(),
                    result.observedPhysicalRows(),
                    result.presenceComparedCells());
        }

        @Override
        public void close() throws SQLException {
            delegate.close();
        }
    }

    private static <T> T sql(final SqlWork<T> work) {
        try {
            return work.get();
        } catch (final SQLException failure) {
            throw new IllegalStateException("COL_PILOT_SQL_STOP", failure);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T get() throws SQLException;
    }
}
