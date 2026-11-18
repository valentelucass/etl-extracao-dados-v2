package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoShadowCapability;
import br.com.esl.etl.v2.plataforma.autorizacao.AuthorizedRuntimeInvocation;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationBoundary;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimePreflightReport;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeTargetPreflight;
import java.util.Objects;
import java.util.UUID;

/** Único ponto de composição do runtime; validar e planejar não criam clientes nem conexões. */
public final class RuntimeCompositionRoot {

    private final RuntimeConfiguration configuration;
    private final RuntimeTargetPreflight targetPreflight;
    private final RuntimeAuthorizationBoundary authorizationBoundary;
    private final ManifestoShadowCapability manifestoShadowCapability;
    private final java.util.function.Supplier<
                    br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization>
            operationalAuthority;
    private final OperationalExecutor operationalExecutor;
    private final IndependentExportExecutor independentExportExecutor;
    private final GovernedExportDelivery.Port governedExportDelivery;

    @FunctionalInterface
    interface IndependentExportExecutor {
        com.fasterxml.jackson.databind.node.ObjectNode execute(
                RuntimeConfiguration configuration,
                br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.Pin
                        selectionPin,
                com.fasterxml.jackson.databind.JsonNode selection,
                long startedNanos,
                java.time.Instant startedAt)
                throws Exception;
    }

    // No CLI/config/env hook. Production constructors keep the real authority and native wire.
    static RuntimeCompositionRoot forIndependentExportFixture(
            final RuntimeConfiguration configuration,
            final java.util.function.Supplier<
                            br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization>
                    authority,
            final IndependentExportExecutor executor,
            final GovernedExportDelivery.Port delivery) {
        return new RuntimeCompositionRoot(
                configuration,
                new RuntimeTargetPreflight(),
                denyAllBoundary(configuration),
                authority,
                RuntimeOperationalExecution::execute,
                executor,
                delivery);
    }

    public RuntimeCompositionRoot(final RuntimeConfiguration configuration) {
        this(configuration, new RuntimeTargetPreflight());
    }

    RuntimeCompositionRoot(
            final RuntimeConfiguration configuration,
            final RuntimeTargetPreflight targetPreflight) {
        this(configuration, targetPreflight, denyAllBoundary(configuration));
    }

    RuntimeCompositionRoot(
            final RuntimeConfiguration configuration,
            final RuntimeTargetPreflight targetPreflight,
            final RuntimeAuthorizationBoundary authorizationBoundary) {
        this(
                configuration,
                targetPreflight,
                authorizationBoundary,
                br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization
                        ::fromAdministeredArtifact,
                RuntimeOperationalExecution::execute);
    }

    RuntimeCompositionRoot(
            final RuntimeConfiguration configuration,
            final java.util.function.Supplier<
                            br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization>
                    authority,
            final OperationalExecutor executor) {
        this(
                configuration,
                new RuntimeTargetPreflight(),
                denyAllBoundary(configuration),
                authority,
                executor);
    }

    private RuntimeCompositionRoot(
            final RuntimeConfiguration configuration,
            final RuntimeTargetPreflight targetPreflight,
            final RuntimeAuthorizationBoundary authorizationBoundary,
            final java.util.function.Supplier<
                            br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization>
                    authority,
            final OperationalExecutor executor) {
        this(
                configuration,
                targetPreflight,
                authorizationBoundary,
                authority,
                executor,
                RuntimeIndependentExportExecution::execute,
                new CapturedDashboardStageDelivery());
    }

    private RuntimeCompositionRoot(
            final RuntimeConfiguration configuration,
            final RuntimeTargetPreflight targetPreflight,
            final RuntimeAuthorizationBoundary authorizationBoundary,
            final java.util.function.Supplier<
                            br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization>
                    authority,
            final OperationalExecutor executor,
            final IndependentExportExecutor independentExecutor,
            final GovernedExportDelivery.Port delivery) {
        this.configuration = Objects.requireNonNull(configuration, "A configuração é obrigatória.");
        this.targetPreflight =
                Objects.requireNonNull(targetPreflight, "O preflight de alvo é obrigatório.");
        this.authorizationBoundary =
                Objects.requireNonNull(
                        authorizationBoundary, "O boundary de autorização é obrigatório.");
        this.manifestoShadowCapability = ManifestoShadowCapability.localDenyAll();
        this.operationalAuthority = Objects.requireNonNull(authority);
        this.operationalExecutor = Objects.requireNonNull(executor);
        this.independentExportExecutor = Objects.requireNonNull(independentExecutor);
        this.governedExportDelivery = Objects.requireNonNull(delivery);
    }

    public RuntimePreflightReport validateConfiguration() {
        return targetPreflight.validate(configuration);
    }

    public RuntimePreflightReport createDryRunPlan() {
        return validateConfiguration();
    }

    /** Separate opt-in source profile; the existing durable authority owns every ALLOW/CONSUME. */
    com.fasterxml.jackson.databind.node.ObjectNode executeIndependentExport(
            final java.nio.file.Path operationalRequest,
            final br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.Pin
                    selectionPin,
            final com.fasterxml.jackson.databind.JsonNode selection,
            final long invocationStartedNanos,
            final java.time.Instant invocationStartedAt)
            throws Exception {
        return executeIndependentExport(
                operationalRequest,
                selectionPin,
                selection,
                invocationStartedNanos,
                invocationStartedAt,
                null);
    }

    com.fasterxml.jackson.databind.node.ObjectNode executeIndependentExport(
            final java.nio.file.Path operationalRequest,
            final br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.Pin
                    selectionPin,
            final com.fasterxml.jackson.databind.JsonNode selection,
            final long invocationStartedNanos,
            final java.time.Instant invocationStartedAt,
            final String officialProfile)
            throws Exception {
        final var request = RuntimeOperationalRequest.read(configuration, operationalRequest);
        RuntimeIndependentExportExecution.validate(configuration, request, selectionPin, selection);
        if (officialProfile != null) {
            if (!officialProfile.equals(selection.path("profile").asText())) {
                throw new IllegalArgumentException("EXPORT_OFFICIAL_PROFILE_BINDING");
            }
            GovernedExportDelivery.validatePlan(selection, officialProfile);
        }
        final var source = configuration.dataExport().orElseThrow();
        final var plan =
                br.com.esl.etl.v2.plataforma.orquestracao.IndependentExportPlanBinding.bind(
                        request.plan,
                        selectionPin.sha256(),
                        br.com.esl.etl.v2.plataforma.configuracao
                                .DataExportRuntimeConfigurationFingerprint.from(source)
                                .sha256(),
                        selection.required("profile").textValue());
        final var scope =
                br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope.from(
                        request.invocation,
                        br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction.RUN,
                        plan);
        final var authority = operationalAuthority.get();
        final var capability = authority.authorize(scope);
        return authority.consumeAndExecute(
                capability,
                scope,
                () -> {
                    try {
                        final var capture =
                                independentExportExecutor.execute(
                                        configuration,
                                        selectionPin,
                                        selection,
                                        invocationStartedNanos,
                                        invocationStartedAt);
                        return officialProfile == null
                                ? capture
                                : GovernedExportDelivery.finish(
                                        selection,
                                        selectionPin,
                                        capture,
                                        officialProfile,
                                        governedExportDelivery,
                                        invocationStartedNanos);
                    } catch (final Exception failure) {
                        throw new IllegalStateException(
                                "EXPORT_AUTHORIZED_EXECUTION_FAILED", failure);
                    }
                });
    }

    RuntimeDevPreparedInvocation prepareDevOperationalRequest(
            final RuntimeDevWorkspaceBundle bundle,
            final br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget target) {
        return prepareDevOperationalRequest(bundle, target, false, false);
    }

    RuntimeDevPreparedInvocation prepareDevOperationalRequest(
            final RuntimeDevWorkspaceBundle bundle,
            final br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget target,
            final boolean curatedSource,
            final boolean sampleOptIn) {
        validateConfiguration();
        if (!configuration.shadowStorage().usesC78LocalAuthentication()
                || !target.jdbcUrl().equals(configuration.shadowStorage().jdbcUrl())) {
            throw new IllegalArgumentException("DEV_C78_EFFECTIVE_TARGET_REQUIRED");
        }
        final var request = RuntimeOperationalRequest.read(configuration, bundle.request());
        if (request.laboratory()) {
            throw new IllegalArgumentException("DEV_LABORATORY_REQUEST_REFUSED");
        }
        if (sampleOptIn) {
            RuntimeDevSampleExecution.validateScope(configuration, request);
        }
        return new RuntimeDevPreparedInvocation(
                configuration, request, target, curatedSource, sampleOptIn);
    }

    RuntimeDevEngineExecution.Result executeDevEngine(
            final RuntimeDevPreparedInvocation prepared,
            final RuntimeDevWorkspaceBundle bundle,
            final java.io.PrintStream diagnostic,
            final java.nio.file.Path dispatch,
            final String dispatchSHA256)
            throws Exception {
        if (prepared.configuration != configuration
                || prepared.request.laboratory()
                || prepared.request.users != null
                || prepared.request.temporal != null
                || prepared.request.dependency != null
                || !java.util.Set.of(
                                br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate
                                        .COLETAS,
                                br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate
                                        .FRETES)
                        .contains(prepared.request.template)) {
            throw new IllegalArgumentException("DEV_ENGINE_PREPARED_SCOPE_REQUIRED");
        }
        return RuntimeDevEngineExecution.execute(
                prepared, bundle, diagnostic, dispatch, dispatchSHA256);
    }

    /**
     * Scope is frozen before the authority; business composition happens only after consumption.
     */
    br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory executeOperationalRequest(
            final java.nio.file.Path file, final RuntimeAction action) {
        return executeOperationalRequest(file, action, ignored -> {});
    }

    br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory executeOperationalRequest(
            final java.nio.file.Path file,
            final RuntimeAction action,
            final java.util.function.Consumer<String> diagnostic) {
        return executeOperationalRequest(file, action, diagnostic, null);
    }

    br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory executeOperationalRequest(
            final java.nio.file.Path file,
            final RuntimeAction action,
            final java.util.function.Consumer<String> diagnostic,
            final java.io.InputStream controls) {
        validateConfiguration();
        br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource.validateTarget(
                configuration.shadowStorage());
        final var request = RuntimeOperationalRequest.read(configuration, file);
        return executeFrozen(request, action, diagnostic, controls);
    }

    br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory executeFrozen(
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final java.util.function.Consumer<String> diagnostic,
            final java.io.InputStream controls) {
        if (request.temporal != null) {
            request.temporal.requireAction(action);
        }
        final var scope =
                br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope.from(
                        request.invocation, action, request.plan, request.referenceReleaseId);
        final var authority = operationalAuthority.get();
        final var capability = authority.authorize(scope);
        return authority.consumeAndExecute(
                capability,
                scope,
                () -> {
                    if (request.dependency != null && action != RuntimeAction.STATUS) {
                        final var predecessor =
                                executeFrozen(
                                        request.dependency, RuntimeAction.STATUS, diagnostic, null);
                        if (predecessor
                                != br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory
                                        .SUCCESS) {
                            diagnostic.accept(
                                    "RUNTIME_DEPENDENCY reason=DEPENDENCY_NOT_PUBLISHED"
                                            + " source_composed=0");
                            return br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory
                                    .SOURCE_DQ;
                        }
                    }
                    return operationalExecutor.execute(
                            configuration, request, action, diagnostic, controls);
                });
    }

    /** Única entrada reservada às futuras ações operacionais; o estado atual sempre nega. */
    public AuthorizedRuntimeInvocation authorizeOperationalAction(
            final UUID invocationId, final RuntimeAction action) {
        return authorizationBoundary.authorize(invocationId, action);
    }

    /** Capability declarativa da vertical; o único boundary operacional continua deny-all. */
    public ManifestoShadowCapability manifestoShadowCapability() {
        return manifestoShadowCapability;
    }

    private static RuntimeAuthorizationBoundary denyAllBoundary(
            final RuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "A configuração é obrigatória.");
        return new RuntimeAuthorizationBoundary(
                event -> {
                    throw new IllegalStateException(
                            "O sink de auditoria operacional ainda não está configurado.");
                },
                configuration.clock());
    }

    @FunctionalInterface
    interface OperationalExecutor {
        br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory execute(
                RuntimeConfiguration configuration,
                RuntimeOperationalRequest request,
                RuntimeAction action,
                java.util.function.Consumer<String> diagnostic,
                java.io.InputStream controls);
    }
}
