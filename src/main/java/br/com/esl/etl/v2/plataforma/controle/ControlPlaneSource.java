package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Instant;
import java.util.Objects;

/**
 * Registro sanitizado de uma instância estável e não secreta da fonte. {@code registeredAt} é
 * metadado de compatibilidade do comando; a criação persiste o relógio autoritativo do SQL Server.
 */
public record ControlPlaneSource(String sourceInstance, String sourceKind, Instant registeredAt) {

    public ControlPlaneSource {
        sourceInstance = required(sourceInstance, 128, "A instância da fonte é obrigatória.");
        sourceKind = required(sourceKind, 64, "O tipo da fonte é obrigatório.");
        registeredAt = Objects.requireNonNull(registeredAt, "O horário de registro é obrigatório.");
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
