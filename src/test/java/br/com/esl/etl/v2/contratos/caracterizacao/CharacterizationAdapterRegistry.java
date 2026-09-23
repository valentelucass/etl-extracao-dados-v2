package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registro fechado que impede um source kind de herdar pressupostos do outro. */
final class CharacterizationAdapterRegistry {

    private static final Map<SourceKind, CharacterizationOracleAdapter> ADAPTERS = create();

    private CharacterizationAdapterRegistry() {}

    static CharacterizationOracleAdapter adapterFor(final SourceKind sourceKind) {
        return Objects.requireNonNull(
                ADAPTERS.get(Objects.requireNonNull(sourceKind, "O source kind é obrigatório.")),
                "O adapter sintético não foi registrado.");
    }

    private static Map<SourceKind, CharacterizationOracleAdapter> create() {
        final Map<SourceKind, CharacterizationOracleAdapter> adapters =
                new EnumMap<>(SourceKind.class);
        for (final CharacterizationOracleAdapter adapter :
                List.of(
                        new InMemoryDataExportCharacterizationAdapter(),
                        new InMemoryGraphQlCharacterizationAdapter())) {
            if (adapters.put(adapter.sourceKind(), adapter) != null) {
                throw new IllegalStateException("Há adapter sintético duplicado.");
            }
        }
        if (adapters.size() != SourceKind.values().length) {
            throw new IllegalStateException("O registro de adapters sintéticos está incompleto.");
        }
        return Map.copyOf(adapters);
    }
}
