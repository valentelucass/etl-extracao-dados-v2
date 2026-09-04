package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Adaptador transitório para variáveis de ambiente protegidas, sem suporte a arquivos ou -D. */
public final class EnvironmentSecretProvider implements SecretProvider {

    private final Map<SecretKey, String> secrets;

    public EnvironmentSecretProvider(final Map<String, String> environment) {
        Objects.requireNonNull(environment, "O ambiente é obrigatório.");
        final EnumMap<SecretKey, String> selected = new EnumMap<>(SecretKey.class);
        for (final SecretKey key : SecretKey.values()) {
            final String value = environment.get(key.environmentVariable());
            if (value != null) {
                selected.put(key, value);
            }
        }
        this.secrets = Map.copyOf(selected);
    }

    @Override
    public String require(final SecretKey key) {
        Objects.requireNonNull(key, "A referência do segredo é obrigatória.");
        final String value = secrets.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Segredo obrigatório ausente no provider protegido.");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "EnvironmentSecretProvider[redacted]";
    }
}
