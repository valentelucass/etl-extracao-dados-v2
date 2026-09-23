package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoAttributePresence;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoFieldValue;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoFreshnessOrigin;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoMdfeObservation;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoMetric;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoMetricValue;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageRecord;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoTextField;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Mapeia uma observação física 6399 sem inferir relação Manifesto--Coleta. */
public final class ManifestoDataExportRecordMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TEMPORAL_FIELDS =
            List.of("finished_at", "closed_at", "departured_at", "created_at");
    private static final List<String> PRESENCE_FIELDS = createPresenceFields();
    private static final List<String> ROOT_FIELDS = createRootFields();

    public ManifestoStageRecord map(final int inputOrdinal, final JsonNode record) {
        Objects.requireNonNull(record, "O registro 6399 é obrigatório.");
        if (!record.isObject()) {
            return ManifestoStageRecord.quarantine(
                    inputOrdinal, null, "UNSUPPORTED_RECORD_ENVELOPE");
        }
        final ScopedSourceIdentity.SourceKey rootKey;
        try {
            rootKey = rootKey(record);
        } catch (final ManifestoMappingException exception) {
            return ManifestoStageRecord.quarantine(inputOrdinal, null, exception.reasonCode());
        }
        final String typeFailure = validateTypedLeaves(record);
        if (typeFailure != null) {
            return ManifestoStageRecord.quarantine(inputOrdinal, rootKey, typeFailure);
        }
        final Freshness freshness = resolveFreshness(record);
        if (freshness.reasonCode() != null) {
            return ManifestoStageRecord.quarantine(inputOrdinal, rootKey, freshness.reasonCode());
        }
        final ManifestoFieldValue competence = resolveCompetence(record);
        if (competence == null) {
            return ManifestoStageRecord.quarantine(
                    inputOrdinal, rootKey, "INVALID_COMPETENCE_TEMPORAL");
        }
        final ChildValues children = resolveChildren(record);
        if (children.reasonCode() != null) {
            return ManifestoStageRecord.quarantine(inputOrdinal, rootKey, children.reasonCode());
        }
        return ManifestoStageRecord.valid(
                inputOrdinal,
                rootKey,
                rootFields(record),
                metrics(record),
                canonicalJson(record),
                presenceJson(record),
                relationCandidatesJson(record),
                freshness.instant(),
                freshness.origin(),
                competence,
                children.pickSourceKey(),
                children.mdfe());
    }

    private static ScopedSourceIdentity.SourceKey rootKey(final JsonNode record) {
        try {
            final ScopedSourceIdentity.SourceKey value =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY,
                            record.get("sequence_code"));
            if (new BigInteger(value.storageValue().substring("INTEGER:".length())).signum() <= 0) {
                throw new ManifestoMappingException("INVALID_SOURCE_KEY_VALUE");
            }
            return value;
        } catch (final IdentityQuarantineException exception) {
            throw new ManifestoMappingException(exception.reason().name());
        }
    }

    private static String validateTypedLeaves(final JsonNode record) {
        for (final ManifestoTextField field : ManifestoTextField.values()) {
            if (field == ManifestoTextField.MDFE_KEY) {
                continue;
            }
            final JsonNode value = record.get(field.sourceField());
            if (value != null && !value.isNull()) {
                if (!value.isTextual()) {
                    return "INVALID_TEXT_FIELD_TYPE";
                }
                if (!validUnicode(value.textValue())) {
                    return "INVALID_UNICODE_TEXT";
                }
                if (value.textValue().length() > field.maximumUtf16Units()) {
                    return "TEXT_LIMIT_EXCEEDED";
                }
            }
        }
        for (final ManifestoMetric metric : ManifestoMetric.values()) {
            final JsonNode value = record.get(metric.sourceField());
            if (value != null && !value.isNull() && parseMetric(value) == null) {
                return "INVALID_METRIC_VALUE";
            }
        }
        for (final String temporal : TEMPORAL_FIELDS) {
            final JsonNode value = record.get(temporal);
            if (value != null && !value.isNull() && !value.isTextual()) {
                return "INVALID_FRESHNESS_TEMPORAL";
            }
        }
        return null;
    }

    private static Freshness resolveFreshness(final JsonNode record) {
        for (final String temporal : TEMPORAL_FIELDS) {
            final FieldValue value = field(record, temporal);
            if (value.presence() == ManifestoAttributePresence.VALUE
                    && parseOffsetInstant(value.value().textValue()) == null) {
                return Freshness.invalid();
            }
        }
        for (final ManifestoFreshnessOrigin origin : ManifestoFreshnessOrigin.values()) {
            final FieldValue value = field(record, origin.sourceField());
            if (value.presence() == ManifestoAttributePresence.VALUE) {
                final Instant instant = parseOffsetInstant(value.value().textValue());
                if (instant == null) {
                    return Freshness.invalid();
                }
                return new Freshness(instant, origin, null);
            }
        }
        return new Freshness(null, null, "MISSING_VALID_FRESHNESS");
    }

    private static ManifestoFieldValue resolveCompetence(final JsonNode record) {
        final FieldValue primary = field(record, "departured_at");
        if (primary.presence() == ManifestoAttributePresence.VALUE) {
            final Instant instant = parseOffsetInstant(primary.value().textValue());
            return instant == null
                    ? null
                    : ManifestoFieldValue.value(
                            competenceJson("departured_at", primary.value().textValue(), instant));
        }
        final FieldValue fallback = field(record, "created_at");
        if (fallback.presence() == ManifestoAttributePresence.VALUE) {
            final Instant instant = parseOffsetInstant(fallback.value().textValue());
            return instant == null
                    ? null
                    : ManifestoFieldValue.value(
                            competenceJson("created_at", fallback.value().textValue(), instant));
        }
        return primary.presence() == ManifestoAttributePresence.NULL
                        || fallback.presence() == ManifestoAttributePresence.NULL
                ? ManifestoFieldValue.nullValue()
                : ManifestoFieldValue.absent();
    }

    private static ChildValues resolveChildren(final JsonNode record) {
        final FieldValue pick = field(record, "mft_pfs_pck_sequence_code");
        ScopedSourceIdentity.SourceKey pickKey = null;
        if (pick.presence() == ManifestoAttributePresence.VALUE) {
            if (!pick.value().isIntegralNumber()) {
                return ChildValues.quarantine("INVALID_CHILD_KEY_TYPE");
            }
            final BigInteger value = pick.value().bigIntegerValue();
            if (value.signum() <= 0) {
                return ChildValues.quarantine("INVALID_CHILD_KEY_VALUE");
            }
            pickKey =
                    new ScopedSourceIdentity.SourceKey(
                            ScopedSourceIdentity.WireType.INTEGER, "INTEGER:" + value);
        }
        final FieldValue key = field(record, "mft_mfs_key");
        final FieldValue number = field(record, "mft_mfs_number");
        if (key.presence() != ManifestoAttributePresence.VALUE
                && number.presence() != ManifestoAttributePresence.VALUE) {
            return new ChildValues(pickKey, null, null);
        }
        if (key.presence() != ManifestoAttributePresence.VALUE) {
            return ChildValues.quarantine("CHILD_NUMBER_SIGNAL_WITHOUT_VALID_KEY");
        }
        if (number.presence() != ManifestoAttributePresence.VALUE) {
            return ChildValues.quarantine("MDFE_NUMBER_KEY_PAIR_ASYMMETRY");
        }
        if (!key.value().isTextual()) {
            return ChildValues.quarantine("INVALID_CHILD_KEY_TYPE");
        }
        if (!key.value().textValue().matches("[0-9]{44}")) {
            return ChildValues.quarantine("INVALID_CHILD_KEY_VALUE");
        }
        if (!number.value().isIntegralNumber() || number.value().bigIntegerValue().signum() <= 0) {
            return ChildValues.quarantine("MDFE_NUMBER_KEY_PAIR_ASYMMETRY");
        }
        return new ChildValues(
                pickKey,
                new ManifestoMdfeObservation(
                        key.value().textValue(), number.value().bigIntegerValue()),
                null);
    }

    private static Map<String, ManifestoFieldValue> rootFields(final JsonNode record) {
        final Map<String, ManifestoFieldValue> values = new LinkedHashMap<>();
        for (final String name : ROOT_FIELDS) {
            final FieldValue value = field(record, name);
            values.put(
                    name,
                    value.presence() == ManifestoAttributePresence.VALUE
                            ? name.equals("status")
                                    ? ManifestoFieldValue.text(
                                            value.value().textValue(), canonicalJson(value.value()))
                                    : ManifestoFieldValue.value(canonicalJson(value.value()))
                            : value.presence() == ManifestoAttributePresence.NULL
                                    ? ManifestoFieldValue.nullValue()
                                    : ManifestoFieldValue.absent());
        }
        return values;
    }

    private static Map<ManifestoMetric, ManifestoMetricValue> metrics(final JsonNode record) {
        final Map<ManifestoMetric, ManifestoMetricValue> values =
                new EnumMap<>(ManifestoMetric.class);
        for (final ManifestoMetric metric : ManifestoMetric.values()) {
            final FieldValue field = field(record, metric.sourceField());
            values.put(
                    metric,
                    field.presence() == ManifestoAttributePresence.VALUE
                            ? ManifestoMetricValue.value(
                                    Objects.requireNonNull(parseMetric(field.value())))
                            : field.presence() == ManifestoAttributePresence.NULL
                                    ? ManifestoMetricValue.nullValue()
                                    : ManifestoMetricValue.absent());
        }
        return values;
    }

    private static String presenceJson(final JsonNode record) {
        final ObjectNode result = JSON.createObjectNode();
        for (final String name : PRESENCE_FIELDS) {
            result.put(name, field(record, name).presence().name());
        }
        return canonicalJson(result);
    }

    private static String relationCandidatesJson(final JsonNode record) {
        final ObjectNode candidates = JSON.createObjectNode();
        final FieldValue pick = field(record, "mft_pfs_pck_sequence_code");
        final ObjectNode candidate = candidates.putObject("mft_pfs_pck_sequence_code");
        candidate.put("presence", pick.presence().name());
        if (pick.presence() == ManifestoAttributePresence.VALUE) {
            candidate.set("value", canonicalNode(pick.value()));
        }
        return canonicalJson(candidates);
    }

    private static FieldValue field(final JsonNode record, final String name) {
        if (!record.has(name)) {
            return new FieldValue(ManifestoAttributePresence.ABSENT, null);
        }
        final JsonNode value = record.get(name);
        return value == null || value.isNull()
                ? new FieldValue(ManifestoAttributePresence.NULL, null)
                : new FieldValue(ManifestoAttributePresence.VALUE, value);
    }

    private static BigDecimal parseMetric(final JsonNode value) {
        try {
            if (value.isNumber()) {
                return value.decimalValue();
            }
            return value.isTextual() ? new BigDecimal(value.textValue()) : null;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private static Instant parseOffsetInstant(final String value) {
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (final DateTimeParseException secondIgnored) {
                return null;
            }
        }
    }

    private static boolean validUnicode(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private static String competenceJson(
            final String sourcePath, final String raw, final Instant instant) {
        final ObjectNode result = JSON.createObjectNode();
        result.put("fallbackOrigin", sourcePath);
        result.put("instantUtc", instant.toString());
        result.put("raw", raw);
        return canonicalJson(result);
    }

    private static String canonicalJson(final JsonNode node) {
        try {
            return JSON.writeValueAsString(canonicalNode(node));
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "O JSON de Manifestos não pode ser preservado.", exception);
        }
    }

    private static JsonNode canonicalNode(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode result = JSON.createObjectNode();
            final Map<String, JsonNode> sorted = new TreeMap<>();
            final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                sorted.put(field.getKey(), field.getValue());
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

    private static List<String> createPresenceFields() {
        final List<String> fields = new ArrayList<>();
        fields.add("sequence_code");
        fields.addAll(TEMPORAL_FIELDS);
        fields.add("status");
        fields.add("mft_pfs_pck_sequence_code");
        fields.add("mft_mfs_number");
        for (final ManifestoTextField field : ManifestoTextField.values()) {
            if (!fields.contains(field.sourceField())) {
                fields.add(field.sourceField());
            }
        }
        for (final ManifestoMetric metric : ManifestoMetric.values()) {
            fields.add(metric.sourceField());
        }
        return List.copyOf(fields);
    }

    private static List<String> createRootFields() {
        final List<String> fields = new ArrayList<>();
        fields.add("sequence_code");
        fields.add("status");
        fields.add("mdfe_status");
        for (final ManifestoTextField field : ManifestoTextField.values()) {
            if (field != ManifestoTextField.STATUS
                    && field != ManifestoTextField.MDFE_STATUS
                    && field != ManifestoTextField.MDFE_KEY) {
                fields.add(field.sourceField());
            }
        }
        return List.copyOf(fields);
    }

    private record FieldValue(ManifestoAttributePresence presence, JsonNode value) {}

    private record Freshness(Instant instant, ManifestoFreshnessOrigin origin, String reasonCode) {
        private static Freshness invalid() {
            return new Freshness(null, null, "INVALID_FRESHNESS_TEMPORAL");
        }
    }

    private record ChildValues(
            ScopedSourceIdentity.SourceKey pickSourceKey,
            ManifestoMdfeObservation mdfe,
            String reasonCode) {
        private static ChildValues quarantine(final String reasonCode) {
            return new ChildValues(null, null, reasonCode);
        }
    }

    private static final class ManifestoMappingException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String reasonCode;

        private ManifestoMappingException(final String reasonCode) {
            super("A identidade de Manifesto deve ser quarentenada.");
            this.reasonCode = reasonCode;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }
}
