package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaAttributePresence;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaFieldValue;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaNumericValue;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaParseState;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageDisposition;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStatusDecision;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaTemporalValue;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Mapper linha a linha 8656, fechado nos 17 paths ratificados pela decisão local. */
public final class LocalizacaoCargaDataExportRecordMapper {
    private static final int MAXIMUM_RAW_RECORD_CHARACTERS = 1_048_576;
    public static final List<String> ACCEPTED_FIELDS =
            List.of(
                    "corporation_sequence_number",
                    "type",
                    "service_at",
                    "invoices_volumes",
                    "taxed_weight",
                    "invoices_value",
                    "total",
                    "service_type",
                    "fit_crn_psn_nickname",
                    "fit_dpn_delivery_prediction_at",
                    "fit_dyn_name",
                    "fit_dyn_drt_nickname",
                    "fit_fsn_name",
                    "fit_fln_status",
                    "fit_fln_cln_nickname",
                    "fit_o_n_name",
                    "fit_o_n_drt_nickname");

    public LocalizacaoCargaStageRecord map(final int ordinal, final JsonNode record) {
        return map(ordinal, record, WireLexemes.unavailable());
    }

    /** Entrada preferencial quando o léxico numérico bruto ainda está disponível. */
    public LocalizacaoCargaStageRecord map(final int ordinal, final String rawRecordJson) {
        if (rawRecordJson == null
                || rawRecordJson.isBlank()
                || rawRecordJson.length() > MAXIMUM_RAW_RECORD_CHARACTERS) {
            throw new IllegalArgumentException("O JSON bruto 8656 é inválido ou excede o limite.");
        }
        try {
            return map(
                    ordinal,
                    LocalizacaoCargaJson.JSON.readTree(rawRecordJson),
                    captureLexemes(rawRecordJson));
        } catch (final IOException error) {
            throw new IllegalArgumentException("O JSON bruto 8656 é inválido.", error);
        }
    }

    private LocalizacaoCargaStageRecord map(
            final int ordinal, final JsonNode record, final WireLexemes lexemes) {
        Objects.requireNonNull(record, "O registro 8656 é obrigatório.");
        if (!record.isObject() || hasUnapprovedField(record) || hasUnsupportedShape(record)) {
            final String reason =
                    !record.isObject()
                            ? "INVALID_RECORD_TYPE"
                            : hasUnapprovedField(record)
                                    ? "UNAPPROVED_SOURCE_PATH"
                                    : "UNAPPROVED_FIELD_SHAPE";
            return LocalizacaoCargaStageRecord.quarantine(
                    ordinal,
                    reason,
                    LocalizacaoCargaJson.canonicalJson(record),
                    presenceEvidence(record, lexemes));
        }

        final String payloadJson = canonicalRecord(record);
        final String presenceJson = presenceEvidence(record, lexemes);
        final ScopedSourceIdentity.SourceKey sourceKey;
        try {
            sourceKey =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY,
                            record.get("corporation_sequence_number"));
        } catch (final IdentityQuarantineException error) {
            return quarantine(
                    ordinal,
                    error.reason().name(),
                    null,
                    payloadJson,
                    presenceJson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        final LocalizacaoCargaFieldValue<Instant> serviceAt = temporal(record);
        final LocalizacaoCargaFieldValue<Integer> volumes =
                integer(record, "invoices_volumes", lexemes.invoicesVolumes());
        final LocalizacaoCargaFieldValue<BigDecimal> taxedWeight =
                decimal(record, "taxed_weight", lexemes.taxedWeight());
        final LocalizacaoCargaFieldValue<BigDecimal> invoicesValue =
                decimal(record, "invoices_value", lexemes.invoicesValue());
        final LocalizacaoCargaFieldValue<BigDecimal> total =
                decimal(record, "total", lexemes.total());
        final StatusValue status = status(record);

        final String invalidReason =
                invalidReason(
                        serviceAt,
                        volumes,
                        taxedWeight,
                        invoicesValue,
                        total,
                        status,
                        hasUnavailableNumericLexeme(record, lexemes));
        if (invalidReason != null) {
            return quarantine(
                    ordinal,
                    invalidReason,
                    sourceKey,
                    payloadJson,
                    presenceJson,
                    serviceAt.rawJson(),
                    serviceAt.typedValue(),
                    serviceAt.presence(),
                    serviceAt.parseState(),
                    volumes.rawJson(),
                    volumes.typedValue(),
                    volumes.presence(),
                    volumes.parseState(),
                    taxedWeight.rawJson(),
                    taxedWeight.typedValue(),
                    invoicesValue.rawJson(),
                    invoicesValue.typedValue(),
                    total.rawJson(),
                    total.typedValue(),
                    status.rawEvidence(),
                    status.decision().normalized());
        }

        return new LocalizacaoCargaStageRecord(
                ordinal,
                sourceKey,
                payloadJson,
                presenceJson,
                serviceAt.rawJson(),
                serviceAt.typedValue(),
                serviceAt.presence(),
                serviceAt.parseState(),
                volumes.rawJson(),
                volumes.typedValue(),
                volumes.presence(),
                volumes.parseState(),
                taxedWeight.rawJson(),
                taxedWeight.typedValue(),
                invoicesValue.rawJson(),
                invoicesValue.typedValue(),
                total.rawJson(),
                total.typedValue(),
                status.rawEvidence(),
                status.decision().normalized(),
                status.decision().terminal(),
                "UNSOURCED_LEGACY",
                LocalizacaoCargaStageDisposition.VALID,
                null);
    }

    private static boolean hasUnapprovedField(final JsonNode record) {
        final Iterator<String> names = record.fieldNames();
        while (names.hasNext()) {
            if (!ACCEPTED_FIELDS.contains(names.next())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnsupportedShape(final JsonNode record) {
        for (final String name : ACCEPTED_FIELDS) {
            final JsonNode value = record.get(name);
            if (value != null && value.isContainerNode()) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalRecord(final JsonNode record) {
        final ObjectNode accepted = LocalizacaoCargaJson.JSON.createObjectNode();
        for (final String name : ACCEPTED_FIELDS) {
            if (record.has(name)) {
                accepted.set(name, LocalizacaoCargaJson.canonical(record.get(name)));
            }
        }
        return LocalizacaoCargaJson.canonicalJson(accepted);
    }

    private static LocalizacaoCargaFieldValue<Instant> temporal(final JsonNode record) {
        final Field field = field(record, "service_at");
        if (field.presence() == LocalizacaoCargaAttributePresence.ABSENT) {
            return LocalizacaoCargaTemporalValue.absent();
        }
        if (field.presence() == LocalizacaoCargaAttributePresence.NULL) {
            return LocalizacaoCargaTemporalValue.explicitNull();
        }
        return field.value().isTextual()
                ? LocalizacaoCargaTemporalValue.parse(
                        LocalizacaoCargaJson.canonicalJson(field.value()),
                        field.value().textValue())
                : LocalizacaoCargaTemporalValue.parse(
                        LocalizacaoCargaJson.canonicalJson(field.value()), null);
    }

    private static LocalizacaoCargaFieldValue<Integer> integer(
            final JsonNode record, final String name, final String capturedLexeme) {
        final Field field = field(record, name);
        return LocalizacaoCargaNumericValue.integer(
                "/" + name,
                field.presence(),
                raw(field, capturedLexeme),
                wireText(field, capturedLexeme));
    }

    private static LocalizacaoCargaFieldValue<BigDecimal> decimal(
            final JsonNode record, final String name, final String capturedLexeme) {
        final Field field = field(record, name);
        return LocalizacaoCargaNumericValue.decimal(
                "/" + name,
                field.presence(),
                raw(field, capturedLexeme),
                wireText(field, capturedLexeme));
    }

    private static String raw(final Field field, final String capturedLexeme) {
        if (field.presence() != LocalizacaoCargaAttributePresence.VALUE) {
            return null;
        }
        return field.value().isNumber() && capturedLexeme != null
                ? capturedLexeme
                : LocalizacaoCargaJson.canonicalJson(field.value());
    }

    private static String wireText(final Field field, final String capturedLexeme) {
        if (field.presence() != LocalizacaoCargaAttributePresence.VALUE
                || (!field.value().isTextual() && !field.value().isNumber())) {
            return null;
        }
        return field.value().isNumber() ? capturedLexeme : field.value().textValue();
    }

    private static StatusValue status(final JsonNode record) {
        final Field field = field(record, "fit_fln_status");
        if (field.presence() == LocalizacaoCargaAttributePresence.VALUE
                && !field.value().isTextual()) {
            return new StatusValue(
                    LocalizacaoCargaJson.canonicalJson(field.value()),
                    LocalizacaoCargaStatusDecision.fromRaw(null),
                    true);
        }
        final String raw =
                field.presence() == LocalizacaoCargaAttributePresence.VALUE
                        ? field.value().textValue()
                        : null;
        return new StatusValue(raw, LocalizacaoCargaStatusDecision.fromRaw(raw), false);
    }

    private static String invalidReason(
            final LocalizacaoCargaFieldValue<Instant> serviceAt,
            final LocalizacaoCargaFieldValue<Integer> volumes,
            final LocalizacaoCargaFieldValue<BigDecimal> taxedWeight,
            final LocalizacaoCargaFieldValue<BigDecimal> invoicesValue,
            final LocalizacaoCargaFieldValue<BigDecimal> total,
            final StatusValue status,
            final boolean unavailableNumericLexeme) {
        if (unavailableNumericLexeme) {
            return "UNVERIFIED_NUMERIC_WIRE_LEXEME";
        }
        if (serviceAt.parseState() != LocalizacaoCargaParseState.VALID) {
            return serviceAt.parseState() == LocalizacaoCargaParseState.INVALID
                    ? "INVALID_SERVICE_AT"
                    : "MISSING_VALID_SERVICE_AT";
        }
        if (volumes.parseState() == LocalizacaoCargaParseState.INVALID) {
            return "INVALID_INVOICES_VOLUMES";
        }
        if (taxedWeight.parseState() == LocalizacaoCargaParseState.INVALID) {
            return "INVALID_TAXED_WEIGHT";
        }
        if (invoicesValue.parseState() == LocalizacaoCargaParseState.INVALID) {
            return "INVALID_INVOICES_VALUE";
        }
        if (total.parseState() == LocalizacaoCargaParseState.INVALID) {
            return "INVALID_TOTAL";
        }
        return status.invalidType() ? "INVALID_STATUS_TYPE" : null;
    }

    private static String presenceEvidence(final JsonNode record, final WireLexemes lexemes) {
        final ObjectNode result = LocalizacaoCargaJson.JSON.createObjectNode();
        final ObjectNode fields = result.putObject("fields");
        for (final String name : ACCEPTED_FIELDS) {
            final Field field = field(record, name);
            final ObjectNode evidence = fields.putObject(name);
            evidence.put("path", "/" + name);
            evidence.put("presence", field.presence().name());
            evidence.put("provenance", "DATAEXPORT_8656");
            evidence.put(
                    "parseState",
                    switch (field.presence()) {
                        case ABSENT -> "NOT_PRESENT";
                        case NULL -> "EXPLICIT_NULL";
                        case VALUE -> "PRESERVED_OR_STRICTLY_PARSED_BY_NAMED_FIELD";
                    });
            evidence.put("typedState", typedState(name));
            if (field.presence() == LocalizacaoCargaAttributePresence.VALUE) {
                evidence.set("raw", LocalizacaoCargaJson.canonical(field.value()));
                if (field.value().isNumber()) {
                    final String captured = numericLexeme(name, lexemes);
                    evidence.put(
                            "rawWireLexeme",
                            captured == null
                                    ? "UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION"
                                    : captured);
                } else {
                    evidence.put("rawWireLexeme", "NOT_APPLICABLE_NON_NUMERIC_TOKEN");
                }
            } else {
                evidence.putNull("raw");
                evidence.put("rawWireLexeme", "NOT_APPLICABLE_WITHOUT_VALUE");
            }
        }
        final ObjectNode policy = result.putObject("policy");
        policy.put("matrix", "LOCALIZACAO_8656_17_PATHS_V1");
        policy.put(
                "freightVolumeFallback",
                "FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION");
        final ObjectNode legacy = policy.putObject("statusBranchNickname");
        legacy.put("path", "ABSENT");
        legacy.put("presence", "ABSENT");
        legacy.put("provenance", "UNSOURCED_LEGACY");
        return LocalizacaoCargaJson.canonicalJson(result);
    }

    private static String typedState(final String name) {
        if (name.equals("corporation_sequence_number")) {
            return "INTEGER_TYPE_TAGGED_SOURCE_KEY";
        }
        if (name.equals("service_at")
                || name.equals("invoices_volumes")
                || name.equals("taxed_weight")
                || name.equals("invoices_value")
                || name.equals("total")
                || name.equals("fit_fln_status")) {
            return "STRICT_TYPED_COLUMN";
        }
        return "UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW";
    }

    private static boolean hasUnavailableNumericLexeme(
            final JsonNode record, final WireLexemes lexemes) {
        for (final String name :
                List.of("invoices_volumes", "taxed_weight", "invoices_value", "total")) {
            final JsonNode value = record.get(name);
            if (value != null && value.isNumber() && numericLexeme(name, lexemes) == null) {
                return true;
            }
        }
        return false;
    }

    private static String numericLexeme(final String name, final WireLexemes lexemes) {
        return switch (name) {
            case "invoices_volumes" -> lexemes.invoicesVolumes();
            case "taxed_weight" -> lexemes.taxedWeight();
            case "invoices_value" -> lexemes.invoicesValue();
            case "total" -> lexemes.total();
            default -> null;
        };
    }

    private static WireLexemes captureLexemes(final String rawJson) throws IOException {
        String invoicesVolumes = null;
        String taxedWeight = null;
        String invoicesValue = null;
        String total = null;
        try (JsonParser parser = LocalizacaoCargaJson.JSON.getFactory().createParser(rawJson)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return WireLexemes.available(null, null, null, null);
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String name = parser.currentName();
                final JsonToken token = parser.nextToken();
                if (token != null && token.isNumeric()) {
                    final String lexeme = parser.getText();
                    if ("invoices_volumes".equals(name)) {
                        invoicesVolumes = lexeme;
                    } else if ("taxed_weight".equals(name)) {
                        taxedWeight = lexeme;
                    } else if ("invoices_value".equals(name)) {
                        invoicesValue = lexeme;
                    } else if ("total".equals(name)) {
                        total = lexeme;
                    }
                }
                parser.skipChildren();
            }
        }
        return WireLexemes.available(invoicesVolumes, taxedWeight, invoicesValue, total);
    }

    private static Field field(final JsonNode record, final String name) {
        if (!record.has(name)) {
            return new Field(LocalizacaoCargaAttributePresence.ABSENT, null);
        }
        final JsonNode value = record.get(name);
        return value == null || value.isNull()
                ? new Field(LocalizacaoCargaAttributePresence.NULL, null)
                : new Field(LocalizacaoCargaAttributePresence.VALUE, value);
    }

    private static LocalizacaoCargaStageRecord quarantine(
            final int ordinal,
            final String reason,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final String payload,
            final String presence,
            final String serviceRaw,
            final Instant serviceAt,
            final LocalizacaoCargaAttributePresence servicePresence,
            final LocalizacaoCargaParseState serviceParse,
            final String volumesRaw,
            final Integer volumes,
            final LocalizacaoCargaAttributePresence volumesPresence,
            final LocalizacaoCargaParseState volumesParse,
            final String weightRaw,
            final BigDecimal weight,
            final String valueRaw,
            final BigDecimal value,
            final String totalRaw,
            final BigDecimal total,
            final String statusRaw,
            final String statusNormalized) {
        return new LocalizacaoCargaStageRecord(
                ordinal,
                sourceKey,
                payload,
                presence,
                serviceRaw,
                serviceAt,
                servicePresence,
                serviceParse,
                volumesRaw,
                volumes,
                volumesPresence,
                volumesParse,
                weightRaw,
                weight,
                valueRaw,
                value,
                totalRaw,
                total,
                statusRaw,
                statusNormalized,
                false,
                "UNSOURCED_LEGACY",
                LocalizacaoCargaStageDisposition.QUARANTINE,
                reason);
    }

    private record Field(LocalizacaoCargaAttributePresence presence, JsonNode value) {}

    private record StatusValue(
            String rawEvidence, LocalizacaoCargaStatusDecision decision, boolean invalidType) {}

    private record WireLexemes(
            boolean rawAvailable,
            String invoicesVolumes,
            String taxedWeight,
            String invoicesValue,
            String total) {
        private static WireLexemes unavailable() {
            return new WireLexemes(false, null, null, null, null);
        }

        private static WireLexemes available(
                final String invoicesVolumes,
                final String taxedWeight,
                final String invoicesValue,
                final String total) {
            return new WireLexemes(true, invoicesVolumes, taxedWeight, invoicesValue, total);
        }
    }
}
