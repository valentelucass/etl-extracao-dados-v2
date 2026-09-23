package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ScenarioOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Bundle sintético de cenários normalizados de um único perfil. */
public record CharacterizationFixture(
        String schemaVersion, String fixtureMarker, String profileId, List<Scenario> scenarios) {

    private static final Pattern SYNTHETIC_MARKER = Pattern.compile("SYNTH_[A-Z0-9_]{3,127}");
    private static final Pattern PROFILE_ID = Pattern.compile("[A-Z][A-Z0-9_]{2,95}");

    public CharacterizationFixture {
        if (!"V2_012_FIXTURE_V1".equals(schemaVersion)) {
            throw new IllegalArgumentException("A versão da fixture V2-012 é inválida.");
        }
        if (fixtureMarker == null || !SYNTHETIC_MARKER.matcher(fixtureMarker).matches()) {
            throw new IllegalArgumentException("O marcador sintético da fixture é inválido.");
        }
        if (profileId == null || !PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("O perfil da fixture é inválido.");
        }
        final List<Scenario> sorted =
                new ArrayList<>(Objects.requireNonNull(scenarios, "Os cenários são obrigatórios."));
        sorted.sort(Comparator.comparing(Scenario::scenarioId));
        if (sorted.isEmpty()
                || sorted.stream().map(Scenario::scenarioId).distinct().count() != sorted.size()) {
            throw new IllegalArgumentException("A fixture deve possuir cenários únicos.");
        }
        scenarios = List.copyOf(sorted);
    }

    public String fixtureFingerprint() {
        return CharacterizationFingerprint.sha256(this);
    }

    public record Scenario(
            String scenarioId,
            ScenarioOutcome expectedOutcome,
            Set<FailClosedReason> expectedReasons,
            CharacterizationObservation observation) {

        public Scenario {
            if (scenarioId == null || !SYNTHETIC_MARKER.matcher(scenarioId).matches()) {
                throw new IllegalArgumentException("O identificador do cenário é inválido.");
            }
            expectedOutcome =
                    Objects.requireNonNull(expectedOutcome, "O resultado esperado é obrigatório.");
            Objects.requireNonNull(expectedReasons, "Os motivos esperados são obrigatórios.");
            final EnumSet<FailClosedReason> reasons = EnumSet.noneOf(FailClosedReason.class);
            reasons.addAll(expectedReasons);
            expectedReasons = Collections.unmodifiableSet(reasons);
            observation =
                    Objects.requireNonNull(observation, "A observação sintética é obrigatória.");
            if ((expectedOutcome == ScenarioOutcome.SYNTHETIC_STRUCTURE_ACCEPTED)
                    != expectedReasons.isEmpty()) {
                throw new IllegalArgumentException("O resultado esperado diverge dos motivos.");
            }
        }
    }
}
