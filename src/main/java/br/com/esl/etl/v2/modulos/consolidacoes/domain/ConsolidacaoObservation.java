package br.com.esl.etl.v2.modulos.consolidacoes.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Physical source observation. No business identifier is asserted to be unique. */
public record ConsolidacaoObservation(
        ExpansionValue<String> ctdFgsCrnPsnNickname,
        ExpansionValue<String> sequenceCode,
        ExpansionValue<java.time.LocalDate> issueDate,
        ExpansionValue<String> ctdRhrCpyNickname,
        ExpansionValue<String> ctdRhrCpyDocument,
        ExpansionValue<String> documentNumber,
        ExpansionValue<String> ctdFgsFheCteNumber,
        ExpansionValue<java.math.BigDecimal> chargedValue,
        ExpansionValue<java.math.BigDecimal> subtotal,
        ExpansionValue<java.math.BigDecimal> ctdFgsSubtotal,
        ExpansionValue<java.math.BigDecimal> ctdFgsTotal,
        ExpansionValue<java.math.BigDecimal> ctdFgsTaxedWeight,
        ExpansionValue<Boolean> ctdFgsFteHasDeliveryReceipt,
        ExpansionValue<String> billStatus,
        ExpansionValue<java.time.Instant> ctdFgsServiceAt,
        ExpansionValue<String> ctdFgsCorporationSequenceNumber,
        ExpansionValue<String> ctdFgsInvoicesVolumes,
        ExpansionValue<br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings>
                ctdFgsInvoicesMapping,
        ExpansionValue<String> ctdFgsDosCtyName,
        ExpansionValue<String> ctdSdrDocument,
        ExpansionValue<String> ctdSdrNickname,
        ExpansionValue<String> ctdFgsOasCtyName,
        ExpansionValue<String> ctdRptDocument,
        ExpansionValue<String> ctdRptNickname,
        ExpansionValue<Boolean> ctdRptChargeTde,
        ExpansionValue<java.time.Instant> ctdFgsDpnDeliveryPredictionAt,
        ExpansionValue<java.time.Instant> ctdFgsFinishedAt,
        ExpansionValue<Boolean> ctdFgsDpnFinishedWithinDeadline,
        ExpansionValue<String> ctdFgsFteLceOreDescription,
        ExpansionValue<String> ctdFgsFteFoeOreDescription,
        ExpansionValue<java.time.Instant> ctdFgsFteFoeOccurrenceAt,
        ExpansionValue<java.time.Instant> ctdFgsDTCreatedAt,
        ExpansionValue<String> invoicesVolumes,
        ExpansionValue<java.math.BigDecimal> realWeight,
        ExpansionValue<java.math.BigDecimal> taxedWeight,
        ExpansionValue<java.math.BigDecimal> invoicesValue,
        ExpansionValue<java.math.BigDecimal> ctdDiyCinTdaValue,
        ExpansionValue<String> ctdAntDocument,
        ExpansionValue<ConsolidacaoChargeDetails> chargedValueDetails)
        implements ExpansionObservation {
    public ConsolidacaoObservation {
        java.util.Objects.requireNonNull(ctdFgsCrnPsnNickname);
        java.util.Objects.requireNonNull(sequenceCode);
        java.util.Objects.requireNonNull(issueDate);
        java.util.Objects.requireNonNull(ctdRhrCpyNickname);
        java.util.Objects.requireNonNull(ctdRhrCpyDocument);
        java.util.Objects.requireNonNull(documentNumber);
        java.util.Objects.requireNonNull(ctdFgsFheCteNumber);
        java.util.Objects.requireNonNull(chargedValue);
        java.util.Objects.requireNonNull(subtotal);
        java.util.Objects.requireNonNull(ctdFgsSubtotal);
        java.util.Objects.requireNonNull(ctdFgsTotal);
        java.util.Objects.requireNonNull(ctdFgsTaxedWeight);
        java.util.Objects.requireNonNull(ctdFgsFteHasDeliveryReceipt);
        java.util.Objects.requireNonNull(billStatus);
        java.util.Objects.requireNonNull(ctdFgsServiceAt);
        java.util.Objects.requireNonNull(ctdFgsCorporationSequenceNumber);
        java.util.Objects.requireNonNull(ctdFgsInvoicesVolumes);
        java.util.Objects.requireNonNull(ctdFgsInvoicesMapping);
        java.util.Objects.requireNonNull(ctdFgsDosCtyName);
        java.util.Objects.requireNonNull(ctdSdrDocument);
        java.util.Objects.requireNonNull(ctdSdrNickname);
        java.util.Objects.requireNonNull(ctdFgsOasCtyName);
        java.util.Objects.requireNonNull(ctdRptDocument);
        java.util.Objects.requireNonNull(ctdRptNickname);
        java.util.Objects.requireNonNull(ctdRptChargeTde);
        java.util.Objects.requireNonNull(ctdFgsDpnDeliveryPredictionAt);
        java.util.Objects.requireNonNull(ctdFgsFinishedAt);
        java.util.Objects.requireNonNull(ctdFgsDpnFinishedWithinDeadline);
        java.util.Objects.requireNonNull(ctdFgsFteLceOreDescription);
        java.util.Objects.requireNonNull(ctdFgsFteFoeOreDescription);
        java.util.Objects.requireNonNull(ctdFgsFteFoeOccurrenceAt);
        java.util.Objects.requireNonNull(ctdFgsDTCreatedAt);
        java.util.Objects.requireNonNull(invoicesVolumes);
        java.util.Objects.requireNonNull(realWeight);
        java.util.Objects.requireNonNull(taxedWeight);
        java.util.Objects.requireNonNull(invoicesValue);
        java.util.Objects.requireNonNull(ctdDiyCinTdaValue);
        java.util.Objects.requireNonNull(ctdAntDocument);
        java.util.Objects.requireNonNull(chargedValueDetails);
    }

    @Override
    public String vertical() {
        return "CON";
    }

    @Override
    public boolean valid() {
        return ctdFgsCrnPsnNickname.valid()
                && sequenceCode.valid()
                && issueDate.valid()
                && ctdRhrCpyNickname.valid()
                && ctdRhrCpyDocument.valid()
                && documentNumber.valid()
                && ctdFgsFheCteNumber.valid()
                && chargedValue.valid()
                && subtotal.valid()
                && ctdFgsSubtotal.valid()
                && ctdFgsTotal.valid()
                && ctdFgsTaxedWeight.valid()
                && ctdFgsFteHasDeliveryReceipt.valid()
                && billStatus.valid()
                && ctdFgsServiceAt.valid()
                && ctdFgsCorporationSequenceNumber.valid()
                && ctdFgsInvoicesVolumes.valid()
                && ctdFgsInvoicesMapping.valid()
                && ctdFgsDosCtyName.valid()
                && ctdSdrDocument.valid()
                && ctdSdrNickname.valid()
                && ctdFgsOasCtyName.valid()
                && ctdRptDocument.valid()
                && ctdRptNickname.valid()
                && ctdRptChargeTde.valid()
                && ctdFgsDpnDeliveryPredictionAt.valid()
                && ctdFgsFinishedAt.valid()
                && ctdFgsDpnFinishedWithinDeadline.valid()
                && ctdFgsFteLceOreDescription.valid()
                && ctdFgsFteFoeOreDescription.valid()
                && ctdFgsFteFoeOccurrenceAt.valid()
                && ctdFgsDTCreatedAt.valid()
                && invoicesVolumes.valid()
                && realWeight.valid()
                && taxedWeight.valid()
                && invoicesValue.valid()
                && ctdDiyCinTdaValue.valid()
                && ctdAntDocument.valid()
                && chargedValueDetails.valid();
    }

    @Override
    public String toString() {
        return "ConsolidacaoObservation[redacted]";
    }
}
