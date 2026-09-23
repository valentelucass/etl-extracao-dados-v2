package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FoundationOutcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ObservationStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ProfileStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ScenarioOutcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CharacterizationFoundationTest {

    private final CharacterizationProfileRegistry registry =
            CharacterizationProfileRegistry.loadDefault();
    private final CharacterizationFixtureLoader fixtureLoader = new CharacterizationFixtureLoader();

    @Test
    void loadsExactlyTheFourPreparedProviderNeutralProfiles() {
        assertEquals(EnumSet.allOf(Entity.class), entities(registry.profiles()));
        assertEquals(4, CharacterizationProfileRegistry.PROFILE_RESOURCES.size());

        for (final CharacterizationProfile profile : registry.profiles()) {
            assertEquals(ProfileStatus.PREPARED_NOT_EXECUTED, profile.profileStatus());
            assertEquals(GateStatus.ORACLE_REQUIRED, profile.gateStatus());
            assertFalse(profile.pagination().completenessProven());
            assertFalse(profile.pagination().snapshotProven());
            assertFalse(profile.pagination().shortPageIsTerminal());
            assertTrue(profile.sourceKey().typeTagged());
            assertEquals(19, profile.requiredChecks().size());
            assertNotEquals("DEFAULT", profile.scope().sourceInstance().name());
            assertNotEquals("GLOBAL", profile.scope().tenantScope().name());
        }
    }

    @Test
    void bindsEveryProfileToTheNormalizedSyntheticFixtureFingerprint() {
        assertAll(
                registry.profiles().stream()
                        .map(
                                profile ->
                                        () ->
                                                assertEquals(
                                                        profile.fixtureFingerprint(),
                                                        fixtureLoader
                                                                .loadResource(
                                                                        profile.fixtureResource())
                                                                .fixtureFingerprint(),
                                                        profile.profileId())));
    }

    @Test
    void preservesTheEntitySpecificContractBindingsWithoutCrossSourceInference() {
        final CharacterizationProfile coletas = registry.profile(Entity.COLETAS);
        assertEquals("dataexport-6908", coletas.contract().contractId());
        assertEquals("/id", coletas.sourceKey().path());
        assertEquals("/sequence_code", coletas.businessAliases().get(0).path());
        assertTrue(
                coletas.filters().stream()
                        .noneMatch(CharacterizationProfile.FilterContract::watermark));

        final CharacterizationProfile manifestos = registry.profile(Entity.MANIFESTOS);
        assertEquals("dataexport-6399", manifestos.contract().contractId());
        assertEquals("/sequence_code", manifestos.sourceKey().path());
        assertTrue(manifestos.expectedPaths().contains("/mft_mfs_key"));
        assertFalse(manifestos.expectedPaths().contains("/updated_at"));

        final CharacterizationProfile cotacoes = registry.profile(Entity.COTACOES);
        assertEquals("dataexport-6906", cotacoes.contract().contractId());
        assertEquals("/sequence_code", cotacoes.sourceKey().path());
        assertFalse(cotacoes.expectedPaths().contains("/id"));
        assertFalse(cotacoes.expectedPaths().contains("/updated_at"));

        final CharacterizationProfile usuarios = registry.profile(Entity.USUARIOS);
        assertEquals(SourceKind.GRAPHQL, usuarios.sourceKind());
        assertEquals("graphql-individual", usuarios.contract().contractId());
        assertEquals("/node/id", usuarios.sourceKey().path());
        assertEquals(20, usuarios.pagination().maximumPageSize());
        assertTrue(
                usuarios.filters().stream()
                        .anyMatch(
                                filter ->
                                        "individual.enabled".equals(filter.path())
                                                && Boolean.TRUE.equals(
                                                        filter.requiredBooleanValue())));
        assertFalse(usuarios.expectedPaths().contains("/node/updatedAt"));
    }

    @Test
    void everySyntheticScenarioProducesItsExactExpectedFailClosedResult() {
        for (final CharacterizationProfile profile : registry.profiles()) {
            final CharacterizationFixture fixture =
                    fixtureLoader.loadResource(profile.fixtureResource());
            assertEquals(profile.profileId(), fixture.profileId());
            final CharacterizationOracleAdapter adapter =
                    CharacterizationAdapterRegistry.adapterFor(profile.sourceKind());
            final CharacterizationEvaluator evaluator = registry.evaluator(profile.entity());
            for (final CharacterizationFixture.Scenario scenario : fixture.scenarios()) {
                final CharacterizationObservation observation = adapter.observe(profile, scenario);
                final CharacterizationResult result = evaluator.evaluate(profile, observation);
                final ObservationStatus expectedStatus =
                        scenario.expectedOutcome() == ScenarioOutcome.SYNTHETIC_STRUCTURE_ACCEPTED
                                ? ObservationStatus.SYNTHETIC_STRUCTURE_ACCEPTED
                                : ObservationStatus.FAIL_CLOSED;
                assertEquals(
                        expectedStatus,
                        result.observationStatus(),
                        profile.profileId() + "/" + scenario.scenarioId());
                assertEquals(
                        scenario.expectedReasons(),
                        result.failClosedReasons(),
                        profile.profileId() + "/" + scenario.scenarioId());
                assertEquals(ProfileStatus.PREPARED_NOT_EXECUTED, result.profileStatus());
                assertEquals(GateStatus.ORACLE_REQUIRED, result.gateStatus());
            }
        }
    }

    @Test
    void fixturesContainTheFixedCommonAndEntitySpecificMatrix() {
        final Set<String> common =
                Set.of(
                        "SYNTH_ACCEPTED_BASELINE",
                        "SYNTH_AUTHORIZATION_MISSING",
                        "SYNTH_BYTE_LIMIT_EXCEEDED",
                        "SYNTH_CARDINALITY_DRIFT",
                        "SYNTH_ORACLE_MISSING",
                        "SYNTH_PAGE_LIMIT_EXCEEDED",
                        "SYNTH_PATH_DRIFT",
                        "SYNTH_PRESENCE_MODEL_INCOMPLETE",
                        "SYNTH_ROOT_DRIFT",
                        "SYNTH_ROW_LIMIT_EXCEEDED",
                        "SYNTH_SOURCE_INSTANCE_MISSING",
                        "SYNTH_SOURCE_KEY_COLLISION",
                        "SYNTH_SOURCE_KEY_MISSING",
                        "SYNTH_SOURCE_KEY_NULL",
                        "SYNTH_SOURCE_KEY_TYPE_DRIFT",
                        "SYNTH_SOURCE_KEY_VALUE_INVALID",
                        "SYNTH_TENANT_SCOPE_MISSING",
                        "SYNTH_TERMINALITY_INCOMPLETE");
        final Map<Entity, Set<String>> entitySpecific =
                Map.of(
                        Entity.COLETAS,
                        Set.of(
                                "SYNTH_ACCEPTED_COMPLEMENTARY_FILTER_ABSENT",
                                "SYNTH_ACCEPTED_SHORT_PAGE_CONTINUES",
                                "SYNTH_BUSINESS_ALIAS_AS_SOURCE_KEY",
                                "SYNTH_DISTINCT_SOURCE_ZERO_WITH_ROWS",
                                "SYNTH_EXPANSION_DRIFT",
                                "SYNTH_EXPANSION_FLAG_WITHOUT_ROWS",
                                "SYNTH_FIELD_TYPE_DRIFT",
                                "SYNTH_FILTER_CONTRACT_DRIFT",
                                "SYNTH_FILTER_WATERMARK_DRIFT",
                                "SYNTH_INVALID_TIMESTAMP",
                                "SYNTH_LOGICAL_PER_EXCEEDED",
                                "SYNTH_ORDER_PARITY_VIOLATION",
                                "SYNTH_PAGE_SIZE_EXCEEDED",
                                "SYNTH_RELAY_FLAG_ON_NUMERIC_PAGE",
                                "SYNTH_RESERVED_SOURCE_INSTANCE_DEFAULT",
                                "SYNTH_RELATIONSHIP_INFERENCE_DRIFT",
                                "SYNTH_SHORT_PAGE_FALSE_TERMINAL",
                                "SYNTH_SOURCE_KEY_FIELD_WEAKEST_PRESENCE",
                                "SYNTH_SOURCE_KEY_PARTIALLY_MISSING",
                                "SYNTH_SOURCE_KEY_TAGGING_MISSING",
                                "SYNTH_STATUS_SEMANTICS_DRIFT",
                                "SYNTH_STATUS_POLICY_FINGERPRINT_DRIFT",
                                "SYNTH_STATUS_COUNT_PRESENCE_MISMATCH",
                                "SYNTH_TERMINAL_KIND_DRIFT",
                                "SYNTH_TEMPORAL_STATE_NOT_APPLICABLE",
                                "SYNTH_TEMPORAL_TRANSLATION_DRIFT",
                                "SYNTH_TIMEZONE_UNCONFIRMED",
                                "SYNTH_TIMEZONE_HOST_DRIFT",
                                "SYNTH_UNMARKED_SCOPE_REFUSED"),
                        Entity.MANIFESTOS,
                        Set.of(
                                "SYNTH_ACCEPTED_CHILD_REPLAY",
                                "SYNTH_ACCEPTED_SHORT_PAGE_CONTINUES",
                                "SYNTH_ACCEPTED_TOP_LEVEL_ROOT",
                                "SYNTH_CHILD_ATTRIBUTE_ASYMMETRY",
                                "SYNTH_CHILD_DISTINCT_ZERO_WITH_COUNT",
                                "SYNTH_CHILD_IDENTITY_CONFLICT",
                                "SYNTH_CHILD_KEY_WITHOUT_ATTRIBUTE",
                                "SYNTH_CHILD_KEY_TAGGING_MISSING",
                                "SYNTH_CHILD_VALUE_PRESENCE_WITHOUT_COUNT",
                                "SYNTH_DATAEXPORT_CURSOR_INVENTED",
                                "SYNTH_EXPANSION_DRIFT",
                                "SYNTH_EXPANSION_FLAG_WITHOUT_ROWS",
                                "SYNTH_INVALID_TIMESTAMP",
                                "SYNTH_MDFE_ATTRIBUTE_AS_IDENTITY",
                                "SYNTH_MDFE_KEY_DOMAIN_INVALID",
                                "SYNTH_MDFE_STATUS_CHILD_ROLE_DRIFT",
                                "SYNTH_ORDER_PARITY_VIOLATION",
                                "SYNTH_SHORT_PAGE_FALSE_TERMINAL",
                                "SYNTH_STATUS_POLICY_FINGERPRINT_DRIFT",
                                "SYNTH_TIMEZONE_UNCONFIRMED",
                                "SYNTH_ZERO_PAGE_SIZE"),
                        Entity.COTACOES,
                        Set.of(
                                "SYNTH_ACCEPTED_SHORT_PAGE_CONTINUES",
                                "SYNTH_CHILD_DRIFT",
                                "SYNTH_EXPANSION_DRIFT",
                                "SYNTH_INVALID_TIMESTAMP",
                                "SYNTH_ORDER_PARITY_VIOLATION",
                                "SYNTH_ORDER_EXPRESSION_DRIFT",
                                "SYNTH_ORDER_TIE_REPLAY_ACCEPTED",
                                "SYNTH_ORDER_TIE_VIOLATION",
                                "SYNTH_SHORT_PAGE_FALSE_TERMINAL",
                                "SYNTH_TEMPORAL_VALID_WITHOUT_VALUES",
                                "SYNTH_TEMPORAL_VALUE_PRESENCE_MISMATCH",
                                "SYNTH_TEMPORAL_PRECEDENCE_DRIFT",
                                "SYNTH_TIMEZONE_UNCONFIRMED"),
                        Entity.USUARIOS,
                        Set.of(
                                "SYNTH_ACCEPTED_CROSS_TYPE_TAGS_DISTINCT",
                                "SYNTH_ACCEPTED_INTEGER_ID",
                                "SYNTH_CURSOR_CYCLE",
                                "SYNTH_CURSOR_COUNT_INCOHERENT",
                                "SYNTH_CURSOR_MISSING",
                                "SYNTH_CURSOR_REPEATED",
                                "SYNTH_CURSOR_REQUIREMENT_MISSING",
                                "SYNTH_DATA_PAGES_EXCEED_ROWS",
                                "SYNTH_DEACTIVATION_BY_ABSENCE",
                                "SYNTH_CHILD_COUNT_WITHOUT_OBSERVATION",
                                "SYNTH_FIELD_SCOPE_MISMATCH",
                                "SYNTH_FILTER_REQUIRED_VALUE_DRIFT",
                                "SYNTH_HAS_NEXT_AT_LIMIT",
                                "SYNTH_PAGE_SIZE_EXCEEDED",
                                "SYNTH_SHORT_PAGE_FALSE_TERMINAL",
                                "SYNTH_SOURCE_KEY_CROSS_TYPE_COLLISION"));
        final EnumSet<FailClosedReason> coveredReasons = EnumSet.noneOf(FailClosedReason.class);

        for (final CharacterizationProfile profile : registry.profiles()) {
            final CharacterizationFixture fixture =
                    fixtureLoader.loadResource(profile.fixtureResource());
            final Set<String> scenarioIds =
                    fixture.scenarios().stream()
                            .map(CharacterizationFixture.Scenario::scenarioId)
                            .collect(Collectors.toUnmodifiableSet());
            assertTrue(scenarioIds.containsAll(common), profile.profileId());
            assertTrue(
                    scenarioIds.containsAll(entitySpecific.get(profile.entity())),
                    profile.profileId());
            fixture.scenarios()
                    .forEach(scenario -> coveredReasons.addAll(scenario.expectedReasons()));
        }

        final EnumSet<FailClosedReason> requiredReasons = EnumSet.allOf(FailClosedReason.class);
        requiredReasons.remove(FailClosedReason.UNSUPPORTED_EVIDENCE_CLASSIFICATION);
        assertEquals(requiredReasons, coveredReasons);
    }

    @Test
    void aggregatesOnlyFourAcceptedProfilesAndIsolatesOneEntityFailure() {
        final Map<Entity, CharacterizationResult> accepted = acceptedResults();
        final CharacterizationSummary complete =
                CharacterizationSummary.from(new ArrayList<>(accepted.values()));
        assertEquals(
                FoundationOutcome.FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES,
                complete.outcome());
        assertEquals(4, complete.profileCount());

        final CharacterizationProfile failedProfile = registry.profile(Entity.MANIFESTOS);
        final CharacterizationFixture fixture =
                fixtureLoader.loadResource(failedProfile.fixtureResource());
        final CharacterizationFixture.Scenario failedScenario =
                scenario(fixture, "SYNTH_ORACLE_MISSING");
        final CharacterizationResult failed =
                registry.evaluator(Entity.MANIFESTOS)
                        .evaluate(failedProfile, failedScenario.observation());
        final List<CharacterizationResult> mixed = new ArrayList<>(accepted.values());
        mixed.remove(accepted.get(Entity.MANIFESTOS));
        mixed.add(failed);

        final CharacterizationSummary failClosed = CharacterizationSummary.from(mixed);
        assertEquals(FoundationOutcome.FOUNDATION_FAIL_CLOSED, failClosed.outcome());
        for (final Entity unaffected : List.of(Entity.COLETAS, Entity.COTACOES, Entity.USUARIOS)) {
            assertTrue(failClosed.results().contains(accepted.get(unaffected)));
        }
    }

    @Test
    void rejectsAnyMissingOrDuplicateProfileRegistration() {
        final List<CharacterizationProfile> profiles = registry.profiles();
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterizationProfileRegistry.of(profiles.subList(0, 3)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationProfileRegistry.of(
                                List.of(
                                        profiles.get(0),
                                        profiles.get(0),
                                        profiles.get(1),
                                        profiles.get(2))));
    }

    @Test
    void refusesToFabricateAnAcceptedResultWithMissingOrFailedChecks() {
        final CharacterizationResult accepted = acceptedResults().get(Entity.COLETAS);
        final Map<CharacterizationVocabulary.CharacterizationCheck, Boolean> failedChecks =
                new EnumMap<>(accepted.checks());
        failedChecks.put(CharacterizationVocabulary.CharacterizationCheck.ROOT_MATCH, false);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CharacterizationResult(
                                accepted.profileId(),
                                accepted.entity(),
                                accepted.profileFingerprint(),
                                accepted.contractFingerprint(),
                                accepted.observationFingerprint(),
                                accepted.observationStatus(),
                                accepted.profileStatus(),
                                accepted.gateStatus(),
                                failedChecks,
                                accepted.failClosedReasons()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CharacterizationResult(
                                accepted.profileId(),
                                accepted.entity(),
                                accepted.profileFingerprint(),
                                accepted.contractFingerprint(),
                                accepted.observationFingerprint(),
                                accepted.observationStatus(),
                                accepted.profileStatus(),
                                accepted.gateStatus(),
                                Map.of(),
                                accepted.failClosedReasons()));
    }

    private Map<Entity, CharacterizationResult> acceptedResults() {
        final Map<Entity, CharacterizationResult> results = new EnumMap<>(Entity.class);
        for (final CharacterizationProfile profile : registry.profiles()) {
            final CharacterizationFixture fixture =
                    fixtureLoader.loadResource(profile.fixtureResource());
            final CharacterizationFixture.Scenario accepted =
                    scenario(fixture, "SYNTH_ACCEPTED_BASELINE");
            results.put(
                    profile.entity(),
                    registry.evaluator(profile.entity()).evaluate(profile, accepted.observation()));
        }
        return results;
    }

    private static CharacterizationFixture.Scenario scenario(
            final CharacterizationFixture fixture, final String scenarioId) {
        return fixture.scenarios().stream()
                .filter(candidate -> scenarioId.equals(candidate.scenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private static EnumSet<Entity> entities(final List<CharacterizationProfile> profiles) {
        final EnumSet<Entity> result = EnumSet.noneOf(Entity.class);
        profiles.forEach(profile -> result.add(profile.entity()));
        return result;
    }
}
