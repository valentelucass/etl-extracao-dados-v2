package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Candidate port of versioned V1 expressions, supplied with captured primitives and raw reference
 * snapshots rather than precomputed billing facts. No SQL, default absence or source-clock
 * invention.
 */
public final class LegacyFreightBillingRules {
    private LegacyFreightBillingRules() {}

    public static ObjectNode derive(final JsonNode captured, final JsonNode references) {
        require(
                "LEGACY_FREIGHT_RAW_REFERENCES_V1".equals(text(references, "schema")),
                "FREIGHT_RAW_REFERENCE_SCHEMA");
        require(
                captured.required("id").isIntegralNumber()
                        && captured.get("id")
                                .bigIntegerValue()
                                .toString()
                                .equals(text(references, "sourceKey")),
                "FREIGHT_RAW_REFERENCE_ID");
        // 6389 sequence_code (column2) is not the legacy corporationSequenceNumber (column27).
        final long minuta =
                captured.required("corporation_sequence_number").bigIntegerValue().longValueExact();
        final var lifecycle = references.required("lifecycle");
        final boolean excluded = bool(lifecycle, "excludedAtSource");
        final boolean courtesy = bool(captured, "courtesy");
        final String classification = nullableText(captured, "fit_fsn_name");
        final String classificationNorm = ciAiAscii(classification);
        final boolean blocked =
                classificationNorm.contains("bloqueio")
                        && (classificationNorm.contains("anulacao")
                                || classificationNorm.contains("isolamento"));
        final String sourceStatus = nullableText(captured, "status");
        final String normalizedStatus = normalizedStatus(sourceStatus);
        final var documents = references.required("documents");
        final var cteId = documents.required("cteId");
        final var cteNumber = captured.required("fit_fhe_cte_number");
        final var cteSeries = captured.required("fit_fhe_cte_series");
        final String cteKey = sqlTrim(nullableText(captured, "fit_fhe_cte_key"));
        final boolean hasCte =
                !cteId.isNull() || cteKey != null || !cteNumber.isNull() || !cteSeries.isNull();
        final var fiscal = references.required("fiscal");
        // The independently retained best raw4924 row must be selected under the V1 rank contract.
        require(
                bool(fiscal, "rankedSnapshotComplete"),
                "FREIGHT_FISCAL_RANK_COMPLETENESS_REQUIRED");
        final String actualCteStatus = nullableText(fiscal, "statusCte");
        final String actualCteResult = nullableText(fiscal, "statusCteResult");
        final boolean fiscalCanceled =
                canceled(ciAiAscii(actualCteStatus))
                        || cancellationText(ciAiAscii(actualCteResult))
                        || hasCte
                                && java.util.Set.of(
                                                "cancelado", "cancelada", "canceled", "cancelled")
                                        .contains(normalizedStatus);
        final String service = text(captured, "service_at");
        final String issued = nullableText(documents, "cteIssuedAt");
        // V1 FreteRepository: COALESCE(cte_issued_at, servico_em), never NFSe or updated_at.
        final var actualReference = OffsetDateTime.parse(issued == null ? service : issued);
        final var calendar = references.required("calendar");
        require(
                actualReference.toLocalDate().equals(LocalDate.parse(text(calendar, "sourceDay"))),
                "FREIGHT_CALENDAR_EXACT_SOURCE_DAY");
        final var adjusted = LocalDate.parse(text(calendar, "referenceDay"));
        require(
                !adjusted.isAfter(actualReference.toLocalDate()),
                "FREIGHT_CALENDAR_CANNOT_ADVANCE_BILLING");
        // Calendar snapshot contains the current governed day/holiday rules; do not infer weekends.
        require(
                bool(calendar, "previousBusinessDaySnapshotVerified"),
                "FREIGHT_CALENDAR_REFERENCE_REQUIRED");
        final var reference = adjusted.atStartOfDay().atOffset(actualReference.getOffset());
        final boolean sourceEligible = !courtesy && !blocked;
        final boolean eligible =
                !excluded && !fiscalCanceled && !courtesy && !blocked && sourceEligible;
        final var originalRevenue = decimalOrSqlZero(captured.required("total"));
        // subtotal is absent from the fixed114; it must come from a retained raw
        // extension/reference.
        final var originalFreight =
                decimalOrSqlZero(references.required("rawFrete").required("subtotal"));
        final var location = references.required("localizacao");
        require(
                location.required("sequenceNumber").isIntegralNumber()
                        && location.get("sequenceNumber").longValue() == minuta,
                "FREIGHT_LOCALIZACAO_JOIN_USES_CORPORATION_SEQUENCE");
        require(
                bool(location, "activeJoinSnapshotComplete"),
                "FREIGHT_LOCALIZACAO_COMPLETENESS_REQUIRED");
        final String responsible = sqlTrim(nullableText(location, "destinationBranchNickname"));
        // Materialized key in V1 is NULLIF(LOWER(LTRIM(RTRIM(nickname))), '').
        final String key = responsible == null ? "sem_responsavel" : ciAiAscii(responsible);
        final var result =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                        .objectNode()
                        .put("schema", "LEGACY_FREIGHT_DERIVED_BILLING_V1")
                        .put("sourceKey", text(references, "sourceKey"))
                        .put("numeroMinuta", minuta)
                        .put("sourceServiceAt", service)
                        .put("referenceAt", reference.toString())
                        .put("actualReferenceAt", actualReference.toString())
                        .put(
                                "referenceWasRetroacted",
                                !adjusted.equals(actualReference.toLocalDate()))
                        .put("courtesy", courtesy)
                        .put("classificationBlocked", blocked)
                        .put("cteCanceled", fiscalCanceled)
                        .put("sourceEligible", sourceEligible)
                        .put("eligible", eligible)
                        .put("grossRevenue", eligible ? originalRevenue : BigDecimal.ZERO)
                        .put("freightValue", eligible ? originalFreight : BigDecimal.ZERO)
                        .put("responsavel_regiao_destino_key", key)
                        .put("semanticApprovalIssued", false)
                        .put("SQLExecuted", false);
        if (responsible == null) {
            result.putNull("responsavel_regiao_destino");
        } else {
            result.put("responsavel_regiao_destino", responsible);
        }
        result.set("cteId", cteId);
        result.set("cteNumber", cteNumber);
        if (issued == null) {
            result.putNull("cteIssuedAt");
        } else {
            result.put("cteIssuedAt", issued);
        }
        return result;
    }

    private static String normalizedStatus(final String value) {
        final String trimmed = sqlTrim(value);
        if (trimmed == null) {
            return "";
        }
        return switch (trimmed) {
            case "pending" -> "pendente";
            case "finished" -> "finalizado";
            case "in_transit" -> "em transito";
            case "standby" -> "aguardando";
            case "manifested" -> "registrado";
            case "occurrence_treatment" -> "tratamento de ocorrencia";
            default -> ciAiAscii(trimmed);
        };
    }

    private static boolean canceled(final String value) {
        return java.util.Set.of("cancelado", "cancelada", "canceled", "cancelled").contains(value)
                || cancellationText(value);
    }

    private static boolean cancellationText(final String value) {
        return value.contains("cancelad") || value.contains("cancell");
    }

    private static String ciAiAscii(final String value) {
        final String trimmed = sqlTrim(value);
        if (trimmed == null) {
            return "";
        }
        require(
                trimmed.chars().allMatch(c -> c >= 32 && c <= 126),
                "FREIGHT_SQL_COLLATION_NON_ASCII_PENDING");
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String sqlTrim(final String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return start == end ? null : value.substring(start, end);
    }

    private static String nullableText(final JsonNode node, final String field) {
        final var value = node.required(field);
        require(value.isNull() || value.isTextual(), "FREIGHT_TYPED_REFERENCE_TEXT");
        return value.isNull() ? null : value.textValue();
    }

    private static boolean bool(final JsonNode node, final String field) {
        require(node.required(field).isBoolean(), "FREIGHT_TYPED_REFERENCE_BOOLEAN");
        return node.get(field).booleanValue();
    }

    private static BigDecimal decimalOrSqlZero(final JsonNode value) {
        require(value.isNull() || value.isNumber(), "FREIGHT_TYPED_SOURCE_DECIMAL");
        return value.isNull() ? BigDecimal.ZERO : value.decimalValue();
    }
}
