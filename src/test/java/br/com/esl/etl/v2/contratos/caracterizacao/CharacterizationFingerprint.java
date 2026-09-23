package br.com.esl.etl.v2.contratos.caracterizacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** SHA-256 de JSON canônico, independente da ordem de propriedades e mapas. */
public final class CharacterizationFingerprint {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CharacterizationFingerprint() {}

    public static String sha256(final Object value) {
        final JsonNode tree = JSON.valueToTree(value);
        return sha256(canonicalBytes(tree));
    }

    public static String sha256(final byte[] value) {
        return java.util.HexFormat.of().formatHex(digest().digest(value.clone()));
    }

    public static byte[] canonicalBytes(final JsonNode value) {
        try {
            return JSON.writeValueAsString(canonical(value)).getBytes(StandardCharsets.UTF_8);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("Não foi possível canonicalizar a evidência.");
        }
    }

    private static JsonNode canonical(final JsonNode value) {
        if (value.isObject()) {
            final Map<String, JsonNode> fields = new TreeMap<>();
            final Iterator<Map.Entry<String, JsonNode>> iterator = value.fields();
            while (iterator.hasNext()) {
                final Map.Entry<String, JsonNode> field = iterator.next();
                fields.put(field.getKey(), field.getValue());
            }
            final ObjectNode result = JSON.createObjectNode();
            fields.forEach((name, child) -> result.set(name, canonical(child)));
            return result;
        }
        if (value.isArray()) {
            final ArrayNode result = JSON.createArrayNode();
            for (final JsonNode child : value) {
                result.add(canonical(child));
            }
            return result;
        }
        return value.deepCopy();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
