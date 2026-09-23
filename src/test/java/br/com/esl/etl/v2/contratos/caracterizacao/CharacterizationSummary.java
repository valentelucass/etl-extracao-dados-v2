package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FoundationOutcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ObservationStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ProfileStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Índice agregado que exige as quatro entidades e nunca converte sucesso parcial em sucesso. */
public record CharacterizationSummary(
        String schemaVersion, List<CharacterizationResult> results, FoundationOutcome outcome) {

    public CharacterizationSummary {
        if (!"V2_012_SUMMARY_V1".equals(schemaVersion)) {
            throw new IllegalArgumentException("A versão do summary V2-012 é inválida.");
        }
        final List<CharacterizationResult> sorted =
                new ArrayList<>(Objects.requireNonNull(results, "Os resultados são obrigatórios."));
        sorted.sort(Comparator.comparing(CharacterizationResult::entity));
        results = List.copyOf(sorted);
        outcome = Objects.requireNonNull(outcome, "O resultado agregado é obrigatório.");
        if (outcome != deriveOutcome(results)) {
            throw new IllegalArgumentException(
                    "O resultado agregado não corresponde às entidades.");
        }
    }

    public static CharacterizationSummary from(final List<CharacterizationResult> results) {
        final List<CharacterizationResult> required =
                List.copyOf(Objects.requireNonNull(results, "Os resultados são obrigatórios."));
        return new CharacterizationSummary("V2_012_SUMMARY_V1", required, deriveOutcome(required));
    }

    public int profileCount() {
        return results.size();
    }

    private static FoundationOutcome deriveOutcome(final List<CharacterizationResult> results) {
        final EnumSet<Entity> entities = EnumSet.noneOf(Entity.class);
        final boolean allEligible =
                results.stream()
                        .allMatch(
                                result ->
                                        entities.add(result.entity())
                                                && result.observationStatus()
                                                        == ObservationStatus
                                                                .SYNTHETIC_STRUCTURE_ACCEPTED
                                                && result.profileStatus()
                                                        == ProfileStatus.PREPARED_NOT_EXECUTED
                                                && result.gateStatus()
                                                        == GateStatus.ORACLE_REQUIRED);
        return results.size() == Entity.values().length
                        && entities.size() == Entity.values().length
                        && allEligible
                ? FoundationOutcome.FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES
                : FoundationOutcome.FOUNDATION_FAIL_CLOSED;
    }
}
