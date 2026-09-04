package br.com.esl.etl.v2.contratos;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Matriz de classificação sem valores, identificadores ou nomes de contas remotas. */
public record ContractFinancialMatrixEvidence(
        Map<ContractFinancialField, ContractFinancialClassification> classifications) {

    public ContractFinancialMatrixEvidence {
        Objects.requireNonNull(classifications, "As classificações financeiras são obrigatórias.");
        final Map<ContractFinancialField, ContractFinancialClassification> normalized =
                new EnumMap<>(ContractFinancialField.class);
        for (final ContractFinancialField field : ContractFinancialField.values()) {
            final ContractFinancialClassification classification = classifications.get(field);
            if (classification == null) {
                throw new IllegalArgumentException(
                        "Toda classificação financeira precisa ser explicitamente informada.");
            }
            normalized.put(field, classification);
        }
        classifications = Map.copyOf(normalized);
    }

    public static ContractFinancialMatrixEvidence notObserved() {
        final Map<ContractFinancialField, ContractFinancialClassification> classifications =
                new EnumMap<>(ContractFinancialField.class);
        for (final ContractFinancialField field : ContractFinancialField.values()) {
            classifications.put(field, ContractFinancialClassification.ABSENT);
        }
        return new ContractFinancialMatrixEvidence(classifications);
    }

    public boolean allClassificationsAcceptedAsEquivalent() {
        return classifications.values().stream()
                .allMatch(
                        classification ->
                                classification == ContractFinancialClassification.EQUIVALENT);
    }

    @Override
    public String toString() {
        return "ContractFinancialMatrixEvidence[classificationCount="
                + classifications.size()
                + "]";
    }
}
