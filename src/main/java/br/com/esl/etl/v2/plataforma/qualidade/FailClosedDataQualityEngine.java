package br.com.esl.etl.v2.plataforma.qualidade;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Orquestra uma única chamada set-based e recusa ausência, shape divergente, parcialidade e FAIL.
 */
public final class FailClosedDataQualityEngine {

    private final DataQualityGateway gateway;

    public FailClosedDataQualityEngine(final DataQualityGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "O gateway de Data Quality é obrigatório.");
    }

    public DataQualityPromotionPermit evaluate(final DataQualityEvaluationRequest request) {
        final DataQualityEvaluationRequest required =
                Objects.requireNonNull(request, "O pedido de Data Quality é obrigatório.");
        final DataQualityRunSummary summary;
        try {
            summary = gateway.evaluatePlatformIntegrity(required);
        } catch (final CancellationException exception) {
            throw exception;
        } catch (final DataQualityEvaluationException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            throw new DataQualityEvaluationException(
                    "SQL_UNAVAILABLE", DataQualityFailureKind.UNAVAILABLE, exception);
        }
        if (summary == null) {
            throw new DataQualityEvaluationException("SUMMARY_ABSENT");
        }
        try {
            summary.requireExactSuccessfulMatch(required);
        } catch (final IllegalArgumentException exception) {
            throw new DataQualityEvaluationException("SUMMARY_DIVERGENT", exception);
        } catch (final IllegalStateException exception) {
            throw new DataQualityEvaluationException("QUALITY_GATE_FAILED", exception);
        }
        return new DataQualityPromotionPermit(summary);
    }
}
