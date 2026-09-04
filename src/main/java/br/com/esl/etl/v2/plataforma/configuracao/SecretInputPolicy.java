package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Impede que canais inseguros recebam chaves classificadas como segredo. */
public final class SecretInputPolicy {

    private SecretInputPolicy() {}

    static void rejectSecretSystemProperties(final Properties systemProperties) {
        Objects.requireNonNull(systemProperties, "As propriedades de sistema são obrigatórias.");
        for (final String propertyName : systemProperties.stringPropertyNames()) {
            if (isSecretName(propertyName)) {
                throw new IllegalArgumentException(
                        "Segredos não são aceitos por propriedades de sistema.");
            }
            final String normalized = propertyName.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("v2.")
                    || normalized.startsWith("v2_")
                    || normalized.startsWith("runtime.")
                    || normalized.startsWith("dataexport.")
                    || normalized.startsWith("graphql.")
                    || normalized.startsWith("shadow.")) {
                throw new IllegalArgumentException(
                        "Configuração de runtime não é aceita por propriedades de sistema.");
            }
        }
    }

    public static void rejectSecretCommandLineOption(final String option) {
        Objects.requireNonNull(option, "A opção de linha de comando é obrigatória.");
        final int valueSeparator = option.indexOf('=');
        final String optionName = valueSeparator < 0 ? option : option.substring(0, valueSeparator);
        if (isSecretName(optionName)) {
            throw new IllegalArgumentException("Segredos não são aceitos pela linha de comando.");
        }
    }

    static void rejectSecretFileProperties(final Properties properties) {
        Objects.requireNonNull(properties, "As propriedades de configuração são obrigatórias.");
        for (final String propertyName : properties.stringPropertyNames()) {
            if (isSecretName(propertyName)) {
                throw new IllegalArgumentException(
                        "Segredos não são aceitos no arquivo de configuração.");
            }
        }
    }

    private static boolean isSecretName(final String value) {
        final String normalized =
                value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                        .toLowerCase(Locale.ROOT)
                        .replace('_', '-')
                        .replace('.', '-');
        final String compact = normalized.replace("-", "");
        return compact.contains("token")
                || compact.contains("secret")
                || compact.contains("password")
                || compact.contains("passwd")
                || compact.contains("passphrase")
                || compact.contains("credential")
                || compact.contains("authorization")
                || compact.contains("apikey")
                || compact.contains("accesskey")
                || compact.contains("privatekey")
                || compact.contains("clientkey")
                || compact.equals("pwd")
                || compact.endsWith("pwd");
    }
}
