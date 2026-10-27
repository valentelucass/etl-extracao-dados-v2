package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Closed core vocabulary; none of these values establishes provider identity or admission. */
public enum ObservedExpansionCaptureTemplate {
    CAP(8636, "CAP", "accounting_debits", "issue_date", "issue_date desc"),
    FAT(4924, "FAT", "freights", "service_at", "unique_id asc"),
    INV(10633, "INV", "check_in_orders", "started_at", "sequence_code asc"),
    SIN(6392, "SIN", "insurance_claims", "opening_at_date", "sequence_code asc");

    private final int templateId;
    private final String vertical;
    private final String filterRoot;
    private final String filterField;
    private final String knownOrderExpression;

    ObservedExpansionCaptureTemplate(
            final int templateId,
            final String vertical,
            final String filterRoot,
            final String filterField,
            final String knownOrderExpression) {
        this.templateId = templateId;
        this.vertical = vertical;
        this.filterRoot = filterRoot;
        this.filterField = filterField;
        this.knownOrderExpression = knownOrderExpression;
    }

    public int templateId() {
        return templateId;
    }

    public String vertical() {
        return vertical;
    }

    public String filterRoot() {
        return filterRoot;
    }

    public String filterField() {
        return filterField;
    }

    /** Existing code expression, not a claim that the source has accepted this ordering. */
    public String knownOrderExpression() {
        return knownOrderExpression;
    }
}
