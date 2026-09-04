package br.com.esl.etl.v2.plataforma.observabilidade;

public record StructuredLogBudgetSnapshot(
        long emittedEvents,
        long emittedPrimaryEvents,
        long emittedBytes,
        long droppedEvents,
        boolean exhaustionSummaryAttempted,
        boolean exhausted) {

    public StructuredLogBudgetSnapshot {
        if (emittedEvents < 0
                || emittedPrimaryEvents < 0
                || emittedPrimaryEvents > emittedEvents
                || emittedEvents > emittedPrimaryEvents + 1
                || emittedBytes < 0
                || droppedEvents < 0
                || exhausted != (droppedEvents > 0)
                || exhaustionSummaryAttempted != exhausted) {
            throw new IllegalArgumentException("O resumo do budget de logs é inválido.");
        }
    }
}
