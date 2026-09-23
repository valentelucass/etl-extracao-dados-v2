package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;

/** Boundary de oráculo; esta fatia registra apenas implementações sintéticas em memória. */
interface CharacterizationOracleAdapter {

    SourceKind sourceKind();

    CharacterizationObservation observe(
            CharacterizationProfile profile, CharacterizationFixture.Scenario scenario);
}
