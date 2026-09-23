package br.com.esl.etl.v2.modulos.cotacoes.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Uma única página 6906 em voo, limitada a mil observações. */
public final class CotacaoStageBatch {
    private final UUID executionId;
    private final int batchNumber;
    private final List<CotacaoStageRecord> records;
    private final Instant observedAt;

    public CotacaoStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<CotacaoStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O número do batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        final List<CotacaoStageRecord> bounded =
                new ArrayList<>(CotacaoStageRecord.MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[CotacaoStageRecord.MAXIMUM_PAGE_SIZE + 1];
        for (final CotacaoStageRecord record :
                Objects.requireNonNull(records, "Os registros são obrigatórios.")) {
            final CotacaoStageRecord required =
                    Objects.requireNonNull(record, "Registro nulo não é aceito.");
            if (bounded.size() == CotacaoStageRecord.MAXIMUM_PAGE_SIZE
                    || ordinals[required.inputOrdinal()]) {
                throw new IllegalArgumentException(
                        "A página de Cotações excede ou repete ordinal.");
            }
            ordinals[required.inputOrdinal()] = true;
            bounded.add(required);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("A página de Cotações não pode ser vazia.");
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

    public CotacaoStageRecord recordAt(final int index) {
        return records.get(index);
    }

    public Instant observedAt() {
        return observedAt;
    }
}
