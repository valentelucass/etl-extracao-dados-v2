package br.com.esl.etl.v2.modulos.sinistros.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed physical observation; none of these candidates supplies a synthetic root identity. */
public record SinistroObservation(
        ExpansionValue<String> boNumber,
        ExpansionValue<String> brokersCaseNumber,
        ExpansionValue<String> customerCommunicationContactName,
        ExpansionValue<java.time.LocalDate> customerCommunicationDate,
        ExpansionValue<String> customerCommunicationTime,
        ExpansionValue<java.math.BigDecimal> customerCreditEntriesSubtotal,
        ExpansionValue<java.math.BigDecimal> customerDebitsSubtotal,
        ExpansionValue<java.time.LocalDate> expectedSolutionDate,
        ExpansionValue<java.time.LocalDate> finishedAtDate,
        ExpansionValue<java.time.LocalTime> finishedAtTime,
        ExpansionValue<String> finishedCommentary,
        ExpansionValue<String> icmCrnPsnNickname,
        ExpansionValue<String> icmDvrIilName,
        ExpansionValue<String> icmFerName,
        ExpansionValue<String> icmFisFitCorporationSequenceNumber,
        ExpansionValue<String> icmFisFitPyrNickname,
        ExpansionValue<String> icmFisIoeNumber,
        ExpansionValue<String> icmTttDealingType,
        ExpansionValue<String> icmTttOreCode,
        ExpansionValue<String> icmTttOreDescription,
        ExpansionValue<String> icmTttSolutionType,
        ExpansionValue<java.time.Instant> icmTttTreatmentAt,
        ExpansionValue<String> icmVieLicensePlate,
        ExpansionValue<String> informedBy,
        ExpansionValue<String> insuranceClaimCommentary,
        ExpansionValue<String> insuranceClaimLocation,
        ExpansionValue<java.math.BigDecimal> insuranceClaimTotal,
        ExpansionValue<java.math.BigDecimal> insurerCreditsSubtotal,
        ExpansionValue<String> internalDescription,
        ExpansionValue<String> invoicesCount,
        ExpansionValue<java.math.BigDecimal> invoicesValue,
        ExpansionValue<String> invoicesVolumes,
        ExpansionValue<java.math.BigDecimal> invoicesWeight,
        ExpansionValue<String> listOfClaimedProducts,
        ExpansionValue<java.time.LocalDate> occurrenceAtDate,
        ExpansionValue<java.time.LocalTime> occurrenceAtTime,
        ExpansionValue<java.time.LocalDate> openingAtDate,
        ExpansionValue<String> policyNumber,
        ExpansionValue<ExpansionStrings> rcfdc,
        ExpansionValue<ExpansionStrings> rctac,
        ExpansionValue<ExpansionStrings> rctrc,
        ExpansionValue<java.math.BigDecimal> responsibleCreditsSubtotal,
        ExpansionValue<java.math.BigDecimal> responsibleDebitEntriesSubtotal,
        ExpansionValue<String> sequenceCode)
        implements ExpansionObservation {
    public SinistroObservation {
        java.util.Objects.requireNonNull(boNumber);
        java.util.Objects.requireNonNull(brokersCaseNumber);
        java.util.Objects.requireNonNull(customerCommunicationContactName);
        java.util.Objects.requireNonNull(customerCommunicationDate);
        java.util.Objects.requireNonNull(customerCommunicationTime);
        java.util.Objects.requireNonNull(customerCreditEntriesSubtotal);
        java.util.Objects.requireNonNull(customerDebitsSubtotal);
        java.util.Objects.requireNonNull(expectedSolutionDate);
        java.util.Objects.requireNonNull(finishedAtDate);
        java.util.Objects.requireNonNull(finishedAtTime);
        java.util.Objects.requireNonNull(finishedCommentary);
        java.util.Objects.requireNonNull(icmCrnPsnNickname);
        java.util.Objects.requireNonNull(icmDvrIilName);
        java.util.Objects.requireNonNull(icmFerName);
        java.util.Objects.requireNonNull(icmFisFitCorporationSequenceNumber);
        java.util.Objects.requireNonNull(icmFisFitPyrNickname);
        java.util.Objects.requireNonNull(icmFisIoeNumber);
        java.util.Objects.requireNonNull(icmTttDealingType);
        java.util.Objects.requireNonNull(icmTttOreCode);
        java.util.Objects.requireNonNull(icmTttOreDescription);
        java.util.Objects.requireNonNull(icmTttSolutionType);
        java.util.Objects.requireNonNull(icmTttTreatmentAt);
        java.util.Objects.requireNonNull(icmVieLicensePlate);
        java.util.Objects.requireNonNull(informedBy);
        java.util.Objects.requireNonNull(insuranceClaimCommentary);
        java.util.Objects.requireNonNull(insuranceClaimLocation);
        java.util.Objects.requireNonNull(insuranceClaimTotal);
        java.util.Objects.requireNonNull(insurerCreditsSubtotal);
        java.util.Objects.requireNonNull(internalDescription);
        java.util.Objects.requireNonNull(invoicesCount);
        java.util.Objects.requireNonNull(invoicesValue);
        java.util.Objects.requireNonNull(invoicesVolumes);
        java.util.Objects.requireNonNull(invoicesWeight);
        java.util.Objects.requireNonNull(listOfClaimedProducts);
        java.util.Objects.requireNonNull(occurrenceAtDate);
        java.util.Objects.requireNonNull(occurrenceAtTime);
        java.util.Objects.requireNonNull(openingAtDate);
        java.util.Objects.requireNonNull(policyNumber);
        java.util.Objects.requireNonNull(rcfdc);
        java.util.Objects.requireNonNull(rctac);
        java.util.Objects.requireNonNull(rctrc);
        java.util.Objects.requireNonNull(responsibleCreditsSubtotal);
        java.util.Objects.requireNonNull(responsibleDebitEntriesSubtotal);
        java.util.Objects.requireNonNull(sequenceCode);
    }

    @Override
    public String vertical() {
        return "SIN";
    }

    @Override
    public boolean valid() {
        return boNumber.valid()
                && brokersCaseNumber.valid()
                && customerCommunicationContactName.valid()
                && customerCommunicationDate.valid()
                && customerCommunicationTime.valid()
                && customerCreditEntriesSubtotal.valid()
                && customerDebitsSubtotal.valid()
                && expectedSolutionDate.valid()
                && finishedAtDate.valid()
                && finishedAtTime.valid()
                && finishedCommentary.valid()
                && icmCrnPsnNickname.valid()
                && icmDvrIilName.valid()
                && icmFerName.valid()
                && icmFisFitCorporationSequenceNumber.valid()
                && icmFisFitPyrNickname.valid()
                && icmFisIoeNumber.valid()
                && icmTttDealingType.valid()
                && icmTttOreCode.valid()
                && icmTttOreDescription.valid()
                && icmTttSolutionType.valid()
                && icmTttTreatmentAt.valid()
                && icmVieLicensePlate.valid()
                && informedBy.valid()
                && insuranceClaimCommentary.valid()
                && insuranceClaimLocation.valid()
                && insuranceClaimTotal.valid()
                && insurerCreditsSubtotal.valid()
                && internalDescription.valid()
                && invoicesCount.valid()
                && invoicesValue.valid()
                && invoicesVolumes.valid()
                && invoicesWeight.valid()
                && listOfClaimedProducts.valid()
                && occurrenceAtDate.valid()
                && occurrenceAtTime.valid()
                && openingAtDate.valid()
                && policyNumber.valid()
                && rcfdc.valid()
                && rctac.valid()
                && rctrc.valid()
                && responsibleCreditsSubtotal.valid()
                && responsibleDebitEntriesSubtotal.valid()
                && sequenceCode.valid();
    }
}
