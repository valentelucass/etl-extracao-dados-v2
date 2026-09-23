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
        this.configuration = Objects.requireNonNull(configuration, "A configuração é obrigatória.");
        this.targetPreflight =
                Objects.requireNonNull(targetPreflight, "O preflight de alvo é obrigatório.");
        this.authorizationBoundary =
                Objects.requireNonNull(
                        authorizationBoundary, "O boundary de autorização é obrigatório.");
        this.manifestoShadowCapability = ManifestoShadowCapability.localDenyAll();
        this.operationalAuthority = Objects.requireNonNull(authority);
        this.operationalExecutor = Objects.requireNonNull(executor);
    }

    public RuntimePreflightReport validateConfiguration() {
        return targetPreflight.validate(configuration);
    }

    public RuntimePreflightReport createDryRunPlan() {
        return validateConfiguration();
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

    private br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory executeFrozen(
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
                                    "RUNTIME_DEPENDENCY reason=DEPENDENCY_NOT_PUBLISHED source_composed=0");
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
