package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteAttributePresence;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteFreshnessPolicy;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteSidecarEnvelope;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStatusDecision;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteTemporalValue;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Boundary Data Export 6389: tipos estritos, uma página e quarentena fail-closed. */
public final class FreteDataExportRecordMapper {
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final BigInteger MAXIMUM_ALIAS = BigInteger.valueOf(Long.MAX_VALUE);
    private static final List<String> PRESENCE_FIELDS =
            List.of(
                    "id",
                    "updated_at",
                    "reference_number",
                    "fit_p_m_pck_sequence_code",
                    "corporation_sequence_number",
                    "finished_at",
                    "fit_dpn_performance_finished_at",
                    "status",
                    "cte_created_at",
                    "cte_issued_at",
                    "criado_em",
                    "servico_em",
                    "cte",
                    "ctes",
                    "cte_key",
                    "finalizations",
                    "finalizacoes",
                    "total");

    public FreteStageRecord map(final int ordinal, final JsonNode record) {
        return map(ordinal, record, FreteGraphQlSidecarMapper.absent());
    }

    public FreteStageRecord map(
            final int ordinal, final JsonNode record, final FreteSidecarEnvelope sidecarEnvelope) {
        Objects.requireNonNull(record, "O registro 6389 é obrigatório.");
        final FreteSidecarEnvelope sidecar =
                Objects.requireNonNull(sidecarEnvelope, "O sidecar é obrigatório.");
        if (!record.isObject()) {
            return FreteStageRecord.quarantine(ordinal, "INVALID_FRETE_RECORD");
        }
        final ScopedSourceIdentity.SourceKey sourceKey;
        try {
            sourceKey =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY,
                            record.get("id"));
        } catch (final IdentityQuarantineException error) {
            return FreteStageRecord.quarantine(ordinal, error.reason().name());
        }
        if (!FreteStageRecord.isPositiveCanonicalId(sourceKey)) {
            return FreteStageRecord.quarantine(ordinal, "INVALID_SOURCE_KEY_VALUE");
        }

        final String aliasJson;
        try {
            aliasJson = aliasEvidence(record);
        } catch (final IllegalArgumentException error) {
            return FreteStageRecord.quarantine(ordinal, "INVALID_BUSINESS_ALIAS");
        }

        final FreteTemporalValue cteCreated = temporal(record, "cte_created_at");
        final FreteTemporalValue cteIssued = temporal(record, "cte_issued_at");
        final FreteTemporalValue criadoEm = temporal(record, "criado_em");
        final FreteTemporalValue servicoEm = temporal(record, "servico_em");
        final FreteFreshnessPolicy.Selection freshness =
                FreteFreshnessPolicy.select(cteCreated, cteIssued, criadoEm, servicoEm);
        if (!freshness.selected()) {
            return FreteStageRecord.quarantine(ordinal, freshness.quarantineReasonCode());
        }

        final PerformanceSelection performance = performance(record);
        if (performance.reasonCode() != null) {
            return FreteStageRecord.quarantine(ordinal, performance.reasonCode());
        }

        final String financialJson;
        try {
            financialJson = financialEvidence(record);
        } catch (final IllegalArgumentException error) {
            return FreteStageRecord.quarantine(ordinal, "INVALID_FINANCIAL_VALUE");
        }
        final String statusRaw;
        final Field status = field(record, "status");
        if (status.presence() == FreteAttributePresence.VALUE) {
            if (!status.value().isTextual()) {
                return FreteStageRecord.quarantine(ordinal, "INVALID_STATUS_TYPE");
            }
            statusRaw = status.value().textValue();
        } else {
            statusRaw = null;
        }
        final FreteStatusDecision statusDecision = FreteStatusDecision.fromRaw(statusRaw);
        return new FreteStageRecord(
                ordinal,
                sourceKey,
                FreteJson.canonicalJson(record),
                presenceJson(record),
                aliasJson,
                statusDecision.raw(),
                statusDecision.code(),
                statusDecision.label(),
                statusDecision.terminal(),
                freshnessEvidence(cteCreated, cteIssued, criadoEm, servicoEm),
                freshness.freshnessAtUtc(),
                freshness.origin(),
                servicoEm.parseState() == FreteTemporalValue.ParseState.VALID
                        ? servicoEm.instantUtc()
                        : null,
                performance.evidenceJson(),
                performance.instantUtc(),
                performance.origin(),
                groupedEvidence(
                        record,
                        "CTE_FINALIZATIONS",
                        "status",
                        "cte",
                        "ctes",
                        "cte_key",
                        "finalizations",
                        "finalizacoes"),
                financialJson,
                groupedEvidence(
                        record,
                        "UNRESOLVED_RELATION_CANDIDATES_V2_046B",
                        "fit_p_m_pck_sequence_code"),
                sidecar.canonicalJson(),
                null);
    }

    private static FreteTemporalValue temporal(final JsonNode record, final String name) {
        final Field field = field(record, name);
        final String path = "/" + name;
        if (field.presence() == FreteAttributePresence.ABSENT) {
            return FreteTemporalValue.absent(path);
        }
        if (field.presence() == FreteAttributePresence.NULL) {
            return FreteTemporalValue.explicitNull(path);
        }
        if (!field.value().isTextual()) {
            return FreteTemporalValue.invalid(path, FreteJson.canonicalJson(field.value()));
        }
        final Instant instant = parseIsoTemporal(field.value().textValue());
        return instant == null
                ? FreteTemporalValue.invalid(path, FreteJson.canonicalJson(field.value()))
                : FreteTemporalValue.valid(path, FreteJson.canonicalJson(field.value()), instant);
    }

    private static Instant parseIsoTemporal(final String raw) {
        final String value = raw == null ? "" : raw.trim();
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (final DateTimeParseException ignored) {
        }
        for (final DateTimeFormatter format :
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
            try {
                return LocalDateTime.parse(value, format).atZone(SOURCE_ZONE).toInstant();
            } catch (final DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(SOURCE_ZONE)
                    .toInstant();
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }

    private static PerformanceSelection performance(final JsonNode record) {
        final Field official = field(record, "fit_dpn_performance_finished_at");
        final Field fallback = field(record, "finished_at");
        final ObjectNode evidence = FreteJson.JSON.createObjectNode();
        evidence.put("precedence", "OFFICIAL_6389_THEN_FINISHED_AT");
        appendFieldEvidence(
                evidence,
                "official",
                "/fit_dpn_performance_finished_at",
                official,
                "PERFORMANCE_6389");
        appendFieldEvidence(evidence, "fallback", "/finished_at", fallback, "DECLARED_FALLBACK");
        final Field selected;
        final String origin;
        if (official.presence() == FreteAttributePresence.VALUE) {
            selected = official;
            origin = "OFFICIAL_6389";
        } else if (fallback.presence() == FreteAttributePresence.VALUE) {
            selected = fallback;
            origin = "FINISHED_AT_FALLBACK";
        } else {
            evidence.put("selectedOrigin", "NONE");
            evidence.put("parseState", "NOT_PRESENT");
            return new PerformanceSelection(FreteJson.canonicalJson(evidence), null, null, null);
        }
        if (!selected.value().isTextual()) {
            evidence.put("selectedOrigin", origin);
            evidence.put("parseState", "INVALID");
            return new PerformanceSelection(
                    FreteJson.canonicalJson(evidence), null, null, "INVALID_PERFORMANCE_TYPE");
        }
        final Instant parsed = parsePerformanceTemporal(selected.value().textValue());
        evidence.put("selectedOrigin", origin);
        evidence.put("parseState", parsed == null ? "INVALID_OR_AMBIGUOUS" : "VALID");
        if (parsed != null) {
            evidence.put("instantUtc", parsed.toString());
        }
        return parsed == null
                ? new PerformanceSelection(
                        FreteJson.canonicalJson(evidence),
                        null,
                        null,
                        "AMBIGUOUS_OR_INVALID_PERFORMANCE_DATE")
                : new PerformanceSelection(FreteJson.canonicalJson(evidence), parsed, origin, null);
    }

    private static Instant parsePerformanceTemporal(final String raw) {
        final Instant iso = parseIsoTemporal(raw);
        if (iso != null) {
            return iso;
        }
        final String value = raw == null ? "" : raw.trim();
        final java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(
                                "^(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?)?$")
                        .matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        final int first = Integer.parseInt(matcher.group(1));
        final int second = Integer.parseInt(matcher.group(2));
        if ((first <= 12 && second <= 12) || (first > 12 && second > 12)) {
            return null;
        }
        final int day = first > 12 ? first : second;
        final int month = first > 12 ? second : first;
        final int hour = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        final int minute = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
        final int secondOfMinute =
                matcher.group(6) == null ? 0 : Integer.parseInt(matcher.group(6));
        try {
            return LocalDateTime.of(
                            Integer.parseInt(matcher.group(3)),
                            month,
                            day,
                            hour,
                            minute,
                            secondOfMinute)
                    .atZone(SOURCE_ZONE)
                    .toInstant();
        } catch (final java.time.DateTimeException error) {
            return null;
        }
    }

    private static String aliasEvidence(final JsonNode record) {
        final Field alias = field(record, "corporation_sequence_number");
        final ObjectNode evidence = FreteJson.JSON.createObjectNode();
        evidence.put("path", "/corporation_sequence_number");
        evidence.put("presence", alias.presence().name());
        evidence.put("role", "VERSIONED_NON_TECHNICAL_ALIAS_NEVER_IDENTITY");
        if (alias.presence() == FreteAttributePresence.VALUE) {
            if (!alias.value().isIntegralNumber()) {
                throw new IllegalArgumentException("Alias deve manter o tipo integral observado.");
            }
            final BigInteger value = alias.value().bigIntegerValue();
            if (value.signum() < 0 || value.compareTo(MAXIMUM_ALIAS) > 0) {
                throw new IllegalArgumentException("Alias fora do domínio conservador.");
            }
            evidence.set("raw", FreteJson.canonical(alias.value()));
            evidence.put("typedInteger", value.toString());
            evidence.put("parseState", "VALID");
        }
        return FreteJson.canonicalJson(evidence);
    }

    private static String freshnessEvidence(
            final FreteTemporalValue cteCreated,
            final FreteTemporalValue cteIssued,
            final FreteTemporalValue criadoEm,
            final FreteTemporalValue servicoEm) {
        final ObjectNode result = FreteJson.JSON.createObjectNode();
        result.put("precedence", "cte_created_at>cte_issued_at>criado_em>servico_em");
        result.put("evidenceScope", "SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE");
        appendTemporal(result, "cte_created_at", cteCreated);
        appendTemporal(result, "cte_issued_at", cteIssued);
        appendTemporal(result, "criado_em", criadoEm);
        appendTemporal(result, "servico_em", servicoEm);
        result.put("updated_at", "IGNORED_UNVERIFIED");
        return FreteJson.canonicalJson(result);
    }

    private static void appendTemporal(
            final ObjectNode target, final String name, final FreteTemporalValue value) {
        final ObjectNode evidence = target.putObject(name);
        evidence.put("path", value.sourcePath());
        evidence.put("presence", value.presence().name());
        evidence.put("parseState", value.parseState().name());
        if (value.rawJson() != null) {
            try {
                evidence.set("raw", FreteJson.JSON.readTree(value.rawJson()));
            } catch (final com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new IllegalArgumentException("Evidência temporal canônica inválida.", error);
            }
        }
        if (value.instantUtc() != null) {
            evidence.put("instantUtc", value.instantUtc().toString());
        }
    }

    private static String financialEvidence(final JsonNode record) {
        final ObjectNode result = FreteJson.JSON.createObjectNode();
        result.put("evidenceScope", "SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE");
        result.put("currency", "UNRESOLVED_NO_INFERENCE");
        result.put("unit", "UNRESOLVED_NO_INFERENCE");
        result.put("arithmetic", "FORBIDDEN");
        for (final String field : List.of("reference_number", "total")) {
            final Field value = field(record, field);
            appendFieldEvidence(result, field, "/" + field, value, "RAW_TYPED_ONLY");
            if (field.equals("total") && value.presence() == FreteAttributePresence.VALUE) {
                if ((!value.value().isNumber() && !value.value().isTextual())
                        || !isConservativeDecimal(value.value().asText())) {
                    throw new IllegalArgumentException("Total financeiro inválido.");
                }
                ((ObjectNode) result.get(field))
                        .put(
                                "typedDecimal",
                                new BigDecimal(value.value().asText()).toPlainString());
            }
        }
        final JsonNode cte = record.get("cte");
        final JsonNode owner =
                cte != null && cte.isObject() ? cte : FreteJson.JSON.createObjectNode();
        appendFieldEvidence(result, "cte.key", "/cte/key", field(owner, "key"), "RAW_TYPED_ONLY");
        return FreteJson.canonicalJson(result);
    }

    private static boolean isConservativeDecimal(final String value) {
        try {
            final BigDecimal decimal = new BigDecimal(value);
            return decimal.scale() <= 8 && decimal.precision() - decimal.scale() <= 20;
        } catch (final NumberFormatException error) {
            return false;
        }
    }

    private static String groupedEvidence(
            final JsonNode record, final String policy, final String... fields) {
        final ObjectNode result = FreteJson.JSON.createObjectNode();
        result.put("policy", policy);
        if (policy.equals("CTE_FINALIZATIONS")) {
            result.put("evidenceScope", "SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE");
        } else if (policy.equals("UNRESOLVED_RELATION_CANDIDATES_V2_046B")) {
            result.put("evidenceScope", "CONTRACTED_DATAEXPORT_6389_PATHS_ONLY");
        }
        for (final String name : fields) {
            appendFieldEvidence(result, name, "/" + name, field(record, name), "PRESERVED");
        }
        return FreteJson.canonicalJson(result);
    }

    private static void appendFieldEvidence(
            final ObjectNode target,
            final String name,
            final String path,
            final Field value,
            final String provenance) {
        final ObjectNode evidence = target.putObject(name);
        evidence.put("path", path);
        evidence.put("presence", value.presence().name());
        evidence.put("provenance", provenance);
        if (value.presence() == FreteAttributePresence.VALUE) {
            evidence.set("raw", FreteJson.canonical(value.value()));
            evidence.put("parseState", "PRESERVED");
        }
    }

    private static String presenceJson(final JsonNode record) {
        final ObjectNode result = FreteJson.JSON.createObjectNode();
        for (final String name : PRESENCE_FIELDS) {
            result.put(name, field(record, name).presence().name());
        }
        return FreteJson.canonicalJson(result);
    }

    private static Field field(final JsonNode record, final String name) {
        if (record == null || !record.has(name)) {
            return new Field(FreteAttributePresence.ABSENT, null);
        }
        final JsonNode value = record.get(name);
        return value == null || value.isNull()
                ? new Field(FreteAttributePresence.NULL, null)
                : new Field(FreteAttributePresence.VALUE, value);
    }

    private record Field(FreteAttributePresence presence, JsonNode value) {}

    private record PerformanceSelection(
            String evidenceJson, Instant instantUtc, String origin, String reasonCode) {}
}
