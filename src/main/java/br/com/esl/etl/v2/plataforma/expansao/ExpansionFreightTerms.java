package br.com.esl.etl.v2.plataforma.expansao;

import java.time.LocalDate;

/** Synthetic lateral source inputs for MAT-03; they are neither ESL fields nor final revenue. */
public record ExpansionFreightTerms(
        String sourceKey,
        int revision,
        LocalDate billingReferenceDate,
        String classification,
        Boolean courtesy,
        Boolean eligible,
        Integer fallbackVolumes,
        String payerToken,
        String currency,
        String unit,
        boolean active,
        String evidence) {
    public ExpansionFreightTerms {
        if (sourceKey == null
                || !sourceKey.matches("INTEGER:[1-9][0-9]{0,17}")
                || revision < 1
                || revision > 1000
                || classification != null && classification.length() > 256
                || fallbackVolumes != null && (fallbackVolumes < 0 || fallbackVolumes > 1000000)
                || payerToken == null
                || !payerToken.matches("[0-9a-f]{64}")
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || !"MAJOR".equals(unit)
                || evidence == null
                || !evidence.matches("synthetic-[a-z0-9-]{1,48}")) {
            throw new IllegalArgumentException("EXP_FREIGHT_TERMS_BINDING");
        }
    }

    @Override
    public String toString() {
        return "ExpansionFreightTerms[revision=" + revision + "]";
    }
}
