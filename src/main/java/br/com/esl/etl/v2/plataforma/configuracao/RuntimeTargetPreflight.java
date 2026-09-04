package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.Objects;

/** Valida o alvo antes de qualquer factory de execução poder ser composta. */
public final class RuntimeTargetPreflight {

    public RuntimePreflightReport validate(final RuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "A configuração de runtime é obrigatória.");
        if (configuration.environment() != RuntimeEnvironment.LOCAL_SHADOW) {
            throw new IllegalStateException(
                    "O ambiente configurado ainda não possui autorização operacional de escrita.");
        }
        final ShadowStorageProperties shadowStorage = configuration.shadowStorage();
        if (shadowStorage.auditEnabled()
                && shadowStorage.targetKind() != ShadowStorageTargetKind.LOCAL_EPHEMERAL) {
            throw new IllegalStateException(
                    "O alvo de sombra não está na allowlist local aprovada.");
        }
        return new RuntimePreflightReport(
                configuration.environment(),
                configuration.businessZone(),
                configuration.dataExport().isPresent(),
                shadowStorage.auditEnabled());
    }
}
