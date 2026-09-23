package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;

/** Parser tree restrito: recusa chaves duplicadas e documentos JSON concatenados. */
final class DataExportStrictJsonParser {

    private static final JsonFactory STRICT_FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(35)
                                    .maxNameLength(256)
                                    .maxNumberLength(1_000)
                                    .maxStringLength(20_000_000)
                                    .build())
                    .build();

    private static final JsonMapper STRICT_MAPPER =
            JsonMapper.builder(STRICT_FACTORY)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    // Preserve source decimal precision and scale before any domain conversion.
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                    .build();

    private DataExportStrictJsonParser() {}

    static JsonNode readTree(final String document) throws JsonProcessingException {
        return readTree(document, ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES);
    }

    static JsonNode readTree(final String document, final int maximumNodes)
            throws JsonProcessingException {
        if (maximumNodes < 1 || maximumNodes > ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES) {
            throw new IllegalArgumentException("O limite de nós JSON é inválido.");
        }
        verifyNodeBudget(document, maximumNodes);
        return STRICT_MAPPER.readTree(document);
    }

    private static void verifyNodeBudget(final String document, final int maximumNodes)
            throws JsonProcessingException {
        int nodes = 0;
        try (JsonParser parser = STRICT_MAPPER.createParser(document)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_OBJECT
                        || token == JsonToken.START_ARRAY
                        || token.isScalarValue()) {
                    nodes = Math.incrementExact(nodes);
                    if (nodes > maximumNodes) {
                        throw new JsonParseException(
                                parser, "A resposta excede o limite de nós JSON.");
                    }
                }
            }
        } catch (final JsonProcessingException exception) {
            throw exception;
        } catch (final IOException exception) {
            throw new JsonParseException(
                    null, "Falha de I/O ao validar a estrutura JSON.", exception);
        }
    }
}
