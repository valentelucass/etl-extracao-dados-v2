package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Loader de JSON fechado, limitado, sem BOM e com detecção de chaves duplicadas. */
public final class Qfnd02Loader {

    private static final int MAXIMUM_BYTES = 262_144;
    private final ObjectMapper mapper =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .build())
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public Qfnd02Profile loadProfileResource(final String resource) {
        return readResource(resource, Qfnd02Profile.class);
    }

    public Qfnd02Observation loadFixtureResource(final String resource) {
        return readResource(resource, Qfnd02Observation.class);
    }

    public Qfnd02Profile parseProfile(final String json) {
        return parseProfile(json.getBytes(StandardCharsets.UTF_8));
    }

    public Qfnd02Profile parseProfile(final byte[] bytes) {
        return read(bytes, Qfnd02Profile.class);
    }

    public Qfnd02Observation parseFixture(final byte[] bytes) {
        return read(bytes, Qfnd02Observation.class);
    }

    private <T> T readResource(final String resource, final Class<T> type) {
        try (InputStream stream = Qfnd02Loader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Recurso Q-FND-02 ausente.");
            }
            return read(stream.readNBytes(MAXIMUM_BYTES + 1), type);
        } catch (final IOException exception) {
            throw new IllegalArgumentException("Falha ao ler recurso Q-FND-02.", exception);
        }
    }

    private <T> T read(final byte[] bytes, final Class<T> type) {
        if (bytes.length == 0 || bytes.length > MAXIMUM_BYTES || hasBom(bytes)) {
            throw new IllegalArgumentException("JSON Q-FND-02 vazio, excessivo ou com BOM.");
        }
        validateUtf8(bytes);
        try {
            return mapper.readValue(bytes, type);
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalArgumentException("JSON Q-FND-02 inválido.", exception);
        }
    }

    private static boolean hasBom(final byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }

    private static void validateUtf8(final byte[] bytes) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("JSON Q-FND-02 não é UTF-8 estrito.", exception);
        }
    }
}
