package br.com.esl.etl.v2.modulos.cotacoes.aplicacao;

import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoAttributePresence;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoFreshnessOrigin;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Boundary 6906: conversões estritas e quarentena; não decide tarifa ou acesso remoto. */
public final class CotacaoDataExportRecordMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<String> PRESENCE_FIELDS =
            List.of(
                    "sequence_code",
                    "requested_at",
                    "qoe_qes_fit_nse_issued_at",
                    "qoe_qes_fit_fhe_cte_issued_at",
                    "qoe_qes_total",
                    "qoe_crn_psn_nickname",
                    "qoe_uer_name",
                    "qoe_qes_ony_sae_code",
                    "qoe_qes_diy_sae_code");

    public CotacaoStageRecord map(final int ordinal, final JsonNode record) {
        Objects.requireNonNull(record, "O registro 6906 é obrigatório.");
        if (!record.isObject()) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_COTACAO_RECORD");
        }
        final ScopedSourceIdentity.SourceKey key;
        try {
            key =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY,
                            record.get("sequence_code"));
        } catch (final IdentityQuarantineException error) {
            return CotacaoStageRecord.quarantine(ordinal, error.reason().name());
        }
        if (!CotacaoStageRecord.isPositiveSequenceCode(key)) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_SOURCE_KEY_VALUE");
        }
        if (hasInvalidDate(record, "qoe_qes_fit_nse_issued_at")
                || hasInvalidDate(record, "qoe_qes_fit_fhe_cte_issued_at")
                || hasInvalidDate(record, "requested_at")) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_COTACAO_DATE");
        }
        final Instant nfseIssuedAt = parseDate(field(record, "qoe_qes_fit_nse_issued_at"));
        final Instant cteIssuedAt = parseDate(field(record, "qoe_qes_fit_fhe_cte_issued_at"));
        final Instant requestedAt = parseDate(field(record, "requested_at"));
        final Instant freshness = firstPresent(nfseIssuedAt, cteIssuedAt, requestedAt);
        if (freshness == null) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_FRESHNESS");
        }
        final BigDecimal total;
        try {
            total = strictDecimal(field(record, "qoe_qes_total"));
        } catch (final IllegalArgumentException error) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_TOTAL_AMOUNT");
        }
        try {
            return new CotacaoStageRecord(
                    ordinal,
                    key,
                    canonicalJson(record),
                    presenceJson(record),
                    normalizedUser(field(record, "qoe_uer_name")),
                    nfseIssuedAt,
                    cteIssuedAt,
                    requestedAt,
                    freshness,
                    freshnessOrigin(record),
                    freshness.atZone(SOURCE_ZONE).toLocalDate(),
                    total,
                    null,
                    strictUf(field(record, "qoe_qes_ony_sae_code")),
                    strictUf(field(record, "qoe_qes_diy_sae_code")),
                    null);
        } catch (final IllegalArgumentException error) {
            return CotacaoStageRecord.quarantine(ordinal, "INVALID_COTACAO_ATTRIBUTE");
        }
    }

    private static CotacaoFreshnessOrigin freshnessOrigin(final JsonNode record) {
        if (parseDate(field(record, "qoe_qes_fit_nse_issued_at")) != null) {
            return CotacaoFreshnessOrigin.NFSE_ISSUED_AT;
        }
        if (parseDate(field(record, "qoe_qes_fit_fhe_cte_issued_at")) != null) {
            return CotacaoFreshnessOrigin.CTE_ISSUED_AT;
        }
        return CotacaoFreshnessOrigin.REQUESTED_AT;
    }

    private static boolean hasInvalidDate(final JsonNode record, final String name) {
        final Field value = field(record, name);
        return value.presence == CotacaoAttributePresence.VALUE && parseDate(value) == null;
    }

    private static Instant firstPresent(
            final Instant nfseIssuedAt, final Instant cteIssuedAt, final Instant requestedAt) {
        if (nfseIssuedAt != null) {
            return nfseIssuedAt;
        }
        if (cteIssuedAt != null) {
            return cteIssuedAt;
        }
        return requestedAt;
    }

    private static Instant parseDate(final Field value) {
        if (value.presence != CotacaoAttributePresence.VALUE || !value.value.isTextual()) {
            return null;
        }
        final String text = value.value.textValue().trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        for (final DateTimeFormatter format :
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                        DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                if (format == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDate.parse(text, format).atStartOfDay(SOURCE_ZONE).toInstant();
                }
                return LocalDateTime.parse(text, format).atZone(SOURCE_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static BigDecimal strictDecimal(final Field value) {
        if (value.presence != CotacaoAttributePresence.VALUE) {
            return null;
        }
        if (!value.value.isTextual() && !value.value.isNumber()) {
            throw new IllegalArgumentException();
        }
        final BigDecimal decimal = new BigDecimal(value.value.asText());
        if (!CotacaoStageRecord.isTotalAmountRepresentable(decimal)) {
            throw new IllegalArgumentException();
        }
        return decimal;
    }

    private static String strictUf(final Field value) {
        if (value.presence != CotacaoAttributePresence.VALUE) {
            return null;
        }
        if (!value.value.isTextual()) {
            throw new IllegalArgumentException();
        }
        return value.value.textValue();
    }

    /** User is a display attribute only: trim/NFC, never identity or tenant. */
    static String normalizeUser(final String value) {
        return value == null ? null : Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    private static String normalizedUser(final Field value) {
        if (value.presence != CotacaoAttributePresence.VALUE) {
            return null;
        }
        if (!value.value.isTextual()) {
            throw new IllegalArgumentException("Usuário de Cotações não é texto.");
        }
        final String normalized = normalizeUser(value.value.textValue());
        return normalized.isEmpty() ? null : normalized;
    }

    private static String presenceJson(final JsonNode record) {
        final ObjectNode result = JSON.createObjectNode();
        for (final String name : PRESENCE_FIELDS) {
            result.put(name, field(record, name).presence.name());
        }
        return canonicalJson(result);
    }

    private static Field field(final JsonNode record, final String name) {
        if (!record.has(name)) {
            return new Field(CotacaoAttributePresence.ABSENT, null);
        }
        final JsonNode value = record.get(name);
        return value == null || value.isNull()
                ? new Field(CotacaoAttributePresence.NULL, null)
                : new Field(CotacaoAttributePresence.VALUE, value);
    }

    private static String canonicalJson(final JsonNode node) {
        try {
            return JSON.writeValueAsString(canonical(node));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("JSON 6906 inválido.", error);
        }
    }

    private static JsonNode canonical(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode result = JSON.createObjectNode();
            final Map<String, JsonNode> fields = new TreeMap<>();
            final Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                final Map.Entry<String, JsonNode> entry = iterator.next();
                fields.put(entry.getKey(), entry.getValue());
            }
            fields.forEach((name, value) -> result.set(name, canonical(value)));
            return result;
        }
        if (node.isArray()) {
            final ArrayNode result = JSON.createArrayNode();
            for (JsonNode item : node) {
                result.add(canonical(item));
            }
            return result;
        }
        return node.deepCopy();
    }

    private record Field(CotacaoAttributePresence presence, JsonNode value) {}
}
