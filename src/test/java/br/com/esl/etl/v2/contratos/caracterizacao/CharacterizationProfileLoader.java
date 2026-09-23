package br.com.esl.etl.v2.contratos.caracterizacao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** Faz binding fechado dos quatro perfis canônicos a partir de resources sintéticos. */
public final class CharacterizationProfileLoader {

    public static final long MAXIMUM_PROFILE_BYTES = 128L * 1_024L;

    private final StrictUtf8JsonLoader jsonLoader;
    private final ObjectMapper objectMapper;

    public CharacterizationProfileLoader() {
        this(new StrictUtf8JsonLoader());
    }

    CharacterizationProfileLoader(final StrictUtf8JsonLoader jsonLoader) {
        this.jsonLoader = Objects.requireNonNull(jsonLoader, "O loader UTF-8 é obrigatório.");
        this.objectMapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    public CharacterizationProfile loadResource(final String resourceName) {
        if (resourceName == null
                || !resourceName.matches(
                        "/contracts/v2-012/profiles/[a-z0-9-]+\\.profile\\.json")) {
            throw new IllegalArgumentException("O resource de perfil V2-012 é inválido.");
        }
        final JsonNode document =
                jsonLoader.loadResource(
                        CharacterizationProfileLoader.class, resourceName, MAXIMUM_PROFILE_BYTES);
        return bindProfile(document);
    }

    CharacterizationProfile bindProfile(final JsonNode document) {
        try {
            return objectMapper.treeToValue(
                    Objects.requireNonNull(document, "O documento de perfil é obrigatório."),
                    CharacterizationProfile.class);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "O perfil V2-012 não respeita o schema fechado.", exception);
        }
    }
}
