package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.util.Objects;
import java.util.regex.Pattern;

/** Configuração imutável de uma instância Data Export, sem expor seu segredo. */
public record DataExportSourceConfiguration(
        String sourceInstance,
        String tenantScope,
        DataExportClientSettings settings,
        EslResiliencePolicy resiliencePolicy) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public DataExportSourceConfiguration {
        sourceInstance = validateIdentifier(sourceInstance, "A instância da fonte é obrigatória.");
        tenantScope = validateIdentifier(tenantScope, "O escopo de tenant é obrigatório.");
        settings = Objects.requireNonNull(settings, "A configuração Data Export é obrigatória.");
        resiliencePolicy =
                Objects.requireNonNull(
                        resiliencePolicy, "A política de resiliência ESL é obrigatória.");
    }

    private static String validateIdentifier(final String value, final String message) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
