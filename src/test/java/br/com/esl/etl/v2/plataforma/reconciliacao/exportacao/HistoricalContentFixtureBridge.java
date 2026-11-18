package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Authored catalog and books only. No retained supplier capture, historical receipt or authority is
 * read.
 */
public final class HistoricalContentFixtureBridge {
    private HistoricalContentFixtureBridge() {}

    public static ExportValidationSupport.Pin manifest(final Path directory, final String scenario)
            throws Exception {
        final var f = new ExportPairFixture(directory);
        final var pair = f.manifest();
        final var request = f.request.deepCopy();
        request.remove("attemptId");
        request.put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_REQUEST_V1")
                .put("replayId", UUID.randomUUID().toString())
                .put("pairId", "SYNTHETIC_HISTORICAL_FRETES");
        if ("ACQUISITION_FIELD".equals(scenario)) {
            request.put("requestedAt", "2036-03-27T15:00:00-03:00");
        }
        final var requestPin = pin(directory, "historical-request.json", request);
        final var config =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONFIG_V1")
                        .put("historicalContentOnly", true)
                        .put("profile", IndependentExportRequest.PROFILE)
                        .put("zone", "America/Sao_Paulo");
        final var catalog =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_JAVA_REALPAIR_CATALOG0725_V3_FINAL")
                        .put("fixtureOnly", true);
        final var selected =
                catalog.putArray("pairs")
                        .addObject()
                        .put("pairId", "SYNTHETIC_HISTORICAL_FRETES")
                        .put("entity", "FRETES")
                        .put("reportId", 6389)
                        .put("historicalContentAccepted", true)
                        .put("replayPreparationBlocked", false)
                        .put("periodStart", request.path("periodStart").asText())
                        .put("periodEnd", request.path("periodEnd").asText());
        selected.putObject("api").set("workbookPin", upper(pair.path("api").required("dataPin")));
        selected.putObject("ui").set("workbookPin", upper(pair.path("ui").required("dataPin")));
        final var catalogPin = pin(directory, "historical-catalog.json", catalog);
        final var manifest =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_PAIR_V1")
                        .put("historicalContentOnly", true);
        manifest.set("requestPin", node(requestPin));
        manifest.set("configPin", node(pin(directory, "historical-config.json", config)));
        manifest.set("mappingPin", pair.required("mappingPin"));
        manifest.set("catalogPin", node(catalogPin));
        for (final String side : List.of("api", "ui")) {
            manifest.putObject(side).set("dataPin", pair.path(side).required("dataPin"));
        }
        final var binding =
                manifest.putObject("catalogBinding")
                        .put("schema", "ROOT_JAVA_REALPAIR_PRODUCT_CATALOG_BINDING0728_V1")
                        .put("pairId", "SYNTHETIC_HISTORICAL_FRETES");
        binding.set("pairCatalog", upper(node(catalogPin)));
        binding.set("sourceRootPin", upper(pair.path("api").required("dataPin")));
        binding.set("uiRootPin", upper(pair.path("ui").required("dataPin")));
        if ("MIXED_PAIR".equals(scenario)) {
            ((ObjectNode) manifest.required("api"))
                    .set("dataPin", pair.path("ui").required("dataPin"));
        }
        final var admission =
                manifest.deepCopy()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_PAIR_BINDING_V1")
                        .put("replayId", request.path("replayId").asText());
        manifest.set("admissionPin", node(pin(directory, "historical-admission.json", admission)));
        return pin(directory, "historical-manifest.json", manifest);
    }

    private static ObjectNode upper(final JsonNode input) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("path", input.path("path").asText())
                .put("bytes", input.path("bytes").asLong())
                .put("sha256", input.path("sha256").asText().toUpperCase(Locale.ROOT));
    }

    private static JsonNode node(final ExportValidationSupport.Pin pin) {
        return ExportValidationSupport.JSON.valueToTree(pin);
    }

    private static ExportValidationSupport.Pin pin(
            final Path directory, final String name, final JsonNode value) throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        final var path = directory.resolve(name).toAbsolutePath();
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }
}
