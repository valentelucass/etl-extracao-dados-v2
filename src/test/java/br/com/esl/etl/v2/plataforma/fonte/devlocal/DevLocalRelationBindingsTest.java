package br.com.esl.etl.v2.plataforma.fonte.devlocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DevLocalRelationBindingsTest {
    @Test
    void sampleDeclaresTwoBindingsAndRichResultMatchesIndependentLiteralOracle()
            throws IOException {
        final JsonNode sample = DevLocalDataset.sample();
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals(2, sample.path("relationBindings").size());
        assertEquals(sample.path("relationBindings"), actual.path("relationBindings"));
        final JsonNode expected;
        try (final var input = getClass().getResourceAsStream("/dev-local/expected.json")) {
            assertNotNull(input);
            expected =
                    DevLocalDataset.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(
                expected.path("relationBindingChecks"),
                DevLocalDataset.expected().path("relationBindingChecks"));
        assertEquals(2, expected.path("relationBindingChecks").size());
        for (final JsonNode assertion : expected.path("relationBindingChecks")) {
            assertEquals(assertion.get("value"), actual.at(assertion.path("path").asText()));
        }
        assertEquals("MATCH", DevLocalDataset.verify(actual).path("status").asText());
        assertEquals(150, DevLocalDataset.verify(actual).path("comparisons").intValue());
    }

    @Test
    void noDeclarationsNeverInferRelationsFromMatchingNumbersOrRelationCandidates() {
        final ObjectNode sample = sample();
        sample.remove("relationBindings");
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertTrue(actual.path("relationBindings").isArray());
        assertTrue(actual.path("relationBindings").isEmpty());
        assertTrue(actual.path("relationBindingObservations").isEmpty());
        assertEquals(
                "INTEGER:3101", entity(actual, "Manifestos").at("/rows/0/relationKey").asText());
        assertEquals("DIVERGED", DevLocalDataset.verify(actual).path("status").asText());
        sample.putArray("relationBindings");
        assertEquals(actual, DevLocalDataset.characterize(sample));
    }

    @Test
    void independentOracleRejectsChangedOrReorderedDeclaredEvidence() {
        final ObjectNode sample = sample();
        binding(sample, 0).put("evidence", "synthetic-mc-successor");
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals("synthetic-mc-successor", actual.at("/relationBindings/0/evidence").asText());
        assertEquals("DIVERGED", DevLocalDataset.verify(actual).path("status").asText());
        ((ObjectNode) actual.path("relationBindings").get(0)).put("targetKey", "INTEGER:3102");
        assertEquals("DIVERGED", DevLocalDataset.verify(actual).path("status").asText());
        assertEquals(
                "synthetic-mc-fixture-v1",
                DevLocalDataset.sample().at("/relationBindings/0/evidence").asText());
    }

    @Test
    void matchingNumberInAnotherEntityCannotSupplyMissingScopedEndpoint() {
        final ObjectNode sample = sample();
        binding(sample, 0).put("targetKey", "INTEGER:4101");
        final JsonNode actual = DevLocalDataset.characterize(sample);
        final JsonNode observation = actual.at("/relationBindingObservations/0");
        assertEquals("Coletas", observation.path("targetEntity").asText());
        assertEquals(0, observation.path("targetObservedRows").intValue());
        assertEquals("ORPHAN_BLOCKED", observation.path("status").asText());
        assertEquals("INTEGER:4101", entity(actual, "Fretes").at("/rows/0/sourceKey").asText());
        assertFalse(observation.path("canonicalIdentityProven").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"origin", "target"})
    void absentEndpointStaysExplicitlyBlocked(final String endpoint) {
        final ObjectNode sample = sample();
        binding(sample, 0).put(endpoint + "Key", "INTEGER:9991");
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals("ORPHAN_BLOCKED", actual.at("/relationBindingObservations/0/status").asText());
        assertEquals(2, actual.path("relationBindings").size());
    }

    @Test
    void ambiguousRootIsNeverCollapsedIntoResolvedEndpoint() {
        final ObjectNode sample = sample();
        row(sample, "Coletas", 0, 1).put("id", 3101);
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals(2, actual.at("/relationBindingObservations/0/targetObservedRows").intValue());
        assertEquals(
                "AMBIGUOUS_BLOCKED", actual.at("/relationBindingObservations/0/status").asText());
    }

    @Test
    void filteredOrQuarantinedRootNeverBecomesUsableBindingEvidence() {
        final ObjectNode filtered = sample();
        binding(filtered, 0).put("targetKey", "INTEGER:3103");
        assertEquals(
                "OUTSIDE_WINDOW_BLOCKED",
                DevLocalDataset.characterize(filtered)
                        .at("/relationBindingObservations/0/status")
                        .asText());
        final ObjectNode invalid = sample();
        row(invalid, "Coletas", 0, 0).put("status", false);
        final JsonNode actual = DevLocalDataset.characterize(invalid);
        assertFalse(entity(actual, "Coletas").at("/rows/0/eligible").asBoolean());
        assertEquals(
                "QUARANTINED_BLOCKED", actual.at("/relationBindingObservations/0/status").asText());
    }

    @Test
    void mapperAcceptedRootWithoutFreshnessCannotProveDatedBinding() {
        final ObjectNode sample = sample();
        row(sample, "Coletas", 0, 0).put("status_updated_at", "invalid-synthetic-date");
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertTrue(entity(actual, "Coletas").at("/rows/0/valid").asBoolean());
        assertTrue(entity(actual, "Coletas").at("/rows/0/freshness").isNull());
        assertEquals(
                "DATE_MISMATCH_BLOCKED",
                actual.at("/relationBindingObservations/0/status").asText());
    }

    @Test
    void exactArrayCapIsSupportedWithOnlyDeclaredComponentsAndNoInferredIdentity() {
        final ObjectNode sample = sample();
        final ObjectNode base = binding(sample, 0).deepCopy();
        final ArrayNode bindings = sample.putArray("relationBindings");
        for (int i = 0; i < 32; i++) {
            final ObjectNode declaration = base.deepCopy();
            declaration.put("originComponent", "INTEGER:" + (70001 + i));
            declaration.put("evidence", "synthetic-range-" + i);
            bindings.add(declaration);
        }
        final JsonNode actual = DevLocalDataset.characterize(sample);
        assertEquals(32, actual.path("relationBindings").size());
        assertEquals(32, actual.path("relationBindingObservations").size());
        assertEquals(
                "DECLARED_SYNTHETIC_BINDING",
                actual.at("/relationBindingObservations/31/componentAuthority").asText());
        assertFalse(
                actual.at("/relationBindingObservations/31/canonicalIdentityProven").asBoolean());
    }

    @Test
    void dateMustMatchObservedTargetUtcDayBeforeRootEvidenceIsUsable() {
        final ObjectNode sample = sample();
        ((ObjectNode) sample.get("filter")).put("endExclusive", "2026-10-03T00:00:00Z");
        binding(sample, 0).put("date", "2026-10-02");
        assertEquals(
                "DATE_MISMATCH_BLOCKED",
                DevLocalDataset.characterize(sample)
                        .at("/relationBindingObservations/0/status")
                        .asText());
    }

    @Test
    void bindingsHaveFiniteArrayCapAndClosedRequiredSchema() {
        final ObjectNode sample = sample();
        final ArrayNode bindings = (ArrayNode) sample.get("relationBindings");
        for (int i = bindings.size(); i < 33; i++) {
            bindings.add(bindings.get(0).deepCopy());
        }
        assertReason("DEV_RELATION_BOUND", sample);
        sample.set("relationBindings", DevLocalDataset.parse("{}"));
        assertReason("DEV_RELATION_BOUND", sample);
        final ObjectNode unknown = sample();
        binding(unknown, 0).put("endpoint", "synthetic-only-unapproved-field");
        assertReason("DEV_RELATION_SCHEMA", unknown);
        final ObjectNode missing = sample();
        binding(missing, 0).remove("cardinality");
        assertReason("DEV_RELATION_SCHEMA", missing);
    }

    @ParameterizedTest
    @CsvSource({"MC,INTEGER:3101", "CF,ROOT"})
    void actualRelationalBindingRejectsWrongTargetComponentRole(
            final String relation, final String component) {
        final ObjectNode sample = sample();
        final ObjectNode declaration = binding(sample, "MC".equals(relation) ? 0 : 1);
        declaration.put("targetComponent", component);
        assertReason("DEV_RELATION_DECLARATION", sample);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ROOT",
                "STRING:3101",
                "INTEGER:0",
                "INTEGER:-1",
                "INTEGER:03101",
                "INTEGER:9223372036854775808"
            })
    void originKeyRequiresExactPositiveIntegerWireWithoutCoercion(final String key) {
        final ObjectNode sample = sample();
        binding(sample, 0).put("originKey", key);
        assertReason("DEV_RELATION_DECLARATION", sample);
    }

    @Test
    void numericJsonFieldsAndRootOriginComponentCannotBeCoerced() {
        final ObjectNode sample = sample();
        binding(sample, 0).put("targetKey", 3101);
        assertReason("DEV_RELATION_SCHEMA", sample);
        binding(sample, 0).put("targetKey", "INTEGER:3101");
        binding(sample, 0).put("originComponent", "ROOT");
        assertReason("DEV_RELATION_DECLARATION", sample);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2", "1.0", "4294967297", "\"1\"", "null"})
    void revisionCannotOverflowCoerceOrWidenDeclaredRevisionOne(final String revision) {
        final ObjectNode sample = sample();
        binding(sample, 0).set("revision", DevLocalDataset.parse(revision));
        assertReason("DEV_RELATION_SCHEMA", sample);
    }

    @ParameterizedTest
    @CsvSource({
        "relation,XX",
        "cardinality,MANY_TO_ONE",
        "evidence,nominal-private-probe",
        "date,2026-02-30",
        "date,2026-10-01T00:00:00Z"
    })
    void invalidLiteralDomainValuesAreRejectedWithoutInputLeak(
            final String field, final String value) {
        final ObjectNode sample = sample();
        binding(sample, 0).put(field, value);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class, () -> DevLocalDataset.characterize(sample));
        assertEquals("DEV_RELATION_DECLARATION", failure.getMessage());
        assertFalse(failure.toString().contains(value));
        assertEquals(null, failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-30", "2026-10-02"})
    void declarationDateCannotExtendFixtureFilterWindow(final String date) {
        final ObjectNode sample = sample();
        binding(sample, 0).put("date", date);
        assertReason("DEV_RELATION_DATE_BOUND", sample);
    }

    @Test
    void duplicateEvidenceAndConflictingOriginComponentAreNotAcceptedAsIndependentBindings() {
        final ObjectNode duplicate = sample();
        binding(duplicate, 1).put("evidence", binding(duplicate, 0).path("evidence").asText());
        assertReason("DEV_RELATION_CONFLICT", duplicate);
        final ObjectNode conflict = sample();
        final ObjectNode another = binding(conflict, 0).deepCopy();
        another.put("evidence", "synthetic-mc-conflict");
        another.put("targetKey", "INTEGER:3102");
        ((ArrayNode) conflict.get("relationBindings")).add(another);
        assertReason("DEV_RELATION_CONFLICT", conflict);
    }

    @Test
    void callerMutationCannotChangeFreshEmbeddedDeclarationsOrOracle() {
        final ObjectNode sample = sample();
        binding(sample, 0).put("evidence", "synthetic-changed-copy");
        final JsonNode expected = DevLocalDataset.expected();
        ((ObjectNode) expected.at("/relationBindingChecks/0/value/0"))
                .put("evidence", "synthetic-changed-oracle");
        assertEquals(
                "synthetic-mc-fixture-v1",
                DevLocalDataset.sample().at("/relationBindings/0/evidence").asText());
        assertEquals(
                "synthetic-mc-fixture-v1",
                DevLocalDataset.expected()
                        .at("/relationBindingChecks/0/value/0/evidence")
                        .asText());
    }

    private static ObjectNode sample() {
        return (ObjectNode) DevLocalDataset.sample();
    }

    private static ObjectNode binding(final JsonNode sample, final int index) {
        return (ObjectNode) sample.path("relationBindings").get(index);
    }

    private static JsonNode entity(final JsonNode root, final String name) {
        for (final JsonNode entity : root.path("entities")) {
            if (name.equals(entity.path("entity").asText())) {
                return entity;
            }
        }
        throw new AssertionError("SYNTHETIC_ENTITY_MISSING");
    }

    private static ObjectNode row(
            final JsonNode sample, final String entity, final int page, final int row) {
        return (ObjectNode) entity(sample, entity).at("/pages/" + page + "/rows/" + row);
    }

    private static void assertReason(final String code, final JsonNode sample) {
        assertEquals(
                code,
                assertThrows(
                                IllegalArgumentException.class,
                                () -> DevLocalDataset.characterize(sample))
                        .getMessage());
    }
}
