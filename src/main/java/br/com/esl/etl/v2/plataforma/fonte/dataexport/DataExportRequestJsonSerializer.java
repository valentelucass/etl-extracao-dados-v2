package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/** Serializa filtros por caminho, incluindo raízes irmãs como {@code search.scopes}. */
public final class DataExportRequestJsonSerializer {

    private final ObjectMapper objectMapper;

    public DataExportRequestJsonSerializer(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper é obrigatório.");
    }

    public ObjectNode serialize(final DataExportPageRequest request, final ZoneId sourceZone) {
        Objects.requireNonNull(request, "A requisição é obrigatória.");
        Objects.requireNonNull(sourceZone, "O timezone da fonte é obrigatório.");

        final ObjectNode payload = objectMapper.createObjectNode();
        final ObjectNode search = objectMapper.createObjectNode();
        for (final Map.Entry<SearchPath, DataExportFilterValue> entry :
                request.filters().entrySet()) {
            final SearchPath path = entry.getKey();
            final ObjectNode root = getOrCreateObject(search, path.root());
            root.put(path.field(), entry.getValue().formatForSource(sourceZone));
        }
        payload.set("search", search);
        payload.put("page", String.valueOf(request.page()));
        payload.put("per", String.valueOf(request.pageSize()));
        if (!request.orderBy().isEmpty()) {
            payload.put("order_by", String.join(", ", request.orderBy()));
        }
        return payload;
    }

    public String serializeToString(final DataExportPageRequest request, final ZoneId sourceZone) {
        try {
            return objectMapper.writeValueAsString(serialize(request, sourceZone));
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Não foi possível serializar a requisição Data Export.", exception);
        }
    }

    private ObjectNode getOrCreateObject(final ObjectNode parent, final String name) {
        final JsonNode existing = parent.get(name);
        if (existing == null) {
            final ObjectNode created = objectMapper.createObjectNode();
            parent.set(name, created);
            return created;
        }
        if (existing instanceof final ObjectNode objectNode) {
            return objectNode;
        }
        throw new IllegalStateException(
                "O caminho Data Export conflita com um valor escalar: " + name);
    }
}
