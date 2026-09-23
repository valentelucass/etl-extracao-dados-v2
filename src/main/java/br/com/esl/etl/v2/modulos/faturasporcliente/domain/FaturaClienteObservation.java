package br.com.esl.etl.v2.modulos.faturasporcliente.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed physical observation; none of these candidates supplies a synthetic root identity. */
public record FaturaClienteObservation(
        ExpansionValue<String> comments,
        ExpansionValue<String> corporationSequenceNumber,
        ExpansionValue<Boolean> courtesy,
        ExpansionValue<String> emissionType,
        ExpansionValue<java.time.Instant> finishedAt,
        ExpansionValue<String> fitAntAntName,
        ExpansionValue<java.math.BigDecimal> fitAntDiscountValue,
        ExpansionValue<String> fitAntDocument,
        ExpansionValue<java.time.LocalDate> fitAntIlsAtnTransactionDate,
        ExpansionValue<java.time.LocalDate> fitAntIlsDueDate,
        ExpansionValue<java.time.LocalDate> fitAntIlsOriginalDueDate,
        ExpansionValue<java.math.BigDecimal> fitAntInterestValue,
        ExpansionValue<java.time.LocalDate> fitAntIssueDate,
        ExpansionValue<String> fitAntTatAccountNumber,
        ExpansionValue<String> fitAntTatAgencyNumber,
        ExpansionValue<String> fitAntTatBnkName,
        ExpansionValue<String> fitAntTatBroDescription,
        ExpansionValue<String> fitAntTatCustomInstruction,
        ExpansionValue<java.math.BigDecimal> fitAntValue,
        ExpansionValue<String> fitCrnPsnNickname,
        ExpansionValue<java.time.Instant> fitDTCreatedAt,
        ExpansionValue<String> fitDiySaeName,
        ExpansionValue<String> fitDynDrtNickname,
        ExpansionValue<java.time.Instant> fitFheCteIssuedAt,
        ExpansionValue<String> fitFheCteKey,
        ExpansionValue<String> fitFheCteNumber,
        ExpansionValue<String> fitFheCteStatus,
        ExpansionValue<String> fitFheCteStatusResult,
        ExpansionValue<String> fitFsnName,
        ExpansionValue<String> fitFteFoeOreDescription,
        ExpansionValue<Boolean> fitFteHasDeliveryReceipt,
        ExpansionValue<ExpansionStrings> fitFteInvoicesOrderNumber,
        ExpansionValue<String> fitNseNumber,
        ExpansionValue<String> fitPyrCorBillingCycle,
        ExpansionValue<String> fitPyrCorBillingDueInDays,
        ExpansionValue<String> fitPyrDocument,
        ExpansionValue<String> fitPyrName,
        ExpansionValue<String> fitRptDocument,
        ExpansionValue<String> fitRptName,
        ExpansionValue<String> fitSdrDocument,
        ExpansionValue<String> fitSdrName,
        ExpansionValue<String> fitSpsSlrPsnName,
        ExpansionValue<String> id,
        ExpansionValue<ExpansionStrings> invoicesMapping,
        ExpansionValue<String> nfseNumber,
        ExpansionValue<String> paymentType,
        ExpansionValue<String> referenceNumber,
        ExpansionValue<java.time.Instant> serviceAt,
        ExpansionValue<String> serviceType,
        ExpansionValue<String> status,
        ExpansionValue<java.math.BigDecimal> thirdPartyCtesValue,
        ExpansionValue<java.math.BigDecimal> total,
        ExpansionValue<String> type)
        implements ExpansionObservation {
    public FaturaClienteObservation {
        java.util.Objects.requireNonNull(comments);
        java.util.Objects.requireNonNull(corporationSequenceNumber);
        java.util.Objects.requireNonNull(courtesy);
        java.util.Objects.requireNonNull(emissionType);
        java.util.Objects.requireNonNull(finishedAt);
        java.util.Objects.requireNonNull(fitAntAntName);
        java.util.Objects.requireNonNull(fitAntDiscountValue);
        java.util.Objects.requireNonNull(fitAntDocument);
        java.util.Objects.requireNonNull(fitAntIlsAtnTransactionDate);
        java.util.Objects.requireNonNull(fitAntIlsDueDate);
        java.util.Objects.requireNonNull(fitAntIlsOriginalDueDate);
        java.util.Objects.requireNonNull(fitAntInterestValue);
        java.util.Objects.requireNonNull(fitAntIssueDate);
        java.util.Objects.requireNonNull(fitAntTatAccountNumber);
        java.util.Objects.requireNonNull(fitAntTatAgencyNumber);
        java.util.Objects.requireNonNull(fitAntTatBnkName);
        java.util.Objects.requireNonNull(fitAntTatBroDescription);
        java.util.Objects.requireNonNull(fitAntTatCustomInstruction);
        java.util.Objects.requireNonNull(fitAntValue);
        java.util.Objects.requireNonNull(fitCrnPsnNickname);
        java.util.Objects.requireNonNull(fitDTCreatedAt);
        java.util.Objects.requireNonNull(fitDiySaeName);
        java.util.Objects.requireNonNull(fitDynDrtNickname);
        java.util.Objects.requireNonNull(fitFheCteIssuedAt);
        java.util.Objects.requireNonNull(fitFheCteKey);
        java.util.Objects.requireNonNull(fitFheCteNumber);
        java.util.Objects.requireNonNull(fitFheCteStatus);
        java.util.Objects.requireNonNull(fitFheCteStatusResult);
        java.util.Objects.requireNonNull(fitFsnName);
        java.util.Objects.requireNonNull(fitFteFoeOreDescription);
        java.util.Objects.requireNonNull(fitFteHasDeliveryReceipt);
        java.util.Objects.requireNonNull(fitFteInvoicesOrderNumber);
        java.util.Objects.requireNonNull(fitNseNumber);
        java.util.Objects.requireNonNull(fitPyrCorBillingCycle);
        java.util.Objects.requireNonNull(fitPyrCorBillingDueInDays);
        java.util.Objects.requireNonNull(fitPyrDocument);
        java.util.Objects.requireNonNull(fitPyrName);
        java.util.Objects.requireNonNull(fitRptDocument);
        java.util.Objects.requireNonNull(fitRptName);
        java.util.Objects.requireNonNull(fitSdrDocument);
        java.util.Objects.requireNonNull(fitSdrName);
        java.util.Objects.requireNonNull(fitSpsSlrPsnName);
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(invoicesMapping);
        java.util.Objects.requireNonNull(nfseNumber);
        java.util.Objects.requireNonNull(paymentType);
        java.util.Objects.requireNonNull(referenceNumber);
        java.util.Objects.requireNonNull(serviceAt);
        java.util.Objects.requireNonNull(serviceType);
        java.util.Objects.requireNonNull(status);
        java.util.Objects.requireNonNull(thirdPartyCtesValue);
        java.util.Objects.requireNonNull(total);
        java.util.Objects.requireNonNull(type);
    }

    @Override
    public String vertical() {
        return "FAT";
    }

    @Override
    public boolean valid() {
        return comments.valid()
                && corporationSequenceNumber.valid()
                && courtesy.valid()
                && emissionType.valid()
                && finishedAt.valid()
                && fitAntAntName.valid()
                && fitAntDiscountValue.valid()
                && fitAntDocument.valid()
                && fitAntIlsAtnTransactionDate.valid()
                && fitAntIlsDueDate.valid()
                && fitAntIlsOriginalDueDate.valid()
                && fitAntInterestValue.valid()
                && fitAntIssueDate.valid()
                && fitAntTatAccountNumber.valid()
                && fitAntTatAgencyNumber.valid()
                && fitAntTatBnkName.valid()
                && fitAntTatBroDescription.valid()
                && fitAntTatCustomInstruction.valid()
                && fitAntValue.valid()
                && fitCrnPsnNickname.valid()
                && fitDTCreatedAt.valid()
                && fitDiySaeName.valid()
                && fitDynDrtNickname.valid()
                && fitFheCteIssuedAt.valid()
                && fitFheCteKey.valid()
                && fitFheCteNumber.valid()
                && fitFheCteStatus.valid()
                && fitFheCteStatusResult.valid()
                && fitFsnName.valid()
                && fitFteFoeOreDescription.valid()
                && fitFteHasDeliveryReceipt.valid()
                && fitFteInvoicesOrderNumber.valid()
                && fitNseNumber.valid()
                && fitPyrCorBillingCycle.valid()
                && fitPyrCorBillingDueInDays.valid()
                && fitPyrDocument.valid()
                && fitPyrName.valid()
                && fitRptDocument.valid()
                && fitRptName.valid()
                && fitSdrDocument.valid()
                && fitSdrName.valid()
                && fitSpsSlrPsnName.valid()
                && id.valid()
                && invoicesMapping.valid()
                && nfseNumber.valid()
                && paymentType.valid()
                && referenceNumber.valid()
                && serviceAt.valid()
                && serviceType.valid()
                && status.valid()
                && thirdPartyCtesValue.valid()
                && total.valid()
                && type.valid();
    }
}
