package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Instant;
import java.util.Objects;

/** Alerta técnico sanitizado; não transporta mensagem livre, ID real ou payload. */
public record OperationalAlert(
        CorrelationReference correlationReference,
        int sequence,
        AlertSeverity severity,
        String alertCode,
        String ownerRole,
        long occurrenceCount,
        Instant occurredAt) {

    public OperationalAlert {
        correlationReference =
                Objects.requireNonNull(
                        correlationReference, "A referência de correlação é obrigatória.");
        severity = Objects.requireNonNull(severity, "A severidade do alerta é obrigatória.");
        alertCode = ObservabilityFields.upperCode(alertCode, "O alert code");
        ownerRole = ObservabilityFields.ownerRole(ownerRole);
        occurredAt = ObservabilityFields.sqlServerInstant(occurredAt, "O horário do alerta");
        if (sequence < 1 || sequence > 4096 || occurrenceCount < 1) {
            throw new IllegalArgumentException("A sequência ou contagem do alerta é inválida.");
        }
    }
}
