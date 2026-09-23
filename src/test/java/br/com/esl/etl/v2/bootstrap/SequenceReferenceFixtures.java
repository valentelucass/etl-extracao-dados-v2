package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** Separate reference release example: source pages and source revision remain byte-identical. */
final class SequenceReferenceFixtures {
    private SequenceReferenceFixtures() {}

    static Path write(final Path folder, final boolean alternate) throws Exception {
        final var sequence = IntegralSequenceFixtures.scheduled(folder, alternate, 2);
        final var target = folder.resolve("reference");
        IntegralCampaignFixtures.copy(folder.resolve("initial"), target);
        final var spec = new IntegralArtifactFixtures.Spec(alternate, 2);
        IntegralCampaignFixtures.supplementRevision(target, spec.revision() + 1);
        revise(target, alternate);
        final var declaration = (ObjectNode) IntegralArtifactFixtures.read(sequence);
        final var steps = (ArrayNode) declaration.path("steps");
        final var revised = ((ObjectNode) steps.get(0)).deepCopy();
        while (steps.size() > 1) {
            steps.remove(steps.size() - 1);
        }
        revised.put("id", "reference")
                .put("predecessor", "bootstrap")
                .put("mode", "BACKFILL")
                .put("executionRevision", spec.revision() + 1)
                .put("referenceRevision", 3);
        revised.set("input", IntegralArtifactFixtures.pin(folder, target.resolve("input.json")));
        revised.set("oracle", IntegralArtifactFixtures.pin(folder, target.resolve("oracle.json")));
        steps.add(revised);
        IntegralArtifactFixtures.save(sequence, declaration);
        return SequenceRecompositionFixtures.append(
                folder, sequence, target, spec, "reference", spec.revision() + 2);
    }

    static void revise(final Path target, final boolean alternate) throws Exception {
        final var refsFile = target.resolve("references/manifest.json");
        final var refs = (ObjectNode) IntegralArtifactFixtures.read(refsFile);
        refs.put("revision", 3);
        for (final String kind : java.util.List.of("tariffs", "regions")) {
            final var dataFile = target.resolve("references/" + kind + ".json");
            final var data = IntegralArtifactFixtures.read(dataFile);
            for (final var row : data.path("rows")) {
                if (kind.equals("tariffs")
                        && row.path("origin").asText().equals("SP")
                        && row.path("destination").asText().equals("RJ")) {
                    ((ObjectNode) row).put("amount", alternate ? "3.61" : "2.83");
                } else if (kind.equals("regions") && row.path("kind").asText().equals("CEP")) {
                    ((ObjectNode) row)
                            .put("region", alternate ? "REGIAO_REVISAO_B" : "REGIAO_REVISAO_A");
                }
            }
            IntegralArtifactFixtures.save(dataFile, data);
            ((ObjectNode) refs.path("files"))
                    .set(kind, IntegralArtifactFixtures.pin(refsFile.getParent(), dataFile));
        }
        IntegralArtifactFixtures.save(refsFile, refs);
        final var input = (ObjectNode) IntegralArtifactFixtures.read(target.resolve("input.json"));
        input.set("references", IntegralArtifactFixtures.pin(target, refsFile));
        IntegralArtifactFixtures.save(target.resolve("input.json"), input);
        // Independent literal changes, selected only by the frozen output contract's column names.
        output(
                target,
                AnalyticSqlContract.SQL_03,
                "Região Logística",
                alternate ? "REGIAO_REVISAO_B" : "REGIAO_REVISAO_A");
        output(target, AnalyticSqlContract.SQL_05, "Min. Frete/KG", alternate ? "3.61" : "2.83");
        final var oracle =
                (ObjectNode) IntegralArtifactFixtures.read(target.resolve("oracle.json"));
        oracle.put("inputSha256", QualificationJson.sha256(target.resolve("input.json")));
        oracle.set(
                "outputs",
                IntegralArtifactFixtures.pin(target, target.resolve("outputs/manifest.json")));
        IntegralArtifactFixtures.save(target.resolve("oracle.json"), oracle);
    }

    private static void output(
            final Path folder,
            final AnalyticSqlContract contract,
            final String column,
            final String value)
            throws Exception {
        final var manifestFile = folder.resolve("outputs/manifest.json");
        final var manifest = IntegralArtifactFixtures.read(manifestFile);
        final int index =
                AnalyticSqlCatalog.columns(contract).stream()
                                .filter(item -> item.name().equals(column))
                                .findFirst()
                                .orElseThrow()
                                .ordinal()
                        - 1;
        for (final var entry : manifest.path("outputs")) {
            if (!entry.path("id").asText().equals(contract.id())) {
                continue;
            }
            for (final var descriptor : entry.path("batches")) {
                final var file = manifestFile.getParent().resolve(descriptor.path("file").asText());
                final var rows = IntegralArtifactFixtures.read(file);
                for (final var row : rows) {
                    ((ArrayNode) row)
                            .set(
                                    index,
                                    com.fasterxml.jackson.databind.node.TextNode.valueOf(value));
                }
                IntegralArtifactFixtures.save(file, rows);
                ((ObjectNode) descriptor).put("sha256", QualificationJson.sha256(file));
            }
        }
        IntegralArtifactFixtures.save(manifestFile, manifest);
    }
}
