package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ScenarioOutcome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolve uma base sanitizada e overrides pequenos antes de criar observações imutáveis. */
public final class CharacterizationFixtureLoader {

    public static final long MAXIMUM_FIXTURE_BYTES = 256L * 1_024L;

    private final StrictUtf8JsonLoader jsonLoader;
    private final ObjectMapper objectMapper;

    public CharacterizationFixtureLoader() {
        this(new StrictUtf8JsonLoader());
    }

    CharacterizationFixtureLoader(final StrictUtf8JsonLoader jsonLoader) {
        this.jsonLoader = Objects.requireNonNull(jsonLoader, "O loader UTF-8 é obrigatório.");
        this.objectMapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    public CharacterizationFixture loadResource(final String resourceName) {
        if (resourceName == null
                || !resourceName.matches(
                        "/contracts/v2-012/fixtures/[a-z0-9-]+\\.synthetic\\.json")) {
            throw new IllegalArgumentException("O resource de fixture V2-012 é inválido.");
        }
        final JsonNode document =
                jsonLoader.loadResource(
                        CharacterizationFixtureLoader.class, resourceName, MAXIMUM_FIXTURE_BYTES);
        final RawFixture raw;
        try {
            raw = objectMapper.treeToValue(document, RawFixture.class);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "A fixture V2-012 não respeita o schema fechado.", exception);
        }
        if (raw.baseObservation() == null || !raw.baseObservation().isObject()) {
            throw new IllegalArgumentException("A fixture não possui observação-base fechada.");
        }
        final List<CharacterizationFixture.Scenario> scenarios = new ArrayList<>();
        for (final RawScenario scenario :
                Objects.requireNonNull(
                        raw.scenarios(), "Os cenários da fixture são obrigatórios.")) {
            if (scenario == null
                    || scenario.override() == null
                    || !scenario.override().isObject()) {
                throw new IllegalArgumentException("Um override de cenário é inválido.");
            }
            final JsonNode merged = merge(raw.baseObservation(), scenario.override());
            scenarios.add(
                    new CharacterizationFixture.Scenario(
                            scenario.scenarioId(),
                            scenario.expectedOutcome(),
                            scenario.expectedReasons(),
                            bindObservation(merged)));
        }
        return new CharacterizationFixture(
                raw.schemaVersion(), raw.fixtureMarker(), raw.profileId(), scenarios);
    }

    CharacterizationObservation bindObservation(final JsonNode document) {
        try {
            return objectMapper.treeToValue(
                    Objects.requireNonNull(document, "A observação sintética é obrigatória."),
                    CharacterizationObservation.class);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Uma observação sintética não respeita o schema fechado.", exception);
        }
    }

    private static JsonNode merge(final JsonNode base, final JsonNode override) {
        final ObjectNode merged = base.deepCopy();
        override.fields()
                .forEachRemaining(
                        field -> {
                            final JsonNode current = merged.get(field.getKey());
                            final JsonNode replacement = field.getValue();
                            if ("fieldObservations".equals(field.getKey())
                                    && current != null
                                    && current.isArray()
                                    && replacement.isObject()) {
                                merged.set(
                                        field.getKey(),
                                        mergeFieldObservationOverrides(current, replacement));
                            } else if ("childObservations".equals(field.getKey())
                                    && current != null
                                    && current.isArray()
                                    && replacement.isObject()) {
                                merged.set(
                                        field.getKey(),
                                        mergeChildObservationOverrides(current, replacement));
                            } else if (current != null
                                    && current.isObject()
                                    && replacement.isObject()) {
                                merged.set(field.getKey(), merge(current, replacement));
                            } else {
                                merged.set(field.getKey(), replacement.deepCopy());
                            }
                        });
        return merged;
    }

    private static JsonNode mergeFieldObservationOverrides(
            final JsonNode current, final JsonNode overrides) {
        final ArrayNode merged = current.deepCopy();
        overrides
                .fields()
                .forEachRemaining(
                        override -> {
                            int matchedIndex = -1;
                            for (int index = 0; index < merged.size(); index++) {
                                if (override.getKey()
                                        .equals(merged.get(index).path("path").textValue())) {
                                    matchedIndex = index;
                                    break;
                                }
                            }
                            if (matchedIndex < 0 || !override.getValue().isObject()) {
                                throw new IllegalArgumentException(
                                        "Um override de campo não pertence à observação-base.");
                            }
                            merged.set(
                                    matchedIndex,
                                    merge(merged.get(matchedIndex), override.getValue()));
                        });
        return merged;
    }

    private static JsonNode mergeChildObservationOverrides(
            final JsonNode current, final JsonNode overrides) {
        final ArrayNode merged = current.deepCopy();
        overrides
                .fields()
                .forEachRemaining(
                        override -> {
                            int matchedIndex = -1;
                            for (int index = 0; index < merged.size(); index++) {
                                if (override.getKey()
                                        .equals(merged.get(index).path("kind").textValue())) {
                                    matchedIndex = index;
                                    break;
                                }
                            }
                            if (matchedIndex < 0 || !override.getValue().isObject()) {
                                throw new IllegalArgumentException(
                                        "Um override de filho não pertence à observação-base.");
                            }
                            merged.set(
                                    matchedIndex,
                                    merge(merged.get(matchedIndex), override.getValue()));
                        });
        return merged;
    }

    record RawFixture(
            String schemaVersion,
            String fixtureMarker,
            String profileId,
            JsonNode baseObservation,
            List<RawScenario> scenarios) {}

    record RawScenario(
            String scenarioId,
            ScenarioOutcome expectedOutcome,
            Set<FailClosedReason> expectedReasons,
            JsonNode override) {}
}
