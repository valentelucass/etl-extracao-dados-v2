package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaShadowRollbackTrial;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * SAMPLE is staging-only. The production Main and the completed-traversal runner do not call it.
 */
final class Coletas6908SampleRunner {
    private Coletas6908SampleRunner() {}

    static Receipt runInLocalShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment)
            throws Exception {
        preflight(plan, configuration, request);
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")
                || !Boolean.getBoolean("coletas6908.sample.enabled")
                || !"1".equals(System.getProperty("jdk.httpclient.redirects.retrylimit"))
                || !"true".equals(System.getProperty("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry"))) {
            throw new IllegalStateException("COL_SAMPLE_OPT_IN_REQUIRED");
        }
        if (!configuration
                .shadowStorage()
                .jdbcUrl()
                .equals(environment.get("V2_SHADOW_JDBC_URL"))) {
            throw new IllegalArgumentException("COL_SAMPLE_TARGET_BINDING_REQUIRED");
        }
        // Fail on a missing credential before opening the local SQL transaction. Never log it.
        new EnvironmentSecretProvider(environment).require(SecretKey.DATA_EXPORT_TOKEN);
        final var attempts = new RuntimeHttpAttempts(2);
        return ColetaShadowRollbackTrial.executeFromEnvironment(
                bank ->
                        run(
                                plan,
                                configuration,
                                request,
                                RuntimeOperationalExecution.observedSourceGateways(attempts),
                                new BankTrial(bank),
                                attempts));
    }

    static Receipt run(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeOperationalExecution.SourceGateways source,
            final Trial trial,
            final RuntimeHttpAttempts attempts)
            throws Exception {
        preflight(plan, configuration, request);
        final var deadline = new Coletas6908PilotDeadline(plan.deadline());
        final int observedRows;
        final int expectedRoots;
        try (trial) {
            final Runnable checkpoint =
                    () -> {
                        deadline.check();
                        trial.checkpoint();
                        deadline.check();
                    };
            checkpoint.run();
            final var extraction =
                    new Coletas6908SampleExtraction(plan, trial.staging(), checkpoint);
            final var status =
                    RuntimeOperationalExecution.executeSample(
                            configuration,
                            request,
                            trial.dataSource(),
                            source,
                            extraction,
                            forbiddenPromotion(),
                            checkpoint);
            checkpoint.run();
            // FAILED is the honest traversal result. A failed SQL transition suppresses the
            // boundary exception, invalidating it; that cannot become a sample receipt.
            if (status != RuntimeExitCategory.SOURCE_DQ || !extraction.boundaryObserved()) {
                throw new IllegalStateException("COL_SAMPLE_TRAVERSAL_STOP");
            }
            final var expected = extraction.expectedRoots();
            final var binding =
                    new Binding(
                            plan.sourceInstance(),
                            plan.tenantScope(),
                            plan.businessDate(),
                            request.binding.contractFingerprint().sha256(),
                            extraction.executionId(),
                            1);
            final var compared = trial.verifyStaging(binding, expected, extraction.batchPages());
            checkpoint.run();
            if (!compared.matches()
                    || compared.presenceComparedCells() != 0
                    || compared.observedPhysicalRows() != extraction.physicalRows()) {
                throw new IllegalStateException("COL_SAMPLE_STAGING_READBACK_STOP");
            }
            observedRows = compared.observedPhysicalRows();
            expectedRoots = expected.size();
        }
        return new Receipt(
                "SAMPLE_STAGED_READBACK_ROLLED_BACK",
                "PARTIAL_SINGLE_PAGE",
                1,
                observedRows,
                expectedRoots,
                0,
                false,
                false,
                false,
                attempts.summary());
    }

    static void preflight(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request) {
        Coletas6908PilotRunner.preflight(plan, configuration, request);
        if (plan.per() > 5
                || !configuration.environment().name().equals("LOCAL_SHADOW")
                || !configuration.shadowStorage().usesIntegratedSecurity()
                || configuration.dataExport().orElseThrow().resiliencePolicy().maxInFlight() != 1) {
            throw new IllegalArgumentException("COL_SAMPLE_SCOPE_REQUIRED");
        }
    }

    private static ColetaPromotionGateway forbiddenPromotion() {
        return new ColetaPromotionGateway() {
            @Override
            public void prepareCandidateSet(final ContractPromotionPermit permit) {
                throw new IllegalStateException("COL_SAMPLE_PROMOTION_FORBIDDEN");
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit permit,
                    final DataQualityPromotionPermit quality) {
                throw new IllegalStateException("COL_SAMPLE_PROMOTION_FORBIDDEN");
            }
        };
    }

    interface Trial extends AutoCloseable {
        DataSource dataSource();

        ColetaStagingGateway staging();

        void checkpoint();

        Comparison verifyStaging(
                Binding binding, List<ExpectedRoot> expected, Map<Integer, Integer> batchPages);

        @Override
        void close() throws SQLException;
    }

    record Comparison(boolean matches, int observedPhysicalRows, int presenceComparedCells) {}

    record Receipt(
            String status,
            String scope,
            int pagesFetched,
            int physicalRows,
            int distinctRoots,
            int presenceComparedCells,
            boolean promoted,
            boolean windowCompleteness,
            boolean childCompleteness,
            String httpAttempts) {}

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
        public void checkpoint() {
            sql(
                    () -> {
                        delegate.checkpoint();
                        return null;
                    });
        }

        @Override
        public Comparison verifyStaging(
                final Binding binding,
                final List<ExpectedRoot> expected,
                final Map<Integer, Integer> batchPages) {
            final var result =
                    sql(
                            () ->
                                    delegate.verifyObservedSample(
                                            binding,
                                            expected,
                                            batchPages,
                                            CancellationToken.none()));
            return new Comparison(
                    result.matches(),
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
            throw new IllegalStateException("COL_SAMPLE_SQL_STOP", failure);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T get() throws SQLException;
    }
}
