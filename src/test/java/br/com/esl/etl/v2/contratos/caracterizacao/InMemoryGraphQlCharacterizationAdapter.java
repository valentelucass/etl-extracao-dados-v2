package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;

import java.util.Objects;

/** Adapter GraphQL puramente sintético, sem configuração ESL ou transporte. */
final class InMemoryGraphQlCharacterizationAdapter implements CharacterizationOracleAdapter {

    @Override
    public SourceKind sourceKind() {
        return SourceKind.GRAPHQL;
    }

    @Override
    public CharacterizationObservation observe(
            final CharacterizationProfile profile,
            final CharacterizationFixture.Scenario scenario) {
        final CharacterizationProfile requiredProfile =
                Objects.requireNonNull(profile, "O perfil GraphQL é obrigatório.");
        final CharacterizationObservation observation =
                Objects.requireNonNull(scenario, "O cenário GraphQL é obrigatório.").observation();
        if (requiredProfile.sourceKind() != SourceKind.GRAPHQL
                || requiredProfile.oracleKind() != OracleKind.GRAPHQL_PROVIDER
                || observation.sourceKind() != SourceKind.GRAPHQL
                || observation.oracleKind() != OracleKind.GRAPHQL_PROVIDER) {
            throw new IllegalArgumentException("O cenário não pertence ao adapter GraphQL.");
        }
        return observation;
    }
}
