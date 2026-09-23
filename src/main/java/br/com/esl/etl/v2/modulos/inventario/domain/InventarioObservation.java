package br.com.esl.etl.v2.modulos.inventario.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed physical observation; none of these candidates supplies a synthetic root identity. */
public record InventarioObservation(
        ExpansionValue<String> cnrCSFitCorporationSequenceNumber,
        ExpansionValue<java.time.Instant> cnrCSFitDpnDeliveryPredictionAt,
        ExpansionValue<java.time.Instant> cnrCSFitDpnPerformanceFinishedAt,
        ExpansionValue<String> cnrCSFitDynDrtNickname,
        ExpansionValue<String> cnrCSFitDynName,
        ExpansionValue<java.time.Instant> cnrCSFitFteLceOccurrenceAt,
        ExpansionValue<String> cnrCSFitFteLceOreDescription,
        ExpansionValue<ExpansionStrings> cnrCSFitInvoicesMapping,
        ExpansionValue<java.math.BigDecimal> cnrCSFitInvoicesValue,
        ExpansionValue<String> cnrCSFitInvoicesVolumes,
        ExpansionValue<String> cnrCSFitPyrNickname,
        ExpansionValue<java.math.BigDecimal> cnrCSFitRealWeight,
        ExpansionValue<String> cnrCSFitRptAdsCtyName,
        ExpansionValue<String> cnrCSFitRptNickname,
        ExpansionValue<String> cnrCSFitSdrAdsCtyName,
        ExpansionValue<String> cnrCSFitSdrNickname,
        ExpansionValue<java.math.BigDecimal> cnrCSFitTaxedWeight,
        ExpansionValue<java.math.BigDecimal> cnrCSFitTotalCubicVolume,
        ExpansionValue<String> cnrCSReadVolumes,
        ExpansionValue<String> cnrCisEoePsnName,
        ExpansionValue<String> cnrCrnPsnNickname,
        ExpansionValue<java.time.Instant> finishedAt,
        ExpansionValue<String> sequenceCode,
        ExpansionValue<java.time.Instant> startedAt,
        ExpansionValue<String> status,
        ExpansionValue<String> type)
        implements ExpansionObservation {
    public InventarioObservation {
        java.util.Objects.requireNonNull(cnrCSFitCorporationSequenceNumber);
        java.util.Objects.requireNonNull(cnrCSFitDpnDeliveryPredictionAt);
        java.util.Objects.requireNonNull(cnrCSFitDpnPerformanceFinishedAt);
        java.util.Objects.requireNonNull(cnrCSFitDynDrtNickname);
        java.util.Objects.requireNonNull(cnrCSFitDynName);
        java.util.Objects.requireNonNull(cnrCSFitFteLceOccurrenceAt);
        java.util.Objects.requireNonNull(cnrCSFitFteLceOreDescription);
        java.util.Objects.requireNonNull(cnrCSFitInvoicesMapping);
        java.util.Objects.requireNonNull(cnrCSFitInvoicesValue);
        java.util.Objects.requireNonNull(cnrCSFitInvoicesVolumes);
        java.util.Objects.requireNonNull(cnrCSFitPyrNickname);
        java.util.Objects.requireNonNull(cnrCSFitRealWeight);
        java.util.Objects.requireNonNull(cnrCSFitRptAdsCtyName);
        java.util.Objects.requireNonNull(cnrCSFitRptNickname);
        java.util.Objects.requireNonNull(cnrCSFitSdrAdsCtyName);
        java.util.Objects.requireNonNull(cnrCSFitSdrNickname);
        java.util.Objects.requireNonNull(cnrCSFitTaxedWeight);
        java.util.Objects.requireNonNull(cnrCSFitTotalCubicVolume);
        java.util.Objects.requireNonNull(cnrCSReadVolumes);
        java.util.Objects.requireNonNull(cnrCisEoePsnName);
        java.util.Objects.requireNonNull(cnrCrnPsnNickname);
        java.util.Objects.requireNonNull(finishedAt);
        java.util.Objects.requireNonNull(sequenceCode);
        java.util.Objects.requireNonNull(startedAt);
        java.util.Objects.requireNonNull(status);
        java.util.Objects.requireNonNull(type);
    }

    @Override
    public String vertical() {
        return "INV";
    }

    @Override
    public boolean valid() {
        return cnrCSFitCorporationSequenceNumber.valid()
                && cnrCSFitDpnDeliveryPredictionAt.valid()
                && cnrCSFitDpnPerformanceFinishedAt.valid()
                && cnrCSFitDynDrtNickname.valid()
                && cnrCSFitDynName.valid()
                && cnrCSFitFteLceOccurrenceAt.valid()
                && cnrCSFitFteLceOreDescription.valid()
                && cnrCSFitInvoicesMapping.valid()
                && cnrCSFitInvoicesValue.valid()
                && cnrCSFitInvoicesVolumes.valid()
                && cnrCSFitPyrNickname.valid()
                && cnrCSFitRealWeight.valid()
                && cnrCSFitRptAdsCtyName.valid()
                && cnrCSFitRptNickname.valid()
                && cnrCSFitSdrAdsCtyName.valid()
                && cnrCSFitSdrNickname.valid()
                && cnrCSFitTaxedWeight.valid()
                && cnrCSFitTotalCubicVolume.valid()
                && cnrCSReadVolumes.valid()
                && cnrCisEoePsnName.valid()
                && cnrCrnPsnNickname.valid()
                && finishedAt.valid()
                && sequenceCode.valid()
                && startedAt.valid()
                && status.valid()
                && type.valid();
    }
}
