package br.com.esl.etl.v2.plataforma.observabilidade;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/** Único adapter produtivo autorizado a acessar SLF4J diretamente. */
public final class Slf4jStructuredLogSink implements StructuredLogSink {

    private final Logger logger;

    public Slf4jStructuredLogSink(final Class<?> componentType) {
        logger = LoggerFactory.getLogger(Objects.requireNonNull(componentType));
    }

    Slf4jStructuredLogSink(final Logger logger) {
        this.logger = Objects.requireNonNull(logger, "O logger é obrigatório.");
    }

    @Override
    public void write(final StructuredLogEvent event) {
        final StructuredLogEvent required =
                Objects.requireNonNull(event, "O evento estruturado é obrigatório.");
        logger.atLevel(level(required.severity()))
                .addKeyValue("event_code", required.eventCode())
                .addKeyValue("component_code", required.componentCode())
                .addKeyValue("outcome_code", required.outcomeCode())
                .addKeyValue("correlation_ref", required.correlationReference().sha256())
                .addKeyValue("occurrence_count", required.occurrenceCount())
                .addKeyValue("occurred_at_utc", required.occurredAt())
                .log("structured_event");
    }

    private static Level level(final LogSeverity severity) {
        return switch (severity) {
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }
}
