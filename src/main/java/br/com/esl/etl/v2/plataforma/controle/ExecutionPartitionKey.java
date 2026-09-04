package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Instant;
import java.util.Objects;

/**
 * Chave semântica canônica de uma partição de execução.
 *
 * <p>Os instantes são normalizados para UTC na borda. A zona e a tradução da fonte pertencem ao
 * contrato/fingerprint da ocorrência, nunca ao relógio da máquina.
 */
public record ExecutionPartitionKey(
        String environment,
        String sourceInstance,
        String tenantScope,
        String entity,
        ExecutionMode mode,
        Instant partitionStart,
        Instant partitionEndExclusive) {

    public ExecutionPartitionKey {
        environment = required(environment, 32, "O ambiente é obrigatório.");
        sourceInstance = required(sourceInstance, 128, "A instância da fonte é obrigatória.");
        tenantScope = required(tenantScope, 128, "O escopo de tenant é obrigatório.");
        entity = required(entity, 128, "A entidade é obrigatória.");
        mode = Objects.requireNonNull(mode, "O modo é obrigatório.");
        partitionStart =
                Objects.requireNonNull(partitionStart, "O início da partição é obrigatório.");
        partitionEndExclusive =
                Objects.requireNonNull(
                        partitionEndExclusive, "O fim exclusivo da partição é obrigatório.");
        if (!partitionStart.isBefore(partitionEndExclusive)) {
            throw new IllegalArgumentException(
                    "A partição deve usar o intervalo [início, fim exclusivo).");
        }
    }

    private static String required(
            final String value, final int maximumLength, final String message) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
