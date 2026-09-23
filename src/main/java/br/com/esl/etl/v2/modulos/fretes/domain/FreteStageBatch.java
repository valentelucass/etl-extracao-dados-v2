package br.com.esl.etl.v2.modulos.fretes.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Uma página física 6389 em voo, nunca o universo da execução. */
public final class FreteStageBatch {
    private final UUID executionId;
    private final int batchNumber;
    private final List<FreteStageRecord> records;
    private final Instant observedAt;

    public FreteStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<FreteStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        final List<FreteStageRecord> bounded = new ArrayList<>(FreteStageRecord.MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[FreteStageRecord.MAXIMUM_PAGE_SIZE + 1];
        for (final FreteStageRecord record :
                Objects.requireNonNull(records, "Os registros são obrigatórios.")) {
            final FreteStageRecord required =
                    Objects.requireNonNull(record, "Registro nulo não é aceito.");
            if (bounded.size() == FreteStageRecord.MAXIMUM_PAGE_SIZE
                    || ordinals[required.inputOrdinal()]) {
                throw new IllegalArgumentException(
                        "A página 6389 excede o limite ou repete ordinal.");
            }
            ordinals[required.inputOrdinal()] = true;
            bounded.add(required);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("A página 6389 não pode ser vazia.");
        }
        this.records = List.copyOf(bounded);
    }

    public UUID executionId() {
        return executionId;
    }

    public int batchNumber() {
        return batchNumber;
    }

    public int size() {
        return records.size();
    }

    public FreteStageRecord recordAt(final int index) {
        return records.get(index);
    }

    public Instant observedAt() {
        return observedAt;
    }
}
