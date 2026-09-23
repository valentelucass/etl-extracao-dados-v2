package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class Qfnd02MutationTest {

    private static final String ROOT = "/contracts/v2-012/extensions/q-fnd-02/";
    private final Qfnd02Loader loader = new Qfnd02Loader();

    @Test
    void catalogsFortyEightUniqueRealMutations() {
        final List<Qfnd02Observation.Scenario> scenarios = scenarios();
        assertEquals(48, scenarios.size());
        assertEquals(
                scenarios.size(),
                new HashSet<>(
                                scenarios.stream()
                                        .map(Qfnd02Observation.Scenario::scenarioId)
                                        .toList())
                        .size());
        assertEquals(
                scenarios.size(),
                new HashSet<>(
                                scenarios.stream()
                                        .map(
                                                scenario ->
                                                        (scenario.scenarioId()
                                                                                .startsWith(
                                                                                        "SYNTH_FRE_")
                                                                        ? "FRETES|"
                                                                        : "LOCALIZACAO|")
                                                                + scenario.mutation())
                                        .toList())
                        .size());
        assertEquals(
                Set.of(
                        "PROFILE_DECISION_FRE-01",
                        "PROFILE_DECISION_FRE-02",
                        "PROFILE_DECISION_FRE-03",
                        "PROFILE_DECISION_FRE-04",
                        "PROFILE_DECISION_FRE-05",
                        "PROFILE_DECISION_FRE-06",
                        "PROFILE_DECISION_FRE-07",
                        "PROFILE_DECISION_LOC-01",
                        "PROFILE_DECISION_LOC-02",
                        "PROFILE_DECISION_LOC-03",
                        "PROFILE_DECISION_LOC-04",
                        "PROFILE_DECISION_LOC-05",
                        "PROFILE_DECISION_LOC-06",
                        "PROFILE_DECISION_LOC-07"),
                scenarios.stream()
                        .map(Qfnd02Observation.Scenario::mutation)
                        .filter(mutation -> mutation.startsWith("PROFILE_DECISION_"))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @TestFactory
    Stream<DynamicTest> executesEveryCatalogedMutationAndChecksItsExactReason() {
        return scenarios().stream()
                .map(
                        scenario ->
                                DynamicTest.dynamicTest(
                                        scenario.scenarioId(), () -> execute(scenario)));
    }

    private void execute(final Qfnd02Observation.Scenario scenario) throws IOException {
        final boolean freight = scenario.scenarioId().startsWith("SYNTH_FRE_");
        final String profileFile =
                freight ? "fretes-6389.profile.json" : "localizacao-8656.profile.json";
        final String fixtureFile =
                freight ? "fretes-6389.synthetic.json" : "localizacao-8656.synthetic.json";
        final String profileJson = resource("profiles/" + profileFile);
        final String fixtureJson = resource("fixtures/" + fixtureFile);
        final String mutation = scenario.mutation();
        if (mutation.startsWith("PROFILE_")) {
            final byte[] bytes = mutateProfile(profileJson, mutation);
            assertThrows(IllegalArgumentException.class, () -> loader.parseProfile(bytes));
            assertEquals("PROFILE_REJECTED", scenario.expectedReason());
            return;
        }
        if ("FIXTURE_UTF8_BOM".equals(mutation)) {
            final byte[] original = fixtureJson.getBytes(StandardCharsets.UTF_8);
            final byte[] bytes = new byte[original.length + 3];
            bytes[0] = (byte) 0xEF;
            bytes[1] = (byte) 0xBB;
            bytes[2] = (byte) 0xBF;
            System.arraycopy(original, 0, bytes, 3, original.length);
            assertThrows(IllegalArgumentException.class, () -> loader.parseFixture(bytes));
            assertEquals("FIXTURE_REJECTED", scenario.expectedReason());
            return;
        }
        if ("FIXTURE_MALFORMED_UTF8".equals(mutation)) {
            final byte[] bytes = fixtureJson.getBytes(StandardCharsets.UTF_8);
            bytes[bytes.length / 2] = (byte) 0xC3;
            bytes[bytes.length / 2 + 1] = (byte) 0x28;
            assertThrows(IllegalArgumentException.class, () -> loader.parseFixture(bytes));
            assertEquals("FIXTURE_REJECTED", scenario.expectedReason());
            return;
        }
        final String mutatedFixture = mutateFixture(fixtureJson, mutation);
        if (scenario.expectedReason().equals("FIXTURE_REJECTED")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> loader.parseFixture(mutatedFixture.getBytes(StandardCharsets.UTF_8)));
            return;
        }
        final Qfnd02Profile profile = loader.parseProfile(profileJson);
        final Qfnd02Observation observation =
                loader.parseFixture(mutatedFixture.getBytes(StandardCharsets.UTF_8));
        final Qfnd02Result result =
                Qfnd02Registry.loadDefault()
                        .evaluator(profile.profileId())
                        .evaluate(profile, observation);
        assertEquals(Qfnd02Vocabulary.Outcome.FAIL_CLOSED, result.outcome(), scenario.scenarioId());
        assertEquals(
                Set.of(Qfnd02Vocabulary.Reason.valueOf(scenario.expectedReason())),
                result.reasons());
    }

    private byte[] mutateProfile(final String json, final String mutation) {
        final String mutated;
        if (mutation.startsWith("PROFILE_DECISION_")) {
            mutated = mutateDecision(json, mutation.substring("PROFILE_DECISION_".length()));
        } else {
            mutated =
                    switch (mutation) {
                        case "PROFILE_WRONG_KEY" ->
                                json.replace(
                                        "\"sourceKey\": {\"path\": \"/id\"",
                                        "\"sourceKey\": {\"path\": \"/reference_number\"");
                        case "PROFILE_MISSING_PATH" ->
                                json.replaceFirst("\\s*\"/updated_at\",\\R", "\n");
                        case "PROFILE_EXTRA_PATH" ->
                                json.replaceFirst("\"/updated_at\"", "\"/unexpected_path\"");
                        case "PROFILE_INVENTED_TYPE" ->
                                json.replaceFirst("\"UNVERIFIED_ORACLE_REQUIRED\"", "\"STRING\"");
                        case "PROFILE_SIDECAR_MIXED" ->
                                json.replace("\"GRAPHQL_SIDECAR\"", "\"DATA_EXPORT\"");
                        case "PROFILE_SIDECAR_AUTHORITY" ->
                                replaceLast(
                                        json,
                                        "\"rootOrFreshnessAuthority\": false",
                                        "\"rootOrFreshnessAuthority\": true");
                        case "PROFILE_PUBLICATION_ENABLED" ->
                                json.replace(
                                        "\"publicationEnabled\": false",
                                        "\"publicationEnabled\": true");
                        case "PROFILE_COMPLETENESS_CLAIMED" ->
                                json.replaceFirst(
                                        "\"completenessProven\": false",
                                        "\"completenessProven\": true");
                        case "PROFILE_EXECUTED" ->
                                json.replace("\"PREPARED_NOT_EXECUTED\"", "\"EXECUTED\"");
                        case "PROFILE_FINGERPRINT_INVALID" ->
                                json.replaceFirst("[0-9a-f]{64}", "0".repeat(64));
                        case "PROFILE_DUPLICATE_JSON_KEY" ->
                                json.replaceFirst(
                                        "\\{",
                                        "{\"schemaVersion\":\"V2_012_Q_FND_02_PROFILE_V1\",");
                        case "PROFILE_SEQUENCE_NUMBER_KEY" ->
                                json.replace(
                                        "\"path\": \"/corporation_sequence_number\"",
                                        "\"path\": \"/sequence_number\"");
                        case "PROFILE_STATUS_BRANCH_SOURCED" ->
                                json.replace(
                                        "\"ABSENT_UNSOURCED_LEGACY\"",
                                        "\"SOURCED_FROM_SIMILAR_FIELD\"");
                        case "PROFILE_SWEEP_ENABLED" ->
                                json.replace("\"sweepEnabled\": false", "\"sweepEnabled\": true");
                        case "PROFILE_SNAPSHOT_CLAIMED" ->
                                json.replaceFirst(
                                        "\"snapshotProven\": false", "\"snapshotProven\": true");
                        case "PROFILE_RELATION_ENABLED" ->
                                json.replace(
                                        "\"relationshipEnabled\": false",
                                        "\"relationshipEnabled\": true");
                        case "PROFILE_NUMERIC_POLICY" ->
                                json.replaceFirst("\"precision\": 38", "\"precision\": 37");
                        case "PROFILE_FRESHNESS_POLICY" ->
                                replaceValue(json, "freshnessPolicy", "DRIFT");
                        case "PROFILE_TERMINAL_STATUS" ->
                                json.replaceFirst("\"finished\"", "\"finished_like\"");
                        case "PROFILE_ABSENCE_POLICY" ->
                                replaceValue(json, "absencePolicy", "DRIFT");
                        case "PROFILE_LIMIT" ->
                                json.replace(
                                        "\"maximumPageSize\": 100", "\"maximumPageSize\": 101");
                        default ->
                                throw new IllegalArgumentException(
                                        "Mutação de perfil sem executor.");
                    };
        }
        assertNotEquals(json, mutated, mutation);
        return mutated.getBytes(StandardCharsets.UTF_8);
    }

    private String mutateFixture(final String json, final String mutation) {
        final String mutated =
                switch (mutation) {
                    case "FIXTURE_MISSING_PATH" ->
                            json.replaceFirst("\\s*\"/updated_at\",\\R", "\n");
                    case "FIXTURE_PRESENCE_MISSING" ->
                            json.replaceFirst(
                                    "\\[\"ABSENT\", \"NULL\", \"VALUE\"\\]",
                                    "[\"ABSENT\", \"VALUE\"]");
                    case "FIXTURE_SHORT_PAGE_TERMINAL" ->
                            json.replaceFirst(
                                    "\"shortPageIsTerminal\": false",
                                    "\"shortPageIsTerminal\": true");
                    case "FIXTURE_PROFILE_BINDING" ->
                            json.replaceFirst(
                                    "V2_012_LOCALIZACAO_8656_DATA_EXPORT",
                                    "V2_012_LOCALIZACAO_8656_FOREIGN");
                    case "FIXTURE_AUTHORITY_DRIFT" ->
                            json.replaceFirst(
                                    "\"rootOrFreshnessAuthority\": true",
                                    "\"rootOrFreshnessAuthority\": false");
                    case "FIXTURE_UNKNOWN_MEMBER" ->
                            json.replaceFirst("\\{", "{\"unknownMember\":true,");
                    case "FIXTURE_DUPLICATE_JSON_KEY" ->
                            json.replaceFirst(
                                    "\\{", "{\"schemaVersion\":\"V2_012_Q_FND_02_FIXTURE_V1\",");
                    default ->
                            throw new IllegalArgumentException("Mutação de fixture sem executor.");
                };
        assertNotEquals(json, mutated, mutation);
        return mutated;
    }

    private static String mutateDecision(final String json, final String id) {
        return replaceValue(json, id, "DRIFT");
    }

    private static String replaceValue(
            final String json, final String property, final String replacement) {
        final Pattern pattern =
                Pattern.compile("(\\\"" + Pattern.quote(property) + "\\\"\\s*:\\s*\\\")[^\\\"]+");
        final Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return json;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + replacement));
    }

    private static String replaceLast(
            final String value, final String target, final String replacement) {
        final int index = value.lastIndexOf(target);
        if (index < 0) {
            return value;
        }
        return value.substring(0, index) + replacement + value.substring(index + target.length());
    }

    private List<Qfnd02Observation.Scenario> scenarios() {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();
        final List<Qfnd02Observation.Scenario> result = new ArrayList<>();
        for (final Qfnd02Profile profile : registry.profiles()) {
            result.addAll(registry.observation(profile.profileId()).scenarios());
        }
        return List.copyOf(result);
    }

    private static String resource(final String path) throws IOException {
        try (var stream = Qfnd02MutationTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) {
                throw new IOException("Recurso sintético ausente.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
