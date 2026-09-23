package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionLocalContract;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationWireOracle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Authors inputs and expectations independently before any physical query is made. */
public final class QualificationArtifactScenarioFixtures {
    private QualificationArtifactScenarioFixtures() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        write(Path.of(args[0]).resolve("first"), false, 2);
        write(Path.of(args[0]).resolve("second"), true, 2);
    }

    static Paths write(final Path folder, final boolean alternate, final int roots)
            throws Exception {
        Files.createDirectories(folder);
        final var input =
                object().put("version", "local-artifact-scenario-v1")
                        .put("mode", "LOCAL_ARTIFACT_ROLLBACK")
                        .put("target", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("support", "PACKAGED_ANALYTIC_SUPPORT_V1")
                        .put("windowStart", "2036-04-01")
                        .put("windowEndExclusive", "2036-04-04")
                        .put("roots", roots)
                        .put("pageSize", 2);
        final var expansions = input.putArray("expansions");
        for (final var template :
                List.of(
                        DataExportTemplate.CONTAS_A_PAGAR,
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        DataExportTemplate.INVENTARIO,
                        DataExportTemplate.SINISTROS)) {
            final String family = JdbcExpansionLaboratory.vertical(template);
            final String name = family.toLowerCase(Locale.ROOT);
            final var manifest =
                    object().put("version", 1)
                            .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                            .put("family", family)
                            .put(
                                    "contractSha256",
                                    ExpansionLocalContract.release(template)
                                            .contractFingerprint()
                                            .sha256())
                            .put("date", "2036-04-01")
                            .put("source", "SYNTHETIC_EXPANSION_LAB")
                            .put("tenant", "SYNTHETIC_TENANT")
                            .put("pageSize", 2)
                            .put("maximumPages", 1000)
                            .put("maximumRows", 100000)
                            .put("complete", true);
            final var pages = manifest.putArray("pages");
            for (int first = 0, page = 1; first < roots * 3 + 2; first += 2, page++) {
                final var rows = array();
                for (int index = first; index < Math.min(first + 2, roots * 3); index++) {
                    final var data = AnalyticScenarioFixtures.expansionData(template);
                    if (alternate) {
                        data.put(amountField(family), "200.00");
                    }
                    rows.add(
                            ExpansionLaboratoryFixtures.envelope(
                                    template,
                                    data,
                                    index + 1,
                                    index / 3 + 1,
                                    index % 3 == 1 ? 2 : 1));
                }
                final String file = "page-" + page + ".json";
                save(folder.resolve(name).resolve(file), rows);
                pages.add(pin(file, folder.resolve(name).resolve(file)));
            }
            save(folder.resolve(name).resolve("capture.json"), manifest);
            expansions.add(
                    pin(name + "/capture.json", folder.resolve(name).resolve("capture.json")));
        }
        final Path inputFile = folder.resolve("input.json");
        final var raster =
                QualificationRasterArtifactFixtures.write(
                        folder.resolve("raster"), roots, alternate);
        input.set("raster", pin("raster/raster.json", raster));
        final var relationManifest =
                object().put("version", "local-expansion-relations-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("date", "2036-04-01");
        final var relationFiles = relationManifest.putArray("batches");
        for (int first = 1; first <= roots; first += 14) {
            final var relationRows = array();
            for (final var relation :
                    ExpansionLaboratoryRelationFixtures.bindingBatch(
                            java.time.LocalDate.of(2036, 4, 1),
                            first,
                            Math.min(14, roots - first + 1))) {
                final var row =
                        object().put("bindingKey", relation.bindingKey())
                                .put("revision", relation.revision())
                                .put("kind", relation.kind().name())
                                .put("targetKey", relation.targetKey())
                                .put("targetDate", relation.targetDate().toString())
                                .put("cardinality", relation.cardinality().name())
                                .put("active", relation.active())
                                .put("evidence", relation.evidence());
                row.set("root", key(relation.root()));
                row.set("part", key(relation.part()));
                row.set("component", key(relation.component()));
                row.set("document", key(relation.document()));
                relationRows.add(row);
            }
            final String file = "batch-" + first + ".json";
            save(folder.resolve("relations").resolve(file), relationRows);
            relationFiles.add(pin(file, folder.resolve("relations").resolve(file)));
        }
        final Path relationsFile = folder.resolve("relations/manifest.json");
        save(relationsFile, relationManifest);
        input.set("relations", pin("relations/manifest.json", relationsFile));
        save(inputFile, input);
        final var outputs =
                (ObjectNode) resource("/qualification-laboratory/outputs.synthetic.json");
        if (alternate) {
            for (final var contract : outputs.path("contracts")) {
                for (final var cell : contract.path("columns")) {
                    final String id = contract.path("id").asText(),
                            name = cell.path("name").asText();
                    if (id.equals("SQL-01")
                                    && Set.of("Fatura/Valor", "Fatura/Valor Total").contains(name)
                            || id.equals("SQL-06") && name.equals("Valor")
                            || id.equals("SQL-11") && name.equals("Valor de NF")
                            || id.equals("SQL-12") && name.equals("Resultado final")) {
                        ((ObjectNode) cell)
                                .put("value", "200.00")
                                .put(
                                        "example",
                                        "Independent alternate input: declared amount 200.00, unchanged rule and grain.");
                    }
                    if (id.equals("SQL-13") && name.equals("placa_veiculo")) {
                        ((ObjectNode) cell)
                                .put("value", "SYN0009")
                                .put("example", "Independent alternate Raster plate literal.");
                    }
                    if (id.equals("SQL-13")
                            && Set.of("TRANSIT TIME", "transit_time_texto").contains(name)) {
                        ((ObjectNode) cell)
                                .put("value", "02:05")
                                .put(
                                        "example",
                                        "Declared direct duration 125 minutes is 2 hours and 5 minutes.");
                    }
                }
            }
        }
        save(folder.resolve("outputs.json"), outputs);
        // A separate oracle implementation reconstructs expected wire literals; never a query or
        // captured/staged row. Alternate expectations are declared separately from the input edit.
        final var wire = new QualificationWireOracle(AnalyticScenarioVariant.BASELINE);
        final var manifest =
                object().put("version", "local-wire-expectations-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var wireRows = manifest.putArray("rows");
        for (final String entity :
                List.of("CAP", "FAT", "INV", "SIN", "MAN", "COL", "COT", "FRE", "LOC")) {
            for (int root = 1; root <= roots; root++) {
                for (int component = 1;
                        component <= (Set.of("CAP", "FAT", "INV", "SIN").contains(entity) ? 2 : 1);
                        component++) {
                    final ObjectNode row = wire.source(entity, root, component, 1, false);
                    if (alternate && Set.of("CAP", "FAT", "INV", "SIN").contains(entity)) {
                        row.put(amountField(entity), "200.00");
                    }
                    final String file =
                            entity.toLowerCase(Locale.ROOT)
                                    + "-"
                                    + root
                                    + "-"
                                    + component
                                    + ".json";
                    save(folder.resolve("wire").resolve(file), row);
                    wireRows.addObject()
                            .put("entity", entity)
                            .put("root", root)
                            .put("component", component)
                            .put("revision", 1)
                            .put("correction", false)
                            .put("rootKey", entity + "-root-" + root)
                            .put("rootType", "STRING")
                            .put("partKey", "part-" + root)
                            .put("partType", "STRING")
                            .put("componentKey", "component-" + component)
                            .put("componentType", "STRING")
                            .put("file", file)
                            .put(
                                    "sha256",
                                    QualificationJson.sha256(folder.resolve("wire").resolve(file)));
                }
            }
        }
        save(folder.resolve("wire").resolve("manifest.json"), manifest);
        final var facts =
                object().put("version", "local-fact-oracles-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var entries = facts.putArray("facts");
        for (final String fact : List.of("MAT01", "MAT02", "MAT03", "MAT04", "MAT05")) {
            final var rows = array();
            if (fact.equals("MAT02")) {
                rows.addObject()
                        .put("date", "2036-04-01")
                        .put("branch", "synthetic-branch-a")
                        .put("issued", roots)
                        .put("unloaded", 0)
                        .put("scanned", roots)
                        .put("incomplete", 0)
                        .put("total", roots)
                        .put("percentage", "100.00000000");
                // Unloading is explicitly assigned to branch B; inventory is assigned to A.
                rows.addObject()
                        .put("date", "2036-04-01")
                        .put("branch", "synthetic-branch-b")
                        .put("issued", 0)
                        .put("unloaded", roots)
                        .put("scanned", 0)
                        .put("incomplete", 0)
                        .put("total", roots)
                        .put("percentage", "0.00000000");
            } else {
                for (int root = 1; root <= roots; root++) {
                    switch (fact) {
                        case "MAT01" -> {
                            rows.addObject()
                                    .put("sourceKey", "INTEGER:" + (300000 + root))
                                    .put("indicator", "PE")
                                    .put("amount", "120.00");
                            rows.addObject()
                                    .put("sourceKey", "INTEGER:" + (300000 + root))
                                    .put("indicator", "CB")
                                    .put("amount", "120.00");
                        }
                        case "MAT03" ->
                                rows.addObject()
                                        .put("sourceKey", "INTEGER:" + (300000 + root))
                                        .put("amount", "120.00");
                        case "MAT04" ->
                                rows.addObject()
                                        .put("sourceKey", "FAT-root-" + root)
                                        .put("amount", alternate ? "200.00" : "100.00");
                        case "MAT05" ->
                                rows.addObject()
                                        .put("sourceKey", "INTEGER:" + root)
                                        .put("date", "2036-04-01")
                                        .put("amount", "120.00");
                        default -> throw new IllegalArgumentException();
                    }
                }
            }
            final String file = fact.toLowerCase(Locale.ROOT) + ".json";
            save(folder.resolve("facts").resolve(file), rows);
            final var descriptor = pin(file, folder.resolve("facts").resolve(file));
            descriptor
                    .put("id", fact)
                    .put(
                            "grain",
                            fact.equals("MAT01")
                                    ? "sourceKey,indicator"
                                    : fact.equals("MAT02") ? "date,branch" : "sourceKey");
            entries.add(descriptor);
        }
        save(folder.resolve("facts").resolve("manifest.json"), facts);
        final var oracle =
                object().put("version", "local-artifact-oracles-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1")
                        .put("inputSha256", QualificationJson.sha256(inputFile))
                        .put("schemaSha256", LocalArtifactScenario.schemaFingerprint())
                        .put("runtimeSha256", LocalArtifactScenario.runtimeFingerprint());
        oracle.set("outputs", pin("outputs.json", folder.resolve("outputs.json")));
        oracle.set(
                "wire", pin("wire/manifest.json", folder.resolve("wire").resolve("manifest.json")));
        oracle.set(
                "facts",
                pin("facts/manifest.json", folder.resolve("facts").resolve("manifest.json")));
        final Path oracleFile = folder.resolve("oracle.json");
        save(oracleFile, oracle);
        return new Paths(inputFile, oracleFile);
    }

    private static String amountField(final String entity) {
        return switch (entity) {
            case "CAP" -> "value";
            case "FAT" -> "fit_ant_value";
            case "INV" -> "cnr_c_s_fit_invoices_value";
            case "SIN" -> "insurance_claim_total";
            default -> throw new IllegalArgumentException();
        };
    }

    private static ObjectNode key(final br.com.esl.etl.v2.plataforma.expansao.ExpansionKey value) {
        return object().put("kind", value.kind().name()).put("value", value.value());
    }

    private static JsonNode resource(final String name) throws IOException {
        try (var stream = QualificationArtifactScenarioFixtures.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException();
            }
            return QualificationJson.parse(stream.readNBytes(524289), 524288);
        }
    }

    private static ObjectNode pin(final String file, final Path path) throws IOException {
        return object().put("file", file).put("sha256", QualificationJson.sha256(path));
    }

    private static void save(final Path path, final JsonNode node) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, node.toPrettyString());
    }

    private static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static ArrayNode array() {
        return JsonNodeFactory.instance.arrayNode();
    }

    record Paths(Path input, Path oracle) {}
}
