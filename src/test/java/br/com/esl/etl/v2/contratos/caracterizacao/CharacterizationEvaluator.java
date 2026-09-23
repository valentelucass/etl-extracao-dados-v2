package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.AuthorizationState;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.CharacterizationCheck;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.EvidenceClassification;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ObservationStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleAvailability;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PaginationKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.StatusMode;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimestampState;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Comparador puro que acumula todas as divergências e fecha somente a entidade avaliada. */
public final class CharacterizationEvaluator {

    private final CharacterizationRule rule;

    CharacterizationEvaluator(final CharacterizationRule rule) {
        this.rule = Objects.requireNonNull(rule, "A regra da entidade é obrigatória.");
    }

    public CharacterizationResult evaluate(
            final CharacterizationProfile profile, final CharacterizationObservation observation) {
        Objects.requireNonNull(profile, "O perfil é obrigatório.");
        Objects.requireNonNull(observation, "A observação é obrigatória.");
        final EnumSet<FailClosedReason> reasons = EnumSet.noneOf(FailClosedReason.class);
        binding(profile, observation, reasons);
        scopes(profile, observation, reasons);
        structure(profile, observation, reasons);
        filters(profile, observation, reasons);
        relationships(profile, observation, reasons);
        absencePolicy(observation, reasons);
        sourceKey(profile, observation, reasons);
        terminalAndLimits(profile, observation, reasons);
        ordering(profile, observation, reasons);
        temporal(profile, observation, reasons);
        status(profile, observation, reasons);
        counts(observation, reasons);
        final int commonReasonCount = reasons.size();
        rule.evaluate(profile, observation, reasons);
        final boolean entityRulesMatch = reasons.size() == commonReasonCount;
        final Map<CharacterizationCheck, Boolean> checks =
                checks(profile, observation, reasons, entityRulesMatch);
        if (profile.requiredChecks().stream()
                .anyMatch(check -> !checks.getOrDefault(check, false))) {
            if (reasons.isEmpty()) {
                reasons.add(FailClosedReason.PROFILE_BINDING_MISMATCH);
            }
        }
        return new CharacterizationResult(
                profile.profileId(),
                profile.entity(),
                profile.profileFingerprint(),
                profile.contract().contractFingerprint(),
                observation.observationFingerprint(),
                reasons.isEmpty()
                        ? ObservationStatus.SYNTHETIC_STRUCTURE_ACCEPTED
                        : ObservationStatus.FAIL_CLOSED,
                profile.profileStatus(),
                profile.gateStatus(),
                checks,
                reasons);
    }

    private void binding(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (profile.entity() != rule.entity()
                || profile.entity() != observation.entity()
                || profile.sourceKind() != observation.sourceKind()
                || profile.oracleKind() != observation.oracleKind()
                || profile.pagination().kind() != observation.pagination().kind()
                || profile.pagination().terminalCondition()
                        != observation.pagination().terminalCondition()) {
            reasons.add(FailClosedReason.PROFILE_BINDING_MISMATCH);
        }
        if (observation.oracleAvailability() == OracleAvailability.MISSING) {
            reasons.add(FailClosedReason.ORACLE_MISSING);
        }
        if (observation.authorizationState() == AuthorizationState.MISSING) {
            reasons.add(FailClosedReason.AUTHORIZATION_MISSING);
        }
        if (observation.evidenceClassification() != EvidenceClassification.SYNTHETIC_FIXTURE) {
            reasons.add(FailClosedReason.UNSUPPORTED_EVIDENCE_CLASSIFICATION);
        }
    }

    private static void scopes(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.sourceInstance() == null || observation.sourceInstance().isBlank()) {
            reasons.add(FailClosedReason.SOURCE_INSTANCE_MISSING);
        }
        if (observation.tenantScope() == null || observation.tenantScope().isBlank()) {
            reasons.add(FailClosedReason.TENANT_SCOPE_MISSING);
        }
        if (isReserved(profile, observation.sourceInstance())
                || isReserved(profile, observation.tenantScope())) {
            reasons.add(FailClosedReason.RESERVED_SCOPE_VALUE);
        } else if (isUnmarkedSyntheticScope(observation.sourceInstance())
                || isUnmarkedSyntheticScope(observation.tenantScope())) {
            reasons.add(FailClosedReason.NON_SYNTHETIC_SCOPE_VALUE);
        }
    }

    private static boolean isReserved(
            final CharacterizationProfile profile, final String candidate) {
        return candidate != null
                && profile.scope().forbiddenValues().contains(candidate.toUpperCase(Locale.ROOT));
    }

    private static boolean isUnmarkedSyntheticScope(final String candidate) {
        return candidate != null && !candidate.isBlank() && !candidate.startsWith("SYNTH_");
    }

    private static void structure(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (!profile.rootGrain().acceptedRecordRoots().contains(observation.recordRoot())) {
            reasons.add(FailClosedReason.ROOT_DRIFT);
        }
        if (!profile.expectedPaths().equals(observation.observedPaths())) {
            reasons.add(FailClosedReason.PATH_DRIFT);
        }
        final java.util.TreeSet<String> scopedObservedPaths =
                new java.util.TreeSet<>(observation.observedRecordPaths());
        scopedObservedPaths.addAll(observation.observedConnectionPaths());
        final boolean scopedInventoryCoherent =
                Collections.disjoint(
                                observation.observedRecordPaths(),
                                observation.observedConnectionPaths())
                        && scopedObservedPaths.equals(observation.observedPaths());
        final boolean partitionDrift =
                profile.expectedPaths().equals(observation.observedPaths())
                        && (!profile.pathScopes()
                                        .recordPaths()
                                        .equals(observation.observedRecordPaths())
                                || !profile.pathScopes()
                                        .connectionPaths()
                                        .equals(observation.observedConnectionPaths()));
        if (!scopedInventoryCoherent || partitionDrift) {
            reasons.add(FailClosedReason.FIELD_SCOPE_MISMATCH);
        }
        if (!profile.rootGrain().rootScalarPaths().equals(observation.observedRootScalarPaths())) {
            reasons.add(FailClosedReason.ROOT_SCALAR_ROLE_DRIFT);
        }
        final Map<String, CharacterizationProfile.FieldContract> expectedFields =
                profile.fieldContracts().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        CharacterizationProfile.FieldContract::path,
                                        java.util.function.Function.identity()));
        final Map<String, CharacterizationObservation.FieldObservation> observedFields =
                observation.fieldObservations().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        CharacterizationObservation.FieldObservation::path,
                                        java.util.function.Function.identity()));
        if (!expectedFields.keySet().equals(observedFields.keySet())
                || !observedFields.keySet().equals(observation.observedPaths())) {
            reasons.add(FailClosedReason.PATH_DRIFT);
        } else {
            expectedFields.forEach(
                    (path, expected) -> {
                        final CharacterizationObservation.FieldObservation observed =
                                observedFields.get(path);
                        if (!expected.wireTypes().containsAll(observed.wireTypes())) {
                            reasons.add(FailClosedReason.FIELD_TYPE_MISMATCH);
                        }
                        if (!expected.presenceStates().containsAll(observed.presenceStates())) {
                            reasons.add(FailClosedReason.PRESENCE_MODEL_INCOMPLETE);
                        }
                    });
        }
        if (profile.rootGrain().cardinality() != observation.rootCardinality()) {
            reasons.add(FailClosedReason.CARDINALITY_DRIFT);
        }
        if (!observation.triStateTrackingEnabled()) {
            reasons.add(FailClosedReason.PRESENCE_MODEL_INCOMPLETE);
        }
    }

    private static void filters(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        final Map<String, CharacterizationProfile.FilterContract> expected =
                profile.filters().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        CharacterizationProfile.FilterContract::path,
                                        java.util.function.Function.identity()));
        final Map<String, CharacterizationObservation.FilterObservation> observed =
                observation.filterObservations().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        CharacterizationObservation.FilterObservation::path,
                                        java.util.function.Function.identity()));
        if (!expected.keySet().equals(observed.keySet())) {
            reasons.add(FailClosedReason.FILTER_CONTRACT_DRIFT);
            return;
        }
        expected.forEach(
                (path, contract) -> {
                    final CharacterizationObservation.FilterObservation candidate =
                            observed.get(path);
                    if (contract.role() == FilterRole.REQUIRED && !candidate.present()
                            || candidate.watermark() != contract.watermark()
                            || !Objects.equals(
                                    candidate.booleanValue(), contract.requiredBooleanValue())) {
                        reasons.add(FailClosedReason.FILTER_CONTRACT_DRIFT);
                    }
                });
    }

    private static void relationships(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (!profile.relationshipCandidatePaths()
                        .equals(observation.observedRelationshipCandidatePaths())
                || observation.relationshipInferenceApplied()) {
            reasons.add(FailClosedReason.RELATIONSHIP_INFERENCE_DRIFT);
        }
    }

    private static void absencePolicy(
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.absenceLifecycleInferenceApplied()) {
            reasons.add(FailClosedReason.ABSENCE_POLICY_DRIFT);
        }
    }

    private static void sourceKey(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (!profile.sourceKey().path().equals(observation.observedSourceKeyPath())) {
            reasons.add(FailClosedReason.SOURCE_KEY_PATH_MISMATCH);
        }
        if (!observation.observedSourceKeyTypeTagged()) {
            reasons.add(FailClosedReason.SOURCE_KEY_TAGGING_MISSING);
        }
        if (observation.sourceKeyPresence() == Presence.ABSENT) {
            reasons.add(FailClosedReason.SOURCE_KEY_MISSING);
        } else if (observation.sourceKeyPresence() == Presence.NULL) {
            reasons.add(FailClosedReason.SOURCE_KEY_NULL);
        } else if (!profile.sourceKey().wireTypes().containsAll(observation.sourceKeyWireTypes())) {
            reasons.add(FailClosedReason.SOURCE_KEY_TYPE_MISMATCH);
        } else if (!observation.sourceKeyValueValid()) {
            reasons.add(FailClosedReason.SOURCE_KEY_VALUE_INVALID);
        }
        final CharacterizationObservation.FieldObservation sourceKeyField =
                observation.fieldObservations().stream()
                        .filter(field -> observation.observedSourceKeyPath().equals(field.path()))
                        .findFirst()
                        .orElse(null);
        if (sourceKeyField == null
                || !sourceKeyField.wireTypes().equals(observation.sourceKeyWireTypes())) {
            reasons.add(FailClosedReason.SOURCE_KEY_TYPE_MISMATCH);
        } else {
            final Presence weakestObservedPresence =
                    sourceKeyField.presenceStates().contains(Presence.ABSENT)
                            ? Presence.ABSENT
                            : sourceKeyField.presenceStates().contains(Presence.NULL)
                                    ? Presence.NULL
                                    : Presence.VALUE;
            if (weakestObservedPresence != observation.sourceKeyPresence()) {
                reasons.add(
                        weakestObservedPresence == Presence.ABSENT
                                ? FailClosedReason.SOURCE_KEY_MISSING
                                : weakestObservedPresence == Presence.NULL
                                        ? FailClosedReason.SOURCE_KEY_NULL
                                        : FailClosedReason.SOURCE_KEY_TYPE_MISMATCH);
            }
        }
        if (observation.sourceKeyCount() != observation.physicalRowCount()) {
            reasons.add(FailClosedReason.SOURCE_KEY_MISSING);
        }
        if (observation.sourceKeyCollision()
                || observation.crossTypeTaggedDistinctPairCount()
                        != observation.crossTypeLexemePairCount()) {
            reasons.add(FailClosedReason.SOURCE_KEY_COLLISION);
        }
    }

    private static void terminalAndLimits(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.pagination().pageSize() == 0) {
            reasons.add(FailClosedReason.PAGINATION_SEMANTICS_DRIFT);
        }
        if (!observation.pagination().terminalConditionMet()) {
            reasons.add(FailClosedReason.TERMINALITY_INCOMPLETE);
        }
        if (observation.pagination().pagesObserved()
                != observation.pagination().dataPagesObserved()
                        + observation.pagination().terminalProbeRequests()) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
        if (profile.pagination().kind() == PaginationKind.NUMERIC_PAGE
                        && observation.pagination().terminalProbeRequests() != 1
                || profile.pagination().kind() == PaginationKind.RELAY_CURSOR
                        && observation.pagination().terminalProbeRequests() != 0) {
            reasons.add(FailClosedReason.TERMINALITY_INCOMPLETE);
        }
        if (profile.pagination().kind() == PaginationKind.NUMERIC_PAGE
                && (observation.pagination().cursorRequired()
                        || observation.pagination().cursorPresent()
                        || observation.pagination().cursorCount() != 0
                        || observation.pagination().distinctCursorCount() != 0
                        || observation.pagination().cursorCycleDetected()
                        || observation.pagination().hasNextPageAtLimit())) {
            reasons.add(FailClosedReason.PAGINATION_SEMANTICS_DRIFT);
        }
        if (!profile.pagination().shortPageIsTerminal()
                && observation.pagination().shortNonTerminalPageObserved()
                && !observation.pagination().continuedAfterShortPage()) {
            reasons.add(FailClosedReason.TERMINALITY_INCOMPLETE);
        }
        if (profile.pagination().kind() == PaginationKind.RELAY_CURSOR
                && (observation.pagination().pagesObserved() < 1
                        || observation.pagination().dataPagesObserved() < 1)) {
            reasons.add(FailClosedReason.TERMINALITY_INCOMPLETE);
        }
        final CharacterizationProfile.CharacterizationLimits limits = profile.limits();
        final CharacterizationObservation.ResourceUsage usage = observation.usage();
        if (usage.bytes() > limits.maximumBytes()) {
            reasons.add(FailClosedReason.BYTE_LIMIT_EXCEEDED);
        }
        if (usage.rows() > limits.maximumRows()) {
            reasons.add(FailClosedReason.ROW_LIMIT_EXCEEDED);
        }
        if (usage.pages() > limits.maximumPages()) {
            reasons.add(FailClosedReason.PAGE_LIMIT_EXCEEDED);
        }
        if (profile.pagination().kind() == PaginationKind.NUMERIC_PAGE
                && observation.pagination().pageSize() > profile.pagination().maximumPageSize()) {
            reasons.add(FailClosedReason.PAGE_SIZE_EXCEEDED);
        }
        if (usage.depth() > limits.maximumDepth()) {
            reasons.add(FailClosedReason.DEPTH_LIMIT_EXCEEDED);
        }
        if (usage.paths() > limits.maximumPaths()) {
            reasons.add(FailClosedReason.PATH_LIMIT_EXCEEDED);
        }
        if (usage.nodes() > limits.maximumNodes()) {
            reasons.add(FailClosedReason.NODE_LIMIT_EXCEEDED);
        }
    }

    private static void temporal(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (profile.temporal().translation() != observation.temporal().observedTranslation()) {
            reasons.add(FailClosedReason.TEMPORAL_STATE_MISMATCH);
        }
        if (observation.temporal().timestampState() == TimestampState.INVALID) {
            reasons.add(FailClosedReason.INVALID_TIMESTAMP);
        } else if (!profile.temporal().timestampPaths().isEmpty()
                && observation.temporal().timestampState() != TimestampState.VALID) {
            reasons.add(FailClosedReason.TEMPORAL_STATE_MISMATCH);
        }
        if (profile.temporal().timezoneRequirement() == TimezoneRequirement.EXPLICIT_REQUIRED) {
            if (observation.temporal().timezoneState() != TimezoneState.EXPLICIT_CONFIRMED) {
                reasons.add(FailClosedReason.TIMEZONE_UNCONFIRMED);
            } else if (!Objects.equals(
                    profile.temporal().timezone(), observation.temporal().observedTimezone())) {
                reasons.add(FailClosedReason.TIMEZONE_MISMATCH);
            }
        }
        if (profile.temporal().timezoneRequirement() == TimezoneRequirement.NOT_APPLICABLE
                && (observation.temporal().timestampState() != TimestampState.NOT_APPLICABLE
                        || observation.temporal().timezoneState() != TimezoneState.NOT_APPLICABLE
                        || observation.temporal().observedTimezone() != null)) {
            reasons.add(FailClosedReason.PROFILE_BINDING_MISMATCH);
        }
        if (!profile.temporal().timestampPaths().equals(observation.temporal().observedPaths())) {
            reasons.add(FailClosedReason.TEMPORAL_PRECEDENCE_DRIFT);
            return;
        }
        final boolean validPathWithoutObservedValue =
                observation.temporal().validValuePaths().stream()
                        .anyMatch(
                                validPath ->
                                        observation.fieldObservations().stream()
                                                .filter(field -> validPath.equals(field.path()))
                                                .noneMatch(
                                                        field ->
                                                                field.presenceStates()
                                                                        .contains(Presence.VALUE)));
        if (validPathWithoutObservedValue) {
            reasons.add(FailClosedReason.TEMPORAL_STATE_MISMATCH);
        }
        if (profile.temporal().timestampPaths().isEmpty()) {
            if (!observation.temporal().validValuePaths().isEmpty()
                    || observation.temporal().selectedFreshnessPath() != null
                    || observation.temporal().precedenceApplied()) {
                reasons.add(FailClosedReason.TEMPORAL_PRECEDENCE_DRIFT);
            }
            return;
        }
        if (observation.temporal().timestampState() == TimestampState.VALID
                && observation.temporal().validValuePaths().isEmpty()) {
            reasons.add(FailClosedReason.TEMPORAL_STATE_MISMATCH);
        }
        final String expectedWinner =
                profile.temporal().precedence().stream()
                        .filter(observation.temporal().validValuePaths()::contains)
                        .findFirst()
                        .orElse(null);
        if (!observation.temporal().precedenceApplied()
                || !Objects.equals(
                        expectedWinner, observation.temporal().selectedFreshnessPath())) {
            reasons.add(FailClosedReason.TEMPORAL_PRECEDENCE_DRIFT);
        }
    }

    private static void ordering(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (!Objects.equals(
                        profile.ordering().expression(),
                        observation.pagination().observedOrderingExpression())
                || profile.ordering().role() != OrderingRole.ABSENT
                        && !observation.pagination().orderingRespected()) {
            reasons.add(FailClosedReason.ORDER_PARITY_VIOLATION);
        }
    }

    private static void status(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        final CharacterizationProfile.StatusContract expected = profile.status();
        final CharacterizationObservation.StatusObservation observed = observation.status();
        final CharacterizationObservation.FieldObservation statusField =
                observed.path() == null
                        ? null
                        : observation.fieldObservations().stream()
                                .filter(field -> observed.path().equals(field.path()))
                                .findFirst()
                                .orElse(null);
        final long minimumNonValueRows =
                statusField == null
                        ? 0
                        : statusField.presenceStates().stream()
                                .filter(presence -> presence != Presence.VALUE)
                                .count();
        final long classifiedStatusCount = (long) observed.knownCount() + observed.unknownCount();
        if (classifiedStatusCount > observation.physicalRowCount() - minimumNonValueRows
                || statusField != null
                        && statusField.presenceStates().contains(Presence.VALUE)
                                != (classifiedStatusCount > 0)) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
        if (expected.mode() != observed.mode()
                || !Objects.equals(expected.path(), observed.path())
                || !Objects.equals(
                        expected.policyFingerprint(), observed.appliedPolicyFingerprint())) {
            reasons.add(FailClosedReason.STATUS_SEMANTICS_DRIFT);
            return;
        }
        if (expected.mode() == StatusMode.NOT_APPLICABLE) {
            if (observed.knownCount() != 0
                    || observed.unknownCount() != 0
                    || observed.referenceApplied()
                    || observed.conflict()) {
                reasons.add(FailClosedReason.STATUS_SEMANTICS_DRIFT);
            }
            return;
        }
        if (!observed.referenceApplied()
                || observed.conflict()
                || !expected.unknownValuesAllowed() && observed.unknownCount() > 0) {
            reasons.add(FailClosedReason.STATUS_SEMANTICS_DRIFT);
        }
    }

    private static void counts(
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        final int observedChildCount =
                observation.childObservations().stream()
                        .mapToInt(CharacterizationObservation.ChildObservation::count)
                        .sum();
        final int observedDistinctChildCount =
                observation.childObservations().stream()
                        .mapToInt(CharacterizationObservation.ChildObservation::distinctCount)
                        .sum();
        final boolean incoherentChildPresence =
                observation.childObservations().stream()
                        .anyMatch(
                                child ->
                                        (child.count() == 0) != (child.distinctCount() == 0)
                                                || child.presenceStates().contains(Presence.VALUE)
                                                        != (child.count() > 0)
                                                || child.count()
                                                        > observation.physicalRowCount()
                                                                - child.presenceStates().stream()
                                                                        .filter(
                                                                                presence ->
                                                                                        presence
                                                                                                != Presence
                                                                                                        .VALUE)
                                                                        .count());
        if (observation.distinctSourceKeyCount() > observation.sourceKeyCount()
                || (observation.sourceKeyCount() == 0)
                        != (observation.distinctSourceKeyCount() == 0)
                || observation.crossTypeTaggedDistinctPairCount()
                        > observation.distinctSourceKeyCount()
                || observation.pagination().maximumDistinctSourceKeysOnPage()
                        > observation.distinctSourceKeyCount()
                || profileDoesNotApplyToCounts(observation)
                || (long) observation.distinctSourceKeyCount()
                        > (long) observation.pagination().dataPagesObserved()
                                * observation.pagination().maximumDistinctSourceKeysOnPage()
                || observation.logicalRootCount() > observation.physicalRowCount()
                || (observation.childCount() == 0) != (observation.distinctChildCount() == 0)
                || observation.sourceKeyPresence() == Presence.VALUE
                        && (observation.sourceKeyCount() == 0
                                || observation.physicalRowCount() == 0)
                || observation.pagination().dataPagesObserved() > observation.physicalRowCount()
                || observation.distinctChildCount() > observation.childCount()
                || observedChildCount != observation.childCount()
                || observedDistinctChildCount != observation.distinctChildCount()
                || incoherentChildPresence
                || observation.usage().rows() != observation.physicalRowCount()
                || observation.usage().pages() != observation.pagination().pagesObserved()) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
    }

    private static boolean profileDoesNotApplyToCounts(
            final CharacterizationObservation observation) {
        return observation.sourceKeyWireTypes().size() < 2
                && (observation.crossTypeLexemePairCount() != 0
                        || observation.crossTypeTaggedDistinctPairCount() != 0);
    }

    private static Map<CharacterizationCheck, Boolean> checks(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons,
            final boolean entityRulesMatch) {
        final EnumMap<CharacterizationCheck, Boolean> checks =
                new EnumMap<>(CharacterizationCheck.class);
        checks.put(
                CharacterizationCheck.SYNTHETIC_ORACLE_AVAILABLE,
                observation.oracleAvailability() == OracleAvailability.SYNTHETIC_IN_MEMORY_ONLY);
        checks.put(
                CharacterizationCheck.SYNTHETIC_AUTHORIZATION_CONTEXT,
                observation.authorizationState() == AuthorizationState.SYNTHETIC_TEST_ONLY);
        checks.put(
                CharacterizationCheck.EXPLICIT_SCOPES,
                !containsAny(
                        reasons,
                        FailClosedReason.SOURCE_INSTANCE_MISSING,
                        FailClosedReason.TENANT_SCOPE_MISSING,
                        FailClosedReason.RESERVED_SCOPE_VALUE,
                        FailClosedReason.NON_SYNTHETIC_SCOPE_VALUE));
        checks.put(
                CharacterizationCheck.ROOT_MATCH, !reasons.contains(FailClosedReason.ROOT_DRIFT));
        checks.put(
                CharacterizationCheck.PATH_SET_MATCH,
                !containsAny(
                        reasons,
                        FailClosedReason.PATH_DRIFT,
                        FailClosedReason.FIELD_SCOPE_MISMATCH,
                        FailClosedReason.ROOT_SCALAR_ROLE_DRIFT));
        checks.put(
                CharacterizationCheck.FIELD_TYPES_MATCH,
                !reasons.contains(FailClosedReason.FIELD_TYPE_MISMATCH));
        checks.put(
                CharacterizationCheck.FILTERS_MATCH,
                !reasons.contains(FailClosedReason.FILTER_CONTRACT_DRIFT));
        checks.put(
                CharacterizationCheck.RELATIONSHIPS_PRESERVED,
                !reasons.contains(FailClosedReason.RELATIONSHIP_INFERENCE_DRIFT));
        checks.put(
                CharacterizationCheck.ABSENCE_POLICY_PRESERVED,
                !reasons.contains(FailClosedReason.ABSENCE_POLICY_DRIFT));
        checks.put(
                CharacterizationCheck.SOURCE_KEY_VALID,
                !containsAny(
                        reasons,
                        FailClosedReason.SOURCE_KEY_MISSING,
                        FailClosedReason.SOURCE_KEY_NULL,
                        FailClosedReason.SOURCE_KEY_PATH_MISMATCH,
                        FailClosedReason.SOURCE_KEY_TAGGING_MISSING,
                        FailClosedReason.SOURCE_KEY_TYPE_MISMATCH,
                        FailClosedReason.SOURCE_KEY_VALUE_INVALID,
                        FailClosedReason.SOURCE_KEY_COLLISION));
        checks.put(
                CharacterizationCheck.PRESENCE_TRI_STATE_TRACKED,
                !reasons.contains(FailClosedReason.PRESENCE_MODEL_INCOMPLETE));
        checks.put(
                CharacterizationCheck.TERMINAL_CONDITION_MET,
                !containsAny(
                        reasons,
                        FailClosedReason.PAGINATION_SEMANTICS_DRIFT,
                        FailClosedReason.TERMINALITY_INCOMPLETE));
        checks.put(
                CharacterizationCheck.LIMITS_RESPECTED,
                !containsAny(
                        reasons,
                        FailClosedReason.BYTE_LIMIT_EXCEEDED,
                        FailClosedReason.ROW_LIMIT_EXCEEDED,
                        FailClosedReason.PAGE_LIMIT_EXCEEDED,
                        FailClosedReason.PAGE_SIZE_EXCEEDED,
                        FailClosedReason.GRAPHQL_PAGE_SIZE_EXCEEDED,
                        FailClosedReason.DEPTH_LIMIT_EXCEEDED,
                        FailClosedReason.PATH_LIMIT_EXCEEDED,
                        FailClosedReason.NODE_LIMIT_EXCEEDED));
        checks.put(
                CharacterizationCheck.ORDERING_PARITY_VALID,
                !reasons.contains(FailClosedReason.ORDER_PARITY_VIOLATION));
        checks.put(
                CharacterizationCheck.TEMPORAL_EVIDENCE_VALID,
                !containsAny(
                        reasons,
                        FailClosedReason.INVALID_TIMESTAMP,
                        FailClosedReason.TEMPORAL_STATE_MISMATCH,
                        FailClosedReason.TIMEZONE_UNCONFIRMED,
                        FailClosedReason.TIMEZONE_MISMATCH,
                        FailClosedReason.TEMPORAL_PRECEDENCE_DRIFT));
        checks.put(
                CharacterizationCheck.STATUS_SEMANTICS_VALID,
                !reasons.contains(FailClosedReason.STATUS_SEMANTICS_DRIFT));
        checks.put(
                CharacterizationCheck.CARDINALITY_MATCH,
                !containsAny(
                        reasons,
                        FailClosedReason.CARDINALITY_DRIFT,
                        FailClosedReason.OBSERVATION_COUNT_MISMATCH));
        checks.put(CharacterizationCheck.ENTITY_RULES_MATCH, entityRulesMatch);
        checks.put(
                CharacterizationCheck.SYNTHETIC_EVIDENCE_ONLY,
                observation.evidenceClassification() == EvidenceClassification.SYNTHETIC_FIXTURE);
        return Map.copyOf(checks);
    }

    private static boolean containsAny(
            final EnumSet<FailClosedReason> reasons, final FailClosedReason... candidates) {
        for (final FailClosedReason candidate : candidates) {
            if (reasons.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
