package br.com.esl.etl.v2.plataforma.contrato;

import java.util.Objects;

/** Gate fail-closed que impede transformar fim de paginação em prova de dataset completo. */
public enum SourceCompletenessStatus {
    PROVEN_COMPLETE,
    BLOCKED_NO_COMPLETENESS_PROOF;

    /** Registros observados podem seguir para shadow; ausência e autoridade continuam proibidas. */
    public boolean permits(final SourceDataEffect effect) {
        final SourceDataEffect required =
                Objects.requireNonNull(effect, "O efeito de dados é obrigatório.");
        return this == PROVEN_COMPLETE || required == SourceDataEffect.SHADOW_UPSERT;
    }
}
