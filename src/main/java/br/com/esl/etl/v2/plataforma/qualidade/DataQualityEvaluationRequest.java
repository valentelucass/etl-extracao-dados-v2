package br.com.esl.etl.v2.plataforma.qualidade;

import java.util.Objects;
import java.util.UUID;

/** Pedido escalar para uma procedure SQL set-based. */
public record DataQualityEvaluationRequest(
        UUID executionId, DataQualityPolicyReference policyReference) {

    public DataQualityEvaluationRequest {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        policyReference =
                Objects.requireNonNull(policyReference, "A referência da policy é obrigatória.");
    }

    @Override
    public String toString() {
        return "DataQualityEvaluationRequest[policy=" + policyReference.version() + "]";
    }
}
