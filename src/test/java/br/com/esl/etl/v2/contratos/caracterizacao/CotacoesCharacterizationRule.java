package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExpansionPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.LogicalGrain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PerSemantics;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.EnumSet;

/** Regras do 6906, cuja source key não depende de um campo id. */
final class CotacoesCharacterizationRule implements CharacterizationRule {

    @Override
    public Entity entity() {
        return Entity.COTACOES;
    }

    @Override
    public void validateProfile(final CharacterizationProfile profile) {
        if (!"dataexport-6906".equals(profile.contract().contractId())
                || !"2026-09-04.v2-025b.1".equals(profile.contract().contractVersion())
                || !"dataexport-cotacoes".equals(profile.contract().documentReference())
                || !"4bd641aa5ebe1c069776dc7009377d43c4c1d3e26727969667cc7adc01071166"
                        .equals(profile.contract().contractFingerprint())
                || !"355db9b9998d0366378c763b10738c875772d98bc649bf7b3bcceabbfbe345cb"
                        .equals(profile.contract().identityFingerprint())
                || !"/sequence_code".equals(profile.sourceKey().path())
                || !profile.sourceKey().wireTypes().equals(java.util.Set.of(WireType.INTEGER))
                || profile.sourceKey().domain() != SourceKeyDomain.CANONICAL_INTEGER
                || !profile.childIdentities().isEmpty()
                || !profile.relationshipCandidatePaths().isEmpty()
                || profile.expectedPaths().contains("/id")
                || profile.expectedPaths().contains("/updated_at")
                || profile.rootGrain().expansionPolicy()
                        != ExpansionPolicy.NO_CHILD_OR_EXPANSION_PROVEN
                || profile.rootGrain().logicalGrain() != LogicalGrain.DISTINCT_SCOPED_SEQUENCE_CODE
                || profile.pagination().perSemantics() != PerSemantics.UNPROVEN
                || profile.ordering().role() != OrderingRole.PARITY_ONLY_NOT_IDENTITY_OR_CURSOR
                || !"sequence_code asc".equals(profile.ordering().expression())
                || profile.temporal().translation()
                        != TemporalTranslation.SOURCE_CIVIL_DATE_UNPROVEN
                || !profile.temporal()
                        .precedence()
                        .equals(
                                java.util.List.of(
                                        "/qoe_qes_fit_nse_issued_at",
                                        "/qoe_qes_fit_fhe_cte_issued_at",
                                        "/requested_at"))
                || profile.filters().stream()
                        .noneMatch(
                                filter ->
                                        "quotes.requested_at".equals(filter.path())
                                                && filter.role() == FilterRole.REQUIRED
                                                && !filter.watermark()
                                                && filter.requiredBooleanValue() == null)) {
            throw new IllegalArgumentException(
                    "O perfil Cotações 6906 diverge do catálogo canônico.");
        }
    }

    @Override
    public void evaluate(
            final CharacterizationProfile profile,
            final CharacterizationObservation observation,
            final EnumSet<FailClosedReason> reasons) {
        final int replayOrTieRows =
                observation.pagination().exactReplayTieCount()
                        + observation.pagination().divergentTieCount();
        if (observation.physicalExpansion()
                || observation.childCount() != 0
                || observation.distinctChildCount() != 0
                || !observation.childObservations().isEmpty()
                || !reasons.contains(FailClosedReason.SOURCE_KEY_MISSING)
                        && !reasons.contains(FailClosedReason.SOURCE_KEY_NULL)
                        && observation.physicalRowCount()
                                != observation.logicalRootCount() + replayOrTieRows
                || observation.distinctSourceKeyCount() != observation.logicalRootCount()) {
            reasons.add(FailClosedReason.EXPANSION_SEMANTICS_DRIFT);
        }
        if (observation.pagination().divergentTieCount() > 0) {
            reasons.add(FailClosedReason.ORDER_PARITY_VIOLATION);
        }
    }
}
