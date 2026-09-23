package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** Authoring only: independent existing A/B rules produce the input and expected files. */
final class IntegralSequenceFixtures {
    private IntegralSequenceFixtures() {}

    static Path write(final Path directory, final boolean alternate, final int roots)
            throws Exception {
        final var spec = new IntegralArtifactFixtures.Spec(alternate, roots);
        final var first = directory.resolve("initial");
        final var advanced = directory.resolve("advanced");
        IntegralArtifactFixtures.write(first, spec);
        IntegralArtifactFixtures.write(
                advanced, new IntegralArtifactFixtures.Spec(alternate, roots, true));
        final var input =
                IntegralArtifactFixtures.object()
                        .put("version", "local-artifact-sequence-v1")
                        .put("target", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("source", spec.source())
                        .put("tenant", spec.tenant())
                        .put("windowStart", spec.start().toString())
                        .put("windowEndExclusive", spec.end().toString())
                        .put("roots", roots)
                        .put("pageSize", spec.pageSize())
                        .put("maximumSeconds", 720);
        final var steps = input.putArray("steps");
        step(
                steps.addObject(),
                directory,
                "initial",
                "bootstrap",
                null,
                "BOOTSTRAP",
                spec.revision(),
                spec.revision());
        step(
                steps.addObject(),
                directory,
                "initial",
                "replay",
                "bootstrap",
                "REPLAY",
                spec.revision() + 1,
                spec.revision());
        step(
                steps.addObject(),
                directory,
                "advanced",
                "later",
                "replay",
                "BACKFILL",
                spec.revision() + 1,
                spec.revision() + 1);
        final var file = directory.resolve("sequence.json");
        IntegralArtifactFixtures.save(file, input);
        return file;
    }

    private static void step(
            final ObjectNode node,
            final Path directory,
            final String folder,
            final String id,
            final String previous,
            final String mode,
            final int execution,
            final int source)
            throws Exception {
        node.put("id", id)
                .put("predecessor", previous)
                .put("mode", mode)
                .put("executionRevision", execution)
                .put("sourceRevision", source)
                .put("referenceRevision", 2)
                .put("maximumSeconds", 240);
        pin(node.putObject("input"), directory, folder + "/input.json");
        pin(node.putObject("oracle"), directory, folder + "/oracle.json");
    }

    private static void pin(final ObjectNode node, final Path directory, final String file)
            throws Exception {
        node.put("file", file).put("sha256", QualificationJson.sha256(directory.resolve(file)));
    }

    static Path scheduled(final Path directory, final boolean alternate, final int roots)
            throws Exception {
        final var file = write(directory, alternate, roots);
        final var json = (ObjectNode) IntegralArtifactFixtures.read(file);
        json.put("version", "local-artifact-sequence-v2");
        final var spec = new IntegralArtifactFixtures.Spec(alternate, roots);
        for (final var node : json.path("steps")) {
            final var schedules = ((ObjectNode) node).putObject("schedule");
            for (final String family : java.util.List.of("COL", "FRE", "MAN", "COT", "LOC")) {
                schedules
                        .putObject(family)
                        .put("tick", spec.start().plusDays(1) + "T12:00:00Z")
                        .put("lookbackSeconds", 0)
                        .put("deadlineSeconds", 172800)
                        .put("maximumCatchUp", 1)
                        .putArray("blackouts");
            }
        }
        IntegralArtifactFixtures.save(file, json);
        return file;
    }
}
