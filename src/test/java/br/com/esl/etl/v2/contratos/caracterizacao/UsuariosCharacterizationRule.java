package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.AbsencePolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExpansionPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.LogicalGrain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PaginationKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.EnumSet;
import java.util.Set;

/** Regras do documento estático individual(enabled=true), sem incremental ou DE 9901. */
final class UsuariosCharacterizationRule implements CharacterizationRule {

    @Override
    public Entity entity() {
        return Entity.USUARIOS;
    }

    @Override
    public void validateProfile(final CharacterizationProfile profile) {
        if (!"graphql-individual".equals(profile.contract().contractId())
                || !"2026-08-31.v2-025a.1".equals(profile.contract().contractVersion())
                || !"graphql-users-snapshot".equals(profile.contract().documentReference())
                || !"a650e5e8313e6d45bff326dd8ab15920ba23da8e44e9237f538c4cbce76aca7c"
                        .equals(profile.contract().contractFingerprint())
                || !"8e8ad89777277f3b8bf72b4b63f65d8fa813843380f44da99f0b035bbf788528"
                        .equals(profile.contract().identityFingerprint())
                || !"/node/id".equals(profile.sourceKey().path())
                || !profile.sourceKey()
                        .wireTypes()
                        .equals(Set.of(WireType.INTEGER, WireType.STRING))
                || profile.sourceKey().domain()
                        != SourceKeyDomain.CANONICAL_INTEGER_OR_NON_BLANK_STRING
                || !profile.childIdentities().isEmpty()
                || !profile.relationshipCandidatePaths().isEmpty()
                || profile.pagination().kind() != PaginationKind.RELAY_CURSOR
                || profile.pagination().maximumPageSize() != 20
                || profile.absencePolicy() != AbsencePolicy.NO_DEACTIVATION_BY_ABSENCE
                || profile.ordering().role() != OrderingRole.ABSENT
                || profile.expectedPaths().contains("/node/updatedAt")
                || !profile.temporal().timestampPaths().isEmpty()
                || !profile.temporal().precedence().isEmpty()
                || profile.rootGrain().expansionPolicy() != ExpansionPolicy.ONE_NODE_PER_EDGE
                || profile.rootGrain().logicalGrain() != LogicalGrain.ONE_SCOPED_NODE_PER_EDGE
                || profile.temporal().translation() != TemporalTranslation.ABSENT_NO_TEMPORAL_FIELD
                || profile.filters().stream()
                        .noneMatch(
                                filter ->
                                        "individual.enabled".equals(filter.path())
                                                && filter.role() == FilterRole.REQUIRED
                                                && Boolean.TRUE.equals(
                                                        filter.requiredBooleanValue())
                                                && !filter.watermark())) {
            throw new IllegalArgumentException("O perfil Usuários diverge do catálogo canônico.");
        }
    }

    @Override
    public void evaluate(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        if (observation.pagination().pageSize() > profile.pagination().maximumPageSize()
                || observation.pagination().maximumDistinctSourceKeysOnPage()
                        > observation.pagination().pageSize()) {
            reasons.add(FailClosedReason.GRAPHQL_PAGE_SIZE_EXCEEDED);
        }
        if (observation.pagination().hasNextPageAtLimit()) {
            reasons.add(FailClosedReason.PAGE_LIMIT_EXCEEDED);
            reasons.add(FailClosedReason.TERMINALITY_INCOMPLETE);
        }
        if (observation.pagination().cursorRequired()
                && !observation.pagination().cursorPresent()) {
            reasons.add(FailClosedReason.GRAPHQL_CURSOR_MISSING);
        }
        final int requiredCursorTransitions =
                Math.max(0, observation.pagination().dataPagesObserved() - 1);
        if (observation.pagination().cursorRequired()
                && observation.pagination().cursorCount() < requiredCursorTransitions) {
            reasons.add(FailClosedReason.GRAPHQL_CURSOR_MISSING);
        }
        if (observation.pagination().pagesObserved() > 1
                && !observation.pagination().cursorRequired()) {
            reasons.add(FailClosedReason.GRAPHQL_CURSOR_MISSING);
        }
        if (observation.pagination().cursorCount()
                > observation.pagination().distinctCursorCount()) {
            reasons.add(FailClosedReason.GRAPHQL_CURSOR_REPEATED);
        }
        if (observation.pagination().distinctCursorCount() > observation.pagination().cursorCount()
                || observation.pagination().cursorCount()
                        > observation.pagination().dataPagesObserved()
                || observation.pagination().cursorPresent()
                        != (observation.pagination().cursorCount() > 0)) {
            reasons.add(FailClosedReason.OBSERVATION_COUNT_MISMATCH);
        }
        if (observation.pagination().cursorCycleDetected()) {
            reasons.add(FailClosedReason.GRAPHQL_CURSOR_CYCLE);
        }
        if (observation.physicalExpansion()
                || !observation.childObservations().isEmpty()
                || !reasons.contains(FailClosedReason.SOURCE_KEY_MISSING)
                        && !reasons.contains(FailClosedReason.SOURCE_KEY_NULL)
                        && observation.physicalRowCount() != observation.logicalRootCount()
                || observation.distinctSourceKeyCount() != observation.logicalRootCount()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
    }
}
