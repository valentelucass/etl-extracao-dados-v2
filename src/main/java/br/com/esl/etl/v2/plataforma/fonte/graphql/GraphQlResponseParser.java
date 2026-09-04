package br.com.esl.etl.v2.plataforma.fonte.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Valida o envelope Relay exato e produz somente uma página contratualmente observada. */
final class GraphQlResponseParser {

    private final GraphQlContractObservationConfiguration configuration;
    private final GraphQlContractAdapter contractAdapter;

    GraphQlResponseParser(final GraphQlContractObservationConfiguration configuration) {
        this.configuration =
                Objects.requireNonNull(configuration, "A configuração GraphQL é obrigatória.");
        contractAdapter =
                new GraphQlContractAdapter(
                        configuration.observationLimits(), configuration.responsePathBoundary());
    }

    GraphQlPageResponse parse(final String document, final GraphQlPageRequest request) {
        Objects.requireNonNull(document, "A resposta GraphQL é obrigatória.");
        Objects.requireNonNull(request, "A requisição GraphQL é obrigatória.");
        if (request.operation() != configuration.operation()) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_CONNECTION);
        }
        final JsonNode root;
        try {
            root =
                    GraphQlStrictJsonParser.readTree(
                            document, configuration.observationLimits().maximumNodes());
        } catch (final JsonProcessingException | ArithmeticException exception) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_JSON);
        }
        if (root == null || !root.isObject()) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_ENVELOPE);
        }
        if (root.has("errors")) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.GRAPHQL_ERRORS);
        }
        final JsonNode data = onlyRequiredObject(root, "data", ReasonKind.ENVELOPE);
        final JsonNode connection =
                onlyRequiredObject(
                        data, configuration.operation().connectionName(), ReasonKind.CONNECTION);
        if (connection.size() != 2 || !connection.has("edges") || !connection.has("pageInfo")) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_CONNECTION);
        }
        final JsonNode rawEdges = connection.get("edges");
        if (!rawEdges.isArray()
                || rawEdges.size() > request.pageSize()
                || rawEdges.size() > request.operation().maximumPageSize()) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_EDGE);
        }
        final ArrayNode nodes =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (final JsonNode edge : rawEdges) {
            if (!edge.isObject() || edge.size() != 1 || !edge.has("node")) {
                throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_EDGE);
            }
            final JsonNode node = edge.get("node");
            if (node == null || !node.isObject() || node.isEmpty()) {
                throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_NODE);
            }
            final JsonNode id = node.get("id");
            final boolean approvedIdShape =
                    id != null
                            && (id.isIntegralNumber()
                                    || id.isTextual() && !id.textValue().isBlank());
            if (!approvedIdShape) {
                throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_NODE);
            }
            nodes.add(node);
        }
        final JsonNode pageInfo = connection.get("pageInfo");
        if (!pageInfo.isObject()
                || pageInfo.size() != 2
                || !pageInfo.has("hasNextPage")
                || !pageInfo.get("hasNextPage").isBoolean()
                || !pageInfo.has("endCursor")) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_PAGE_INFO);
        }
        final boolean hasNextPage = pageInfo.get("hasNextPage").booleanValue();
        final JsonNode rawCursor = pageInfo.get("endCursor");
        final Optional<GraphQlCursor> cursor;
        if (rawCursor.isNull()) {
            cursor = Optional.empty();
        } else if (rawCursor.isTextual()) {
            try {
                cursor = Optional.of(GraphQlCursor.observed(rawCursor.textValue()));
            } catch (final IllegalArgumentException exception) {
                throw new GraphQlResponseException(
                        GraphQlResponseException.Reason.INVALID_PAGE_INFO);
            }
        } else {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_PAGE_INFO);
        }
        if (hasNextPage && cursor.isEmpty()) {
            throw new GraphQlResponseException(GraphQlResponseException.Reason.INVALID_PAGE_INFO);
        }
        final GraphQlPageResponse page = new GraphQlPageResponse(nodes, hasNextPage, cursor);
        return page.withResponseBytes(document.getBytes(StandardCharsets.UTF_8).length)
                .withObservation(
                        contractAdapter.response(configuration.operation(), page),
                        contractAdapter.limits(),
                        configuration.responsePathBoundary().fingerprint());
    }

    private static JsonNode onlyRequiredObject(
            final JsonNode parent, final String field, final ReasonKind reasonKind) {
        final Iterator<Map.Entry<String, JsonNode>> fields = parent.fields();
        if (!fields.hasNext()) {
            throw reasonKind.exception();
        }
        final Map.Entry<String, JsonNode> only = fields.next();
        if (fields.hasNext()
                || !only.getKey().equals(field)
                || only.getValue() == null
                || !only.getValue().isObject()) {
            throw reasonKind.exception();
        }
        return only.getValue();
    }

    private enum ReasonKind {
        ENVELOPE(GraphQlResponseException.Reason.INVALID_ENVELOPE),
        CONNECTION(GraphQlResponseException.Reason.INVALID_CONNECTION);

        private final GraphQlResponseException.Reason reason;

        ReasonKind(final GraphQlResponseException.Reason reason) {
            this.reason = reason;
        }

        private GraphQlResponseException exception() {
            return new GraphQlResponseException(reason);
        }
    }
}
