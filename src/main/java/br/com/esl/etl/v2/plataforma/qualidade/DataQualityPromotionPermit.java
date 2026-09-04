package br.com.esl.etl.v2.plataforma.qualidade;

import java.time.Instant;
import java.util.UUID;

/** Permit imutável emitido somente depois de uma avaliação completa e aprovada. */
public final class DataQualityPromotionPermit {

    private final UUID executionId;
    private final DataQualityPolicyReference policyReference;
    private final String evaluationSha256;
    private final Instant evaluatedAt;

    DataQualityPromotionPermit(final DataQualityRunSummary summary) {
        executionId = summary.executionId();
        policyReference = summary.policyReference();
        evaluationSha256 = summary.evaluationSha256();
        evaluatedAt = summary.evaluatedAt();
    }

    public UUID executionId() {
        return executionId;
    }

    public DataQualityPolicyReference policyReference() {
        return policyReference;
    }

    public String evaluationSha256() {
        return evaluationSha256;
    }

    public Instant evaluatedAt() {
        return evaluatedAt;
    }

    @Override
    public String toString() {
        return "DataQualityPromotionPermit[policy=" + policyReference.version() + "]";
    }
}
