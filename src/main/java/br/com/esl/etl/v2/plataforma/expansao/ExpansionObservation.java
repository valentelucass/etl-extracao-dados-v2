package br.com.esl.etl.v2.plataforma.expansao;

/** Domain-only physical observation, before any independently declared identity binding. */
public interface ExpansionObservation {
    String vertical();

    boolean valid();
}
