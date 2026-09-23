package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProfileStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderEvidence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderType;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.SourceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Perfil de entidade que mantém canais de fonte isolados. */
public record Qfnd02Profile(
        String schemaVersion,
        String profileId,
        Entity entity,
        List<Channel> channels,
        boolean explicitScopeRequired,
        boolean relationshipEnabled,
        boolean sweepEnabled,
        boolean publicationEnabled,
        boolean bootstrapEnabled,
        boolean parityEnabled,
        String statusBranchNicknameState,
        String freshnessPolicy,
        Set<String> knownTerminalStatuses,
        Map<String, String> decisionPolicies,
        Map<String, NumericPolicy> numericPolicies,
        String absencePolicy,
        Limits limits,
        ProfileStatus profileStatus,
        GateStatus gateStatus,
        ProviderEvidence providerEvidence) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_]{2,95}");

    public Qfnd02Profile {
        if (!"V2_012_Q_FND_02_PROFILE_V1".equals(schemaVersion)
                || profileId == null
                || !IDENTIFIER.matcher(profileId).matches()) {
            throw new IllegalArgumentException("Perfil Q-FND-02 inválido.");
        }
        entity = Objects.requireNonNull(entity, "Entidade obrigatória.");
        channels = sortedChannels(channels);
        knownTerminalStatuses = sortedStrings(knownTerminalStatuses);
        decisionPolicies = sortedTextMap(decisionPolicies);
        numericPolicies = sortedNumericPolicies(numericPolicies);
        limits = Objects.requireNonNull(limits, "Limites obrigatórios.");
        profileStatus = Objects.requireNonNull(profileStatus, "Status obrigatório.");
        gateStatus = Objects.requireNonNull(gateStatus, "Gate obrigatório.");
        providerEvidence = Objects.requireNonNull(providerEvidence, "Evidência obrigatória.");
        statusBranchNicknameState = requireText(statusBranchNicknameState);
        freshnessPolicy = requireText(freshnessPolicy);
        absencePolicy = requireText(absencePolicy);
        if (!explicitScopeRequired
                || relationshipEnabled
                || sweepEnabled
                || publicationEnabled
                || bootstrapEnabled
                || parityEnabled
                || profileStatus != ProfileStatus.PREPARED_NOT_EXECUTED
                || gateStatus != GateStatus.ORACLE_REQUIRED
                || providerEvidence != ProviderEvidence.NOT_EXECUTED) {
            throw new IllegalArgumentException("Capacidade externa indevidamente habilitada.");
        }
        if (entity == Entity.FRETES) {
            Qfnd02FretesRule.validate(
                    profileId,
                    channels,
                    statusBranchNicknameState,
                    freshnessPolicy,
                    knownTerminalStatuses,
                    decisionPolicies,
                    numericPolicies,
                    absencePolicy,
                    limits);
        } else if (entity == Entity.LOCALIZACAO_CARGAS) {
            Qfnd02LocalizacaoRule.validate(
                    profileId,
                    channels,
                    statusBranchNicknameState,
                    freshnessPolicy,
                    knownTerminalStatuses,
                    decisionPolicies,
                    numericPolicies,
                    absencePolicy,
                    limits);
        }
    }

    public Channel channel(final String channelId) {
        return channels.stream()
                .filter(channel -> channel.channelId().equals(channelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Canal Q-FND-02 desconhecido."));
    }

    public SourceKey sourceKey() {
        return channels.stream()
                .filter(channel -> channel.sourceProfile() == SourceProfile.DATA_EXPORT)
                .map(Channel::sourceKey)
                .findFirst()
                .orElseThrow();
    }

    public Set<String> expectedPaths() {
        final TreeSet<String> paths = new TreeSet<>();
        channels.forEach(channel -> paths.addAll(channel.expectedPaths()));
        return Collections.unmodifiableSet(paths);
    }

    public boolean rootOrFreshnessAuthority() {
        return channels.stream().anyMatch(Channel::rootOrFreshnessAuthority);
    }

    public boolean publicationBlocked() {
        return channels.stream().allMatch(Channel::publicationBlocked);
    }

    private static List<Channel> sortedChannels(final List<Channel> values) {
        final ArrayList<Channel> result = new ArrayList<>(Objects.requireNonNull(values));
        result.sort(Comparator.comparing(Channel::channelId));
        if (result.isEmpty()
                || result.stream().anyMatch(Objects::isNull)
                || result.stream().map(Channel::channelId).distinct().count() != result.size()) {
            throw new IllegalArgumentException("Canais Q-FND-02 inválidos.");
        }
        return List.copyOf(result);
    }

    private static Set<String> sortedStrings(final Set<String> values) {
        final TreeSet<String> result = new TreeSet<>(Objects.requireNonNull(values));
        if (result.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Conjunto textual inválido.");
        }
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, String> sortedTextMap(final Map<String, String> values) {
        final TreeMap<String, String> sorted = new TreeMap<>(Objects.requireNonNull(values));
        if (sorted.entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey() == null
                                        || entry.getKey().isBlank()
                                        || entry.getValue() == null
                                        || entry.getValue().isBlank())) {
            throw new IllegalArgumentException("Mapa de decisões inválido.");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, NumericPolicy> sortedNumericPolicies(
            final Map<String, NumericPolicy> values) {
        final TreeMap<String, NumericPolicy> sorted = new TreeMap<>(Objects.requireNonNull(values));
        if (sorted.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Políticas numéricas inválidas.");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static String requireText(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Valor textual obrigatório.");
        }
        return value;
    }

    public record Channel(
            String channelId,
            SourceProfile sourceProfile,
            ContractBinding contract,
            SourceKey sourceKey,
            Set<String> expectedPaths,
            Map<String, ProviderType> fieldTypes,
            Map<String, Set<Presence>> presenceByPath,
            boolean syntheticOnly,
            boolean observationOnly,
            boolean publicationBlocked,
            boolean rootOrFreshnessAuthority,
            boolean updatedAtFreshness,
            boolean shortPageIsTerminal,
            boolean completenessProven,
            boolean snapshotProven,
            String filterPolicy,
            String temporalTranslation) {

        public Channel {
            channelId = requireText(channelId);
            sourceProfile = Objects.requireNonNull(sourceProfile);
            contract = Objects.requireNonNull(contract);
            sourceKey = Objects.requireNonNull(sourceKey);
            expectedPaths = sortedStrings(expectedPaths);
            fieldTypes = sortedTypes(fieldTypes);
            presenceByPath = sortedPresence(presenceByPath);
            filterPolicy = requireText(filterPolicy);
            temporalTranslation = requireText(temporalTranslation);
            if (!fieldTypes.keySet().equals(expectedPaths)
                    || !presenceByPath.keySet().equals(expectedPaths)
                    || presenceByPath.values().stream()
                            .anyMatch(
                                    states ->
                                            !states.equals(
                                                    Set.of(
                                                            Presence.ABSENT,
                                                            Presence.NULL,
                                                            Presence.VALUE)))
                    || !publicationBlocked
                    || updatedAtFreshness
                    || shortPageIsTerminal
                    || completenessProven
                    || snapshotProven) {
                throw new IllegalArgumentException("Contrato de canal inseguro ou incompleto.");
            }
        }

        private static Map<String, ProviderType> sortedTypes(
                final Map<String, ProviderType> values) {
            final TreeMap<String, ProviderType> sorted =
                    new TreeMap<>(Objects.requireNonNull(values));
            if (sorted.isEmpty() || sorted.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Tipos de campo inválidos.");
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }

        private static Map<String, Set<Presence>> sortedPresence(
                final Map<String, Set<Presence>> values) {
            final TreeMap<String, Set<Presence>> sorted = new TreeMap<>();
            Objects.requireNonNull(values)
                    .forEach(
                            (path, states) ->
                                    sorted.put(
                                            path,
                                            Collections.unmodifiableSet(
                                                    EnumSet.copyOf(
                                                            Objects.requireNonNull(states)))));
            return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }
    }

    public record ContractBinding(
            String contractId,
            String contractVersion,
            String releaseFingerprint,
            String identityFingerprint) {

        private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

        public ContractBinding {
            contractId = requireText(contractId);
            contractVersion = requireText(contractVersion);
            if (!SHA.matcher(releaseFingerprint).matches()
                    || !SHA.matcher(identityFingerprint).matches()) {
                throw new IllegalArgumentException("Fingerprint contratual inválido.");
            }
        }
    }

    public record SourceKey(String path, ProviderType wireType, boolean typeTagged) {
        public SourceKey {
            path = Objects.requireNonNull(path);
            wireType = Objects.requireNonNull(wireType);
        }
    }

    public record Limits(
            int maximumBytes,
            int maximumRows,
            int maximumPages,
            int maximumPaths,
            int maximumDepth,
            int maximumPageSize,
            int maximumMicrobatch,
            int maximumGraphQlEdges,
            int terminalContinuationProbePages) {
        public Limits {
            if (maximumBytes != 65536
                    || maximumRows != 1000
                    || maximumPages != 100
                    || maximumPaths != 256
                    || maximumDepth != 16
                    || maximumPageSize != 100
                    || maximumMicrobatch != 100
                    || maximumGraphQlEdges != 100
                    || terminalContinuationProbePages != 1) {
                throw new IllegalArgumentException("Limites Q-FND-02 divergentes.");
            }
        }
    }

    public record NumericPolicy(String grammar, int precision, int scale, String invalidPolicy) {
        public NumericPolicy {
            grammar = requireText(grammar);
            invalidPolicy = requireText(invalidPolicy);
            if (precision <= 0 || scale < 0 || scale > precision) {
                throw new IllegalArgumentException("Política numérica inválida.");
            }
        }
    }
}
