package br.com.esl.etl.v2.plataforma.fonte.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Serializa somente documentos e variáveis tipadas do catálogo fechado. */
final class GraphQlRequestJsonSerializer {

    private final ObjectMapper objectMapper;

    GraphQlRequestJsonSerializer(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper é obrigatório.");
    }

    String serialize(final GraphQlPageRequest request) {
        Objects.requireNonNull(request, "A requisição GraphQL é obrigatória.");
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("operationName", request.operation().operationName());
        root.put("query", request.operation().documentText());
        final ObjectNode variables = root.putObject("variables");
        final ObjectNode parameters = variables.putObject("params");
        request.parameters().writeTo(parameters);
        request.after()
                .ifPresentOrElse(
                        cursor -> variables.put("after", cursor.value()),
                        () -> variables.putNull("after"));
        variables.put("first", request.pageSize());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar a requisição GraphQL.");
        }
    }
}
