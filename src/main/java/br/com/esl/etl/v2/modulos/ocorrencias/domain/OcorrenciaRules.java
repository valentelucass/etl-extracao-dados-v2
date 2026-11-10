package br.com.esl.etl.v2.modulos.ocorrencias.domain;

/** OCO-0761-01: all source fields must retain a valid, lossless disposition. */
public final class OcorrenciaRules {
    public static final String CONTRACT_RULE = "OCO-0761-01";

    private OcorrenciaRules() {}

    public static boolean accepts(final OcorrenciaObservation observation) {
        return observation != null && observation.valid();
    }
}
