package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityCatalog;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Mapeia uma linha 6908 por vez, sem inferir relações nem materializar uma página inteira. */
public final class ColetaDataExportRecordMapper {

    private static final FirstWaveIdentityContract IDENTITY_CONTRACT =
            FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> PRESENCE_FIELDS =
            List.of(
                    "id",
                    "sequence_code",
                    "status",
                    "status_updated_at",
                    "finish_date",
                    "service_date",
                    "request_date",
                    "cancellation_reason",
                    "manifesto",
                    "pick_item_id",
                    "fit_p_m_pck_sequence_code",
                    "frete",
                    "pck_mik_mft_sequence_code");
    private static final List<String> RELATION_CANDIDATE_FIELDS =
            List.of(
                    "manifesto",
                    "pick_item_id",
                    "fit_p_m_pck_sequence_code",
                    "frete",
                    "pck_mik_mft_sequence_code");

    public ColetaStageRecord map(final int inputOrdinal, final JsonNode record) {
        Objects.requireNonNull(record, "O registro 6908 é obrigatório.");
        if (!record.isObject()) {
            return ColetaStageRecord.quarantine(inputOrdinal, null, "INVALID_COLETA_RECORD");
        }
        final ScopedSourceIdentity.SourceKey sourceKey;
        try {
            sourceKey =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            IDENTITY_CONTRACT.sourceKey().wireTypes(), record.get("id"));
        } catch (final IdentityQuarantineException exception) {
            return ColetaStageRecord.quarantine(inputOrdinal, null, exception.reason().name());
        }

        final FieldValue sequenceCode = field(record, "sequence_code");
        if (sequenceCode.presence() == ColetaAttributePresence.VALUE
                && !sequenceCode.value().isIntegralNumber()) {
            return ColetaStageRecord.quarantine(
                    inputOrdinal, sourceKey, "INVALID_SEQUENCE_CODE_TYPE");
        }
        final FieldValue status = field(record, "status");
        if (status.presence() == ColetaAttributePresence.VALUE && !status.value().isTextual()) {
            return ColetaStageRecord.quarantine(inputOrdinal, sourceKey, "INVALID_STATUS_TYPE");
        }
        final FieldValue cancellationReason = field(record, "cancellation_reason");
        if (cancellationReason.presence() == ColetaAttributePresence.VALUE
                && !cancellationReason.value().isTextual()) {
            return ColetaStageRecord.quarantine(
                    inputOrdinal, sourceKey, "INVALID_CANCELLATION_REASON_TYPE");
        }

        final String rawStatus = status.textValue();
        final String rawCancellationReason = cancellationReason.textValue();
        final Freshness freshness = resolveFreshness(record);
        return ColetaStageRecord.valid(
                inputOrdinal,
                sourceKey,
                sequenceCode.presence(),
                sequenceCode.presence() == ColetaAttributePresence.VALUE
                        ? canonicalJson(sequenceCode.value())
                        : null,
                canonicalJson(record),
                presenceJson(record),
                relationCandidatesJson(record),
                ColetaStatus.resolve(rawStatus, rawCancellationReason),
                freshness.raw(),
                freshness.instant(),
                freshness.origin());
    }

    private static Freshness resolveFreshness(final JsonNode record) {
        final FieldValue statusUpdatedAt = field(record, "status_updated_at");
        final Instant parsedStatus = parseDateTime(statusUpdatedAt.textValue());
        if (parsedStatus != null) {
            return new Freshness(
                    statusUpdatedAt.textValue(),
                    parsedStatus,
                    ColetaFreshnessOrigin.STATUS_UPDATED_AT);
        }
        for (final Map.Entry<String, ColetaFreshnessOrigin> fallback :
                List.of(
                        Map.entry("finish_date", ColetaFreshnessOrigin.FINISH_DATE),
                        Map.entry("service_date", ColetaFreshnessOrigin.SERVICE_DATE),
                        Map.entry("request_date", ColetaFreshnessOrigin.REQUEST_DATE))) {
            final Instant parsed = parseBusinessDate(field(record, fallback.getKey()).textValue());
            if (parsed != null) {
                return new Freshness(statusUpdatedAt.textValue(), parsed, fallback.getValue());
            }
        }
        return new Freshness(statusUpdatedAt.textValue(), null, ColetaFreshnessOrigin.UNAVAILABLE);
    }

    private static Instant parseDateTime(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (final DateTimeParseException ignored) {
            // Tenta a forma com offset e, por último, os formatos locais historicamente aceitos.
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (final DateTimeParseException ignored) {
            // A próxima tentativa mantém explicitamente a zona do contrato.
        }
        for (final DateTimeFormatter format :
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                .withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
                                .withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu H:mm:ss")
                                .withResolverStyle(ResolverStyle.STRICT))) {
            try {
                return LocalDateTime.parse(trimmed, format).atZone(SOURCE_ZONE).toInstant();
            } catch (final DateTimeParseException ignored) {
                // Testa o próximo formato aprovado pelo mapper.
            }
        }
        return null;
    }

    private static Instant parseBusinessDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay(SOURCE_ZONE).toInstant();
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }

    private static String presenceJson(final JsonNode record) {
        final ObjectNode presence = JSON.createObjectNode();
        for (final String name : PRESENCE_FIELDS) {
            presence.put(name, field(record, name).presence().name());
        }
        return canonicalJson(presence);
    }

    private static String relationCandidatesJson(final JsonNode record) {
        final ObjectNode candidates = JSON.createObjectNode();
        for (final String name : RELATION_CANDIDATE_FIELDS) {
            final FieldValue candidate = field(record, name);
            final ObjectNode value = candidates.putObject(name);
            value.put("presence", candidate.presence().name());
            if (candidate.presence() == ColetaAttributePresence.VALUE) {
                value.set("value", canonicalNode(candidate.value()));
            }
        }
        return canonicalJson(candidates);
    }

    private static FieldValue field(final JsonNode record, final String name) {
        if (!record.has(name)) {
            return new FieldValue(ColetaAttributePresence.ABSENT, null);
        }
        final JsonNode value = record.get(name);
        return value == null || value.isNull()
                ? new FieldValue(ColetaAttributePresence.NULL, null)
                : new FieldValue(ColetaAttributePresence.VALUE, value);
    }

    private static String canonicalJson(final JsonNode node) {
        try {
            return JSON.writeValueAsString(canonicalNode(node));
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "O JSON de Coletas não pode ser preservado.", exception);
        }
    }

    private static JsonNode canonicalNode(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode result = JSON.createObjectNode();
            final Map<String, JsonNode> sorted = new TreeMap<>();
            final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> entry = fields.next();
                sorted.put(entry.getKey(), entry.getValue());
            }
            sorted.forEach((name, value) -> result.set(name, canonicalNode(value)));
            return result;
        }
        if (node.isArray()) {
            final ArrayNode result = JSON.createArrayNode();
            for (final JsonNode item : node) {
                result.add(canonicalNode(item));
            }
            return result;
        }
        return node.deepCopy();
    }

    private record FieldValue(ColetaAttributePresence presence, JsonNode value) {
        private String textValue() {
            return presence == ColetaAttributePresence.VALUE && value.isTextual()
                    ? value.textValue()
                    : null;
        }
    }

    private record Freshness(String raw, Instant instant, ColetaFreshnessOrigin origin) {}
}
