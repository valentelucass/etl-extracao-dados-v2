package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

/** Ausência incremental nunca desativa a raiz e sweep permanece indisponível. */
public final class LocalizacaoCargaAbsencePolicy {
    public static final String COMPLETENESS = "BLOCKED_NO_COMPLETENESS_PROOF";
    public static final String SWEEP = "NOT_EVALUATED";

    private LocalizacaoCargaAbsencePolicy() {}

    public static Action action(final LocalizacaoCargaAttributePresence presence) {
        return switch (presence) {
            case ABSENT -> Action.PRESERVE_KNOWN_VALUE;
            case NULL -> Action.APPLY_EXPLICIT_NULL_ON_ACCEPTED_NEWER_OBSERVATION;
            case VALUE -> Action.APPLY_TYPED_VALUE_ON_ACCEPTED_NEWER_OBSERVATION;
        };
    }

    public enum Action {
        PRESERVE_KNOWN_VALUE,
        APPLY_EXPLICIT_NULL_ON_ACCEPTED_NEWER_OBSERVATION,
        APPLY_TYPED_VALUE_ON_ACCEPTED_NEWER_OBSERVATION
    }
}
