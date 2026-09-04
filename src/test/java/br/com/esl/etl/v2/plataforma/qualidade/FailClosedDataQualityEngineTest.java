package br.com.esl.etl.v2.plataforma.qualidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FailClosedDataQualityEngineTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000623");
    private static final DataQualityPolicyReference POLICY =
            new DataQualityPolicyReference("synthetic-dq-v1", "a".repeat(64));
    private static final Instant EVALUATED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void emitsAPermitOnlyForTheExactCompletePassedSummary() {
        final AtomicReference<DataQualityEvaluationRequest> observed = new AtomicReference<>();
        final FailClosedDataQualityEngine engine =
                new FailClosedDataQualityEngine(
                        request -> {
                            observed.set(request);
                            return passedSummary(request.executionId(), request.policyReference());
                        });
        final DataQualityEvaluationRequest request =
                new DataQualityEvaluationRequest(EXECUTION_ID, POLICY);

        final DataQualityPromotionPermit permit = engine.evaluate(request);

        assertSame(request, observed.get());
        assertEquals(EXECUTION_ID, permit.executionId());
        assertEquals(POLICY, permit.policyReference());
        assertEquals("b".repeat(64), permit.evaluationSha256());
        assertEquals(EVALUATED_AT, permit.evaluatedAt());
        assertFalse(permit.toString().contains(EXECUTION_ID.toString()));
        assertFalse(permit.toString().contains(POLICY.sha256()));
    }

    @Test
    void refusesNullFailedAndDivergentSummariesWithoutInventingSuccess() {
        final DataQualityEvaluationRequest request =
                new DataQualityEvaluationRequest(EXECUTION_ID, POLICY);
        final DataQualityRunSummary failed =
                new DataQualityRunSummary(
                        EXECUTION_ID,
                        POLICY,
                        "b".repeat(64),
                        4,
                        4,
                        3,
                        1,
                        4,
                        1,
                        DataQualityState.FAILED,
                        EVALUATED_AT);
        final DataQualityRunSummary divergent = passedSummary(UUID.randomUUID(), POLICY);

        assertReason("SUMMARY_ABSENT", new FailClosedDataQualityEngine(ignored -> null), request);
        assertReason(
                "QUALITY_GATE_FAILED", new FailClosedDataQualityEngine(ignored -> failed), request);
        assertReason(
                "SUMMARY_DIVERGENT",
                new FailClosedDataQualityEngine(ignored -> divergent),
                request);
    }

    @Test
    void convertsAnUnexpectedGatewayFailureToASanitizedSqlFailureAndPreservesCause() {
        final IllegalStateException cause = new IllegalStateException("synthetic-sensitive-detail");
        final FailClosedDataQualityEngine engine =
                new FailClosedDataQualityEngine(
                        ignored -> {
                            throw cause;
                        });

        final DataQualityEvaluationException failure =
                assertThrows(
                        DataQualityEvaluationException.class,
                        () ->
                                engine.evaluate(
                                        new DataQualityEvaluationRequest(EXECUTION_ID, POLICY)));

        assertSame(cause, failure.getCause());
        assertEquals(DataQualityFailureKind.UNAVAILABLE, failure.kind());
        assertEquals("SQL_UNAVAILABLE", failure.reasonCode());
        assertTrue(failure.getMessage().contains("SQL_UNAVAILABLE"));
        assertFalse(failure.getMessage().contains("synthetic-sensitive-detail"));
    }

    @Test
    void validatesPolicyRequestAndSummaryEquationsFailFast() {
        assertThrows(
                NullPointerException.class,
                () -> new DataQualityPolicyReference(null, "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataQualityPolicyReference(" invalid ", "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataQualityPolicyReference("valid-v1", "A".repeat(64)));
        assertThrows(
                NullPointerException.class, () -> new DataQualityEvaluationRequest(null, POLICY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataQualityRunSummary(
                                EXECUTION_ID,
                                POLICY,
                                "b".repeat(64),
                                4,
                                3,
                                3,
                                0,
                                3,
                                0,
                                DataQualityState.PASSED,
                                EVALUATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataQualityRunSummary(
                                EXECUTION_ID,
                                POLICY,
                                "b".repeat(64),
                                4,
                                4,
                                4,
                                0,
                                3,
                                4,
                                DataQualityState.PASSED,
                                EVALUATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataQualityRunSummary(
                                EXECUTION_ID,
                                POLICY,
                                "b".repeat(64),
                                4,
                                4,
                                4,
                                0,
                                4,
                                0,
                                DataQualityState.FAILED,
                                EVALUATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataQualityRunSummary(
                                EXECUTION_ID,
                                POLICY,
                                "b".repeat(64),
                                1,
                                1,
                                1,
                                0,
                                1,
                                0,
                                DataQualityState.PASSED,
                                EVALUATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataQualityEvaluationException("bad-code"));
    }

    @Test
    void preservesCooperativeCancellationInsteadOfReclassifyingIt() {
        final CancellationException cancellation =
                new CancellationException("synthetic-sensitive-detail");
        final FailClosedDataQualityEngine engine =
                new FailClosedDataQualityEngine(
                        ignored -> {
                            throw cancellation;
                        });

        final CancellationException observed =
                assertThrows(
                        CancellationException.class,
                        () ->
                                engine.evaluate(
                                        new DataQualityEvaluationRequest(EXECUTION_ID, POLICY)));

        assertSame(cancellation, observed);
    }

    @Test
    void safeSummariesDoNotRenderIdentifiersOrFingerprints() {
        final DataQualityRunSummary summary = passedSummary(EXECUTION_ID, POLICY);
        final DataQualityEvaluationRequest request =
                new DataQualityEvaluationRequest(EXECUTION_ID, POLICY);

        assertFalse(summary.toString().contains(EXECUTION_ID.toString()));
        assertFalse(summary.toString().contains(POLICY.sha256()));
        assertFalse(request.toString().contains(EXECUTION_ID.toString()));
        assertEquals("DataQualityPolicyReference[version=synthetic-dq-v1]", POLICY.toString());
    }

    private static DataQualityRunSummary passedSummary(
            final UUID executionId, final DataQualityPolicyReference policy) {
        return new DataQualityRunSummary(
                executionId,
                policy,
                "b".repeat(64),
                4,
                4,
                4,
                0,
                4,
                0,
                DataQualityState.PASSED,
                EVALUATED_AT);
    }

    private static void assertReason(
            final String reason,
            final FailClosedDataQualityEngine engine,
            final DataQualityEvaluationRequest request) {
        final DataQualityEvaluationException failure =
                assertThrows(DataQualityEvaluationException.class, () -> engine.evaluate(request));
        assertEquals(reason, failure.reasonCode());
        assertEquals(DataQualityFailureKind.DETERMINISTIC, failure.kind());
    }
}
