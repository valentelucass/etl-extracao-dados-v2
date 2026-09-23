package br.com.esl.etl.v2.plataforma.expansao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** MAT-04 synthetic title grain; dates change attributes of the same root. */
public record ExpansionInvoiceFact(
        long rootId,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate paidDate,
        LocalDate baseDate,
        LocalDate monthlyReferenceDate,
        String clientKey,
        String clientProvenance,
        Boolean hasInvoice,
        String processState,
        String paymentState,
        Integer daysPastDue,
        BigDecimal operationalValue,
        String currency,
        String unit,
        String disposition,
        long components,
        long documents,
        long freights,
        int referenceRevision,
        Long labelRelease,
        LocalDate businessDate,
        UUID receipt) {
    @Override
    public String toString() {
        return "ExpansionInvoiceFact[disposition=" + disposition + "]";
    }
}
