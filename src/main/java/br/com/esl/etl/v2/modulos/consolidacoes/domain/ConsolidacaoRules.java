package br.com.esl.etl.v2.modulos.consolidacoes.domain;

/** CON-0761-01: all source fields must retain a valid, lossless disposition. */
public final class ConsolidacaoRules {
    public static final String CONTRACT_RULE = "CON-0761-01";

    private ConsolidacaoRules() {}

    public static boolean accepts(final ConsolidacaoObservation observation) {
        return observation != null && observation.valid();
    }
}
