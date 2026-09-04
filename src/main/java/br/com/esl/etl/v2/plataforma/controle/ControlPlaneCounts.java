package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Equação de volume fechada para uma fase da ocorrência. {@code quarantinedRootKeys} conta apenas
 * raízes identificadas; linhas sem chave ficam em {@code unidentifiedQuarantineRows}. O instante
 * {@code recordedAt} é observação do caller e não substitui o relógio persistido do SQL Server.
 */
public record ControlPlaneCounts(
        UUID executionId,
        String phase,
        long physicalRows,
        long distinctRootKeys,
        long duplicateRows,
        long validRows,
        long quarantinedRootKeys,
        long unidentifiedQuarantineRows,
        Instant recordedAt) {

    public ControlPlaneCounts {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        phase = required(phase, 64);
        if (phase.equals("STAGING_KERNEL")) {
            throw new IllegalArgumentException(
                    "A fase STAGING_KERNEL é reservada ao kernel transacional interno.");
        }
        recordedAt = Objects.requireNonNull(recordedAt, "O horário da contagem é obrigatório.");
        if (physicalRows < 0
                || distinctRootKeys < 0
                || duplicateRows < 0
                || validRows < 0
                || quarantinedRootKeys < 0
                || unidentifiedQuarantineRows < 0) {
            throw new IllegalArgumentException("As contagens não podem ser negativas.");
        }
        try {
            final long identifiedPhysicalRows = Math.addExact(distinctRootKeys, duplicateRows);
            if (physicalRows != Math.addExact(identifiedPhysicalRows, unidentifiedQuarantineRows)
                    || distinctRootKeys != Math.addExact(validRows, quarantinedRootKeys)) {
                throw new IllegalArgumentException("A equação de contagens da execução não fecha.");
            }
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "A equação de contagens da execução excede o limite suportado.", exception);
        }
    }

    private static String required(final String value, final int maximumLength) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException("A fase da contagem é obrigatória.");
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A fase da contagem é obrigatória.");
        }
        return normalized;
    }
}
