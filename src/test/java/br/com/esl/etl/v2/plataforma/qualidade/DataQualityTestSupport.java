package br.com.esl.etl.v2.plataforma.qualidade;

import java.time.Instant;
import java.util.UUID;

public final class DataQualityTestSupport {

    private static final DataQualityPolicyReference POLICY =
            new DataQualityPolicyReference("synthetic-dq-v1", "f".repeat(64));

    private DataQualityTestSupport() {}

    public static DataQualityPromotionPermit promotionPermit(final UUID executionId) {
        final DataQualityEvaluationRequest request =
                new DataQualityEvaluationRequest(executionId, POLICY);
        return new FailClosedDataQualityEngine(
                        ignored ->
                                new DataQualityRunSummary(
                                        executionId,
                                        POLICY,
                                        "e".repeat(64),
                                        4,
                                        4,
                                        4,
                                        0,
                                        4,
                                        0,
                                        DataQualityState.PASSED,
                                        Instant.parse("2026-08-31T00:00:00Z")))
                .evaluate(request);
    }
}
