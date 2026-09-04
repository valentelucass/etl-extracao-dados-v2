package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.util.Objects;
import java.util.regex.Pattern;

/** Configuração imutável de uma instância GraphQL, sem segredo materializado. */
public record GraphQlSourceConfiguration(
        String sourceInstance,
        String tenantScope,
        GraphQlClientSettings settings,
        EslResiliencePolicy resiliencePolicy) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public GraphQlSourceConfiguration {
        sourceInstance = validateIdentifier(sourceInstance, "A instância GraphQL é obrigatória.");
        tenantScope = validateIdentifier(tenantScope, "O tenant GraphQL é obrigatório.");
        settings = Objects.requireNonNull(settings, "A configuração GraphQL é obrigatória.");
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

    @Override
    public String toString() {
        return "GraphQlSourceConfiguration[sourceInstance=<redacted>, tenantScope=<redacted>, "
                + "settings="
                + settings
                + ", resiliencePolicy="
                + resiliencePolicy
                + "]";
    }
}
