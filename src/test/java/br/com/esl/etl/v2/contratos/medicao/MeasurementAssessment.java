package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;

/** Resultado fechado e fail-closed da avaliação puramente estrutural. */
public record MeasurementAssessment(Disposition disposition, Reason reason) {

    public MeasurementAssessment {
        Objects.requireNonNull(disposition, "A disposição da medição é obrigatória.");
        Objects.requireNonNull(reason, "A razão da medição é obrigatória.");
        final boolean positiveReason =
                reason == Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY;
        if (positiveReason != (disposition == Disposition.PROVEN_SYNTHETICALLY)) {
            throw new IllegalArgumentException("A disposição diverge da razão da medição.");
        }
    }

    public boolean proven() {
        return disposition == Disposition.PROVEN_SYNTHETICALLY;
    }

    static MeasurementAssessment provenSynthetically() {
        return new MeasurementAssessment(
                Disposition.PROVEN_SYNTHETICALLY,
                Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY);
    }

    static MeasurementAssessment rejected(final Reason reason) {
        return new MeasurementAssessment(Disposition.REJECTED, reason);
    }

    public enum Disposition {
        PROVEN_SYNTHETICALLY,
        REJECTED
    }

    public enum Reason {
        EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
        FINAL_IN_FLIGHT_NOT_ZERO,
        IN_FLIGHT_PEAK_NOT_EXACTLY_ONE,
        ACQUISITION_FETCH_MISMATCH,
        RELEASE_FETCH_MISMATCH,
        FETCHED_PAGE_COUNT_MISMATCH,
        CONSUMED_PAGE_COUNT_MISMATCH,
        RECORD_COUNT_MISMATCH,
        RESPONSE_BYTES_NOT_OBSERVED,
        MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY
    }
}
