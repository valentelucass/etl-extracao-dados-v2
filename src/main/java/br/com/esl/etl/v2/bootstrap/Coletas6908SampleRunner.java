package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaShadowRollbackTrial;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * SAMPLE is staging-only. The production Main and the completed-traversal runner do not call it.
 */
final class Coletas6908SampleRunner {
    private Coletas6908SampleRunner() {}

    static RuntimeConfiguration pinnedConfiguration(final RuntimeConfiguration configuration) {
        if (!configuration.environment().name().equals("LOCAL_SHADOW")
                || !configuration.shadowStorage().usesSampleSaAuthentication()) {
            throw new IllegalArgumentException("COL_SAMPLE_PIN_PROFILE_REQUIRED");
        }
        return new RuntimeConfiguration(
                configuration.environment(),
                configuration.businessZone(),
                configuration.clock(),
                configuration.dataExport(),
                configuration.graphQl(),
                ShadowStorageProperties.enabledSampleSqlPin(
                        configuration.shadowStorage().jdbcUrl(),
                        Coletas6908SamplePreflight.PIN_APPROVAL));
    }

    static Receipt runInPinnedSharedShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Coletas6908SamplePreflight.SharedAdmission admission)
            throws Exception {
        preflightPinned(plan, configuration, request, System.getProperties(), admission);
        admission.check(configuration.clock());
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                System.getProperties(),
                () -> new EnvironmentSecretProvider(environment),
                null,
                (target, provider, work) ->
                        ColetaShadowRollbackTrial.executePinnedSharedSampleSa(
                                target, provider, bank -> work.execute(new BankTrial(bank))),
                () -> admission.check(configuration.clock()));
    }

    static Receipt runInPinnedSharedShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Properties properties,
            final SecretProvider provider,
            final RuntimeOperationalExecution.SourceGateways source,
            final TrialExecution trials,
            final Coletas6908SamplePreflight.SharedAdmission admission)
            throws Exception {
        preflightPinned(plan, configuration, request, properties, admission);
        admission.check(configuration.clock());
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                properties,
                () -> provider,
                source,
                trials,
                () -> admission.check(configuration.clock()));
    }

    static Receipt runInSharedShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Coletas6908SamplePreflight.SharedAdmission admission)
            throws Exception {
        requireStrictSqlMode(configuration, System.getProperties());
        preflightShared(plan, configuration, request, System.getProperties());
        admission.check(configuration.clock());
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                System.getProperties(),
                () -> new EnvironmentSecretProvider(environment),
                null,
                (target, provider, work) ->
                        ColetaShadowRollbackTrial.executeSharedSampleSa(
                                target, provider, bank -> work.execute(new BankTrial(bank))),
                () -> admission.check(configuration.clock()));
    }

    static Receipt runInSharedShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Properties properties,
            final SecretProvider provider,
            final RuntimeOperationalExecution.SourceGateways source,
            final TrialExecution trials,
            final Coletas6908SamplePreflight.SharedAdmission admission)
            throws Exception {
        requireStrictSqlMode(configuration, properties);
        preflightShared(plan, configuration, request, properties);
        admission.check(configuration.clock());
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                properties,
                () -> provider,
                source,
                trials,
                () -> admission.check(configuration.clock()));
    }

    static Receipt runInLocalShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment)
            throws Exception {
        requireLegacyMode(System.getProperties());
        requireStrictSqlMode(configuration, System.getProperties());
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                System.getProperties(),
                () -> new EnvironmentSecretProvider(environment),
                null,
                Coletas6908SampleRunner::executeTrial,
                () -> {});
    }

    static Receipt runInLocalShadow(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Properties properties,
            final SecretProvider secrets,
            final RuntimeOperationalExecution.SourceGateways source,
            final TrialExecution trials)
            throws Exception {
        requireLegacyMode(properties);
        requireStrictSqlMode(configuration, properties);
        return runWithSecretProvider(
                plan,
                configuration,
                request,
                environment,
                properties,
                () -> secrets,
                source,
                trials,
                () -> {});
    }

    private static Receipt runWithSecretProvider(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Map<String, String> environment,
            final Properties properties,
            final Supplier<SecretProvider> secretProvider,
            final RuntimeOperationalExecution.SourceGateways source,
            final TrialExecution trials,
            final Runnable admissionCheckpoint)
            throws Exception {
        preflight(plan, configuration, request);
        if (!"true".equals(properties.getProperty("shadow.local.integration.enabled"))
                || !"true".equals(properties.getProperty("shadow.local.integration.profile.active"))
                || !"true".equals(properties.getProperty("coletas6908.sample.enabled"))
                || !"1".equals(properties.getProperty("jdk.httpclient.redirects.retrylimit"))
                || !"true".equals(properties.getProperty("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(properties.getProperty("jdk.httpclient.enableAllMethodRetry"))) {
            throw new IllegalStateException("COL_SAMPLE_OPT_IN_REQUIRED");
        }
        admissionCheckpoint.run();
        validateAuthentication(configuration, properties);
        if (!configuration
                .shadowStorage()
                .jdbcUrl()
                .equals(environment.get("V2_SHADOW_JDBC_URL"))) {
            throw new IllegalArgumentException("COL_SAMPLE_TARGET_BINDING_REQUIRED");
        }
        // Fail on a missing credential before opening the local SQL transaction. Never log it.
        final var provider = secretProvider.get();
        provider.require(SecretKey.DATA_EXPORT_TOKEN);
        admissionCheckpoint.run();
        final var attempts = new RuntimeHttpAttempts(2);
        final var receipt =
                trials.execute(
                        configuration.shadowStorage(),
                        provider,
                        trial ->
                                run(
                                        plan,
                                        configuration,
                                        request,
                                        source == null
                                                ? RuntimeOperationalExecution
                                                        .observedSourceGateways(attempts)
                                                : source,
                                        trial,
                                        attempts,
                                        admissionCheckpoint));
        admissionCheckpoint.run();
        return receipt;
    }

    static void validateAuthentication(
            final RuntimeConfiguration configuration, final Properties properties) {
        final String optIn = properties.getProperty("coletas6908.sample.sa.enabled");
        final boolean sa = configuration.shadowStorage().usesSampleSaAuthentication();
        final String pin = properties.getProperty("coletas6908.sample.sql-pin.enabled");
        final boolean pinned = configuration.shadowStorage().usesSampleSqlPinAuthentication();
        if ((sa && !"true".equals(optIn)) || (!sa && optIn != null && !"false".equals(optIn))) {
            throw new IllegalArgumentException("COL_SAMPLE_AUTHENTICATION_BINDING_REQUIRED");
        }
        if ((pinned && !"true".equals(pin)) || (!pinned && pin != null && !"false".equals(pin))) {
            throw new IllegalArgumentException("COL_SAMPLE_PIN_AUTHENTICATION_BINDING_REQUIRED");
        }
    }

    private static void requireStrictSqlMode(
            final RuntimeConfiguration configuration, final Properties properties) {
        if (configuration.shadowStorage().usesSampleSqlPinAuthentication()
                || "true".equals(properties.getProperty("coletas6908.sample.sql-pin.enabled"))) {
            throw new IllegalArgumentException("COL_SAMPLE_PIN_SEPARATE_ENTRY_REQUIRED");
        }
    }

    private static void requireLegacyMode(final Properties properties) {
        if ("true".equals(properties.getProperty("coletas6908.sample.shared.enabled"))) {
            throw new IllegalArgumentException("COL_SAMPLE_SHARED_RECEIPT_REQUIRED");
        }
    }

    private static Receipt executeTrial(
            final ShadowStorageProperties target,
            final SecretProvider secrets,
            final SampleWork work)
            throws Exception {
        if (target.usesSampleSaAuthentication()) {
            return ColetaShadowRollbackTrial.executeSampleSa(
                    target, secrets, bank -> work.execute(new BankTrial(bank)));
        }
        return ColetaShadowRollbackTrial.executeFromEnvironment(
                bank -> work.execute(new BankTrial(bank)));
    }

    static Receipt run(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeOperationalExecution.SourceGateways source,
            final Trial trial,
            final RuntimeHttpAttempts attempts)
            throws Exception {
        return run(plan, configuration, request, source, trial, attempts, () -> {});
    }

    private static Receipt run(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeOperationalExecution.SourceGateways source,
            final Trial trial,
            final RuntimeHttpAttempts attempts,
            final Runnable admissionCheckpoint)
            throws Exception {
        preflight(plan, configuration, request);
        final var deadline = new Coletas6908PilotDeadline(plan.deadline());
        final int observedRows;
        final int expectedRoots;
        try (trial) {
            final Runnable checkpoint =
                    () -> {
                        deadline.check();
                        admissionCheckpoint.run();
                        trial.checkpoint();
                        admissionCheckpoint.run();
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
        admissionCheckpoint.run();
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
        if (configuration.shadowStorage().usesSampleSaAuthentication()) {
            // Banco's pure constructor validates exact local SA target and verified TLS.
            if (configuration.shadowStorage().usesSampleSqlPinAuthentication()) {
                ShadowStorageProperties.enabledSampleSqlPin(
                        configuration.shadowStorage().jdbcUrl(),
                        configuration.shadowStorage().approvalReference());
            } else {
                ShadowStorageProperties.enabledSampleSa(configuration.shadowStorage().jdbcUrl());
            }
            Coletas6908PilotRunner.preflightScope(plan, configuration, request);
        } else {
            Coletas6908PilotRunner.preflight(plan, configuration, request);
        }
        if (plan.per() > 5
                || !configuration.environment().name().equals("LOCAL_SHADOW")
                || (!configuration.shadowStorage().usesIntegratedSecurity()
                        && !configuration.shadowStorage().usesSampleSaAuthentication())
                || configuration.dataExport().orElseThrow().resiliencePolicy().maxInFlight() != 1) {
            throw new IllegalArgumentException("COL_SAMPLE_SCOPE_REQUIRED");
        }
    }

    static void preflightShared(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Properties properties) {
        preflight(plan, configuration, request);
        validateAuthentication(configuration, properties);
        if (!"true".equals(properties.getProperty("coletas6908.sample.shared.enabled"))
                || !configuration.shadowStorage().usesSampleSaAuthentication()
                || plan.per() != 1) {
            throw new IllegalArgumentException("COL_SAMPLE_SHARED_SCOPE_REQUIRED");
        }
    }

    static void preflightPinned(
            final Coletas6908PilotPlan plan,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final Properties properties,
            final Coletas6908SamplePreflight.SharedAdmission admission) {
        admission.check(configuration.clock());
        preflightShared(plan, configuration, request, properties);
        if (!configuration.shadowStorage().usesSampleSqlPinAuthentication()
                || !admission.sqlPinned()) {
            throw new IllegalArgumentException("COL_SAMPLE_PIN_RECEIPT_REQUIRED");
        }
        for (final String global :
                List.of(
                        "javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType",
                        "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreProvider")) {
            if (properties.containsKey(global)) {
                throw new IllegalArgumentException("COL_SAMPLE_PIN_GLOBAL_TRUST_FORBIDDEN");
            }
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

    @FunctionalInterface
    interface TrialExecution {
        Receipt execute(ShadowStorageProperties target, SecretProvider secrets, SampleWork work)
                throws Exception;
    }

    @FunctionalInterface
    interface SampleWork {
        Receipt execute(Trial trial) throws Exception;
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
