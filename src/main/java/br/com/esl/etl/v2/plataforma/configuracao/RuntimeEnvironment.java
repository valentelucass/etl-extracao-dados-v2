package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.Locale;

/** Ambientes de execução permitidos enquanto o V2 permanece exclusivamente em sombra. */
public enum RuntimeEnvironment {
    LOCAL_SHADOW,
    NON_PRODUCTION_SHADOW;

    static RuntimeEnvironment fromConfiguration(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Ambiente de execução V2 inválido ou não autorizado.");
        }
    }
}
