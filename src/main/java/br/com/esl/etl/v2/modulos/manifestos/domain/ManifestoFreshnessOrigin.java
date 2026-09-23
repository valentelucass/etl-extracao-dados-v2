package br.com.esl.etl.v2.modulos.manifestos.domain;

/** Ordem fechada de frescor de Manifestos, compartilhada por dedupe e promoção. */
public enum ManifestoFreshnessOrigin {
    FINISHED_AT("finished_at"),
    CLOSED_AT("closed_at"),
    DEPARTURED_AT("departured_at"),
    CREATED_AT("created_at");

    private final String sourceField;

    ManifestoFreshnessOrigin(final String sourceField) {
        this.sourceField = sourceField;
    }

    public String sourceField() {
        return sourceField;
    }
}
