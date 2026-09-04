package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Instant;
import java.util.Objects;

/** Evento fechado: aceita apenas códigos, uma correlação opaca e uma medida escalar. */
public record StructuredLogEvent(
        LogSeverity severity,
        String eventCode,
        String componentCode,
        String outcomeCode,
        CorrelationReference correlationReference,
        long occurrenceCount,
        Instant occurredAt) {

    public StructuredLogEvent {
        severity = Objects.requireNonNull(severity, "A severidade é obrigatória.");
        eventCode = ObservabilityFields.upperCode(eventCode, "O event code");
        componentCode = ObservabilityFields.upperCode(componentCode, "O component code");
        outcomeCode = ObservabilityFields.upperCode(outcomeCode, "O outcome code");
        correlationReference =
                Objects.requireNonNull(
                        correlationReference, "A referência de correlação é obrigatória.");
        occurredAt = Objects.requireNonNull(occurredAt, "O horário do evento é obrigatório.");
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("A contagem do evento não pode ser negativa.");
        }
    }

    @Override
    public String toString() {
        return "StructuredLogEvent[eventCode=" + eventCode + ", outcomeCode=" + outcomeCode + "]";
    }
}
