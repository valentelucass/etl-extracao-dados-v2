package br.com.esl.etl.v2.modulos.manifestos.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Lote físico limitado a uma página 6399, sem retenção da travessia inteira em memória. */
public final class ManifestoStageBatch {

    private final UUID executionId;
    private final int batchNumber;
    private final List<ManifestoStageRecord> records;
    private final Instant observedAt;

    public ManifestoStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<ManifestoStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        Objects.requireNonNull(records, "Os registros são obrigatórios.");
        final List<ManifestoStageRecord> bounded =
                new ArrayList<>(ManifestoStageRecord.MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[ManifestoStageRecord.MAXIMUM_PAGE_SIZE + 1];
        final Iterator<ManifestoStageRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            if (bounded.size() == ManifestoStageRecord.MAXIMUM_PAGE_SIZE) {
                throw new IllegalArgumentException("O batch excede o limite da página 6399.");
            }
            final ManifestoStageRecord record =
                    Objects.requireNonNull(iterator.next(), "O batch não aceita registro nulo.");
            if (ordinals[record.inputOrdinal()]) {
                throw new IllegalArgumentException("O batch repete ordinal físico.");
            }
            ordinals[record.inputOrdinal()] = true;
            bounded.add(record);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("O batch não pode ser vazio.");
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

    public ManifestoStageRecord recordAt(final int zeroBasedIndex) {
        return records.get(zeroBasedIndex);
    }

    public Instant observedAt() {
        return observedAt;
    }

    @Override
    public String toString() {
        return "ManifestoStageBatch[executionId=<redacted>, batchNumber="
                + batchNumber
                + ", size="
                + records.size()
                + "]";
    }
}
