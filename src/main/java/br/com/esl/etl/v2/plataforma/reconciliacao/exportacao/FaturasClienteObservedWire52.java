package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact archived4924 wire types; current INFO, scope and Native presentation remain independent.
 */
final class FaturasClienteObservedWire52 {
    static final String RESOURCE =
            "contracts/dataexport/expansion-4924-observed-wire-20261025-v1.json";
    static final String SHA256 = "4bdc78464659c30367ba8d97ed7e9a5f88824503beaa05aaff621c5e97354405";
    static final String INFO_NAME_BINDING_POLICY = "STRICT_QUALIFIED_NAMES52_FILTERS18_V1";
    static final String INFO_NAME_BINDING_VARIANT = "OBSERVED_QUALIFIED_NAMES52_FILTERS18_V1";

    record Shape(Map<String, JsonNode> paths, Set<String> fields, Set<String> filters) {}

    private FaturasClienteObservedWire52() {}

    static final String P2_RESOURCE =
            "contracts/dataexport/expansion-4924-observed-wire-20261108-p2.json";
    static final String P2_SHA256 =
            "aa2630cde155d02ce0635a173b801c8a126546a33d2e847c7befb837df67885e";

    static Shape shape() throws IOException {
        return load(RESOURCE, SHA256, "observed-expansion-wire-4924-20261025-v1");
    }

    static Shape observed20261108P2() throws IOException {
        return load(P2_RESOURCE, P2_SHA256, "observed-expansion-wire-4924-20261108-p2");
    }

    private static Shape load(final String resource, final String sha, final String release)
            throws IOException {
        try (var stream = FaturasClienteObservedWire52.class.getResourceAsStream("/" + resource)) {
            require(stream != null, "FATURAS_CLIENTE_WIRE_RESOURCE_REQUIRED");
            final byte[] bytes = stream.readNBytes(65537);
            require(
                    bytes.length <= 65536 && sha.equals(ExportValidationSupport.sha256(bytes)),
                    "FATURAS_CLIENTE_WIRE_RESOURCE_PIN");
            final JsonNode contract = strict(bytes);
            require(
                    contract.path("templateId").isIntegralNumber()
                            && contract.path("templateId").intValue() == 4924
                            && release.equals(text(contract, "schemaRelease"))
                            && "$".equals(text(contract, "recordRoot"))
                            && "ARRAY".equals(text(contract, "rootCardinality")),
                    "FATURAS_CLIENTE_WIRE_ROOT_ARRAY_CONTEXT");
            final var paths = new HashMap<String, JsonNode>();
            final var fields = new HashSet<String>();
            final var metadataFields = new HashSet<String>();
            final var filters = new HashSet<String>();
            for (final var rule : contract.required("fields")) {
                final String field = text(rule, "path");
                require(
                        field.matches("[A-Za-z_][A-Za-z0-9_]*")
                                && paths.putIfAbsent("/" + field, rule) == null
                                && fields.add(field)
                                && rule.path("required").isBoolean()
                                && rule.get("required").booleanValue()
                                && rule.path("wireTypes").isArray()
                                && !rule.get("wireTypes").isEmpty()
                                && rule.path("arrayItemTypes").isArray()
                                && rule.path("unknownNonNull").isBoolean(),
                        "FATURAS_CLIENTE_WIRE_REQUIRED_UNIQUE_RULE");
            }
            for (final var metadata : contract.required("metadata")) {
                if ("DATA_FIELD".equals(text(metadata, "kind"))) {
                    require(
                            metadataFields.add(text(metadata, "path")),
                            "FATURAS_CLIENTE_WIRE_UNIQUE_METADATA_FIELD");
                } else {
                    require(
                            "DATA_FILTER".equals(text(metadata, "kind"))
                                    && filters.add(text(metadata, "path")),
                            "FATURAS_CLIENTE_WIRE_UNIQUE_METADATA_FILTER");
                }
            }
            require(
                    fields.size() == 52
                            && paths.size() == 52
                            && fields.equals(metadataFields)
                            && filters.size() == 18
                            && filters.equals(FaturasClienteIndependentRequest.INFO_FILTER_NAMES),
                    "FATURAS_CLIENTE_WIRE_EXACT52_FIELDS18_FILTERS");
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
                parsed.fields().size() == 52
                        && parsed.filters().size() == 18
                        && fields.equals(shape.fields())
                        && filters.equals(shape.filters()),
                "FATURAS_CLIENTE_INFO_EXACT52_FIELDS18_FILTERS");
        return INFO_NAME_BINDING_VARIANT;
    }

    /** Refusals qualify occurrences; they do not discard new paths, NULLs or literal responses. */
    static boolean qualified(
            final JsonNode row, final Shape shape, final Map<String, Long> refusals) {
        require(row != null && row.isObject(), "FATURAS_CLIENTE_WIRE_ROW_OBJECT");
        boolean valid = true;
        for (final String field : shape.fields()) {
            final JsonNode value = row.get(field);
            final JsonNode rule = shape.paths().get("/" + field);
            if (value == null || !allowed(value, rule.path("wireTypes"))) {
                valid = false;
                refusals.merge(
                        "/" + field + ":" + (value == null ? "ABSENT" : type(value)),
                        1L,
                        Long::sum);
            }
            if (value != null && value.isArray()) {
                for (final JsonNode item : value) {
                    if (!allowed(item, rule.path("arrayItemTypes"))) {
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

    private static boolean allowed(final JsonNode value, final JsonNode allowedTypes) {
        for (final var allowed : allowedTypes) {
            if (type(value).equals(allowed.asText())) {
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
            require(
                    node != null && parser.nextToken() == null,
                    "FATURAS_CLIENTE_JSON_EOF_REQUIRED");
            return node;
        }
    }
}
