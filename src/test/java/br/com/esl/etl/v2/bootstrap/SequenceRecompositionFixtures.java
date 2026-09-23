package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;

/** Expected KM values are literal rules, authored before any runtime or SQL observation. */
final class SequenceRecompositionFixtures {
    private SequenceRecompositionFixtures() {}

    static Path write(final Path folder, final boolean alternate, final int roots)
            throws Exception {
        final var sequence = IntegralSequenceFixtures.scheduled(folder, alternate, roots);
        final var source = folder.resolve("advanced");
        final var spec = new IntegralArtifactFixtures.Spec(alternate, roots, true);
        return append(folder, sequence, source, spec, "later", spec.revision() + 1);
    }

    static Path append(
            final Path folder,
            final Path sequence,
            final Path source,
            final IntegralArtifactFixtures.Spec spec,
            final String predecessor,
            final int execution)
            throws Exception {
        final boolean alternate = spec.alternate();
        final var target = folder.resolve("supplement");
        try (var paths = Files.walk(source)) {
            for (final var path : paths.toList()) {
                final var copy = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy);
                }
            }
        }
        final var input = (ObjectNode) IntegralArtifactFixtures.read(target.resolve("input.json"));
        final int supplementRevision = input.path("supplementRevision").asInt(spec.revision()) + 1;
        final int referenceRevision =
                IntegralArtifactFixtures.read(
                                target.resolve(input.path("references").path("file").asText()))
                        .path("revision")
                        .intValue();
        input.put("version", "local-artifact-scenario-v3")
                .put("captureDate", spec.start().toString())
                .put("supplementRevision", supplementRevision);
        final var supportFile = target.resolve("support/manifest.json");
        final var support = (ObjectNode) IntegralArtifactFixtures.read(supportFile);
        final var groups = support.path("batches").fields();
        while (groups.hasNext()) {
            final var group = groups.next();
            for (final var descriptor : group.getValue()) {
                final var file = supportFile.getParent().resolve(descriptor.path("file").asText());
                final var rows = IntegralArtifactFixtures.read(file);
                for (final var row : rows) {
                    ((ObjectNode) row).put("revision", supplementRevision);
                    if (group.getKey().equals("relational")) {
                        ((ObjectNode) row)
                                .put("evidenceId", row.path("evidenceId").asText() + "-supplement");
                    }
                    if (group.getKey().equals("freight")) {
                        ((ObjectNode) row.path("attributes").path("attributes"))
                                .put("km", alternate ? "57.62500000" : "44.87500000");
                    }
                }
                IntegralArtifactFixtures.save(file, rows);
                ((ObjectNode) descriptor).put("sha256", QualificationJson.sha256(file));
            }
        }
        IntegralArtifactFixtures.save(supportFile, support);
        input.set("supplements", IntegralArtifactFixtures.pin(target, supportFile));
        IntegralArtifactFixtures.save(target.resolve("input.json"), input);
        final var outputFile = target.resolve("outputs/manifest.json");
        final var outputs = (ObjectNode) IntegralArtifactFixtures.read(outputFile);
        final int km =
                AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_02).stream()
                                .filter(column -> column.name().equals("KM"))
                                .findFirst()
                                .orElseThrow()
                                .ordinal()
                        - 1;
        for (final var output : outputs.path("outputs")) {
            if (!output.path("id").asText().equals(AnalyticSqlContract.SQL_02.id())) {
                continue;
            }
            for (final var descriptor : output.path("batches")) {
                final var file = outputFile.getParent().resolve(descriptor.path("file").asText());
                final var rows = IntegralArtifactFixtures.read(file);
                for (final var row : rows) {
                    ((ArrayNode) row)
                            .set(
                                    km,
                                    com.fasterxml.jackson.databind.node.TextNode.valueOf(
                                            alternate ? "57.625" : "44.875"));
                }
                IntegralArtifactFixtures.save(file, rows);
                ((ObjectNode) descriptor).put("sha256", QualificationJson.sha256(file));
            }
        }
        IntegralArtifactFixtures.save(outputFile, outputs);
        final var oracle =
                (ObjectNode) IntegralArtifactFixtures.read(target.resolve("oracle.json"));
        oracle.put("inputSha256", QualificationJson.sha256(target.resolve("input.json")));
        oracle.set("outputs", IntegralArtifactFixtures.pin(target, outputFile));
        IntegralArtifactFixtures.save(target.resolve("oracle.json"), oracle);
        final var declaration = (ObjectNode) IntegralArtifactFixtures.read(sequence);
        declaration.put("version", "local-artifact-sequence-v3").put("maximumSeconds", 1800);
        for (final var stage : declaration.path("steps")) {
            ((ObjectNode) stage).put("operation", "CAPTURE");
        }
        final var last =
                ((ArrayNode) declaration.path("steps"))
                        .addObject()
                        .put("id", "recompose")
                        .put("predecessor", predecessor)
                        .put("operation", "RECOMPOSE")
                        .put("mode", "BACKFILL")
                        .put("executionRevision", execution)
                        .put("sourceRevision", spec.revision())
                        .put("referenceRevision", referenceRevision)
                        .put("maximumSeconds", 240);
        last.putNull("schedule");
        last.set("input", IntegralArtifactFixtures.pin(folder, target.resolve("input.json")));
        last.set("oracle", IntegralArtifactFixtures.pin(folder, target.resolve("oracle.json")));
        IntegralArtifactFixtures.save(sequence, declaration);
        return sequence;
    }
}
