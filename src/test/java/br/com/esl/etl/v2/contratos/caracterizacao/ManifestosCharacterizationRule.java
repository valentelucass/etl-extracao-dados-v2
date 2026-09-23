package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExpansionPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.LogicalGrain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PerSemantics;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.StatusMode;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.EnumSet;

/** Regras de raiz e filhos do 6399 sem inferir relação Manifesto--Coleta. */
final class ManifestosCharacterizationRule implements CharacterizationRule {

    @Override
    public Entity entity() {
        return Entity.MANIFESTOS;
    }

    @Override
    public void validateProfile(final CharacterizationProfile profile) {
        if (!"dataexport-6399".equals(profile.contract().contractId())
                || !"2026-09-04.v2-025b.2".equals(profile.contract().contractVersion())
                || !"dataexport-manifestos".equals(profile.contract().documentReference())
                || !"30181093f00f47507d1910d9806a97697118ab671aeae0598d21a4a2726a7cb8"
                        .equals(profile.contract().contractFingerprint())
                || !"ad2e3457a91961372dd29cb96bd6f2cb5fd6f67614d1255303787a8e2c4085c3"
                        .equals(profile.contract().identityFingerprint())
                || !"/sequence_code".equals(profile.sourceKey().path())
                || !profile.sourceKey().wireTypes().equals(java.util.Set.of(WireType.INTEGER))
                || profile.sourceKey().domain() != SourceKeyDomain.POSITIVE_INTEGER
                || !profile.businessAliases().isEmpty()
                || !profile.relationshipCandidatePaths()
                        .equals(java.util.Set.of("/mft_pfs_pck_sequence_code"))
                || profile.childIdentities().size() != 2
                || profile.rootGrain().expansionPolicy()
                        != ExpansionPolicy.PHYSICAL_ROWS_PRESERVE_DISTINCT_CHILDREN
                || profile.rootGrain().logicalGrain() != LogicalGrain.DISTINCT_SCOPED_SEQUENCE_CODE
                || !profile.rootGrain()
                        .acceptedRecordRoots()
                        .equals(java.util.Set.of("/*", "/data/*"))
                || !profile.rootGrain().rootScalarPaths().equals(java.util.Set.of("/mdfe_status"))
                || profile.pagination().perSemantics() != PerSemantics.UNPROVEN
                || profile.expectedPaths().contains("/updated_at")
                || profile.temporal().timezoneRequirement() != TimezoneRequirement.EXPLICIT_REQUIRED
                || profile.temporal().translation()
                        != TemporalTranslation.SOURCE_CIVIL_DATE_UNPROVEN
                || !"sequence_code asc".equals(profile.ordering().expression())
                || profile.ordering().role() != OrderingRole.PARITY_ONLY_NOT_IDENTITY_OR_CURSOR
                || !"7721b88a934b2a58ff19bd7cee53f7bd57023a31528c77143915d72005cd19af"
                        .equals(profile.status().policyFingerprint())
                || !"/status".equals(profile.status().path())
                || profile.status().mode() != StatusMode.KNOWN_PRECEDENCE_UNKNOWN_PRESERVED
                || !"manifestos-v2-026-v03".equals(profile.status().reference())
                || !profile.status().unknownValuesAllowed()
                || !profile.temporal()
                        .precedence()
                        .equals(
                                java.util.List.of(
                                        "/finished_at",
                                        "/closed_at",
                                        "/departured_at",
                                        "/created_at"))
                || !validChildren(profile)
                || profile.filters().stream()
                        .noneMatch(
                                filter ->
                                        "manifests.service_date".equals(filter.path())
                                                && filter.role() == FilterRole.REQUIRED
                                                && !filter.watermark()
                                                && filter.requiredBooleanValue() == null)) {
            throw new IllegalArgumentException(
                    "O perfil Manifestos 6399 diverge do catálogo canônico.");
        }
    }

    private static boolean validChildren(final CharacterizationProfile profile) {
        final CharacterizationProfile.ChildIdentityContract pick =
                profile.childIdentities().stream()
                        .filter(child -> child.kind() == ChildKind.PICK)
                        .findFirst()
                        .orElse(null);
        final CharacterizationProfile.ChildIdentityContract mdfe =
                profile.childIdentities().stream()
                        .filter(child -> child.kind() == ChildKind.MDFE)
                        .findFirst()
                        .orElse(null);
        return pick != null
                && "/mft_pfs_pck_sequence_code".equals(pick.path())
                && pick.wireTypes().equals(java.util.Set.of(WireType.INTEGER))
                && pick.domain() == ChildKeyDomain.POSITIVE_INTEGER
                && pick.attributePaths().isEmpty()
                && mdfe != null
                && "/mft_mfs_key".equals(mdfe.path())
                && mdfe.wireTypes().equals(java.util.Set.of(WireType.STRING))
                && mdfe.domain() == ChildKeyDomain.FIXED_44_DIGIT_STRING
                && mdfe.attributePaths().equals(java.util.Set.of("/mft_mfs_number"));
    }

    @Override
    public void evaluate(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.distinctSourceKeyCount() != observation.logicalRootCount()) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
        if (observation.physicalRowCount() > observation.logicalRootCount()
                && !observation.physicalExpansion()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
        if (observation.physicalRowCount() <= observation.logicalRootCount()
                && observation.physicalExpansion()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
        if (observation.childConflict()) {
            reasons.add(FailClosedReason.CHILD_IDENTITY_CONFLICT);
        }
        validateChildObservations(profile, observation, reasons);
    }

    private static void validateChildObservations(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.childObservations().size() != profile.childIdentities().size()) {
            reasons.add(FailClosedReason.CHILD_IDENTITY_CONFLICT);
            return;
        }
        for (final CharacterizationProfile.ChildIdentityContract contract :
                profile.childIdentities()) {
            final CharacterizationObservation.ChildObservation observed =
                    observation.childObservations().stream()
                            .filter(candidate -> candidate.kind() == contract.kind())
                            .findFirst()
                            .orElse(null);
            final CharacterizationObservation.FieldObservation observedKeyField =
                    observed == null
                            ? null
                            : observation.fieldObservations().stream()
                                    .filter(
                                            field ->
                                                    observed.observedKeyPath().equals(field.path()))
                                    .findFirst()
                                    .orElse(null);
            if (observed == null
                    || !contract.wireTypes().containsAll(observed.wireTypes())
                    || observedKeyField == null
                    || !observedKeyField.wireTypes().equals(observed.wireTypes())
                    || !observedKeyField.presenceStates().containsAll(observed.presenceStates())
                    || !observed.valueDomainValid()
                    || observed.collision()
                    || !observed.presenceStates().equals(EnumSet.allOf(Presence.class))) {
                reasons.add(FailClosedReason.CHILD_IDENTITY_CONFLICT);
            }
            if (observed != null && !contract.path().equals(observed.observedKeyPath())) {
                reasons.add(FailClosedReason.CHILD_KEY_PATH_MISMATCH);
            }
            if (observed != null && !observed.observedTypeTagged()) {
                reasons.add(FailClosedReason.CHILD_KEY_TAGGING_MISSING);
            }
            if (observed == null
                    || !contract.attributePaths().equals(observed.attributePaths())
                    || observed.attributeWithoutKeyCount() > 0
                    || observed.keyWithoutAttributeCount() > 0) {
                reasons.add(FailClosedReason.CHILD_ATTRIBUTE_ASYMMETRY);
            }
        }
    }
}
