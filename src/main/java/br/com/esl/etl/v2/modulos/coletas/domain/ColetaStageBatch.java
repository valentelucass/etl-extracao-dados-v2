package br.com.esl.etl.v2.modulos.coletas.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um microbatch 6908 limitado, preservado para uma transação JDBC síncrona. Uma página física
 * expansível pode exigir microbatches sequenciais.
 */
public final class ColetaStageBatch {

    private final UUID executionId;
    private final int batchNumber;
    private final List<ColetaStageRecord> records;
    private final Instant observedAt;

    public ColetaStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<ColetaStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O número do batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        Objects.requireNonNull(records, "Os registros de Coletas são obrigatórios.");
        final List<ColetaStageRecord> bounded =
                new ArrayList<>(ColetaStageRecord.MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[ColetaStageRecord.MAXIMUM_PAGE_SIZE + 1];
        final Iterator<ColetaStageRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            if (bounded.size() == ColetaStageRecord.MAXIMUM_PAGE_SIZE) {
                throw new IllegalArgumentException(
                        "O batch de Coletas excede o limite de "
                                + ColetaStageRecord.MAXIMUM_PAGE_SIZE
                                + " registros.");
            }
            final ColetaStageRecord record =
                    Objects.requireNonNull(
                            iterator.next(), "O batch de Coletas não aceita registro nulo.");
            if (ordinals[record.inputOrdinal()]) {
                throw new IllegalArgumentException("O batch de Coletas repete um ordinal.");
            }
            ordinals[record.inputOrdinal()] = true;
            bounded.add(record);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("O batch de Coletas não pode ser vazio.");
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

    public ColetaStageRecord recordAt(final int zeroBasedIndex) {
        return records.get(zeroBasedIndex);
    }

    public Instant observedAt() {
        return observedAt;
    }

    @Override
    public String toString() {
        return "ColetaStageBatch[executionId=<redacted>, batchNumber="
                + batchNumber
                + ", size="
                + records.size()
                + "]";
    }
}
