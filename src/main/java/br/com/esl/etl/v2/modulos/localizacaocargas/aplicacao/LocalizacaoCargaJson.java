package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Canonicalização local de um registro limitado, sem envelope aberto de propriedades. */
final class LocalizacaoCargaJson {
    /**
     * Admite a contraprova cross-layer de 5.001 caracteres sem deixar um token numérico consumir
     * todo o limite de um registro. Valores tipados continuam limitados pelos parsers LOC-04.
     */
    static final int MAXIMUM_NUMERIC_TOKEN_CHARACTERS = 8_192;

    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNumberLength(MAXIMUM_NUMERIC_TOKEN_CHARACTERS)
                                    .build())
                    .build();

    static final ObjectMapper JSON =
            JsonMapper.builder(JSON_FACTORY)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                    .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .build();

    private LocalizacaoCargaJson() {}

    static String canonicalJson(final JsonNode node) {
        try {
            return JSON.writeValueAsString(canonical(node));
        } catch (final JsonProcessingException error) {
            throw new IllegalArgumentException("JSON 8656 inválido.", error);
        }
    }

    static JsonNode canonical(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode result = JSON.createObjectNode();
            node.fieldNames().forEachRemaining(name -> result.set(name, canonical(node.get(name))));
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
