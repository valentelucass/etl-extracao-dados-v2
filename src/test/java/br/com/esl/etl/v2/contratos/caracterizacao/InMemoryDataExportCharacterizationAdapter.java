package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;

import java.util.Objects;

/** Adapter Data Export puramente sintético, sem configuração ESL ou transporte. */
final class InMemoryDataExportCharacterizationAdapter implements CharacterizationOracleAdapter {

    @Override
    public SourceKind sourceKind() {
        return SourceKind.DATA_EXPORT;
    }

    @Override
    public CharacterizationObservation observe(
            final CharacterizationProfile profile,
            final CharacterizationFixture.Scenario scenario) {
        final CharacterizationProfile requiredProfile =
                Objects.requireNonNull(profile, "O perfil Data Export é obrigatório.");
        final CharacterizationObservation observation =
                Objects.requireNonNull(scenario, "O cenário Data Export é obrigatório.")
                        .observation();
        if (requiredProfile.sourceKind() != SourceKind.DATA_EXPORT
                || requiredProfile.oracleKind() != OracleKind.DATA_EXPORT_PROVIDER
                || observation.sourceKind() != SourceKind.DATA_EXPORT
                || observation.oracleKind() != OracleKind.DATA_EXPORT_PROVIDER) {
            throw new IllegalArgumentException("O cenário não pertence ao adapter Data Export.");
        }
        return observation;
    }
}
