package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderType;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.SourceProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Invariantes LOC-01..LOC-07 aplicáveis à fundação, sem inferir relação. */
final class Qfnd02LocalizacaoRule {

    private static final Set<String> PATHS =
            Set.of(
                    "/corporation_sequence_number",
                    "/type",
                    "/service_at",
                    "/invoices_volumes",
                    "/taxed_weight",
                    "/invoices_value",
                    "/total",
                    "/service_type",
                    "/fit_crn_psn_nickname",
                    "/fit_dpn_delivery_prediction_at",
                    "/fit_dyn_name",
                    "/fit_dyn_drt_nickname",
                    "/fit_fsn_name",
                    "/fit_fln_status",
                    "/fit_fln_cln_nickname",
                    "/fit_o_n_name",
                    "/fit_o_n_drt_nickname");
    private static final Set<String> TERMINALS =
            Set.of("finished", "delivered", "canceled", "cancelled");
    private static final Map<String, String> POLICIES =
            Map.ofEntries(
                    Map.entry(
                            "LOC-01",
                            "CORPORATION_SEQUENCE_INTEGER_SCOPED_ONLY_SEQUENCE_NUMBER_FORBIDDEN"),
                    Map.entry(
                            "LOC-02",
                            "PER_PATH_ABSENT_NULL_VALUE_RAW_TYPED_PARSE_PATH_PROVENANCE_ABSENT_PRESERVES"),
                    Map.entry(
                            "LOC-03",
                            "LOCAL_VOLUME_ZERO_REAL_FREIGHT_FALLBACK_DEFERRED_EXACT_SCOPED_CANDIDATE_AMBIGUITY_BLOCKS"),
                    Map.entry(
                            "LOC-04",
                            "STRICT_NUMERIC_NO_LOCALE_ROUNDING_TRUNCATION_INVALID_OVERFLOW_SCALE_QUARANTINE"),
                    Map.entry(
                            "LOC-05",
                            "SERVICE_AT_ONLY_OLDER_NOOP_EQUAL_IDENTICAL_REPLAY_EQUAL_DIVERGENT_AND_TWO_NULL_DIVERGENT_QUARANTINE"),
                    Map.entry(
                            "LOC-06",
                            "STATUS_TRIM_LOWER_NULL_BLANK_SEM_STATUS_EXPLICIT_TERMINALS_UNKNOWN_NON_TERMINAL"),
                    Map.entry(
                            "LOC-07",
                            "STATUS_BRANCH_NICKNAME_ABSENT_UNSOURCED_LEGACY_NO_SIMILAR_FALLBACK"));

    private Qfnd02LocalizacaoRule() {}

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
        if (!"V2_012_LOCALIZACAO_8656_DATA_EXPORT".equals(profileId)
                || channels.size() != 1
                || !"ABSENT_UNSOURCED_LEGACY".equals(legacyState)
                || !"SERVICE_AT_ONLY_CANDIDATE_TRANSLATION_UNPROVEN_NO_TECHNICAL_TIEBREAKER"
                        .equals(freshnessPolicy)
                || !TERMINALS.equals(terminals)
                || !POLICIES.equals(policies)
                || !expectedNumericPolicies().equals(numericPolicies)
                || !"INCREMENTAL_ABSENCE_NEVER_DEACTIVATES_SWEEP_DISABLED".equals(absencePolicy)
                || limits.maximumMicrobatch() != 100
                || limits.maximumPageSize() != 100) {
            throw new IllegalArgumentException("Perfil Localização divergente.");
        }
        final Qfnd02Profile.Channel channel = channels.get(0);
        if (!"LOCALIZACAO_DATA_EXPORT_8656".equals(channel.channelId())
                || channel.sourceProfile() != SourceProfile.DATA_EXPORT
                || !"dataexport-8656".equals(channel.contract().contractId())
                || !"2026-09-04.v2-025b.1".equals(channel.contract().contractVersion())
                || !"da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24"
                        .equals(channel.contract().releaseFingerprint())
                || !"14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0"
                        .equals(channel.contract().identityFingerprint())
                || !PATHS.equals(channel.expectedPaths())
                || channel.expectedPaths().contains("/sequence_number")
                || !"/corporation_sequence_number".equals(channel.sourceKey().path())
                || channel.sourceKey().wireType() != ProviderType.INTEGER
                || !channel.sourceKey().typeTagged()
                || channel.syntheticOnly()
                || channel.observationOnly()
                || !channel.rootOrFreshnessAuthority()
                || !"FREIGHTS_SERVICE_AT_CANDIDATE_FILTER_TRANSLATION_UNPROVEN"
                        .equals(channel.filterPolicy())
                || !"SERVICE_AT_FILTER_TRANSLATION_UNPROVEN"
                        .equals(channel.temporalTranslation())) {
            throw new IllegalArgumentException("Canal Data Export 8656 divergente.");
        }
        if (channel.fieldTypes().entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey().equals("/corporation_sequence_number")
                                        ? entry.getValue() != ProviderType.INTEGER
                                        : entry.getValue()
                                                != ProviderType.UNVERIFIED_ORACLE_REQUIRED)) {
            throw new IllegalArgumentException("Tipo provider de Localização foi inventado.");
        }
    }

    private static Map<String, Qfnd02Profile.NumericPolicy> expectedNumericPolicies() {
        final Qfnd02Profile.NumericPolicy integer =
                new Qfnd02Profile.NumericPolicy(
                        "ASCII_DIGITS_NO_SIGN_NO_GROUPING_NO_WHITESPACE",
                        10,
                        0,
                        "QUARANTINE_PRESERVE_RAW_NEVER_ZERO_OR_NULL");
        final Qfnd02Profile.NumericPolicy decimal =
                new Qfnd02Profile.NumericPolicy(
                        "ASCII_OPTIONAL_MINUS_DIGITS_OPTIONAL_DOT_FRACTION_NO_GROUPING_NO_WHITESPACE",
                        38,
                        9,
                        "QUARANTINE_PRESERVE_RAW_NO_ROUNDING");
        return Map.of(
                "/invoices_volumes", integer,
                "/taxed_weight", decimal,
                "/invoices_value", decimal,
                "/total", decimal);
    }
}
