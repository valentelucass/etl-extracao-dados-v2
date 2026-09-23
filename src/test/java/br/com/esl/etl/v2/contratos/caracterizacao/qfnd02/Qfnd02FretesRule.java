package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderType;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.SourceProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Invariantes FRE-01..FRE-07 aplicáveis à fundação, sem executar fornecedor. */
final class Qfnd02FretesRule {

    private static final Set<String> DATA_EXPORT_PATHS =
            Set.of(
                    "/id",
                    "/updated_at",
                    "/reference_number",
                    "/fit_p_m_pck_sequence_code",
                    "/corporation_sequence_number",
                    "/finished_at",
                    "/fit_dpn_performance_finished_at");
    private static final Set<String> SIDECAR_PATHS =
            Set.of(
                    "/freight/edges/node/id",
                    "/freight/edges/node/accountingCreditId",
                    "/freight/edges/node/accountingCreditInstallmentId",
                    "/freight/edges/node/referenceNumber",
                    "/freight/edges/node/cte/key",
                    "/freight/edges/node/total",
                    "/freight/edges/node/corporationSequenceNumber",
                    "/freight/edges/node/pickItemId",
                    "/freight/pageInfo/hasNextPage",
                    "/freight/pageInfo/endCursor");
    private static final Set<String> TERMINALS =
            Set.of("finished", "done", "canceled", "cancelled");
    private static final Map<String, String> POLICIES =
            Map.ofEntries(
                    Map.entry(
                            "FRE-01",
                            "ID_INTEGER_SCOPED_ONLY_CORPORATION_SEQUENCE_ALIAS_ZERO_TO_MANY"),
                    Map.entry(
                            "FRE-02",
                            String.join(
                                    "",
                                    "FIRST_PRESENT_VALID_CTE_CREATED_CTE_ISSUED_CRIADO_SERVICO_",
                                    "INVALID_BLOCKS_OLDER_NOOP_EQUAL_IDENTICAL_REPLAY_",
                                    "EQUAL_DIVERGENT_QUARANTINE")),
                    Map.entry(
                            "FRE-03",
                            "PERFORMANCE_OFFICIAL_THEN_FINISHED_FALLBACK_WITH_PROVENANCE_AMBIGUOUS_QUARANTINE"),
                    Map.entry(
                            "FRE-04",
                            "SIDECAR_INDEPENDENT_BOUNDED_OBSERVATION_ONLY_RELATION_CANDIDATES_UNRESOLVED"),
                    Map.entry(
                            "FRE-05",
                            "SHORT_PAGE_NOT_TERMINAL_COMPLETENESS_UNPROVEN_ABSENCE_NO_DEACTIVATION"),
                    Map.entry(
                            "FRE-06",
                            "FINANCIAL_RAW_TYPED_NO_CURRENCY_UNIT_ROUNDING_OR_ARITHMETIC_INFERENCE"),
                    Map.entry("FRE-07", "AMBIGUOUS_TEMPORAL_QUARANTINE_NO_HEURISTIC"));

    private Qfnd02FretesRule() {}

    static void validate(
            final String profileId,
            final List<Qfnd02Profile.Channel> channels,
            final String legacyState,
            final String freshnessPolicy,
            final Set<String> terminals,
            final Map<String, String> policies,
            final Map<String, Qfnd02Profile.NumericPolicy> numericPolicies,
            final String absencePolicy,
            final Qfnd02Profile.Limits limits) {
        if (!"V2_012_FRETES_6389".equals(profileId)
                || channels.size() != 2
                || !"NOT_APPLICABLE".equals(legacyState)
                || !"DECISION_ONLY_CTE_CREATED_AT_CTE_ISSUED_AT_CRIADO_EM_SERVICO_EM_UPDATED_AT_FORBIDDEN"
                        .equals(freshnessPolicy)
                || !TERMINALS.equals(terminals)
                || !POLICIES.equals(policies)
                || !numericPolicies.isEmpty()
                || !"INCREMENTAL_ABSENCE_NEVER_DEACTIVATES_SWEEP_DISABLED".equals(absencePolicy)
                || limits.maximumMicrobatch() != 100
                || limits.maximumGraphQlEdges() != 100
                || limits.maximumPageSize() != 100) {
            throw new IllegalArgumentException("Perfil Fretes divergente.");
        }
        final Qfnd02Profile.Channel dataExport = find(channels, "FRETES_DATA_EXPORT_6389");
        final Qfnd02Profile.Channel sidecar = find(channels, "FRETES_GRAPHQL_SIDECAR");
        if (dataExport.sourceProfile() != SourceProfile.DATA_EXPORT
                || !"dataexport-6389".equals(dataExport.contract().contractId())
                || !"2026-08-31.v2-025a.1".equals(dataExport.contract().contractVersion())
                || !"23aef4e4e03488d291d3990bdba813800e3ebb8c8be18e766a788daf9741ec15"
                        .equals(dataExport.contract().releaseFingerprint())
                || !"4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723"
                        .equals(dataExport.contract().identityFingerprint())
                || !DATA_EXPORT_PATHS.equals(dataExport.expectedPaths())
                || !"/id".equals(dataExport.sourceKey().path())
                || dataExport.sourceKey().wireType() != ProviderType.INTEGER
                || !dataExport.sourceKey().typeTagged()
                || dataExport.syntheticOnly()
                || dataExport.observationOnly()
                || !dataExport.rootOrFreshnessAuthority()
                || !"FREIGHTS_SERVICE_AT_CANDIDATE_FILTER_TRANSLATION_UNPROVEN"
                        .equals(dataExport.filterPolicy())
                || !"PROVIDER_BOUNDARY_AND_FRESHNESS_UNVERIFIED"
                        .equals(dataExport.temporalTranslation())) {
            throw new IllegalArgumentException("Canal Data Export 6389 divergente.");
        }
        if (dataExport.fieldTypes().entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey().equals("/id")
                                        ? entry.getValue() != ProviderType.INTEGER
                                        : entry.getValue()
                                                != ProviderType.UNVERIFIED_ORACLE_REQUIRED)) {
            throw new IllegalArgumentException("Tipo provider de Fretes foi inventado.");
        }
        if (sidecar.sourceProfile() != SourceProfile.GRAPHQL_SIDECAR
                || !"FREIGHTS_TRANSITIONAL_SIDECAR".equals(sidecar.contract().contractId())
                || !"2026-09-06.freights-transitional-sidecar.1"
                        .equals(sidecar.contract().contractVersion())
                || !"b6a8ea2744956f1ef5a2407abd57609c8a353930952a5db49996eff5b3f0c318"
                        .equals(sidecar.contract().releaseFingerprint())
                || !"4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723"
                        .equals(sidecar.contract().identityFingerprint())
                || !SIDECAR_PATHS.equals(sidecar.expectedPaths())
                || !sidecar.sourceKey().path().isEmpty()
                || sidecar.sourceKey().typeTagged()
                || !sidecar.syntheticOnly()
                || !sidecar.observationOnly()
                || sidecar.rootOrFreshnessAuthority()
                || sidecar.fieldTypes().values().stream()
                        .anyMatch(type -> type != ProviderType.UNVERIFIED_ORACLE_REQUIRED)
                || !"NO_ROOT_FILTER_OBSERVATION_ONLY".equals(sidecar.filterPolicy())
                || !"NO_TEMPORAL_AUTHORITY".equals(sidecar.temporalTranslation())) {
            throw new IllegalArgumentException("Sidecar GraphQL deixou de ser observacional.");
        }
    }

    private static Qfnd02Profile.Channel find(
            final List<Qfnd02Profile.Channel> channels, final String id) {
        return channels.stream()
                .filter(channel -> channel.channelId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Canal de Fretes ausente."));
    }
}
