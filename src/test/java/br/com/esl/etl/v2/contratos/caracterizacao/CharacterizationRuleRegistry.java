package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registro fechado de regras pequenas, sem um switch central de comportamento. */
final class CharacterizationRuleRegistry {

    private static final Map<Entity, CharacterizationRule> RULES = createRules();

    private CharacterizationRuleRegistry() {}

    static CharacterizationRule ruleFor(final Entity entity) {
        final CharacterizationRule rule =
                RULES.get(Objects.requireNonNull(entity, "A entidade é obrigatória."));
        if (rule == null) {
            throw new IllegalArgumentException(
                    "Não existe regra de caracterização para a entidade.");
        }
        return rule;
    }

    private static Map<Entity, CharacterizationRule> createRules() {
        final Map<Entity, CharacterizationRule> rules = new EnumMap<>(Entity.class);
        for (final CharacterizationRule rule :
                List.of(
                        new ColetasCharacterizationRule(),
                        new ManifestosCharacterizationRule(),
                        new CotacoesCharacterizationRule(),
                        new UsuariosCharacterizationRule())) {
            if (rules.put(rule.entity(), rule) != null) {
                throw new IllegalStateException("Há regra de entidade duplicada.");
            }
        }
        if (rules.size() != Entity.values().length) {
            throw new IllegalStateException("O registro de regras está incompleto.");
        }
        return Map.copyOf(rules);
    }
}
