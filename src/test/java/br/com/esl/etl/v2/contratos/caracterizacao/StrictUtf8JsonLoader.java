package br.com.esl.etl.v2.contratos.caracterizacao;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Carrega JSON sintético com teto anterior ao parse e decodificação UTF-8 estrita. */
public final class StrictUtf8JsonLoader {

    private static final int BUFFER_SIZE = 8_192;
    private static final Pattern V2_012_RESOURCE =
            Pattern.compile(
                    "^/contracts/v2-012/(?:profiles/[a-z0-9-]+\\.profile|fixtures/[a-z0-9-]+\\.synthetic)\\.json$");
    public static final JsonStructureLimits DEFAULT_STRUCTURE_LIMITS =
            new JsonStructureLimits(64, 8_192, 32_768);
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final JsonDocumentParser parser;

    public StrictUtf8JsonLoader() {
        this(JSON::readTree);
    }

    StrictUtf8JsonLoader(final JsonDocumentParser parser) {
        this.parser = Objects.requireNonNull(parser, "O parser JSON é obrigatório.");
    }

    public JsonNode loadResource(
            final Class<?> anchor, final String resourceName, final long maximumBytes) {
        Objects.requireNonNull(anchor, "A âncora do recurso é obrigatória.");
        if (resourceName == null || !V2_012_RESOURCE.matcher(resourceName).matches()) {
            throw new IllegalArgumentException("O recurso V2-012 é inválido.");
        }
        try (InputStream input = anchor.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("O recurso sintético V2-012 não existe.");
            }
            return load(input, maximumBytes);
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível fechar o recurso sintético V2-012.");
        }
    }

    public JsonNode load(final byte[] bytes, final long maximumBytes) {
        return load(bytes, maximumBytes, DEFAULT_STRUCTURE_LIMITS);
    }

    public JsonNode load(
            final byte[] bytes,
            final long maximumBytes,
            final JsonStructureLimits structureLimits) {
        Objects.requireNonNull(bytes, "Os bytes JSON são obrigatórios.");
        validateMaximum(maximumBytes);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("O JSON sintético excede o limite de bytes.");
        }
        return parseStrict(bytes.clone(), structureLimits);
    }

    JsonNode load(final InputStream input, final long maximumBytes) {
        Objects.requireNonNull(input, "O stream JSON é obrigatório.");
        validateMaximum(maximumBytes);
        final byte[] bytes = readBounded(input, maximumBytes);
        return parseStrict(bytes, DEFAULT_STRUCTURE_LIMITS);
    }

    private JsonNode parseStrict(final byte[] bytes, final JsonStructureLimits structureLimits) {
        final String decoded;
        try {
            decoded =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("O JSON sintético não usa UTF-8 estrito.");
        }
        validateStructure(decoded, structureLimits);
        try {
            final JsonNode result = parser.parse(decoded);
            if (result == null || result.isMissingNode() || result.isNull()) {
                throw new IllegalArgumentException("O documento JSON sintético está vazio.");
            }
            return result;
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("O documento JSON sintético é inválido.");
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível ler o documento JSON sintético.");
        }
    }

    private static void validateStructure(
            final String document, final JsonStructureLimits structureLimits) {
        final JsonStructureLimits limits =
                Objects.requireNonNull(structureLimits, "Os limites estruturais são obrigatórios.");
        int depth = 0;
        int paths = 0;
        int nodes = 0;
        int roots = 0;
        try (JsonParser stream = JSON.getFactory().createParser(document)) {
            JsonToken token;
            while ((token = stream.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    paths++;
                    if (paths > limits.maximumPaths()) {
                        throw new IllegalArgumentException(
                                "O JSON sintético excede o limite de paths.");
                    }
                    continue;
                }
                if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                    continue;
                }
                nodes++;
                if (nodes > limits.maximumNodes()) {
                    throw new IllegalArgumentException("O JSON sintético excede o limite de nós.");
                }
                if (depth == 0) {
                    roots++;
                    if (roots > 1) {
                        throw new IllegalArgumentException(
                                "O JSON sintético contém documentos adicionais.");
                    }
                }
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    depth++;
                    if (depth > limits.maximumDepth()) {
                        throw new IllegalArgumentException(
                                "O JSON sintético excede o limite de profundidade.");
                    }
                }
            }
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("O documento JSON sintético é inválido.");
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível inspecionar o JSON sintético.");
        }
        if (roots != 1 || depth != 0) {
            throw new IllegalArgumentException(
                    "O documento JSON sintético está vazio ou incompleto.");
        }
    }

    private static byte[] readBounded(final InputStream input, final long maximumBytes) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[BUFFER_SIZE];
        long received = 0L;
        try {
            while (true) {
                final long remainingIncludingSentinel = maximumBytes - received + 1L;
                final int requested =
                        (int) Math.min((long) buffer.length, remainingIncludingSentinel);
                final int count = input.read(buffer, 0, requested);
                if (count < 0) {
                    return output.toByteArray();
                }
                if (count == 0) {
                    continue;
                }
                received += count;
                if (received > maximumBytes) {
                    throw new IllegalArgumentException(
                            "O JSON sintético excede o limite de bytes.");
                }
                output.write(buffer, 0, count);
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível ler o recurso sintético V2-012.");
        }
    }

    private static void validateMaximum(final long maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("O limite de bytes do JSON sintético é inválido.");
        }
    }

    @FunctionalInterface
    interface JsonDocumentParser {
        JsonNode parse(String document) throws IOException;
    }

    public record JsonStructureLimits(int maximumDepth, int maximumPaths, int maximumNodes) {

        public JsonStructureLimits {
            if (maximumDepth <= 0 || maximumPaths <= 0 || maximumNodes <= 0) {
                throw new IllegalArgumentException(
                        "Os limites estruturais do JSON devem ser positivos.");
            }
        }
    }
}
