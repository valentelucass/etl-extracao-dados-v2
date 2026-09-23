package br.com.esl.etl.v2.modulos.coletas.domain;

import java.util.Objects;

/**
 * Regra congelada em V2-010: uma ausência observada não modifica o estado corrente de Coletas.
 * V2-013 e uma prova independente de snapshot completo continuam obrigatórios antes de qualquer
 * avaliação de ausência.
 */
public final class ColetaAbsencePolicy {

    public static final String COMPLETENESS = "BLOCKED_NO_COMPLETENESS_PROOF";
    public static final String EVALUATION = "NOT_EVALUATED";

    private ColetaAbsencePolicy() {}

    public static Decision observe(final Observation observation) {
        Objects.requireNonNull(observation, "A observação de ausência é obrigatória.");
        return new Decision(COMPLETENESS, EVALUATION, false);
    }

    public enum Observation {
        FIRST_INDEPENDENT_ABSENCE,
        SECOND_INDEPENDENT_ABSENCE,
        REAPPEARANCE
    }

    /** Resultado auditável, sem rótulo de compatibilidade e sem mutação de estado ativo. */
    public record Decision(String completeness, String evaluation, boolean changesActiveState) {}
}
