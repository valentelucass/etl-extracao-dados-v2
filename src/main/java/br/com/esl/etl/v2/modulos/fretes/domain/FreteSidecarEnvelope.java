package br.com.esl.etl.v2.modulos.fretes.domain;

/** Sidecar GraphQL já sanitizado, independente da raiz Data Export e limitado por página. */
public record FreteSidecarEnvelope(String canonicalJson, int edgeCount) {
    public FreteSidecarEnvelope {
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("O sidecar canônico é obrigatório.");
        }
        if (edgeCount < 0 || edgeCount > FreteStageRecord.MAXIMUM_SIDECAR_EDGES) {
            throw new IllegalArgumentException("O sidecar excede uma página limitada.");
        }
    }
}
