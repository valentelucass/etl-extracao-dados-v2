package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExpansionPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.LogicalGrain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PerSemantics;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.StatusMode;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.EnumSet;

/** Regras exclusivas de Coletas 6908, inclusive expansão física por ID lógico. */
final class ColetasCharacterizationRule implements CharacterizationRule {

    @Override
    public Entity entity() {
        return Entity.COLETAS;
    }

    @Override
    public void validateProfile(final CharacterizationProfile profile) {
        if (!"dataexport-6908".equals(profile.contract().contractId())
                || !"2026-08-31.v2-025a.1".equals(profile.contract().contractVersion())
                || !"dataexport-coletas".equals(profile.contract().documentReference())
                || !"052d84c37d9c55ae2b2b4891eb4771c68158ee7365402a1388cc68256c67cc59"
                        .equals(profile.contract().contractFingerprint())
                || !"0ca2cd5ce2573d0bdec31d9bc141887f2d58bc79cd67d289981034a4865705e1"
                        .equals(profile.contract().identityFingerprint())
                || !"/id".equals(profile.sourceKey().path())
                || !profile.sourceKey().wireTypes().equals(java.util.Set.of(WireType.INTEGER))
                || profile.sourceKey().domain() != SourceKeyDomain.CANONICAL_INTEGER
                || profile.businessAliases().size() != 1
                || !profile.childIdentities().isEmpty()
                || !profile.relationshipCandidatePaths()
                        .equals(
                                java.util.Set.of(
                                        "/manifesto",
                                        "/pick_item_id",
                                        "/fit_p_m_pck_sequence_code",
                                        "/frete",
                                        "/pck_mik_mft_sequence_code"))
                || !"/sequence_code".equals(profile.businessAliases().get(0).path())
                || profile.rootGrain().expansionPolicy()
                        != ExpansionPolicy.PHYSICAL_ROWS_MAY_EXCEED_LOGICAL_ROOTS
                || profile.rootGrain().logicalGrain() != LogicalGrain.DISTINCT_SCOPED_ID
                || profile.pagination().perSemantics()
                        != PerSemantics.DISTINCT_SOURCE_KEYS_WITH_PHYSICAL_EXPANSION
                || profile.temporal().timezoneRequirement() != TimezoneRequirement.EXPLICIT_REQUIRED
                || profile.temporal().translation() != TemporalTranslation.BOUNDARIES_UNPROVEN
                || !"sequence_code asc".equals(profile.ordering().expression())
                || profile.ordering().role() != OrderingRole.PARITY_ONLY_NOT_IDENTITY_OR_CURSOR
                || !"077c4e806c3174dd4909e94e99a72847ba6b76868fce492561f9bb307a85422c"
                        .equals(profile.status().policyFingerprint())
                || !"/status".equals(profile.status().path())
                || profile.status().mode() != StatusMode.CLOSED_CATALOG
                || !"coletas-status-v1".equals(profile.status().reference())
                || profile.status().unknownValuesAllowed()
                || !profile.temporal()
                        .precedence()
                        .equals(
                                java.util.List.of(
                                        "/status_updated_at",
                                        "/finish_date",
                                        "/service_date",
                                        "/request_date"))
                || !hasFilter(profile, "picks.request_date", FilterRole.REQUIRED)
                || !hasFilter(profile, "scopes.by_updated_at", FilterRole.COMPLEMENTARY)) {
            throw new IllegalArgumentException(
                    "O perfil Coletas 6908 diverge do catálogo canônico.");
        }
    }

    @Override
    public void evaluate(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.distinctSourceKeyCount() != observation.logicalRootCount()) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
        if (observation.pagination().maximumDistinctSourceKeysOnPage()
                > observation.pagination().pageSize()) {
            reasons.add(FailClosedReason.LOGICAL_ENTITY_LIMIT_EXCEEDED);
        }
        if (observation.physicalRowCount() > observation.logicalRootCount()
                && !observation.physicalExpansion()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
        if (observation.physicalRowCount() <= observation.logicalRootCount()
                && observation.physicalExpansion()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
        if (!observation.childObservations().isEmpty()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
    }

    private static boolean hasFilter(
            final CharacterizationProfile profile, final String path, final FilterRole role) {
        return profile.filters().stream()
                .anyMatch(
                        filter ->
                                filter.path().equals(path)
                                        && filter.role() == role
                                        && !filter.watermark()
                                        && filter.requiredBooleanValue() == null);
    }
}
