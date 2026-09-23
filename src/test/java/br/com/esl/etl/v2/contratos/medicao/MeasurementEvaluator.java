package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;

/** Avalia somente contadores estruturais; heap e duração não entram nesta API. */
public final class MeasurementEvaluator {

    public MeasurementAssessment evaluate(final MeasurementEvidence evidence) {
        final MeasurementEvidence required =
                Objects.requireNonNull(evidence, "A evidência de medição é obrigatória.");
        if (required.maxRetainedPages() > 0L || required.finalRetainedPages() > 0L) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED);
        }
        if (required.finalInFlightPages() != 0L) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.FINAL_IN_FLIGHT_NOT_ZERO);
        }
        if (required.maxInFlightPages() != 1L) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.IN_FLIGHT_PEAK_NOT_EXACTLY_ONE);
        }
        if (required.acquisitions() != required.fetchedPages()) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.ACQUISITION_FETCH_MISMATCH);
        }
        if (required.releases() != required.fetchedPages()) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.RELEASE_FETCH_MISMATCH);
        }
        if (required.fetchedPages() != required.plan().expectedFetchedPages()) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.FETCHED_PAGE_COUNT_MISMATCH);
        }
        if (required.consumedPages() != required.plan().expectedConsumedPages()) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.CONSUMED_PAGE_COUNT_MISMATCH);
        }
        if (required.records() != required.plan().expectedRecords()) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.RECORD_COUNT_MISMATCH);
        }
        if (required.bytes() == 0L) {
            return MeasurementAssessment.rejected(
                    MeasurementAssessment.Reason.RESPONSE_BYTES_NOT_OBSERVED);
        }
        return MeasurementAssessment.provenSynthetically();
    }
}
