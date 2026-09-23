package br.com.esl.etl.v2.contratos.caracterizacao;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class CharacterizationClosedSchemaTest {

    private final StrictUtf8JsonLoader jsonLoader = new StrictUtf8JsonLoader();

    @Test
    void rejectsMissingAndNullPrimitiveEvidenceInsteadOfDefaultingFailOpen() {
        final CharacterizationFixtureLoader fixtureLoader = new CharacterizationFixtureLoader();
        final ObjectNode fixtureDocument =
                (ObjectNode)
                        jsonLoader.loadResource(
                                CharacterizationClosedSchemaTest.class,
                                "/contracts/v2-012/fixtures/coletas-6908.synthetic.json",
                                CharacterizationFixtureLoader.MAXIMUM_FIXTURE_BYTES);

        final ObjectNode missingCollision =
                (ObjectNode) fixtureDocument.path("baseObservation").deepCopy();
        missingCollision.remove("sourceKeyCollision");
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureLoader.bindObservation(missingCollision));

        final ObjectNode nullAbsencePolicy =
                (ObjectNode) fixtureDocument.path("baseObservation").deepCopy();
        nullAbsencePolicy.putNull("absenceLifecycleInferenceApplied");
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureLoader.bindObservation(nullAbsencePolicy));
    }

    @Test
    void rejectsMissingAndNullPrimitiveProfileProperties() {
        final CharacterizationProfileLoader profileLoader = new CharacterizationProfileLoader();
        final ObjectNode missingProperty = profileDocument();
        ((ObjectNode) missingProperty.path("pagination")).remove("shortPageIsTerminal");
        assertThrows(
                IllegalArgumentException.class, () -> profileLoader.bindProfile(missingProperty));

        final ObjectNode nullProperty = profileDocument();
        ((ObjectNode) nullProperty.path("pagination")).putNull("completenessProven");
        assertThrows(IllegalArgumentException.class, () -> profileLoader.bindProfile(nullProperty));
    }

    private ObjectNode profileDocument() {
        return (ObjectNode)
                jsonLoader.loadResource(
                        CharacterizationClosedSchemaTest.class,
                        "/contracts/v2-012/profiles/coletas-6908.profile.json",
                        CharacterizationProfileLoader.MAXIMUM_PROFILE_BYTES);
    }
}
