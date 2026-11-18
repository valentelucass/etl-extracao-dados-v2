package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/** Only reusable field/control metadata is ported; no historical payloads or acquisition IDs. */
public final class ExportColumnCatalog {
    private static final String PIN =
            "1b796ac6ebd4fba197c5f3ff6995242e32a8903a13484ca8f42afb5ed6f17724";

    private ExportColumnCatalog() {}

    public static JsonNode fretes() throws IOException {
        try (var input =
                ExportColumnCatalog.class.getResourceAsStream(
                        "/contracts/dataexport/fretes-independent-export-columns-v1.json")) {
            require(input != null, "EXPORT_COLUMN_RESOURCE");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536 && PIN.equals(ExportValidationSupport.sha256(bytes)),
                    "EXPORT_COLUMN_RESOURCE_PIN");
            return ExportValidationSupport.JSON.readTree(bytes);
        }
    }

    public static void verify(final JsonNode mapping) throws IOException {
        final var canonical = fretes();
        require(
                mapping.required("idColumnIndex").equals(canonical.required("idColumnIndex")),
                "EXPORT_ID_COLUMN_BINDING");
        final var fields = mapping.required("columns");
        require(
                fields.size() == canonical.required("columns").size(),
                "EXPORT_ALL_COLUMNS_REQUIRED");
        for (int index = 0; index < fields.size(); index++) {
            final var expected = canonical.get("columns").get(index);
            final var actual = fields.get(index);
            for (final String key :
                    java.util.List.of("name", "itemId", "uiColumn", "total", "totalKind")) {
                require(
                        expected.required(key).equals(actual.required(key)),
                        "EXPORT_HISTORICAL_FIELD_MODEL_BINDING");
            }
        }
    }
}
