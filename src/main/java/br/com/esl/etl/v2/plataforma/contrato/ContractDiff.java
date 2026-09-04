package br.com.esl.etl.v2.plataforma.contrato;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Diff limitado e ordenado de um único componente observado. */
public record ContractDiff(List<ContractChange> changes) {

    public static final int MAXIMUM_CHANGES = 1024;

    public ContractDiff {
        Objects.requireNonNull(changes, "As mudanças de contrato são obrigatórias.");
        if (changes.size() > MAXIMUM_CHANGES) {
            throw new IllegalArgumentException("O diff de contrato excede o limite de mudanças.");
        }
        final List<ContractChange> sorted = new ArrayList<>(changes.size());
        for (final ContractChange change : changes) {
            sorted.add(Objects.requireNonNull(change, "O diff não aceita mudança nula."));
        }
        sorted.sort(
                Comparator.comparing(ContractChange::component)
                        .thenComparing(ContractChange::path)
                        .thenComparing(ContractChange::kind)
                        .thenComparing(ContractChange::severity)
                        .thenComparing(change -> change.signature().version())
                        .thenComparing(change -> change.signature().sha256()));
        changes = List.copyOf(sorted);
    }

    public long breakingCount() {
        return changes.stream()
                .filter(change -> change.severity() == ContractChange.Severity.BREAKING)
                .count();
    }

    public long compatibleCount() {
        return changes.size() - breakingCount();
    }

    @Override
    public String toString() {
        return "ContractDiff[changeCount="
                + changes.size()
                + ", breakingCount="
                + breakingCount()
                + ", compatibleCount="
                + compatibleCount()
                + "]";
    }
}
