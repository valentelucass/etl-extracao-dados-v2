package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteAttributePresence;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteSidecarEnvelope;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Preserva somente os dez paths transitórios aprovados, sem lookup ou crosswalk. */
public final class FreteGraphQlSidecarMapper {
    public FreteSidecarEnvelope map(final JsonNode response) {
        Objects.requireNonNull(response, "A resposta sidecar é obrigatória.");
        final JsonNode freight = response.path("freight");
        if (!freight.isObject()) {
            throw new IllegalArgumentException("O sidecar exige /freight objeto.");
        }
        final JsonNode edges = freight.get("edges");
        if (edges != null && !edges.isNull() && !edges.isArray()) {
            throw new IllegalArgumentException("/freight/edges deve ser array, NULL ou ausente.");
        }
        final int count = edges == null || edges.isNull() ? 0 : edges.size();
        if (count > FreteStageRecord.MAXIMUM_SIDECAR_EDGES) {
            throw new IllegalArgumentException("SIDECAR_PAGE_LIMIT_EXCEEDED");
        }

        final ObjectNode result = FreteJson.JSON.createObjectNode();
        result.put("catalogVersion", "fretes-graphql-sidecar-v1");
        result.put("relationPolicy", "PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK");
        result.put("edgesPresence", presence(freight, "edges").name());
        final ArrayNode sanitizedEdges = result.putArray("edges");
        if (edges != null && edges.isArray()) {
            for (int index = 0; index < edges.size(); index++) {
                final JsonNode edge = edges.get(index);
                if (!edge.isObject() || !edge.path("node").isObject()) {
                    throw new IllegalArgumentException("INVALID_SIDECAR_EDGE");
                }
                final JsonNode node = edge.path("node");
                final ObjectNode sanitized = sanitizedEdges.addObject();
                sanitized.put("ordinal", index + 1);
                evidence(sanitized, node, "id", "/freight/edges/node/id");
                evidence(
                        sanitized,
                        node,
                        "accountingCreditId",
                        "/freight/edges/node/accountingCreditId");
                evidence(
                        sanitized,
                        node,
                        "accountingCreditInstallmentId",
                        "/freight/edges/node/accountingCreditInstallmentId");
                evidence(sanitized, node, "referenceNumber", "/freight/edges/node/referenceNumber");
                final ObjectNode cte = sanitized.putObject("cte");
                final JsonNode rawCte = node.get("cte");
                if (rawCte != null && !rawCte.isNull() && !rawCte.isObject()) {
                    throw new IllegalArgumentException("INVALID_SIDECAR_CTE");
                }
                evidence(
                        cte,
                        rawCte == null || rawCte.isNull()
                                ? FreteJson.JSON.createObjectNode()
                                : rawCte,
                        "key",
                        "/freight/edges/node/cte/key");
                evidence(sanitized, node, "total", "/freight/edges/node/total");
                evidence(
                        sanitized,
                        node,
                        "corporationSequenceNumber",
                        "/freight/edges/node/corporationSequenceNumber");
                evidence(sanitized, node, "pickItemId", "/freight/edges/node/pickItemId");
                sanitized.put("pickItemResolution", "UNRESOLVED_CANDIDATE_ONLY");
            }
        }
        final ObjectNode pageInfo = result.putObject("pageInfo");
        final JsonNode rawPageInfo = freight.get("pageInfo");
        if (rawPageInfo != null && !rawPageInfo.isNull() && !rawPageInfo.isObject()) {
            throw new IllegalArgumentException("INVALID_SIDECAR_PAGE_INFO");
        }
        final JsonNode sourcePageInfo =
                rawPageInfo == null || rawPageInfo.isNull()
                        ? FreteJson.JSON.createObjectNode()
                        : rawPageInfo;
        pageInfo.put("presence", presence(freight, "pageInfo").name());
        evidence(pageInfo, sourcePageInfo, "hasNextPage", "/freight/pageInfo/hasNextPage");
        evidence(pageInfo, sourcePageInfo, "endCursor", "/freight/pageInfo/endCursor");
        return new FreteSidecarEnvelope(FreteJson.canonicalJson(result), count);
    }

    public static FreteSidecarEnvelope absent() {
        final ObjectNode result = FreteJson.JSON.createObjectNode();
        result.put("catalogVersion", "fretes-graphql-sidecar-v1");
        result.put("relationPolicy", "PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK");
        result.put("edgesPresence", "ABSENT");
        result.putArray("edges");
        final ObjectNode pageInfo = result.putObject("pageInfo");
        pageInfo.put("presence", "ABSENT");
        final ObjectNode empty = FreteJson.JSON.createObjectNode();
        evidence(pageInfo, empty, "hasNextPage", "/freight/pageInfo/hasNextPage");
        evidence(pageInfo, empty, "endCursor", "/freight/pageInfo/endCursor");
        return new FreteSidecarEnvelope(FreteJson.canonicalJson(result), 0);
    }

    private static void evidence(
            final ObjectNode target, final JsonNode owner, final String field, final String path) {
        final ObjectNode evidence = target.putObject(field);
        evidence.put("path", path);
        evidence.put("presence", presence(owner, field).name());
        final JsonNode value = owner.get(field);
        if (value != null && !value.isNull()) {
            evidence.set("raw", FreteJson.canonical(value));
            evidence.put("parseState", "PRESERVED_NO_INFERENCE");
        }
    }

    private static FreteAttributePresence presence(final JsonNode owner, final String field) {
        if (owner == null || !owner.has(field)) {
            return FreteAttributePresence.ABSENT;
        }
        final JsonNode value = owner.get(field);
        return value == null || value.isNull()
                ? FreteAttributePresence.NULL
                : FreteAttributePresence.VALUE;
    }
}
