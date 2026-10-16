package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** A real frozen plan/target, without SQL, source clients or an effect capability. */
final class RuntimeDevPreparedInvocation {
    final RuntimeConfiguration configuration;
    final RuntimeOperationalRequest request;
    final C78LocalSqlTarget target;
    final RuntimeAuthorizationScope scope;
    final boolean curatedSource;
    final boolean sampleOptIn;
    final br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan catalogPlan;

    RuntimeDevPreparedInvocation(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final C78LocalSqlTarget target) {
        this(configuration, request, target, false, false);
    }

    RuntimeDevPreparedInvocation(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final C78LocalSqlTarget target,
            final boolean curatedSource,
            final boolean sampleOptIn) {
        this.configuration = configuration;
        this.request = request;
        this.target = target;
        this.curatedSource = curatedSource;
        this.sampleOptIn = sampleOptIn;
        if (!configuration.shadowStorage().usesC78LocalAuthentication()
                || !target.jdbcUrl().equals(configuration.shadowStorage().jdbcUrl())
                || configuration.shadowStorage().usesIntegratedSecurity()
                || configuration.shadowStorage().usesSampleSaAuthentication()
                || configuration.shadowStorage().usesSampleSqlPinAuthentication()) {
            throw new IllegalArgumentException("DEV_C78_EFFECTIVE_TARGET_REQUIRED");
        }
        scope =
                RuntimeAuthorizationScope.from(
                        request.invocation,
                        RuntimeAction.RUN,
                        request.plan,
                        request.referenceReleaseId);
        catalogPlan =
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.prepare(
                        request.invocation, target);
    }

    static RuntimeConfiguration composeC78(
            final RuntimeConfiguration base, final C78LocalSqlTarget target) {
        if (base.environment() != RuntimeEnvironment.LOCAL_SHADOW
                || base.shadowStorage().auditEnabled()) {
            throw new IllegalArgumentException("DEV_SOURCE_BASE_WITHOUT_STORAGE_REQUIRED");
        }
        return new RuntimeConfiguration(
                base.environment(),
                base.businessZone(),
                base.clock(),
                base.dataExport(),
                base.graphQl(),
                ShadowStorageProperties.enabledC78Local(target));
    }

    Map<String, Object> preparationReceipt(final RuntimeDevWorkspaceBundle bundle) {
        final var result = new LinkedHashMap<String, Object>();
        result.put("status", "DEV_MAIN_PREPARED_EFFECT_ADMISSION_REQUIRED");
        result.put("profile", C78LocalSqlTarget.PROFILE);
        result.put("manifestSHA256", bundle.manifestSha256());
        result.put("pinnedFiles", bundle.pinnedFiles());
        result.put("plannedWorkloads", request.plan.executionCount());
        result.put("effectivePlanSHA256", request.plan.fingerprint().sha256());
        result.put(
                "configurationSHA256", bundle.entrySha256(RuntimeDevWorkspaceBundle.CONFIGURATION));
        result.put("requestSHA256", bundle.entrySha256(RuntimeDevWorkspaceBundle.REQUEST));
        result.put(
                "catalogMaxSelects",
                br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan.MAX_SELECTS);
        result.put("effectiveShadowAudit", configuration.shadowStorage().auditEnabled());
        result.put(
                "effectiveIntegratedSecurity",
                configuration.shadowStorage().usesIntegratedSecurity());
        result.put(
                "effectiveC78Metadata", configuration.shadowStorage().usesC78LocalAuthentication());
        result.put("sampleProfileInherited", false);
        result.put("authorityAdmitted", false);
        result.put("sourceAdmitted", false);
        result.put("sqlAdmitted", false);
        result.put("engineExecuted", false);
        result.put("curatedSourceMetadata", curatedSource);
        result.put("sampleExecutionSelected", sampleOptIn);
        result.put("physicalEvidence", false);
        return Map.copyOf(result);
    }

    @Override
    public String toString() {
        return "RuntimeDevPreparedInvocation[redacted; effects-not-admitted]";
    }
}
