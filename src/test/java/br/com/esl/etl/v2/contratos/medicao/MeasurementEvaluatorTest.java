package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MeasurementEvaluatorTest {

    private final MeasurementEvaluator evaluator = new MeasurementEvaluator();

    @Test
    void provesOnlyAnExactManagedInFlightPeakWithReconciledSyntheticEvidence() {
        final MeasurementAssessment assessment = evaluator.evaluate(healthyGraphQlEvidence());

        assertEquals(
                MeasurementAssessment.Disposition.PROVEN_SYNTHETICALLY, assessment.disposition());
        assertEquals(
                MeasurementAssessment.Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY,
                assessment.reason());
        assertTrue(assessment.proven());
    }

    @Test
    void rejectsADeadGaugeAndAPeakAboveOne() {
        assertEquals(
                MeasurementAssessment.Reason.IN_FLIGHT_PEAK_NOT_EXACTLY_ONE,
                evaluator.evaluate(withInFlightPeak(0L)).reason());
        assertEquals(
                MeasurementAssessment.Reason.IN_FLIGHT_PEAK_NOT_EXACTLY_ONE,
                evaluator.evaluate(withInFlightPeak(2L)).reason());
    }

    @Test
    void rejectsExecutionWideRetentionByMetricsWithoutSeeingTheMutantType() {
        final MeasurementEvidence retained =
                copyOf(healthyGraphQlEvidence(), 16L, 16L, 128L, 512L, 1L, 0L, 16L, 16L, 1L, 0L);

        final MeasurementAssessment assessment = evaluator.evaluate(retained);

        assertEquals(MeasurementAssessment.Disposition.REJECTED, assessment.disposition());
        assertEquals(
                MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
                assessment.reason());
        assertFalse(assessment.proven());
    }

    @Test
    void rejectsEveryStructuralCounterMismatchFailClosed() {
        final MeasurementEvidence healthy = healthyGraphQlEvidence();

        assertEquals(
                MeasurementAssessment.Reason.FINAL_IN_FLIGHT_NOT_ZERO,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 16L, 128L, 512L, 1L, 1L, 16L, 15L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.ACQUISITION_FETCH_MISMATCH,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 16L, 128L, 512L, 1L, 0L, 15L, 16L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.RELEASE_FETCH_MISMATCH,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 16L, 128L, 512L, 1L, 0L, 16L, 15L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.FETCHED_PAGE_COUNT_MISMATCH,
                evaluator
                        .evaluate(copyOf(healthy, 15L, 16L, 128L, 512L, 1L, 0L, 15L, 15L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.CONSUMED_PAGE_COUNT_MISMATCH,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 15L, 128L, 512L, 1L, 0L, 16L, 16L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.RECORD_COUNT_MISMATCH,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 16L, 127L, 512L, 1L, 0L, 16L, 16L, 0L, 0L))
                        .reason());
        assertEquals(
                MeasurementAssessment.Reason.RESPONSE_BYTES_NOT_OBSERVED,
                evaluator
                        .evaluate(copyOf(healthy, 16L, 16L, 128L, 0L, 1L, 0L, 16L, 16L, 0L, 0L))
                        .reason());
    }

    @Test
    void exposesNoEvaluatorEntryPointOrEvidenceFieldForJvmDiagnostics() {
        final Method[] evaluateMethods =
                Arrays.stream(MeasurementEvaluator.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("evaluate"))
                        .toArray(Method[]::new);
        assertEquals(1, evaluateMethods.length);
        assertArrayEquals(
                new Class<?>[] {MeasurementEvidence.class}, evaluateMethods[0].getParameterTypes());
        assertFalse(
                Arrays.stream(MeasurementEvaluator.class.getDeclaredFields())
                        .anyMatch(
                                field ->
                                        MeasurementDiagnostics.class.isAssignableFrom(
                                                field.getType())));
        assertFalse(
                Arrays.stream(MeasurementEvidence.class.getRecordComponents())
                        .map(RecordComponent::getType)
                        .anyMatch(MeasurementDiagnostics.class::isAssignableFrom));
    }

    @Test
    void rejectsNegativeOrInternallyImpossibleEvidenceAtTheBoundary() {
        final MeasurementPlan plan = MeasurementPlan.graphQl(16);
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementEvidence(plan, -1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementEvidence(plan, 1L, 1L, 8L, 32L, 0L, 1L, 1L, 0L, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementEvidence(plan, 1L, 1L, 8L, 32L, 1L, 0L, 1L, 1L, 0L, 1L));
    }

    private MeasurementEvidence withInFlightPeak(final long peak) {
        final MeasurementEvidence healthy = healthyGraphQlEvidence();
        return copyOf(healthy, 16L, 16L, 128L, 512L, peak, 0L, 16L, 16L, 0L, 0L);
    }

    private static MeasurementEvidence healthyGraphQlEvidence() {
        return new MeasurementEvidence(
                MeasurementPlan.graphQl(16), 16L, 16L, 128L, 512L, 1L, 0L, 16L, 16L, 0L, 0L);
    }

    private static MeasurementEvidence copyOf(
            final MeasurementEvidence source,
            final long fetchedPages,
            final long consumedPages,
            final long records,
            final long bytes,
            final long maxInFlightPages,
            final long finalInFlightPages,
            final long acquisitions,
            final long releases,
            final long maxRetainedPages,
            final long finalRetainedPages) {
        return new MeasurementEvidence(
                source.plan(),
                fetchedPages,
                consumedPages,
                records,
                bytes,
                maxInFlightPages,
                finalInFlightPages,
                acquisitions,
                releases,
                maxRetainedPages,
                finalRetainedPages);
    }
}
