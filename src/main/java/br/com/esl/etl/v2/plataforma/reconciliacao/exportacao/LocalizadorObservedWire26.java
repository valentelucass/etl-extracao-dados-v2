package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact archived P1b schema reader. Native carriers, current query semantics and identity are not
 * inferred.
 */
final class LocalizadorObservedWire26 {
    static final String RESOURCE =
            "contracts/dataexport/localizacao-cargas-8656-observed-p1b-20261024.json";
    static final String SHA256 = "4abf6c4735a4e3e53e6599dfc5f485132a3f749f4ac6c2d8ab3956ea37aa2997";
    static final String OBSERVED_NULL8_P2_RESOURCE =
            "contracts/dataexport/localizacao-cargas-8656-observed-p2-20261108.json";
    static final String OBSERVED_NULL8_P2_SHA256 =
            "f18964355902c4ef50d15cc3b12eb6e2e75ea2338104a0713cc2e6199d6de4fd";
    static final String INFO_NAME_BINDING_POLICY =
            "STRICT_HISTORICAL_OR_OBSERVED_QUALIFIED_BIJECTION_V1";
    static final String HISTORICAL_INFO_VARIANT = "HISTORICAL_SIMPLE_NAMES_V1";
    static final String QUALIFIED_INFO_VARIANT = "OBSERVED_QUALIFIED_BIJECTION8_V1";
    private static final Map<String, String> OBSERVED_QUALIFIED_TO_HISTORICAL =
            Map.of(
                    "freights.service_at", "service_at",
                    "fit_freight_location.current_location_id", "current_location_id",
                    "freights.payer_id", "payer_id",
                    "freights.type", "type",
                    "freights.status", "status",
                    "fit_fis_invoice.number", "number",
                    "fit_fis_invoice.key", "key",
                    "scopes.by_updated_at", "by_updated_at");

    record Shape(Map<String, JsonNode> paths, Set<String> fields, Set<String> filters) {}

    private LocalizadorObservedWire26() {}

    static Shape shape() throws IOException {
        return load(RESOURCE, SHA256, "2026-10-24.p1b-localizacao-cargas-observed.v1");
    }

    /** Field-specific NULL evidence only; the archived P1b factory remains unchanged. */
    static Shape observed20261108NullsP2() throws IOException {
        return load(
                OBSERVED_NULL8_P2_RESOURCE,
                OBSERVED_NULL8_P2_SHA256,
                "2026-11-08.p2-localizacao-cargas-observed-null8.v1");
    }

    private static Shape load(
            final String resource, final String sha256, final String contractVersion)
            throws IOException {
        try (var stream = LocalizadorObservedWire26.class.getResourceAsStream("/" + resource)) {
            require(stream != null, "LOCALIZADOR_P1B_RESOURCE_REQUIRED");
            final byte[] bytes = stream.readNBytes(65537);
            require(
                    bytes.length <= 65536 && sha256.equals(ExportValidationSupport.sha256(bytes)),
                    "LOCALIZADOR_P1B_PIN");
            final JsonNode contract = strict(bytes);
            final JsonNode response = contract.required("response");
            require(
                    contract.path("templateId").isIntegralNumber()
                            && contract.path("templateId").intValue() == 8656
                            && contractVersion.equals(text(contract, "contractVersion"))
                            && "$".equals(text(response, "recordRoot"))
                            && "ARRAY".equals(text(response, "rootCardinality")),
                    "LOCALIZADOR_P1B_ROOT_ARRAY_CONTEXT");
            final var paths = new HashMap<String, JsonNode>();
            final var fields = new HashSet<String>();
            final var metadataFields = new HashSet<String>();
            final var filters = new HashSet<String>();
            for (final var rule : response.required("fields")) {
                final String path = text(rule, "path");
                require(
                        paths.putIfAbsent(path, rule) == null
                                && "RECORD".equals(text(rule, "scope")),
                        "LOCALIZADOR_P1B_UNIQUE_PATH");
                if (path.matches("/[A-Za-z_][A-Za-z0-9_]*")) {
                    require(
                            "REQUIRED".equals(text(rule, "presence")),
                            "LOCALIZADOR_P1B_REQUIRED_ROOT_FIELDS");
                    fields.add(path.substring(1));
                } else {
                    require(
                            "/invoices_mapping/*".equals(path)
                                    && "OPTIONAL".equals(text(rule, "presence")),
                            "LOCALIZADOR_P1B_ONLY_OBSERVED_ARRAY_ITEM_PATH");
                }
            }
            for (final var field : contract.required("metadata")) {
                if ("DATA_FIELD".equals(text(field, "kind"))) {
                    require(
                            metadataFields.add(text(field, "path")),
                            "LOCALIZADOR_P1B_UNIQUE_METADATA_FIELD");
                } else {
                    require(
                            "DATA_FILTER".equals(text(field, "kind"))
                                    && filters.add(text(field, "path")),
                            "LOCALIZADOR_P1B_UNIQUE_METADATA_FILTER");
                }
            }
            require(
                    paths.size() == 27
                            && fields.size() == 26
                            && fields.equals(metadataFields)
                            && filters.equals(
                                    LocalizadorIndependentRequest.HISTORICAL_FILTER_NAMES),
                    "LOCALIZADOR_P1B_26_FIELDS_27_RULES_8_FILTERS");
            return new Shape(Map.copyOf(paths), Set.copyOf(fields), Set.copyOf(filters));
        }
    }

    static String validateInfoNames(final JsonNode info, final Shape shape) {
        final var parsed = new DataExportTemplateInfoParser().parse(info);
        final var fields = new HashSet<String>();
        final var filters = new HashSet<String>();
        parsed.fields().forEach(field -> fields.add(field.technicalName()));
        parsed.filters().forEach(field -> filters.add(field.technicalName()));
        require(
                fields.equals(shape.fields())
                        && parsed.fields().size() == shape.fields().size()
                        && parsed.filters().size() == shape.filters().size(),
                "LOCALIZADOR_INFO_EXACT_HISTORICAL_NAMES");
        if (filters.equals(shape.filters())) {
            return HISTORICAL_INFO_VARIANT;
        }
        require(
                filters.equals(OBSERVED_QUALIFIED_TO_HISTORICAL.keySet()),
                "LOCALIZADOR_INFO_EXACT_HISTORICAL_NAMES");
        final var historical = new HashSet<String>();
        for (final String qualified : filters) {
            require(
                    historical.add(OBSERVED_QUALIFIED_TO_HISTORICAL.get(qualified)),
                    "LOCALIZADOR_INFO_OBSERVED_FILTER_BIJECTION");
        }
        require(
                historical.equals(shape.filters()) && historical.size() == 8,
                "LOCALIZADOR_INFO_OBSERVED_FILTER_BIJECTION");
        return QUALIFIED_INFO_VARIANT;
    }

    static boolean qualified(
            final JsonNode row, final Shape shape, final Map<String, Long> refusals) {
        require(row != null && row.isObject(), "LOCALIZADOR_ROW_OBJECT_REQUIRED");
        boolean valid = true;
        for (final String field : shape.fields()) {
            final JsonNode value = row.get(field);
            if (value == null || !valueQualified(value, shape.paths().get("/" + field))) {
                valid = false;
                refusals.merge(
                        "/" + field + ":" + (value == null ? "ABSENT" : type(value)),
                        1L,
                        Long::sum);
            }
            if (value != null && value.isArray()) {
                for (final JsonNode item : value) {
                    if (!valueQualified(item, shape.paths().get("/" + field + "/*"))) {
                        valid = false;
                        refusals.merge("/" + field + "/*:" + type(item), 1L, Long::sum);
                    }
                }
            }
        }
        for (final var names = row.fieldNames(); names.hasNext(); ) {
            if (!shape.fields().contains(names.next())) {
                valid = false;
                refusals.merge("UNAPPROVED_SOURCE_PATH", 1L, Long::sum);
            }
        }
        return valid;
    }

    private static boolean valueQualified(final JsonNode value, final JsonNode rule) {
        if (rule == null) {
            return false;
        }
        if (value.isNull()) {
            return rule.path("nullable").isBoolean() && rule.path("nullable").booleanValue();
        }
        final String type = type(value);
        for (final var allowed : rule.path("jsonTypes")) {
            if (type.equals(allowed.asText())) {
                return true;
            }
        }
        return false;
    }

    static String type(final JsonNode value) {
        return value.isNull()
                ? "NULL"
                : value.isIntegralNumber()
                        ? "INTEGER"
                        : value.isNumber()
                                ? "NUMBER"
                                : value.isTextual()
                                        ? "STRING"
                                        : value.isArray()
                                                ? "ARRAY"
                                                : value.isObject() ? "OBJECT" : "BOOLEAN";
    }

    static JsonNode strict(final byte[] bytes) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode node = ExportValidationSupport.JSON.readTree(parser);
            require(node != null && parser.nextToken() == null, "LOCALIZADOR_JSON_EOF_REQUIRED");
            return node;
        }
    }

    /** Original object slice from JSONL envelope; no tree serialization or token normalization. */
    static String originalRecord(final String line) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(
                    parser.nextToken() == JsonToken.START_OBJECT,
                    "LOCALIZADOR_RAW_ENVELOPE_OBJECT");
            String result = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME,
                        "LOCALIZADOR_RAW_ENVELOPE_FIELD");
                final String name = parser.currentName();
                final JsonToken token = parser.nextToken();
                if ("record".equals(name)) {
                    require(
                            result == null && token == JsonToken.START_OBJECT,
                            "LOCALIZADOR_RAW_RECORD_OBJECT");
                    final int start =
                            Math.toIntExact(parser.currentTokenLocation().getCharOffset());
                    parser.skipChildren();
                    final int end = Math.toIntExact(parser.currentLocation().getCharOffset());
                    require(
                            start >= 0
                                    && end > start
                                    && end <= line.length()
                                    && line.charAt(start) == '{'
                                    && line.charAt(end - 1) == '}',
                            "LOCALIZADOR_RAW_RECORD_OFFSETS");
                    result = line.substring(start, end);
                } else {
                    require(token != null, "LOCALIZADOR_RAW_ENVELOPE_VALUE");
                    parser.skipChildren();
                }
            }
            require(result != null && parser.nextToken() == null, "LOCALIZADOR_RAW_RECORD_EOF");
            return result;
        }
    }
}
