package br.com.esl.etl.v2.modulos.ocorrencias.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Physical source observation. No business identifier is asserted to be unique. */
public record OcorrenciaObservation(
        ExpansionValue<String> iceFEFitCorporationSequenceNumber,
        ExpansionValue<String> iceFEIoeNumber,
        ExpansionValue<String> iceFitFheCteNumber,
        ExpansionValue<String> iceOreCode,
        ExpansionValue<String> iceOreDescription,
        ExpansionValue<java.time.Instant> occurrenceAt,
        ExpansionValue<Boolean> onMobile,
        ExpansionValue<String> iceUerName,
        ExpansionValue<java.time.Instant> iceFitServiceAt,
        ExpansionValue<java.time.Instant> iceFitDpnDeliveryPredictionAt,
        ExpansionValue<java.time.Instant> occurrenceScheduleAt,
        ExpansionValue<java.time.Instant> iceFitDpnPerformanceFinishedAt,
        ExpansionValue<String> deliveryStatus,
        ExpansionValue<String> iceFitFlnClnNickname,
        ExpansionValue<Boolean> iceFitDpnManualSchedule,
        ExpansionValue<java.time.Instant> iceTttSolutionDeadline,
        ExpansionValue<java.time.Instant> iceTttSolutionAt,
        ExpansionValue<String> iceTttSolutionType,
        ExpansionValue<String> iceTttSoeDescription,
        ExpansionValue<String> iceTttSolutionComments)
        implements ExpansionObservation {
    public OcorrenciaObservation {
        java.util.Objects.requireNonNull(iceFEFitCorporationSequenceNumber);
        java.util.Objects.requireNonNull(iceFEIoeNumber);
        java.util.Objects.requireNonNull(iceFitFheCteNumber);
        java.util.Objects.requireNonNull(iceOreCode);
        java.util.Objects.requireNonNull(iceOreDescription);
        java.util.Objects.requireNonNull(occurrenceAt);
        java.util.Objects.requireNonNull(onMobile);
        java.util.Objects.requireNonNull(iceUerName);
        java.util.Objects.requireNonNull(iceFitServiceAt);
        java.util.Objects.requireNonNull(iceFitDpnDeliveryPredictionAt);
        java.util.Objects.requireNonNull(occurrenceScheduleAt);
        java.util.Objects.requireNonNull(iceFitDpnPerformanceFinishedAt);
        java.util.Objects.requireNonNull(deliveryStatus);
        java.util.Objects.requireNonNull(iceFitFlnClnNickname);
        java.util.Objects.requireNonNull(iceFitDpnManualSchedule);
        java.util.Objects.requireNonNull(iceTttSolutionDeadline);
        java.util.Objects.requireNonNull(iceTttSolutionAt);
        java.util.Objects.requireNonNull(iceTttSolutionType);
        java.util.Objects.requireNonNull(iceTttSoeDescription);
        java.util.Objects.requireNonNull(iceTttSolutionComments);
    }

    @Override
    public String vertical() {
        return "OCO";
    }

    @Override
    public boolean valid() {
        return iceFEFitCorporationSequenceNumber.valid()
                && iceFEIoeNumber.valid()
                && iceFitFheCteNumber.valid()
                && iceOreCode.valid()
                && iceOreDescription.valid()
                && occurrenceAt.valid()
                && onMobile.valid()
                && iceUerName.valid()
                && iceFitServiceAt.valid()
                && iceFitDpnDeliveryPredictionAt.valid()
                && occurrenceScheduleAt.valid()
                && iceFitDpnPerformanceFinishedAt.valid()
                && deliveryStatus.valid()
                && iceFitFlnClnNickname.valid()
                && iceFitDpnManualSchedule.valid()
                && iceTttSolutionDeadline.valid()
                && iceTttSolutionAt.valid()
                && iceTttSolutionType.valid()
                && iceTttSoeDescription.valid()
                && iceTttSolutionComments.valid();
    }

    @Override
    public String toString() {
        return "OcorrenciaObservation[redacted]";
    }
}
