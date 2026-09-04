package br.com.esl.etl.v2.plataforma.persistencia.staging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote finito e síncrono: a coleção é limitada e não representa a execução inteira. {@code
 * stagedAt} é observação do caller mantida na assinatura; cada registro persiste o relógio do SQL
 * Server após o fencing e o instante técnico não participa da identidade de retry.
 */
public record StagingBatch(
        UUID executionId,
        int batchNumber,
        int maximumBatchSize,
        List<StagingRecord> records,
        Instant stagedAt) {

    public static final int ABSOLUTE_MAXIMUM_BATCH_SIZE = StagingRecord.MAXIMUM_INPUT_ORDINAL;

    public StagingBatch {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        stagedAt = Objects.requireNonNull(stagedAt, "O horário de staging é obrigatório.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O número do lote de staging deve ser positivo.");
        }
        if (maximumBatchSize < 1 || maximumBatchSize > ABSOLUTE_MAXIMUM_BATCH_SIZE) {
            throw new IllegalArgumentException("O limite do lote de staging é inválido.");
        }
        Objects.requireNonNull(records, "Os registros do lote são obrigatórios.");
        final List<StagingRecord> boundedRecords = new ArrayList<>(Math.min(maximumBatchSize, 256));
        final boolean[] observedOrdinals = new boolean[StagingRecord.MAXIMUM_INPUT_ORDINAL + 1];
        final Iterator<StagingRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            if (boundedRecords.size() == maximumBatchSize) {
                throw new IllegalArgumentException(
                        "O lote deve ser não vazio e respeitar seu limite explícito.");
            }
            final StagingRecord record =
                    Objects.requireNonNull(
                            iterator.next(), "O lote de staging não pode conter registro nulo.");
            if (observedOrdinals[record.inputOrdinal()]) {
                throw new IllegalArgumentException(
                        "O lote de staging não pode repetir um ordinal de entrada.");
            }
            observedOrdinals[record.inputOrdinal()] = true;
            boundedRecords.add(record);
        }
        if (boundedRecords.isEmpty()) {
            throw new IllegalArgumentException(
                    "O lote deve ser não vazio e respeitar seu limite explícito.");
        }
        records = List.copyOf(boundedRecords);
    }
}
