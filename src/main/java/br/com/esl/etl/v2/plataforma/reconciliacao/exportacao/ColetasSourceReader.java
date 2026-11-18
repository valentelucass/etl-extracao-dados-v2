package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.zip.Inflater;

/** Bounded direct mapped JSONL and historical closed row codec; no business-specific IDs/weeks. */
final class ColetasSourceReader {
    record Row(List<ObjectNode> atoms) {}

    private ColetasSourceReader() {}

    static List<Row> read(
            final byte[] bytes,
            final JsonNode fields,
            final JsonNode policy,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                bytes.length > 0 && bytes.length <= 8388608 && bytes[bytes.length - 1] == '\n',
                "COLETAS_COMPLETE_JSONL_FINAL_LF");
        final String format = text(policy, "sourceFormat");
        require(
                List.of("MAPPED_PAYLOAD_JSONL_V1", "COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2")
                        .contains(format),
                "COLETAS_EXPLICIT_SOURCE_FORMAT");
        final List<Row> rows = new ArrayList<>();
        long expanded = 0;
        boolean header = false;
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(
                                                java.nio.charset.CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(!line.isBlank() && line.length() <= 262144, "COLETAS_SOURCE_LINE_BOUND");
                final List<ObjectNode> atoms = new ArrayList<>();
                if ("MAPPED_PAYLOAD_JSONL_V1".equals(format)) {
                    final var members = new java.util.HashMap<String, ObjectNode>();
                    try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                        require(
                                parser.nextToken()
                                        == com.fasterxml.jackson.core.JsonToken.START_OBJECT,
                                "COLETAS_MAPPED_OBJECT");
                        while (parser.nextToken()
                                != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                            require(
                                    parser.currentToken()
                                            == com.fasterxml.jackson.core.JsonToken.FIELD_NAME,
                                    "COLETAS_MAPPED_MEMBER");
                            final String name = parser.currentName();
                            final var token = parser.nextToken();
                            final ObjectNode atom;
                            if (token == com.fasterxml.jackson.core.JsonToken.VALUE_NULL) {
                                atom = atom("NULL", "null", null);
                            } else if (token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                                atom = atom("VALUE", "string", parser.getText());
                            } else if (token != null && token.isNumeric()) {
                                atom =
                                        atom(
                                                "VALUE",
                                                token
                                                                == com.fasterxml.jackson.core
                                                                        .JsonToken.VALUE_NUMBER_INT
                                                        ? "integer"
                                                        : "number",
                                                parser.getText());
                            } else {
                                final JsonNode value =
                                        ExportValidationSupport.JSON.readTree(parser);
                                atom =
                                        atom(
                                                "VALUE",
                                                value != null && value.isBoolean()
                                                        ? "boolean"
                                                        : "unsupported",
                                                null);
                            }
                            require(
                                    members.putIfAbsent(name, atom) == null,
                                    "COLETAS_DUPLICATE_SOURCE_MEMBER");
                        }
                        require(parser.nextToken() == null, "COLETAS_SOURCE_TRAILING_JSON");
                    }
                    fields.forEach(
                            field ->
                                    atoms.add(
                                            members.getOrDefault(
                                                    text(field, "sourceField"),
                                                    atom("ABSENT", "json:absent", null))));
                } else {
                    final var wrapper = strict(line.getBytes(StandardCharsets.UTF_8));
                    if (!header) {
                        require(
                                "COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2"
                                                .equals(text(wrapper, "schema"))
                                        && "MAPPED_PAYLOAD".equals(text(wrapper, "stage"))
                                        && wrapper.path("header").path("template_id").asInt()
                                                == 6908
                                        && ExportValidationSupport.sha256(
                                                        line.getBytes(StandardCharsets.UTF_8))
                                                .equals(
                                                        text(
                                                                policy,
                                                                "closedProjectionHeaderSha256")),
                                "COLETAS_CLOSED_HEADER_ADMISSION");
                        final var admittedItems = new HashSet<String>();
                        wrapper.path("header")
                                .path("binding_evidence")
                                .fieldNames()
                                .forEachRemaining(admittedItems::add);
                        final var expected = new HashSet<String>();
                        fields.forEach(field -> expected.add(text(field, "itemId")));
                        require(
                                admittedItems.equals(expected),
                                "COLETAS_CLOSED_ALL31_HEADER_BINDINGS");
                        header = true;
                        continue;
                    }
                    require(
                            "ZLIB_JSON_ROW05_V1".equals(text(wrapper, "codec"))
                                    && wrapper.path("bytes").isIntegralNumber()
                                    && wrapper.path("bytes").asInt() > 0
                                    && wrapper.path("bytes").asInt() <= 2097152,
                            "COLETAS_CLOSED_ROW_CODEC");
                    final byte[] compressed = Base64.getDecoder().decode(text(wrapper, "data"));
                    final var inflater = new Inflater();
                    final byte[] decoded;
                    try {
                        inflater.setInput(compressed);
                        final var output = new java.io.ByteArrayOutputStream();
                        final byte[] scratch = new byte[8192];
                        while (!inflater.finished()) {
                            budget.check();
                            final int size = inflater.inflate(scratch);
                            require(
                                    size > 0
                                            && output.size() + size
                                                    <= wrapper.path("bytes").asInt(),
                                    "COLETAS_CLOSED_ROW_INFLATION");
                            output.write(scratch, 0, size);
                        }
                        require(inflater.getRemaining() == 0, "COLETAS_CLOSED_ROW_TRAILING_ZLIB");
                        decoded = output.toByteArray();
                    } finally {
                        inflater.end();
                    }
                    expanded += decoded.length;
                    require(
                            expanded <= 100663296
                                    && decoded.length == wrapper.path("bytes").asInt()
                                    && ExportValidationSupport.sha256(decoded)
                                            .equalsIgnoreCase(text(wrapper, "sha256")),
                            "COLETAS_CLOSED_ROW_HASH_BOUND");
                    final var record = strict(decoded);
                    require(
                            record.isObject() && record.size() == 1 && record.has("row"),
                            "COLETAS_CLOSED_ROW_SHAPE");
                    final var row = record.required("row");
                    require(
                            row.path("cells").size() == 31
                                    && row.path("cell_references").size() == 31
                                    && row.required("qualified_root")
                                            .equals(row.path("cells").path("item_221149"))
                                    && !row.path("root_identity_evidence").isEmpty(),
                            "COLETAS_CLOSED_ROOT_AND_REFERENCES");
                    for (final var field : fields) {
                        final var original = row.path("cells").required(text(field, "itemId"));
                        require(
                                original.isObject()
                                        && original.size() == 3
                                        && original.has("value")
                                        && List.of("VALUE", "NULL", "ABSENT")
                                                .contains(text(original, "presence"))
                                        && row.path("cell_references").has(text(field, "itemId")),
                                "COLETAS_CLOSED_ORIGINAL_ATOM");
                        require(
                                "VALUE".equals(text(original, "presence"))
                                        ? !original.get("value").isNull()
                                        : original.get("value").isNull(),
                                "COLETAS_CLOSED_NONVALUE_PAYLOAD");
                        atoms.add((ObjectNode) original);
                    }
                }
                require(rows.size() < 12000 && atoms.size() == 31, "COLETAS_SOURCE_ROWS_BOUND");
                rows.add(new Row(List.copyOf(atoms)));
            }
        }
        require(
                !"COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2".equals(format) || header,
                "COLETAS_CLOSED_HEADER_REQUIRED");
        return List.copyOf(rows);
    }

    static ObjectNode atom(final String presence, final String type, final String value) {
        final var atom =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("presence", presence)
                        .put("type", type);
        if (value == null) {
            atom.putNull("value");
        } else {
            atom.put("value", value);
        }
        return atom;
    }

    private static JsonNode strict(final byte[] bytes) throws Exception {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode result = ExportValidationSupport.JSON.readTree(parser);
            require(result != null && parser.nextToken() == null, "COLETAS_CLOSED_TRAILING_JSON");
            return result;
        }
    }
}
