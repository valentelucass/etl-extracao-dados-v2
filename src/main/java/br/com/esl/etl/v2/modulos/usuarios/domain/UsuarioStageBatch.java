package br.com.esl.etl.v2.modulos.usuarios.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Uma única página GraphQL materializada de forma limitada para um batch JDBC síncrono. */
public final class UsuarioStageBatch {

    public static final int MAXIMUM_PAGE_SIZE = 20;

    private final UUID executionId;
    private final int batchNumber;
    private final List<UsuarioStageRecord> records;
    private final Instant observedAt;

    public UsuarioStageBatch(
            final UUID executionId,
            final int batchNumber,
            final Iterable<UsuarioStageRecord> records,
            final Instant observedAt) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        this.observedAt = Objects.requireNonNull(observedAt, "A observação é obrigatória.");
        if (batchNumber < 1) {
            throw new IllegalArgumentException("O número do batch deve ser positivo.");
        }
        this.batchNumber = batchNumber;
        Objects.requireNonNull(records, "Os registros de Usuários são obrigatórios.");
        final List<UsuarioStageRecord> bounded = new ArrayList<>(MAXIMUM_PAGE_SIZE);
        final boolean[] ordinals = new boolean[MAXIMUM_PAGE_SIZE + 1];
        final Iterator<UsuarioStageRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            if (bounded.size() == MAXIMUM_PAGE_SIZE) {
                throw new IllegalArgumentException("O batch de Usuários excede uma página.");
            }
            final UsuarioStageRecord record =
                    Objects.requireNonNull(
                            iterator.next(), "O batch de Usuários não aceita registro nulo.");
            if (ordinals[record.inputOrdinal()]) {
                throw new IllegalArgumentException("O batch de Usuários repete um ordinal.");
            }
            ordinals[record.inputOrdinal()] = true;
            bounded.add(record);
        }
        if (bounded.isEmpty()) {
            throw new IllegalArgumentException("O batch de Usuários não pode ser vazio.");
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

    public UsuarioStageRecord recordAt(final int zeroBasedIndex) {
        return records.get(zeroBasedIndex);
    }

    public Instant observedAt() {
        return observedAt;
    }

    @Override
    public String toString() {
        return "UsuarioStageBatch[executionId=<redacted>, batchNumber="
                + batchNumber
                + ", size="
                + records.size()
                + "]";
    }
}
