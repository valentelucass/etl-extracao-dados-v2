package br.com.esl.etl.v2.bootstrap;

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
        this.configuration = Objects.requireNonNull(configuration, "A configuração é obrigatória.");
        this.targetPreflight =
                Objects.requireNonNull(targetPreflight, "O preflight de alvo é obrigatório.");
        this.authorizationBoundary =
                Objects.requireNonNull(
                        authorizationBoundary, "O boundary de autorização é obrigatório.");
    }

    public RuntimePreflightReport validateConfiguration() {
        return targetPreflight.validate(configuration);
    }

    public RuntimePreflightReport createDryRunPlan() {
        return validateConfiguration();
    }

    /** Única entrada reservada às futuras ações operacionais; o estado atual sempre nega. */
    public AuthorizedRuntimeInvocation authorizeOperationalAction(
            final UUID invocationId, final RuntimeAction action) {
        return authorizationBoundary.authorize(invocationId, action);
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
}
