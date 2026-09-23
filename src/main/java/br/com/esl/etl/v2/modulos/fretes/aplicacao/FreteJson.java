package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Canonicalização local de um único registro/página limitada. */
final class FreteJson {
    static final ObjectMapper JSON = new ObjectMapper();

    private FreteJson() {}

    static String canonicalJson(final JsonNode node) {
        try {
            return JSON.writeValueAsString(canonical(node));
        } catch (final JsonProcessingException error) {
            throw new IllegalArgumentException("JSON 6389 inválido.", error);
        }
    }

    static JsonNode canonical(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode result = JSON.createObjectNode();
            final Map<String, JsonNode> fields = new TreeMap<>();
            final Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                final Map.Entry<String, JsonNode> field = iterator.next();
                fields.put(field.getKey(), field.getValue());
            }
            fields.forEach((name, value) -> result.set(name, canonical(value)));
            return result;
        }
        if (node.isArray()) {
            final ArrayNode result = JSON.createArrayNode();
            for (final JsonNode item : node) {
                result.add(canonical(item));
            }
            return result;
        }
        return node.deepCopy();
    }
}
