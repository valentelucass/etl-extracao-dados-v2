package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Carrega exatamente os quatro perfis aprovados, uma entidade por registro. */
public final class CharacterizationProfileRegistry {

    public static final List<String> PROFILE_RESOURCES =
            List.of(
                    "/contracts/v2-012/profiles/coletas-6908.profile.json",
                    "/contracts/v2-012/profiles/manifestos-6399.profile.json",
                    "/contracts/v2-012/profiles/cotacoes-6906.profile.json",
                    "/contracts/v2-012/profiles/usuarios-individual.profile.json");

    private final List<CharacterizationProfile> profiles;
    private final Map<Entity, CharacterizationProfile> byEntity;

    private CharacterizationProfileRegistry(final List<CharacterizationProfile> values) {
        final List<CharacterizationProfile> sorted =
                new ArrayList<>(Objects.requireNonNull(values, "Os perfis são obrigatórios."));
        sorted.sort(Comparator.comparing(CharacterizationProfile::entity));
        final Map<Entity, CharacterizationProfile> indexed = new EnumMap<>(Entity.class);
        final Set<String> profileIds = new HashSet<>();
        for (final CharacterizationProfile profile : sorted) {
            final CharacterizationRule rule =
                    CharacterizationRuleRegistry.ruleFor(profile.entity());
            rule.validateProfile(profile);
            if (indexed.put(profile.entity(), profile) != null
                    || !profileIds.add(profile.profileId())) {
                throw new IllegalArgumentException("Há perfil ou entidade duplicada.");
            }
        }
        if (sorted.size() != Entity.values().length || indexed.size() != Entity.values().length) {
            throw new IllegalArgumentException("O registro deve conter exatamente quatro perfis.");
        }
        profiles = List.copyOf(sorted);
        byEntity = Map.copyOf(indexed);
    }

    public static CharacterizationProfileRegistry loadDefault() {
        final CharacterizationProfileLoader loader = new CharacterizationProfileLoader();
        return new CharacterizationProfileRegistry(
                PROFILE_RESOURCES.stream().map(loader::loadResource).toList());
    }

    static CharacterizationProfileRegistry of(final List<CharacterizationProfile> profiles) {
        return new CharacterizationProfileRegistry(profiles);
    }

    public List<CharacterizationProfile> profiles() {
        return profiles;
    }

    public CharacterizationProfile profile(final Entity entity) {
        return Objects.requireNonNull(
                byEntity.get(Objects.requireNonNull(entity, "A entidade é obrigatória.")),
                "O perfil da entidade não foi registrado.");
    }

    public CharacterizationEvaluator evaluator(final Entity entity) {
        return new CharacterizationEvaluator(CharacterizationRuleRegistry.ruleFor(entity));
    }
}
