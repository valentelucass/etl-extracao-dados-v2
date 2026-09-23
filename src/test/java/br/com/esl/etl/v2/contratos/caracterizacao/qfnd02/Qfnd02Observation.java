package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.EvidenceClassification;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderEvidence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderType;

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

/** Observação estritamente sintética usada para exercer o binding local. */
public record Qfnd02Observation(
        String schemaVersion,
        String fixtureMarker,
        String profileId,
        EvidenceClassification evidenceClassification,
        ProviderEvidence providerEvidence,
        List<ChannelObservation> channels,
        List<Scenario> scenarios) {

    public Qfnd02Observation {
        if (!"V2_012_Q_FND_02_FIXTURE_V1".equals(schemaVersion)
                || fixtureMarker == null
                || !fixtureMarker.matches("SYNTH_[A-Z0-9_]{3,127}")
                || profileId == null
                || profileId.isBlank()
                || evidenceClassification != EvidenceClassification.SYNTHETIC_FIXTURE
                || providerEvidence != ProviderEvidence.NOT_EXECUTED) {
            throw new IllegalArgumentException("Fixture Q-FND-02 inválida.");
        }
        final ArrayList<ChannelObservation> sorted =
                new ArrayList<>(Objects.requireNonNull(channels));
        sorted.sort(Comparator.comparing(ChannelObservation::channelId));
        if (sorted.isEmpty()
                || sorted.stream().map(ChannelObservation::channelId).distinct().count()
                        != sorted.size()) {
            throw new IllegalArgumentException("Canais observados inválidos.");
        }
        channels = List.copyOf(sorted);
        scenarios = List.copyOf(Objects.requireNonNull(scenarios));
        if (scenarios.isEmpty()
                || scenarios.stream().map(Scenario::scenarioId).distinct().count()
                        != scenarios.size()) {
            throw new IllegalArgumentException("Cenários sintéticos inválidos.");
        }
    }

    public record ChannelObservation(
            String channelId,
            Set<String> observedPaths,
            Map<String, ProviderType> observedTypes,
            Map<String, Set<Presence>> presenceByPath,
            String sourceKeyPath,
            boolean sourceKeyTypeTagged,
            boolean shortPageIsTerminal,
            boolean completenessProven,
            boolean snapshotProven,
            boolean relationshipEnabled,
            boolean publicationEnabled,
            boolean sweepEnabled,
            boolean rootOrFreshnessAuthority) {

        public ChannelObservation {
            if (channelId == null || channelId.isBlank() || sourceKeyPath == null) {
                throw new IllegalArgumentException("Canal observado inválido.");
            }
            observedPaths =
                    Collections.unmodifiableSet(
                            new TreeSet<>(Objects.requireNonNull(observedPaths)));
            final TreeMap<String, ProviderType> types =
                    new TreeMap<>(Objects.requireNonNull(observedTypes));
            observedTypes = Collections.unmodifiableMap(new LinkedHashMap<>(types));
            final TreeMap<String, Set<Presence>> presences = new TreeMap<>();
            Objects.requireNonNull(presenceByPath)
                    .forEach(
                            (path, states) ->
                                    presences.put(
                                            path,
                                            Collections.unmodifiableSet(
                                                    EnumSet.copyOf(
                                                            Objects.requireNonNull(states)))));
            presenceByPath = Collections.unmodifiableMap(new LinkedHashMap<>(presences));
        }
    }

    public record Scenario(String scenarioId, String mutation, String expectedReason) {
        public Scenario {
            if (scenarioId == null
                    || !scenarioId.matches("SYNTH_[A-Z0-9_]{3,127}")
                    || mutation == null
                    || mutation.isBlank()
                    || expectedReason == null
                    || expectedReason.isBlank()) {
                throw new IllegalArgumentException("Cenário negativo inválido.");
            }
        }
    }
}
