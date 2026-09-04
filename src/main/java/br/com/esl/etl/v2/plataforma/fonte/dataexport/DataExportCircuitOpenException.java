package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Indica que o circuito daquele template está aberto e evita nova chamada à fonte. */
public final class DataExportCircuitOpenException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int templateId;

    public DataExportCircuitOpenException(final int templateId) {
        super(
                "Circuit breaker Data Export aberto para o template "
                        + validateTemplateId(templateId)
                        + ".");
        this.templateId = templateId;
    }

    public int templateId() {
        return templateId;
    }

    private static int validateTemplateId(final int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("O template Data Export deve ser positivo.");
        }
        return value;
    }
}
