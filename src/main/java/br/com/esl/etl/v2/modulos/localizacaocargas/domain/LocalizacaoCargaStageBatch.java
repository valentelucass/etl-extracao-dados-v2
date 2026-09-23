package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Exatamente uma página limitada 8656; nunca acumula o universo da execução. */
public final class LocalizacaoCargaStageBatch {
    private final UUID executionId;
    private final int batchNumber;
    private final List<LocalizacaoCargaStageRecord> records;
    private final Instant observedAt;

    public LocalizacaoCargaStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<LocalizacaoCargaStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        final List<LocalizacaoCargaStageRecord> bounded =
                new ArrayList<>(LocalizacaoCargaStageRecord.MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[LocalizacaoCargaStageRecord.MAXIMUM_PAGE_SIZE + 1];
        for (final LocalizacaoCargaStageRecord item : Objects.requireNonNull(records)) {
            final LocalizacaoCargaStageRecord record = Objects.requireNonNull(item);
            if (bounded.size() == LocalizacaoCargaStageRecord.MAXIMUM_PAGE_SIZE
                    || ordinals[record.inputOrdinal()]) {
                throw new IllegalArgumentException("O microbatch excede 100 ou repete ordinal.");
            }
            ordinals[record.inputOrdinal()] = true;
            bounded.add(record);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("O microbatch não pode ser vazio.");
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

    public LocalizacaoCargaStageRecord recordAt(final int index) {
        return records.get(index);
    }

    public Instant observedAt() {
        return observedAt;
    }
}
