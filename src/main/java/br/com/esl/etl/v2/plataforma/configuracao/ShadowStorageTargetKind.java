package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.Locale;

/** Classifica o único alvo permitido para a auditoria de sombra habilitada. */
public enum ShadowStorageTargetKind {
    LOCAL_EPHEMERAL,
    APPROVED_NON_PRODUCTION;

    static ShadowStorageTargetKind fromConfiguration(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Configuração de tipo de alvo de sombra inválida.");
        }
    }
}
