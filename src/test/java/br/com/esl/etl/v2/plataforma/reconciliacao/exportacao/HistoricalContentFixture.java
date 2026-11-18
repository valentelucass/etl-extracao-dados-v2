package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Synthetic-only bridge for the real Main tests. No historical artifact is opened. */
public final class HistoricalContentFixture {
    private HistoricalContentFixture() {}

    public static ObjectNode create(final Path directory) throws Exception {
        final var fixture = new ExportPairFixture(directory);
        final var normal = fixture.manifest();
        final var request = fixture.request.deepCopy();
        request.put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_REQUEST_V1")
                .put("replayId", request.path("attemptId").asText())
                .put("pairId", "SYNTHETIC_FRETES");
        request.remove("attemptId");
        final var pair =
                request.objectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_PAIR_V1")
                        .put("historicalContentOnly", true)
                        .put("mainResumeAllowed", false)
                        .put("operationalAcceptanceIssued", false);
        pair.set("requestPin", write(directory, "historical-request.json", request));
        final var config =
                request.objectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONFIG_V1")
                        .put("profile", IndependentExportRequest.PROFILE)
                        .put("zone", "America/Sao_Paulo")
                        .put("historicalContentOnly", true);
        pair.set("configPin", write(directory, "historical-config.json", config));
        pair.set("mappingPin", normal.required("mappingPin"));
        final var catalog =
                request.objectNode().put("schema", "ROOT_JAVA_REALPAIR_CATALOG0725_V3_FINAL");
        final var entry =
                catalog.putArray("pairs")
                        .addObject()
                        .put("pairId", "SYNTHETIC_FRETES")
                        .put("entity", "FRETES")
                        .put("reportId", 6389)
                        .put("historicalContentAccepted", true)
                        .put("replayPreparationBlocked", false)
                        .put("periodStart", request.path("periodStart").asText())
                        .put("periodEnd", request.path("periodEnd").asText());
        entry.putObject("api").set("workbookPin", upper(normal.path("api").required("dataPin")));
        entry.putObject("ui").set("workbookPin", upper(normal.path("ui").required("dataPin")));
        final var catalogPin = write(directory, "historical-catalog.json", catalog);
        pair.set("catalogPin", catalogPin);
        final var binding =
                pair.putObject("catalogBinding")
                        .put("schema", "ROOT_JAVA_REALPAIR_PRODUCT_CATALOG_BINDING0728_V1")
                        .put("pairId", "SYNTHETIC_FRETES");
        binding.set("pairCatalog", upper(catalogPin));
        binding.set("sourceRootPin", entry.path("api").required("workbookPin"));
        binding.set("uiRootPin", entry.path("ui").required("workbookPin"));
        pair.putObject("api").set("dataPin", normal.path("api").required("dataPin"));
        pair.putObject("ui").set("dataPin", normal.path("ui").required("dataPin"));
        return admit(directory, pair);
    }

    public static ObjectNode admit(final Path directory, final ObjectNode pair) throws Exception {
        final var admission =
                pair.objectNode().put("schema", "INDEPENDENT_EXPORT_HISTORICAL_PAIR_BINDING_V1");
        admission.put("replayId", read(pair.required("requestPin")).path("replayId").asText());
        for (final String field :
                List.of(
                        "requestPin",
                        "configPin",
                        "mappingPin",
                        "catalogPin",
                        "api",
                        "ui",
                        "catalogBinding",
                        "policyPin")) {
            if (pair.has(field)) {
                admission.set(field, pair.required(field));
            }
        }
        pair.set("admissionPin", write(directory, "historical-admission.json", admission));
        return pair;
    }

    public static ObjectNode write(final Path directory, final String name, final JsonNode value)
            throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        final var path = directory.resolve(name).toAbsolutePath();
        java.nio.file.Files.write(path, bytes);
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("path", path.toString())
                .put("sha256", ExportValidationSupport.sha256(bytes))
                .put("bytes", bytes.length);
    }

    public static ObjectNode read(final JsonNode pin) throws Exception {
        return (ObjectNode)
                ExportValidationSupport.JSON.readTree(
                        java.nio.file.Files.readAllBytes(Path.of(pin.path("path").asText())));
    }

    public static ObjectNode upper(final JsonNode pin) {
        final ObjectNode result = ((ObjectNode) pin).deepCopy();
        result.put("sha256", result.path("sha256").asText().toUpperCase(Locale.ROOT));
        return result;
    }
}
