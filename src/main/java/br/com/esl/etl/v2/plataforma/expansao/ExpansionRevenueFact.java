package br.com.esl.etl.v2.plataforma.expansao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * MAT-03 has exactly one row per explicitly bound synthetic freight, regardless of date changes.
 */
public record ExpansionRevenueFact(
        long dependencyId,
        String sourceKey,
        long freightStageId,
        Long termId,
        Integer termRevision,
        LocalDate originalReferenceDate,
        LocalDate billingReferenceDate,
        String branchCode,
        Long calendarRelease,
        Long branchRelease,
        Long payerRelease,
        BigDecimal sourceValue,
        BigDecimal revenueValue,
        String currency,
        String unit,
        boolean cancelled,
        String cancellationProvenance,
        boolean billingBlock,
        Boolean courtesy,
        Boolean eligible,
        Boolean active,
        Integer volumes,
        String volumeProvenance,
        Long locationStageId,
        String disposition,
        long invoices,
        long documents,
        int referenceRevision,
        LocalDate businessDate,
        UUID receipt) {
    @Override
    public String toString() {
        return "ExpansionRevenueFact[disposition=" + disposition + "]";
    }
}
