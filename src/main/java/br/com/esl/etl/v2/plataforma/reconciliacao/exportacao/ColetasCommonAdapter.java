package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permanent port of the qualified Coletas common31 decisions. Preserves the retained source payload
 * and every native occurrence; presentation keys are a separate, explicitly scoped axis.
 */
public final class ColetasCommonAdapter {
    private static final String CATALOG_SHA =
            "87841247dc337e37f25c7cef8f1b12f026ba9f1de80ee83ab38cf70c09e56a9c";
    private static final DateTimeFormatter SECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final Map<String, String> STATUSES =
            Map.of(
                    "treatment",
                    "Em tratativa",
                    "analyze",
                    "Em análise",
                    "manifested",
                    "Manifestada",
                    "pending",
                    "Pendente",
                    "in_transit",
                    "Em trânsito",
                    "done",
                    "Finalizada",
                    "finished",
                    "Coletada",
                    "canceled",
                    "Cancelada");
    private static final Map<String, String> ACTIONS =
            Map.of(
                    "nothing",
                    "Nenhuma",
                    "finish",
                    "Baixar Entrega/Coleta",
                    "finish_reverse_pick",
                    "Baixar Coleta Frete Reverso",
                    "done",
                    "Finalizar Transporte",
                    "rollback_to_pending",
                    "Voltar para entrega",
                    "send_to_occurrences_dealing",
                    "Enviar para tratativas de ocorrências",
                    "send_to_damages_dealing",
                    "Enviar para tratativas de avarias",
                    "send_to_incidents_dealing",
                    "Enviar para tratativas de sinistros",
                    "confirm_scheduling",
                    "Confirmar Agendamento",
                    "scheduling_to_pending",
                    "Voltar para Agendamento");

    private ColetasCommonAdapter() {}

    public static String statusLabel(final String raw) {
        return STATUSES.get(raw);
    }

    public static JsonNode catalog() throws java.io.IOException {
        try (var input =
                ColetasCommonAdapter.class.getResourceAsStream(
                        "/contracts/dataexport/coletas-common31-v1.json")) {
            require(input != null, "COLETAS_COMMON_CATALOG");
            final byte[] bytes = input.readNBytes(32769);
            require(
                    bytes.length <= 32768
                            && CATALOG_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "COLETAS_COMMON_CATALOG_PIN");
            return ExportValidationSupport.JSON.readTree(bytes);
        }
    }

    public static ObjectNode compare(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin nativeBook,
            final ExportValidationSupport.Pin policyPin,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final JsonNode policy = policyPin.json(262144, budget);
        require(
                "COLETAS_COMMON31_POLICY_V1".equals(text(policy, "schema"))
                        && source.sha256().equals(text(policy, "sourceSha256"))
                        && nativeBook.sha256().equals(text(policy, "workbookSha256"))
                        && text(request, "attemptId").equals(text(policy, "attemptId")),
                "COLETAS_PAIR_POLICY_BINDING");
        final var evidencePin =
                ExportValidationSupport.Pin.from(
                        policy.required("independentPresentationEvidencePin"));
        final var evidence = evidencePin.json(262144, budget);
        require(
                "COLETAS_COMMON31_PRESENTATION_EVIDENCE_V1".equals(text(evidence, "schema"))
                        && source.sha256().equals(text(evidence, "sourceSha256"))
                        && nativeBook.sha256().equals(text(evidence, "workbookSha256"))
                        && CATALOG_SHA.equals(text(evidence, "catalogSha256")),
                "COLETAS_PRESENTATION_EVIDENCE_SCOPE");
        final var fields = catalog().required("columns");
        final List<ExactExportTable.Column> columns = new ArrayList<>();
        for (final var field : fields) {
            final String kind = text(field, "kind");
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "name"),
                            field.get("nativeColumn").intValue(),
                            field.get("nativeColumn").intValue(),
                            "decimal".equals(kind) || "integer".equals(kind),
                            "decimal".equals(kind)
                                    ? "BINARY64_UNITS_129"
                                    : "integer".equals(kind) ? "DECIMAL_EXACT" : "NONE"));
        }
        final var nativeTable =
                BoundedExportWorkbook.readCommonRows(
                        nativeBook.read(8388608, budget), columns, budget);
        require("1900".equals(nativeTable.epoch()), "COLETAS_QUALIFIED_1900_PRESENTATION_REQUIRED");
        final var sourceBag = new ExactExportTable(columns, 0, budget);
        final var uiBag = new ExactExportTable(columns, 0, budget);
        final var originalColumns =
                columns.stream()
                        .map(
                                column ->
                                        new ExactExportTable.Column(
                                                column.name(),
                                                column.apiColumn(),
                                                column.uiColumn(),
                                                column.total()))
                        .toList();
        final var sourceOriginal = new ExactExportTable(originalColumns, 0, budget);
        final var uiOriginal = new ExactExportTable(originalColumns, 0, budget);
        final LocalDate start = LocalDate.parse(text(request, "periodStart"));
        final LocalDate end = LocalDate.parse(text(request, "periodEnd"));
        final Set<String> nullOmission = stringSet(policy.required("nullOmissionItems"));
        final Set<String> allItems = new HashSet<>();
        fields.forEach(field -> allItems.add(text(field, "itemId")));
        require(
                allItems.containsAll(nullOmission) && !nullOmission.contains("item_221149"),
                "COLETAS_FIELD_SPECIFIC_NULL_POLICY");
        require(
                java.util.Collections.disjoint(
                                nullOmission,
                                Set.of(
                                        "item_124818",
                                        "item_123121",
                                        "item_123134",
                                        "item_135309",
                                        "item_131395"))
                        && Set.of("item_124818", "item_123121", "item_123134")
                                .containsAll(stringSet(policy.required("dateBlankItems")))
                        && Set.of("item_135309", "item_131395")
                                .containsAll(
                                        stringSet(
                                                policy.required(
                                                        "timestampIntegerSecondDisplayItems"))),
                "COLETAS_NARROW_PRESENTATION_ALLOWLISTS");
        long sourceRows = 0;
        long pending = 0;
        for (final var sourceRow :
                ColetasSourceReader.read(source.read(8388608, budget), fields, policy, budget)) {
            budget.check();
            require(++sourceRows <= 12000, "COLETAS_MAPPED_LINE_BOUND");
            verifyPeriod(sourceRow.atoms().get(4).path("value").asText(), start, end);
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            final List<ExactExportTable.Atom> originals = new ArrayList<>();
            boolean available = true;
            int ordinal = 0;
            for (final var field : fields) {
                final var original = sourceRow.atoms().get(ordinal++);
                originals.add(original(original));
                final var decision = source(field, original, policy);
                available &= decision != null;
                pending += decision == null ? 1 : 0;
                values.add(decision);
            }
            if (available) {
                sourceBag.add(values);
            }
            sourceOriginal.add(originals);
        }
        for (final var row : nativeTable.rows()) {
            budget.check();
            final var id = row.cells().get(0);
            final boolean identified =
                    "NUMBER".equals(id.type())
                            && new BigDecimal(id.value()).stripTrailingZeros().scale() <= 0
                            && (row.physicalMask() & 1) == 1;
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            boolean available = identified;
            for (int index = 0; index < fields.size(); index++) {
                final var field = fields.get(index);
                final var decision =
                        nativeCell(
                                field,
                                row.cells().get(index),
                                identified,
                                nullOmission,
                                policy,
                                evidence);
                available &= decision != null;
                pending += decision == null ? 1 : 0;
                values.add(decision);
            }
            if (available) {
                verifyPeriod(values.get(4).value(), start, end);
                uiBag.add(values);
            } else if (!identified) {
                pending++;
            }
            if (identified) {
                uiOriginal.add(row.cells());
            }
        }
        sourceBag.seal();
        uiBag.seal();
        sourceOriginal.seal();
        uiOriginal.seal();
        final var comparison = sourceBag.compare(uiBag);
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put("schema", "COLETAS_COMMON31_RESULT_V1");
        result.put("attemptId", text(request, "attemptId"));
        result.put("sourceRows", sourceRows);
        result.put("uiRows", nativeTable.rows().size());
        result.put("pendingCellDecisions", pending);
        result.put("allOccurrencesCompared", pending == 0);
        result.put(
                "status",
                pending > 0
                        ? "PENDING_FIELD_REPRESENTATION_EVIDENCE"
                        : comparison.contentEqual()
                                ? "CONTENT_EQUAL_FOR_THIS_PAIR"
                                : "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR");
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        final var projection = result.putObject("fullValueProjection");
        projection.set("api", sourceBag.fullValueProjection());
        projection.set("ui", uiBag.fullValueProjection());
        final var originalProjection = result.putObject("originalValueProjection");
        originalProjection.set("api", sourceOriginal.fullValueProjection());
        originalProjection.set("ui", uiOriginal.fullValueProjection());
        result.put("sourceFractionsAndExtraFieldsRetainedInPinnedPayload", true);
        result.put("snapshotIdentityProven", false);
        result.put("semanticParityApproved", false);
        result.put("presentationEvidenceAuthenticationVerified", false);
        result.put("mainResumeAllowed", false);
        result.put("dashboardConsumptionVerified", false);
        result.set("dashboardBridge", IndependentDashboardBridge.contract());
        result.put("deduplicationPerformed", false);
        for (final var pin : List.of(source, nativeBook, policyPin, evidencePin)) {
            pin.read(8388608, budget);
        }
        return result;
    }

    private static ExactExportTable.Atom source(
            final JsonNode field, final JsonNode atom, final JsonNode policy) {
        final String item = text(field, "itemId");
        final var value = atom.required("value");
        if ("ABSENT".equals(text(atom, "presence"))) {
            return new ExactExportTable.Atom("ABSENT", "");
        }
        if ("NULL".equals(text(atom, "presence"))) {
            return new ExactExportTable.Atom("NULL", "");
        }
        final String kind = text(field, "kind");
        if ("integer".equals(kind)) {
            if (!Set.of("integer", "number", "decimal").contains(text(atom, "type"))
                    || !value.isTextual()) {
                return null;
            }
            try {
                ExactBinary64.ratio(value.textValue());
                return number(new BigDecimal(value.textValue()).toBigIntegerExact());
            } catch (final IllegalArgumentException | ArithmeticException refused) {
                return null;
            }
        }
        if ("decimal".equals(kind)) {
            if (!Set.of("integer", "number", "decimal", "string").contains(text(atom, "type"))
                    || !value.isTextual()) {
                return null;
            }
            try {
                return encoded(value.textValue());
            } catch (final IllegalArgumentException refused) {
                return null;
            }
        }
        if (!"string".equals(text(atom, "type")) || !value.isTextual()) {
            return null;
        }
        if ("date".equals(kind)) {
            try {
                return literal(QualifiedColetasModel.sourceCivil(value.textValue()));
            } catch (final IllegalArgumentException refused) {
                return null;
            }
        }
        if ("timestamp".equals(kind)) {
            final String time = value.textValue();
            if (!time.matches(
                            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,128})?-03:00")
                    || !policy.path("timestampIntegerSecondDisplayItems").isArray()
                    || !stringSet(policy.get("timestampIntegerSecondDisplayItems"))
                            .contains(item)) {
                return null;
            }
            try {
                // Preserve original fractions in source bytes; this separate key follows the
                // scoped whole-second display decision, never a UTC/watermark replacement.
                return literal(QualifiedColetasModel.sourceTimestamp(time));
            } catch (final IllegalArgumentException refused) {
                return null;
            }
        }
        if ("status".equals(kind)) {
            return STATUSES.containsKey(value.textValue())
                    ? literal(STATUSES.get(value.textValue()))
                    : null;
        }
        if ("item_123132".equals(item)) {
            return ACTIONS.containsKey(value.textValue())
                    ? literal(ACTIONS.get(value.textValue()))
                    : null;
        }
        return literal(value.textValue());
    }

    private static ExactExportTable.Atom nativeCell(
            final JsonNode field,
            final ExactExportTable.Atom cell,
            final boolean identified,
            final Set<String> nullItems,
            final JsonNode policy,
            final JsonNode evidence) {
        final String item = text(field, "itemId");
        final String kind = text(field, "kind");
        if ("ABSENT".equals(cell.type())) {
            return identified && nullItems.contains(item)
                    ? new ExactExportTable.Atom("NULL", "")
                    : cell;
        }
        if ("BLANK".equals(cell.type())) {
            if ("date".equals(kind)
                    && policy.path("dateBlankItems").isArray()
                    && stringSet(policy.get("dateBlankItems")).contains(item)
                    && verifiedCarrier(item, cell.carrier(), evidence)
                    && QualifiedColetasModel.carrierRole("date", cell.carrier())) {
                return new ExactExportTable.Atom("NULL", "");
            }
            return cell;
        }
        if ("integer".equals(kind)) {
            if (!"NUMBER".equals(cell.type())) {
                return null;
            }
            try {
                ExactBinary64.ratio(cell.value());
                return number(new BigDecimal(cell.value()).toBigIntegerExact());
            } catch (final IllegalArgumentException | ArithmeticException refused) {
                return null;
            }
        }
        if ("decimal".equals(kind)) {
            if (!"NUMBER".equals(cell.type())
                    || !verifiedCarrier(item, cell.carrier(), evidence)
                    || !QualifiedColetasModel.carrierRole("decimal", cell.carrier())) {
                return null;
            }
            try {
                return encoded(cell.value());
            } catch (final IllegalArgumentException refused) {
                return null;
            }
        }
        if ("date".equals(kind) || "timestamp".equals(kind)) {
            if (!"NUMBER".equals(cell.type())
                    || !verifiedCarrier(item, cell.carrier(), evidence)
                    || !QualifiedColetasModel.carrierRole(kind, cell.carrier())) {
                return null;
            }
            try {
                final var serial = new BigDecimal(cell.value());
                require(
                        serial.compareTo(new BigDecimal("61")) >= 0
                                && serial.compareTo(new BigDecimal("2958466")) < 0,
                        "COLETAS_NATIVE_DATE_RANGE");
                if ("date".equals(kind)) {
                    return literal(QualifiedColetasModel.nativeCivil(cell.value()));
                }
                require(
                        stringSet(policy.required("timestampIntegerSecondDisplayItems"))
                                .contains(item),
                        "COLETAS_SCOPED_TIMESTAMP_POLICY");
                return literal(QualifiedColetasModel.nativeTimestamp(cell.value()));
            } catch (final IllegalArgumentException
                    | ArithmeticException
                    | java.time.DateTimeException refused) {
                return null;
            }
        }
        return "TEXT".equals(cell.type()) ? literal(cell.value()) : null;
    }

    private static boolean verifiedCarrier(
            final String item, final String carrier, final JsonNode evidence) {
        final var allowed = evidence.path("columnCarriers").path(item);
        return allowed.isArray() && stringSet(allowed).contains(carrier);
    }

    private static void verifyPeriod(
            final String value, final LocalDate start, final LocalDate end) {
        final var date = LocalDate.parse(value);
        require(!date.isBefore(start) && !date.isAfter(end), "COLETAS_ROW_OUTSIDE_PERIOD");
    }

    private static Set<String> stringSet(final JsonNode values) {
        require(values.isArray() && values.size() <= 64, "COLETAS_POLICY_SET");
        final var result = new HashSet<String>();
        values.forEach(
                value -> {
                    require(
                            value.isTextual()
                                    && value.textValue().length() <= 2048
                                    && result.add(value.textValue()),
                            "COLETAS_POLICY_SET_VALUE");
                });
        return Set.copyOf(result);
    }

    private static ExactExportTable.Atom number(final BigInteger value) {
        return new ExactExportTable.Atom("NUMBER", value.toString());
    }

    private static ExactExportTable.Atom original(final JsonNode atom) {
        final String presence = text(atom, "presence");
        final String type = text(atom, "type");
        if (!"VALUE".equals(presence)) {
            return new ExactExportTable.Atom(presence, "", "ORIGINAL:" + type);
        }
        final var value = atom.required("value");
        return Set.of("integer", "number", "decimal").contains(type) && value.isTextual()
                ? new ExactExportTable.Atom("NUMBER", value.textValue(), "ORIGINAL:" + type)
                : new ExactExportTable.Atom(
                        "TEXT",
                        value.isTextual() ? value.textValue() : value.toString(),
                        "ORIGINAL:" + type);
    }

    private static ExactExportTable.Atom decimal(final String value) {
        return new ExactExportTable.Atom(
                "NUMBER", new BigDecimal(value).stripTrailingZeros().toPlainString());
    }

    private static ExactExportTable.Atom encoded(final String value) {
        final var encoded = ExactBinary64.encode(value);
        return new ExactExportTable.Atom(
                "NUMBER",
                encoded.units().signum() == 0 && encoded.bits().startsWith("8")
                        ? "-0"
                        : encoded.units().toString(),
                "BINARY64:" + encoded.bits());
    }

    private static ExactExportTable.Atom literal(final String value) {
        return new ExactExportTable.Atom("TEXT", value);
    }
}
