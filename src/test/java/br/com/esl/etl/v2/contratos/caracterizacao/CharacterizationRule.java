package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;

import java.util.EnumSet;

/** Extensão pequena e independente das invariantes específicas de uma entidade. */
interface CharacterizationRule {

    Entity entity();

    void validateProfile(CharacterizationProfile profile);

    void evaluate(
            CharacterizationProfile profile,
            CharacterizationObservation observation,
            EnumSet<FailClosedReason> reasons);
}
